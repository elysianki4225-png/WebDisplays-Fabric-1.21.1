package net.montoyo.wd.client.renderers

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

@Environment(EnvType.CLIENT)
class ModelSmartphone {
    fun render(buffers: MultiBufferSource, stack: PoseStack, packedLight: Int) {
        val pose = stack.last()
        val mat = pose.pose()
        val vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SMARTPHONE_TEX))

        val zFront = 0.0f
        val zBack = -0.032f

        vc.addVertex(mat, 0f, 0f, zFront).setColor(255, 255, 255, 255).setUv(0f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f)
        vc.addVertex(mat, BODY_W, 0f, zFront).setColor(255, 255, 255, 255).setUv(16f / 32f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f)
        vc.addVertex(mat, BODY_W, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(16f / 32f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f)
        vc.addVertex(mat, 0f, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f)

        vc.addVertex(mat, BODY_W, 0f, zBack).setColor(255, 255, 255, 255).setUv(16f / 32f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f)
        vc.addVertex(mat, 0f, 0f, zBack).setColor(255, 255, 255, 255).setUv(1f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f)
        vc.addVertex(mat, 0f, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f)
        vc.addVertex(mat, BODY_W, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(16f / 32f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f)

        val eu1 = 0f
        val ev1 = 28f / 32f
        val eu2 = 16f / 32f
        val ev2 = 1f

        vc.addVertex(mat, 0f, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f)
        vc.addVertex(mat, BODY_W, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f)
        vc.addVertex(mat, BODY_W, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f)
        vc.addVertex(mat, 0f, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f)

        vc.addVertex(mat, 0f, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f)
        vc.addVertex(mat, BODY_W, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f)
        vc.addVertex(mat, BODY_W, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f)
        vc.addVertex(mat, 0f, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f)

        vc.addVertex(mat, 0f, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f)
        vc.addVertex(mat, 0f, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f)
        vc.addVertex(mat, 0f, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f)
        vc.addVertex(mat, 0f, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f)

        vc.addVertex(mat, BODY_W, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f)
        vc.addVertex(mat, BODY_W, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f)
        vc.addVertex(mat, BODY_W, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f)
        vc.addVertex(mat, BODY_W, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f)
    }

    companion object {
        private val SMARTPHONE_TEX = ResourceLocation.fromNamespaceAndPath("webdisplays", "textures/item/model/smartphone.png")

        const val BODY_W = 0.42f
        const val BODY_H = 0.74f
        const val SCREEN_X1 = BODY_W * (2.0f / 16.0f)
        const val SCREEN_X2 = BODY_W * (14.0f / 16.0f)
        const val SCREEN_Y1 = BODY_H * (3.0f / 28.0f)
        const val SCREEN_Y2 = BODY_H * (25.0f / 28.0f)
    }
}
