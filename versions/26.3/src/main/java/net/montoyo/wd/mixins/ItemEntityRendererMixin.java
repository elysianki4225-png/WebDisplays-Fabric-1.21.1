package net.montoyo.wd.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.client.renderers.DroppedPadStateAccess;
import net.montoyo.wd.item.ItemMinePad2;
import net.montoyo.wd.utilities.NbtCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void wd$extractPadState(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        if (!(state instanceof DroppedPadStateAccess access)) return;
        ItemStack stack = entity.getItem();
        if (stack.getItem() instanceof ItemMinePad2 padItem) {
            access.wd$setIsPad(true);
            access.wd$setIsMobile(padItem.isMobileLayout());
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            UUID padId = null;
            if (data != null) {
                CompoundTag tag = data.copyTag();
                if (NbtCompat.hasUUID(tag, "PadID")) {
                    padId = NbtCompat.getUUID(tag, "PadID");
                }
            }
            access.wd$setPadId(padId);
        } else {
            access.wd$setIsPad(false);
            access.wd$setIsMobile(false);
            access.wd$setPadId(null);
        }
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void wd$renderFlatPad(
        ItemEntityRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        CameraRenderState cameraRenderState,
        CallbackInfo ci
    ) {
        if (state instanceof DroppedPadStateAccess access && access.wd$isPad()) {
            float spinYaw = state.ageInTicks / 20.0f + state.bobOffset;
            ClientProxy.renderDroppedPad(
                access.wd$getPadId(),
                access.wd$isMobile(),
                state.lightCoords,
                spinYaw,
                poseStack,
                submitNodeCollector
            );
            ci.cancel();
        }
    }
}
