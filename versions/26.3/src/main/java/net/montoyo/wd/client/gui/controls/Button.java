/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.montoyo.wd.client.gui.loading.JsonOWrapper;

public class Button extends Control {

    protected final WDButton btn;
    protected boolean selected = false;
    protected boolean shiftDown = false;
    protected int originalColor = COLOR_WHITE;
    protected int shiftColor = COLOR_WHITE;

    public static class ClickEvent extends Event<Button> {

        private final boolean shiftDown;

        public ClickEvent(Button btn) {
            source = btn;
            shiftDown = btn.shiftDown;
        }

        public boolean isShiftDown() {
            return shiftDown;
        }

    }

    public Button() {
        btn = new WDButton(0, 0, 0, 0, Component.nullToEmpty(""), a -> {});
    }

    public Button(String text, int x, int y, int width) {
        btn = new WDButton(x, y, width, 20, Component.nullToEmpty(text), a -> {});
    }

    public Button(String text, int x, int y) {
        btn = new WDButton(0, 0, x, y, Component.nullToEmpty(text), a -> {});
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(mouseButton, 0));
        if(mouseButton == 0 && btn.mouseClicked(event, false)) {
            selected = true;
            btn.playDownSound(mc.getSoundManager());

            if(!onClick())
                parent.actionPerformed(new ClickEvent(this));
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        if(selected && state == 0) {
            btn.mouseReleased(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(state, 0)));
            selected = false;

            return true;
        }

        return true;
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
        btn.setFGColor(shiftDown ? shiftColor : originalColor);
        btn.extractRenderState(graphics, mouseX, mouseY, ptt);
    }

    public void setLabel(String label) {
        btn.setMessage(Component.nullToEmpty(label));
    }

    public String getLabel() {
        return btn.getMessage().getString();
    }

    public void setWidth(int width) {
        btn.setWidth(width);
    }

    public void setHeight(int height) {
        btn.setHeight(height);
    }

    @Override
    public int getWidth() {
        return btn.getWidth();
    }

    @Override
    public int getHeight() {
        return btn.getHeight();
    }

    @Override
    public void setPos(int x, int y) {
        btn.setPosition(x, y);
    }

    @Override
    public int getX() {
        return btn.getX();
    }

    @Override
    public int getY() {
        return btn.getY();
    }

    public net.minecraft.client.gui.components.Button getMcButton() {
        return btn;
    }

    public void setDisabled(boolean dis) {
        btn.active = !dis;
    }

    public boolean isDisabled() {
        return !btn.active;
    }

    public void enable() {
        btn.active = true;
    }

    public void disable() {
        btn.active = false;
    }

    public void setVisible(boolean visible) {
        btn.visible = visible;
    }

    public boolean isVisible() {
        return btn.visible;
    }

    public void show() {
        btn.visible = true;
    }

    public void hide() {
        btn.visible = false;
    }

    public boolean isShiftDown() {
        return shiftDown;
    }

    @Override
    public boolean keyUp(int key, int scanCode, int modifiers) {
        if(key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT) {
            shiftDown = false;
            btn.setFGColor(originalColor);

            return true;
        }

        return false;
    }

    @Override
    public boolean keyDown(int key, int scanCode, int modifiers) {
        if(key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT) {
            shiftDown = true;
            btn.setFGColor(shiftColor);

            return true;
        }

        return false;
    }

    public void setTextColor(int color) {
        originalColor = toARGB(color);
        if(!shiftDown)
            btn.setFGColor(originalColor);
    }

    public int getTextColor() {
        return btn.getFGColor();
    }

    public void setShiftTextColor(int shiftColor) {
        this.shiftColor = toARGB(shiftColor);
        if(shiftDown)
            btn.setFGColor(this.shiftColor);
    }

    public int getShiftTextColor() {
        return shiftColor;
    }

    @Override
    public void load(JsonOWrapper json) {
        super.load(json);
        btn.setPosition(
                json.getInt("x", 0),
                json.getInt("y", 0)
        );
        btn.setWidth(json.getInt("width", 200));
        btn.setHeight(json.getInt("height", 20));
        btn.setMessage(Component.nullToEmpty(tr(json.getString("label", btn.getMessage().getString()))));
        btn.active = json.getBool("active", btn.active);
        btn.visible = json.getBool("visible", btn.visible);

        originalColor = toARGB(json.getColor("color", originalColor));
        shiftColor = toARGB(json.getColor("shiftColor", shiftColor));
        btn.setFGColor(originalColor);
    }

    protected boolean onClick() {
        return false;
    }

}
