/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.MultiBufferSource
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_NEAREST

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
    private var fbo: RenderTarget? = null
    private var selected = -1
    private var update = false
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
        createFBO()
    }

    private fun getYOffset(): Int {
        val amount = scrollPos / (height - 2 - scrollSize).toDouble() * (contentH - height).toDouble()
        return amount.toInt()
    }

    private fun isInScrollbar(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= x + width - 5 && mouseX <= x + width - 1 &&
            mouseY >= y + 1 + scrollPos && mouseY <= y + 1 + scrollPos + scrollSize
    }

    private fun createFBO() {
        fbo?.destroyBuffers()

        val target = TextureTarget(parent!!.screen2DisplayX(width), parent!!.screen2DisplayY(height), true, Minecraft.ON_OSX)
        target.setFilterMode(GL_NEAREST)
        target.bindWrite(true)
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 1.0f)
        RenderSystem.clearDepth(GL_COLOR_BUFFER_BIT.toDouble())
        target.unbindWrite()
        mc.mainRenderTarget.bindWrite(true)
        fbo = target
        update = true
    }

    private fun renderToFBO(source: MultiBufferSource.BufferSource) {
        val target = fbo ?: return
        val graphics = beginFramebuffer(target, width.toFloat(), height.toFloat())
        GL11.glColorMask(true, true, true, true)
        RenderSystem.applyModelViewMatrix()
        graphics.fill(0, 0, width, height, COLOR_BLACK)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        val offset = 4 - getYOffset()
        for (i in content.indices) {
            val pos = i * 12 + offset
            if (pos + 12 >= 1) {
                if (pos >= height - 1) break
                val color = if (i == selected) selColor else COLOR_WHITE
                graphics.drawString(font, content[i].text, 4, i * 12 + offset, color)
            }
        }

        graphics.renderOutline(0, 0, width, height, 0xFF808080.toInt())
        graphics.flush()
        endFramebuffer(graphics, target)
    }

    override fun destroy() {
        fbo?.destroyBuffers()
    }

    fun setSize(w: Int, h: Int) {
        width = w
        height = h
        createFBO()
    }

    fun setWidth(width: Int) {
        this.width = width
        createFBO()
    }

    fun setHeight(height: Int) {
        this.height = height
        createFBO()
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
        update = true
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
        if (dis) {
            selected = -1
            update = true
        }
    }

    override fun disable() {
        disabled = true
        selected = -1
        update = true
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
            update = true
            return true
        }
        return false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int): Boolean {
        if (!disabled && mouseButton == 0) {
            if (isInScrollbar(mouseX, mouseY)) {
                scrolling = true
                scrollGrab = mouseY - (y + 1 + scrollPos)
                return true
            } else if (selected >= 0) {
                println(parent!!.actionPerformed(EntryClick(this)))
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
        if (!disabled && !scrolling && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            val disp = 12.0 * (height - 2 - scrollSize).toDouble() / (contentH - height).toDouble()
            var sp = scrollPos
            if (amount < 0) sp += disp.toInt() else sp -= disp.toInt()
            if (sp < 0) sp = 0.0 else if (sp > height - 2 - scrollSize) sp = (height - 2 - scrollSize).toDouble()
            if (sp != scrollPos) {
                scrollPos = sp
                update = true
            }
            return true
        }
        return false
    }

    override fun mouseClickMove(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (!disabled && scrolling) {
            var sp = mouseY - scrollGrab - y - 1
            if (sp < 0) sp = 0.0 else if (sp > height - 2 - scrollSize) sp = (height - 2 - scrollSize).toDouble()
            if (scrollPos != sp) {
                scrollPos = sp
                update = true
            }
            return true
        }
        return false
    }

    override fun draw(graphics: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {
        if (visible) {
            if (update) {
                renderToFBO(graphics.bufferSource())
                update = false
            }

            RenderSystem.setShaderTexture(0, fbo!!.colorTextureId)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            fillTexturedRect(graphics.pose(), x, y, width, height, 0.0, 1.0, 1.0, 0.0)

            fillRect(
                graphics.bufferSource(),
                x + width - 5,
                y + 1 + scrollPos,
                4,
                scrollSize,
                if (scrolling || isInScrollbar(mouseX.toDouble(), mouseY.toDouble())) 0xFF202020.toInt() else 0xFF404040.toInt()
            )
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
        this.selColor = selColor
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
        update = true
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
        selColor = json.getColor("selectionColor", 0xFF0080FF.toInt())
        createFBO()
    }
}
