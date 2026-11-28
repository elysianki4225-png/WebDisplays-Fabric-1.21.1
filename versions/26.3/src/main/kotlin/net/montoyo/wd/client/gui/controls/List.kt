/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.montoyo.wd.client.gui.WDTheme
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import kotlin.math.max

class List : BasicControl {
    private class Entry(val text: String, val userdata: Any?)

    class EntryClick(lst: List) : Event<List>() {
        private val id: Int
        private val entry: Entry

        init {
            source = lst
            id = lst.selected
            entry = lst.content[lst.selected]
        }

        fun getId(): Int = id

        fun getLabel(): String = entry.text

        fun getUserdata(): Any? = entry.userdata
    }

    private var width = 0
    private var height = 0
    private val content = ArrayList<Entry>()
    private var selected = -1
    private var selColor = 0xFF0080FF.toInt()

    private var contentH = 0
    private var scrollSize = 0
    private var scrollPos = 0.0
    private var scrolling = false
    private var scrollGrab = 0.0

    constructor() {
        content.add(Entry("", null))
        selected = 0
    }

    constructor(x: Int, y: Int, w: Int, h: Int) {
        this.x = x
        this.y = y
        width = w
        height = h
        scrollSize = h - 2
    }

    private fun getYOffset(): Int {
        if (height - 2 - scrollSize <= 0 || contentH <= height) return 0
        val amount = scrollPos / (height - 2 - scrollSize).toDouble() * (contentH - height).toDouble()
        return amount.toInt()
    }

    private fun isInScrollbar(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= x + width - 5 && mouseX <= x + width - 1 &&
            mouseY >= y + 1 + scrollPos && mouseY <= y + 1 + scrollPos + scrollSize
    }

    override fun destroy() {}

    fun setSize(w: Int, h: Int) {
        width = w
        height = h
        updateContent()
    }

    fun setWidth(width: Int) {
        this.width = width
    }

    fun setHeight(height: Int) {
        this.height = height
        updateContent()
    }

    override fun getWidth(): Int = width

    override fun getHeight(): Int = height

    fun updateContent() {
        contentH = content.size * 12 + 4
        val h2 = height - 2
        if (contentH <= h2) {
            scrollSize = h2
            scrollPos = 0.0
        } else {
            scrollSize = h2 * h2 / contentH
            if (scrollSize < 4) scrollSize = 4
        }
    }

    fun addElement(str: String): Int = addElement(str, null)

    fun addElement(str: String, ud: Any?): Int {
        content.add(Entry(str, ud))
        updateContent()
        return content.size - 1
    }

    fun addElementRaw(str: String): Int = addElement(str, null)

    fun addElementRaw(str: String, ud: Any?): Int {
        content.add(Entry(str, ud))
        return content.size - 1
    }

    override fun setDisabled(dis: Boolean) {
        disabled = dis
        if (dis) selected = -1
    }

    override fun disable() {
        disabled = true
        selected = -1
    }

