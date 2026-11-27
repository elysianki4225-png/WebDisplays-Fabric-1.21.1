/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.entity.TileEntityScreen;
import net.montoyo.wd.utilities.BlockSide;
import net.montoyo.wd.utilities.Vector3f;
import net.montoyo.wd.utilities.Vector3i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.math.Axis.*;

public class ScreenRenderer implements BlockEntityRenderer<TileEntityScreen, ScreenRenderer.ScreenRenderState> {

	public ScreenRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	public static class ScreenRendererProvider implements BlockEntityRendererProvider<TileEntityScreen, ScreenRenderState> {
		@Override
		public @NotNull BlockEntityRenderer<TileEntityScreen, ScreenRenderState> create(@NotNull Context arg) {
			return new ScreenRenderer(arg);
		}
	}

	public record ScreenQuad(
			Identifier textureId,
			BlockSide side,
			float midX,
			float midY,
			float midZ,
			float sw,
			float sh,
			float turnOnScale,
			boolean hasRotation,
			float rotationAngle
	) {}

	public static class ScreenRenderState extends BlockEntityRenderState {
		public final List<ScreenQuad> quads = new ArrayList<>();
	}

	private final Vector3f mid = new Vector3f();
	private final Vector3i tmpi = new Vector3i();
	private final Vector3f tmpf = new Vector3f();

	private static long windowStartNs = 0L;
	private static int createdInWindow = 0;

	@Override
	public int getViewDistance() {
		return Math.max(32, (int) Math.ceil(WebDisplays.INSTANCE.unloadDistance) + 8);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public ScreenRenderState createRenderState() {
		return new ScreenRenderState();
	}

	@Override
	public void extractRenderState(TileEntityScreen te, ScreenRenderState state, float partialTick, Vec3 camPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderer.super.extractRenderState(te, state, partialTick, camPos, crumblingOverlay);
		state.quads.clear();

		if (!te.isLoaded())
			return;

		int screenCount = te.screenCount();
		if (screenCount == 0)
			return;

		BlockPos bp = te.getBlockPos();
		long nowMs = System.currentTimeMillis();
		long nowNs = System.nanoTime();
		if (nowNs - windowStartNs >= 16_666_667L) {
			windowStartNs = nowNs;
			createdInWindow = 0;
		}

		boolean mcefReady = MCEF.isInitialized();

		for (int i = 0; i < screenCount; i++) {
			TileEntityScreen.Screen scr = te.getScreen(i);

			tmpi.set(scr.side.right);
			tmpi.mul(scr.size.x);
			tmpi.addMul(scr.side.up, scr.size.y);
			tmpf.set(tmpi);
			mid.set(0.5, 0.5, 0.5);
			mid.addMul(tmpf, 0.5f);
			tmpf.set(scr.side.left);
			mid.addMul(tmpf, 0.5f);
			tmpf.set(scr.side.down);
			mid.addMul(tmpf, 0.5f);

			if (WebDisplays.INSTANCE.enableBackfaceCulling) {
				double planeX = bp.getX() + mid.x + scr.side.forward.x * 0.505;
				double planeY = bp.getY() + mid.y + scr.side.forward.y * 0.505;
				double planeZ = bp.getZ() + mid.z + scr.side.forward.z * 0.505;
				double dot = (camPos.x - planeX) * scr.side.forward.x
						+ (camPos.y - planeY) * scr.side.forward.y
						+ (camPos.z - planeZ) * scr.side.forward.z;

				if (dot < -0.1) {
					if (WebDisplays.INSTANCE.pauseCulledScreens && scr.browser instanceof MCEFBrowser mb) {
						if (nowMs - scr.lastVisibleMs > 500L) {
							mb.setVisible(false);
						}
					}
					continue;
				}
			}

			scr.lastVisibleMs = nowMs;

			if (scr.browser == null) {
				if (!mcefReady || createdInWindow >= WebDisplays.INSTANCE.maxBrowsersPerFrame) {
					continue;
				}
				createdInWindow++;
				scr.createBrowser(true);
				if (scr.browser == null) {
					continue;
				}
			}

			if (scr.browser instanceof MCEFBrowser mb) {
				mb.setVisible(true);
				Identifier texLoc = mb.getRenderer().getTextureLocation();
				if (texLoc == null) {
					continue;
				}

				float turnOnScale = 1.0f;
				if (scr.doTurnOnAnim) {
					long lt = nowMs - scr.turnOnTime;
					float ft = ((float) lt) / 100.0f;

					if (ft >= 1.0f) {
						ft = 1.0f;
						scr.doTurnOnAnim = false;
					}
					turnOnScale = ft;
				}

				float sw = ((float) scr.size.x) * 0.5f - 2.f / 16.f;
				float sh = ((float) scr.size.y) * 0.5f - 2.f / 16.f;

				if (scr.rotation.isVertical) {
					float tmp = sw;
					sw = sh;
					sh = tmp;
				}

				state.quads.add(new ScreenQuad(
						texLoc,
						scr.side,
						mid.x, mid.y, mid.z,
						sw, sh,
						turnOnScale,
						!scr.rotation.isNull,
						scr.rotation.angle
				));
			}
		}
	}

	@Override
	public void submit(ScreenRenderState state, @NotNull PoseStack poseStack, @NotNull SubmitNodeCollector submitNodeCollector, @NotNull CameraRenderState cameraRenderState) {
		for (ScreenQuad q : state.quads) {
			poseStack.pushPose();
			poseStack.translate(q.midX(), q.midY(), q.midZ());

			switch (q.side()) {
				case BOTTOM -> poseStack.mulPose(XP.rotation(90.f + 49.8f));
				case TOP -> poseStack.mulPose(XN.rotation(90.f + 49.8f));
				case NORTH -> poseStack.mulPose(YN.rotationDegrees(180.f));
				case SOUTH -> {}
				case WEST -> poseStack.mulPose(YN.rotationDegrees(90.f));
				case EAST -> poseStack.mulPose(YP.rotationDegrees(90.f));
			}

			if (q.turnOnScale() < 1.0f) {
				poseStack.scale(q.turnOnScale(), q.turnOnScale(), 1.0f);
			}

			if (q.hasRotation()) {
				poseStack.mulPose(ZP.rotationDegrees(q.rotationAngle()));
			}

			final float sw = q.sw();
			final float sh = q.sh();
			submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.armorCutoutNoCull(q.textureId()), (pose, vc) -> {
				vc.addVertex(pose, -sw, -sh, 0.505f).setColor(255, 255, 255, 255).setUv(0.f, 1.f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.f, 0.f, 1.f);
				vc.addVertex(pose, sw, -sh, 0.505f).setColor(255, 255, 255, 255).setUv(1.f, 1.f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.f, 0.f, 1.f);
				vc.addVertex(pose, sw, sh, 0.505f).setColor(255, 255, 255, 255).setUv(1.f, 0.f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.f, 0.f, 1.f);
				vc.addVertex(pose, -sw, sh, 0.505f).setColor(255, 255, 255, 255).setUv(0.f, 0.f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.f, 0.f, 1.f);
			});

			poseStack.popPose();
		}
	}
}
