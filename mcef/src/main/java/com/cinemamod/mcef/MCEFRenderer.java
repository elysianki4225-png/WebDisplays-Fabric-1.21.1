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
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;

public class MCEFRenderer {
    private static final int PBO_STREAM_THRESHOLD_BYTES = 16384;
    private static final long MIPMAP_INTERVAL_NS = 100_000_000L; // 10 Hz max mipmap rebuild during video/scroll

    private final boolean transparent;
    private final int[] textureID = new int[1];
    private final int[] pboIDs = new int[2];
    private int pboIndex = 0;
    private int pboCapacity = 0;
    private boolean pboSupported = false;
    private boolean mipmapSupported = false;
    private int texWidth = 0;
    private int texHeight = 0;
    private long lastMipmapNs = 0L;

    protected MCEFRenderer(boolean transparent) {
        this.transparent = transparent;
    }

    public void initialize() {
        if (textureID[0] != 0) {
            return;
        }
        GLCapabilities caps = GL.getCapabilities();
        mipmapSupported = caps.OpenGL30;
        pboSupported = caps.OpenGL30 && (caps.OpenGL21 || caps.GL_ARB_pixel_buffer_object);

        textureID[0] = glGenTextures();
        RenderSystem.bindTexture(textureID[0]);
        RenderSystem.texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER,
                mipmapSupported ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR);
        RenderSystem.texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        RenderSystem.texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        RenderSystem.texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        if (caps.GL_EXT_texture_filter_anisotropic) {
            float maxAniso = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            if (maxAniso > 1.0f) {
                glTexParameterf(GL_TEXTURE_2D,
                        EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                        Math.min(8.0f, maxAniso));
            }
        }

