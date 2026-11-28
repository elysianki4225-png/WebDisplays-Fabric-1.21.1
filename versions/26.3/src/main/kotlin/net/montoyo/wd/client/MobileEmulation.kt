package net.montoyo.wd.client

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest

@Environment(EnvType.CLIENT)
object MobileEmulation {
    private val CHROME_VERSION_REGEX = Regex("""Chrome/(\d+(?:\.\d+)*)""")
    private const val FALLBACK_CHROME_VERSION = "126.0.0.0"

    private val HIGH_ENTROPY_HINTS = setOf(
        "sec-ch-ua-platform-version",
        "sec-ch-ua-model",
        "sec-ch-ua-arch",
        "sec-ch-ua-bitness"
    )

    @Volatile
    private var registered = false

    @JvmStatic
    fun register() {
        if (registered || !MCEF.isInitialized()) return
        synchronized(this) {
            if (registered || !MCEF.isInitialized()) return
            MCEF.getClient().addRequestHandler(requestHandler)
            MCEF.getClient().addLoadHandler(loadHandler)
            registered = true
        }
    }

    private fun buildMobileUserAgent(originalUa: String?): String {
        val chromeVer = originalUa?.let { CHROME_VERSION_REGEX.find(it)?.groupValues?.getOrNull(1) }
            ?: FALLBACK_CHROME_VERSION
        return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVer Mobile Safari/537.36"
    }

    private val resourceRequestHandler = object : CefResourceRequestHandlerAdapter() {
        override fun onBeforeResourceLoad(
            browser: CefBrowser?,
            frame: CefFrame?,
            request: CefRequest?
        ): Boolean {
            if (browser !is MCEFBrowser || !browser.isMobileMode || request == null || request.isReadOnly) {
                return false
            }

            val currentUa = request.getHeaderByName("User-Agent")
            request.setHeaderByName("User-Agent", buildMobileUserAgent(currentUa), true)
            request.setHeaderByName("Sec-CH-UA-Mobile", "?1", true)
            request.setHeaderByName("Sec-CH-UA-Platform", "\"Android\"", true)

            val rt = request.resourceType
            val isFrameNav = rt == CefRequest.ResourceType.RT_MAIN_FRAME || rt == CefRequest.ResourceType.RT_SUB_FRAME
            val headers = HashMap<String, String>()
            request.getHeaderMap(headers)
            val existingLowerKeys = headers.keys.mapTo(HashSet()) { it.lowercase() }

            if (isFrameNav || existingLowerKeys.any { it in HIGH_ENTROPY_HINTS }) {
                request.setHeaderByName("Sec-CH-UA-Platform-Version", "\"14.0.0\"", true)
                request.setHeaderByName("Sec-CH-UA-Model", "\"Pixel 8\"", true)
                request.setHeaderByName("Sec-CH-UA-Arch", "\"\"", true)
                request.setHeaderByName("Sec-CH-UA-Bitness", "\"\"", true)
            }

            return false
        }
    }

    private val requestHandler = object : CefRequestHandlerAdapter() {
        override fun getResourceRequestHandler(
            browser: CefBrowser?,
            frame: CefFrame?,
            request: CefRequest?,
            isNavigation: Boolean,
            isDownload: Boolean,
            requestInitiator: String?,
            disableDefaultHandling: BoolRef?
        ): CefResourceRequestHandler? {
            return if (browser is MCEFBrowser && browser.isMobileMode) resourceRequestHandler else null
        }
    }

