/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component
import net.montoyo.wd.client.gui.WDTheme
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import org.lwjgl.sdl.SDLScancode

class TextField : Control {
    class EnterPressedEvent(field: TextField) : Event<TextField>() {
        private val text: String

        init {
            source = field
            text = field.field.value
        }

        fun getText(): String = text
    }

    class TabPressedEvent(field: TextField) : Event<TextField>() {
        private val beginning: String

        init {
            source = field
            val text = field.field.value
            val max = field.field.cursorPosition
            var spacePos = 0

            for (i in max - 1 downTo 0) {
                if (Character.isSpaceChar(text[i])) {
                    spacePos = i
                    break
                }
            }

            beginning = text.substring(spacePos, max).trim()
        }

        fun getBeginning(): String = beginning
    }

    class TextChangedEvent(tf: TextField, private val oldContent: String) : Event<TextField>() {
        private val newContent: String

        init {
            source = tf
            newContent = tf.field.value
        }

        fun getOldContent(): String = oldContent

        fun getNewContent(): String = newContent
    }

    fun interface TextChangeListener {
        fun onTextChange(tf: TextField, oldContent: String, newContent: String)
    }

    private val field: WDEditBox
    private var enabled = true
    private var textColor = DEFAULT_TEXT_COLOR
    private var disabledColor = DEFAULT_DISABLED_COLOR
    private val listeners = ArrayList<TextChangeListener>()

    constructor() {
        field = WDEditBox(font, 1, 1, 198, 20, Component.nullToEmpty(""))
        setFocused(false)
    }

    constructor(x: Int, y: Int, width: Int, height: Int) {
        field = WDEditBox(font, x + 1, y + 1, width - 2, height - 2, Component.nullToEmpty(""))
        setFocused(false)
    }

    constructor(x: Int, y: Int, width: Int, height: Int, text: String) {
        field = WDEditBox(font, x + 1, y + 1, width - 2, height - 2, Component.nullToEmpty(""))
        field.value = text
        setFocused(false)
    }

    override fun keyDown(event: KeyEvent): Boolean {
        if (!enabled) return false

        val key = event.key()
        if (field.isFocused) {
            if (key == InputConstants.KEY_RETURN || key == SDLScancode.SDL_SCANCODE_KP_ENTER) {
                parent!!.actionPerformed(EnterPressedEvent(this))
                return true
            } else if (key == InputConstants.KEY_TAB) {
                parent!!.actionPerformed(TabPressedEvent(this))
                return true
            }
        }

        val old = if (field.isFocused) field.value else null
        val handled = field.keyPressed(event)
        notifyIfChanged(old)
        return handled
    }

    override fun keyUp(event: KeyEvent): Boolean {
        return enabled && field.keyReleased(event)
    }

    override fun keyDown(key: Int, scanCode: Int, modifiers: Int): Boolean {
        return keyDown(KeyEvent(key, scanCode, modifiers))
    }

    override fun keyUp(key: Int, scanCode: Int, modifiers: Int): Boolean {
        return keyUp(KeyEvent(key, scanCode, modifiers))
    }

    override fun keyTyped(keyCode: Int, modifier: Int): Boolean {
        if (!enabled) return false

        if (keyCode == InputConstants.KEY_RETURN || keyCode == SDLScancode.SDL_SCANCODE_KP_ENTER) {
            parent!!.actionPerformed(EnterPressedEvent(this))
        } else if (keyCode == InputConstants.KEY_TAB) {
            parent!!.actionPerformed(TabPressedEvent(this))
        } else {
            val old = if (field.isFocused) field.value else null
            val handled = field.charTyped(CharacterEvent(keyCode))
            notifyIfChanged(old)
            return handled
        }

        return false
    }

