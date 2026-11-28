/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import com.google.common.collect.Lists
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import net.montoyo.wd.client.gui.WDTheme
import net.montoyo.wd.client.gui.loading.JsonOWrapper

class CheckBox : BasicControl {
    class CheckedEvent(cb: CheckBox) : Event<CheckBox>() {
        private val checked: Boolean

        init {
            source = cb
            checked = cb.checked
        }

        fun isChecked(): Boolean = checked
    }

    private var label: String
    private var labelW = 0
    private var checked = false
    private var tooltip: kotlin.collections.List<String>? = null

    constructor() {
        label = ""
    }

    constructor(x: Int, y: Int, label: String) {
        this.label = label
        labelW = font.width(label)
        checked = false
        this.x = x
        this.y = y
    }

    constructor(x: Int, y: Int, label: String, valChecked: Boolean) {
        this.label = label
        labelW = font.width(label)
        checked = valChecked
        this.x = x
        this.y = y
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int): Boolean {
        if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT && !disabled) {
            if (mouseX >= x && mouseX <= x + WIDTH + 4 + labelW && mouseY >= y && mouseY < y + HEIGHT) {
                checked = !checked
                mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                parent!!.actionPerformed(CheckedEvent(this))
                return true
            }
        }
        return false
    }

    override fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        if (visible) {
            val inside = !disabled && mouseX >= x && mouseX <= x + WIDTH + 4 + labelW && mouseY >= y && mouseY < y + HEIGHT
            val bx = x + 1
            val by = y + 1
            val bSize = 12

            val bg: Int
            val border: Int
            if (disabled) {
                bg = if (checked) 0x661D4ED8 else 0x70111620
                border = 0xFF283142.toInt()
            } else if (checked) {
                bg = if (inside) 0xFF3B82F6.toInt() else 0xFF2563EB.toInt()
                border = if (inside) 0xFF93C5FD.toInt() else 0xFF60A5FA.toInt()
            } else {
                bg = if (inside) 0xE018202E.toInt() else 0xD40E131C.toInt()
                border = if (inside) 0xFF5A739C.toInt() else 0xFF374357.toInt()
            }

            graphics.fill(bx, by, bx + bSize, by + bSize, bg)
            WDTheme.drawBorder(graphics, bx, by, bSize, bSize, border)

            if (checked) {
                val checkCol = if (disabled) 0x99CBD5E1.toInt() else 0xFFFFFFFF.toInt()
                graphics.fill(bx + 3, by + 6, bx + 4, by + 8, checkCol)
                graphics.fill(bx + 4, by + 7, bx + 5, by + 9, checkCol)
                graphics.fill(bx + 5, by + 8, bx + 6, by + 9, checkCol)
                graphics.fill(bx + 6, by + 6, bx + 7, by + 8, checkCol)
                graphics.fill(bx + 7, by + 5, bx + 8, by + 7, checkCol)
                graphics.fill(bx + 8, by + 4, bx + 9, by + 6, checkCol)
                graphics.fill(bx + 9, by + 3, bx + 10, by + 5, checkCol)
            }

            val labelCol = if (disabled) WDTheme.TEXT_MUTED else if (inside) 0xFF93C5FD.toInt() else WDTheme.TEXT_PRIMARY
            graphics.text(Minecraft.getInstance().font, label, x + WIDTH + 3, y + 3, labelCol, false)
        }
    }

    fun setLabel(label: String) {
        this.label = label
        labelW = font.width(label)
    }

    fun getLabel(): String = label

    fun isChecked(): Boolean = checked

    fun setChecked(checked: Boolean) {
        this.checked = checked
    }

    override fun getWidth(): Int = WIDTH + 4 + labelW

    override fun getHeight(): Int = HEIGHT

    override fun load(json: JsonOWrapper) {
        super.load(json)
        label = tr(json.getString("label", "") ?: "")
        labelW = font.width(label)
        checked = json.getBool("checked", false)

        val tt = tr(json.getString("tooltip", "") ?: "")
        if (tt.isNotEmpty()) {
            tooltip = Lists.newArrayList(*tt.split("\\\\n").toTypedArray())
            parent!!.requirePostDraw(this)
        }
    }

    override fun postDraw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        val tt = tooltip
        if (tt != null && !disabled && mouseX >= x && mouseX <= x + WIDTH + 4 + labelW && mouseY >= y && mouseY < y + HEIGHT) {
            parent!!.drawTooltip(graphics, tt, mouseX, mouseY)
        }
    }

    companion object {
        const val WIDTH = 14
        const val HEIGHT = 14
    }
}
