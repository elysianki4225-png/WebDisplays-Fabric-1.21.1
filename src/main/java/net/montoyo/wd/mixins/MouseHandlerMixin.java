package net.montoyo.wd.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.montoyo.wd.init.ItemInit;
import net.montoyo.wd.item.ItemLaserPointer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow @Final private Minecraft minecraft;

	@Inject(at = @At("HEAD"), method = "onPress", cancellable = true)
	public void prePress(long window, int button, int action, int mods, CallbackInfo ci) {
		if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE)
			return;

		if (minecraft.screen == null && minecraft.player != null && minecraft.level != null) {
			if (minecraft.player.getItemInHand(InteractionHand.MAIN_HAND).getItem().equals(ItemInit.LASER_POINTER.get())
					&& (minecraft.hitResult == null || minecraft.hitResult.getType() == HitResult.Type.BLOCK || minecraft.hitResult.getType() == HitResult.Type.MISS)) {
				ItemLaserPointer.press(action == GLFW.GLFW_PRESS, button);
				ci.cancel();
			}
		}
	}
}
