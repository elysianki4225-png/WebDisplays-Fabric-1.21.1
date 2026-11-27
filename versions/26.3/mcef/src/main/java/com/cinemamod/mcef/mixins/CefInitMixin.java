package com.cinemamod.mcef.mixins;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.internal.MCEFDownloadListener;
import com.cinemamod.mcef.internal.MCEFDownloaderMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class CefInitMixin {
    @Shadow
    public abstract void setScreen(@Nullable Screen guiScreen);

    @Unique
    private static final ThreadLocal<Boolean> recursionDetector = ThreadLocal.withInitial(() -> false);
    @Unique
    private static boolean mcefInitScheduled = false;

    @Inject(at = @At("HEAD"), method = "setScreen", cancellable = true)
    public void redirScreen(Screen guiScreen, CallbackInfo ci) {
        if (!MCEF.isInitialized() && !mcefInitScheduled) {
            boolean recursionValue = recursionDetector.get();
            recursionDetector.set(true);

            if (guiScreen instanceof TitleScreen
                    || guiScreen instanceof SelectWorldScreen
                    || guiScreen instanceof CreateWorldScreen
                    || guiScreen instanceof JoinMultiplayerScreen) {
                if (MCEFDownloadListener.INSTANCE.isDone() && !MCEFDownloadListener.INSTANCE.isFailed()) {
                    mcefInitScheduled = true;
                    MCEF.getLogger().debug("MCEF download complete, initializing CEF...");
                    Minecraft.getInstance().execute(MCEF::initialize);
                } else if (!MCEFDownloadListener.INSTANCE.isDone() && !MCEFDownloadListener.INSTANCE.isFailed() && !recursionValue) {
                    MCEF.getLogger().debug("MCEF still downloading, displaying download screen.");
                    setScreen(new MCEFDownloaderMenu(guiScreen));
                    ci.cancel();
                } else if (MCEFDownloadListener.INSTANCE.isFailed()) {
                    mcefInitScheduled = true;
                    MCEF.getLogger().warn("MCEF download/initialization skipped or failed; browser screens will stay in safe fallback mode.");
                }
            }

            recursionDetector.set(recursionValue);
        }
    }
}
