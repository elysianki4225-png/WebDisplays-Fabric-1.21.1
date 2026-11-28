/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import org.lwjgl.glfw.GLFW

open class Button : Control {
    @JvmField
    protected val btn: WDButton

    @JvmField
    protected var selected = false

    @JvmField
    protected var shiftDown = false

    @JvmField
    protected var originalColor = 0

    @JvmField
    protected var shiftColor = 0

    class ClickEvent(btn: Button) : Event<Button>() {
        private val shiftDown: Boolean

        init {
            source = btn
            shiftDown = btn.shiftDown
        }

        fun isShiftDown(): Boolean = shiftDown
    }

    constructor() {
        btn = WDButton(0, 0, 0, 0, Component.nullToEmpty("")) {}
    }

    constructor(text: String, x: Int, y: Int, width: Int) {
        btn = WDButton(x, y, width, 20, Component.nullToEmpty(text)) {}
    }

    constructor(text: String, x: Int, y: Int) {
        btn = WDButton(0, 0, x, y, Component.nullToEmpty(text)) {}
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int): Boolean {
        if (mouseButton == 0 && btn.mouseClicked(mouseX, mouseY, mouseButton)) {
            selected = true
            btn.playDownSound(mc.soundManager)

            if (!onClick()) {
                parent!!.actionPerformed(ClickEvent(this))
            }
            return true
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, state: Int): Boolean {
        if (selected && state == 0) {
            btn.mouseReleased(mouseX, mouseY, state)
            selected = false
            return true
        }
        return true
    }

    override fun draw(poseStack: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {
        btn.setFGColor(16777215)
        btn.render(poseStack, mouseX, mouseY, ptt)
    }

    fun setLabel(label: String) {
        btn.message = Component.nullToEmpty(label)
    }

    fun getLabel(): String = btn.message.string

    fun setWidth(width: Int) {
        btn.width = width
    }

    fun setHeight(height: Int) {
        btn.setHeight(height)
    }

    override fun getWidth(): Int = btn.width

    override fun getHeight(): Int = btn.height

    override fun setPos(x: Int, y: Int) {
        btn.setPosition(x, y)
    }

    override fun getX(): Int = btn.x

    override fun getY(): Int = btn.y

    fun getMcButton(): net.minecraft.client.gui.components.Button = btn

    fun setDisabled(dis: Boolean) {
        btn.active = !dis
    }

    fun isDisabled(): Boolean = !btn.active

    fun enable() {
        btn.active = true
    }

    fun disable() {
        btn.active = false
    }

    fun setVisible(visible: Boolean) {
        btn.visible = visible
    }

    fun isVisible(): Boolean = btn.visible

    fun show() {
        btn.visible = true
    }

    fun hide() {
        btn.visible = false
    }

    fun isShiftDown(): Boolean = shiftDown

    override fun keyUp(key: Int, scanCode: Int, modifiers: Int): Boolean {
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftDown = false
            btn.setFGColor(originalColor)
            return true
        }
        return false
    }

    override fun keyDown(key: Int, scanCode: Int, modifiers: Int): Boolean {
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftDown = true
            btn.setFGColor(shiftColor)
            return true
        }
        return false
    }

    fun setTextColor(color: Int) {
        originalColor = color
        if (!shiftDown) btn.setFGColor(color)
    }

    fun getTextColor(): Int = btn.getFGColor()

    fun setShiftTextColor(shiftColor: Int) {
        this.shiftColor = shiftColor
        if (shiftDown) btn.setFGColor(shiftColor)
    }

    fun getShiftTextColor(): Int = shiftColor

    override fun load(json: JsonOWrapper) {
        super.load(json)
        btn.setPosition(json.getInt("x", 0), json.getInt("y", 0))
        btn.width = json.getInt("width", 200)
        btn.setHeight(json.getInt("height", 20))
        btn.message = Component.nullToEmpty(tr(json.getString("label", btn.message.contents.toString()) ?: ""))
        btn.active = json.getBool("active", btn.active)
        btn.visible = json.getBool("visible", btn.visible)

        originalColor = json.getColor("color", originalColor)
        shiftColor = json.getColor("shiftColor", shiftColor)
        btn.setFGColor(originalColor)
    }

    protected open fun onClick(): Boolean = false
}
