package net.montoyo.wd.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared visual tokens and drawing primitives matching Minecraft 26.3's frosted-glass UI aesthetic.
 */
@Environment(EnvType.CLIENT)
public final class WDTheme {

    public static final int PANEL_BG = 0xD610151E;
    public static final int PANEL_HEADER_BG = 0xEA161D29;
    public static final int PANEL_BORDER = 0xFF2D3748;
    public static final int PANEL_HIGHLIGHT = 0x24FFFFFF;

    public static final int CARD_BG = 0x9E161C27;
    public static final int CARD_HEADER_BG = 0x441E2636;
    public static final int CARD_BORDER = 0xFF2A3446;

    public static final int INSET_BG = 0xD40B0F16;
    public static final int INSET_BORDER = 0xFF263042;

    public static final int ACCENT = 0xFF3B9EFF;
    public static final int ACCENT_SOFT = 0x333B9EFF;
    public static final int ACCENT_BORDER = 0xFF56ADFF;

    public static final int DANGER = 0xFFEF4444;
    public static final int DANGER_SOFT = 0x30EF4444;

    public static final int SUCCESS = 0xFF22C55E;

    public static final int TEXT_PRIMARY = 0xFFF3F6FC;
    public static final int TEXT_SECONDARY = 0xFF9AA7BC;
    public static final int TEXT_MUTED = 0xFF667285;

    private WDTheme() {
    }

    public static void drawDropShadow(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 3, y - 2, x + w + 3, y + h + 4, 0x22000000);
        g.fill(x - 2, y - 1, x + w + 2, y + h + 3, 0x33000000);
        g.fill(x - 1, y, x + w + 1, y + h + 2, 0x44000000);
    }

    public static void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void drawGlassPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        drawDropShadow(g, x, y, w, h);
        g.fill(x, y, x + w, y + h, PANEL_BG);
        drawBorder(g, x, y, w, h, PANEL_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, PANEL_HIGHLIGHT);
    }

    public static void drawGlassPanelWithHeader(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String title, String badgeText) {
        drawGlassPanel(g, x, y, w, h);
        int headerH = 24;
        g.fill(x + 1, y + 1, x + w - 1, y + headerH, PANEL_HEADER_BG);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, PANEL_HIGHLIGHT);
        g.fill(x + 1, y + headerH, x + w - 1, y + headerH + 1, PANEL_BORDER);
        // Subtle left accent bar on header
        g.fill(x + 1, y + 5, x + 3, y + headerH - 4, ACCENT);

        if (title != null && !title.isEmpty()) {
            g.text(font, title, x + 10, y + 8, TEXT_PRIMARY, true);
        }

        if (badgeText != null && !badgeText.isEmpty()) {
            int bw = font.width(badgeText) + 10;
            drawPillBadge(g, font, x + w - bw - 8, y + 5, badgeText, ACCENT_SOFT, 0x663B9EFF, 0xFFBFDBFE);
        }
    }

    public static void drawSectionCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String title) {
        g.fill(x, y, x + w, y + h, CARD_BG);
        drawBorder(g, x, y, w, h, CARD_BORDER);
        if (title != null && !title.isEmpty()) {
            g.fill(x + 1, y + 1, x + w - 1, y + 17, CARD_HEADER_BG);
            g.fill(x + 1, y + 17, x + w - 1, y + 18, CARD_BORDER);
            g.text(font, title, x + 7, y + 5, TEXT_SECONDARY, false);
        }
    }

    public static int drawPillBadge(GuiGraphicsExtractor g, Font font, int x, int y, String text, int bg, int border, int fg) {
        int bw = font.width(text) + 10;
        int bh = 14;
        g.fill(x, y, x + bw, y + bh, bg);
        drawBorder(g, x, y, bw, bh, border);
        g.text(font, text, x + 5, y + 3, fg, false);
        return bw;
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ca = Math.round(aa + (ba - aa) * t);
        int cr = Math.round(ar + (br - ar) * t);
        int cg = Math.round(ag + (bg - ag) * t);
        int cb = Math.round(ab + (bb - ab) * t);
        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }
}
