package net.montoyo.wd.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.montoyo.wd.init.ItemInit;
import net.montoyo.wd.item.ItemLaserPointer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow @Final private Minecraft minecraft;

	@Inject(at = @At("HEAD"), method = "onButton", cancellable = true)
	public void preButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
		if (action != InputConstants.PRESS && action != InputConstants.RELEASE)
			return;

		if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.level != null) {
			if (minecraft.player.getItemInHand(InteractionHand.MAIN_HAND).getItem().equals(ItemInit.LASER_POINTER.get())
					&& (minecraft.hitResult == null || minecraft.hitResult.getType() == HitResult.Type.BLOCK || minecraft.hitResult.getType() == HitResult.Type.MISS)) {
				int btn = buttonInfo.button();
				if (btn == InputConstants.MOUSE_BUTTON_LEFT) btn = 0;
				else if (btn == InputConstants.MOUSE_BUTTON_RIGHT) btn = 1;
				else if (btn == InputConstants.MOUSE_BUTTON_MIDDLE) btn = 2;
				ItemLaserPointer.press(action == InputConstants.PRESS, btn);
				ci.cancel();
			}
		}
	}
}
