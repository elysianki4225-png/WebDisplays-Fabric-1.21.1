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
        long minIntervalNs;
        if (MCEF.hasVisibleBrowsers() || (MCEF.hasActiveBrowsers() && MCEF.hasRecentActivity(2_000_000_000L))) {
            minIntervalNs = 1_000_000_000L / Math.max(10, MCEF.getSettings().getMaxMessageLoopFps());
        } else if (MCEF.hasActiveBrowsers()) {
            minIntervalNs = 100_000_000L; // 10 Hz when all browsers are culled/hidden
        } else {
            minIntervalNs = 200_000_000L; // 5 Hz when idle with no browsers
        }

        if (now - lastMessageLoopNs >= minIntervalNs) {
            lastMessageLoopNs = now;
            MCEF.getApp().getHandle().N_DoMessageLoopWork();
        }
    }
}
