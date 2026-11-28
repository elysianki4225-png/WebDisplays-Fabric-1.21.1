/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.montoyo.wd.client.gui.loading.JsonOWrapper

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
    protected var texture: ResourceLocation? = null

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
        texture = ResourceLocation.parse(json.getString("resourceLocation", "") ?: "")
    }

    override fun draw(poseStack: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {
        val tex = texture
        if (tex != null) {
            poseStack.pose().pushPose()
            RenderSystem.setShaderTexture(1, tex)
            RenderSystem.bindTexture(1)
            RenderSystem.enableBlend()
            fillTexturedRect(poseStack.pose(), x, y, width, height, u1, v1, u2, v2)
            RenderSystem.disableBlend()
            RenderSystem.bindTexture(-1)
            poseStack.pose().popPose()
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

    fun setTexture(texture: ResourceLocation?) {
        this.texture = texture
    }

    fun getU1(): Double = u1

    fun getV1(): Double = v1

    fun getU2(): Double = u2

    fun getV2(): Double = v2
}
