/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.resources.language.I18n
import net.montoyo.wd.client.gui.WDScreen
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import net.montoyo.wd.utilities.Bounds

@Environment(EnvType.CLIENT)
abstract class Control {
    @JvmField
    protected val mc: Minecraft = Minecraft.getInstance()

    @JvmField
    protected val font: Font = mc.font

    private var controlName: String = ""

    @JvmField
    var userdata: Any? = null

    protected val parent: WDScreen?
        get() = sharedParent

    init {
        sharedParent = WDScreen.CURRENT_SCREEN
    }

    open fun getUserdata(): Any? = userdata

    open fun setUserdata(userdata: Any?) {
        this.userdata = userdata
    }

    open fun keyTyped(keyCode: Int, modifier: Int): Boolean = false

    open fun keyUp(key: Int, scanCode: Int, modifiers: Int): Boolean = false

    open fun keyDown(key: Int, scanCode: Int, modifiers: Int): Boolean = false

    open fun keyUp(event: KeyEvent): Boolean = keyUp(event.key(), event.key(), event.modifiers())

    open fun keyDown(event: KeyEvent): Boolean = keyDown(event.key(), event.key(), event.modifiers())

    open fun mouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int): Boolean = false

    open fun unfocus() {}

    open fun mouseReleased(mouseX: Double, mouseY: Double, state: Int): Boolean = false

    open fun mouseClickMove(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean = false

    open fun mouseMove(mouseX: Double, mouseY: Double): Boolean = false

    open fun mouseScroll(mouseX: Double, mouseY: Double, amount: Double): Boolean = false

    open fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {}

    open fun postDraw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {}

    open fun destroy() {}

    abstract fun getX(): Int
    abstract fun getY(): Int
    abstract fun getWidth(): Int
    abstract fun getHeight(): Int
    abstract fun setPos(x: Int, y: Int)

    fun fillRect(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        graphics.fill(x, y, x + w, y + h, color)
    }

    @JvmOverloads
    fun drawBorder(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int, sz: Int = 1) {
        val x2 = x + w
        val y2 = y + h
        graphics.fill(x, y, x2, y + sz, color)
        graphics.fill(x, y2 - sz, x2, y2, color)
        graphics.fill(x, y, x + sz, y2, color)
        graphics.fill(x2 - sz, y, x2, y2, color)
    }

    open fun setName(name: String) {
        this.controlName = name
    }

    open fun getName(): String = controlName

    open fun load(json: JsonOWrapper) {
        controlName = json.getString("name", "") ?: ""
    }

    companion object {
        const val COLOR_BLACK = 0xFF000000.toInt()
        const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        const val COLOR_RED = 0xFFFF0000.toInt()
        const val COLOR_GREEN = 0xFF00FF00.toInt()
        const val COLOR_BLUE = 0xFF0000FF.toInt()
        const val COLOR_CYAN = 0xFF00FFFF.toInt()
        const val COLOR_MANGENTA = 0xFFFF00FF.toInt()
        const val COLOR_YELLOW = 0xFFFFFF00.toInt()

        @JvmField
        var sharedParent: WDScreen? = null

        @JvmStatic
        fun toARGB(color: Int): Int {
            return if ((color and 0xFF000000.toInt()) == 0) (0xFF000000.toInt() or color) else color
        }

        @JvmStatic
        fun tr(text: String): String {
            return if (text.length >= 2 && text[0] == '$') {
                if (text[1] == '$') text.substring(1) else I18n.get(text.substring(1))
            } else {
                text
            }
        }

        @JvmStatic
        fun findBounds(controlList: kotlin.collections.List<Control>): Bounds {
            var minX = Integer.MAX_VALUE
            var minY = Integer.MAX_VALUE
            var maxX = Integer.MIN_VALUE
            var maxY = Integer.MIN_VALUE

            for (ctrl in controlList) {
                var x = ctrl.getX()
                var y = ctrl.getY()
                if (x < minX) minX = x
                if (y < minY) minY = y

                x += ctrl.getWidth()
                y += ctrl.getHeight()
                if (x > maxX) maxX = x
                if (y >= maxY) maxY = y
            }

            return Bounds(minX, minY, maxX, maxY)
        }
    }
}
