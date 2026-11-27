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

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class MCEFRenderer {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);

    private final boolean transparent;
    private final int id;
    private final Identifier textureIdentifier;
    private DynamicTexture texture;
    private int texWidth = 0;
    private int texHeight = 0;
    private boolean initialized = false;

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
    }

    private void ensureSize(int width, int height) {
        if (texture != null && texWidth == width && texHeight == height) {
            return;
        }
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(textureIdentifier);
            texture = null;
        }
        texture = new DynamicTexture(() -> "mcef_browser_" + id, width, height, false);
        Minecraft.getInstance().getTextureManager().register(textureIdentifier, texture);
        texWidth = width;
        texHeight = height;
    }

    protected void onPaint(ByteBuffer buffer, int width, int height) {
        if (!initialized || width <= 0 || height <= 0 || buffer == null) {
            return;
        }
        ensureSize(width, height);
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        copyRect(buffer, width, height, 0, 0, width, height, image);
        texture.upload();
    }

    protected void onPaintRects(ByteBuffer buffer, int width, int height, Rectangle[] dirtyRects) {
        if (!initialized || width <= 0 || height <= 0 || buffer == null) {
            return;
        }
        boolean resized = (texture == null || texWidth != width || texHeight != height);
        ensureSize(width, height);
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }

        if (resized || dirtyRects == null || dirtyRects.length == 0) {
            copyRect(buffer, width, height, 0, 0, width, height, image);
        } else {
            for (Rectangle rect : dirtyRects) {
                int x = Math.max(0, rect.x);
                int y = Math.max(0, rect.y);
                int w = Math.min(width - x, rect.width);
                int h = Math.min(height - y, rect.height);
                if (w > 0 && h > 0) {
                    copyRect(buffer, width, height, x, y, w, h, image);
                }
            }
        }
        texture.upload();
    }

    protected void onPaintSubBuffer(ByteBuffer buffer, int srcWidth, int dstX, int dstY, int rectW, int rectH) {
        if (!initialized || texture == null || buffer == null || rectW <= 0 || rectH <= 0) {
            return;
        }
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        int clampedW = Math.min(rectW, texWidth - dstX);
        int clampedH = Math.min(rectH, texHeight - dstY);
        if (dstX < 0 || dstY < 0 || clampedW <= 0 || clampedH <= 0) {
            return;
        }

        ByteBuffer dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer ints = dup.asIntBuffer();
        int alphaMask = transparent ? 0 : 0xFF000000;
        for (int py = 0; py < clampedH; py++) {
            int rowBase = py * srcWidth;
            for (int px = 0; px < clampedW; px++) {
                int argb = ints.get(rowBase + px) | alphaMask;
                image.setPixel(dstX + px, dstY + py, argb);
            }
        }
        texture.upload();
    }

    private void copyRect(ByteBuffer buffer, int srcWidth, int srcHeight, int rx, int ry, int rw, int rh, NativeImage image) {
        ByteBuffer dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer ints = dup.asIntBuffer();
        int alphaMask = transparent ? 0 : 0xFF000000;
        int maxW = Math.min(rw, Math.min(srcWidth, texWidth) - rx);
        int maxH = Math.min(rh, Math.min(srcHeight, texHeight) - ry);
        for (int dy = 0; dy < maxH; dy++) {
            int py = ry + dy;
            int rowBase = py * srcWidth + rx;
            for (int dx = 0; dx < maxW; dx++) {
                int argb = ints.get(rowBase + dx) | alphaMask;
                image.setPixel(rx + dx, py, argb);
            }
        }
    }
}
