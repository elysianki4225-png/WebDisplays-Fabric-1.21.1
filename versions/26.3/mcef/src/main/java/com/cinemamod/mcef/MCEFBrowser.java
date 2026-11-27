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

import com.cinemamod.mcef.listeners.MCEFCursorChangeListener;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsr;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.input.CefMouseEvent;
import org.cef.misc.CefCursorType;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import static org.lwjgl.glfw.GLFW.*;

/**
 * An OSR (offscreen rendering) browser that can be rendered in Minecraft.
 */
public class MCEFBrowser extends CefBrowserOsr {
    private final MCEFRenderer renderer;
    private final Rectangle viewSize = new Rectangle(0, 0, 1, 1);
    private final Rectangle popupSize = new Rectangle(0, 0, 0, 0);
    private boolean popupVisible = false;
    private boolean cursorHidden = false;
    private MCEFCursorChangeListener cursorChangeListener;

    private long lastRenderedTime = System.nanoTime();
    private MCEFDragContext dragContext;
    private int dragOperations = 0;

    protected MCEFBrowser(MCEFClient client, String url, boolean transparent, CefRequestContext context) {
        super(client.getHandle(), url, transparent, context);
        renderer = new MCEFRenderer(transparent);
        if (RenderSystem.isOnRenderThread()) {
            renderer.initialize();
        } else {
            Minecraft.getInstance().execute(renderer::initialize);
        }
        createImmediately();
    }

    public MCEFRenderer getRenderer() {
        return renderer;
    }

    public MCEFDragContext getDragContext() {
        return dragContext;
    }

    public MCEFCursorChangeListener getCursorChangeListener() {
        return cursorChangeListener;
    }

