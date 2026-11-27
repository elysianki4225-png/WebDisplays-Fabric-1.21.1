/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

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
public final class ModelMinePad {
	private static final Identifier MINEPAD_ITEM_TEX = Identifier.fromNamespaceAndPath("webdisplays", "textures/item/model/minepad_item.png");

	public void submit(SubmitNodeCollector collector, PoseStack stack, int lightCoords) {
		float x1 = 0.0f;
		float y1 = 0.0f;
		float x2 = 27.65f / 32.0f + 0.01f;
		float y2 = 14.0f / 32.0f + 0.002f;

		float width = 32f;
		float height = 32f;
		float padding = 1f / 23f;
		float padding1 = 1f / 21f;

		float bx1 = x1 - padding1;
		float bx2 = x2 + padding1;
		float by1 = y1 - padding;
		float by2 = y2 + padding;
		float zFront = 0.0f;
		float zBack = -0.032f;

		collector.order(0).submitCustomGeometry(stack, RenderTypes.armorCutoutNoCull(MINEPAD_ITEM_TEX), (pose, consumer) -> {
			// Front bezel + opaque dark screen
			consumer.addVertex(pose, x1, y1 - padding, zFront).setColor(255, 255, 255, 255).setUv(1f / width, 12f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
			consumer.addVertex(pose, x2, y1 - padding, zFront).setColor(255, 255, 255, 255).setUv(19f / width, 12f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
			consumer.addVertex(pose, x2, y2 + padding, zFront).setColor(255, 255, 255, 255).setUv(19f / width, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
			consumer.addVertex(pose, x1, y2 + padding, zFront).setColor(255, 255, 255, 255).setUv(1f / width, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);

			consumer.addVertex(pose, bx1, y1, zFront).setColor(255, 255, 255, 255).setUv(0f / width, 10f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
			consumer.addVertex(pose, bx2, y1, zFront).setColor(255, 255, 255, 255).setUv(20f / width, 10f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
			consumer.addVertex(pose, bx2, y2, zFront).setColor(255, 255, 255, 255).setUv(20f / width, 1f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
			consumer.addVertex(pose, bx1, y2, zFront).setColor(255, 255, 255, 255).setUv(0f / width, 1f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);

			// Back plate
			consumer.addVertex(pose, bx2, by1, zBack).setColor(255, 255, 255, 255).setUv(0f / width, 24f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, 0.0f, -1.0f);
			consumer.addVertex(pose, bx1, by1, zBack).setColor(255, 255, 255, 255).setUv(20f / width, 24f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, 0.0f, -1.0f);
			consumer.addVertex(pose, bx1, by2, zBack).setColor(255, 255, 255, 255).setUv(20f / width, 13f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, 0.0f, -1.0f);
			consumer.addVertex(pose, bx2, by2, zBack).setColor(255, 255, 255, 255).setUv(0f / width, 13f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, 0.0f, -1.0f);

			// Side rims
			float su0 = 0f / width, sv0 = 0f / height, su1 = 1f / width, sv1 = 1f / height;
			// Top
			consumer.addVertex(pose, bx1, by2, zFront).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, 1.0f, 0.0f);
			consumer.addVertex(pose, bx2, by2, zFront).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, 1.0f, 0.0f);
			consumer.addVertex(pose, bx2, by2, zBack).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, 1.0f, 0.0f);
			consumer.addVertex(pose, bx1, by2, zBack).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, 1.0f, 0.0f);
			// Bottom
			consumer.addVertex(pose, bx1, by1, zBack).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, -1.0f, 0.0f);
			consumer.addVertex(pose, bx2, by1, zBack).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, -1.0f, 0.0f);
			consumer.addVertex(pose, bx2, by1, zFront).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, -1.0f, 0.0f);
			consumer.addVertex(pose, bx1, by1, zFront).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 0.0f, -1.0f, 0.0f);
			// Left
			consumer.addVertex(pose, bx1, by1, zBack).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, -1.0f, 0.0f, 0.0f);
			consumer.addVertex(pose, bx1, by1, zFront).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, -1.0f, 0.0f, 0.0f);
			consumer.addVertex(pose, bx1, by2, zFront).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, -1.0f, 0.0f, 0.0f);
			consumer.addVertex(pose, bx1, by2, zBack).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, -1.0f, 0.0f, 0.0f);
			// Right
			consumer.addVertex(pose, bx2, by1, zFront).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 1.0f, 0.0f, 0.0f);
			consumer.addVertex(pose, bx2, by1, zBack).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 1.0f, 0.0f, 0.0f);
			consumer.addVertex(pose, bx2, by2, zBack).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 1.0f, 0.0f, 0.0f);
			consumer.addVertex(pose, bx2, by2, zFront).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, 1.0f, 0.0f, 0.0f);
		});
	}
}
