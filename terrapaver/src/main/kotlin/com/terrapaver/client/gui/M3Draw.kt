package com.terrapaver.client.gui

import com.mojang.blaze3d.vertex.VertexConsumer
import com.terrapaver.config.PavingConfig
import com.terrapaver.config.PavingMode
import com.terrapaver.config.TunnelShape
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.resources.language.I18n
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object M3Draw {
    private const val CORNER_SEGS = 10 // 10 segs/corner -> 40 + 4 = 44 segs (even)

    fun fitText(font: Font, text: String, maxWidth: Int): String {
        if (maxWidth <= 4) return ""
        if (font.width(text) <= maxWidth) return text
        val ell = "…"
        val ellW = font.width(ell)
        val trimmed = font.plainSubstrByWidth(text, (maxWidth - ellW).coerceAtLeast(1))
        return trimmed + ell
    }

    fun fillRoundedRect(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, radius: Int, color: Int) {
        fillRoundedRectF(g, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), radius.toFloat(), color)
    }

    fun fillRoundedRectF(g: GuiGraphics, x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
        if (w <= 0f || h <= 0f) return
        val a = (color ushr 24) and 0xFF
        if (a <= 0) return
        val rCol = (color ushr 16) and 0xFF
        val gCol = (color ushr 8) and 0xFF
        val bCol = color and 0xFF

        val pose = g.pose().last().pose()
        val buf = g.bufferSource().getBuffer(RenderType.gui())

        val rad = radius.coerceIn(0f, min(w, h) * 0.5f)
        if (rad <= 0.6f) {
            emitQuad(buf, pose, x, y, x + w, y + h, rCol, gCol, bCol, a)
            return
        }

        val innerRad = (rad - 0.35f).coerceAtLeast(0f)
        val outerRad = rad + 0.45f
        val cx = x + w * 0.5f
        val cy = y + h * 0.5f

        val ix1 = x + rad
        val iy1 = y + rad
        val ix2 = x + w - rad
        val iy2 = y + h - rad

        val count = CORNER_SEGS * 4 + 4
        val inX = FloatArray(count)
        val inY = FloatArray(count)
        val outX = FloatArray(count)
        val outY = FloatArray(count)

        buildPerimeter(ix1, iy1, ix2, iy2, innerRad, inX, inY)
        buildPerimeter(ix1, iy1, ix2, iy2, outerRad, outX, outY)

        // Solid interior fan (2 perimeter segments per quad)
        var i = 0
        while (i < count) {
            val i1 = (i + 1) % count
            val i2 = (i + 2) % count
            buf.addVertex(pose, cx, cy, 0f).setColor(rCol, gCol, bCol, a)
            buf.addVertex(pose, inX[i2], inY[i2], 0f).setColor(rCol, gCol, bCol, a)
            buf.addVertex(pose, inX[i1], inY[i1], 0f).setColor(rCol, gCol, bCol, a)
            buf.addVertex(pose, inX[i], inY[i], 0f).setColor(rCol, gCol, bCol, a)
            i += 2
        }

        // Sub-pixel AA outer fringe (alpha -> 0)
        for (k in 0 until count) {
            val next = (k + 1) % count
            buf.addVertex(pose, inX[k], inY[k], 0f).setColor(rCol, gCol, bCol, a)
            buf.addVertex(pose, inX[next], inY[next], 0f).setColor(rCol, gCol, bCol, a)
            buf.addVertex(pose, outX[next], outY[next], 0f).setColor(rCol, gCol, bCol, 0)
            buf.addVertex(pose, outX[k], outY[k], 0f).setColor(rCol, gCol, bCol, 0)
        }
    }

    fun strokeRoundedRect(
        g: GuiGraphics,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        radius: Int,
        color: Int,
        thickness: Float = 1.0f
    ) {
        strokeRoundedRectF(g, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), radius.toFloat(), color, thickness)
    }

    fun strokeRoundedRectF(
        g: GuiGraphics,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        color: Int,
        thickness: Float = 1.0f
    ) {
        if (w <= 1f || h <= 1f) return
        val a = (color ushr 24) and 0xFF
        if (a <= 0) return
        val rCol = (color ushr 16) and 0xFF
        val gCol = (color ushr 8) and 0xFF
        val bCol = color and 0xFF

        val pose = g.pose().last().pose()
        val buf = g.bufferSource().getBuffer(RenderType.gui())
        val rad = radius.coerceIn(0f, min(w, h) * 0.5f)
        val halfT = (thickness * 0.5f).coerceAtLeast(0.25f)

        val ix1 = x + rad
        val iy1 = y + rad
        val ix2 = x + w - rad
        val iy2 = y + h - rad

        val count = CORNER_SEGS * 4 + 4
        val aa = 0.45f

        val rInEdge = -halfT - aa
        val rInSolid = (-halfT + 0.12f).coerceAtMost(0f)
        val rOutSolid = (halfT - 0.12f).coerceAtLeast(0f)
        val rOutEdge = halfT + aa

        val p0x = FloatArray(count)
        val p0y = FloatArray(count)
        val p1x = FloatArray(count)
        val p1y = FloatArray(count)
        val p2x = FloatArray(count)
        val p2y = FloatArray(count)
        val p3x = FloatArray(count)
        val p3y = FloatArray(count)

        buildOffsetPerimeter(ix1, iy1, ix2, iy2, rad, rInEdge, p0x, p0y)
        buildOffsetPerimeter(ix1, iy1, ix2, iy2, rad, rInSolid, p1x, p1y)
        buildOffsetPerimeter(ix1, iy1, ix2, iy2, rad, rOutSolid, p2x, p2y)
        buildOffsetPerimeter(ix1, iy1, ix2, iy2, rad, rOutEdge, p3x, p3y)

        for (k in 0 until count) {
            val next = (k + 1) % count
            // Inner AA fade
            buf.addVertex(pose, p0x[k], p0y[k], 0f).setColor(rCol, gCol, bCol, 0)
            buf.addVertex(pose, p0x[next], p0y[next], 0f).setColor(rCol, gCol, bCol, 0)
            buf.addVertex(pose, p1x[next], p1y[next], 0f).setColor(rCol, gCol, bCol, a)
            buf.addVertex(pose, p1x[k], p1y[k], 0f).setColor(rCol, gCol, bCol, a)

            // Solid core stroke
            buf.addVertex(pose, p1x[k], p1y[k], 0f).setColor(rCol, gCol, bCol, a)
            buf.addVertex(pose, p1x[next], p1y[next], 0f).setColor(rCol, gCol, bCol, a)
            buf.addVertex(pose, p2x[next], p2y[next], 0f).setColor(rCol, gCol, bCol, a)
            buf.addVertex(pose, p2x[k], p2y[k], 0f).setColor(rCol, gCol, bCol, a)

            // Outer AA fade
            buf.addVertex(pose, p2x[k], p2y[k], 0f).setColor(rCol, gCol, bCol, a)
            buf.addVertex(pose, p2x[next], p2y[next], 0f).setColor(rCol, gCol, bCol, a)
            buf.addVertex(pose, p3x[next], p3y[next], 0f).setColor(rCol, gCol, bCol, 0)
            buf.addVertex(pose, p3x[k], p3y[k], 0f).setColor(rCol, gCol, bCol, 0)
        }
    }

    private fun buildPerimeter(
        ix1: Float,
        iy1: Float,
        ix2: Float,
        iy2: Float,
        r: Float,
        outX: FloatArray,
        outY: FloatArray
    ) {
        var idx = 0
        // BR (0 -> PI/2)
        for (s in 0..CORNER_SEGS) {
            val ang = (PI * 0.5 * s / CORNER_SEGS)
            outX[idx] = ix2 + (cos(ang) * r).toFloat()
            outY[idx] = iy2 + (sin(ang) * r).toFloat()
            idx++
        }
        // BL (PI/2 -> PI)
        for (s in 0..CORNER_SEGS) {
            val ang = PI * 0.5 + (PI * 0.5 * s / CORNER_SEGS)
            outX[idx] = ix1 + (cos(ang) * r).toFloat()
            outY[idx] = iy2 + (sin(ang) * r).toFloat()
            idx++
        }
        // TL (PI -> 3*PI/2)
        for (s in 0..CORNER_SEGS) {
            val ang = PI + (PI * 0.5 * s / CORNER_SEGS)
            outX[idx] = ix1 + (cos(ang) * r).toFloat()
            outY[idx] = iy1 + (sin(ang) * r).toFloat()
            idx++
        }
        // TR (3*PI/2 -> 2*PI)
        for (s in 0..CORNER_SEGS) {
            val ang = PI * 1.5 + (PI * 0.5 * s / CORNER_SEGS)
            outX[idx] = ix2 + (cos(ang) * r).toFloat()
            outY[idx] = iy1 + (sin(ang) * r).toFloat()
            idx++
        }
    }

    private fun buildOffsetPerimeter(
        ix1: Float,
        iy1: Float,
        ix2: Float,
        iy2: Float,
        baseRad: Float,
        offset: Float,
        outX: FloatArray,
        outY: FloatArray
    ) {
        val r = (baseRad + offset).coerceAtLeast(0f)
        val extra = if (baseRad + offset < 0f) -(baseRad + offset) else 0f
        buildPerimeter(
            (ix1 + extra).coerceAtMost((ix1 + ix2) * 0.5f),
            (iy1 + extra).coerceAtMost((iy1 + iy2) * 0.5f),
            (ix2 - extra).coerceAtLeast((ix1 + ix2) * 0.5f),
            (iy2 - extra).coerceAtLeast((iy1 + iy2) * 0.5f),
            r, outX, outY
        )
    }

    private fun emitQuad(
        buf: VertexConsumer,
        pose: Matrix4f,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        r: Int,
        g: Int,
        b: Int,
        a: Int
    ) {
        buf.addVertex(pose, x1, y2, 0f).setColor(r, g, b, a)
        buf.addVertex(pose, x2, y2, 0f).setColor(r, g, b, a)
        buf.addVertex(pose, x2, y1, 0f).setColor(r, g, b, a)
        buf.addVertex(pose, x1, y1, 0f).setColor(r, g, b, a)
    }

    fun drawElevationShadow(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, radius: Int, layers: Int = 3) {
        val count = layers.coerceIn(1, 5)
        for (i in count downTo 1) {
            val spread = i * 1.4f
            val yOff = i * 1.1f
            val alpha = 5 + (count - i) * 3
            fillRoundedRectF(
                g,
                x - spread * 0.6f,
                y + yOff - spread * 0.25f,
                w + spread * 1.2f,
                h + spread * 0.9f,
                radius + spread,
                M3Theme.withAlpha(0x000F172A, alpha)
            )
        }
    }

    fun fillCircle(g: GuiGraphics, cx: Int, cy: Int, r: Int, color: Int) {
        fillCircleF(g, cx.toFloat(), cy.toFloat(), r.toFloat(), color)
    }

    fun fillCircleF(g: GuiGraphics, cx: Float, cy: Float, r: Float, color: Int) {
        fillRoundedRectF(g, cx - r, cy - r, r * 2f, r * 2f, r, color)
    }

    fun drawLinearProgress(
        g: GuiGraphics,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        progress: Float,
        theme: M3Palette,
        indeterminate: Boolean = false
    ) {
        val r = h * 0.5f
        fillRoundedRectF(g, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r, theme.surfaceHigh)
        if (indeterminate) {
            val phase = ((System.currentTimeMillis() % 1400L) / 1400.0f)
            val segW = max(h * 2f, w * 0.34f)
            val start = x + (w - segW) * phase
            fillRoundedRectF(g, start, y.toFloat(), segW, h.toFloat(), r, theme.primary)
        } else {
            val p = progress.coerceIn(0f, 1f)
            val fillW = w * p
            if (fillW >= h) {
                fillRoundedRectF(g, x.toFloat(), y.toFloat(), fillW, h.toFloat(), r, theme.primary)
            } else if (fillW > 0.5f) {
                fillRoundedRectF(g, x.toFloat(), y.toFloat(), h.toFloat(), h.toFloat(), r, theme.primary)
            }
        }
    }

    fun drawCard(
        g: GuiGraphics,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        theme: M3Palette,
        elevated: Boolean = true,
        radius: Int = 14
    ) {
        if (elevated) {
            drawElevationShadow(g, x, y, w, h, radius, 3)
            fillRoundedRect(g, x, y, w, h, radius, theme.card)
            strokeRoundedRect(g, x, y, w, h, radius, M3Theme.withAlpha(theme.outlineVariant, 150), 0.9f)
        } else {
            fillRoundedRect(g, x, y, w, h, radius, theme.surfaceContainer)
            strokeRoundedRect(g, x, y, w, h, radius, M3Theme.withAlpha(theme.outlineVariant, 110), 0.8f)
        }
    }

    fun drawCrossSectionPreview(
        g: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        cfg: PavingConfig,
        theme: M3Palette,
        title: String
    ) {
        fillRoundedRect(g, x, y, w, h, 12, theme.surfaceLow)
        strokeRoundedRect(g, x, y, w, h, 12, M3Theme.withAlpha(theme.outlineVariant, 160), 0.9f)

        g.drawString(font, title, x + 10, y + 8, theme.onSurfaceVariant, false)

        val tunnelW = cfg.effectiveTunnelWidth()
        val tunnelH = cfg.effectiveTunnelHeight()
        val roadW = cfg.roadWidth

        val areaX = x + 12
        val areaY = y + 22
        val areaW = w - 24
        val areaH = h - 42

        val maxBlocksW = max(tunnelW + 4, roadW + 4).toDouble()
        val maxBlocksH = (tunnelH + 4).toDouble()
        val scale = min(areaW / maxBlocksW, areaH / maxBlocksH).coerceIn(3.5, 13.0)

        val originX = areaX + areaW / 2.0
        val floorY = areaY + areaH - 8.0

        val dotColor = M3Theme.withAlpha(theme.outlineVariant, 90)
        for (gx in -10..10 step 2) {
            for (gy in 0..14 step 2) {
                val px = (originX + gx * scale).roundToInt()
                val py = (floorY - gy * scale).roundToInt()
                if (px in (areaX + 2)..(areaX + areaW - 3) && py in (areaY + 2)..(areaY + areaH - 3)) {
                    g.fill(px, py, px + 1, py + 1, dotColor)
                }
            }
        }

        val halfTunnelPx = (tunnelW * scale * 0.5).roundToInt()
        val shellThick = max(3, (scale * 0.9).roundToInt())
        val tunnelTopPx = (tunnelH * scale).roundToInt()

        val shellColor = if (cfg.addTunnelWalls) {
            M3Theme.blend(theme.outline, theme.primaryContainer, 0.35f)
        } else {
            M3Theme.withAlpha(theme.outlineVariant, 70)
        }
        val boreColor = theme.card

        when (cfg.tunnelShape) {
            TunnelShape.RECTANGULAR -> {
                fillRoundedRect(
                    g,
                    (originX - halfTunnelPx - shellThick).roundToInt(),
                    (floorY - tunnelTopPx - shellThick).roundToInt(),
                    (halfTunnelPx + shellThick) * 2,
                    tunnelTopPx + shellThick,
                    4,
                    shellColor
                )
                fillRoundedRect(
                    g,
                    (originX - halfTunnelPx).roundToInt(),
                    (floorY - tunnelTopPx).roundToInt(),
                    halfTunnelPx * 2,
                    tunnelTopPx,
                    2,
                    boreColor
                )
            }
            TunnelShape.ARCH -> {
                val archR = halfTunnelPx
                fillRoundedRect(
                    g,
                    (originX - halfTunnelPx - shellThick).roundToInt(),
                    (floorY - tunnelTopPx - shellThick).roundToInt(),
                    (halfTunnelPx + shellThick) * 2,
                    tunnelTopPx + shellThick,
                    archR + shellThick,
                    shellColor
                )
                g.fill(
                    (originX - halfTunnelPx - shellThick).roundToInt(),
                    (floorY - tunnelTopPx * 0.45).roundToInt(),
                    (originX + halfTunnelPx + shellThick).roundToInt(),
                    floorY.roundToInt(),
                    shellColor
                )
                fillRoundedRect(
                    g,
                    (originX - halfTunnelPx).roundToInt(),
                    (floorY - tunnelTopPx).roundToInt(),
                    halfTunnelPx * 2,
                    tunnelTopPx,
                    archR,
                    boreColor
                )
                g.fill(
                    (originX - halfTunnelPx).roundToInt(),
                    (floorY - tunnelTopPx * 0.45).roundToInt(),
                    (originX + halfTunnelPx).roundToInt(),
                    floorY.roundToInt(),
                    boreColor
                )
            }
            TunnelShape.CIRCULAR -> {
                fillRoundedRect(
                    g,
                    (originX - halfTunnelPx - shellThick).roundToInt(),
                    (floorY - tunnelTopPx - shellThick).roundToInt(),
                    (halfTunnelPx + shellThick) * 2,
                    tunnelTopPx + shellThick,
                    min(halfTunnelPx + shellThick, (tunnelTopPx + shellThick) / 2),
                    shellColor
                )
                fillRoundedRect(
                    g,
                    (originX - halfTunnelPx).roundToInt(),
                    (floorY - tunnelTopPx).roundToInt(),
                    halfTunnelPx * 2,
                    tunnelTopPx,
                    min(halfTunnelPx, tunnelTopPx / 2),
                    boreColor
                )
            }
        }

        if (cfg.addTunnelLights) {
            fillRoundedRect(g, originX.roundToInt() - 4, (floorY - tunnelTopPx + 1).roundToInt(), 8, 3, 1, 0xFFFFB300.toInt())
            val wallLightY = (floorY - tunnelTopPx * 0.48).roundToInt()
            fillRoundedRect(g, (originX - halfTunnelPx + 1).roundToInt(), wallLightY, 3, 5, 1, 0xFFFFB300.toInt())
            fillRoundedRect(g, (originX + halfTunnelPx - 4).roundToInt(), wallLightY, 3, 5, 1, 0xFFFFB300.toInt())
        }

        val halfRoadPx = (roadW * scale * 0.5).roundToInt()
        val deckH = max(4, scale.roundToInt())
        val deckColor = if (cfg.pavingMode == PavingMode.CREATE_RAIL) 0xFF78909C.toInt() else 0xFF37474F.toInt()
        fillRoundedRect(
            g,
            (originX - halfRoadPx).roundToInt(),
            (floorY - deckH).roundToInt(),
            halfRoadPx * 2,
            deckH,
            2,
            deckColor
        )

        if (cfg.pavingMode == PavingMode.CREATE_RAIL) {
            val trainHalfW = (1.5 * scale).roundToInt()
            val trainH = (4.0 * scale).roundToInt()
            val envX = originX.roundToInt() - trainHalfW
            val envY = (floorY - deckH - trainH).roundToInt()
            strokeRoundedRect(g, envX, envY, trainHalfW * 2, trainH, 3, theme.primary)
            val railY = (floorY - deckH - 2).roundToInt()
            g.fill(originX.roundToInt() - (1.0 * scale).roundToInt(), railY, originX.roundToInt() - (1.0 * scale).roundToInt() + 2, railY + 2, 0xFFD84315.toInt())
            g.fill(originX.roundToInt() + (1.0 * scale).roundToInt() - 2, railY, originX.roundToInt() + (1.0 * scale).roundToInt(), railY + 2, 0xFFD84315.toInt())
        } else if (cfg.addLaneMarkings) {
            g.fill(originX.roundToInt() - 1, (floorY - deckH).roundToInt(), originX.roundToInt() + 1, (floorY - deckH + 2).roundToInt(), 0xFFFFD54F.toInt())
        }

        val activeLayout = if (cfg.pavingMode == PavingMode.CREATE_RAIL) cfg.railLampLayout else cfg.roadLampLayout
        if (activeLayout != com.terrapaver.config.LampLayout.OFF) {
            val postH = max(8, (scale * 3.8).roundToInt())
            val postY = (floorY - deckH - postH).roundToInt()
            val leftPostX = (originX - halfRoadPx - 2).roundToInt()
            val rightPostX = (originX + halfRoadPx + 1).roundToInt()
            val poleColor = 0xFF455A64.toInt()
            val glowColor = 0xFFFFCA28.toInt()

            g.fill(leftPostX, postY, leftPostX + 2, (floorY - deckH).roundToInt(), poleColor)
            g.fill(leftPostX, postY, leftPostX + 6, postY + 2, poleColor)
            g.fill(leftPostX + 4, postY + 2, leftPostX + 7, postY + 4, glowColor)

            if (activeLayout != com.terrapaver.config.LampLayout.SINGLE_SIDE) {
                g.fill(rightPostX, postY, rightPostX + 2, (floorY - deckH).roundToInt(), poleColor)
                g.fill(rightPostX - 4, postY, rightPostX + 2, postY + 2, poleColor)
                g.fill(rightPostX - 5, postY + 2, rightPostX - 2, postY + 4, glowColor)
            }
        }

        val dimText = fitText(font, I18n.get("terrapaver.preview.dims", roadW, tunnelW, tunnelH), w - 20)
        g.drawString(font, dimText, x + 10, y + h - 15, theme.primary, false)
    }
}
