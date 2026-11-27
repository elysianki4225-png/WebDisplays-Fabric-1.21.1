/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.montoyo.wd.client.gui.loading.JsonOWrapper;
import net.montoyo.wd.utilities.Bounds;

public class ControlGroup extends Container {

    private int width;
    private int height;
    private String label;
    private int labelW;
    private int labelColor = COLOR_WHITE;
    private boolean labelShadowed = true;

    public ControlGroup() {
        width = 100;
        height = 100;
        label = "";
        paddingX = 8;
        paddingY = 8;
    }

    public ControlGroup(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        width = w;
        height = h;
        paddingX = 8;
        paddingY = 8;
        label = "";
        labelW = 0;
    }

    public ControlGroup(int x, int y, int w, int h, String label) {
        this.x = x;
        this.y = y;
        width = w;
        height = h;
        this.label = label;
        this.labelW = font.width(label);
        paddingX = 8;
        paddingY = 8;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    public void setSize(int w, int h) {
        width = w;
        height = h;
    }

    public void setLabel(String label) {
        this.label = label;
        labelW = font.width(label);
    }

    public String getLabel() {
        return label;
    }

    public int getLabelColor() {
        return labelColor;
    }

    public void setLabelColor(int labelColor) {
        this.labelColor = toARGB(labelColor);
    }

    public boolean isLabelShadowed() {
        return labelShadowed;
    }

    public void setLabelShadowed(boolean labelShadowed) {
        this.labelShadowed = labelShadowed;
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
        super.draw(graphics, mouseX, mouseY, ptt);

        if(visible) {
            int bp = 4;
            int x1 = x + bp;
            int y1 = y + bp;
            int x2 = x + width - bp;
            int y2 = y + height - bp;
            int lw = labelW + 12;
            int borderCol = 0xFF808080;

            if(labelW == 0) {
                graphics.fill(x1, y1, x2, y1 + 1, borderCol);
            } else {
                graphics.fill(x1, y1, x1 + 8, y1 + 1, borderCol);
                graphics.fill(x1 + lw, y1, x2, y1 + 1, borderCol);
            }

            graphics.fill(x1, y2 - 1, x2, y2, borderCol);
            graphics.fill(x1, y1, x1 + 1, y2, borderCol);
            graphics.fill(x2 - 1, y1, x2, y2, borderCol);

            if(labelW != 0)
                graphics.text(Minecraft.getInstance().font, label, x + 10 + bp, y, toARGB(labelColor), labelShadowed);
        }
    }

    public void pack() {
        Bounds bounds = findBounds(childs);
        for(Control ctrl : childs)
            ctrl.setPos(ctrl.getX() - bounds.minX, ctrl.getY() - bounds.minY);

        width = bounds.getWidth() + paddingX * 2;
        height = bounds.getHeight() + paddingY * 2;
    }

    @Override
    public void unfocus() {
        for (Control control : childs) {
            control.unfocus();
        }
    }

    @Override
    public void load(JsonOWrapper json) {
        super.load(json);
        width = json.getInt("width", 100);
        height = json.getInt("height", 100);
        label = tr(json.getString("label", ""));
        labelW = font.width(label);
        labelColor = toARGB(json.getColor("labelColor", COLOR_WHITE));
        labelShadowed = json.getBool("labelShadowed", true);

        if(json.getBool("pack", false))
            pack();
    }

}
