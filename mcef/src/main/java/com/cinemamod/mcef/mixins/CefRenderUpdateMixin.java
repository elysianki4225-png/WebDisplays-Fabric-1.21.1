/*
 *     MCEF (Minecraft Chromium Embedded Framework)
 *     Copyright (C) 2023 CinemaMod Group
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 */

package com.cinemamod.mcef.mixins;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class CefRenderUpdateMixin {
    @Unique
    private static long lastMessageLoopNs = 0L;

    @Inject(at = @At("HEAD"), method = "render")
    public void preRender(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
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
