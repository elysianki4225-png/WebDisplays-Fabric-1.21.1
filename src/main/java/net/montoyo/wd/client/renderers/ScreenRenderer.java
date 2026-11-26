/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.entity.TileEntityScreen;
import net.montoyo.wd.utilities.Vector3f;
import net.montoyo.wd.utilities.Vector3i;
import org.jetbrains.annotations.NotNull;

import static com.mojang.math.Axis.*;

public class ScreenRenderer implements BlockEntityRenderer<TileEntityScreen> {
	public ScreenRenderer() {
	}

	public static class ScreenRendererProvider implements BlockEntityRendererProvider<TileEntityScreen> {
		@Override
		public @NotNull BlockEntityRenderer<TileEntityScreen> create(@NotNull Context arg) {
			return new ScreenRenderer();
		}
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
	public void render(TileEntityScreen te, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (!te.isLoaded())
			return;

		int screenCount = te.screenCount();
		if (screenCount == 0)
			return;

		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.getMainCamera();
		Vec3 camPos = camera.getPosition();
		BlockPos bp = te.getBlockPos();
		long nowMs = System.currentTimeMillis();
		long nowNs = System.nanoTime();
		if (nowNs - windowStartNs >= 16_666_667L) {
			windowStartNs = nowNs;
			createdInWindow = 0;
		}

		boolean mcefReady = MCEF.isInitialized();
		RenderSystem.disableBlend();

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

			// Backface culling: skip rendering & browser painting if camera is strictly behind screen plane
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
				int texId = mb.getRenderer().getTextureID();
				if (texId == 0) {
					continue;
				}

				poseStack.pushPose();
				poseStack.translate(mid.x, mid.y, mid.z);

				switch (scr.side) {
					case BOTTOM -> poseStack.mulPose(XP.rotation(90.f + 49.8f));
					case TOP -> poseStack.mulPose(XN.rotation(90.f + 49.8f));
					case NORTH -> poseStack.mulPose(YN.rotationDegrees(180.f));
					case SOUTH -> {}
					case WEST -> poseStack.mulPose(YN.rotationDegrees(90.f));
					case EAST -> poseStack.mulPose(YP.rotationDegrees(90.f));
				}

				if (scr.doTurnOnAnim) {
					long lt = nowMs - scr.turnOnTime;
					float ft = ((float) lt) / 100.0f;

					if (ft >= 1.0f) {
						ft = 1.0f;
						scr.doTurnOnAnim = false;
					}

					poseStack.scale(ft, ft, 1.0f);
				}

				if (!scr.rotation.isNull)
					poseStack.mulPose(ZP.rotationDegrees(scr.rotation.angle));

				float sw = ((float) scr.size.x) * 0.5f - 2.f / 16.f;
				float sh = ((float) scr.size.y) * 0.5f - 2.f / 16.f;

				if (scr.rotation.isVertical) {
					float tmp = sw;
					sw = sh;
					sh = tmp;
				}

				RenderSystem.enableDepthTest();
				RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
				RenderSystem.setShaderTexture(0, texId);
				RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
				BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
				var mat = poseStack.last().pose();
				builder.addVertex(mat, -sw, -sh, 0.505f).setUv(0.f, 1.f).setColor(1.f, 1.f, 1.f, 1.f);
				builder.addVertex(mat, sw, -sh, 0.505f).setUv(1.f, 1.f).setColor(1.f, 1.f, 1.f, 1.f);
				builder.addVertex(mat, sw, sh, 0.505f).setUv(1.f, 0.f).setColor(1.f, 1.f, 1.f, 1.f);
				builder.addVertex(mat, -sw, sh, 0.505f).setUv(0.f, 0.f).setColor(1.f, 1.f, 1.f, 1.f);
				BufferUploader.drawWithShader(builder.buildOrThrow());

				poseStack.popPose();
			}
		}

		RenderSystem.enableDepthTest();
	}
}
