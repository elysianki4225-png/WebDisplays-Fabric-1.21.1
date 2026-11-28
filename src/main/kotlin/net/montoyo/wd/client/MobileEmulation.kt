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
    if (window.screen && (!window.screen.orientation || window.screen.orientation.type !== 'portrait-primary')) {
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
  } catch (_) {}
})();
"""
}
