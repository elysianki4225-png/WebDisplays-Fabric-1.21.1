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
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsr;
import org.cef.callback.CefDragData;
import org.cef.event.CefKeyEvent;
import org.cef.event.CefMouseEvent;
import org.cef.event.CefMouseWheelEvent;
import org.cef.misc.CefCursorType;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLScancode;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.nio.ByteBuffer;

/**
 * An instance of an "Off-screen rendered" Chromium web browser.
 */
public class MCEFBrowser extends CefBrowserOsr {
    private final MCEFRenderer renderer;
    private final MCEFDragContext dragContext = new MCEFDragContext();
    private MCEFCursorChangeListener cursorChangeListener;
    private boolean browserControls = true;
    private int lastWidth = 0, lastHeight = 0;
    private int btnMask = 0;
    private boolean visible = true;
    private boolean closed = false;
    private volatile boolean mobileMode = false;

    protected ByteBuffer popupGraphics;
    protected Rectangle popupSize;
    protected boolean showPopup = false;
    protected boolean popupDrawn = false;

    public MCEFBrowser(MCEFClient client, String url, boolean transparent) {
        super(client.getHandle(), url, transparent, null);
        renderer = new MCEFRenderer(transparent);
        cursorChangeListener = (cefCursorID) -> setCursor(CefCursorType.fromId(cefCursorID));
        MCEF.incrementActiveBrowsers();

        if (RenderSystem.isOnRenderThread()) {
            renderer.initialize();
        } else {
            Minecraft.getInstance().execute(renderer::initialize);
        }
    }

    public MCEFRenderer getRenderer() {
        return renderer;
    }

    public MCEFCursorChangeListener getCursorChangeListener() {
        return cursorChangeListener;
    }

    public void setCursorChangeListener(MCEFCursorChangeListener cursorChangeListener) {
        this.cursorChangeListener = cursorChangeListener;
    }

    public boolean usingBrowserControls() {
        return browserControls;
    }

    public MCEFBrowser useBrowserControls(boolean browserControls) {
        this.browserControls = browserControls;
        return this;
    }

    public MCEFDragContext getDragContext() {
        return dragContext;
    }

    public boolean isMobileMode() {
        return mobileMode;
    }

    public void setMobileMode(boolean mobileMode) {
        this.mobileMode = mobileMode;
    }

    public void setVisible(boolean visible) {
        if (this.visible != visible && !closed) {
            this.visible = visible;
            setWindowVisibility(visible);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    // Popups
    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        super.onPopupShow(browser, show);
        showPopup = show;
        if (!show) popupDrawn = false;
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        super.onPopupSize(browser, size);
        popupSize = size;
        this.popupGraphics = ByteBuffer.allocateDirect(
                Math.max(1, size.width * size.height * 4)
        );
    }

    // Graphics
    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (dirtyRects == null || dirtyRects.length == 0 || width <= 0 || height <= 0 || buffer == null) {
            return;
        }

        if (renderer.getTextureID() == 0) {
            if (RenderSystem.isOnRenderThread()) {
                renderer.initialize();
            }
        }

        if (!popup) {
            if (lastWidth != width || lastHeight != height || renderer.getTextureID() == 0) {
                renderer.onPaint(buffer, width, height);
                lastWidth = width;
                lastHeight = height;
            } else {
                renderer.onPaintRects(buffer, width, height, dirtyRects);
                if ((popupDrawn || showPopup) && popupSize != null) {
                    if (!showPopup) {
                        renderer.onPaintRects(buffer, width, height, new Rectangle[]{popupSize});
                        popupGraphics = null;
                        popupSize = null;
                    } else if (popupDrawn && popupGraphics != null) {
                        renderer.onPaintSubBuffer(popupGraphics, popupSize.width, popupSize.x, popupSize.y, popupSize.width, popupSize.height);
                    }
                }
            }
        } else if (popupSize != null && popupSize.width > 0 && popupSize.height > 0) {
            int start = buffer.capacity();
            int end = 0;
            for (Rectangle dirtyRect : dirtyRects) {
                if (dirtyRect.width <= 0 || dirtyRect.height <= 0) {
                    continue;
                }
                int rectStart = (dirtyRect.x + (dirtyRect.y * popupSize.width)) << 2;
                if (rectStart < start) start = rectStart;

                int rectEnd = ((dirtyRect.x + dirtyRect.width) + ((dirtyRect.y + dirtyRect.height - 1) * popupSize.width)) << 2;
                if (rectEnd > end) end = rectEnd;
            }

            if (start < 0) start = 0;
            if (end > buffer.capacity()) end = buffer.capacity();

            if (end > start && this.popupGraphics != null && end <= this.popupGraphics.capacity()) {
                long addrFrom = MemoryUtil.memAddress(buffer);
                long addrTo = MemoryUtil.memAddress(popupGraphics);
                MemoryUtil.memCopy(addrFrom + start, addrTo + start, (end - start));
                renderer.onPaintSubBuffer(popupGraphics, popupSize.width, popupSize.x, popupSize.y, popupSize.width, popupSize.height);
            }

            popupDrawn = true;
        }
    }

