package com.terrapaver.client

import com.terrapaver.client.gui.M3Draw
import com.terrapaver.client.gui.M3Theme
import com.terrapaver.config.PavingConfig
import com.terrapaver.network.TerraPaverNet
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import kotlin.math.roundToInt

object PavingHudOverlay {
    fun render(g: GuiGraphics, deltaTracker: DeltaTracker) {
        if (!ClientPavingState.shouldShowHud()) return
        val st = ClientPavingState.status ?: return
        val mc = Minecraft.getInstance()
        val font = mc.font
        val screenW = mc.window.guiScaledWidth

        val theme = M3Theme.get(PavingConfig.clientConfig.m3SeedTheme)
        val cardW = 286
        val cardH = 44
        val x = (screenW - cardW) / 2
        val y = 10

        M3Draw.drawElevationShadow(g, x, y, cardW, cardH, 16, 3)
        M3Draw.fillRoundedRect(g, x, y, cardW, cardH, 16, theme.surface)
        M3Draw.strokeRoundedRect(g, x, y, cardW, cardH, 16, theme.outlineVariant)

        val dotColor = when {
            st.paused -> 0xFFF57C00.toInt()
            st.phase == TerraPaverNet.S2CPavingStatus.PHASE_DONE -> 0xFF146C2E.toInt()
            else -> theme.primary
        }
        M3Draw.fillCircle(g, x + 16, y + 15, 4, dotColor)

        val label = I18n.get(st.statusKey)
        g.drawString(font, label, x + 26, y + 11, theme.onSurface, false)

        val pct = "${(st.progress * 100f).roundToInt()}%"
        val pctW = font.width(pct)
        M3Draw.fillRoundedRect(g, x + cardW - pctW - 20, y + 8, pctW + 12, 13, 6, theme.primaryContainer)
        g.drawString(font, pct, x + cardW - pctW - 14, y + 11, theme.onPrimaryContainer, false)

        M3Draw.drawLinearProgress(
            g, x + 12, y + 25, cardW - 24, 6,
            st.progress, theme,
            indeterminate = st.active && st.phase == TerraPaverNet.S2CPavingStatus.PHASE_SURVEYING && st.progress < 0.05f
        )

        val meta = I18n.get("terrapaver.hud.meta", st.placedBlocks, st.tunnelSegments, st.viaductSegments)
        g.drawString(font, meta, x + 12, y + 34, theme.onSurfaceVariant, false)
    }
}
