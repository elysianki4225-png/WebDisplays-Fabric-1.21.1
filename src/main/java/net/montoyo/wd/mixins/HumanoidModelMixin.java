package net.montoyo.wd.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.client.gui.GuiMinePad;
import net.montoyo.wd.item.ItemMinePad2;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin<T extends LivingEntity> {
	@Shadow @Final public ModelPart rightArm;
	@Shadow @Final public ModelPart leftArm;

	@Inject(
			method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
			at = @At("TAIL")
	)
	private void webdisplays$poseSmartphoneArm(
			T entity,
			float limbSwing,
			float limbSwingAmount,
			float ageInTicks,
			float netHeadYaw,
			float headPitch,
			CallbackInfo ci
	) {
		if (!(entity instanceof Player player))
			return;

		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		boolean mainPhone = main.getItem() instanceof ItemMinePad2 p && p.isMobileLayout();
		boolean offPhone = off.getItem() instanceof ItemMinePad2 p && p.isMobileLayout();
		if (!mainPhone && !offPhone)
			return;

		Minecraft mc = Minecraft.getInstance();
		boolean isLocalActive = player == mc.player && (mc.screen instanceof GuiMinePad || ClientProxy.getSmartphoneTapProgress() > 0f);
		float tapWave = isLocalActive ? Mth.sin(ageInTicks * 0.32f) * 0.045f : 0f;
		float tapDip = (player == mc.player) ? Mth.sin(ClientProxy.getSmartphoneTapProgress() * (float) Math.PI) * 0.20f : 0f;
		float swingDip = player.swinging ? Mth.sin((player.swingTime / 6.0f) * (float) Math.PI) * 0.18f : 0f;

		if (mainPhone) {
			boolean right = player.getMainArm() == HumanoidArm.RIGHT;
			ModelPart arm = right ? rightArm : leftArm;
			applyPhonePose(arm, right, headPitch, isLocalActive || player.swinging, tapWave + tapDip + swingDip);
		}
		if (offPhone) {
			boolean right = player.getMainArm() != HumanoidArm.RIGHT;
			ModelPart arm = right ? rightArm : leftArm;
			applyPhonePose(arm, right, headPitch, isLocalActive || player.swinging, tapWave + tapDip + swingDip);
		}
	}

	private static void applyPhonePose(ModelPart arm, boolean isRight, float headPitch, boolean active, float motion) {
		arm.xRot = -0.88f + Mth.clamp(headPitch, -45f, 45f) * 0.008f;
		arm.yRot = isRight ? -0.34f : 0.34f;
		arm.zRot = isRight ? -0.08f : 0.08f;
		if (active) {
			arm.xRot -= 0.14f + motion;
			arm.yRot += isRight ? -0.06f : 0.06f;
		}
	}
}