    override fun mouseMove(mouseX: Double, mouseY: Double): Boolean {
        var sel = -1
        if (!disabled && mouseX >= x + 1 && mouseX <= x + width - 6 && mouseY >= y + 2 && mouseY <= y + height - 2) {
            val offset = y + 4 - getYOffset()
            sel = ((mouseY - offset) / 12).toInt()
            if (sel < 0 || sel >= content.size) sel = -1
        }

        if (selected != sel) {
            selected = sel
            return true
        }
        return false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int): Boolean {
        if (!disabled && mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
            if (isInScrollbar(mouseX, mouseY)) {
                scrolling = true
                scrollGrab = mouseY - (y + 1 + scrollPos)
                return true
            } else if (selected >= 0) {
                parent!!.actionPerformed(EntryClick(this))
                return true
            }
            return false
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, state: Int): Boolean {
        if (!disabled && scrolling) {
            scrolling = false
            return true
        }
        return false
    }

    override fun mouseScroll(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (!disabled && !scrolling && contentH > height && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            val disp = 12.0 * (height - 2 - scrollSize).toDouble() / (contentH - height).toDouble()
            var sp = scrollPos
            if (amount < 0) sp += disp.toInt() else sp -= disp.toInt()
            if (sp < 0) sp = 0.0 else if (sp > height - 2 - scrollSize) sp = (height - 2 - scrollSize).toDouble()
            if (sp != scrollPos) scrollPos = sp
            return true
        }
        return false
    }

    override fun mouseClickMove(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (!disabled && scrolling) {
            var sp = mouseY - scrollGrab - y - 1
            if (sp < 0) sp = 0.0 else if (sp > height - 2 - scrollSize) sp = (height - 2 - scrollSize).toDouble()
            if (scrollPos != sp) scrollPos = sp
            return true
        }
        return false
    }

    override fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        if (visible) {
            graphics.fill(x, y, x + width, y + height, WDTheme.INSET_BG)

            graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1)
            val offset = 4 - getYOffset()
            val isDangerSel = (selColor and 0x00FFFFFF) == 0x00FF0000
            val rowBg = if (isDangerSel) WDTheme.DANGER_SOFT else WDTheme.ACCENT_SOFT
            val rowTextSel = if (isDangerSel) 0xFFFCA5A5.toInt() else 0xFF93C5FD.toInt()

            for (i in content.indices) {
                val pos = i * 12 + offset
                if (pos + 12 >= 1) {
                    if (pos >= height - 1) break

                    if (i == selected) {
                        graphics.fill(x + 2, y + pos - 2, x + width - 6, y + pos + 10, rowBg)
                    } else if ((i and 1) == 1) {
                        graphics.fill(x + 2, y + pos - 2, x + width - 6, y + pos + 10, 0x10FFFFFF)
                    }

                    val color = if (i == selected) rowTextSel else WDTheme.TEXT_PRIMARY
                    graphics.text(font, content[i].text, x + 5, y + pos, color, false)
                    if (i == selected && isDangerSel) {
                        graphics.text(font, "×", x + width - 14, y + pos, 0xFFF87171.toInt(), false)
                    }
                }
            }
            graphics.disableScissor()

            WDTheme.drawBorder(graphics, x, y, width, height, WDTheme.INSET_BORDER)

            if (contentH > height) {
                val sbY = y + 2 + scrollPos.toInt()
                val sbH = max(8, scrollSize - 2)
                fillRect(
                    graphics,
                    x + width - 4,
                    sbY,
                    2,
                    sbH,
                    if (scrolling || isInScrollbar(mouseX.toDouble(), mouseY.toDouble())) WDTheme.ACCENT else 0xFF475569.toInt()
                )
            }
        }
    }

    fun getEntryLabel(id: Int): String = content[id].text

    fun getEntryUserdata(id: Int): Any? = content[id].userdata

    fun findEntryByLabel(label: String): Int {
        for (i in content.indices) {
            if (content[i].text == label) return i
        }
        return -1
    }

    fun findEntryByUserdata(o: Any?): Int {
        if (o == null) {
            for (i in content.indices) {
                if (content[i].userdata == null) return i
            }
        } else {
            for (i in content.indices) {
                if (content[i].userdata != null && content[i].userdata == o) return i
            }
        }
        return -1
    }

    fun setSelectionColor(selColor: Int) {
        this.selColor = toARGB(selColor)
    }

    fun getSelectionColor(): Int = selColor

    fun getElementCount(): Int = content.size

    fun removeElement(id: Int) {
        if (selected != -1 && id == content.size - 1) selected = -1
        content.removeAt(id)
        updateContent()
    }

    fun removeElementRaw(id: Int) {
        if (selected != -1 && id == content.size - 1) selected = -1
        content.removeAt(id)
    }

    fun clear() {
        content.clear()
        scrollPos = 0.0
        scrolling = false
        scrollSize = height - 2
        selected = -1
    }

    fun clearRaw() {
        content.clear()
        scrollPos = 0.0
        scrolling = false
        selected = -1
    }

    override fun load(json: JsonOWrapper) {
        super.load(json)
        width = json.getInt("width", 100)
        height = json.getInt("height", 100)
        selColor = toARGB(json.getColor("selectionColor", 0xFF0080FF.toInt()))
        updateContent()
    }
}
