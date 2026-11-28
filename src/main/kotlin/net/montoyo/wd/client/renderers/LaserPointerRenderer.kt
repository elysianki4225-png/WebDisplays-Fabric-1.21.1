/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis.XP
import com.mojang.math.Axis.YP
import com.mojang.math.Axis.ZP
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.montoyo.wd.client.ClientProxy
import net.montoyo.wd.init.ItemInit
import net.montoyo.wd.item.ItemLaserPointer
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@Environment(EnvType.CLIENT)
class LaserPointerRenderer : IItemRenderer {
    private val t = Tesselator.getInstance()
    private var bb: BufferBuilder? = null

    override fun render(
        poseStack: PoseStack,
        isItem: ItemStack,
        handSideSign: Float,
        swingProgress: Float,
        equipProgress: Float,
        multiBufferSource: MultiBufferSource,
        packedLight: Int
    ): Boolean {
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()

        val piF = PI.toFloat()
        val sqrtSwingProg = sqrt(swingProgress.toDouble()).toFloat()
        val sinSqrtSwingProg1 = sin((sqrtSwingProg * piF).toDouble()).toFloat()

        RenderSystem.setShader(GameRenderer::getPositionColorShader)

        val matrix0 = poseStack.last().pose()
        poseStack.pushPose()
        poseStack.translate(
            handSideSign * -0.4f * sinSqrtSwingProg1,
            (0.2f * sin((sqrtSwingProg * piF * 2.0f).toDouble())).toFloat(),
            (-0.2f * sin((swingProgress * piF).toDouble())).toFloat()
        )
        poseStack.translate(handSideSign * 0.56f, -0.52f - equipProgress * 0.6f, -0.72f)
        poseStack.mulPose(YP.rotationDegrees(handSideSign * (45.0f - sin((swingProgress * swingProgress * piF).toDouble()).toFloat() * 20.0f)))
        poseStack.mulPose(ZP.rotationDegrees(handSideSign * sinSqrtSwingProg1 * -20.0f))
        poseStack.mulPose(XP.rotationDegrees(sinSqrtSwingProg1 * -80.0f))
        poseStack.mulPose(YP.rotationDegrees(handSideSign * -30.0f))
        poseStack.translate(0.0f, 0.2f, 0.0f)
        poseStack.mulPose(XP.rotationDegrees(10.0f))
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f)
        val matrix = poseStack.last().pose()

        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        bb = builder

        builder.addVertex(matrix, 0.0f, 0.0f, 0.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 1.0f, 0.0f, 0.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 1.0f, 0.0f, 4.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 0.0f, 0.0f, 4.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)

        builder.addVertex(matrix, 0.0f, 0.0f, 0.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 0.0f, -1.0f, 0.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 0.0f, -1.0f, 4.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 0.0f, 0.0f, 4.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)

        builder.addVertex(matrix, 1.0f, 0.0f, 0.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 1.0f, -1.0f, 0.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 1.0f, -1.0f, 4.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 1.0f, 0.0f, 4.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)

        builder.addVertex(matrix, 0.0f, -1.0f, 4.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 1.0f, -1.0f, 4.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 1.0f, 0.0f, 4.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)
        builder.addVertex(matrix, 0.0f, 0.0f, 4.0f).setColor(0.5f, 0.5f, 0.5f, 1.0f)

        if (isOn()) {
            drawLineBetween(builder, matrix0, matrix, Vec3(0.5, -0.5, 0.5), Vec3(-40.0, 4000.5, -100.0))
        }

        BufferUploader.drawWithShader(builder.buildOrThrow())

        RenderSystem.disableBlend()
        RenderSystem.disableDepthTest()
        RenderSystem.enableCull()
        poseStack.popPose()

        return true
    }

    companion object {
        @JvmStatic
        fun isOn(): Boolean {
            if (Minecraft.getInstance().screen != null) return false

            val mc = Minecraft.getInstance()
            return mc.player != null && mc.level != null &&
                (ClientProxy.mouseOn || ItemLaserPointer.isOn()) &&
                mc.player!!.getItemInHand(InteractionHand.MAIN_HAND).item == ItemInit.LASER_POINTER.get() &&
                (mc.hitResult == null || mc.hitResult!!.type == HitResult.Type.BLOCK || mc.hitResult!!.type == HitResult.Type.MISS)
        }

        private fun drawLineBetween(
            bb: BufferBuilder,
            matrix0: Matrix4f,
            matrix: Matrix4f,
            local: Vec3,
            target: Vec3
        ) {
            val distance = (local.distanceTo(target) / 2).toFloat()
            val quarterWidth = 0.25f
            val biggerWidth = 10f

            bb.addVertex(matrix, 0.25f, -0.25f, 0.5f).setColor(0.5f, 0.0f, 0.0f, 1.0f)
            bb.addVertex(matrix, quarterWidth + 0.25f, -0.25f, 0.5f).setColor(0.5f, 0.0f, 0.0f, 1.0f)
            bb.addVertex(matrix0, biggerWidth - 6f, 3f, -distance).setColor(0.5f, 0.0f, 0.0f, 1.0f)
            bb.addVertex(matrix0, -6f, 3f, -distance).setColor(0.5f, 0.0f, 0.0f, 1.0f)

            bb.addVertex(matrix, 0.25f, -0.25f, 0.5f).setColor(0.5f, 0.0f, 0.0f, 1.0f)
            bb.addVertex(matrix, 0.25f, -quarterWidth - 0.25f, 0.5f).setColor(0.5f, 0.0f, 0.0f, 1.0f)
            bb.addVertex(matrix0, -6f, -biggerWidth + 3f, -distance).setColor(0.5f, 0.0f, 0.0f, 1.0f)
            bb.addVertex(matrix0, -6f, 3f, -distance).setColor(0.5f, 0.0f, 0.0f, 1.0f)

            bb.addVertex(matrix, quarterWidth + 0.25f, -0.25f, 0.5f).setColor(0.5f, 0.0f, 0.0f, 1.0f)
            bb.addVertex(matrix, quarterWidth + 0.25f, -quarterWidth - 0.25f, 0.5f).setColor(0.5f, 0.0f, 0.0f, 1.0f)
            bb.addVertex(matrix0, biggerWidth - 6f, -biggerWidth + 3f, -distance).setColor(0.5f, 0.0f, 0.0f, 1.0f)
            bb.addVertex(matrix0, biggerWidth - 6f, 3f, -distance).setColor(0.5f, 0.0f, 0.0f, 1.0f)
        }
    }
}
