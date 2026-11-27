/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

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
public final class ModelMinePad {
	private static final ResourceLocation MINEPAD_ITEM_TEX = ResourceLocation.parse("webdisplays:textures/item/model/minepad_item.png");

	public void render(MultiBufferSource buffers, PoseStack stack) {
		render(buffers, stack, LightTexture.FULL_BRIGHT);
	}

	public void render(MultiBufferSource buffers, PoseStack stack, int packedLight) {
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

		PoseStack.Pose pose = stack.last();
		Matrix4f positionMatrix = pose.pose();
		VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(MINEPAD_ITEM_TEX));

		// Front bezel + opaque dark screen
		addQuad(consumer, positionMatrix, pose, x1, y1 - padding, x2, y2 + padding, zFront,
				1f / width, 12f / height, 19f / width, 0f / height, LightTexture.FULL_BRIGHT, 0f, 0f, 1f);
		addQuad(consumer, positionMatrix, pose, x1 - padding1, y1, x2 + padding1, y2, zFront,
				0f / width, 10f / height, 20f / width, 1f / height, LightTexture.FULL_BRIGHT, 0f, 0f, 1f);

		// Back plate (UV region x=0..20, y=13..24 of minepad_item.png)
		consumer.addVertex(positionMatrix, bx2, by1, zBack).setColor(255, 255, 255, 255).setUv(0f / width, 24f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f);
		consumer.addVertex(positionMatrix, bx1, by1, zBack).setColor(255, 255, 255, 255).setUv(20f / width, 24f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f);
		consumer.addVertex(positionMatrix, bx1, by2, zBack).setColor(255, 255, 255, 255).setUv(20f / width, 13f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f);
		consumer.addVertex(positionMatrix, bx2, by2, zBack).setColor(255, 255, 255, 255).setUv(0f / width, 13f / height).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 0f, -1f);

		// Solid 3D side rims (sample opaque metallic rim pixel at (0, 0))
		float su0 = 0f / width, sv0 = 0f / height, su1 = 1f / width, sv1 = 1f / height;
		// Top
		consumer.addVertex(positionMatrix, bx1, by2, zFront).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f);
		consumer.addVertex(positionMatrix, bx2, by2, zFront).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f);
		consumer.addVertex(positionMatrix, bx2, by2, zBack).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f);
		consumer.addVertex(positionMatrix, bx1, by2, zBack).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, 1f, 0f);
		// Bottom
		consumer.addVertex(positionMatrix, bx1, by1, zBack).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f);
		consumer.addVertex(positionMatrix, bx2, by1, zBack).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f);
		consumer.addVertex(positionMatrix, bx2, by1, zFront).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f);
		consumer.addVertex(positionMatrix, bx1, by1, zFront).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0f, -1f, 0f);
		// Left
		consumer.addVertex(positionMatrix, bx1, by1, zBack).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f);
		consumer.addVertex(positionMatrix, bx1, by1, zFront).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f);
		consumer.addVertex(positionMatrix, bx1, by2, zFront).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f);
		consumer.addVertex(positionMatrix, bx1, by2, zBack).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1f, 0f, 0f);
		// Right
		consumer.addVertex(positionMatrix, bx2, by1, zFront).setColor(255, 255, 255, 255).setUv(su0, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f);
		consumer.addVertex(positionMatrix, bx2, by1, zBack).setColor(255, 255, 255, 255).setUv(su1, sv0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f);
		consumer.addVertex(positionMatrix, bx2, by2, zBack).setColor(255, 255, 255, 255).setUv(su1, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f);
		consumer.addVertex(positionMatrix, bx2, by2, zFront).setColor(255, 255, 255, 255).setUv(su0, sv1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1f, 0f, 0f);
	}

	private static void addQuad(VertexConsumer consumer, Matrix4f mat, PoseStack.Pose pose,
								float x1, float y1, float x2, float y2, float z,
								float u1, float v1, float u2, float v2,
								int light, float nx, float ny, float nz) {
		consumer.addVertex(mat, x1, y1, z).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		consumer.addVertex(mat, x2, y1, z).setColor(255, 255, 255, 255).setUv(u2, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		consumer.addVertex(mat, x2, y2, z).setColor(255, 255, 255, 255).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		consumer.addVertex(mat, x1, y2, z).setColor(255, 255, 255, 255).setUv(u1, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}
}
