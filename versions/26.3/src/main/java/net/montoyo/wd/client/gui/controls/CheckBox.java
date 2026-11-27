/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls;

import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.montoyo.wd.client.gui.WDTheme;
import net.montoyo.wd.client.gui.loading.JsonOWrapper;

public class CheckBox extends BasicControl {

    public static final int WIDTH = 14;
    public static final int HEIGHT = 14;

    public static class CheckedEvent extends Event<CheckBox> {

        private final boolean checked;

        public CheckedEvent(CheckBox cb) {
            source = cb;
            checked = cb.checked;
        }

        public boolean isChecked() {
            return checked;
        }

    }

    private String label;
    private int labelW;
    private boolean checked;
    private java.util.List<String> tooltip;

    public CheckBox() {
        label = "";
    }

    public CheckBox(int x, int y, String label) {
        this.label = label;
        labelW = font.width(label);
        checked = false;
        this.x = x;
        this.y = y;
    }

    public CheckBox(int x, int y, String label, boolean val) {
        this.label = label;
        labelW = font.width(label);
        checked = val;
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if(mouseButton == 0 && !disabled) {
            if(mouseX >= x && mouseX <= x + WIDTH + 4 + labelW && mouseY >= y && mouseY < y + HEIGHT) {
                checked = !checked;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                parent.actionPerformed(new CheckedEvent(this));

                return true;
            }
        }

        return false;
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
        if(visible) {
            boolean inside = (!disabled && mouseX >= x && mouseX <= x + WIDTH + 4 + labelW && mouseY >= y && mouseY < y + HEIGHT);
            int bx = x + 1;
            int by = y + 1;
            int bSize = 12;

            int bg;
            int border;
            if (disabled) {
                bg = checked ? 0x661D4ED8 : 0x70111620;
                border = 0xFF283142;
            } else if (checked) {
                bg = inside ? 0xFF3B82F6 : 0xFF2563EB;
                border = inside ? 0xFF93C5FD : 0xFF60A5FA;
            } else {
                bg = inside ? 0xE018202E : 0xD40E131C;
                border = inside ? 0xFF5A739C : 0xFF374357;
            }

            graphics.fill(bx, by, bx + bSize, by + bSize, bg);
            WDTheme.drawBorder(graphics, bx, by, bSize, bSize, border);

            if (checked) {
                int checkCol = disabled ? 0x99CBD5E1 : 0xFFFFFFFF;
                // Crisp vector checkmark inside 12x12 box
                graphics.fill(bx + 3, by + 6, bx + 4, by + 8, checkCol);
                graphics.fill(bx + 4, by + 7, bx + 5, by + 9, checkCol);
                graphics.fill(bx + 5, by + 8, bx + 6, by + 9, checkCol);
                graphics.fill(bx + 6, by + 6, bx + 7, by + 8, checkCol);
                graphics.fill(bx + 7, by + 5, bx + 8, by + 7, checkCol);
                graphics.fill(bx + 8, by + 4, bx + 9, by + 6, checkCol);
                graphics.fill(bx + 9, by + 3, bx + 10, by + 5, checkCol);
            }

            int labelCol = disabled ? WDTheme.TEXT_MUTED : (inside ? 0xFF93C5FD : WDTheme.TEXT_PRIMARY);
            graphics.text(Minecraft.getInstance().font, label, x + WIDTH + 3, y + 3, labelCol, false);
        }
    }

    public void setLabel(String label) {
        this.label = label;
        labelW = font.width(label);
    }

    public String getLabel() {
        return label;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Override
    public int getWidth() {
        return WIDTH + 4 + labelW;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void load(JsonOWrapper json) {
        super.load(json);
        label = tr(json.getString("label", ""));
        labelW = font.width(label);
        checked = json.getBool("checked", false);

        String tt = tr(json.getString("tooltip", ""));
        if(!tt.isEmpty()) {
            tooltip = Lists.newArrayList(tt.split("\\\\n"));
            parent.requirePostDraw(this);
        }
    }

    @Override
    public void postDraw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
        if(tooltip != null && !disabled && mouseX >= x && mouseX <= x + WIDTH + 4 + labelW && mouseY >= y && mouseY < y + HEIGHT)
            parent.drawTooltip(graphics, tooltip, mouseX, mouseY);
    }

}
