// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef;

import java.util.Locale;

public class OS {
    private enum OSType {
        OSUndefined,
        OSLinux,
        OSWindows,
        OSMacintosh,
        OSUnknown,
    }

    private static OSType osType = OSType.OSUndefined;

    public static boolean isWindows() {
        return getOSType() == OSType.OSWindows;
    }

    public static boolean isMacintosh() {
        return getOSType() == OSType.OSMacintosh;
    }

    public static boolean isLinux() {
        return getOSType() == OSType.OSLinux;
    }

    public static boolean isArm64() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arch.equals("aarch64") || arch.equals("arm64") || arch.startsWith("armv8");
    }

    private static OSType getOSType() {
        if (osType == OSType.OSUndefined) {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.startsWith("windows"))
                osType = OSType.OSWindows;
            else if (os.startsWith("linux"))
                osType = OSType.OSLinux;
            else if (os.startsWith("mac") || os.contains("darwin"))
                osType = OSType.OSMacintosh;
            else
                osType = OSType.OSUnknown;
        }
        return osType;
    }
}
