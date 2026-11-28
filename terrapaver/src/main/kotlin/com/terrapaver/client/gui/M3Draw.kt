package com.terrapaver.client.gui

import com.terrapaver.config.PavingConfig
import com.terrapaver.config.PavingMode
import com.terrapaver.config.TunnelShape
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object M3Draw {
    fun fillRoundedRect(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, radius: Int, color: Int) {
        if (w <= 0 || h <= 0) return
        val r = radius.coerceIn(0, min(w, h) / 2)
        if (r <= 1) {
            g.fill(x, y, x + w, y + h, color)
            return
        }
        // Center body
        g.fill(x + r, y, x + w - r, y + h, color)
        // Left & right middle strips
        g.fill(x, y + r, x + r, y + h - r, color)
        g.fill(x + w - r, y + r, x + w, y + h - r, color)

        // Smooth circular corner scanlines
        for (dy in 0 until r) {
            val cy = r - dy - 0.5
            val dx = floor(r - sqrt(max(0.0, r * r - cy * cy))).toInt()
            val xL = x + dx
            val xR = x + w - dx
            g.fill(xL, y + dy, x + r, y + dy + 1, color)
            g.fill(x + w - r, y + dy, xR, y + dy + 1, color)
            val by = y + h - 1 - dy
            g.fill(xL, by, x + r, by + 1, color)
            g.fill(x + w - r, by, xR, by + 1, color)
        }
    }

    fun strokeRoundedRect(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, radius: Int, color: Int) {
        if (w <= 2 || h <= 2) return
        val r = radius.coerceIn(0, min(w, h) / 2)
        if (r <= 1) {
            g.fill(x, y, x + w, y + 1, color)
            g.fill(x, y + h - 1, x + w, y + h, color)
            g.fill(x, y, x + 1, y + h, color)
            g.fill(x + w - 1, y, x + w, y + h, color)
            return
        }
        // Straight edges
        g.fill(x + r, y, x + w - r, y + 1, color)
        g.fill(x + r, y + h - 1, x + w - r, y + h, color)
        g.fill(x, y + r, x + 1, y + h - r, color)
        g.fill(x + w - 1, y + r, x + w, y + h - r, color)

        var prevDx = r
        for (dy in 0 until r) {
            val cy = r - dy - 0.5
            val dx = floor(r - sqrt(max(0.0, r * r - cy * cy))).toInt()
            val span = max(1, prevDx - dx)
            // Top-left & top-right
            g.fill(x + dx, y + dy, x + dx + span, y + dy + 1, color)
            g.fill(x + w - dx - span, y + dy, x + w - dx, y + dy + 1, color)
            // Bottom-left & bottom-right
            val by = y + h - 1 - dy
            g.fill(x + dx, by, x + dx + span, by + 1, color)
            g.fill(x + w - dx - span, by, x + w - dx, by + 1, color)
            prevDx = dx
        }
    }

    fun drawElevationShadow(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, radius: Int, layers: Int = 3) {
        for (i in layers downTo 1) {
            val alpha = 10 + (layers - i) * 5
            fillRoundedRect(
                g,
                x - i + 1,
                y + i,
                w + (i - 1) * 2,
                h + 1,
                radius + i,
                M3Theme.withAlpha(0x00102030, alpha)
            )
        }
    }

    fun fillCircle(g: GuiGraphics, cx: Int, cy: Int, r: Int, color: Int) {
        fillRoundedRect(g, cx - r, cy - r, r * 2, r * 2, r, color)
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
        val r = h / 2
        fillRoundedRect(g, x, y, w, h, r, theme.surfaceHigh)
        if (indeterminate) {
            val phase = ((System.currentTimeMillis() % 1400L) / 1400.0f)
            val segW = max(h * 2, (w * 0.34f).toInt())
            val start = x + ((w - segW) * phase).toInt()
            fillRoundedRect(g, start, y, segW, h, r, theme.primary)
        } else {
            val p = progress.coerceIn(0f, 1f)
            val fillW = (w * p).roundToInt()
            if (fillW >= h) {
                fillRoundedRect(g, x, y, fillW, h, r, theme.primary)
            } else if (fillW > 0) {
                fillRoundedRect(g, x, y, h, h, r, theme.primary)
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
            strokeRoundedRect(g, x, y, w, h, radius, M3Theme.withAlpha(theme.outlineVariant, 160))
        } else {
            fillRoundedRect(g, x, y, w, h, radius, theme.surfaceContainer)
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
        strokeRoundedRect(g, x, y, w, h, 12, theme.outlineVariant)

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

        // Draw subtle grid dots
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

        // Tunnel outer shell + interior
        val halfTunnelPx = (tunnelW * scale * 0.5).roundToInt()
        val shellThick = max(3, (scale * 0.9).roundToInt())
        val tunnelTopPx = (tunnelH * scale).roundToInt()

        val shellColor = M3Theme.blend(theme.outline, theme.primaryContainer, 0.35f)
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
                // Square bottom corners of arch
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

        // Ceiling lantern indicator
        fillRoundedRect(g, originX.roundToInt() - 4, (floorY - tunnelTopPx + 1).roundToInt(), 8, 3, 1, 0xFFFFB300.toInt())

        // Roadbed / Create Railbed deck
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
            // Draw Create Train clearance envelope (dashed outline 3w x 4h)
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

        // Bottom dimension badge
        val dimText = "${roadW}W  |  Tunnel ${tunnelW}×${tunnelH}"
        g.drawString(font, dimText, x + 10, y + h - 15, theme.primary, false)
    }
}
