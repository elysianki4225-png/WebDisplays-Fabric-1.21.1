package net.montoyo.wd.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.item.ItemMinePad2;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererMixin {
    @Shadow
    private void renderPlayerArm(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        float equipProgress,
        float swingProgress,
        HumanoidArm arm,
        PlayerRenderState playerState
    ) {}

    @Inject(method = "submitArmWithItem", at = @At("TAIL"))
    private void wd$renderSmartphoneTapArm(
        PlayerRenderState playerState,
        FirstPersonHandsAndItemsRenderState handState,
        float partialTicks,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack stack,
        float equippedProgress,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        CallbackInfo ci
    ) {
        if (!(stack.getItem() instanceof ItemMinePad2 padItem) || !padItem.isMobileLayout()) {
            return;
        }

        float progress = ClientProxy.getSmartphoneTapProgress();
        if (progress <= 0f) {
            return;
        }

        float tap = (float) Math.sin(progress * Math.PI);
        HumanoidArm mainArm = playerState.avatarRenderState.mainArm;
        HumanoidArm holdingArm = (hand == InteractionHand.MAIN_HAND) ? mainArm : mainArm.getOpposite();
        HumanoidArm oppositeArm = holdingArm.getOpposite();
        int oppSign = (oppositeArm == HumanoidArm.RIGHT) ? 1 : -1;

        poseStack.pushPose();
        poseStack.translate(oppSign * (0.16f - 0.14f * tap), -0.34f + 0.12f * tap, -0.52f + 0.14f * tap);
        poseStack.mulPose(new Matrix4f().rotate(Axis.YP.rotationDegrees(oppSign * (-22.0f + 16.0f * tap))));
        poseStack.mulPose(new Matrix4f().rotate(Axis.XP.rotationDegrees(42.0f + 18.0f * tap)));
        poseStack.scale(0.65f, 0.65f, 0.65f);
        this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, 0.0f, 0.0f, oppositeArm, playerState);
        poseStack.popPose();
    }
}
