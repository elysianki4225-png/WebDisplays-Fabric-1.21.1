/*
 *     MCEF (Minecraft Chromium Embedded Framework)
 *     Copyright (C) 2023 CinemaMod Group
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 */

package com.cinemamod.mcef;

import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefAuthCallback;
import org.cef.callback.CefCallback;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.handler.CefAudioHandler;
import org.cef.handler.CefContextMenuHandler;
import org.cef.handler.CefDisplayHandler;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefRequestHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.misc.BoolRef;
import org.cef.misc.CefAudioParameters;
import org.cef.misc.DataPointer;
import org.cef.network.CefRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A wrapper around {@link CefClient}
 */
public class MCEFClient implements CefLoadHandler, CefContextMenuHandler, CefDisplayHandler, CefAudioHandler, CefRequestHandler {
    private final CefClient handle;
    private final List<CefLoadHandler> loadHandlers = new CopyOnWriteArrayList<>();
    private final List<CefContextMenuHandler> contextMenuHandlers = new ArrayList<>();
    private final List<CefDisplayHandler> displayHandlers = new ArrayList<>();
    private final List<CefAudioHandler> audioHandlers = new ArrayList<>();
    private final List<CefRequestHandler> requestHandlers = new CopyOnWriteArrayList<>();

    public MCEFClient(CefClient cefClient) {
        handle = cefClient;
        cefClient.addLoadHandler(this);
        cefClient.addContextMenuHandler(this);
        cefClient.addDisplayHandler(this);
        cefClient.addAudioHandler(this);
        cefClient.addRequestHandler(this);
    }

    public CefClient getHandle() {
        return handle;
    }

    public void addLoadHandler(CefLoadHandler handler) {
        loadHandlers.add(handler);
    }

