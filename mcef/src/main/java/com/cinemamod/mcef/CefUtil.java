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

package com.cinemamod.mcef;

import net.minecraft.client.Minecraft;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/**
 * Internal wrapper around org.cef.* initialization and multi-architecture flags.
 */
final class CefUtil {
    private CefUtil() {
    }

    private static boolean init;
    private static CefApp cefAppInstance;
    private static CefClient cefClientInstance;

    private static final Path CACHE_PATH = Minecraft.getInstance().gameDirectory
            .toPath()
            .resolve("mods")
            .resolve("mcef-cache");

    private static void setUnixExecutable(File file) {
        if (!file.exists()) {
            return;
        }
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);

        try {
            Files.setPosixFilePermissions(file.toPath(), perms);
        } catch (IOException e) {
            MCEF.getLogger().error("Failed to set " + file + " as executable.", e);
        }
    }

    private static boolean canUseLinuxUserNamespaces() {
        Path procUnpriv = Path.of("/proc/sys/kernel/unprivileged_userns_clone");
        if (Files.exists(procUnpriv)) {
            try {
                String v = Files.readString(procUnpriv).trim();
                if ("0".equals(v)) {
                    return false;
                }
            } catch (IOException ignored) {
            }
        }
        return !Files.exists(Path.of("/.dockerenv")) && !Files.exists(Path.of("/run/.containerenv"));
    }

    static boolean init() {
        MCEFPlatform platform = MCEFPlatform.tryGetPlatform();
        if (platform == null || MCEFPlatform.isAndroid()) {
            MCEF.getLogger().warn("Native desktop JCEF is unavailable on this runtime ({} / {}, android={}).",
                    System.getProperty("os.name"), System.getProperty("os.arch"), MCEFPlatform.isAndroid());
            return false;
        }

        String librariesPath = System.getProperty("mcef.libraries.path");
        if (librariesPath == null) {
            MCEF.getLogger().error("mcef.libraries.path system property is not set.");
            return false;
        }

        // Ensure binaries are executable on Unix platforms
        if (platform.isLinux()) {
            File jcefHelperFile = new File(librariesPath, platform.getNormalizedName() + "/jcef_helper");
            setUnixExecutable(jcefHelperFile);
        } else if (platform.isMacOS()) {
            File mcefLibrariesPath = new File(librariesPath);
            String base = platform.getNormalizedName() + "/jcef_app.app/Contents/Frameworks/";
            setUnixExecutable(new File(mcefLibrariesPath, base + "jcef Helper.app/Contents/MacOS/jcef Helper"));
            setUnixExecutable(new File(mcefLibrariesPath, base + "jcef Helper (GPU).app/Contents/MacOS/jcef Helper (GPU)"));
            setUnixExecutable(new File(mcefLibrariesPath, base + "jcef Helper (Plugin).app/Contents/MacOS/jcef Helper (Plugin)"));
            setUnixExecutable(new File(mcefLibrariesPath, base + "jcef Helper (Renderer).app/Contents/MacOS/jcef Helper (Renderer)"));
            setUnixExecutable(new File(mcefLibrariesPath, base + "jcef Helper (Alerts).app/Contents/MacOS/jcef Helper (Alerts)"));
        }

        MCEFSettings settings = MCEF.getSettings();
        List<String> switches = new ArrayList<>();
        switches.add("--autoplay-policy=no-user-gesture-required");
        switches.add("--disable-web-security");
        switches.add("--enable-widevine-cdm");
        // Reduce idle background CPU & memory usage in OSR mode
        switches.add("--disable-background-timer-throttling");
        switches.add("--disable-renderer-backgrounding");
        switches.add("--disable-background-networking");
        switches.add("--disable-component-update");
        switches.add("--disable-domain-reliability");
        switches.add("--disable-breakpad");
        switches.add("--renderer-process-limit=4");
        switches.add("--disable-features=Translate,BackForwardCache,OptimizationHints,MediaRouter,DialMediaRouteProvider");

        // On Linux ARM64 or restricted sandboxes, --no-sandbox prevents zygote crashes
        if (settings.isNoSandbox() || (platform.isLinux() && (platform.isArm64() || !canUseLinuxUserNamespaces()))) {
            switches.add("--no-sandbox");
            switches.add("--disable-setuid-sandbox");
        }

        // In OSR mode, GPU process on ARM64 Linux/Windows often causes white screens or driver crashes;
        // allow explicit toggle and enable safe GPU sandbox bypass on ARM64.
        if (settings.isDisableGpu() || platform == MCEFPlatform.LINUX_ARM64 || platform == MCEFPlatform.WINDOWS_ARM64) {
            switches.add("--disable-gpu");
            switches.add("--disable-gpu-compositing");
        } else {
            switches.add("--enable-gpu-rasterization");
            switches.add("--enable-zero-copy");
            if (platform.isArm64()) {
                switches.add("--disable-gpu-sandbox");
            }
        }

        String[] cefSwitches = switches.toArray(new String[0]);

        if (!CefApp.startup(cefSwitches)) {
            return false;
        }

        CefSettings cefSettings = new CefSettings();
        cefSettings.windowless_rendering_enabled = true;
        if (settings.isUsingCache()) {
            cefSettings.cache_path = CACHE_PATH.toAbsolutePath().toString();
        }
        cefSettings.background_color = cefSettings.new ColorType(0, 255, 255, 255);
        if (settings.getUserAgent() != null && !Objects.equals(settings.getUserAgent(), "null") && !settings.getUserAgent().isBlank()) {
            cefSettings.user_agent = settings.getUserAgent();
        } else {
            cefSettings.user_agent_product = "MCEF/2";
        }

        cefAppInstance = CefApp.getInstance(cefSwitches, cefSettings);
        cefClientInstance = cefAppInstance.createClient();

        return init = true;
    }

    static void shutdown() {
        if (isInit()) {
            init = false;
            try {
                if (cefClientInstance != null) {
                    cefClientInstance.dispose();
                }
            } finally {
                cefClientInstance = null;
            }
            try {
                if (cefAppInstance != null) {
                    cefAppInstance.dispose();
                }
            } finally {
                cefAppInstance = null;
            }
        }
    }

    static boolean isInit() {
        return init;
    }

    static CefApp getCefApp() {
        return cefAppInstance;
    }

    static CefClient getCefClient() {
        return cefClientInstance;
    }
}
