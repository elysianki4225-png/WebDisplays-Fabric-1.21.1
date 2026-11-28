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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

public class MCEFRenderer {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);
    private static final boolean IS_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final int MAX_INDIVIDUAL_DIRTY_RECTS = 8;

    private final boolean transparent;
    private final int id;
    private final Identifier textureIdentifier;
    private BrowserTexture texture;
    private int texWidth = 0;
    private int texHeight = 0;
    private boolean initialized = false;

    private ByteBuffer stagingBuffer;
    private int stagingCapacityBytes = 0;

    protected MCEFRenderer(boolean transparent) {
        this.transparent = transparent;
        this.id = NEXT_ID.incrementAndGet();
        this.textureIdentifier = Identifier.fromNamespaceAndPath("mcef", "browser/" + this.id);
    }

    public void initialize() {
        initialized = true;
    }

    public Identifier getTextureIdentifier() {
        return textureIdentifier;
    }

    public boolean isReady() {
        return initialized && texture != null && texWidth > 0 && texHeight > 0;
    }

    public int getTextureID() {
        return isReady() ? id : 0;
    }

    public boolean isTransparent() {
        return transparent;
    }

    protected void cleanup() {
        initialized = false;
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(textureIdentifier);
            texture = null;
        }
        texWidth = 0;
        texHeight = 0;
        if (stagingBuffer != null) {
            MemoryUtil.memFree(stagingBuffer);
            stagingBuffer = null;
            stagingCapacityBytes = 0;
        }
    }

    private void ensureSize(int width, int height) {
        if (texture != null && texWidth == width && texHeight == height && texture.isAlive()) {
            return;
        }
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(textureIdentifier);
            texture = null;
        }
        texture = new BrowserTexture("mcef_browser_" + id, width, height);
        Minecraft.getInstance().getTextureManager().register(textureIdentifier, texture);
        texWidth = width;
        texHeight = height;
    }

    private ByteBuffer ensureStagingBuffer(int neededBytes) {
        if (stagingBuffer == null || stagingCapacityBytes < neededBytes) {
            stagingBuffer = MemoryUtil.memRealloc(stagingBuffer, neededBytes);
            stagingCapacityBytes = neededBytes;
        }
        return stagingBuffer;
    }

    protected void onPaint(ByteBuffer buffer, int width, int height) {
        if (!initialized || width <= 0 || height <= 0 || buffer == null || !RenderSystem.isOnRenderThread()) {
            return;
        }
        ensureSize(width, height);
        if (texture == null || !texture.isAlive()) {
            return;
        }
        uploadRect(buffer, width, height, 0, 0, width, height);
    }

    protected void onPaintRects(ByteBuffer buffer, int width, int height, Rectangle[] dirtyRects) {
        if (!initialized || width <= 0 || height <= 0 || buffer == null || !RenderSystem.isOnRenderThread()) {
            return;
        }
        boolean resized = (texture == null || texWidth != width || texHeight != height || !texture.isAlive());
        ensureSize(width, height);
        if (texture == null || !texture.isAlive()) {
            return;
        }

        if (resized || dirtyRects == null || dirtyRects.length == 0) {
            uploadRect(buffer, width, height, 0, 0, width, height);
            return;
        }

        if (dirtyRects.length > MAX_INDIVIDUAL_DIRTY_RECTS) {
            uploadMergedBounds(buffer, width, height, dirtyRects);
            return;
        }

        long totalDirtyPixels = 0L;
        for (Rectangle rect : dirtyRects) {
            if (rect.width > 0 && rect.height > 0) {
                int x = Math.max(0, rect.x);
                int y = Math.max(0, rect.y);
                int w = Math.min(width - x, rect.width);
                int h = Math.min(height - y, rect.height);
                if (w > 0 && h > 0) {
                    totalDirtyPixels += (long) w * (long) h;
                }
            }
        }

        long fullPixels = (long) width * (long) height;
        if (totalDirtyPixels * 10L >= fullPixels * 6L) {
            uploadRect(buffer, width, height, 0, 0, width, height);
            return;
        }

        for (Rectangle rect : dirtyRects) {
            int x = Math.max(0, rect.x);
            int y = Math.max(0, rect.y);
            int w = Math.min(width - x, rect.width);
            int h = Math.min(height - y, rect.height);
            if (w > 0 && h > 0) {
                uploadRect(buffer, width, height, x, y, w, h);
            }
        }
    }

    private void uploadMergedBounds(ByteBuffer buffer, int width, int height, Rectangle[] dirtyRects) {
        int minX = width;
        int minY = height;
        int maxX = 0;
        int maxY = 0;

        for (Rectangle rect : dirtyRects) {
            if (rect.width <= 0 || rect.height <= 0) {
                continue;
            }
            int x0 = Math.max(0, rect.x);
            int y0 = Math.max(0, rect.y);
            int x1 = Math.min(width, rect.x + rect.width);
            int y1 = Math.min(height, rect.y + rect.height);
            if (x1 > x0 && y1 > y0) {
                if (x0 < minX) minX = x0;
                if (y0 < minY) minY = y0;
                if (x1 > maxX) maxX = x1;
                if (y1 > maxY) maxY = y1;
            }
        }

        if (maxX > minX && maxY > minY) {
            uploadRect(buffer, width, height, minX, minY, maxX - minX, maxY - minY);
        }
    }

    protected void onPaintSubBuffer(ByteBuffer buffer, int srcWidth, int dstX, int dstY, int rectW, int rectH) {
        if (!initialized || texture == null || !texture.isAlive() || buffer == null || rectW <= 0 || rectH <= 0 || !RenderSystem.isOnRenderThread()) {
            return;
        }
        int clampedW = Math.min(rectW, texWidth - dstX);
        int clampedH = Math.min(rectH, texHeight - dstY);
        if (dstX < 0 || dstY < 0 || clampedW <= 0 || clampedH <= 0) {
            return;
        }

        int neededBytes = clampedW * clampedH * 4;
        ByteBuffer staging = ensureStagingBuffer(neededBytes);
        long srcBase = MemoryUtil.memAddress(buffer);
        long dstBase = MemoryUtil.memAddress(staging);
        long srcStride = (long) srcWidth * 4L;
        long dstStride = (long) clampedW * 4L;

        for (int py = 0; py < clampedH; py++) {
            swizzleBgraToRgbaRow(srcBase + py * srcStride, dstBase + py * dstStride, clampedW, transparent);
        }

        staging.position(0);
        staging.limit(neededBytes);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToTexture(texture.getTexture(), staging, 0, 0, dstX, dstY, clampedW, clampedH);
    }

    private void uploadRect(ByteBuffer buffer, int srcWidth, int srcHeight, int rx, int ry, int rw, int rh) {
        int maxW = Math.min(rw, Math.min(srcWidth, texWidth) - rx);
        int maxH = Math.min(rh, Math.min(srcHeight, texHeight) - ry);
        if (maxW <= 0 || maxH <= 0) {
            return;
        }

        int neededBytes = maxW * maxH * 4;
        ByteBuffer staging = ensureStagingBuffer(neededBytes);
        long srcBase = MemoryUtil.memAddress(buffer);
        long dstBase = MemoryUtil.memAddress(staging);
        long srcStride = (long) srcWidth * 4L;
        long dstStride = (long) maxW * 4L;
        long srcXOffset = (long) rx * 4L;

        for (int dy = 0; dy < maxH; dy++) {
            long srcRow = srcBase + (long) (ry + dy) * srcStride + srcXOffset;
            long dstRow = dstBase + (long) dy * dstStride;
            swizzleBgraToRgbaRow(srcRow, dstRow, maxW, transparent);
        }

        staging.position(0);
        staging.limit(neededBytes);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToTexture(texture.getTexture(), staging, 0, 0, rx, ry, maxW, maxH);
    }

    private static void swizzleBgraToRgbaRow(long srcAddr, long dstAddr, int pixelCount, boolean transparent) {
        if (IS_LITTLE_ENDIAN) {
            final long alphaMask64 = transparent ? 0L : 0xFF000000FF000000L;
            final int alphaMask32 = transparent ? 0 : 0xFF000000;
            int i = 0;
            int pairLimit = pixelCount & ~1;

            while (i < pairLimit) {
                long offset = (long) i << 2;
                long p = MemoryUtil.memGetLong(srcAddr + offset);
                long rb = ((p & 0x00FF000000FF0000L) >>> 16) | ((p & 0x000000FF000000FFL) << 16);
                long ga = p & 0xFF00FF00FF00FF00L;
                MemoryUtil.memPutLong(dstAddr + offset, ga | rb | alphaMask64);
                i += 2;
            }

            if (i < pixelCount) {
                long offset = (long) i << 2;
                int p = MemoryUtil.memGetInt(srcAddr + offset);
                int rb = ((p & 0x00FF0000) >>> 16) | ((p & 0x000000FF) << 16);
                int ga = p & 0xFF00FF00;
                MemoryUtil.memPutInt(dstAddr + offset, ga | rb | alphaMask32);
            }
        } else {
            byte alphaFill = (byte) 0xFF;
            for (int i = 0; i < pixelCount; i++) {
                long offset = (long) i << 2;
                byte b = MemoryUtil.memGetByte(srcAddr + offset);
                byte g = MemoryUtil.memGetByte(srcAddr + offset + 1);
                byte r = MemoryUtil.memGetByte(srcAddr + offset + 2);
                byte a = transparent ? MemoryUtil.memGetByte(srcAddr + offset + 3) : alphaFill;
                MemoryUtil.memPutByte(dstAddr + offset, r);
                MemoryUtil.memPutByte(dstAddr + offset + 1, g);
                MemoryUtil.memPutByte(dstAddr + offset + 2, b);
                MemoryUtil.memPutByte(dstAddr + offset + 3, a);
            }
        }
    }

    private static final class BrowserTexture extends AbstractTexture {
        BrowserTexture(String label, int width, int height) {
            GpuDevice device = RenderSystem.getDevice();
            this.texture = device.createTexture(
                    () -> label,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM,
                    width,
                    height,
                    1,
                    1
            );
            this.textureView = device.createTextureView(this.texture);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }

        boolean isAlive() {
            return this.texture != null && !this.texture.isClosed();
        }
    }
}
