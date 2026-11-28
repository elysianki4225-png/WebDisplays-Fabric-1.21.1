/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.montoyo.wd.client.gui.WDTheme;
import net.montoyo.wd.client.gui.loading.JsonOWrapper;

import java.util.ArrayList;

public class UpgradeGroup extends BasicControl {

    private int width;
    private int height;
    private int slotCount = 4;
    private ArrayList<ItemStack> upgrades;
    private ItemStack overStack;
    private ItemStack clickStack;

    public UpgradeGroup() {
        parent.requirePostDraw(this);
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
        int slots = Math.max(slotCount, upgrades != null ? upgrades.size() : 0);
        int x = this.x;

        for (int i = 0; i < slots; i++) {
            ItemStack is = (upgrades != null && i < upgrades.size()) ? upgrades.get(i) : null;
            boolean hovered = is != null && is == overStack && !disabled;

            int bg = hovered ? WDTheme.DANGER_SOFT : WDTheme.INSET_BG;
            int border = hovered ? WDTheme.DANGER : WDTheme.INSET_BORDER;
            graphics.fill(x, y, x + 18, y + 18, bg);
            WDTheme.drawBorder(graphics, x, y, 18, 18, border);

            if (is != null) {
                graphics.item(is, x + 1, y + 1);
                graphics.itemDecorations(font, is, x + 1, y + 1);
            }

            x += 20;
        }
    }

    @Override
    public void postDraw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
        if(overStack != null)
            parent.drawItemStackTooltip(graphics, overStack, mouseX, mouseY);
    }

    @Override
    public int getWidth() {
        int slots = Math.max(slotCount, upgrades != null ? upgrades.size() : 0);
        return Math.max(width, slots * 20 - 2);
    }

    @Override
    public int getHeight() {
        return Math.max(height, 18);
    }

    public void setWidth(int w) {
        width = w;
    }

    public void setHeight(int h) {
        height = h;
    }

    public void setUpgrades(ArrayList<ItemStack> upgrades) {
        this.upgrades = upgrades;
    }

    public ArrayList<ItemStack> getUpgrades() {
        return upgrades;
    }

    @Override
    public void load(JsonOWrapper json) {
        super.load(json);
        slotCount = json.getInt("slots", 4);
        width = json.getInt("width", slotCount * 20 - 2);
        height = json.getInt("height", 18);
    }

    @Override
    public boolean mouseMove(double mouseX, double mouseY) {
        if(upgrades != null) {
            overStack = null;

            if(mouseY >= y && mouseY <= y + 18 && mouseX >= x) {
                mouseX -= x;
                int sel = (int) (mouseX / 20);

                if(sel < upgrades.size() && mouseX % 20 <= 18)
                    overStack = upgrades.get(sel);

                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if(mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
            if (mouseX >= x && mouseX <= x + getWidth() && mouseY >= y && mouseY <= y + getHeight()) {
                clickStack = overStack;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        if(state == InputConstants.MOUSE_BUTTON_LEFT && clickStack != null) {
            if(clickStack == overStack && !disabled && upgrades.contains(clickStack))
                parent.actionPerformed(new ClickEvent(this));

            clickStack = null;
            return true;
        }

        return false;
    }

    public ItemStack getMouseOverUpgrade() {
        return overStack;
    }

    public static class ClickEvent extends Event<UpgradeGroup> {

        private final ItemStack clickStack;

        public ClickEvent(UpgradeGroup src) {
            source = src;
            clickStack = src.clickStack;
        }

        public ItemStack getMouseOverStack() {
            return clickStack;
        }

    }

}
