/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class ModelMinePad {
	private static final Identifier MINEPAD_ITEM_TEX = Identifier.fromNamespaceAndPath("webdisplays", "textures/item/model/minepad_item.png");

	public void submit(SubmitNodeCollector collector, PoseStack stack, int lightCoords) {
		float x1 = 0.0f;
		float y1 = 0.0f;
		float x2 = 27.65f / 32.0f + 0.01f;
		float y2 = 14.0f / 32.0f + 0.002f;

		collector.order(0).submitCustomGeometry(stack, RenderTypes.debugFilledBox(), (pose, vc) -> {
			vc.addVertex(pose, x1, y1, 0.0f).setColor(0, 0, 0, 255);
			vc.addVertex(pose, x2, y1, 0.0f).setColor(0, 0, 0, 255);
			vc.addVertex(pose, x2, y2, 0.0f).setColor(0, 0, 0, 255);
			vc.addVertex(pose, x1, y2, 0.0f).setColor(0, 0, 0, 255);
		});

		float width = 32f;
		float height = 32f;
		float padding = 1f / 23f;
		float padding1 = 1f / 21f;
		float z = 0f;

		collector.order(0).submitCustomGeometry(stack, RenderTypes.armorCutoutNoCull(MINEPAD_ITEM_TEX), (pose, consumer) -> {
			consumer.addVertex(pose, x1, y1 - padding, z).setColor(255, 255, 255, 255).setUv(1f / width, 12f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.25f, 0.5f, 1);
			consumer.addVertex(pose, x2, y1 - padding, z).setColor(255, 255, 255, 255).setUv(19f / width, 12f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.25f, 0.5f, 1);
			consumer.addVertex(pose, x2, y2 + padding, z).setColor(255, 255, 255, 255).setUv(19f / width, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.25f, 0.5f, 1);
			consumer.addVertex(pose, x1, y2 + padding, z).setColor(255, 255, 255, 255).setUv(1f / width, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.25f, 0.5f, 1);

			consumer.addVertex(pose, x1 - padding1, y1, z).setColor(255, 255, 255, 255).setUv(0f / width, 10f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.25f, 0.5f, 1);
			consumer.addVertex(pose, x2 + padding1, y1, z).setColor(255, 255, 255, 255).setUv(20f / width, 10f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.25f, 0.5f, 1);
			consumer.addVertex(pose, x2 + padding1, y2, z).setColor(255, 255, 255, 255).setUv(20f / width, 1f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.25f, 0.5f, 1);
			consumer.addVertex(pose, x1 - padding1, y2, z).setColor(255, 255, 255, 255).setUv(0f / width, 1f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.25f, 0.5f, 1);
		});
	}
}
