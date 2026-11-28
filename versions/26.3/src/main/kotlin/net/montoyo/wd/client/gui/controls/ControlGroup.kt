/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.montoyo.wd.client.gui.WDTheme
import net.montoyo.wd.client.gui.loading.JsonOWrapper

class ControlGroup : Container {
    private var width: Int
    private var height: Int
    private var label: String
    private var labelW = 0
    private var labelColor = WDTheme.TEXT_SECONDARY
    private var labelShadowed = false

    constructor() {
        width = 100
        height = 100
        label = ""
        paddingX = 8
        paddingY = 8
    }

    constructor(x: Int, y: Int, w: Int, h: Int) {
        this.x = x
        this.y = y
        width = w
        height = h
        paddingX = 8
        paddingY = 8
        label = ""
        labelW = 0
    }

    constructor(x: Int, y: Int, w: Int, h: Int, label: String) {
        this.x = x
        this.y = y
        width = w
        height = h
        this.label = label
        labelW = font.width(label)
        paddingX = 8
        paddingY = if (labelW > 0) 20 else 8
    }

    override fun getWidth(): Int = width

    override fun getHeight(): Int = height

    fun setSize(w: Int, h: Int) {
        width = w
        height = h
    }

    fun setLabel(label: String) {
        this.label = label
        labelW = font.width(label)
    }

    fun getLabel(): String = label

    fun getLabelColor(): Int = labelColor

    fun setLabelColor(labelColor: Int) {
        this.labelColor = toARGB(labelColor)
    }

    fun isLabelShadowed(): Boolean = labelShadowed

    fun setLabelShadowed(labelShadowed: Boolean) {
        this.labelShadowed = labelShadowed
    }

    override fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        if (visible) {
            WDTheme.drawSectionCard(graphics, Minecraft.getInstance().font, x, y, width, height, if (labelW > 0) label else null)
        }
        super.draw(graphics, mouseX, mouseY, ptt)
    }

    fun pack() {
        val bounds = findBounds(childs)
        for (ctrl in childs) {
            ctrl.setPos(ctrl.getX() - bounds.minX, ctrl.getY() - bounds.minY)
        }
        width = bounds.getWidth() + paddingX * 2
        height = bounds.getHeight() + paddingY + 8
    }

    override fun unfocus() {
        for (control in childs) {
            control.unfocus()
        }
    }

    override fun load(json: JsonOWrapper) {
        super.load(json)
        width = json.getInt("width", 100)
        height = json.getInt("height", 100)
        label = tr(json.getString("label", "") ?: "")
        labelW = font.width(label)
        paddingX = json.getInt("paddingX", 8)
        paddingY = json.getInt("paddingY", if (labelW > 0) 21 else 8)
        labelColor = toARGB(json.getColor("labelColor", WDTheme.TEXT_SECONDARY))
        labelShadowed = json.getBool("labelShadowed", false)

        if (json.getBool("pack", false)) {
            pack()
        }
    }
}
