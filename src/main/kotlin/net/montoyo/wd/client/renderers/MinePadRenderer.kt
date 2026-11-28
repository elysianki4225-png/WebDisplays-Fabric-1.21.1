/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.math.Axis.XP
import com.mojang.math.Axis.YP
import com.mojang.math.Axis.ZP
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.client.ClientProxy
import net.montoyo.wd.config.ClientConfig
import net.montoyo.wd.item.ItemMinePad2
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@Environment(EnvType.CLIENT)
class MinePadRenderer : IItemRenderer {
    private val mc = Minecraft.getInstance()
    private val model = ModelMinePad()
    private val clientProxy = WebDisplays.PROXY as ClientProxy
    private val padIdCache = Collections.synchronizedMap(WeakHashMap<CustomData, UUID>())

    private var sinSqrtSwingProg1 = 0f
    private var sinSqrtSwingProg2 = 0f
    private var sinSwingProg1 = 0f
    private var sinSwingProg2 = 0f

    private fun extractPadId(data: CustomData): UUID? {
        val cached = padIdCache[data]
        if (cached != null) return cached

        val tag = data.copyTag()
        if (!tag.contains("PadID")) return null

        val id = tag.getUUID("PadID")
        padIdCache[data] = id
        return id
    }

    override fun render(
        stack: PoseStack,
        isItem: ItemStack,
        handSideSign: Float,
        swingProgress: Float,
        equipProgress: Float,
        multiBufferSource: MultiBufferSource,
        packedLight: Int
    ): Boolean {
        val piF = PI.toFloat()
        val sqrtSwingProg = sqrt(swingProgress.toDouble()).toFloat()
        sinSqrtSwingProg1 = sin((sqrtSwingProg * piF).toDouble()).toFloat()
        sinSqrtSwingProg2 = sin((sqrtSwingProg * piF * 2.0f).toDouble()).toFloat()
        sinSwingProg1 = sin((swingProgress * piF).toDouble()).toFloat()
        sinSwingProg2 = sin((swingProgress * swingProgress * piF).toDouble()).toFloat()

        val sideHold = renderAtSide(handSideSign)

        val player = mc.player
        if (player != null && !player.isInvisible) {
            stack.pushPose()
            renderArmFirstPerson(stack, multiBufferSource, packedLight, equipProgress, handSideSign)
            stack.popPose()
        }

        stack.pushPose()
        stack.translate(handSideSign * -0.4f * sinSqrtSwingProg1, 0.2f * sinSqrtSwingProg2, -0.2f * sinSwingProg1)
        stack.translate(handSideSign * 0.56f, -0.52f - equipProgress * 0.6f, -0.72f)
        stack.mulPose(YP.rotationDegrees(handSideSign * (45.0f - sinSwingProg2 * 20.0f)))
        stack.mulPose(ZP.rotationDegrees(handSideSign * sinSqrtSwingProg1 * -20.0f))
        stack.mulPose(XP.rotationDegrees(sinSqrtSwingProg1 * -80.0f))
        stack.mulPose(YP.rotationDegrees(handSideSign * -45.0f))

        if (sideHold) {
            stack.translate(0.0f, 0.0f, -0.2f)
            stack.mulPose(YP.rotationDegrees(20.0f * -handSideSign))
            val total = 0.475f
            val off = -0.025f
            stack.translate(-(total - off) + (off * handSideSign), -0.1f, 0.0f)
            stack.mulPose(ZP.rotationDegrees(1.0f))
        } else if (handSideSign >= 0) {
            stack.translate(-1.065f, 0.0f, 0.0f)
        } else {
            stack.translate(0.065f, 0.0f, 0.0f)
        }

        stack.translate(0.063f, 0.28f, 0.001f)
        model.render(multiBufferSource, stack, packedLight)

        if (multiBufferSource is MultiBufferSource.BufferSource) {
            multiBufferSource.endBatch()
        }

        val data = isItem.get(DataComponents.CUSTOM_DATA)
        if (MCEF.isInitialized() && data != null && data.contains("PadID")) {
            val padId = extractPadId(data)
            val pd = if (padId == null) null else clientProxy.getPadByID(padId)
            val view = pd?.view

            if (view is MCEFBrowser) {
                val texId = view.renderer.textureID
                if (texId != 0) {
                    val x1 = 0.0f
                    val y1 = 0.0f
                    val x2 = 27.65f / 32.0f + 0.01f
                    val y2 = 14.0f / 32.0f + 0.002f

                    RenderSystem.enableDepthTest()
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
                    RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
                    RenderSystem.setShaderTexture(0, texId)
                    val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
                    val mat = stack.last().pose()
                    buffer.addVertex(mat, x1, y1, 0.002f).setUv(0.0f, 1.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, x2, y1, 0.002f).setUv(1.0f, 1.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, x2, y2, 0.002f).setUv(1.0f, 0.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, x1, y2, 0.002f).setUv(0.0f, 0.0f).setColor(255, 255, 255, 255)
                    BufferUploader.drawWithShader(buffer.buildOrThrow())
                }
            }
        }

        stack.popPose()
        RenderSystem.enableCull()

        return true
    }

