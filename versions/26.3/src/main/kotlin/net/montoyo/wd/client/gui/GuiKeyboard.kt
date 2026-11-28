/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.client.gui.controls.Button
import net.montoyo.wd.client.gui.controls.Label
import net.montoyo.wd.client.gui.loading.FillControl
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.TypeData
import net.montoyo.wd.utilities.Util
import org.lwjgl.sdl.SDLMouse
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.reflect.Method
import kotlin.math.max

@Environment(EnvType.CLIENT)
class GuiKeyboard() : WDScreen(Component.nullToEmpty(null)) {
    private lateinit var tes: TileEntityScreen
    private lateinit var side: BlockSide
    private val evStack = ArrayList<TypeData>()
    private lateinit var kbPos: BlockPos
    private var showWarning = true
    private var warningPanelBounds: IntArray? = null
    private var closeRequested = false

    @FillControl
    private lateinit var lblInfo: Label

    @FillControl
    private lateinit var btnOk: Button

    constructor(tes: TileEntityScreen, side: BlockSide, kbPos: BlockPos) : this() {
        this.tes = tes
        this.side = side
        this.kbPos = kbPos
    }

    override fun addLoadCustomVariables(vars: MutableMap<String, Double>) {
        vars["showWarning"] = if (showWarning) 1.0 else 0.0
    }

    override fun init() {
        super.init()

        val spServer = minecraft.singleplayerServer
        showWarning = if (spServer != null && !spServer.isPublished) {
            false
        } else {
            !hasUserReadWarning()
        }

        loadFrom(Identifier.fromNamespaceAndPath("webdisplays", "gui/kb_right.json"))

        if (showWarning) {
            var maxLabelW = 0
            var totalH = 0

            for (ctrl in controls) {
                if (ctrl !== lblInfo && ctrl is Label) {
                    if (ctrl.getWidth() > maxLabelW) maxLabelW = ctrl.getWidth()
                    totalH += ctrl.getHeight() + 2
                    ctrl.setPos((width - ctrl.getWidth()) / 2, 0)
                }
            }

            val btnW = max(120, maxLabelW / 2)
            btnOk.setWidth(btnW)
            btnOk.setPos((width - btnW) / 2, 0)
            totalH += btnOk.getHeight() + 6

            var y = (height - totalH) / 2
            for (ctrl in controls) {
                if (ctrl !== lblInfo) {
                    if (ctrl === btnOk) y += 6
                    ctrl.setPos(ctrl.getX(), y)
                    y += ctrl.getHeight() + 2
                }
            }

            val padX = 16
            val pw = maxLabelW + padX * 2
            val ph = totalH + 26
            warningPanelBounds = intArrayOf((width - pw) / 2, (height - ph) / 2 - 4, pw, ph)
        } else {
            warningPanelBounds = null
            grabMouseKeepingScreen()
        }

        defaultBackground = showWarning
        syncTicks = 5
        showVivecraftKeyboard(true)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        val bounds = warningPanelBounds
        if (showWarning && bounds != null) {
            WDTheme.drawGlassPanel(graphics, bounds[0], bounds[1], bounds[2], bounds[3])
            graphics.fill(bounds[0] + 1, bounds[1] + 1, bounds[0] + bounds[2] - 1, bounds[1] + 3, WDTheme.DANGER)
        } else if (!showWarning && lblInfo.isVisible()) {
            val pillW = lblInfo.getWidth() + 16
            val pillH = 18
            val px = lblInfo.getX() - 8
            val py = lblInfo.getY() - 5
            graphics.fill(px, py, px + pillW, py + pillH, 0xCC10151E.toInt())
            WDTheme.drawBorder(graphics, px, py, pillW, pillH, 0xFF2E384B.toInt())
            graphics.fill(px + 1, py + 4, px + 3, py + pillH - 4, WDTheme.ACCENT)
        }
        super.extractRenderState(graphics, mouseX, mouseY, ptt)
    }

    override fun onClose() {
        showVivecraftKeyboard(false)
        super.onClose()
    }

