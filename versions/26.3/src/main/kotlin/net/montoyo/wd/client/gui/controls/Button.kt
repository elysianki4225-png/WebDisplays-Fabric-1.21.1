/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component
import net.montoyo.wd.client.gui.loading.JsonOWrapper

open class Button : Control {
    @JvmField
    protected val btn: WDButton

    @JvmField
    protected var selected = false

    @JvmField
    protected var shiftDown = false

    @JvmField
    protected var originalColor = COLOR_WHITE

    @JvmField
    protected var shiftColor = COLOR_WHITE

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
        val event = MouseButtonEvent(mouseX, mouseY, MouseButtonInfo(mouseButton, 0))
        if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT && btn.mouseClicked(event, false)) {
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
        if (selected && state == InputConstants.MOUSE_BUTTON_LEFT) {
            btn.mouseReleased(MouseButtonEvent(mouseX, mouseY, MouseButtonInfo(state, 0)))
            selected = false
            return true
        }
        return true
    }

    override fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        btn.setFGColor(if (shiftDown) shiftColor else originalColor)
        btn.extractRenderState(graphics, mouseX, mouseY, ptt)
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
        if (key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT) {
            shiftDown = false
            btn.setFGColor(originalColor)
            return true
        }
        return false
    }

    override fun keyDown(key: Int, scanCode: Int, modifiers: Int): Boolean {
        if (key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT) {
            shiftDown = true
            btn.setFGColor(shiftColor)
            return true
        }
        return false
    }

    fun setTextColor(color: Int) {
        originalColor = toARGB(color)
        if (!shiftDown) btn.setFGColor(originalColor)
    }

    fun getTextColor(): Int = btn.getFGColor()

    fun setShiftTextColor(shiftColor: Int) {
        this.shiftColor = toARGB(shiftColor)
        if (shiftDown) btn.setFGColor(this.shiftColor)
    }

    fun getShiftTextColor(): Int = shiftColor

    fun setVariant(variant: WDButton.Variant?) {
        btn.setVariant(variant)
    }

    override fun load(json: JsonOWrapper) {
        super.load(json)
        btn.setPosition(json.getInt("x", 0), json.getInt("y", 0))
        btn.width = json.getInt("width", 200)
        btn.setHeight(json.getInt("height", 20))
        btn.message = Component.nullToEmpty(tr(json.getString("label", btn.message.string) ?: ""))
        btn.active = json.getBool("active", btn.active)
        if (json.getBool("disabled", false)) btn.active = false
        btn.visible = json.getBool("visible", btn.visible)

        val variantStr = (json.getString("variant", "default") ?: "default").uppercase()
        try {
            btn.setVariant(WDButton.Variant.valueOf(variantStr))
        } catch (_: IllegalArgumentException) {
            btn.setVariant(WDButton.Variant.DEFAULT)
        }

        originalColor = toARGB(json.getColor("color", originalColor))
        shiftColor = toARGB(json.getColor("shiftColor", shiftColor))
        btn.setFGColor(originalColor)
    }

    protected open fun onClick(): Boolean = false
}
