/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import kotlin.math.max
import kotlin.math.roundToInt

class Icon : BasicControl() {
    @JvmField
    protected var width = 0

    @JvmField
    protected var height = 0

    @JvmField
    protected var u1 = 0.0

    @JvmField
    protected var v1 = 0.0

    @JvmField
    protected var u2 = 0.0

    @JvmField
    protected var v2 = 0.0

    @JvmField
    protected var texture: Identifier? = null

    override fun getWidth(): Int = width

    override fun getHeight(): Int = height

    override fun load(json: JsonOWrapper) {
        super.load(json)
        width = json.getInt("width", 16)
        height = json.getInt("height", 16)
        u1 = json.getDouble("u1", 0.0)
        v1 = json.getDouble("v1", 0.0)
        u2 = json.getDouble("u2", 1.0)
        v2 = json.getDouble("v2", 1.0)
        texture = Identifier.parse(json.getString("resourceLocation", "") ?: "")
    }

    override fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        val tex = texture
        if (tex != null) {
            val texSize = 1000
            val u = (u1 * texSize).toFloat()
            val v = (v1 * texSize).toFloat()
            val uw = max(1, ((u2 - u1) * texSize).toFloat().roundToInt())
            val vh = max(1, ((v2 - v1) * texSize).toFloat().roundToInt())
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, width, height, uw, vh, texSize, texSize)
        }
    }

    fun setWidth(width: Int) {
        this.width = width
    }

    fun setHeight(height: Int) {
        this.height = height
    }

    fun setTextureCoordinates(u1: Double, v1: Double, u2: Double, v2: Double) {
        this.u1 = u1
        this.v1 = v1
        this.u2 = u2
        this.v2 = v2
    }

    fun setTexture(texture: Identifier?) {
        this.texture = texture
    }

    fun getU1(): Double = u1

    fun getV1(): Double = v1

    fun getU2(): Double = u2

    fun getV2(): Double = v2
}
