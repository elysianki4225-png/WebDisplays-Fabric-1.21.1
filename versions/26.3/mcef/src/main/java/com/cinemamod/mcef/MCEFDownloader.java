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

import com.cinemamod.mcef.internal.MCEFDownloadListener;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * A downloader and extraction tool for java-cef builds across x86_64 and ARM64 platforms.
 */
public class MCEFDownloader {
    private static final String JAVA_CEF_DOWNLOAD_URL = "${host}/java-cef-builds/${java-cef-commit}/${platform}.tar.gz";
    private static final String JAVA_CEF_CHECKSUM_DOWNLOAD_URL = "${host}/java-cef-builds/${java-cef-commit}/${platform}.tar.gz.sha256";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_REDIRECTS = 5;
    private static final int BUFFER_SIZE = 64 * 1024;

    private final String host;
    private final String javaCefCommitHash;
    private final MCEFPlatform platform;

    public MCEFDownloader(String host, String javaCefCommitHash, MCEFPlatform platform) {
        this.host = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        this.javaCefCommitHash = javaCefCommitHash;
        this.platform = platform;
    }

    public String getHost() {
        return host;
    }

    public String getJavaCefDownloadUrl() {
        return formatURL(JAVA_CEF_DOWNLOAD_URL);
    }

    public String getJavaCefChecksumDownloadUrl() {
        return formatURL(JAVA_CEF_CHECKSUM_DOWNLOAD_URL);
    }

    private String formatURL(String url) {
        return url
                .replace("${host}", host)
                .replace("${java-cef-commit}", javaCefCommitHash)
                .replace("${platform}", platform.getNormalizedName());
    }

    public void downloadJavaCefBuild() throws IOException {
        File mcefLibrariesPath = new File(System.getProperty("mcef.libraries.path"));
        MCEFDownloadListener.INSTANCE.setTask("Downloading Chromium Embedded Framework (" + platform.getNormalizedName() + ")");
        File tempFile = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz.part");
        File finalFile = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz");
        downloadFileWithRetries(getJavaCefDownloadUrl(), tempFile);
        if (finalFile.exists()) {
            finalFile.delete();
        }
        if (!tempFile.renameTo(finalFile)) {
            FileUtils.copyFile(tempFile, finalFile);
            tempFile.delete();
        }
    }

    public boolean downloadJavaCefChecksum() throws IOException {
        File mcefLibrariesPath = new File(System.getProperty("mcef.libraries.path"));
        File jcefBuildHashFileTemp = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz.sha256.temp");
        File jcefBuildHashFile = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz.sha256");

        MCEFDownloadListener.INSTANCE.setTask("Checking JCEF Build (" + platform.getNormalizedName() + ")");
        downloadFileWithRetries(getJavaCefChecksumDownloadUrl(), jcefBuildHashFileTemp);

        if (jcefBuildHashFile.exists()) {
            boolean sameContent = FileUtils.contentEquals(jcefBuildHashFile, jcefBuildHashFileTemp);
            if (sameContent) {
                jcefBuildHashFileTemp.delete();
                return true;
            } else {
                MCEF.getLogger().warn("JCEF Hash does not match.");
            }
        } else {
            MCEF.getLogger().warn("JCEF hash not cached yet.");
        }

        if (jcefBuildHashFile.exists()) {
            jcefBuildHashFile.delete();
        }
        if (!jcefBuildHashFileTemp.renameTo(jcefBuildHashFile)) {
            FileUtils.copyFile(jcefBuildHashFileTemp, jcefBuildHashFile);
            jcefBuildHashFileTemp.delete();
        }

        return false;
    }

    public void extractJavaCefBuild(boolean delete) {
        File mcefLibrariesPath = new File(System.getProperty("mcef.libraries.path"));
        File tarGzArchive = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz");
        extractTarGz(tarGzArchive, mcefLibrariesPath);
        if (delete && tarGzArchive.exists()) {
            tarGzArchive.delete();
        }
    }

