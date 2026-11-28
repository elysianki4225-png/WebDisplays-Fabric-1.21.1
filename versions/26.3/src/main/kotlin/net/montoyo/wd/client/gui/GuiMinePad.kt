/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import com.cinemamod.mcef.MCEFBrowser
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.EnvType.CLIENT
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.client.ClientProxy
import net.montoyo.wd.client.gui.controls.WDEditBox
import net.montoyo.wd.config.CommonConfig
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.item.ItemMinePad2
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.server_bound.C2SMessageMinepadUrl
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.NbtCompat
import net.montoyo.wd.utilities.Util
import org.cef.misc.CefCursorType
import org.lwjgl.sdl.SDLKeyboard
import org.lwjgl.sdl.SDLScancode
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Environment(CLIENT)
class GuiMinePad() : WDScreen(Component.nullToEmpty(null)) {
    private var pad: ClientProxy.PadData? = null
    private var vx = 0.0
    private var vy = 0.0
    private var vw = 0.0
    private var vh = 0.0

    private var screenX = 0
    private var screenY = 0
    private var screenW = 0
    private var screenH = 0
    private var addrX = 0
    private var addrY = 0
    private var addrW = 0
    private var addrH = 0
    private var clearX = 0
    private var clearW = 0
    private var goX = 0
    private var goW = 0
    private var navY = 0
    private var navH = 0
    private val navBtnX = IntArray(5)
    private val navBtnW = IntArray(5)

    private var urlBox: WDEditBox? = null
    private var lastSyncedUrl = ""

    private var touchDown = false
    private var touchSwiped = false
    private var touchStartX = 0.0
    private var touchStartY = 0.0
    private var touchLastX = 0.0
    private var touchLastY = 0.0
    private var touchDownRx = 0
    private var touchDownRy = 0

    private var trueWidth = 0
    private var trueHeight = 0

    constructor(pad: ClientProxy.PadData?) : this() {
        this.pad = pad
    }

    override fun init() {
        trueWidth = width
        trueHeight = height

        val p = pad
        if (p != null && p.mobile) {
            val phoneRatio = 9.0 / 16.0
            val chromeH = BEZEL_TOP + STATUS_H + ADDR_H + NAV_H + HOME_IND_H + BEZEL_BOTTOM
            val maxH = max(100.0, trueHeight - chromeH - 16.0)
            val maxW = max(80.0, trueWidth - 36.0)
            vh = min(maxH, maxW / phoneRatio)
            vw = vh * phoneRatio

            val totalScreenH = STATUS_H + ADDR_H + vh.toInt() + NAV_H + HOME_IND_H
            val phoneH = BEZEL_TOP + totalScreenH + BEZEL_BOTTOM
            val phoneTop = (trueHeight - phoneH) / 2

            vx = (trueWidth - vw) / 2.0
            vy = (phoneTop + BEZEL_TOP + STATUS_H + ADDR_H).toDouble()

            screenX = vx.toInt()
            screenY = phoneTop + BEZEL_TOP
            screenW = vw.toInt()
            screenH = totalScreenH

            addrX = screenX + 4
            addrY = screenY + STATUS_H + 2
            addrW = screenW - 8
            addrH = ADDR_H - 4

            goW = 14
            clearW = 12
            goX = addrX + addrW - goW - 1
            clearX = goX - clearW

            val boxX = addrX + 13
            val boxW = max(30, clearX - boxX - 2)
            val box = WDEditBox(font, boxX, addrY + 3, boxW, 11, Component.nullToEmpty(""))
            box.setBordered(false)
            box.setMaxLength(65535)
            box.setTextColor(0xFFF8FAFC.toInt())
            box.setTextColorUneditable(0xFF94A3B8.toInt())
            lastSyncedUrl = getCurrentUrl()
            box.value = lastSyncedUrl
            box.isFocused = false
            urlBox = box

            navY = (vy + vh).toInt()
            navH = NAV_H
            for (i in 0 until 5) {
                val x0 = screenX + (i * screenW) / 5
                val x1 = screenX + ((i + 1) * screenW) / 5
                navBtnX[i] = x0
                navBtnW[i] = max(1, x1 - x0)
            }
        } else {
            vw = width.toDouble() - 32.0f
            vh = vw / WebDisplays.PAD_RATIO
            if (vh > height - 40.0) {
                vh = max(80.0, height - 40.0)
                vw = vh * WebDisplays.PAD_RATIO
            }
            vx = (width - vw) / 2.0
            vy = (height - vh) / 2.0
            urlBox = null
        }

        super.init()

        val view = p?.view
        if (view is MCEFBrowser) {
            view.setFocus(true)
            view.setCursor(CefCursorType.fromId(p.activeCursor))
            view.setCursorChangeListener { id ->
                p.activeCursor = id
                view.setCursor(CefCursorType.fromId(id))
            }
        }
    }