    public void resize(int width, int height) {
        int clampedW = Math.max(1, width);
        int clampedH = Math.max(1, height);
        browser_rect_.setBounds(0, 0, clampedW, clampedH);
        wasResized(clampedW, clampedH);
    }

    // Inputs
    public void sendKeyPress(int keyCode, long scanCode, int modifiers) {
        if (browserControls) {
            if (modifiers == InputConstants.MOD_CONTROL) {
                if (keyCode == InputConstants.KEY_R) {
                    reload();
                    return;
                } else if (keyCode == SDLScancode.SDL_SCANCODE_EQUALS) {
                    if (getZoomLevel() < 9) setZoomLevel(getZoomLevel() + 1);
                    return;
                } else if (keyCode == SDLScancode.SDL_SCANCODE_MINUS) {
                    if (getZoomLevel() > -9) setZoomLevel(getZoomLevel() - 1);
                    return;
                } else if (keyCode == SDLScancode.SDL_SCANCODE_0) {
                    setZoomLevel(0);
                    return;
                }
            } else if (modifiers == InputConstants.MOD_ALT) {
                if (keyCode == SDLScancode.SDL_SCANCODE_LEFT && canGoBack()) {
                    goBack();
                    return;
                } else if (keyCode == SDLScancode.SDL_SCANCODE_RIGHT && canGoForward()) {
                    goForward();
                    return;
                }
            }
        }

        CefKeyEvent e = new CefKeyEvent(CefKeyEvent.KEY_PRESS, keyCode, (char) keyCode, modifiers);
        e.scancode = scanCode;
        sendKeyEvent(e);
    }

    public void sendKeyRelease(int keyCode, long scanCode, int modifiers) {
        if (browserControls) {
            if (modifiers == InputConstants.MOD_CONTROL) {
                if (keyCode == InputConstants.KEY_R) return;
                else if (keyCode == SDLScancode.SDL_SCANCODE_EQUALS) return;
                else if (keyCode == SDLScancode.SDL_SCANCODE_MINUS) return;
                else if (keyCode == SDLScancode.SDL_SCANCODE_0) return;
            } else if (modifiers == InputConstants.MOD_ALT) {
                if (keyCode == SDLScancode.SDL_SCANCODE_LEFT && canGoBack()) return;
                else if (keyCode == SDLScancode.SDL_SCANCODE_RIGHT && canGoForward()) return;
            }
        }

        CefKeyEvent e = new CefKeyEvent(CefKeyEvent.KEY_RELEASE, keyCode, (char) keyCode, modifiers);
        e.scancode = scanCode;
        sendKeyEvent(e);
    }

    public void sendKeyTyped(char c, int modifiers) {
        if (browserControls) {
            if (modifiers == InputConstants.MOD_CONTROL) {
                if ((int) c == InputConstants.KEY_R) return;
                else if ((int) c == SDLScancode.SDL_SCANCODE_EQUALS) return;
                else if ((int) c == SDLScancode.SDL_SCANCODE_MINUS) return;
                else if ((int) c == SDLScancode.SDL_SCANCODE_0) return;
            } else if (modifiers == InputConstants.MOD_ALT) {
                if ((int) c == SDLScancode.SDL_SCANCODE_LEFT && canGoBack()) return;
                else if ((int) c == SDLScancode.SDL_SCANCODE_RIGHT && canGoForward()) return;
            }
        }

        CefKeyEvent e = new CefKeyEvent(CefKeyEvent.KEY_TYPE, c, c, modifiers);
        sendKeyEvent(e);
    }

    public void sendMouseMove(int mouseX, int mouseY) {
        CefMouseEvent e = new CefMouseEvent(CefMouseEvent.MOUSE_MOVED, mouseX, mouseY, 0, 0, dragContext.getVirtualModifiers(btnMask));
        sendMouseEvent(e);

        if (dragContext.isDragging()) {
            this.dragTargetDragOver(new Point(mouseX, mouseY), 0, dragContext.getMask());
        }
    }

