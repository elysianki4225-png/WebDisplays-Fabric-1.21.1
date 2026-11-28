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

import com.cinemamod.mcef.listeners.MCEFInitListener;
import net.minecraft.client.Minecraft;
import org.cef.misc.CefCursorType;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An API to create Chromium web browsers in Minecraft.
 */
public final class MCEF {
    public static final Logger LOGGER = LoggerFactory.getLogger("MCEF");
    private static MCEFSettings settings;
    private static MCEFApp app;
    private static MCEFClient client;

    private static final ArrayList<MCEFInitListener> awaitingInit = new ArrayList<>();
    private static final AtomicInteger activeBrowsers = new AtomicInteger(0);

    public static void scheduleForInit(MCEFInitListener task) {
        if (isInitialized()) {
            task.onInit(true);
            return;
        }
        awaitingInit.add(task);
    }

    static void incrementActiveBrowsers() {
        activeBrowsers.incrementAndGet();
    }

    static void decrementActiveBrowsers() {
        activeBrowsers.updateAndGet(v -> Math.max(0, v - 1));
    }

    public static boolean hasActiveBrowsers() {
        return activeBrowsers.get() > 0;
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static MCEFSettings getSettings() {
        if (settings == null) {
            settings = new MCEFSettings();
            try {
                settings.load();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return settings;
    }

    /**
     * Called by {@link com.cinemamod.mcef.mixins.CefInitMixin}.
     */
    public static boolean initialize() {
        if (isInitialized()) {
            return true;
        }
        MCEFPlatform platform = MCEFPlatform.tryGetPlatform();
        String platformName = platform != null ? platform.getNormalizedName()
                : (System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        MCEF.getLogger().info("Initializing CEF on {}...", platformName);

        if (platform != null && !MCEFPlatform.isAndroid() && CefUtil.init()) {
            app = new MCEFApp(CefUtil.getCefApp());
            client = new MCEFClient(CefUtil.getCefClient());

            awaitingInit.forEach(t -> t.onInit(true));
            awaitingInit.clear();
            MCEF.getLogger().info("Chromium Embedded Framework initialized");

            app.getHandle().registerSchemeHandlerFactory(
                    "mod", "",
                    (browser, frame, url, request) -> new ModScheme(request.getURL())
            );

            if (platform.isLinux() || platform.isWindows()) {
                Runtime.getRuntime().addShutdownHook(new Thread(MCEF::shutdown, "MCEF-Shutdown"));
            } else if (platform.isMacOS()) {
                CefUtil.getCefApp().macOSTerminationRequestRunnable = () -> {
                    shutdown();
                    Minecraft.getInstance().stop();
                };
            }

            return true;
        }
        awaitingInit.forEach(t -> t.onInit(false));
        awaitingInit.clear();
        MCEF.getLogger().error("Could not initialize Chromium Embedded Framework");
        shutdown();
        return false;
    }

    public static MCEFApp getApp() {
        assertInitialized();
        return app;
    }

    public static MCEFClient getClient() {
        assertInitialized();
        return client;
    }

    public static MCEFBrowser createBrowser(String url, boolean transparent) {
        assertInitialized();
        MCEFBrowser browser = new MCEFBrowser(client, url, transparent);
        browser.setCloseAllowed();
        browser.createImmediately();
        return browser;
    }

    public static MCEFBrowser createBrowser(String url, boolean transparent, int width, int height) {
        return createBrowser(url, transparent, width, height, false);
    }

    public static MCEFBrowser createBrowser(String url, boolean transparent, int width, int height, boolean mobile) {
        assertInitialized();
        MCEFBrowser browser = new MCEFBrowser(client, url, transparent);
        browser.setMobileMode(mobile);
        browser.setCloseAllowed();
        browser.createImmediately();
        browser.resize(width, height);
        return browser;
    }

    public static boolean isInitialized() {
        return client != null;
    }

    public static void shutdown() {
        if (isInitialized()) {
            CefUtil.shutdown();
            client = null;
            app = null;
        }
    }

    private static void assertInitialized() {
        if (!isInitialized())
            throw new RuntimeException("Chromium Embedded Framework was never initialized.");
    }

    public static String getJavaCefCommit() throws IOException {
        if (System.getProperty("mcef.java.cef.commit") != null) {
            return System.getProperty("mcef.java.cef.commit");
        }

        Enumeration<URL> resources = MCEF.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
        Map<String, String> commits = new HashMap<>(1);
        resources.asIterator().forEachRemaining(resource -> {
            Properties properties = new Properties();
            try (var stream = resource.openStream()) {
                properties.load(stream);
                if (properties.containsKey("java-cef-commit")) {
                    commits.put(resource.getFile(), properties.getProperty("java-cef-commit"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        if (!commits.isEmpty()) {
            return commits.values().iterator().next();
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("git", "submodule", "status", "common/java-cef");
            processBuilder.directory(new File("../../"));
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split(" ");
                    if (parts.length > 0 && !parts[0].isEmpty()) {
                        return parts[0].replace("+", "");
                    }
                }
            }
        } catch (IOException ignored) {
        }

        // Fallback commit hash matching bundled CinemaMod java-cef 1.21.1
        return "eaeb3d4370aa3526ee237ad1981ad59af3de4dd1";
    }

    static long getGLFWCursorHandle(CefCursorType cursorType) {
        if (CEF_TO_GLFW_CURSORS.containsKey(cursorType)) return CEF_TO_GLFW_CURSORS.get(cursorType);
        long glfwCursorHandle = GLFW.glfwCreateStandardCursor(cursorType.glfwId);
        CEF_TO_GLFW_CURSORS.put(cursorType, glfwCursorHandle);
        return glfwCursorHandle;
    }
    private static final HashMap<CefCursorType, Long> CEF_TO_GLFW_CURSORS = new HashMap<>();
}
