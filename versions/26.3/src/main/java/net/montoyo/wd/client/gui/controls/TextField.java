/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.montoyo.wd.client.gui.loading.JsonOWrapper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

public class TextField extends Control {

    public static class EnterPressedEvent extends Event<TextField> {

        private final String text;

        public EnterPressedEvent(TextField field) {
            source = field;
            text = field.field.getValue();
        }

        public String getText() {
            return text;
        }

    }

    public static class TabPressedEvent extends Event<TextField> {

        private final String beginning;

        public TabPressedEvent(TextField field) {
            source = field;

            String text = field.field.getValue();
            int max = field.field.getCursorPosition();
            int spacePos = 0;

            for(int i = max - 1; i >= 0; i--) {
                if(Character.isSpaceChar(text.charAt(i))) {
                    spacePos = i;
                    break;
                }
            }

            beginning = text.substring(spacePos, max).trim();
        }

        public String getBeginning() {
            return beginning;
        }

    }

    public static class TextChangedEvent extends Event<TextField> {

        private final String oldContent;
        private final String newContent;

        public TextChangedEvent(TextField tf, String old) {
            source = tf;
            oldContent = old;
            newContent = tf.field.getValue();
        }

        public String getOldContent() {
            return oldContent;
        }

        public String getNewContent() {
            return newContent;
        }

    }

    public interface TextChangeListener {

        void onTextChange(TextField tf, String oldContent, String newContent);

    }

    public static final int DEFAULT_TEXT_COLOR = 0xFFE0E0E0;
    public static final int DEFAULT_DISABLED_COLOR = 0xFF707070;

    private final WDEditBox field;
    private boolean enabled = true;
    private int textColor = DEFAULT_TEXT_COLOR;
    private int disabledColor = DEFAULT_DISABLED_COLOR;
    private final ArrayList<TextChangeListener> listeners = new ArrayList<>();

    public TextField() {
        field = new WDEditBox(font, 1, 1, 198, 20, Component.nullToEmpty(""));
        setFocused(false);
    }

    public TextField(int x, int y, int width, int height) {
        field = new WDEditBox(font, x + 1, y + 1, width - 2, height - 2, Component.nullToEmpty(""));
        setFocused(false);
    }

    public TextField(int x, int y, int width, int height, String text) {
        field = new WDEditBox(font, x + 1, y + 1, width - 2, height - 2, Component.nullToEmpty(""));
        field.setValue(text);
        setFocused(false);
    }

    @Override
    public boolean keyDown(int key, int scanCode, int modifiers) {
        String old = (enabled && field.isFocused()) ? field.getValue() : null;
        boolean handled = field.keyPressed(new KeyEvent(key, scanCode, modifiers));
        notifyIfChanged(old);
        return handled;
    }

    @Override
    public boolean keyUp(int key, int scanCode, int modifiers) {
        return field.keyReleased(new KeyEvent(key, scanCode, modifiers));
    }

    @Override
    public boolean keyTyped(int keyCode, int modifier) {
        if(keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
            parent.actionPerformed(new EnterPressedEvent(this));
        else if(keyCode == GLFW.GLFW_KEY_TAB)
            parent.actionPerformed(new TabPressedEvent(this));
        else {
            String old = (enabled && field.isFocused()) ? field.getValue() : null;
            boolean handled = field.charTyped(new CharacterEvent(keyCode));
            notifyIfChanged(old);
            return handled;
        }

        return false;
    }

    private void notifyIfChanged(String old) {
        if(old != null && !field.getValue().equals(old)) {
            for(TextChangeListener tcl : listeners)
                tcl.onTextChange(this, old, field.getValue());

            parent.actionPerformed(new TextChangedEvent(this, old));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (field.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(mouseButton, 0)), false)) {
            setFocused(true);
            return true;
        }
        return false;
    }

    @Override
    public void unfocus() {
        setFocused(false);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        return field.mouseReleased(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(state, 0)));
    }

    @Override
    public boolean mouseClickMove(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return field.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(0, 0)), false);
    }

    @Override
    public boolean mouseMove(double mouseX, double mouseY) {
        field.mouseMoved(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseScroll(double mouseX, double mouseY, double amount) {
        return field.mouseScrolled(mouseX, mouseY, 0.0, amount);
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
        field.extractRenderState(graphics, mouseX, mouseY, ptt);
    }

    public void setText(String text) {
        String old = field.getValue();
        field.setValue(text);

        if(!old.equals(text)) {
            for(TextChangeListener tcl : listeners)
                tcl.onTextChange(this, old, text);
        }
    }

    public void clear() {
        field.setValue("");
    }

    public String getText() {
        return field.getValue();
    }

    public String getSelectedText() {
        return field.getHighlighted();
    }

    public void setWidth(int width) {
        field.setWidth(width - 2);
    }

    @Override
    public int getWidth() {
        return field.getWidth() + 2;
    }

    public void setHeight(int height) {
        field.setHeight(height - 2);
    }

    @Override
    public int getHeight() {
        return field.getHeight() + 2;
    }

    public void setSize(int w, int h) {
        field.setWidth(w - 2);
        field.setHeight(h - 2);
    }

    @Override
    public void setPos(int x, int y) {
        field.setPosition(
                x + 1,
                y + 1
        );
    }

    @Override
    public int getX() {
        return field.getX() - 1;
    }

    @Override
    public int getY() {
        return field.getY() - 1;
    }

    public void setDisabled(boolean en) {
        enabled = !en;
        if (!en)
            field.setFocused(false);
    }

    public boolean isDisabled() {
        return !enabled;
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        field.setFocused(false);
        enabled = false;
    }

    public void setVisible(boolean vi) {
        field.setVisible(vi);
    }

    public boolean isVisible() {
        return field.isVisible();
    }

    public void show() {
        field.setVisible(true);
    }

    public void hide() {
        field.setVisible(false);
    }

    public void setFocused(boolean val) {
        field.setFocused(val);
    }

    public boolean hasFocus() {
        return field.isFocused();
    }

    public void focus() {
        field.setFocused(true);
    }

    public void setTextColor(int color) {
        textColor = toARGB(color);
        field.setTextColor(textColor);
    }

    public int getTextColor() {
        return textColor;
    }

    public void setDisabledTextColor(int color) {
        disabledColor = toARGB(color);
        field.setTextColorUneditable(disabledColor);
    }

    public int getDisabledTextColor() {
        return disabledColor;
    }

    public EditBox getMcField() {
        return field;
    }

    public void addTextChangeListener(TextChangeListener l) {
        if(l != null && !listeners.contains(l))
            listeners.add(l);
    }

    public void removeTextChangeListener(TextChangeListener l) {
        listeners.remove(l);
    }

    @Override
    public void load(JsonOWrapper json) {
        super.load(json);
        field.setPosition(
                json.getInt("x", 0) + 1,
                json.getInt("y", 0) + 1
        );
        field.setWidth(json.getInt("width", 200) - 2);
        field.setHeight(json.getInt("height", 22) - 2);
        field.setValue(tr(json.getString("text", "")));
        field.setVisible(json.getBool("visible", true));
        field.setMaxLength(json.getInt("maxLength", 32));

        enabled = !json.getBool("disabled", false);
        textColor = toARGB(json.getColor("textColor", DEFAULT_TEXT_COLOR));
        disabledColor = toARGB(json.getColor("disabledColor", DEFAULT_DISABLED_COLOR));

        field.setTextColor(textColor);
        field.setTextColorUneditable(disabledColor);
        setFocused(false);
    }

}
