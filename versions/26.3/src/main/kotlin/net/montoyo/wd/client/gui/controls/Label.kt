/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.montoyo.wd.client.gui.loading.JsonOWrapper

class Label : BasicControl {
    private var label: String
    private var labelW = 0
    private var color: Int
    private var shadowed = false

    constructor() {
        label = ""
        color = COLOR_WHITE
    }

    constructor(x: Int, y: Int, str: String) {
        this.x = x
        this.y = y
        label = str
        labelW = font.width(str)
        color = COLOR_WHITE
        shadowed = false
    }

    constructor(x: Int, y: Int, str: String, color: Int) {
        this.x = x
        this.y = y
        label = str
        labelW = font.width(str)
        this.color = toARGB(color)
        shadowed = false
    }

    constructor(x: Int, y: Int, str: String, color: Int, shadowed: Boolean) {
        this.x = x
        this.y = y
        label = str
        labelW = font.width(str)
        this.color = toARGB(color)
        this.shadowed = shadowed
    }

    fun setLabel(label: String) {
        this.label = label
        labelW = font.width(label)
    }

    fun getLabel(): String = label

    fun setColor(color: Int) {
        this.color = toARGB(color)
    }

    fun getColor(): Int = color

    fun setShadowed(shadowed: Boolean) {
        this.shadowed = shadowed
    }

    fun isShadowed(): Boolean = shadowed

    override fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        if (visible) {
            graphics.text(Minecraft.getInstance().font, label, x, y, color, shadowed)
        }
    }

    override fun getWidth(): Int = labelW

    override fun getHeight(): Int = 12

    override fun load(json: JsonOWrapper) {
        super.load(json)
        label = tr(json.getString("label", "") ?: "")
        labelW = font.width(label)
        color = toARGB(json.getColor("color", COLOR_WHITE))
        shadowed = json.getBool("shadowed", false)
    }
}