    override fun tick() {
        super.tick()

        if (closeRequested && minecraft.gui.screen() === this) {
            closeRequested = false
            minecraft.gui.setScreen(null)
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (quitOnEscape && event.key() == InputConstants.KEY_ESCAPE) {
            closeRequested = true
        }
        addKey(TypeData(TypeData.Action.PRESS, event.key(), event.modifiers(), event.key()))
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        addKey(TypeData(TypeData.Action.TYPE, event.codepoint(), currentModifiersMask(), 0))
        return super.charTyped(event)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        addKey(TypeData(TypeData.Action.RELEASE, event.key(), event.modifiers(), event.key()))
        return super.keyReleased(event)
    }

    private fun addKey(data: TypeData) {
        tes.type(side, "[${WebDisplays.GSON.toJson(data)}]", kbPos)
        evStack.add(data)
        if (!evStack.isEmpty() && !syncRequested()) requestSync()
    }

    override fun sync() {
        if (evStack.isNotEmpty()) {
            WDNetworkRegistry.INSTANCE.sendToServer(
                C2SMessageScreenCtrl.type(tes, side, WebDisplays.GSON.toJson(evStack), kbPos)
            )
            evStack.clear()
        }
    }

    @GuiSubscribe
    fun onClick(ev: Button.ClickEvent) {
        if (showWarning && ev.getSource() === btnOk) {
            writeUserAcknowledge()

            for (ctrl in controls) {
                if (ctrl is Label) {
                    ctrl.setVisible(!ctrl.isVisible())
                }
            }

            btnOk.setDisabled(true)
            btnOk.setVisible(false)
            showWarning = false
            defaultBackground = false
            grabMouseKeepingScreen()
        }
    }

    private fun grabMouseKeepingScreen() {
        val mouse = minecraft.mouseHandler
        if (!minecraft.isWindowActive || mouse.isMouseGrabbed) return

        mouse.mouseGrabbed = true
        SDLMouse.SDL_SetWindowRelativeMouseMode(minecraft.window.handle(), true)
        mouse.setIgnoreFirstMove()
        minecraft.missTime = 10000
    }

    private fun hasUserReadWarning(): Boolean {
        try {
            val f = File(FabricLoader.getInstance().gameDir.toString(), WARNING_FNAME)
            if (f.exists()) {
                val br = BufferedReader(FileReader(f))
                val str = br.readLine()
                Util.silentClose(br)
                return str != null && str.trim().equals("read", ignoreCase = true)
            }
        } catch (t: Throwable) {
            Log.warningEx("Can't know if user has already read the warning", t)
        }
        return false
    }

    private fun writeUserAcknowledge() {
        try {
            val f = File(FabricLoader.getInstance().gameDir.toString(), WARNING_FNAME)
            val bw = BufferedWriter(FileWriter(f))
            bw.write("read\n")
            Util.silentClose(bw)
        } catch (t: Throwable) {
            Log.warningEx("Can't write that the user read the warning", t)
        }
    }

    override fun isForBlock(bp: BlockPos, side: BlockSide): Boolean {
        return bp == kbPos || (bp == tes.blockPos && side == this.side)
    }

    companion object {
        private const val WARNING_FNAME = "wd_keyboard_warning.txt"
        private val viveGetPlayer: Method?
        private val viveSetOverlayShowing: Method?

        init {
            var setOverlay: Method? = null
            var getPlayer: Method? = null

            val layouts = arrayOf(
                arrayOf("org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler", "org.vivecraft.client_vr.gameplay.VRPlayer"),
                arrayOf("org.vivecraft.gameplay.screenhandlers.KeyboardHandler", "org.vivecraft.gameplay.VRPlayer")
            )

            for (layout in layouts) {
                try {
                    val handler = Class.forName(layout[0])
                    getPlayer = Class.forName(layout[1]).getMethod("get")
                    setOverlay = handler.getMethod("setOverlayShowing", Boolean::class.javaPrimitiveType)
                    break
                } catch (_: Throwable) {
                    getPlayer = null
                    setOverlay = null
                }
            }

            if (setOverlay == null && FabricLoader.getInstance().isModLoaded("vivecraft")) {
                Log.warning("Vivecraft is loaded but its keyboard overlay methods could not be found.")
            }

            viveGetPlayer = getPlayer
            viveSetOverlayShowing = setOverlay
        }

        private fun showVivecraftKeyboard(showing: Boolean) {
            val setOverlay = viveSetOverlayShowing ?: return
            val getPlayer = viveGetPlayer ?: return
            try {
                if (getPlayer.invoke(null) == null) return
                setOverlay.invoke(null, showing)
            } catch (_: Throwable) {
            }
        }
    }
}
