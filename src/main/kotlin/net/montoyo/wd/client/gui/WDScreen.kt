/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import com.google.gson.JsonObject
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.client.gui.controls.Container
import net.montoyo.wd.client.gui.controls.Control
import net.montoyo.wd.client.gui.controls.Event
import net.montoyo.wd.client.gui.loading.FillControl
import net.montoyo.wd.client.gui.loading.GuiLoader
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.server_bound.C2SMessageACQuery
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Bounds
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.NameUUIDPair
import org.lwjgl.glfw.GLFW
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.stream.Collectors

abstract class WDScreen(component: Component) : Screen(component) {
    protected val controls = ArrayList<Control>()
    protected val postDrawList = ArrayList<Control>()
    private val eventMap = HashMap<Class<out Event>, Method>()
    protected var quitOnEscape = true
    protected var defaultBackground = true
    protected var syncTicks = 40
    private var syncTicksLeft = -1

    init {
        for (m in javaClass.methods) {
            if (m.getAnnotation(GuiSubscribe::class.java) != null) {
                if (!Modifier.isPublic(m.modifiers)) {
                    throw RuntimeException("Found non public @GuiSubscribe")
                }

                val params = m.parameterTypes
                if (params.size != 1 || !Event::class.java.isAssignableFrom(params[0])) {
                    throw RuntimeException("Invalid parameters for @GuiSubscribe")
                }

                @Suppress("UNCHECKED_CAST")
                eventMap[params[0] as Class<out Event>] = m
            }
        }
    }

    protected fun <T : Control> addControl(ctrl: T): T {
        controls.add(ctrl)
        return ctrl
    }

    fun screen2DisplayX(x: Int): Int {
        val ret = x.toDouble() / width.toDouble() * minecraft!!.window.width.toDouble()
        return ret.toInt()
    }

    fun screen2DisplayY(y: Int): Int {
        val ret = y.toDouble() / height.toDouble() * minecraft!!.window.height.toDouble()
        return ret.toInt()
    }

    fun display2ScreenX(x: Int): Int {
        val ret = x.toDouble() / minecraft!!.window.width.toDouble() * width.toDouble()
        return ret.toInt()
    }

    fun display2ScreenY(y: Int): Int {
        val ret = y.toDouble() / minecraft!!.window.height.toDouble() * height.toDouble()
        return ret.toInt()
    }

    protected fun centerControls() {
        val bounds: Bounds = Control.findBounds(controls)

        val diffX = (width - bounds.maxX - bounds.minX) / 2
        val diffY = (height - bounds.maxY - bounds.minY) / 2

        for (ctrl in controls) {
            val x = ctrl.getX()
            val y = ctrl.getY()
            ctrl.setPos(x + diffX, y + diffY)
        }
    }

    override fun render(poseStack: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {
        if (defaultBackground) renderBackground(poseStack, mouseX, mouseY, ptt)

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)

        for (ctrl in controls) ctrl.draw(poseStack, mouseX, mouseY, ptt)

        for (ctrl in postDrawList) ctrl.postDraw(poseStack, mouseX, mouseY, ptt)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        var typed = false
        for (ctrl in controls) {
            typed = typed || ctrl.keyTyped(codePoint.code, modifiers)
        }
        return typed
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        var clicked = false
        var clickedEl: Control? = null

        for (ctrl in controls) {
            clicked = ctrl.mouseClicked(mouseX, mouseY, button)
            if (clicked) {
                clickedEl = ctrl
                break
            }
        }

        if (clicked) {
            for (control in controls) {
                if (control !== clickedEl) control.unfocus()
            }
        }

        return clicked
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        var mouseReleased = false
        for (ctrl in controls) {
            mouseReleased = mouseReleased || ctrl.mouseReleased(mouseX, mouseY, button)
        }
        return mouseReleased
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        var dragged = false
        for (ctrl in controls) {
            dragged = dragged || ctrl.mouseClickMove(mouseX, mouseY, button, dragX, dragY)
        }
        return dragged
    }

    override fun init() {
        controls.clear()
        postDrawList.clear()
        CURRENT_SCREEN = this
    }

