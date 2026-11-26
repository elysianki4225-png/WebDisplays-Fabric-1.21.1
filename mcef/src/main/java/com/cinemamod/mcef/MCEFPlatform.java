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

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Locale;

public enum MCEFPlatform {
    LINUX_AMD64,
    LINUX_ARM64,
    WINDOWS_AMD64,
    WINDOWS_ARM64,
    MACOS_AMD64,
    MACOS_ARM64;

    public String getNormalizedName() {
        return name().toLowerCase(Locale.US);
    }

    public boolean isLinux() {
        return (this == LINUX_AMD64 || this == LINUX_ARM64);
    }

    public boolean isWindows() {
        return (this == WINDOWS_AMD64 || this == WINDOWS_ARM64);
    }

    public boolean isMacOS() {
        return (this == MACOS_AMD64 || this == MACOS_ARM64);
    }

    public boolean isArm64() {
        return (this == LINUX_ARM64 || this == WINDOWS_ARM64 || this == MACOS_ARM64);
    }

    public boolean isAmd64() {
        return (this == LINUX_AMD64 || this == WINDOWS_AMD64 || this == MACOS_AMD64);
    }

    public static boolean isAndroid() {
        if (System.getenv("ANDROID_ROOT") != null || System.getenv("ANDROID_DATA") != null) {
            return true;
        }
        if (System.getenv("POJAV_ENVIRON") != null
                || System.getenv("ZALITH_DIR") != null
                || System.getenv("FCL_NATIVEDIR") != null) {
            return true;
        }
        return new File("/system/build.prop").exists();
    }

    public static boolean isSupported() {
        return tryGetPlatform() != null;
    }

    @Nullable
    public static MCEFPlatform tryGetPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.US);

        boolean isAmd64 = arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64");
        boolean isArm64 = arch.equals("aarch64") || arch.equals("arm64")
                || arch.equals("arm64-v8a") || arch.startsWith("armv8");

        if (os.startsWith("linux")) {
            if (isAmd64) {
                return LINUX_AMD64;
            } else if (isArm64) {
                return LINUX_ARM64;
            }
        } else if (os.startsWith("windows")) {
            if (isAmd64) {
                return WINDOWS_AMD64;
            } else if (isArm64) {
                return WINDOWS_ARM64;
            }
        } else if (os.startsWith("mac") || os.contains("darwin")) {
            if (isAmd64) {
                return MACOS_AMD64;
            } else if (isArm64) {
                return MACOS_ARM64;
            }
        }

        return null;
    }

    public static MCEFPlatform getPlatform() {
        MCEFPlatform platform = tryGetPlatform();
        if (platform != null) {
            return platform;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.US);
        throw new RuntimeException("Unsupported platform: " + os + " " + arch);
    }
}
