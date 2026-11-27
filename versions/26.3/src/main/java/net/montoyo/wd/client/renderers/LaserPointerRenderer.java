/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.init.ItemInit;
import net.montoyo.wd.item.ItemLaserPointer;

@Environment(EnvType.CLIENT)
public final class LaserPointerRenderer {
	private LaserPointerRenderer() {
	}

	public static void register() {
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(LaserPointerRenderer::renderBeam);
	}

	public static boolean isOn() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null) return false;

		return mc.player != null && mc.level != null &&
				(ClientProxy.mouseOn || ItemLaserPointer.isOn()) &&
				mc.player.getItemInHand(InteractionHand.MAIN_HAND).getItem().equals(ItemInit.LASER_POINTER.get()) &&
				(mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.BLOCK || mc.hitResult.getType() == HitResult.Type.MISS);
	}

	private static void renderBeam(LevelRenderContext context) {
		if (!isOn())
			return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.hitResult == null)
			return;

		Vec3 camera = context.levelState().cameraRenderState.pos;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 eye = mc.player.getEyePosition(pt);
		Vec3 look = mc.player.getViewVector(pt);
		Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 start = eye.add(right.scale(0.28)).add(0, -0.18, 0).add(look.scale(0.35));
		Vec3 end = mc.hitResult.getLocation();

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);

		float r = 0.015f;
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, vc) -> {
			float sx = (float) start.x, sy = (float) start.y, sz = (float) start.z;
			float ex = (float) end.x, ey = (float) end.y, ez = (float) end.z;

			vc.addVertex(pose, sx - r, sy - r, sz).setColor(255, 30, 30, 200);
			vc.addVertex(pose, sx + r, sy + r, sz).setColor(255, 30, 30, 200);
			vc.addVertex(pose, ex + r, ey + r, ez).setColor(255, 30, 30, 200);
			vc.addVertex(pose, ex - r, ey - r, ez).setColor(255, 30, 30, 200);

			vc.addVertex(pose, sx - r, sy + r, sz).setColor(255, 30, 30, 200);
			vc.addVertex(pose, sx + r, sy - r, sz).setColor(255, 30, 30, 200);
			vc.addVertex(pose, ex + r, ey - r, ez).setColor(255, 30, 30, 200);
			vc.addVertex(pose, ex - r, ey + r, ez).setColor(255, 30, 30, 200);
		});

		poseStack.popPose();
	}
}
