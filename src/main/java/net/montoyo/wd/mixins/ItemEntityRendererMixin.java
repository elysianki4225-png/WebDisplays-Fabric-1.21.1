package net.montoyo.wd.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import net.montoyo.wd.client.ClientProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
	@Inject(
			method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void webdisplays$renderDroppedPad(
			ItemEntity entity,
			float entityYaw,
			float partialTicks,
			PoseStack poseStack,
			MultiBufferSource buffer,
			int packedLight,
			CallbackInfo ci
	) {
		if (ClientProxy.renderDroppedPad(entity, partialTicks, poseStack, buffer, packedLight)) {
			ci.cancel();
		}
	}
}
