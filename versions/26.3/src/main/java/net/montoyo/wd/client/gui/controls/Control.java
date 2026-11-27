/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.montoyo.wd.client.gui.WDScreen;
import net.montoyo.wd.client.gui.loading.JsonOWrapper;
import net.montoyo.wd.utilities.Bounds;

@Environment(EnvType.CLIENT)
public abstract class Control {

    public static final int COLOR_BLACK    = 0xFF000000;
    public static final int COLOR_WHITE    = 0xFFFFFFFF;
    public static final int COLOR_RED      = 0xFFFF0000;
    public static final int COLOR_GREEN    = 0xFF00FF00;
    public static final int COLOR_BLUE     = 0xFF0000FF;
    public static final int COLOR_CYAN     = 0xFF00FFFF;
    public static final int COLOR_MANGENTA = 0xFFFF00FF;
    public static final int COLOR_YELLOW   = 0xFFFFFF00;

    protected final Minecraft mc;
    protected final Font font;
    protected static WDScreen parent;
    protected String name;
    protected Object userdata;

    public Control() {
        mc = Minecraft.getInstance();
        font = mc.font;
        parent = WDScreen.CURRENT_SCREEN;
    }

    public Object getUserdata() {
        return userdata;
    }

    public void setUserdata(Object userdata) {
        this.userdata = userdata;
    }

    public boolean keyTyped(int keyCode, int modifier) {
        return false;
    }

    public boolean keyUp(int key, int scanCode, int modifiers) {
        return false;
    }

    public boolean keyDown(int key, int scanCode, int modifiers) {
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        return false;
    }

    public void unfocus() {
    }

    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        return false;
    }

    public boolean mouseClickMove(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    public boolean mouseMove(double mouseX, double mouseY) {
        return false;
    }

    public boolean mouseScroll(double mouseX, double mouseY, double amount) {
        return false;
    }

    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
    }

    public void postDraw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
    }

    public void destroy() {
    }

    public WDScreen getParent() {
        return parent;
    }

    public abstract int getX();
    public abstract int getY();
    public abstract int getWidth();
    public abstract int getHeight();
    public abstract void setPos(int x, int y);

    public void fillRect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + h, color);
    }

    public void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        drawBorder(graphics, x, y, w, h, color, 1);
    }

    public void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color, int sz) {
        int x2 = x + w;
        int y2 = y + h;
        graphics.fill(x, y, x2, y + sz, color);
        graphics.fill(x, y2 - sz, x2, y2, color);
        graphics.fill(x, y, x + sz, y2, color);
        graphics.fill(x2 - sz, y, x2, y2, color);
    }

    public static int toARGB(int color) {
        return (color & 0xFF000000) == 0 ? (0xFF000000 | color) : color;
    }

    public static String tr(String text) {
        if(text.length() >= 2 && text.charAt(0) == '$') {
            if(text.charAt(1) == '$')
                return text.substring(1);
            else
                return I18n.get(text.substring(1));
        } else
            return text;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void load(JsonOWrapper json) {
        name = json.getString("name", "");
    }

    public static Bounds findBounds(java.util.List<Control> controlList) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for(Control ctrl : controlList) {
            int x = ctrl.getX();
            int y = ctrl.getY();
            if(x < minX)
                minX = x;

            if(y < minY)
                minY = y;

            x += ctrl.getWidth();
            y += ctrl.getHeight();

            if(x > maxX)
                maxX = x;

            if(y >= maxY)
                maxY = y;
        }

        return new Bounds(minX, minY, maxX, maxY);
    }

}