    private fun getCurrentUrl(): String {
        val u = pad?.view?.url
        if (!u.isNullOrEmpty()) return u
        return CommonConfig.Browser.homepage
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        val bx = vx.toInt()
        val by = vy.toInt()
        val bw = vw.toInt()
        val bh = vh.toInt()

        val p = pad
        if (p != null && p.mobile) {
            val fx = screenX - BEZEL_SIDE
            val fy = screenY - BEZEL_TOP
            val fw = screenW + BEZEL_SIDE * 2
            val fh = screenH + BEZEL_TOP + BEZEL_BOTTOM

            graphics.fill(fx - 2, fy + 26, fx, fy + 34, 0xFF64748B.toInt())
            graphics.fill(fx - 2, fy + 40, fx, fy + 56, 0xFF64748B.toInt())
            graphics.fill(fx - 2, fy + 60, fx, fy + 76, 0xFF64748B.toInt())
            graphics.fill(fx + fw, fy + 44, fx + fw + 2, fy + 64, 0xFF64748B.toInt())

            WDTheme.drawDropShadow(graphics, fx, fy, fw, fh)
            graphics.fill(fx, fy, fx + fw, fy + fh, 0xFF2B3240.toInt())
            WDTheme.drawBorder(graphics, fx, fy, fw, fh, 0xFF475266.toInt())
            graphics.fill(fx + 1, fy + 1, fx + fw - 1, fy + 2, 0x33FFFFFF)

            graphics.fill(screenX - 1, screenY - 1, screenX + screenW + 1, screenY + screenH + 1, 0xFF06090E.toInt())
            graphics.fill(screenX, screenY, screenX + screenW, screenY + screenH, 0xFF0F172A.toInt())

            val timeStr = LocalTime.now().format(CLOCK_FMT)
            val clockX = screenX + 4
            graphics.text(font, timeStr, clockX, screenY + 3, 0xFFE2E8F0.toInt(), false)

            val batX = screenX + screenW - 16
            val batY = screenY + 4
            graphics.fill(batX, batY, batX + 11, batY + 6, 0xFF94A3B8.toInt())
            graphics.fill(batX + 1, batY + 1, batX + 10, batY + 5, 0xFF0F172A.toInt())
            graphics.fill(batX + 2, batY + 2, batX + 9, batY + 4, 0xFF22C55E.toInt())
            graphics.fill(batX + 11, batY + 2, batX + 12, batY + 4, 0xFF94A3B8.toInt())

            val sigX = batX - 11
            graphics.fill(sigX, batY + 4, sigX + 2, batY + 6, 0xFFE2E8F0.toInt())
            graphics.fill(sigX + 3, batY + 3, sigX + 5, batY + 6, 0xFFE2E8F0.toInt())
            graphics.fill(sigX + 6, batY + 1, sigX + 8, batY + 6, 0xFFE2E8F0.toInt())

            val leftLimit = clockX + font.width(timeStr) + 4
            val rightLimit = sigX - 4
            val availIslandW = rightLimit - leftLimit
            val islandW = min(36, min(availIslandW, max(16, screenW / 5)))
            val islandY = screenY + 3
            if (islandW >= 14) {
                val idealCenterX = screenX + (screenW - islandW) / 2
                val islandX = max(leftLimit, min(rightLimit - islandW, idealCenterX))
                graphics.fill(islandX, islandY, islandX + islandW, islandY + 8, 0xFF05070B.toInt())
                graphics.fill(islandX + 1, islandY - 1, islandX + islandW - 1, islandY + 9, 0xFF05070B.toInt())
                val camX = islandX + islandW - 7
                graphics.fill(camX, islandY + 2, camX + 4, islandY + 6, 0xFF0F172A.toInt())
                graphics.fill(camX + 1, islandY + 3, camX + 3, islandY + 5, 0xFF1D4ED8.toInt())
                graphics.fill(camX + 1, islandY + 3, camX + 2, islandY + 4, 0xFF60A5FA.toInt())
            }

            val box = urlBox
            val addrFocus = box != null && box.isFocused
            val addrHover = inRect(mouseX.toDouble(), mouseY.toDouble(), addrX, addrY, addrW, addrH)
            val addrBorder = (if (addrFocus) 0xFF38BDF8 else if (addrHover) 0xFF475569 else 0xFF293548).toInt()
            val addrBg = (if (addrFocus) 0xFF162032 else 0xFF1E293B).toInt()
            graphics.fill(addrX, addrY, addrX + addrW, addrY + addrH, addrBorder)
            graphics.fill(addrX + 1, addrY + 1, addrX + addrW - 1, addrY + addrH - 1, addrBg)

            val curUrl = if (addrFocus && box != null) box.value else getCurrentUrl()
            val isHttps = curUrl.startsWith("https://")
            val iconColor = (if (isHttps) 0xFF22C55E else 0xFF60A5FA).toInt()
            graphics.fill(addrX + 5, addrY + 6, addrX + 10, addrY + 10, iconColor)
            graphics.fill(addrX + 6, addrY + 4, addrX + 9, addrY + 6, iconColor)
            graphics.fill(addrX + 7, addrY + 5, addrX + 8, addrY + 6, addrBg)

            if (addrFocus && box != null) {
                box.extractRenderState(graphics, mouseX, mouseY, ptt)

                val clrHover = inRect(mouseX.toDouble(), mouseY.toDouble(), clearX, addrY, clearW, addrH)
                graphics.text(font, "x", clearX + 3, addrY + 3, (if (clrHover) 0xFFF87171 else 0xFF94A3B8).toInt(), false)

                val goHover = inRect(mouseX.toDouble(), mouseY.toDouble(), goX, addrY, goW, addrH)
                graphics.fill(goX, addrY + 2, goX + goW - 1, addrY + addrH - 2, (if (goHover) 0xFF0284C7 else 0xFF2563EB).toInt())
                graphics.text(font, ">", goX + 4, addrY + 3, 0xFFFFFFFF.toInt(), false)
            } else {
                val maxLabelW = addrW - 22
                var display = curUrl
                if (display.isEmpty()) {
                    display = I18n.get("webdisplays.smartphone.url_hint")
                    graphics.text(font, font.plainSubstrByWidth(display, maxLabelW), addrX + 14, addrY + 4, 0xFF64748B.toInt(), false)
                } else {
                    if (font.width(display) > maxLabelW) {
                        display = font.plainSubstrByWidth(display, max(8, maxLabelW - font.width("..."))) + "..."
                    }
                    graphics.text(font, display, addrX + 14, addrY + 4, 0xFFE2E8F0.toInt(), false)
                }
            }

            graphics.fill(screenX, by - 1, screenX + screenW, by, 0xFF1E293B.toInt())
            val view = p.view
            if (view is MCEFBrowser && view.renderer.isReady) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, view.renderer.textureIdentifier, bx, by, 0.0f, 0.0f, bw, bh, bw, bh)
            }

