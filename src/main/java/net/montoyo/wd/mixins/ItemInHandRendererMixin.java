package net.montoyo.wd.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.montoyo.wd.client.ClientProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces Forge's RenderHandEvent for the first person hands before vanilla applies arm transforms.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void webdisplays$renderCustomHeldItem(AbstractClientPlayer player, float partialTicks, float pitch,
												  InteractionHand hand, float swingProgress, ItemStack stack,
												  float equipProgress, PoseStack poseStack, MultiBufferSource bufferSource,
												  int combinedLight, CallbackInfo ci) {
		if (stack.isEmpty())
			return;

		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		boolean left = arm == HumanoidArm.LEFT;
		if (ClientProxy.renderFirstPersonItem(stack, left, swingProgress, equipProgress, poseStack, bufferSource, combinedLight))
			ci.cancel();
	}
}
