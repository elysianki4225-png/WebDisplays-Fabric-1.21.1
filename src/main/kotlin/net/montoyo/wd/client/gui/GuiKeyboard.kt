/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.client.gui.controls.Button
import net.montoyo.wd.client.gui.controls.Control
import net.montoyo.wd.client.gui.controls.Label
import net.montoyo.wd.client.gui.loading.FillControl
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.TypeData
import net.montoyo.wd.utilities.Util
import org.lwjgl.glfw.GLFW
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.reflect.Method

@Environment(EnvType.CLIENT)
class GuiKeyboard() : WDScreen(Component.nullToEmpty(null)) {
    private lateinit var tes: TileEntityScreen
    private lateinit var side: BlockSide
    private val evStack = ArrayList<TypeData>()
    private lateinit var kbPos: BlockPos
    private var showWarning = true
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

        val spServer = minecraft!!.singleplayerServer
        showWarning = if (spServer != null && !spServer.isPublished) {
            false
        } else {
            !hasUserReadWarning()
        }

        loadFrom(ResourceLocation.fromNamespaceAndPath("webdisplays", "gui/kb_right.json"))

        if (showWarning) {
            var maxLabelW = 0
            var totalH = 0

            for (ctrl in controls) {
                if (ctrl !== lblInfo && ctrl is Label) {
                    if (ctrl.getWidth() > maxLabelW) maxLabelW = ctrl.getWidth()
                    totalH += ctrl.getHeight()
                    ctrl.setPos((width - ctrl.getWidth()) / 2, 0)
                }
            }

            btnOk.setWidth(maxLabelW)
            btnOk.setPos((width - maxLabelW) / 2, 0)
            totalH += btnOk.getHeight()

            var y = (height - totalH) / 2
            for (ctrl in controls) {
                if (ctrl !== lblInfo) {
                    ctrl.setPos(ctrl.getX(), y)
                    y += ctrl.getHeight()
                }
            }
        } else {
            grabMouseKeepingScreen()
            tes.getScreen(side)?.browser?.setFocus(true)
        }

        defaultBackground = showWarning
        syncTicks = 5
        showVivecraftKeyboard(true)
    }

    override fun onClose() {
        showVivecraftKeyboard(false)
        super.onClose()
    }

    override fun tick() {
        super.tick()

        if (closeRequested && minecraft!!.screen === this) {
            closeRequested = false
            minecraft!!.setScreen(null)
        }
    }

    private fun normalizeKeyCode(keyCode: Int): Int {
        return if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) 10 else keyCode
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (quitOnEscape && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeRequested = true
        }
        if (!showWarning) {
            addKey(TypeData(TypeData.Action.PRESS, normalizeKeyCode(keyCode), modifiers, scanCode))
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (!showWarning) {
            addKey(TypeData(TypeData.Action.TYPE, codePoint.code, modifiers, 0))
        }
        return super.charTyped(codePoint, modifiers)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!showWarning) {
            addKey(TypeData(TypeData.Action.RELEASE, normalizeKeyCode(keyCode), modifiers, scanCode))
        }
        return super.keyReleased(keyCode, scanCode, modifiers)
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
            minecraft!!.isWindowActive = true
            grabMouseKeepingScreen()
            tes.getScreen(side)?.browser?.setFocus(true)
        }
    }

    private fun grabMouseKeepingScreen() {
        val mc = minecraft!!
        val mouse = mc.mouseHandler

        if (!mc.isWindowActive || mouse.isMouseGrabbed) return

        mouse.mouseGrabbed = true
        GLFW.glfwSetInputMode(mc.window.window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED)
        mouse.setIgnoreFirstMove()
        mc.missTime = 10000
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