    public void setCursorChangeListener(MCEFCursorChangeListener cursorChangeListener) {
        this.cursorChangeListener = cursorChangeListener;
    }

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        return viewSize;
    }

    public void setVisible(boolean visible) {
        wasHidden(!visible);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        return false;
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        if (!show) {
            popupVisible = false;
            popupSize.setBounds(0, 0, 0, 0);
            invalidate();
        } else {
            popupVisible = true;
        }
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        if (size.width <= 0 || size.height <= 0) {
            return;
        }
        popupSize.setBounds(size);
    }

    public void resize(int width, int height) {
        if (width <= 0) {
            width = 1;
        }
        if (height <= 0) {
            height = 1;
        }
        viewSize.setBounds(0, 0, width, height);
        wasResized(width, height);
    }

    public long getLastRenderedTime() {
        return lastRenderedTime;
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (width <= 0 || height <= 0 || buffer == null) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            ByteBuffer copy = ByteBuffer.allocateDirect(buffer.remaining());
            copy.put(buffer.duplicate());
            copy.flip();
            Minecraft.getInstance().execute(() -> applyPaint(popup, dirtyRects, copy, width, height));
            return;
        }
        applyPaint(popup, dirtyRects, buffer, width, height);
    }

    private void applyPaint(boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (!popup) {
            renderer.onPaintRects(buffer, width, height, dirtyRects);
        } else if (popupVisible && popupSize.width > 0 && popupSize.height > 0) {
            renderer.onPaintSubBuffer(buffer, width, popupSize.x, popupSize.y, popupSize.width, popupSize.height);
        }
        lastRenderedTime = System.nanoTime();
    }

    public void setCursor(CefCursorType cursorType) {
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        if (cursorType == CefCursorType.NONE) {
            if (!cursorHidden) {
                GLFW.glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_HIDDEN);
                cursorHidden = true;
            }
        } else {
            if (cursorHidden) {
                GLFW.glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                cursorHidden = false;
            }
            GLFW.glfwSetCursor(windowHandle, MCEF.getGLFWCursorHandle(cursorType));
        }
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        if (cursorChangeListener != null) {
            cursorChangeListener.onCursorChange(cursorType);
        } else {
            setCursor(CefCursorType.fromId(cursorType));
        }
        return true;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        dragContext = new MCEFDragContext(dragData.clone(), mask);
        return true;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
        dragOperations = operation;
    }

    public void sendKeyPress(int keyCode, long scanCode, int modifiers) {
        if (modifiers == GLFW_MOD_CONTROL) {
            if (keyCode == GLFW_KEY_R) {
                reload();
                return;
            } else if (keyCode == GLFW_KEY_EQUAL) {
                setZoomLevel(getZoomLevel() + 0.5);
                return;
            } else if (keyCode == GLFW_KEY_MINUS) {
                setZoomLevel(getZoomLevel() - 0.5);
                return;
            } else if (keyCode == GLFW_KEY_0) {
                setZoomLevel(0.0);
                return;
            }
        } else if (modifiers == GLFW_MOD_ALT) {
            if (keyCode == GLFW_KEY_LEFT && canGoBack()) {
                goBack();
                return;
            } else if (keyCode == GLFW_KEY_RIGHT && canGoForward()) {
                goForward();
                return;
            }
        }

        if (keyCode == GLFW_KEY_BACKSPACE) {
            sendKeyTyped('\b', modifiers);
            return;
        }

        KeyEvent e = new KeyEvent(KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, keyCode, '\0', scanCode);
        sendKeyEvent(e);
    }

    public void sendKeyRelease(int keyCode, long scanCode, int modifiers) {
        if (modifiers == GLFW_MOD_CONTROL) {
            if (keyCode == GLFW_KEY_R || keyCode == GLFW_KEY_EQUAL || keyCode == GLFW_KEY_MINUS || keyCode == GLFW_KEY_0) {
                return;
            }
        } else if (modifiers == GLFW_MOD_ALT) {
            if ((keyCode == GLFW_KEY_LEFT && canGoBack()) || (keyCode == GLFW_KEY_RIGHT && canGoForward())) {
                return;
            }
        }

        KeyEvent e = new KeyEvent(KeyEvent.KEY_RELEASED, System.currentTimeMillis(), modifiers, keyCode, '\0', scanCode);
        sendKeyEvent(e);
    }

    public void sendKeyTyped(char c, int modifiers) {
        if (modifiers == GLFW_MOD_CONTROL) {
            if ((int) c == GLFW_KEY_R || (int) c == GLFW_KEY_EQUAL || (int) c == GLFW_KEY_MINUS || (int) c == GLFW_KEY_0) {
                return;
            }
        } else if (modifiers == GLFW_MOD_ALT) {
            if (((int) c == GLFW_KEY_LEFT && canGoBack()) || ((int) c == GLFW_KEY_RIGHT && canGoForward())) {
                return;
            }
        }

        KeyEvent e = new KeyEvent(KeyEvent.KEY_TYPED, System.currentTimeMillis(), modifiers, 0, c, 0);
        sendKeyEvent(e);
    }

    public void sendMouseMove(int mouseX, int mouseY) {
        CefMouseEvent e = new CefMouseEvent(CefMouseEvent.MOUSE_MOVED, mouseX, mouseY, 0, CefMouseEvent.BUTTON_NONE, 0);
        sendMouseEvent(e);

        if (dragContext != null) {
            dragTargetDragOver(new Point(mouseX, mouseY), dragContext.getModifiers(), dragOperations);
        }
    }

    public void sendMousePress(int mouseX, int mouseY, int button) {
        int btnMask = CefMouseEvent.getModifiersByButton(button);
        CefMouseEvent e = new CefMouseEvent(GLFW_PRESS, mouseX, mouseY, 1, button, btnMask);
        sendMouseEvent(e);
    }

    public void sendMouseRelease(int mouseX, int mouseY, int button) {
        if (dragContext != null) {
            dragTargetDrop(new Point(mouseX, mouseY), dragContext.getModifiers());
            dragTargetDragLeave();
            dragContext = null;
        }

        int btnMask = CefMouseEvent.getModifiersByButton(button);
        CefMouseEvent e = new CefMouseEvent(GLFW_RELEASE, mouseX, mouseY, 1, button, btnMask);
        sendMouseEvent(e);
    }

    public void sendMouseWheel(int mouseX, int mouseY, double amount, int modifiers) {
        if ((modifiers & GLFW_MOD_CONTROL) != 0) {
            if (amount > 0) {
                setZoomLevel(getZoomLevel() + 0.5);
            } else {
                setZoomLevel(getZoomLevel() - 0.5);
            }
            return;
        }

        int delta = (int) (amount * 120);
        CefMouseEvent e = new CefMouseEvent(CefMouseEvent.MOUSE_WHEEL, mouseX, mouseY, delta, 0, modifiers);
        sendMouseEvent(e);
    }

    public void startDrag(int x, int y) {
        if (dragContext != null) {
            dragTargetDragEnter(dragContext.getDragData(), new Point(x, y), dragContext.getModifiers(), dragOperations);
        }
    }

    public void endDrag(int x, int y) {
        if (dragContext != null) {
            dragTargetDragLeave();
            dragSourceEndedAt(new Point(x, y), dragOperations);
            dragSourceSystemDragEnded();
            dragContext = null;
        }
    }

    public CompletableFuture<BufferedImage> createScreenshot(boolean includeAlphaChannel) {
        return super.createScreenshot(includeAlphaChannel);
    }

    public void setBackgroundColor(int r, int g, int b, int a) {
        throw new UnsupportedOperationException();
    }

    public void close() {
        MCEF.unregisterBrowser(this);
        if (RenderSystem.isOnRenderThread()) {
            renderer.cleanup();
        } else {
            Minecraft.getInstance().execute(renderer::cleanup);
        }
        setCloseAllowed();
        close(true);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (renderer.isReady()) {
            Minecraft.getInstance().execute(renderer::cleanup);
        }
    }
}
