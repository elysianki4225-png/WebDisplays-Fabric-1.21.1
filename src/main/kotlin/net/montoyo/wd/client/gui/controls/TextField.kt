/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.*
import java.awt.event.KeyEvent.VK_ENTER

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

    private fun mapScanCode(key: Int, c: Char): Long {
        if (key == GLFW_KEY_LEFT_CONTROL || key == GLFW_KEY_RIGHT_CONTROL) return 29
        return when (key) {
            GLFW_KEY_DELETE -> 83
            GLFW_KEY_LEFT -> 75
            GLFW_KEY_DOWN -> 80
            GLFW_KEY_UP -> 72
            GLFW_KEY_RIGHT -> 77
            GLFW_KEY_PAGE_DOWN -> 81
            GLFW_KEY_PAGE_UP -> 73
            GLFW_KEY_END -> 79
            GLFW_KEY_HOME -> 71
            VK_ENTER, GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> 28
            else -> GLFW.glfwGetKeyScancode(key).toLong()
        }
    }

    override fun keyDown(key: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!enabled) return false

        if (field.isFocused) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                parent!!.actionPerformed(EnterPressedEvent(this))
                return true
            } else if (key == GLFW.GLFW_KEY_TAB) {
                parent!!.actionPerformed(TabPressedEvent(this))
                return true
            }
        }

        val old = if (field.isFocused) field.value else null
        val handled = field.keyPressed(key, scanCode, modifiers)
        notifyIfChanged(old)
        return handled
    }

    override fun keyUp(key: Int, scanCode: Int, modifiers: Int): Boolean {
        return enabled && field.keyReleased(key, scanCode, modifiers)
    }

    override fun keyTyped(keyCode: Int, modifier: Int): Boolean {
        if (!enabled) return false

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            parent!!.actionPerformed(EnterPressedEvent(this))
        } else if (keyCode == GLFW.GLFW_KEY_TAB) {
            parent!!.actionPerformed(TabPressedEvent(this))
        } else {
            val old = if (field.isFocused) field.value else null
            val handled = field.charTyped(keyCode.toChar(), modifier)
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
        if (enabled && field.mouseClicked(mouseX, mouseY, mouseButton)) {
            setFocused(true)
            return true
        }
        return false
    }

    override fun unfocus() {
        setFocused(false)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, state: Int): Boolean {
        return field.mouseReleased(mouseX, mouseY, state)
    }

    override fun mouseClickMove(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        return field.mouseClicked(mouseX, mouseY, 0)
    }

    override fun mouseMove(mouseX: Double, mouseY: Double): Boolean {
        field.mouseMoved(mouseX, mouseY)
        return true
    }

    override fun mouseScroll(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        return field.mouseScrolled(mouseX, mouseY, 0.0, amount)
    }

    override fun draw(poseStack: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {
        field.render(poseStack, mouseX, mouseY, ptt)
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
        const val DEFAULT_TEXT_COLOR = 14737632
        const val DEFAULT_DISABLED_COLOR = 7368816

        private fun toARGB(color: Int): Int {
            return if ((color and 0xFF000000.toInt()) == 0) (0xFF000000.toInt() or color) else color
        }
    }
}