            graphics.fill(screenX, navY, screenX + screenW, navY + navH, 0xFF0B1120.toInt())
            graphics.fill(screenX, navY, screenX + screenW, navY + 1, 0xFF1E293B.toInt())

            val canBack = p.view?.canGoBack() == true
            val canFwd = p.view?.canGoForward() == true

            for (i in 0 until 5) {
                val nbx = navBtnX[i]
                val nbw = navBtnW[i]
                val enabled = (i != 0 || canBack) && (i != 1 || canFwd)
                val hover = enabled && inRect(mouseX.toDouble(), mouseY.toDouble(), nbx, navY + 2, nbw, navH - 4)
                if (hover) {
                    graphics.fill(nbx + 2, navY + 2, nbx + nbw - 2, navY + navH - 2, 0xFF1E293B.toInt())
                }
                val col = (if (!enabled) 0xFF475569 else if (hover) 0xFF38BDF8 else 0xFFCBD5E1).toInt()
                drawNavIcon(graphics, i, nbx + nbw / 2, navY + navH / 2, col)
            }

            val indY = navY + navH
            graphics.fill(screenX, indY, screenX + screenW, indY + HOME_IND_H, 0xFF0B1120.toInt())
            val pillW = max(26, screenW / 3)
            val pillX = screenX + (screenW - pillW) / 2
            graphics.fill(pillX, indY + 2, pillX + pillW, indY + 4, 0x9994A3B8.toInt())

