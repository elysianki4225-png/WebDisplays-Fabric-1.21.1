package net.montoyo.wd.client.gui.controls;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.montoyo.wd.client.gui.WDTheme;

/**
 * Modern 26.3-styled WebDisplays button with smooth hover feedback and semantic variants.
 */
@Environment(EnvType.CLIENT)
public class WDButton extends Button {
    public enum Variant {
        DEFAULT,
        PRIMARY,
        DANGER,
        SUBTLE
    }

    private int foregroundColor = 0xFFFFFFFF;
    private Variant variant = Variant.DEFAULT;
    private float hoverProgress = 0.0f;

    public WDButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setFGColor(int color) {
        foregroundColor = (color & 0xFF000000) == 0 ? (0xFF000000 | color) : color;
    }

    public int getFGColor() {
        return foregroundColor;
    }

    public void setVariant(Variant variant) {
        this.variant = variant != null ? variant : Variant.DEFAULT;
    }

    public Variant getVariant() {
        return variant;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        boolean hovered = this.active && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        float target = hovered ? 1.0f : 0.0f;
        hoverProgress += (target - hoverProgress) * Math.min(1.0f, 0.32f + partialTick * 0.12f);

        int bg;
        int border;
        int textCol;

        if (!this.active) {
            bg = 0x70141922;
            border = 0xFF232B3A;
            textCol = WDTheme.TEXT_MUTED;
        } else {
            switch (variant) {
                case PRIMARY -> {
                    bg = WDTheme.lerpColor(0xD91D579B, 0xEC2771C7, hoverProgress);
                    border = WDTheme.lerpColor(0xFF3A82D6, 0xFF6AB8FF, hoverProgress);
                }
                case DANGER -> {
                    bg = WDTheme.lerpColor(0xD47F1D1D, 0xEC9F2323, hoverProgress);
                    border = WDTheme.lerpColor(0xFFB91C1C, 0xFFF87171, hoverProgress);
                }
                case SUBTLE -> {
                    bg = WDTheme.lerpColor(0x90181E2B, 0xCC242E42, hoverProgress);
                    border = WDTheme.lerpColor(0xFF2A3447, 0xFF4C6082, hoverProgress);
                }
                default -> {
                    bg = WDTheme.lerpColor(0xCC1E2634, 0xE62A364B, hoverProgress);
                    border = WDTheme.lerpColor(0xFF334057, 0xFF5A739C, hoverProgress);
                }
            }
            textCol = (foregroundColor != 0xFFFFFFFF) ? foregroundColor : WDTheme.TEXT_PRIMARY;
        }

        graphics.fill(x, y, x + w, y + h, bg);
        WDTheme.drawBorder(graphics, x, y, w, h, border);
        if (this.active) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, hovered ? 0x2EFFFFFF : 0x18FFFFFF);
        }

        graphics.centeredText(
                Minecraft.getInstance().font,
                getMessage(),
                x + w / 2,
                y + (h - 8) / 2,
                textCol
        );
    }
}
