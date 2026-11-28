package net.montoyo.wd.client.gui

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared visual tokens and drawing primitives matching Minecraft 26.3's frosted-glass UI aesthetic.
 */
@Environment(EnvType.CLIENT)
object WDTheme {
    const val PANEL_BG = 0xD610151E.toInt()
    const val PANEL_HEADER_BG = 0xEA161D29.toInt()
    const val PANEL_BORDER = 0xFF2D3748.toInt()
    const val PANEL_HIGHLIGHT = 0x24FFFFFF

    const val CARD_BG = 0x9E161C27.toInt()
    const val CARD_HEADER_BG = 0x441E2636
    const val CARD_BORDER = 0xFF2A3446.toInt()

    const val INSET_BG = 0xD40B0F16.toInt()
    const val INSET_BORDER = 0xFF263042.toInt()

    const val ACCENT = 0xFF3B9EFF.toInt()
    const val ACCENT_SOFT = 0x333B9EFF
    const val ACCENT_BORDER = 0xFF56ADFF.toInt()

    const val DANGER = 0xFFEF4444.toInt()
    const val DANGER_SOFT = 0x30EF4444

    const val SUCCESS = 0xFF22C55E.toInt()

    const val TEXT_PRIMARY = 0xFFF3F6FC.toInt()
    const val TEXT_SECONDARY = 0xFF9AA7BC.toInt()
    const val TEXT_MUTED = 0xFF667285.toInt()

    @JvmStatic
    fun drawDropShadow(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        g.fill(x - 3, y - 2, x + w + 3, y + h + 4, 0x22000000)
        g.fill(x - 2, y - 1, x + w + 2, y + h + 3, 0x33000000)
        g.fill(x - 1, y, x + w + 1, y + h + 2, 0x44000000)
    }

    @JvmStatic
    fun drawBorder(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        g.fill(x, y, x + w, y + 1, color)
        g.fill(x, y + h - 1, x + w, y + h, color)
        g.fill(x, y + 1, x + 1, y + h - 1, color)
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color)
    }

    @JvmStatic
    fun drawGlassPanel(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        drawDropShadow(g, x, y, w, h)
        g.fill(x, y, x + w, y + h, PANEL_BG)
        drawBorder(g, x, y, w, h, PANEL_BORDER)
        g.fill(x + 1, y + 1, x + w - 1, y + 2, PANEL_HIGHLIGHT)
    }

    @JvmStatic
    fun drawGlassPanelWithHeader(
        g: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        title: String?,
        badgeText: String?
    ) {
        drawGlassPanel(g, x, y, w, h)
        val headerH = 24
        g.fill(x + 1, y + 1, x + w - 1, y + headerH, PANEL_HEADER_BG)
        g.fill(x + 1, y + 1, x + w - 1, y + 2, PANEL_HIGHLIGHT)
        g.fill(x + 1, y + headerH, x + w - 1, y + headerH + 1, PANEL_BORDER)
        g.fill(x + 1, y + 5, x + 3, y + headerH - 4, ACCENT)

        if (!title.isNullOrEmpty()) {
            g.text(font, title, x + 10, y + 8, TEXT_PRIMARY, true)
        }

        if (!badgeText.isNullOrEmpty()) {
            val bw = font.width(badgeText) + 10
            drawPillBadge(g, font, x + w - bw - 8, y + 5, badgeText, ACCENT_SOFT, 0x663B9EFF, 0xFFBFDBFE.toInt())
        }
    }

    @JvmStatic
    fun drawSectionCard(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, title: String?) {
        g.fill(x, y, x + w, y + h, CARD_BG)
        drawBorder(g, x, y, w, h, CARD_BORDER)
        if (!title.isNullOrEmpty()) {
            g.fill(x + 1, y + 1, x + w - 1, y + 17, CARD_HEADER_BG)
            g.fill(x + 1, y + 17, x + w - 1, y + 18, CARD_BORDER)
            g.text(font, title, x + 7, y + 5, TEXT_SECONDARY, false)
        }
    }

    @JvmStatic
    fun drawPillBadge(
        g: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        y: Int,
        text: String,
        bg: Int,
        border: Int,
        fg: Int
    ): Int {
        val bw = font.width(text) + 10
        val bh = 14
        g.fill(x, y, x + bw, y + bh, bg)
        drawBorder(g, x, y, bw, bh, border)
        g.text(font, text, x + 5, y + 3, fg, false)
        return bw
    }

    @JvmStatic
    fun lerpColor(a: Int, b: Int, t: Float): Int {
        val clamped = max(0.0f, min(1.0f, t))
        val aa = (a ushr 24) and 0xFF
        val ar = (a ushr 16) and 0xFF
        val ag = (a ushr 8) and 0xFF
        val ab = a and 0xFF
        val ba = (b ushr 24) and 0xFF
        val br = (b ushr 16) and 0xFF
        val bg = (b ushr 8) and 0xFF
        val bb = b and 0xFF
        val ca = (aa + (ba - aa) * clamped).roundToInt()
        val cr = (ar + (br - ar) * clamped).roundToInt()
        val cg = (ag + (bg - ag) * clamped).roundToInt()
        val cb = (ab + (bb - ab) * clamped).roundToInt()
        return (ca shl 24) or (cr shl 16) or (cg shl 8) or cb
    }
}
