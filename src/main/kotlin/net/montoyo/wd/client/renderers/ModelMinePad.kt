/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f

@Environment(EnvType.CLIENT)
class ModelMinePad {
    @JvmOverloads
    fun render(buffers: MultiBufferSource, stack: PoseStack, packedLight: Int = LightTexture.FULL_BRIGHT, screenLit: Boolean = true) {
        val frontLight = if (screenLit) LightTexture.FULL_BRIGHT else packedLight
        val x1 = 0.0f
        val y1 = 0.0f
        val x2 = 27.65f / 32.0f + 0.01f
        val y2 = 14.0f / 32.0f + 0.002f

        val width = 32f
        val height = 32f
        val padding = 1f / 23f
        val padding1 = 1f / 21f

        val bx1 = x1 - padding1
        val bx2 = x2 + padding1
        val by1 = y1 - padding
        val by2 = y2 + padding
        val zFront = 0.0f
        val zBack = -0.032f

        val pose = stack.last()
        val positionMatrix = pose.pose()
        val consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(MINEPAD_ITEM_TEX))

        addQuad(
            consumer, positionMatrix, pose, x1, y1 - padding, x2, y2 + padding, zFront,
            1f / width, 12f / height, 19f / width, 0f / height, frontLight, 0f, 0f, 1f
        )
        addQuad(
            consumer, positionMatrix, pose, x1 - padding1, y1, x2 + padding1, y2, zFront,
            0f / width, 10f / height, 20f / width, 1f / height, frontLight, 0f, 0f, 1f
        )

        consumer.addVertex(positionMatrix, bx2, by1, zBack).setColor(255, 255, 255, 255).setUv(0f / width, 24f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f)
        consumer.addVertex(positionMatrix, bx1, by1, zBack).setColor(255, 255, 255, 255).setUv(20f / width, 24f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f)
        consumer.addVertex(positionMatrix, bx1, by2, zBack).setColor(255, 255, 255, 255).setUv(20f / width, 13f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f)
        consumer.addVertex(positionMatrix, bx2, by2, zBack).setColor(255, 255, 255, 255).setUv(0f / width, 13f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f)

        val su0 = 0f / width
        val sv0 = 0f / height
        val su1 = 1f / width
        val sv1 = 1f / height

        consumer.addVertex(positionMatrix, bx1, by2, zFront).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f)
        consumer.addVertex(positionMatrix, bx2, by2, zFront).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f)
        consumer.addVertex(positionMatrix, bx2, by2, zBack).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f)
        consumer.addVertex(positionMatrix, bx1, by2, zBack).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f)

        consumer.addVertex(positionMatrix, bx1, by1, zBack).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f)
        consumer.addVertex(positionMatrix, bx2, by1, zBack).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f)
        consumer.addVertex(positionMatrix, bx2, by1, zFront).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f)
        consumer.addVertex(positionMatrix, bx1, by1, zFront).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f)

        consumer.addVertex(positionMatrix, bx1, by1, zBack).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f)
        consumer.addVertex(positionMatrix, bx1, by1, zFront).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f)
        consumer.addVertex(positionMatrix, bx1, by2, zFront).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f)
        consumer.addVertex(positionMatrix, bx1, by2, zBack).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f)

        consumer.addVertex(positionMatrix, bx2, by1, zFront).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f)
        consumer.addVertex(positionMatrix, bx2, by1, zBack).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f)
        consumer.addVertex(positionMatrix, bx2, by2, zBack).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f)
        consumer.addVertex(positionMatrix, bx2, by2, zFront).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f)
    }

    companion object {
        private val MINEPAD_ITEM_TEX = ResourceLocation.parse("webdisplays:textures/item/model/minepad_item.png")

        private fun addQuad(
            consumer: VertexConsumer,
            mat: Matrix4f,
            pose: PoseStack.Pose,
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            z: Float,
            u1: Float,
            v1: Float,
            u2: Float,
            v2: Float,
            light: Int,
            nx: Float,
            ny: Float,
            nz: Float
        ) {
            consumer.addVertex(mat, x1, y1, z).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz)
            consumer.addVertex(mat, x2, y1, z).setColor(255, 255, 255, 255).setUv(u2, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz)
            consumer.addVertex(mat, x2, y2, z).setColor(255, 255, 255, 255).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz)
            consumer.addVertex(mat, x1, y2, z).setColor(255, 255, 255, 255).setUv(u1, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz)
        }
    }
}
