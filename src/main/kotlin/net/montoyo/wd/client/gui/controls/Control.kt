/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import com.mojang.math.Axis.XP
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.resources.language.I18n
import net.minecraft.resources.ResourceLocation
import net.montoyo.wd.client.gui.WDScreen
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import net.montoyo.wd.utilities.Bounds
import org.joml.Matrix4f
import java.util.Arrays

@Environment(EnvType.CLIENT)
abstract class Control {
    @JvmField
    protected val mc: Minecraft = Minecraft.getInstance()

    @JvmField
    protected val font: Font = mc.font

    @JvmField
    protected val tessellator: Tesselator = Tesselator.getInstance()

    @JvmField
    protected var vBuffer: BufferBuilder? = null

    @JvmField
    var name: String = ""

    @JvmField
    var userdata: Any? = null

    protected val parent: WDScreen?
        get() = sharedParent

    init {
        sharedParent = WDScreen.CURRENT_SCREEN
    }

    open fun getUserdata(): Any? = userdata

    open fun setUserdata(userdata: Any?) {
        this.userdata = userdata
    }

    open fun keyTyped(keyCode: Int, modifier: Int): Boolean = false

    open fun keyUp(key: Int, scanCode: Int, modifiers: Int): Boolean = false

    open fun keyDown(key: Int, scanCode: Int, modifiers: Int): Boolean = false

