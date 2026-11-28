/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
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
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.stream.Collectors
import kotlin.math.max

abstract class WDScreen(component: Component) : Screen(component) {
    protected val controls = ArrayList<Control>()
    protected val postDrawList = ArrayList<Control>()
    private val eventMap = HashMap<Class<out Event<*>>, Method>()
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
                eventMap[params[0] as Class<out Event<*>>] = m
            }
        }
    }

    protected fun <T : Control> addControl(ctrl: T): T {
        controls.add(ctrl)
        return ctrl
    }

    fun screen2DisplayX(x: Int): Int {
        val ret = x.toDouble() / width.toDouble() * minecraft.window.width.toDouble()
        return ret.toInt()
    }

    fun screen2DisplayY(y: Int): Int {
        val ret = y.toDouble() / height.toDouble() * minecraft.window.height.toDouble()
        return ret.toInt()
    }

    fun display2ScreenX(x: Int): Int {
        val ret = x.toDouble() / minecraft.window.width.toDouble() * width.toDouble()
        return ret.toInt()
    }

    fun display2ScreenY(y: Int): Int {
        val ret = y.toDouble() / minecraft.window.height.toDouble() * height.toDouble()
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

    /**
     * Centers all controls inside a framed glass panel and returns [panelX, panelY, panelW, panelH].
     */
    protected fun centerInPanel(padX: Int, padTop: Int, padBottom: Int): IntArray {
        val bounds = Control.findBounds(controls)
        val contentW = max(0, bounds.maxX - bounds.minX)
        val contentH = max(0, bounds.maxY - bounds.minY)
        val panelW = contentW + padX * 2
        val panelH = contentH + padTop + padBottom
        val panelX = (width - panelW) / 2
        val panelY = (height - panelH) / 2

        val diffX = (panelX + padX) - bounds.minX
        val diffY = (panelY + padTop) - bounds.minY

        for (ctrl in controls) {
            ctrl.setPos(ctrl.getX() + diffX, ctrl.getY() + diffY)
        }

        return intArrayOf(panelX, panelY, panelW, panelH)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        if (defaultBackground) super.extractBackground(graphics, mouseX, mouseY, ptt)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        for (ctrl in controls) ctrl.draw(graphics, mouseX, mouseY, ptt)

        for (ctrl in postDrawList) ctrl.postDraw(graphics, mouseX, mouseY, ptt)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val codePoint = event.codepoint()
        val modifiers = currentModifiersMask()
        var typed = false

        for (ctrl in controls) {
            typed = typed || ctrl.keyTyped(codePoint, modifiers)
        }

        return typed
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val button = event.button()
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

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val button = event.button()
        var mouseReleased = false

        for (ctrl in controls) {
            mouseReleased = mouseReleased || ctrl.mouseReleased(mouseX, mouseY, button)
        }

        return mouseReleased
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val button = event.button()
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

    override fun keyPressed(event: KeyEvent): Boolean {
        val keyCode = event.key()
        var down = false

        for (ctrl in controls) {
            if (ctrl.keyDown(event)) down = true
        }

        if (this is GuiKeyboard) {
            return down
        }

        if (quitOnEscape && keyCode == InputConstants.KEY_ESCAPE) {
            minecraft.gui.setScreen(null)
            return true
        }

        return down || super.keyPressed(event)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        var up = false
        for (ctrl in controls) {
            if (ctrl.keyUp(event)) up = true
        }
        return up || super.keyReleased(event)
    }

    open fun actionPerformed(ev: Event<*>): Any? {
        val m = eventMap[ev::class.java]
        if (m != null) {
            try {
                return m.invoke(this, ev)
            } catch (e: IllegalAccessException) {
                Log.errorEx("Access to event %s of screen %s is denied", e, ev::class.java.simpleName, this::class.java.simpleName)
            } catch (e: InvocationTargetException) {
                Log.errorEx("Event %s of screen %s failed", e, ev::class.java.simpleName, this::class.java.simpleName)
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

    fun loadFrom(resLoc: Identifier) {
        try {
            val root: JsonObject = GuiLoader.getJson(resLoc)
                ?: throw RuntimeException("Could not load GUI file $resLoc")

            if (!root.has("controls") || !root.get("controls").isJsonArray) {
                throw RuntimeException("In GUI file $resLoc: missing root 'controls' object.")
            }

            val vars = HashMap<String, Double>()
            vars["width"] = width.toDouble()
            vars["height"] = height.toDouble()
            vars["displayWidth"] = minecraft.window.width.toDouble()
            vars["displayHeight"] = minecraft.window.height.toDouble()
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

    override fun resize(width: Int, height: Int) {
        for (ctrl in controls) ctrl.destroy()
        controls.clear()
        super.resize(width, height)
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

    fun drawItemStackTooltip(graphics: GuiGraphicsExtractor, isItem: ItemStack, x: Int, y: Int) {
        graphics.setTooltipForNextFrame(
            Minecraft.getInstance().font,
            getTooltipFromItem(Minecraft.getInstance(), isItem),
            isItem.tooltipImage,
            x,
            y
        )
    }

    fun drawTooltip(graphics: GuiGraphicsExtractor, lines: List<String>, x: Int, y: Int) {
        graphics.setTooltipForNextFrame(
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

        @JvmStatic
        protected fun currentModifiersMask(): Int {
            val mc = Minecraft.getInstance()
            var mods = 0
            if (mc.hasShiftDown()) mods = mods or InputConstants.MOD_SHIFT
            if (mc.hasControlDown()) mods = mods or InputConstants.MOD_CONTROL
            if (mc.hasAltDown()) mods = mods or InputConstants.MOD_ALT
            return mods
        }
    }
}