    private fun notifyIfChanged(old: String?) {
        if (old != null && field.value != old) {
            for (tcl in listeners) {
                tcl.onTextChange(this, old, field.value)
            }
            parent!!.actionPerformed(TextChangedEvent(this, old))
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int): Boolean {
        if (enabled && field.mouseClicked(MouseButtonEvent(mouseX, mouseY, MouseButtonInfo(mouseButton, 0)), false)) {
            setFocused(true)
            return true
        }
        return false
    }

    override fun unfocus() {
        setFocused(false)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, state: Int): Boolean {
        return field.mouseReleased(MouseButtonEvent(mouseX, mouseY, MouseButtonInfo(state, 0)))
    }

    override fun mouseClickMove(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        return field.mouseClicked(MouseButtonEvent(mouseX, mouseY, MouseButtonInfo(button, 0)), false)
    }

    override fun mouseMove(mouseX: Double, mouseY: Double): Boolean {
        field.mouseMoved(mouseX, mouseY)
        return true
    }

    override fun mouseScroll(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        return field.mouseScrolled(mouseX, mouseY, 0.0, amount)
    }

    override fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        field.extractRenderState(graphics, mouseX, mouseY, ptt)
        if (field.visible) {
            val fx = getX()
            val fy = getY()
            val fw = getWidth()
            val fh = getHeight()
            if (!enabled) {
                graphics.fill(fx, fy, fx + fw, fy + fh, 0x55090D14)
                WDTheme.drawBorder(graphics, fx, fy, fw, fh, 0xFF232B3A.toInt())
            } else if (field.isFocused) {
                WDTheme.drawBorder(graphics, fx, fy, fw, fh, WDTheme.ACCENT)
            } else {
                val hovered = mouseX >= fx && mouseX < fx + fw && mouseY >= fy && mouseY < fy + fh
                WDTheme.drawBorder(graphics, fx, fy, fw, fh, if (hovered) 0xFF4B5D7C.toInt() else 0xFF2E384B.toInt())
            }
        }
    }

    fun setText(text: String) {
        val old = field.value
        field.value = text
        if (old != text) {
            for (tcl in listeners) {
                tcl.onTextChange(this, old, text)
            }
        }
    }

    fun clear() {
        field.value = ""
    }

    fun getText(): String = field.value

    fun getSelectedText(): String = field.highlighted

    fun setWidth(width: Int) {
        field.width = width - 2
    }

    override fun getWidth(): Int = field.width + 2

    fun setHeight(height: Int) {
        field.setHeight(height - 2)
    }

    override fun getHeight(): Int = field.height + 2

    fun setSize(w: Int, h: Int) {
        field.width = w - 2
        field.setHeight(h - 2)
    }

    override fun setPos(x: Int, y: Int) {
        field.setPosition(x + 1, y + 1)
    }

    override fun getX(): Int = field.x - 1

    override fun getY(): Int = field.y - 1

    fun setDisabled(en: Boolean) {
        enabled = !en
        if (en) field.isFocused = false
        field.setEditable(enabled)
    }

    fun isDisabled(): Boolean = !enabled

    fun enable() {
        enabled = true
        field.setEditable(true)
    }

    fun disable() {
        field.isFocused = false
        enabled = false
        field.setEditable(false)
    }

    fun setVisible(vi: Boolean) {
        field.visible = vi
    }

    fun isVisible(): Boolean = field.visible

    fun show() {
        field.visible = true
    }

    fun hide() {
        field.visible = false
    }

    fun setFocused(valFocused: Boolean) {
        field.isFocused = valFocused
    }

    fun hasFocus(): Boolean = field.isFocused

    fun focus() {
        field.isFocused = true
    }

    fun setTextColor(color: Int) {
        textColor = toARGB(color)
        field.setTextColor(textColor)
    }

    fun getTextColor(): Int = textColor

    fun setDisabledTextColor(color: Int) {
        disabledColor = toARGB(color)
        field.setTextColorUneditable(disabledColor)
    }

    fun getDisabledTextColor(): Int = disabledColor

    fun getMcField(): EditBox = field

    fun addTextChangeListener(l: TextChangeListener?) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l)
        }
    }

    fun removeTextChangeListener(l: TextChangeListener?) {
        listeners.remove(l)
    }

    override fun load(json: JsonOWrapper) {
        super.load(json)
        field.setPosition(json.getInt("x", 0) + 1, json.getInt("y", 0) + 1)
        field.width = json.getInt("width", 200) - 2
        field.setHeight(json.getInt("height", 22) - 2)
        field.value = tr(json.getString("text", "") ?: "")
        field.visible = json.getBool("visible", true)
        field.setMaxLength(json.getInt("maxLength", 32))

        enabled = !json.getBool("disabled", false)
        field.setEditable(enabled)
        textColor = toARGB(json.getColor("textColor", DEFAULT_TEXT_COLOR))
        disabledColor = toARGB(json.getColor("disabledColor", DEFAULT_DISABLED_COLOR))

        field.setTextColor(textColor)
        field.setTextColorUneditable(disabledColor)
        setFocused(false)
    }

    companion object {
        const val DEFAULT_TEXT_COLOR = 0xFFE0E0E0.toInt()
        const val DEFAULT_DISABLED_COLOR = 0xFF707070.toInt()
    }
}