    open fun mouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int): Boolean = false

    open fun unfocus() {}

    open fun mouseReleased(mouseX: Double, mouseY: Double, state: Int): Boolean = false

    open fun mouseClickMove(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean = false

    open fun mouseMove(mouseX: Double, mouseY: Double): Boolean = false

    open fun mouseScroll(mouseX: Double, mouseY: Double, amount: Double): Boolean = false

    open fun draw(poseStack: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {}

    open fun postDraw(poseStack: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {}

    open fun destroy() {}

    @JvmName("getParentScreen")
    open fun getParent(): WDScreen? = sharedParent

    abstract fun getX(): Int
    abstract fun getY(): Int
    abstract fun getWidth(): Int
    abstract fun getHeight(): Int
    abstract fun setPos(x: Int, y: Int)

    fun fillRect(source: MultiBufferSource.BufferSource, x: Int, y: Double, w: Int, h: Int, color: Int) {
        val x1 = x.toFloat()
        val y1 = y.toFloat()
        val x2 = (x + w).toFloat()
        val y2 = (y + h).toFloat()
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF

        val sdrCol = Arrays.copyOf(RenderSystem.getShaderColor(), 4)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)

        val consumer = source.getBuffer(RenderType.gui())
        consumer.addVertex(x1, y2, 0.0f).setColor(r, g, b, a)
        consumer.addVertex(x2, y2, 0.0f).setColor(r, g, b, a)
        consumer.addVertex(x2, y1, 0.0f).setColor(r, g, b, a)
        consumer.addVertex(x1, y1, 0.0f).setColor(r, g, b, a)

        RenderSystem.setShaderColor(sdrCol[0], sdrCol[1], sdrCol[2], sdrCol[3])
        RenderSystem.disableBlend()
    }

    fun fillTexturedRect(poseStack: PoseStack, x: Int, y: Int, w: Int, h: Int, u1: Double, v1: Double, u2: Double, v2: Double) {
        val x1 = x.toFloat()
        val y1 = y.toFloat()
        val x2 = (x + w).toFloat()
        val y2 = (y + h).toFloat()

        RenderSystem.setShader(GameRenderer::getPositionColorTexLightmapShader)
        val p = poseStack.last().pose()
        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
        vBuffer = buf
        buf.addVertex(p, x1, y2, 0.0f).setColor(255, 255, 255, 255).setUv(u1.toFloat(), v2.toFloat()).setLight(LightTexture.FULL_BRIGHT)
        buf.addVertex(p, x2, y2, 0.0f).setColor(255, 255, 255, 255).setUv(u2.toFloat(), v2.toFloat()).setLight(LightTexture.FULL_BRIGHT)
        buf.addVertex(p, x2, y1, 0.0f).setColor(255, 255, 255, 255).setUv(u2.toFloat(), v1.toFloat()).setLight(LightTexture.FULL_BRIGHT)
        buf.addVertex(p, x1, y1, 0.0f).setColor(255, 255, 255, 255).setUv(u1.toFloat(), v1.toFloat()).setLight(LightTexture.FULL_BRIGHT)
        BufferUploader.drawWithShader(buf.buildOrThrow())
    }

    fun bindTexture(resLoc: ResourceLocation?) {
        if (resLoc == null) {
            RenderSystem.setShaderTexture(0, 0)
        } else {
            RenderSystem.setShaderTexture(0, resLoc)
        }
    }

    @JvmOverloads
    fun drawBorder(poseStack: GuiGraphics, x: Int, y: Int, w: Int, h: Int, color: Int, sz: Float = 1.0f) {
        val x1 = x.toFloat()
        val y1 = y.toFloat()
        val x2 = (x + w).toFloat()
        val y2 = (y + h).toFloat()
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF

        val sdrCol = Arrays.copyOf(RenderSystem.getShaderColor(), 4)
        RenderSystem.setShaderColor(r / 255f, g / 255f, b / 255f, a / 255f)
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)

        RenderSystem.setShader(GameRenderer::getPositionShader)
        val buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
        vBuffer = buf
        buf.addVertex(x1, y1 + sz, 0.0f)
        buf.addVertex(x2, y1 + sz, 0.0f)
        buf.addVertex(x2, y1, 0.0f)
        buf.addVertex(x1, y1, 0.0f)

        buf.addVertex(x1, y2, 0.0f)
        buf.addVertex(x2, y2, 0.0f)
        buf.addVertex(x2, y2 - sz, 0.0f)
        buf.addVertex(x1, y2 - sz, 0.0f)

        buf.addVertex(x1, y2, 0.0f)
        buf.addVertex(x1 + sz, y2, 0.0f)
        buf.addVertex(x1 + sz, y1, 0.0f)
        buf.addVertex(x1, y1, 0.0f)

        buf.addVertex(x2 - sz, y2, 0.0f)
        buf.addVertex(x2, y2, 0.0f)
        buf.addVertex(x2, y1, 0.0f)
        buf.addVertex(x2 - sz, y1, 0.0f)
        BufferUploader.drawWithShader(buf.buildOrThrow())

        RenderSystem.setShaderColor(sdrCol[0], sdrCol[1], sdrCol[2], sdrCol[3])
        RenderSystem.disableBlend()
    }

    fun beginFramebuffer(fbo: RenderTarget, vpW: Float, vpH: Float): GuiGraphics {
        val tmpGraphics = GuiGraphics(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource())

        fbo.bindWrite(true)

        RenderSystem.backupProjectionMatrix()
        RenderSystem.setProjectionMatrix(Matrix4f().ortho(0.0f, vpW, vpH, 0.0f, -1.0f, 1.0f), VertexSorting.ORTHOGRAPHIC_Z)

        val modelView = RenderSystem.getModelViewStack()
        tmpGraphics.pose().last().pose().set(Matrix4f(modelView))
        val poseStack = tmpGraphics.pose()
        poseStack.pushPose()
        poseStack.setIdentity()
        poseStack.mulPose(XP.rotationDegrees(180.0f))
        modelView.pushMatrix()
        modelView.set(poseStack.last().pose())
        RenderSystem.applyModelViewMatrix()

        if (!fbo.useDepth) {
            RenderSystem.disableDepthTest()
        }

        return tmpGraphics
    }

    fun endFramebuffer(poseStack: GuiGraphics, fbo: RenderTarget) {
        if (!fbo.useDepth) {
            RenderSystem.enableDepthTest()
        }

        RenderSystem.colorMask(true, true, true, true)
        RenderSystem.restoreProjectionMatrix()
        poseStack.pose().popPose()
        RenderSystem.getModelViewStack().popMatrix()
        RenderSystem.applyModelViewMatrix()
        fbo.unbindWrite()
        mc.mainRenderTarget.bindWrite(true)
    }

    @JvmName("setControlName")
    open fun setName(name: String) {
        this.name = name
    }

    @JvmName("getControlName")
    open fun getName(): String = name

    open fun load(json: JsonOWrapper) {
        name = json.getString("name", "") ?: ""
    }

    companion object {
        const val COLOR_BLACK = 0xFF000000.toInt()
        const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        const val COLOR_RED = 0xFFFF0000.toInt()
        const val COLOR_GREEN = 0xFF00FF00.toInt()
        const val COLOR_BLUE = 0xFF0000FF.toInt()
        const val COLOR_CYAN = 0xFF00FFFF.toInt()
        const val COLOR_MANGENTA = 0xFFFF00FF.toInt()
        const val COLOR_YELLOW = 0xFFFFFF00.toInt()

        @JvmField
        var sharedParent: WDScreen? = null

        @JvmStatic
        fun blend(enable: Boolean) {
            if (enable) {
                RenderSystem.enableBlend()
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA)
            } else {
                RenderSystem.disableBlend()
            }
        }

        @JvmStatic
        fun tr(text: String): String {
            return if (text.length >= 2 && text[0] == '$') {
                if (text[1] == '$') text.substring(1) else I18n.get(text.substring(1))
            } else {
                text
            }
        }

        @JvmStatic
        fun findBounds(controlList: List<Control>): Bounds {
            var minX = Integer.MAX_VALUE
            var minY = Integer.MAX_VALUE
            var maxX = Integer.MIN_VALUE
            var maxY = Integer.MIN_VALUE

            for (ctrl in controlList) {
                var x = ctrl.getX()
                var y = ctrl.getY()
                if (x < minX) minX = x
                if (y < minY) minY = y

                x += ctrl.getWidth()
                y += ctrl.getHeight()
                if (x > maxX) maxX = x
                if (y >= maxY) maxY = y
            }

            return Bounds(minX, minY, maxX, maxY)
        }
    }
}
