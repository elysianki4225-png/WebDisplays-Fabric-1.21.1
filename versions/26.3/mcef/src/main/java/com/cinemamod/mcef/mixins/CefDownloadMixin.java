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
import com.cinemamod.mcef.MCEFDownloader;
import com.cinemamod.mcef.MCEFPlatform;
import com.cinemamod.mcef.MCEFSettings;
import com.cinemamod.mcef.internal.MCEFDownloadListener;
import net.minecraft.client.resources.ClientPackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;

@Mixin(ClientPackSource.class)
public class CefDownloadMixin {
    @Unique
    private static void setupLibraryPath(MCEFPlatform platform) throws IOException {
        final File mcefLibrariesDir;

        File buildDir = new File("../build");
        if (buildDir.exists() && buildDir.isDirectory()) {
            mcefLibrariesDir = new File(buildDir, "mcef-libraries/");
        } else {
            mcefLibrariesDir = new File("mods/mcef-libraries/");
        }

        mcefLibrariesDir.mkdirs();

        System.setProperty("mcef.libraries.path", mcefLibrariesDir.getCanonicalPath());
        System.setProperty("jcef.path", new File(mcefLibrariesDir, platform.getNormalizedName()).getCanonicalPath());
    }

    @Inject(at = @At("HEAD"), method = "<clinit>")
    private static void sinit(CallbackInfo callbackInfo) {
        MCEFPlatform platform = MCEFPlatform.tryGetPlatform();
        if (platform == null || MCEFPlatform.isAndroid()) {
            MCEF.getLogger().warn("Skipping MCEF native download on runtime ({} / {}, android={}).",
                    System.getProperty("os.name"), System.getProperty("os.arch"), MCEFPlatform.isAndroid());
            MCEFDownloadListener.INSTANCE.setFailed(true);
            return;
        }

        try {
            setupLibraryPath(platform);
        } catch (IOException e) {
            MCEF.getLogger().error("Failed to setup MCEF library path", e);
            MCEFDownloadListener.INSTANCE.setFailed(true);
            return;
        }

        Thread downloadThread = new Thread(() -> {
            String javaCefCommit;

            try {
                javaCefCommit = MCEF.getJavaCefCommit();
            } catch (IOException e) {
                MCEF.getLogger().error("Failed to resolve java-cef commit", e);
                MCEFDownloadListener.INSTANCE.setFailed(true);
                return;
            }

            MCEF.getLogger().info("java-cef commit: {} (platform: {})", javaCefCommit, platform.getNormalizedName());

            MCEFSettings settings = MCEF.getSettings();
            MCEFDownloader downloader = new MCEFDownloader(settings.getDownloadMirror(), javaCefCommit, platform);

            boolean downloadJcefBuild;
            try {
                downloadJcefBuild = !downloader.downloadJavaCefChecksum();
            } catch (IOException e) {
                if (downloader.hasExtractedBinaries()) {
                    MCEF.getLogger().warn("Offline or mirror unreachable, using existing cached JCEF binaries.");
                    MCEFDownloadListener.INSTANCE.setDone(true);
                    return;
                }
                MCEF.getLogger().error("Failed to download JCEF checksum.", e);
                MCEFDownloadListener.INSTANCE.setFailed(true);
                return;
            }

            downloadJcefBuild |= !downloader.hasExtractedBinaries();

            if (downloadJcefBuild && !settings.isSkipDownload()) {
                try {
                    downloader.downloadJavaCefBuild();
                } catch (IOException e) {
                    MCEF.getLogger().error("Failed to download JCEF.", e);
                    MCEFDownloadListener.INSTANCE.setFailed(true);
                    return;
                }

                downloader.extractJavaCefBuild(true);
            }

            MCEFDownloadListener.INSTANCE.setDone(true);
        }, "MCEF-Downloader");
        downloadThread.setDaemon(true);
        downloadThread.start();
    }
}