    public void sendMousePress(int mouseX, int mouseY, int button) {
        if (button == 1) button = 2;
        else if (button == 2) button = 1;

        if (button == 0) btnMask |= CefMouseEvent.BUTTON1_MASK;
        else if (button == 1) btnMask |= CefMouseEvent.BUTTON2_MASK;
        else if (button == 2) btnMask |= CefMouseEvent.BUTTON3_MASK;

        CefMouseEvent e = new CefMouseEvent(1, mouseX, mouseY, 1, button, btnMask);
        sendMouseEvent(e);
    }

    public void sendMouseRelease(int mouseX, int mouseY, int button) {
        if (button == 1) button = 2;
        else if (button == 2) button = 1;

        if (button == 0 && (btnMask & CefMouseEvent.BUTTON1_MASK) != 0) btnMask ^= CefMouseEvent.BUTTON1_MASK;
        else if (button == 1 && (btnMask & CefMouseEvent.BUTTON2_MASK) != 0) btnMask ^= CefMouseEvent.BUTTON2_MASK;
        else if (button == 2 && (btnMask & CefMouseEvent.BUTTON3_MASK) != 0) btnMask ^= CefMouseEvent.BUTTON3_MASK;

        CefMouseEvent e = new CefMouseEvent(0, mouseX, mouseY, 1, button, btnMask);
        sendMouseEvent(e);

        if (dragContext.isDragging() && button == 0) {
            finishDragging(mouseX, mouseY);
        }
    }

    public void sendMouseWheel(int mouseX, int mouseY, double amount, int modifiers) {
        if (browserControls) {
            if ((modifiers & InputConstants.MOD_CONTROL) != 0) {
                if (amount > 0) {
                    if (getZoomLevel() < 9) setZoomLevel(getZoomLevel() + 1);
                } else if (getZoomLevel() > -9) setZoomLevel(getZoomLevel() - 1);
                return;
            }
        }

        MCEFPlatform platform = MCEFPlatform.tryGetPlatform();
        if (platform == null || !platform.isMacOS()) {
            if (amount < 0) {
                amount = Math.floor(amount);
            } else {
                amount = Math.ceil(amount);
            }
            amount = amount * 3;
        }

        CefMouseWheelEvent e = new CefMouseWheelEvent(CefMouseWheelEvent.WHEEL_UNIT_SCROLL, mouseX, mouseY, amount, modifiers);
        sendMouseWheelEvent(e);
    }

    // Drag & drop
    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        dragContext.startDragging(dragData, mask);
        this.dragTargetDragEnter(dragContext.getDragData(), new Point(x, y), btnMask, dragContext.getMask());
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
        if (dragContext.updateCursor(operation))
            this.onCursorChange(this, dragContext.getVirtualCursor(dragContext.getActualCursor()));

        super.updateDragCursor(browser, operation);
    }

    public void startDragging(CefDragData dragData, int mask, int x, int y) {
        startDragging(this, dragData, mask, x, y);
    }

    public void finishDragging(int x, int y) {
        dragTargetDrop(new Point(x, y), btnMask);
        dragTargetDragLeave();
        dragContext.stopDragging();
        this.onCursorChange(this, dragContext.getActualCursor());
    }

    public void cancelDrag() {
        dragTargetDragLeave();
        dragContext.stopDragging();
        this.onCursorChange(this, dragContext.getActualCursor());
    }

    @Override
    public void close(boolean force) {
        if (!closed) {
            closed = true;
            MCEF.decrementActiveBrowsers();
            if (RenderSystem.isOnRenderThread()) {
                renderer.cleanup();
            } else {
                Minecraft.getInstance().execute(renderer::cleanup);
            }
            if (cursorChangeListener != null) {
                cursorChangeListener.onCursorChange(0);
            }
        }
        super.close(force);
    }

    public void close() {
        close(true);
    }

    @Override
    protected void finalize() throws Throwable {
        if (!closed) {
            closed = true;
            MCEF.decrementActiveBrowsers();
            Minecraft.getInstance().execute(renderer::cleanup);
        }
        super.finalize();
    }

    // Cursor handling
    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        cursorType = dragContext.getVirtualCursor(cursorType);
        cursorChangeListener.onCursorChange(cursorType);
        return super.onCursorChange(browser, cursorType);
    }

    public void setCursor(CefCursorType cursorType) {
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(() -> setCursor(cursorType));
            return;
        }
        if (cursorType == CefCursorType.NONE) {
            SDLMouse.SDL_HideCursor();
        } else {
            SDLMouse.SDL_ShowCursor();
            SDLMouse.SDL_SetCursor(MCEF.getSDLCursorHandle(cursorType));
        }
    }
}