            val hoverTip = getHoveredNavTooltip(mouseX, mouseY)
            if (hoverTip != null) {
                drawTooltip(graphics, listOf(hoverTip), mouseX, mouseY)
            }
        } else {
            val frameX = bx - 12
            val frameY = by - 14
            val frameW = bw + 24
            val frameH = bh + 30

            WDTheme.drawDropShadow(graphics, frameX, frameY, frameW, frameH)
            graphics.fill(frameX, frameY, frameX + frameW, frameY + frameH, 0xEC181E29.toInt())
            WDTheme.drawBorder(graphics, frameX, frameY, frameW, frameH, 0xFF3C485E.toInt())
            graphics.fill(frameX + 1, frameY + 1, frameX + frameW - 1, frameY + 2, 0x28FFFFFF)

            val camX = bx + bw / 2 - 2
            val camY = frameY + 5
            graphics.fill(camX, camY, camX + 4, camY + 4, 0xFF0A0D12.toInt())
            graphics.fill(camX + 1, camY + 1, camX + 3, camY + 3, 0xFF1F2937.toInt())
            graphics.fill(camX + 1, camY + 1, camX + 2, camY + 2, 0xFF38BDF8.toInt())

            graphics.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF070A0F.toInt())

            val view = p?.view
            if (view is MCEFBrowser && view.renderer.isReady) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, view.renderer.textureIdentifier, bx, by, 0.0f, 0.0f, bw, bh, bw, bh)
            }

            val pillW = min(44, max(24, bw / 8))
            val pillX = bx + (bw - pillW) / 2
            val pillY = by + bh + 7
            graphics.fill(pillX, pillY, pillX + pillW, pillY + 2, 0x889AA7BC.toInt())
        }
    }

    private fun drawNavIcon(g: GuiGraphicsExtractor, idx: Int, cx: Int, cy: Int, col: Int) {
        when (idx) {
            0 -> {
                g.fill(cx - 3, cy, cx - 1, cy + 1, col)
                g.fill(cx - 2, cy - 1, cx, cy, col)
                g.fill(cx - 1, cy - 2, cx + 1, cy - 1, col)
                g.fill(cx, cy - 3, cx + 2, cy - 2, col)
                g.fill(cx - 2, cy + 1, cx, cy + 2, col)
                g.fill(cx - 1, cy + 2, cx + 1, cy + 3, col)
                g.fill(cx, cy + 3, cx + 2, cy + 4, col)
            }
            1 -> {
                g.fill(cx + 1, cy, cx + 3, cy + 1, col)
                g.fill(cx, cy - 1, cx + 2, cy, col)
                g.fill(cx - 1, cy - 2, cx + 1, cy - 1, col)
                g.fill(cx - 2, cy - 3, cx, cy - 2, col)
                g.fill(cx, cy + 1, cx + 2, cy + 2, col)
                g.fill(cx - 1, cy + 2, cx + 1, cy + 3, col)
                g.fill(cx - 2, cy + 3, cx, cy + 4, col)
            }
            2 -> {
                g.fill(cx - 2, cy - 3, cx + 2, cy - 2, col)
                g.fill(cx - 3, cy - 2, cx - 2, cy + 2, col)
                g.fill(cx - 2, cy + 2, cx + 2, cy + 3, col)
                g.fill(cx + 2, cy + 1, cx + 3, cy + 2, col)
                g.fill(cx + 1, cy - 4, cx + 3, cy - 1, col)
                g.fill(cx + 3, cy - 3, cx + 4, cy - 2, col)
            }
            3 -> {
                g.fill(cx, cy - 4, cx + 1, cy - 3, col)
                g.fill(cx - 1, cy - 3, cx + 2, cy - 2, col)
                g.fill(cx - 2, cy - 2, cx + 3, cy - 1, col)
                g.fill(cx - 3, cy - 1, cx + 4, cy, col)
                g.fill(cx - 2, cy, cx + 3, cy + 3, col)
                g.fill(cx - 1, cy + 1, cx + 1, cy + 3, 0xFF0B1120.toInt())
            }
            4 -> {
                g.fill(cx - 4, cy - 1, cx - 2, cy + 1, col)
                g.fill(cx - 1, cy - 1, cx + 1, cy + 1, col)
                g.fill(cx + 2, cy - 1, cx + 4, cy + 1, col)
            }
        }
    }

    private fun getHoveredNavTooltip(mouseX: Int, mouseY: Int): String? {
        if (!inRect(mouseX.toDouble(), mouseY.toDouble(), screenX, navY, screenW, navH)) return null
        for (i in 0 until 5) {
            if (inRect(mouseX.toDouble(), mouseY.toDouble(), navBtnX[i], navY, navBtnW[i], navH)) {
                return when (i) {
                    0 -> I18n.get("webdisplays.smartphone.back")
                    1 -> I18n.get("webdisplays.smartphone.forward")
                    2 -> I18n.get("webdisplays.smartphone.reload")
                    3 -> I18n.get("webdisplays.smartphone.home")
                    4 -> I18n.get("webdisplays.smartphone.menu")
                    else -> null
                }
            }
        }
        return null
    }

    private fun findSmartphoneStack(): ItemStack {
        val player = minecraft.player ?: return ItemStack.EMPTY

        var fallback = ItemStack.EMPTY
        for (hand in InteractionHand.entries) {
            val stack = player.getItemInHand(hand)
            val padItem = stack.item
            if (padItem is ItemMinePad2 && padItem.isMobileLayout()) {
                val id = pad?.id
                if (id != null) {
                    val tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                    val itemUuid = NbtCompat.getUUID(tag, "PadID")
                    if (id == itemUuid) return stack
                }
                if (fallback.isEmpty) fallback = stack
            }
        }

        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            val padItem = stack.item
            if (padItem is ItemMinePad2 && padItem.isMobileLayout()) {
                val id = pad?.id
                if (id != null) {
                    val tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                    val itemUuid = NbtCompat.getUUID(tag, "PadID")
                    if (id == itemUuid) return stack
                }
                if (fallback.isEmpty) fallback = stack
            }
        }
        return fallback
    }

    private fun navigateToUrl(rawUrl: String?) {
        if (rawUrl == null) return
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return

        try {
            TileEntityScreen.url(trimmed)
        } catch (_: IOException) {
            return
        }

        val url = Util.resolveUrlOrSearch(trimmed)
        val target = findSmartphoneStack()
        if (!target.isEmpty) {
            val tag = target.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            val uuid = NbtCompat.getUUID(tag, "PadID") ?: (pad?.id ?: UUID.randomUUID())
            NbtCompat.putUUID(tag, "PadID", uuid)
            tag.putString("PadURL", url)
            tag.putBoolean("PadMobile", true)
            target.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageMinepadUrl(uuid, url))
        }

        pad?.view?.loadURL(WebDisplays.applyBlacklist(url) ?: url)
        lastSyncedUrl = url
        urlBox?.value = url
    }

    private fun openUrlGuiFromPhone() {
        val target = findSmartphoneStack()
        minecraft.gui.setScreen(GuiSetURL2(target, getCurrentUrl(), pad))
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val p = pad
        val box = urlBox
        if (p != null && p.mobile && box != null && box.isFocused) {
            val key = event.key()
            if (key == InputConstants.KEY_ESCAPE) {
                box.isFocused = false
                return true
            }
            if (key == InputConstants.KEY_RETURN || key == SDLScancode.SDL_SCANCODE_RETURN || key == SDLScancode.SDL_SCANCODE_KP_ENTER) {
                navigateToUrl(box.value)
                box.isFocused = false
                return true
            }
            box.keyPressed(event)
            return true
        }
        return keyChanged(event, true) || super.keyPressed(event)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        val p = pad
        val box = urlBox
        if (p != null && p.mobile && box != null && box.isFocused) {
            box.keyReleased(event)
            return true
        }
        return keyChanged(event, false) || super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val p = pad
        val box = urlBox
        if (p != null && p.mobile && box != null && box.isFocused) {
            box.charTyped(event)
            return true
        }
        val view = p?.view
        if (view != null) {
            val browser = view as MCEFBrowser
            browser.setFocus(true)
            browser.sendKeyTyped(event.codepoint().toChar(), currentModifiersMask())
            return true
        }
        return super.charTyped(event)
    }

    fun keyChanged(event: KeyEvent, pressed: Boolean): Boolean {
        var keyCode = event.key()
        val scanCode = event.key()
        val modifiers = event.modifiers()

        if (keyCode == InputConstants.KEY_ESCAPE) {
            minecraft.gui.setScreen(null)
            return true
        }

        val iuKey = InputConstants.getKey(event)
        val keystr = iuKey.displayName.string
        if (keystr.isEmpty()) return false

        var key = keystr[keystr.length - 1]
        if (
            keystr == "Enter" ||
            keyCode == InputConstants.KEY_RETURN ||
            keyCode == InputConstants.KEY_NUMPADENTER ||
            keyCode == SDLScancode.SDL_SCANCODE_RETURN ||
            keyCode == SDLScancode.SDL_SCANCODE_KP_ENTER
        ) {
            keyCode = 10
            key = '\n'
        }

        val view = pad?.view
        if (view != null) {
            val browser = view as MCEFBrowser
            browser.setFocus(true)
            if (pressed) browser.sendKeyPress(keyCode, scanCode.toLong(), modifiers)
            else browser.sendKeyRelease(keyCode, scanCode.toLong(), modifiers)

            if (pressed && key == '\n' && modifiers != 0) {
                browser.sendKeyTyped('\r', modifiers)
            }
            return true
        }

        return false
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        super.mouseMoved(mouseX, mouseY)
        if (!touchDown) {
            mouse(-1, false, mouseX.toInt(), mouseY.toInt(), 0.0)
        }
    }

    private fun inViewport(sx: Double, sy: Double): Boolean {
        return vw > 0.0 && vh > 0.0 && sx >= vx && sx <= vx + vw && sy >= vy && sy <= vy + vh
    }

    private fun toClampedBrowserX(sx: Double, resX: Int): Int {
        val mx = ((sx - vx) / vw).coerceIn(0.0, 1.0)
        return (mx * resX).toInt().coerceIn(0, max(0, resX - 1))
    }

    private fun toClampedBrowserY(sy: Double, resY: Int): Int {
        val my = ((sy - vy) / vh).coerceIn(0.0, 1.0)
        return (my * resY).toInt().coerceIn(0, max(0, resY - 1))
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val p = pad
        val box = urlBox

        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && p != null && p.mobile) {
            if (inRect(mouseX, mouseY, addrX, addrY, addrW, addrH)) {
                if (box != null) {
                    if (box.isFocused) {
                        if (inRect(mouseX, mouseY, clearX, addrY, clearW, addrH)) {
                            box.value = ""
                            box.isFocused = true
                            p.view?.setFocus(false)
                            return true
                        }
                        if (inRect(mouseX, mouseY, goX, addrY, goW, addrH)) {
                            navigateToUrl(box.value)
                            box.isFocused = false
                            return true
                        }
                    } else {
                        box.value = getCurrentUrl()
                        box.isFocused = true
                    }
                    box.mouseClicked(event, doubleClick)
                    p.view?.setFocus(false)
                }
                return true
            }

            if (box != null && box.isFocused) {
                box.isFocused = false
            }

            if (inRect(mouseX, mouseY, screenX, navY, screenW, navH)) {
                for (i in 0 until 5) {
                    if (inRect(mouseX, mouseY, navBtnX[i], navY, navBtnW[i], navH)) {
                        when (i) {
                            0 -> if (p.view?.canGoBack() == true) p.view?.goBack()
                            1 -> if (p.view?.canGoForward() == true) p.view?.goForward()
                            2 -> p.view?.reload()
                            3 -> navigateToUrl(CommonConfig.Browser.homepage)
                            4 -> openUrlGuiFromPhone()
                        }
                        return true
                    }
                }
            }

            if (inViewport(mouseX, mouseY)) {
                touchDown = true
                touchSwiped = false
                touchStartX = mouseX
                touchStartY = mouseY
                touchLastX = mouseX
                touchLastY = mouseY
                touchDownRx = toClampedBrowserX(mouseX, p.resX)
                touchDownRy = toClampedBrowserY(mouseY, p.resY)
                (p.view as? MCEFBrowser)?.let { browser ->
                    browser.setFocus(true)
                    browser.sendMouseMove(touchDownRx, touchDownRy)
                }
                return true
            }
        }

        mouse(toCefMouseButton(event.button()), true, mouseX.toInt(), mouseY.toInt(), 0.0)
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val p = pad
        val box = urlBox
        if (p != null && p.mobile && box != null && box.isFocused) {
            if (box.mouseDragged(event, dragX, dragY)) {
                return true
            }
        }

        if (p != null && p.mobile && touchDown && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            val browser = p.view as? MCEFBrowser
            val rx = toClampedBrowserX(mouseX, p.resX)
            val ry = toClampedBrowserY(mouseY, p.resY)
            if (!touchSwiped && hypot(mouseX - touchStartX, mouseY - touchStartY) > 8.0) {
                touchSwiped = true
                browser?.executeJavaScript(
                    "window.__wdTouchStart && window.__wdTouchStart($touchDownRx, $touchDownRy);",
                    browser.url ?: "",
                    0
                )
            }
            if (touchSwiped) {
                val dx = (mouseX - touchLastX) / vw * p.resX
                val dy = (mouseY - touchLastY) / vh * p.resY
                touchLastX = mouseX
                touchLastY = mouseY
                if (dx != 0.0 || dy != 0.0) {
                    browser?.executeJavaScript(
                        "window.__wdTouchMove && window.__wdTouchMove($rx, $ry, $dx, $dy);",
                        browser.url ?: "",
                        0
                    )
                }
            }
            return true
        }

        if (p != null && !p.mobile && inViewport(mouseX, mouseY)) {
            (p.view as? MCEFBrowser)?.sendMouseMove(toClampedBrowserX(mouseX, p.resX), toClampedBrowserY(mouseY, p.resY))
        }

        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val p = pad
        val box = urlBox
        if (p != null && p.mobile && box != null && box.isFocused) {
            box.mouseReleased(event)
        }

        if (p != null && p.mobile && touchDown && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            touchDown = false
            val browser = p.view as? MCEFBrowser
            val rx = toClampedBrowserX(mouseX, p.resX)
            val ry = toClampedBrowserY(mouseY, p.resY)
            if (touchSwiped) {
                touchSwiped = false
                browser?.executeJavaScript(
                    "window.__wdTouchEnd && window.__wdTouchEnd($rx, $ry);",
                    browser.url ?: "",
                    0
                )
            } else {
                browser?.executeJavaScript(
                    "window.__wdTouchTap && window.__wdTouchTap($touchDownRx, $touchDownRy);",
                    browser.url ?: "",
                    0
                )
                browser?.sendMouseMove(touchDownRx, touchDownRy)
                browser?.sendMousePress(touchDownRx, touchDownRy, 0)
                browser?.sendMouseRelease(touchDownRx, touchDownRy, 0)
                browser?.setFocus(true)
            }
            return true
        }

        mouse(toCefMouseButton(event.button()), false, mouseX.toInt(), mouseY.toInt(), 0.0)
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, amount: Double): Boolean {
        val p = pad
        val view = p?.view
        if (view is MCEFBrowser && inViewport(mouseX, mouseY)) {
            val sx = toClampedBrowserX(mouseX, p.resX)
            val sy = toClampedBrowserY(mouseY, p.resY)
            val mc = Minecraft.getInstance()
            val mods = if (mc.hasControlDown() && !mc.hasAltDown() && !mc.hasShiftDown()) InputConstants.MOD_CONTROL else 0
            if (amount != 0.0) {
                view.sendMouseWheel(sx, sy, amount, mods)
            }
            if (p.mobile && horizontalAmount != 0.0) {
                view.executeJavaScript(
                    "window.__wdWheelScroll && window.__wdWheelScroll($sx, $sy, ${-horizontalAmount * 48.0}, 0);",
                    view.url ?: "",
                    0
                )
            }
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount)
    }

    fun mouse(btn: Int, pressed: Boolean, sx: Int, sy: Int, scrollAmount: Double) {
        val p = pad ?: return
        val view = p.view ?: return
        if (vw <= 0.0 || vh <= 0.0) return

        val mx = (sx - vx) / vw
        val my = (sy - vy) / vh
        val isRelease = (btn != -1 && !pressed)

        if (isRelease || (mx in 0.0..1.0 && my in 0.0..1.0)) {
            val rx = (mx.coerceIn(0.0, 1.0) * p.resX).toInt().coerceIn(0, max(0, p.resX - 1))
            val ry = (my.coerceIn(0.0, 1.0) * p.resY).toInt().coerceIn(0, max(0, p.resY - 1))
            val browser = view as MCEFBrowser

            if (btn == -1) browser.sendMouseMove(rx, ry)
            else if (pressed) browser.sendMousePress(rx, ry, btn)
            else browser.sendMouseRelease(rx, ry, btn)
            if (!isRelease || (mx in 0.0..1.0 && my in 0.0..1.0)) {
                view.setFocus(true)
            }
        }
    }

    override fun tick() {
        val p = pad
        if (p == null || p.view == null) {
            minecraft.gui.setScreen(null)
            return
        }

        val box = urlBox
        if (p.mobile && box != null && !box.isFocused) {
            val cur = getCurrentUrl()
            if (cur != lastSyncedUrl) {
                lastSyncedUrl = cur
                box.value = cur
            }
        }
    }

    override fun isForBlock(bp: BlockPos, side: BlockSide): Boolean = false

    override fun removed() {
        touchDown = false
        touchSwiped = false
        super.removed()
        val p = pad
        val view = p?.view
        if (view is MCEFBrowser) {
            view.setCursor(CefCursorType.POINTER)
            view.setCursorChangeListener { cursor -> p.activeCursor = cursor }
        }
    }

    override fun onClose() {
        super.onClose()
        removed()
    }

    companion object {
        private val CLOCK_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private const val BEZEL_SIDE = 6
        private const val BEZEL_TOP = 6
        private const val BEZEL_BOTTOM = 6
        private const val STATUS_H = 14
        private const val ADDR_H = 19
        private const val NAV_H = 20
        private const val HOME_IND_H = 7

        private fun toCefMouseButton(mcButton: Int): Int {
            if (mcButton == InputConstants.MOUSE_BUTTON_LEFT) return 0
            if (mcButton == InputConstants.MOUSE_BUTTON_RIGHT) return 1
            if (mcButton == InputConstants.MOUSE_BUTTON_MIDDLE) return 2
            return mcButton
        }

        private fun inRect(mx: Double, my: Double, x: Int, y: Int, w: Int, h: Int): Boolean {
            return mx >= x && mx < x + w && my >= y && my < y + h
        }

        @JvmStatic
        fun getChar(keyCode: Int, scanCode: Int): Optional<Char> {
            var keystr = SDLKeyboard.SDL_GetKeyName(SDLKeyboard.SDL_GetKeyFromScancode(keyCode, 0.toShort(), false)) ?: "\u0000"
            if (keyCode == InputConstants.KEY_RETURN) keystr = "\n"
            if (keystr.isEmpty()) return Optional.empty()
            return Optional.of(keystr[keystr.length - 1])
        }
    }
}
