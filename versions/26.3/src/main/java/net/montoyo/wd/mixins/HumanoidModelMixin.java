package net.montoyo.wd.mixins;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.ItemStack;
import net.montoyo.wd.item.ItemMinePad2;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void wd$poseSmartphoneArms(HumanoidRenderState state, CallbackInfo ci) {
        boolean rightPhone = isSmartphone(state.rightHandItemStack);
        boolean leftPhone = !rightPhone && isSmartphone(state.leftHandItemStack);
        if (!rightPhone && !leftPhone) {
            return;
        }

        ModelPart holdArm = rightPhone ? this.rightArm : this.leftArm;
        ModelPart tapArm = rightPhone ? this.leftArm : this.rightArm;
        float sideSign = rightPhone ? 1.0f : -1.0f;

        holdArm.xRot = -0.78f + (state.isCrouching ? 0.25f : 0.0f);
        holdArm.yRot = -0.32f * sideSign;
        holdArm.zRot = -0.08f * sideSign;

        float attack = state.swingAnimation;
        if (attack > 0.0f) {
            float tap = (float) Math.sin(attack * Math.PI);
            tapArm.xRot = -0.92f + 0.35f * tap + (state.isCrouching ? 0.25f : 0.0f);
            tapArm.yRot = 0.55f * sideSign;
            tapArm.zRot = 0.12f * sideSign;
            holdArm.xRot += 0.06f * tap;
        }
    }

    private static boolean isSmartphone(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemMinePad2 pad && pad.isMobileLayout();
    }
}