    override fun onClose() {
        if (syncTicksLeft >= 0) {
            sync()
            syncTicksLeft = -1
        }

        for (ctrl in controls) ctrl.destroy()
        CURRENT_SCREEN = null
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, delta: Double): Boolean {
        var scrolled = false
        for (ctrl in controls) {
            scrolled = scrolled || ctrl.mouseScroll(mouseX, mouseY, delta)
        }
        return scrolled
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        for (ctrl in controls) {
            ctrl.mouseMove(mouseX, mouseY)
        }
        super.mouseMoved(mouseX, mouseY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        var down = false
        for (ctrl in controls) {
            if (ctrl.keyDown(keyCode, scanCode, modifiers)) down = true
        }

        if (this is GuiKeyboard) {
            return down
        }

        if (quitOnEscape && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraft!!.setScreen(null)
            return true
        }

        return down || super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        var up = false
        for (ctrl in controls) {
            if (ctrl.keyUp(keyCode, scanCode, modifiers)) up = true
        }
        return up || super.keyReleased(keyCode, scanCode, modifiers)
    }

    open fun actionPerformed(ev: Event): Any? {
        val m = eventMap[ev.javaClass]
        if (m != null) {
            try {
                return m.invoke(this, ev)
            } catch (e: IllegalAccessException) {
                Log.errorEx("Access to event %s of screen %s is denied", e, ev.javaClass.simpleName, javaClass.simpleName)
            } catch (e: InvocationTargetException) {
                Log.errorEx("Event %s of screen %s failed", e, ev.javaClass.simpleName, javaClass.simpleName)
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Control> getControlByName(name: String): T? {
        for (ctrl in controls) {
            if (name == ctrl.getName()) return ctrl as T

            if (ctrl is Container) {
                val ret = ctrl.getByName(name)
                if (ret != null) return ret as T
            }
        }
        return null
    }

    protected open fun addLoadCustomVariables(vars: MutableMap<String, Double>) {}

    fun loadFrom(resLoc: ResourceLocation) {
        try {
            val root: JsonObject = GuiLoader.getJson(resLoc)
                ?: throw RuntimeException("Could not load GUI file $resLoc")

            if (!root.has("controls") || !root.get("controls").isJsonArray) {
                throw RuntimeException("In GUI file $resLoc: missing root 'controls' object.")
            }

            val vars = HashMap<String, Double>()
            vars["width"] = width.toDouble()
            vars["height"] = height.toDouble()
            vars["displayWidth"] = minecraft!!.window.width.toDouble()
            vars["displayHeight"] = minecraft!!.window.height.toDouble()
            addLoadCustomVariables(vars)

            val content = root.get("controls").asJsonArray
            for (elem in content) {
                controls.add(GuiLoader.create(JsonOWrapper(elem.asJsonObject, vars)))
            }

            val fields = javaClass.declaredFields
            for (f in fields) {
                f.isAccessible = true
                val fc = f.getAnnotation(FillControl::class.java)
                if (fc != null) {
                    val name = if (fc.name.isEmpty()) f.name else fc.name
                    val ctrl = getControlByName<Control>(name)
                    if (ctrl == null) {
                        if (fc.required) {
                            throw RuntimeException("In GUI file $resLoc: missing required control $name")
                        }
                        continue
                    }

                    if (!f.type.isAssignableFrom(ctrl.javaClass)) {
                        throw RuntimeException("In GUI file $resLoc: invalid type for control $name")
                    }

                    try {
                        f.set(this, ctrl)
                    } catch (e: IllegalAccessException) {
                        if (fc.required) throw RuntimeException(e)
                    }
                }
            }

            if (root.has("center") && root.get("center").asBoolean) {
                centerControls()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        for (ctrl in controls) ctrl.destroy()
        controls.clear()
        super.resize(minecraft, width, height)
    }

    protected fun requestAutocomplete(beginning: String, matchExact: Boolean) {
        WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageACQuery(beginning, matchExact))
    }

    open fun onAutocompleteResult(pairs: Array<NameUUIDPair>) {}

    open fun onAutocompleteFailure() {}

    protected fun requestSync() {
        syncTicksLeft = syncTicks - 1
    }

    protected fun syncRequested(): Boolean = syncTicksLeft >= 0

    protected fun abortSync() {
        syncTicksLeft = -1
    }

    protected open fun sync() {}

    override fun tick() {
        if (syncTicksLeft >= 0) {
            if (--syncTicksLeft < 0) sync()
        }
    }

    fun drawItemStackTooltip(poseStack: GuiGraphics, isItem: ItemStack, x: Int, y: Int) {
        poseStack.renderTooltip(Minecraft.getInstance().font, isItem, x, y)
    }

    fun drawTooltip(poseStack: GuiGraphics, lines: List<String>, x: Int, y: Int) {
        poseStack.renderTooltip(
            Minecraft.getInstance().font,
            lines.stream().map { a -> FormattedCharSequence.forward(a, Style.EMPTY) }.collect(Collectors.toList()),
            x,
            y
        )
    }

    fun requirePostDraw(ctrl: Control) {
        if (!postDrawList.contains(ctrl)) postDrawList.add(ctrl)
    }

    override fun isPauseScreen(): Boolean = false

    abstract fun isForBlock(bp: BlockPos, side: BlockSide): Boolean

    open fun getWikiPageName(): String? = null

    companion object {
        @JvmField
        var CURRENT_SCREEN: WDScreen? = null
    }
}