    @Override
    public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
        for (CefLoadHandler loadHandler : loadHandlers)
            loadHandler.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
    }

    @Override
    public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
        for (CefLoadHandler loadHandler : loadHandlers) loadHandler.onLoadStart(browser, frame, transitionType);
    }

    @Override
    public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        for (CefLoadHandler loadHandler : loadHandlers) loadHandler.onLoadEnd(browser, frame, httpStatusCode);
    }

    @Override
    public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
        for (CefLoadHandler loadHandler : loadHandlers)
            loadHandler.onLoadError(browser, frame, errorCode, errorText, failedUrl);
    }

    public void addContextMenuHandler(CefContextMenuHandler handler) {
        contextMenuHandlers.add(handler);
    }

    @Override
    public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
        for (CefContextMenuHandler contextMenuHandler : contextMenuHandlers)
            contextMenuHandler.onBeforeContextMenu(browser, frame, params, model);
    }

    @Override
    public boolean onContextMenuCommand(CefBrowser browser, CefFrame frame, CefContextMenuParams params, int commandId, int eventFlags) {
        for (CefContextMenuHandler contextMenuHandler : contextMenuHandlers)
            if (contextMenuHandler.onContextMenuCommand(browser, frame, params, commandId, eventFlags))
                return true;
        return false;
    }

    @Override
    public void onContextMenuDismissed(CefBrowser browser, CefFrame frame) {
        for (CefContextMenuHandler contextMenuHandler : contextMenuHandlers)
            contextMenuHandler.onContextMenuDismissed(browser, frame);
    }

    public void addDisplayHandler(CefDisplayHandler handler) {
        displayHandlers.add(handler);
    }

    @Override
    public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
        for (CefDisplayHandler displayHandler : displayHandlers) displayHandler.onAddressChange(browser, frame, url);
    }

    @Override
    public void onTitleChange(CefBrowser browser, String title) {
        for (CefDisplayHandler displayHandler : displayHandlers) displayHandler.onTitleChange(browser, title);
    }

    @Override
    public boolean onTooltip(CefBrowser browser, String text) {
        for (CefDisplayHandler displayHandler : displayHandlers)
            if (displayHandler.onTooltip(browser, text))
                return true;
        return false;
    }

    @Override
    public void onStatusMessage(CefBrowser browser, String value) {
        for (CefDisplayHandler displayHandler : displayHandlers) displayHandler.onStatusMessage(browser, value);
    }

    @Override
    public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message, String source, int line) {
        for (CefDisplayHandler displayHandler : displayHandlers)
            if (displayHandler.onConsoleMessage(browser, level, message, source, line))
                return true;
        return false;
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        for (CefDisplayHandler displayHandler : displayHandlers)
            if (displayHandler.onCursorChange(browser, cursorType))
                return true;
        return false;
    }
    
    public void addAudioHandler(CefAudioHandler handler) {
        audioHandlers.add(handler);
    }
    
    @Override
    public boolean getAudioParameters(CefBrowser browser, CefAudioParameters params) {
        for (CefAudioHandler audioHandler : audioHandlers) {
            if (audioHandler.getAudioParameters(browser, params))
                return true;
        }
        return false;
    }
    
    @Override
    public void onAudioStreamStarted(CefBrowser browser, CefAudioParameters params, int channels) {
        for (CefAudioHandler audioHandler : audioHandlers) {
            audioHandler.onAudioStreamStarted(browser, params, channels);
        }
    }
    
    @Override
    public void onAudioStreamPacket(CefBrowser browser, DataPointer data, int frames, long pts) {
        for (CefAudioHandler audioHandler : audioHandlers) {
            audioHandler.onAudioStreamPacket(browser, data, frames, pts);
        }
    }
    
    @Override
    public void onAudioStreamStopped(CefBrowser browser) {
        for (CefAudioHandler audioHandler : audioHandlers) {
            audioHandler.onAudioStreamStopped(browser);
        }
    }
    
    @Override
    public void onAudioStreamError(CefBrowser browser, String text) {
        for (CefAudioHandler audioHandler : audioHandlers) {
            audioHandler.onAudioStreamError(browser, text);
        }
        MCEF.getLogger().warn("An audio stream threw an error: " + text);
    }

    public void addRequestHandler(CefRequestHandler handler) {
        requestHandlers.add(handler);
    }

    @Override
    public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean user_gesture, boolean is_redirect) {
        for (CefRequestHandler requestHandler : requestHandlers) {
            if (requestHandler.onBeforeBrowse(browser, frame, request, user_gesture, is_redirect)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String target_url, boolean user_gesture) {
        for (CefRequestHandler requestHandler : requestHandlers) {
            if (requestHandler.onOpenURLFromTab(browser, frame, target_url, user_gesture)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request,
                                                               boolean isNavigation, boolean isDownload, String requestInitiator,
                                                               BoolRef disableDefaultHandling) {
        for (CefRequestHandler requestHandler : requestHandlers) {
            CefResourceRequestHandler rrh = requestHandler.getResourceRequestHandler(
                    browser, frame, request, isNavigation, isDownload, requestInitiator, disableDefaultHandling
            );
            if (rrh != null) {
                return rrh;
            }
        }
        return null;
    }

    @Override
    public boolean getAuthCredentials(CefBrowser browser, String origin_url, boolean isProxy, String host, int port,
                                      String realm, String scheme, CefAuthCallback callback) {
        for (CefRequestHandler requestHandler : requestHandlers) {
            if (requestHandler.getAuthCredentials(browser, origin_url, isProxy, host, port, realm, scheme, callback)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCertificateError(CefBrowser browser, ErrorCode cert_error, String request_url, CefCallback callback) {
        for (CefRequestHandler requestHandler : requestHandlers) {
            if (requestHandler.onCertificateError(browser, cert_error, request_url, callback)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRenderProcessTerminated(CefBrowser browser, TerminationStatus status) {
        for (CefRequestHandler requestHandler : requestHandlers) {
            requestHandler.onRenderProcessTerminated(browser, status);
        }
    }
}