        if (pboSupported) {
            pboIDs[0] = GL15C.glGenBuffers();
            pboIDs[1] = GL15C.glGenBuffers();
        }
        RenderSystem.bindTexture(0);
    }

    public int getTextureID() {
        return textureID[0];
    }

    public boolean isTransparent() {
        return transparent;
    }

    protected void cleanup() {
        if (textureID[0] != 0) {
            glDeleteTextures(textureID[0]);
            textureID[0] = 0;
            texWidth = 0;
            texHeight = 0;
        }
        if (pboIDs[0] != 0) {
            GL15C.glDeleteBuffers(pboIDs[0]);
            pboIDs[0] = 0;
        }
        if (pboIDs[1] != 0) {
            GL15C.glDeleteBuffers(pboIDs[1]);
            pboIDs[1] = 0;
        }
        pboCapacity = 0;
    }

    protected void onPaint(ByteBuffer buffer, int width, int height) {
        if (textureID[0] == 0 || width <= 0 || height <= 0 || buffer == null) {
            return;
        }
        if (transparent) {
            RenderSystem.enableBlend();
        }
        RenderSystem.bindTexture(textureID[0]);
        RenderSystem.pixelStore(GL_UNPACK_ALIGNMENT, 4);
        RenderSystem.pixelStore(GL_UNPACK_ROW_LENGTH, width);
        RenderSystem.pixelStore(GL_UNPACK_SKIP_PIXELS, 0);
        RenderSystem.pixelStore(GL_UNPACK_SKIP_ROWS, 0);

        int byteCount = width * height * 4;
        if (texWidth == width && texHeight == height) {
            if (pboSupported && byteCount >= PBO_STREAM_THRESHOLD_BYTES && uploadContiguousViaPbo(buffer, byteCount)) {
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                        GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 0L);
                GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            } else {
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                        GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            }
        } else {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                    GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            texWidth = width;
            texHeight = height;
        }

        resetUnpackState();
        finishPaintBatch(true);
    }

    protected void onPaint(ByteBuffer buffer, int x, int y, int width, int height) {
        if (textureID[0] == 0 || width <= 0 || height <= 0 || buffer == null) {
            return;
        }
        glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, width, height, GL_BGRA,
                GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
    }

    protected void onPaintRectViaPbo(ByteBuffer buffer, int srcWidth, int srcX, int srcY, int dstX, int dstY, int width, int height) {
        if (textureID[0] == 0 || width <= 0 || height <= 0 || buffer == null) {
            return;
        }
        int neededBytes = width * height * 4;
        if (!pboSupported || neededBytes < PBO_STREAM_THRESHOLD_BYTES) {
            RenderSystem.pixelStore(GL_UNPACK_ROW_LENGTH, srcWidth);
            RenderSystem.pixelStore(GL_UNPACK_SKIP_PIXELS, srcX);
            RenderSystem.pixelStore(GL_UNPACK_SKIP_ROWS, srcY);
            glTexSubImage2D(GL_TEXTURE_2D, 0, dstX, dstY, width, height, GL_BGRA,
                    GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            return;
        }

        int pbo = nextPbo(neededBytes);
        ByteBuffer mapped = GL30C.glMapBufferRange(
                GL21C.GL_PIXEL_UNPACK_BUFFER,
                0,
                neededBytes,
                GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_INVALIDATE_BUFFER_BIT | GL30C.GL_MAP_UNSYNCHRONIZED_BIT
        );
        if (mapped == null) {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            RenderSystem.pixelStore(GL_UNPACK_ROW_LENGTH, srcWidth);
            RenderSystem.pixelStore(GL_UNPACK_SKIP_PIXELS, srcX);
            RenderSystem.pixelStore(GL_UNPACK_SKIP_ROWS, srcY);
            glTexSubImage2D(GL_TEXTURE_2D, 0, dstX, dstY, width, height, GL_BGRA,
                    GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            return;
        }

        long srcBase = MemoryUtil.memAddress(buffer);
        long dstBase = MemoryUtil.memAddress(mapped);
        long srcStride = (long) srcWidth * 4L;
        long dstStride = (long) width * 4L;
        long srcXOff = (long) srcX * 4L;
        if (srcX == 0 && srcWidth == width) {
            MemoryUtil.memCopy(srcBase + (long) srcY * srcStride, dstBase, neededBytes);
        } else {
            for (int row = 0; row < height; row++) {
                MemoryUtil.memCopy(srcBase + (long) (srcY + row) * srcStride + srcXOff,
                        dstBase + (long) row * dstStride, dstStride);
            }
        }
        GL15C.glUnmapBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER);

        RenderSystem.pixelStore(GL_UNPACK_ROW_LENGTH, width);
        RenderSystem.pixelStore(GL_UNPACK_SKIP_PIXELS, 0);
        RenderSystem.pixelStore(GL_UNPACK_SKIP_ROWS, 0);
        glTexSubImage2D(GL_TEXTURE_2D, 0, dstX, dstY, width, height, GL_BGRA,
                GL_UNSIGNED_INT_8_8_8_8_REV, 0L);
        GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
    }

    protected void finishPaintBatch(boolean forceMipmap) {
        if (!mipmapSupported || textureID[0] == 0) {
            return;
        }
        long now = System.nanoTime();
        if (forceMipmap || (now - lastMipmapNs) >= MIPMAP_INTERVAL_NS) {
            lastMipmapNs = now;
            GL30C.glGenerateMipmap(GL_TEXTURE_2D);
        }
    }

    private int nextPbo(int neededBytes) {
        pboIndex = (pboIndex + 1) & 1;
        int pbo = pboIDs[pboIndex];
        GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, pbo);
        // Orphan the backing store with GL_STREAM_DRAW to prevent implicit GPU sync stalls
        GL15C.glBufferData(GL21C.GL_PIXEL_UNPACK_BUFFER, neededBytes, GL15C.GL_STREAM_DRAW);
        if (neededBytes > pboCapacity) {
            pboCapacity = neededBytes;
        }
        return pbo;
    }

    private boolean uploadContiguousViaPbo(ByteBuffer buffer, int neededBytes) {
        nextPbo(neededBytes);
        ByteBuffer mapped = GL30C.glMapBufferRange(
                GL21C.GL_PIXEL_UNPACK_BUFFER,
                0,
                neededBytes,
                GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_INVALIDATE_BUFFER_BIT | GL30C.GL_MAP_UNSYNCHRONIZED_BIT
        );
        if (mapped == null) {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            return false;
        }
        MemoryUtil.memCopy(MemoryUtil.memAddress(buffer), MemoryUtil.memAddress(mapped), neededBytes);
        GL15C.glUnmapBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER);
        return true;
    }

    protected static void resetUnpackState() {
        RenderSystem.pixelStore(GL_UNPACK_ROW_LENGTH, 0);
        RenderSystem.pixelStore(GL_UNPACK_SKIP_PIXELS, 0);
        RenderSystem.pixelStore(GL_UNPACK_SKIP_ROWS, 0);
    }
}
