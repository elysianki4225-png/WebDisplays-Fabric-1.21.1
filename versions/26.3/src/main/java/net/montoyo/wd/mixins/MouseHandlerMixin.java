package net.montoyo.wd.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.montoyo.wd.client.ScreenInputController;
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
		if (window != 0L && window != minecraft.getWindow().handle()) {
			return;
		}
		if (ScreenInputController.onMouseButton(minecraft, (MouseHandler) (Object) this, buttonInfo, action)) {
			ci.cancel();
		}
	}

	@Inject(at = @At("HEAD"), method = "onScroll", cancellable = true)
	public void preScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (window != 0L && window != minecraft.getWindow().handle()) {
			return;
		}
		if (ScreenInputController.onMouseScroll(minecraft, xOffset, yOffset)) {
			ci.cancel();
		}
	}
}