    public boolean hasExtractedBinaries() {
        String jcefPathProp = System.getProperty("jcef.path");
        if (jcefPathProp == null) return false;
        File jcefDir = new File(jcefPathProp);
        if (!jcefDir.isDirectory()) return false;

        if (platform.isWindows()) {
            return new File(jcefDir, "libcef.dll").exists() && new File(jcefDir, "jcef.dll").exists();
        } else if (platform.isLinux()) {
            return new File(jcefDir, "libcef.so").exists() && new File(jcefDir, "libjcef.so").exists();
        } else if (platform.isMacOS()) {
            return new File(jcefDir, "jcef_app.app/Contents/Java/libjcef.dylib").exists();
        }
        return false;
    }

    private static void downloadFileWithRetries(String urlString, File outputFile) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                downloadFile(urlString, outputFile);
                return;
            } catch (IOException e) {
                lastError = e;
                MCEF.getLogger().warn("Download attempt {}/{} failed for {}: {}", attempt, MAX_RETRIES, urlString, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw lastError;
    }

    private static HttpURLConnection openFollowingRedirects(String urlString) throws IOException {
        String current = urlString;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            URL url = URI.create(current).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "MCEF-WebDisplays/2.1");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new IOException("Redirect without Location header from " + current);
                }
                current = URI.create(current).resolve(location).toString();
                continue;
            }
            return conn;
        }
        throw new IOException("Too many redirects for " + urlString);
    }

    private static void downloadFile(String urlString, File outputFile) throws IOException {
        MCEF.getLogger().info("{} -> {}", urlString, outputFile.getCanonicalPath());
        outputFile.getParentFile().mkdirs();

        HttpURLConnection conn = openFollowingRedirects(urlString);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " for " + urlString);
            }

            long fileSize = conn.getContentLengthLong();
            byte[] buffer = new byte[BUFFER_SIZE];
            long readBytes = 0;

            try (InputStream inputStream = new BufferedInputStream(conn.getInputStream(), BUFFER_SIZE);
                 OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile), BUFFER_SIZE)) {
                int count;
                while ((count = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, count);
                    readBytes += count;
                    if (fileSize > 0) {
                        MCEFDownloadListener.INSTANCE.setProgress(Math.min(1.0f, (float) readBytes / (float) fileSize));
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void extractTarGz(File tarGzFile, File outputDirectory) {
        MCEFDownloadListener.INSTANCE.setTask("Extracting Chromium Embedded Framework");
        outputDirectory.mkdirs();

        String canonicalDest;
        try {
            canonicalDest = outputDirectory.getCanonicalPath() + File.separator;
        } catch (IOException e) {
            MCEF.getLogger().error("Failed to resolve extraction directory", e);
            return;
        }

        long fileSize = Math.max(1L, tarGzFile.length());
        long totalBytesRead = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(
                new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(tarGzFile), BUFFER_SIZE)))) {
            TarArchiveEntry entry;
            while ((entry = tarInput.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                File outputFile = new File(outputDirectory, entry.getName());
                if (!outputFile.getCanonicalPath().startsWith(canonicalDest)) {
                    throw new IOException("Blocked unsafe tar entry: " + entry.getName());
                }
                outputFile.getParentFile().mkdirs();

                try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile), BUFFER_SIZE)) {
                    int bytesRead;
                    while ((bytesRead = tarInput.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        float percentComplete = Math.min(0.99f, ((float) totalBytesRead / fileSize) / 2.6158204f);
                        MCEFDownloadListener.INSTANCE.setProgress(percentComplete);
                    }
                }
            }
        } catch (IOException e) {
            MCEF.getLogger().error("Failed to extract gzip file to " + outputDirectory, e);
        }

        MCEFDownloadListener.INSTANCE.setProgress(1.0f);
    }
}