    private val loadHandler = object : CefLoadHandlerAdapter() {
        override fun onLoadingStateChange(
            browser: CefBrowser?,
            isLoading: Boolean,
            canGoBack: Boolean,
            canGoForward: Boolean
        ) {
            applyMobileJs(browser, browser?.mainFrame)
        }

        override fun onLoadStart(
            browser: CefBrowser?,
            frame: CefFrame?,
            transitionType: CefRequest.TransitionType?
        ) {
            applyMobileJs(browser, frame)
        }

        override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
            applyMobileJs(browser, frame)
        }
    }

    private fun applyMobileJs(browser: CefBrowser?, frame: CefFrame?) {
        if (browser !is MCEFBrowser || !browser.isMobileMode || frame == null || !frame.isValid) {
            return
        }
        frame.executeJavaScript(MOBILE_SPOOF_JS, frame.url ?: "", 0)
    }

    private const val MOBILE_SPOOF_JS = """
(function() {
  function ensureViewport() {
    try {
      if (window !== window.top || !document) return;
      if (document.querySelector('meta[name="viewport"]')) return;
      var target = document.head || document.documentElement;
      if (!target) return;
      var meta = document.createElement('meta');
      meta.name = 'viewport';
      meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
      target.appendChild(meta);
    } catch (_) {}
  }

  ensureViewport();
  if (!window.__wdMobileDomListenerAdded) {
    window.__wdMobileDomListenerAdded = true;
    try {
      document.addEventListener('DOMContentLoaded', ensureViewport, { once: true });
    } catch (_) {}
  }

  if (window.__wdMobileSpoofed) return;
  window.__wdMobileSpoofed = true;

  var origUa = '';
  try { origUa = navigator.userAgent || ''; } catch (_) {}
  var m = /Chrome\/(\d+(?:\.\d+)*)/.exec(origUa);
  var chromeVer = m ? m[1] : '126.0.0.0';
  var majorVer = chromeVer.split('.')[0] || '126';
  var mobileUa = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/' + chromeVer + ' Mobile Safari/537.36';
  var mobileAppVer = '5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/' + chromeVer + ' Mobile Safari/537.36';

  function defineNavProp(prop, getter) {
    try {
      Object.defineProperty(Navigator.prototype, prop, {
        get: getter,
        configurable: true
      });
    } catch (_) {
      try {
        Object.defineProperty(navigator, prop, {
          get: getter,
          configurable: true
        });
      } catch (_) {}
    }
  }

  defineNavProp('userAgent', function() { return mobileUa; });
  defineNavProp('appVersion', function() { return mobileAppVer; });
  defineNavProp('platform', function() { return 'Linux armv8l'; });
  defineNavProp('maxTouchPoints', function() { return 5; });

  try {
    var origUAD = navigator.userAgentData;
    var brands = (origUAD && Array.isArray(origUAD.brands))
      ? origUAD.brands.map(function(b) { return { brand: b.brand, version: b.version }; })
      : [
          { brand: 'Not/A)Brand', version: '8' },
          { brand: 'Chromium', version: majorVer },
          { brand: 'Google Chrome', version: majorVer }
        ];
    var fullVersionList = brands.map(function(b) {
      return { brand: b.brand, version: (b.brand === 'Not/A)Brand' ? '8.0.0.0' : chromeVer) };
    });
    var fakeUAD = {
      brands: brands,
      mobile: true,
      platform: 'Android',
      toJSON: function() {
        return { brands: brands, mobile: true, platform: 'Android' };
      },
      getHighEntropyValues: function(hints) {
        var res = { brands: brands, mobile: true, platform: 'Android' };
        if (Array.isArray(hints)) {
          for (var i = 0; i < hints.length; i++) {
            var h = hints[i];
            if (h === 'architecture') res.architecture = '';
            else if (h === 'bitness') res.bitness = '';
            else if (h === 'model') res.model = 'Pixel 8';
            else if (h === 'platformVersion') res.platformVersion = '14.0.0';
            else if (h === 'uaFullVersion') res.uaFullVersion = chromeVer;
            else if (h === 'fullVersionList') res.fullVersionList = fullVersionList;
            else if (h === 'wow64') res.wow64 = false;
            else if (h === 'formFactors') res.formFactors = ['Mobile'];
          }
        }
        return Promise.resolve(res);
      }
    };
    defineNavProp('userAgentData', function() { return fakeUAD; });
  } catch (_) {}

  var touchProps = ['ontouchstart', 'ontouchmove', 'ontouchend', 'ontouchcancel'];
  var touchTargets = [window];
  try { if (typeof Document !== 'undefined') touchTargets.push(Document.prototype); } catch (_) {}
  try { if (typeof HTMLElement !== 'undefined') touchTargets.push(HTMLElement.prototype); } catch (_) {}
  for (var t = 0; t < touchTargets.length; t++) {
    var tgt = touchTargets[t];
    for (var p = 0; p < touchProps.length; p++) {
      var tp = touchProps[p];
      try {
        if (!(tp in tgt)) {
          Object.defineProperty(tgt, tp, {
            value: null,
            writable: true,
            configurable: true
          });
        }
      } catch (_) {}
    }
  }

  try {
    if (typeof Document !== 'undefined' && typeof Document.prototype.createEvent === 'function') {
      var origCreateEvent = Document.prototype.createEvent;
      Document.prototype.createEvent = function(type) {
        try {
          return origCreateEvent.call(this, type);
        } catch (e) {
          if (typeof type === 'string' && type.toLowerCase() === 'touchevent') {
            return origCreateEvent.call(this, 'UIEvent');
          }
          throw e;
        }
      };
    }
  } catch (_) {}

  try {
    if (typeof window.matchMedia === 'function') {
      var origMatchMedia = window.matchMedia.bind(window);
      window.matchMedia = function(query) {
        var mql = origMatchMedia(query);
        if (typeof query !== 'string') return mql;
        var s = query.toLowerCase();
        var overrideVal = null;
        if (/\(\s*(?:any-)?pointer\s*:\s*coarse\s*\)/.test(s) || /\(\s*(?:any-)?hover\s*:\s*none\s*\)/.test(s)) {
          overrideVal = true;
        } else if (/\(\s*(?:any-)?pointer\s*:\s*fine\s*\)/.test(s) || /\(\s*(?:any-)?hover\s*:\s*hover\s*\)/.test(s)) {
          overrideVal = false;
        } else if (/\(\s*orientation\s*:\s*portrait\s*\)/.test(s)) {
          overrideVal = (window.innerHeight >= window.innerWidth);
        } else if (/\(\s*orientation\s*:\s*landscape\s*\)/.test(s)) {
          overrideVal = (window.innerWidth > window.innerHeight);
        }
        if (overrideVal === null) return mql;
        try {
          var wrapped = Object.create(mql);
          Object.defineProperty(wrapped, 'matches', { get: function() { return overrideVal; }, configurable: true });
          Object.defineProperty(wrapped, 'media', { get: function() { return mql.media; }, configurable: true });
          if (mql.addEventListener) wrapped.addEventListener = mql.addEventListener.bind(mql);
          if (mql.removeEventListener) wrapped.removeEventListener = mql.removeEventListener.bind(mql);
          if (mql.addListener) wrapped.addListener = mql.addListener.bind(mql);
          if (mql.removeListener) wrapped.removeListener = mql.removeListener.bind(mql);
          return wrapped;
        } catch (_) {
          return mql;
        }
      };
    }
  } catch (_) {}

  try {
    if (window.screen) {
      var scrProps = ['width', 'availWidth', 'height', 'availHeight'];
      for (var sp = 0; sp < scrProps.length; sp++) {
        (function(prop) {
          try {
            Object.defineProperty(window.screen, prop, {
              get: function() {
                return (prop === 'width' || prop === 'availWidth')
                  ? (window.innerWidth || 412)
                  : (window.innerHeight || 732);
              },
              configurable: true
            });
          } catch (_) {}
        })(scrProps[sp]);
      }
      if (!window.screen.orientation || window.screen.orientation.type !== 'portrait-primary') {
        var fakeOrientation = {
          type: 'portrait-primary',
          angle: 0,
          onchange: null,
          lock: function() { return Promise.resolve(); },
          unlock: function() {},
          addEventListener: function() {},
          removeEventListener: function() {},
          dispatchEvent: function() { return false; }
        };
        Object.defineProperty(window.screen, 'orientation', {
          get: function() { return fakeOrientation; },
          configurable: true
        });
      }
    }
  } catch (_) {}

  var wdTouchState = { active: false, target: null, offsetX: 0, offsetY: 0, win: window, startX: 0, startY: 0, curX: 0, curY: 0 };

  function deepElementFromPoint(x, y) {
    var doc = document;
    var win = window;
    var lx = x;
    var ly = y;
    var ox = 0;
    var oy = 0;
    var el = null;
    for (var depth = 0; depth < 8; depth++) {
      var hit = null;
      try { hit = doc.elementFromPoint(lx, ly); } catch (_) { hit = null; }
      if (!hit) break;
      while (hit && hit.shadowRoot && typeof hit.shadowRoot.elementFromPoint === 'function') {
        var shadowHit = null;
        try { shadowHit = hit.shadowRoot.elementFromPoint(lx, ly); } catch (_) { shadowHit = null; }
        if (!shadowHit || shadowHit === hit) break;
        hit = shadowHit;
      }
      el = hit;
      if (hit.tagName === 'IFRAME' || hit.tagName === 'FRAME') {
        try {
          var subDoc = hit.contentDocument;
          var subWin = hit.contentWindow;
          if (!subDoc || !subWin) break;
          var rect = hit.getBoundingClientRect();
          lx -= rect.left;
          ly -= rect.top;
          ox += rect.left;
          oy += rect.top;
          doc = subDoc;
          win = subWin;
          continue;
        } catch (_) {
          break;
        }
      }
      break;
    }
    return {
      el: el || document.body || document.documentElement,
      localX: lx,
      localY: ly,
      offsetX: ox,
      offsetY: oy,
      win: win
    };
  }

  function dispatchSyntheticPointer(type, target, lx, ly, win) {
    try {
      var w = win || window;
      var PE = w.PointerEvent || window.PointerEvent;
      if (!target || typeof PE === 'undefined') return false;
      var isUp = (type === 'pointerup' || type === 'pointercancel');
      var ev = new PE(type, {
        bubbles: true,
        cancelable: true,
        composed: true,
        view: w,
        pointerId: 1,
        width: 24,
        height: 24,
        pressure: isUp ? 0 : 0.5,
        pointerType: 'touch',
        isPrimary: true,
        clientX: lx,
        clientY: ly,
        screenX: lx,
        screenY: ly,
        button: isUp || type === 'pointerdown' ? 0 : -1,
        buttons: isUp ? 0 : 1
      });
      target.dispatchEvent(ev);
      return ev.defaultPrevented;
    } catch (_) {
      return false;
    }
  }

  function dispatchSyntheticTouch(type, target, lx, ly, includeTouches, win) {
    try {
      var w = win || window;
      var TE = w.TouchEvent || window.TouchEvent;
      var TC = w.Touch || window.Touch;
      if (!target || typeof TE === 'undefined' || typeof TC === 'undefined') return false;
      var t = new TC({
        identifier: 1,
        target: target,
        clientX: lx,
        clientY: ly,
        pageX: lx + (w.scrollX || 0),
        pageY: ly + (w.scrollY || 0),
        screenX: lx,
        screenY: ly,
        radiusX: 12,
        radiusY: 12,
        rotationAngle: 0,
        force: (type === 'touchend' || type === 'touchcancel') ? 0 : 1
      });
      var touches = includeTouches ? [t] : [];
      var ev = new TE(type, {
        cancelable: true,
        bubbles: true,
        composed: true,
        view: w,
        touches: touches,
        targetTouches: touches,
        changedTouches: [t]
      });
      target.dispatchEvent(ev);
      return ev.defaultPrevented;
    } catch (_) {
      return false;
    }
  }

  function scrollChain(el, deltaX, deltaY) {
    var cur = el || document.documentElement || document.body;
    var remX = deltaX;
    var remY = deltaY;
    while (cur) {
      var curDoc = cur.ownerDocument || document;
      var curWin = curDoc.defaultView || window;
      var isRoot = (cur === curDoc.documentElement || cur === curDoc.body);
      var st = null;
      try { st = isRoot ? null : curWin.getComputedStyle(cur); } catch (_) {}
      if (Math.abs(remY) > 0.01) {
        var canY = isRoot || (st && (st.overflowY === 'auto' || st.overflowY === 'scroll' || st.overflowY === 'overlay'));
        if (canY && cur.scrollHeight > cur.clientHeight + 1) {
          var targetY = isRoot ? (curDoc.scrollingElement || curDoc.documentElement || curDoc.body) : cur;
          var beforeY = targetY.scrollTop;
          targetY.scrollTop = beforeY + remY;
          remY -= (targetY.scrollTop - beforeY);
        }
      }
      if (Math.abs(remX) > 0.01) {
        var canX = isRoot || (st && (st.overflowX === 'auto' || st.overflowX === 'scroll' || st.overflowX === 'overlay'));
        if (canX && cur.scrollWidth > cur.clientWidth + 1) {
          var targetX = isRoot ? (curDoc.scrollingElement || curDoc.documentElement || curDoc.body) : cur;
          var beforeX = targetX.scrollLeft;
          targetX.scrollLeft = beforeX + remX;
          remX -= (targetX.scrollLeft - beforeX);
        }
      }
      if (Math.abs(remX) <= 0.01 && Math.abs(remY) <= 0.01) break;
      if (isRoot) {
        var frameEl = null;
        try { frameEl = curWin.frameElement; } catch (_) { frameEl = null; }
        if (frameEl) {
          cur = frameEl;
          continue;
        }
        break;
      }
      cur = cur.parentElement || curDoc.documentElement;
    }
    if (Math.abs(remY) > 0.01 || Math.abs(remX) > 0.01) {
      try { window.scrollBy(remX, remY); } catch (_) {}
    }
  }

  window.__wdTouchStart = function(x, y, touchOnly) {
    var hit = deepElementFromPoint(x, y);
    wdTouchState.active = true;
    wdTouchState.target = hit.el;
    wdTouchState.offsetX = hit.offsetX;
    wdTouchState.offsetY = hit.offsetY;
    wdTouchState.win = hit.win;
    wdTouchState.startX = x;
    wdTouchState.startY = y;
    wdTouchState.curX = x;
    wdTouchState.curY = y;
    if (!touchOnly) {
      dispatchSyntheticPointer('pointerdown', hit.el, hit.localX, hit.localY, hit.win);
    }
    dispatchSyntheticTouch('touchstart', hit.el, hit.localX, hit.localY, true, hit.win);
  };

  window.__wdTouchMove = function(x, y, dx, dy) {
    if (!wdTouchState.active) {
      window.__wdTouchStart(x, y, false);
    }
    wdTouchState.curX = x;
    wdTouchState.curY = y;
    var el = wdTouchState.target || document.body || document.documentElement;
    var lx = x - wdTouchState.offsetX;
    var ly = y - wdTouchState.offsetY;
    var win = wdTouchState.win || window;
    dispatchSyntheticPointer('pointermove', el, lx, ly, win);
    var canceled = dispatchSyntheticTouch('touchmove', el, lx, ly, true, win);
    if (!canceled) {
      scrollChain(el, -dx, -dy);
    }
  };

  window.__wdTouchEnd = function(x, y) {
    if (!wdTouchState.active) return;
    wdTouchState.active = false;
    var el = wdTouchState.target || document.body || document.documentElement;
    var lx = x - wdTouchState.offsetX;
    var ly = y - wdTouchState.offsetY;
    var win = wdTouchState.win || window;
    wdTouchState.target = null;
    wdTouchState.offsetX = 0;
    wdTouchState.offsetY = 0;
    wdTouchState.win = window;
    dispatchSyntheticPointer('pointerup', el, lx, ly, win);
    dispatchSyntheticTouch('touchend', el, lx, ly, false, win);
  };

  window.__wdTouchEndTap = function(x, y) {
    var hit = deepElementFromPoint(x, y);
    var el = wdTouchState.target || hit.el;
    var lx = x - (wdTouchState.active ? wdTouchState.offsetX : hit.offsetX);
    var ly = y - (wdTouchState.active ? wdTouchState.offsetY : hit.offsetY);
    var win = wdTouchState.active ? (wdTouchState.win || window) : hit.win;
    wdTouchState.active = false;
    wdTouchState.target = null;
    wdTouchState.offsetX = 0;
    wdTouchState.offsetY = 0;
    wdTouchState.win = window;
    dispatchSyntheticTouch('touchend', el, lx, ly, false, win);
    try {
      var cur = el;
      for (var d = 0; d < 6 && cur; d++) {
        var tag = cur.tagName;
        if (tag === 'INPUT' || tag === 'TEXTAREA' || cur.isContentEditable) {
          if (typeof cur.focus === 'function') cur.focus();
          break;
        }
        cur = cur.parentElement;
      }
    } catch (_) {}
  };

  window.__wdTouchTap = function(x, y) {
    var hit = deepElementFromPoint(x, y);
    dispatchSyntheticTouch('touchstart', hit.el, hit.localX, hit.localY, true, hit.win);
    dispatchSyntheticTouch('touchend', hit.el, hit.localX, hit.localY, false, hit.win);
  };

  window.__wdWheelScroll = function(x, y, deltaX, deltaY) {
    var hit = deepElementFromPoint(x, y);
    scrollChain(hit.el, deltaX, deltaY);
  };
})();
"""
}