    private fun renderArmFirstPerson(
        stack: PoseStack,
        buffer: MultiBufferSource,
        combinedLight: Int,
        equipProgress: Float,
        handSideSign: Float
    ) {
        val player = mc.player!!
        val tx = -0.3f * sinSqrtSwingProg1
        val ty = 0.4f * sinSqrtSwingProg2
        val tz = -0.4f * sinSwingProg1

        stack.translate(handSideSign * (tx + 0.64000005f), ty - 0.6f - equipProgress * 0.6f, tz - 0.71999997f)
        stack.mulPose(YP.rotationDegrees(handSideSign * 45.0f))
        stack.mulPose(YP.rotationDegrees(handSideSign * sinSqrtSwingProg1 * 70.0f))
        stack.mulPose(ZP.rotationDegrees(handSideSign * sinSwingProg2 * -20.0f))
        stack.translate(-handSideSign, 3.6f, 3.5f)
        stack.mulPose(ZP.rotationDegrees(handSideSign * 120.0f))
        stack.mulPose(XP.rotationDegrees(200.0f))
        stack.mulPose(YP.rotationDegrees(handSideSign * -135.0f))
        stack.translate(handSideSign * 5.6f, 0.0f, 0.0f)

        val playerRenderer = mc.entityRenderDispatcher.getRenderer(player) as PlayerRenderer
        RenderSystem.setShaderTexture(0, player.skin.texture())

        if (handSideSign >= 0.0f) {
            playerRenderer.renderRightHand(stack, buffer, combinedLight, player)
        } else {
            playerRenderer.renderLeftHand(stack, buffer, combinedLight, player)
        }
    }

    fun renderDropped(
        isItem: ItemStack,
        multiBufferSource: MultiBufferSource,
        stack: PoseStack,
        packedLight: Int
    ) {
        val data = isItem.get(DataComponents.CUSTOM_DATA)
        val isLit = data != null && data.contains("PadID")
        val x1 = 0.0f
        val y1 = 0.0f
        val x2 = 27.65f / 32.0f + 0.01f
        val y2 = 14.0f / 32.0f + 0.002f

        stack.pushPose()
        stack.translate(-x2 * 0.5f, -y2 * 0.5f, 0.0f)
        model.render(multiBufferSource, stack, packedLight, isLit)

        if (multiBufferSource is MultiBufferSource.BufferSource) {
            multiBufferSource.endBatch()
        }

        if (isLit && MCEF.isInitialized() && data != null) {
            val padId = extractPadId(data)
            val pd = if (padId == null) null else clientProxy.getPadByID(padId)
            val view = pd?.view

            if (view is MCEFBrowser) {
                val texId = view.renderer.textureID
                if (texId != 0) {
                    RenderSystem.enableDepthTest()
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
                    RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
                    RenderSystem.setShaderTexture(0, texId)
                    val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
                    val mat = stack.last().pose()
                    buffer.addVertex(mat, x1, y1, 0.002f).setUv(0.0f, 1.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, x2, y1, 0.002f).setUv(1.0f, 1.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, x2, y2, 0.002f).setUv(1.0f, 0.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, x1, y2, 0.002f).setUv(0.0f, 0.0f).setColor(255, 255, 255, 255)
                    BufferUploader.drawWithShader(buffer.buildOrThrow())
                }
            }
        }

        stack.popPose()
        RenderSystem.enableCull()
    }

    companion object {
        @JvmStatic
        fun renderAtSide(handSideSign: Float): Boolean {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return false

            var relSide = handSideSign
            if (player.mainArm == HumanoidArm.LEFT) relSide *= -1f

            var sideHold = player.isShiftKeyDown != ClientConfig.sidePad
            if ((relSide < 0 && player.getItemInHand(InteractionHand.MAIN_HAND).item is ItemMinePad2) ||
                (relSide > 0 && player.getItemInHand(InteractionHand.OFF_HAND).item is ItemMinePad2)
            ) {
                sideHold = true
            }

            return sideHold
        }
    }
}
