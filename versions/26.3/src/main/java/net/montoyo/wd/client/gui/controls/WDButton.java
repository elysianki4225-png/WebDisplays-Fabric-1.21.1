package net.montoyo.wd.client.gui.controls;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Vanilla Button without Forge's extra accessors: adjustable height and text colour.
 */
@Environment(EnvType.CLIENT)
public class WDButton extends Button {
    private int foregroundColor = 0xFFFFFFFF;

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

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractDefaultSprite(graphics);
        if (foregroundColor != 0xFFFFFFFF) {
            graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, foregroundColor);
        } else {
            this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        }
    }
}
