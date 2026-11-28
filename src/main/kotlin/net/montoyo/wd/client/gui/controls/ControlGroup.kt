/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import java.util.Arrays

class ControlGroup : Container {
    private var width: Int
    private var height: Int
    private var label: String
    private var labelW = 0
    private var labelColor = COLOR_WHITE
    private var labelShadowed = true

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
        paddingY = 8
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
        this.labelColor = labelColor
    }

    fun isLabelShadowed(): Boolean = labelShadowed

    fun setLabelShadowed(labelShadowed: Boolean) {
        this.labelShadowed = labelShadowed
    }

    override fun draw(poseStack: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {
        super.draw(poseStack, mouseX, mouseY, ptt)

        if (visible) {
            poseStack.pose().pushPose()
            val sdrCol = Arrays.copyOf(RenderSystem.getShaderColor(), 4)
            RenderSystem.setShader(GameRenderer::getPositionShader)
            RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1.0f)
            RenderSystem.enableBlend()
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)

            var x1 = x.toFloat()
            var y1 = y.toFloat()
            var x2 = (x + width).toFloat()
            var y2 = (y + height).toFloat()
            val bp = 4.0f
            var lw = labelW.toFloat()

            x1 += bp
            y1 += bp
            x2 -= bp
            y2 -= bp
            lw += 12.0f

            val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
            vBuffer = buf

            if (labelW == 0) {
                buf.addVertex(x1, y1 + 1.0f, 0.0f)
                buf.addVertex(x2, y1 + 1.0f, 0.0f)
                buf.addVertex(x2, y1, 0.0f)
                buf.addVertex(x1, y1, 0.0f)
            } else {
                buf.addVertex(x1, y1 + 1.0f, 0.0f)
                buf.addVertex(x1 + 8.0f, y1 + 1.0f, 0.0f)
                buf.addVertex(x1 + 8.0f, y1, 0.0f)
                buf.addVertex(x1, y1, 0.0f)

                buf.addVertex(x1 + lw, y1 + 1.0f, 0.0f)
                buf.addVertex(x2, y1 + 1.0f, 0.0f)
                buf.addVertex(x2, y1, 0.0f)
                buf.addVertex(x1 + lw, y1, 0.0f)
            }

            buf.addVertex(x1, y2, 0.0f)
            buf.addVertex(x2, y2, 0.0f)
            buf.addVertex(x2, y2 - 1.0f, 0.0f)
            buf.addVertex(x1, y2 - 1.0f, 0.0f)

            buf.addVertex(x1, y2, 0.0f)
            buf.addVertex(x1 + 1.0f, y2, 0.0f)
            buf.addVertex(x1 + 1.0f, y1, 0.0f)
            buf.addVertex(x1, y1, 0.0f)

            buf.addVertex(x2 - 1.0f, y2, 0.0f)
            buf.addVertex(x2, y2, 0.0f)
            buf.addVertex(x2, y1, 0.0f)
            buf.addVertex(x2 - 1.0f, y1, 0.0f)
            BufferUploader.drawWithShader(buf.buildOrThrow())

            RenderSystem.setShaderColor(sdrCol[0], sdrCol[1], sdrCol[2], sdrCol[3])
            RenderSystem.disableBlend()
            poseStack.pose().popPose()

            if (labelW != 0) {
                poseStack.drawString(Minecraft.getInstance().font, label, x + 10 + bp.toInt(), y, labelColor, labelShadowed)
            }
        }
    }

    fun pack() {
        val bounds = findBounds(childs)
        for (ctrl in childs) {
            ctrl.setPos(ctrl.getX() - bounds.minX, ctrl.getY() - bounds.minY)
        }
        width = bounds.width + paddingX * 2
        height = bounds.height + paddingY * 2
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
        labelColor = json.getColor("labelColor", COLOR_WHITE)
        labelShadowed = json.getBool("labelShadowed", true)

        if (json.getBool("pack", false)) {
            pack()
        }
    }
}
