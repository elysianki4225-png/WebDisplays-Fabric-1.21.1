package net.montoyo.wd.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class ModelSmartphone {
	private static final ResourceLocation SMARTPHONE_TEX = ResourceLocation.fromNamespaceAndPath("webdisplays", "textures/item/model/smartphone.png");

	// Outer phone dimensions in local XY space (screen is [SCREEN_X1..SCREEN_X2] x [SCREEN_Y1..SCREEN_Y2])
	public static final float BODY_W = 0.42f;
	public static final float BODY_H = 0.74f;
	public static final float SCREEN_X1 = BODY_W * (2.0f / 16.0f);
	public static final float SCREEN_X2 = BODY_W * (14.0f / 16.0f);
	public static final float SCREEN_Y1 = BODY_H * (3.0f / 28.0f);
	public static final float SCREEN_Y2 = BODY_H * (25.0f / 28.0f);

	public void render(MultiBufferSource buffers, PoseStack stack, int packedLight) {
		PoseStack.Pose pose = stack.last();
		Matrix4f mat = pose.pose();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SMARTPHONE_TEX));

		float zFront = 0.0f;
		float zBack = -0.032f;

		// Front face (full 16x28 px region -> u=[0..16/32], v=[0..28/32])
		vc.addVertex(mat, 0f, 0f, zFront).setColor(255, 255, 255, 255).setUv(0f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);
		vc.addVertex(mat, BODY_W, 0f, zFront).setColor(255, 255, 255, 255).setUv(16f / 32f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);
		vc.addVertex(mat, BODY_W, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(16f / 32f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);
		vc.addVertex(mat, 0f, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);

		// Back face (u=[16/32..1], v=[0..28/32])
		vc.addVertex(mat, BODY_W, 0f, zBack).setColor(255, 255, 255, 255).setUv(16f / 32f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f);
		vc.addVertex(mat, 0f, 0f, zBack).setColor(255, 255, 255, 255).setUv(1f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f);
		vc.addVertex(mat, 0f, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f);
		vc.addVertex(mat, BODY_W, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(16f / 32f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f);

		// Side frame strip (u=[0..16/32], v=[28/32..1])
		float eu1 = 0f, ev1 = 28f / 32f, eu2 = 16f / 32f, ev2 = 1f;
		// Top
		vc.addVertex(mat, 0f, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f);
		vc.addVertex(mat, BODY_W, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f);
		vc.addVertex(mat, BODY_W, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f);
		vc.addVertex(mat, 0f, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f);
		// Bottom
		vc.addVertex(mat, 0f, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f);
		vc.addVertex(mat, BODY_W, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f);
		vc.addVertex(mat, BODY_W, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f);
		vc.addVertex(mat, 0f, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f);
		// Left
		vc.addVertex(mat, 0f, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f);
		vc.addVertex(mat, 0f, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f);
		vc.addVertex(mat, 0f, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f);
		vc.addVertex(mat, 0f, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f);
		// Right
		vc.addVertex(mat, BODY_W, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f);
		vc.addVertex(mat, BODY_W, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f);
		vc.addVertex(mat, BODY_W, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f);
		vc.addVertex(mat, BODY_W, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f);
	}
}
