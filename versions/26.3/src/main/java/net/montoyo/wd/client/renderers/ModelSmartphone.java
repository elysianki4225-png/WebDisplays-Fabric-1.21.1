package net.montoyo.wd.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

@Environment(EnvType.CLIENT)
public final class ModelSmartphone {
	private static final Identifier SMARTPHONE_TEX = Identifier.fromNamespaceAndPath("webdisplays", "textures/item/model/smartphone.png");

	public static final float BODY_W = 0.42f;
	public static final float BODY_H = 0.74f;
	public static final float SCREEN_X1 = BODY_W * (2.0f / 16.0f);
	public static final float SCREEN_X2 = BODY_W * (14.0f / 16.0f);
	public static final float SCREEN_Y1 = BODY_H * (3.0f / 28.0f);
	public static final float SCREEN_Y2 = BODY_H * (25.0f / 28.0f);

	public void submit(SubmitNodeCollector collector, PoseStack stack, int lightCoords) {
		float zFront = 0.0f;
		float zBack = -0.032f;

		collector.order(0).submitCustomGeometry(stack, RenderTypes.armorCutoutNoCull(SMARTPHONE_TEX), (pose, vc) -> {
			// Front face
			vc.addVertex(pose, 0f, 0f, zFront).setColor(255, 255, 255, 255).setUv(0f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);
			vc.addVertex(pose, BODY_W, 0f, zFront).setColor(255, 255, 255, 255).setUv(16f / 32f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);
			vc.addVertex(pose, BODY_W, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(16f / 32f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);
			vc.addVertex(pose, 0f, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);

			// Back face
			vc.addVertex(pose, BODY_W, 0f, zBack).setColor(255, 255, 255, 255).setUv(16f / 32f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, 0f, -1f);
			vc.addVertex(pose, 0f, 0f, zBack).setColor(255, 255, 255, 255).setUv(1f, 28f / 32f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, 0f, -1f);
			vc.addVertex(pose, 0f, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, 0f, -1f);
			vc.addVertex(pose, BODY_W, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(16f / 32f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, 0f, -1f);

			// Side edges
			float eu1 = 0f, ev1 = 28f / 32f, eu2 = 16f / 32f, ev2 = 1f;
			// Top
			vc.addVertex(pose, 0f, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, 1f, 0f);
			vc.addVertex(pose, BODY_W, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, 1f, 0f);
			vc.addVertex(pose, BODY_W, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, 1f, 0f);
			vc.addVertex(pose, 0f, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, 1f, 0f);
			// Bottom
			vc.addVertex(pose, 0f, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, -1f, 0f);
			vc.addVertex(pose, BODY_W, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, -1f, 0f);
			vc.addVertex(pose, BODY_W, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, -1f, 0f);
			vc.addVertex(pose, 0f, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0f, -1f, 0f);
			// Left
			vc.addVertex(pose, 0f, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, -1f, 0f, 0f);
			vc.addVertex(pose, 0f, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, -1f, 0f, 0f);
			vc.addVertex(pose, 0f, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, -1f, 0f, 0f);
			vc.addVertex(pose, 0f, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, -1f, 0f, 0f);
			// Right
			vc.addVertex(pose, BODY_W, 0f, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 1f, 0f, 0f);
			vc.addVertex(pose, BODY_W, 0f, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 1f, 0f, 0f);
			vc.addVertex(pose, BODY_W, BODY_H, zBack).setColor(255, 255, 255, 255).setUv(eu2, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 1f, 0f, 0f);
			vc.addVertex(pose, BODY_W, BODY_H, zFront).setColor(255, 255, 255, 255).setUv(eu1, ev2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 1f, 0f, 0f);
		});
	}
}
