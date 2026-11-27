package net.montoyo.wd.client.renderers;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import static com.mojang.math.Axis.*;

@Environment(EnvType.CLIENT)
public final class SmartphoneRenderer implements IItemRenderer {
	private static final float PI = (float) Math.PI;
	private final Minecraft mc = Minecraft.getInstance();
	private final ModelSmartphone model = new ModelSmartphone();
	private final ClientProxy clientProxy = (ClientProxy) WebDisplays.PROXY;
	private final Map<CustomData, UUID> padIdCache = Collections.synchronizedMap(new WeakHashMap<>());

	private float sinSqrtSwingProg1;
	private float sinSqrtSwingProg2;
	private float sinSwingProg1;
	private float sinSwingProg2;

	private UUID extractPadId(CustomData data) {
		UUID cached = padIdCache.get(data);
		if (cached != null)
			return cached;

		var tag = data.copyTag();
		if (!tag.contains("PadID"))
			return null;

		UUID id = tag.getUUID("PadID");
		padIdCache.put(data, id);
		return id;
	}

	@Override
	public boolean render(PoseStack stack, ItemStack is, float handSideSign, float swingProgress, float equipProgress, MultiBufferSource multiBufferSource, int packedLight) {
		float sqrtSwingProg = (float) Math.sqrt(swingProgress);
		sinSqrtSwingProg1 = (float) Math.sin(sqrtSwingProg * PI);
		sinSqrtSwingProg2 = (float) Math.sin(sqrtSwingProg * PI * 2.0f);
		sinSwingProg1 = (float) Math.sin(swingProgress * PI);
		sinSwingProg2 = (float) Math.sin(swingProgress * swingProgress * PI);

		if (mc.player != null && !mc.player.isInvisible()) {
			stack.pushPose();
			renderArmFirstPerson(stack, multiBufferSource, packedLight, equipProgress, handSideSign);
			stack.popPose();
		}

		stack.pushPose();
		stack.translate(handSideSign * -0.4f * sinSqrtSwingProg1, 0.2f * sinSqrtSwingProg2, -0.2f * sinSwingProg1);
		stack.translate(handSideSign * 0.52f, -0.48f - equipProgress * 0.6f, -0.78f);
		stack.mulPose(YP.rotationDegrees(handSideSign * (18.0f - sinSwingProg2 * 12.0f)));
		stack.mulPose(ZP.rotationDegrees(handSideSign * sinSqrtSwingProg1 * -14.0f));
		stack.mulPose(XP.rotationDegrees(sinSqrtSwingProg1 * -55.0f - 8.0f));

		if (handSideSign >= 0)
			stack.translate(-0.24f, 0.06f, 0.0f);
		else
			stack.translate(-0.18f, 0.06f, 0.0f);

		model.render(multiBufferSource, stack, packedLight);

		if (multiBufferSource instanceof MultiBufferSource.BufferSource bs)
			bs.endBatch();

		CustomData data = is.get(DataComponents.CUSTOM_DATA);
		if (MCEF.isInitialized() && data != null && data.contains("PadID")) {
			UUID padId = extractPadId(data);
			ClientProxy.PadData pd = padId == null ? null : clientProxy.getPadByID(padId);

			if (pd != null && pd.view instanceof MCEFBrowser browser) {
				int texId = browser.getRenderer().getTextureID();
				if (texId != 0) {
					RenderSystem.enableDepthTest();
					RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
					RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
					RenderSystem.setShaderTexture(0, texId);
					BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
					var mat = stack.last().pose();
					buffer.addVertex(mat, ModelSmartphone.SCREEN_X1, ModelSmartphone.SCREEN_Y1, 0.002f).setUv(0.0F, 1.0F).setColor(255, 255, 255, 255);
					buffer.addVertex(mat, ModelSmartphone.SCREEN_X2, ModelSmartphone.SCREEN_Y1, 0.002f).setUv(1.0F, 1.0F).setColor(255, 255, 255, 255);
					buffer.addVertex(mat, ModelSmartphone.SCREEN_X2, ModelSmartphone.SCREEN_Y2, 0.002f).setUv(1.0F, 0.0F).setColor(255, 255, 255, 255);
					buffer.addVertex(mat, ModelSmartphone.SCREEN_X1, ModelSmartphone.SCREEN_Y2, 0.002f).setUv(0.0F, 0.0F).setColor(255, 255, 255, 255);
					BufferUploader.drawWithShader(buffer.buildOrThrow());
				}
			}
		}

		stack.popPose();
		RenderSystem.enableCull();

		return true;
	}

	private void renderArmFirstPerson(PoseStack stack, MultiBufferSource buffer, int combinedLight, float equipProgress, float handSideSign) {
		float tx = -0.3f * sinSqrtSwingProg1;
		float ty = 0.4f * sinSqrtSwingProg2;
		float tz = -0.4f * sinSwingProg1;

		stack.translate(handSideSign * (tx + 0.64000005f), ty - 0.6f - equipProgress * 0.6f, tz - 0.71999997f);
		stack.mulPose(YP.rotationDegrees(handSideSign * 45.0f));
		stack.mulPose(YP.rotationDegrees(handSideSign * sinSqrtSwingProg1 * 70.0f));
		stack.mulPose(ZP.rotationDegrees(handSideSign * sinSwingProg2 * -20.0f));
		stack.translate(-handSideSign, 3.6f, 3.5f);
		stack.mulPose(ZP.rotationDegrees(handSideSign * 120.0f));
		stack.mulPose(XP.rotationDegrees(200.0f));
		stack.mulPose(YP.rotationDegrees(handSideSign * -135.0f));
		stack.translate(handSideSign * 5.6f, 0.0f, 0.0f);

		PlayerRenderer playerRenderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
		RenderSystem.setShaderTexture(0, mc.player.getSkin().texture());

		if (handSideSign >= 0.0f)
			playerRenderer.renderRightHand(stack, buffer, combinedLight, mc.player);
		else
			playerRenderer.renderLeftHand(stack, buffer, combinedLight, mc.player);
	}
}
