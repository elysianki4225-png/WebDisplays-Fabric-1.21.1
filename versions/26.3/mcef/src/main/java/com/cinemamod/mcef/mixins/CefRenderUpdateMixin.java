package com.cinemamod.mcef.mixins;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class CefRenderUpdateMixin {
    @Unique
    private static long lastMessageLoopNs = 0L;

    @Inject(at = @At("HEAD"), method = "renderFrame")
    public void preRender(boolean renderLevel, CallbackInfo ci) {
        if (!MCEF.isInitialized()) {
            return;
        }

        // Throttle CEF message-loop pumping to avoid excessive JNI overhead at high FPS.
        long now = System.nanoTime();
        long minIntervalNs = MCEF.hasActiveBrowsers()
                ? 1_000_000_000L / Math.max(10, MCEF.getSettings().getMaxMessageLoopFps())
                : 100_000_000L;

        if (now - lastMessageLoopNs >= minIntervalNs) {
            lastMessageLoopNs = now;
            MCEF.getApp().getHandle().N_DoMessageLoopWork();
        }
    }
}
