package net.montoyo.wd.client.renderers;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.utilities.NbtCompat;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class SmartphoneRenderer implements SpecialModelRenderer<UUID> {
	public static final Identifier RENDERER_ID = Identifier.fromNamespaceAndPath("webdisplays", "smartphone");
	private final ModelSmartphone model = new ModelSmartphone();

	public static void register() {
		SpecialModelRenderers.ID_MAPPER.put(RENDERER_ID, Unbaked.MAP_CODEC);
	}

	@Override
	public @Nullable UUID extractArgument(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null)
			return null;
		CompoundTag tag = data.copyTag();
		return NbtCompat.hasUUID(tag, "PadID") ? NbtCompat.getUUID(tag, "PadID") : null;
	}

	@Override
	public void submit(@Nullable UUID padId, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
		poseStack.pushPose();
		poseStack.translate(0.5f - ModelSmartphone.BODY_W * 0.5f, 0.5f - ModelSmartphone.BODY_H * 0.5f, 0.501f);
		model.submit(submitNodeCollector, poseStack, lightCoords);

		if (padId != null && MCEF.isInitialized() && WebDisplays.PROXY instanceof ClientProxy clientProxy) {
			ClientProxy.PadData pd = clientProxy.getPadByID(padId);
			if (pd != null && pd.view instanceof MCEFBrowser browser && browser.getRenderer().isReady()) {
				Identifier texLoc = browser.getRenderer().getTextureIdentifier();
				if (texLoc != null) {
					submitNodeCollector.order(1).submitCustomGeometry(poseStack, RenderTypes.armorCutoutNoCull(texLoc), (pose, vc) -> {
						vc.addVertex(pose, ModelSmartphone.SCREEN_X1, ModelSmartphone.SCREEN_Y1, 0.002f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
						vc.addVertex(pose, ModelSmartphone.SCREEN_X2, ModelSmartphone.SCREEN_Y1, 0.002f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
						vc.addVertex(pose, ModelSmartphone.SCREEN_X2, ModelSmartphone.SCREEN_Y2, 0.002f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
						vc.addVertex(pose, ModelSmartphone.SCREEN_X1, ModelSmartphone.SCREEN_Y2, 0.002f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
					});
				}
			}
		}

		poseStack.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<UUID> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<UUID> bake(SpecialModelRenderer.BakingContext context) {
			return new SmartphoneRenderer();
		}
	}
}
