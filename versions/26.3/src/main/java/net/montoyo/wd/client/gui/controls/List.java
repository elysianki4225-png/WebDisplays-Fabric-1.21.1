/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.montoyo.wd.client.gui.WDTheme;
import net.montoyo.wd.client.gui.loading.JsonOWrapper;

import java.util.ArrayList;

public class List extends BasicControl {

    private static class Entry {
        public final String text;
        public final Object userdata;

        public Entry(String t, Object o) {
            text = t;
            userdata = o;
        }

    }

    public static class EntryClick extends Event<List> {

        private final int id;
        private final Entry entry;

        public EntryClick(List lst) {
            source = lst;
            id = lst.selected;
            entry = lst.content.get(lst.selected);
        }

        public int getId() {
            return id;
        }

        public String getLabel() {
            return entry.text;
        }

        public Object getUserdata() {
            return entry.userdata;
        }

    }

    private int width;
    private int height;
    private final ArrayList<Entry> content = new ArrayList<>();
    private int selected = -1;
    private int selColor = 0xFF0080FF;

    //Scroll handling
    private int contentH = 0;
    private int scrollSize;
    private double scrollPos = 0;
    private boolean scrolling = false;
    private double scrollGrab;

    public List() {
        content.add(new Entry("", null));
        selected = 0;
    }

    public List(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        width = w;
        height = h;
        scrollSize = h - 2;
    }

    private int getYOffset() {
        if (height - 2 - scrollSize <= 0 || contentH <= height) {
            return 0;
        }
        double amount = ((double) scrollPos) / ((double) (height - 2 - scrollSize)) * ((double) (contentH - height));
        return (int) amount;
    }

    private boolean isInScrollbar(double mouseX, double mouseY) {
        return mouseX >= x + width - 5 && mouseX <= x + width - 1 && mouseY >= y + 1 + scrollPos && mouseY <= y + 1 + scrollPos + scrollSize;
    }

    @Override
    public void destroy() {
    }

    public void setSize(int w, int h) {
        width = w;
        height = h;
        updateContent();
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
        updateContent();
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    public void updateContent() {
        contentH = content.size() * 12 + 4;

        int h2 = height - 2;
        if(contentH <= h2) {
            scrollSize = h2;
            scrollPos = 0;
        } else {
            scrollSize = h2 * h2 / contentH;

            if(scrollSize < 4)
                scrollSize = 4;
        }
    }

    public int addElement(String str) {
        return addElement(str, null);
    }

    public int addElement(String str, Object ud) {
        content.add(new Entry(str, ud));
        updateContent();
        return content.size() - 1;
    }

    public int addElementRaw(String str) {
        return addElement(str, null);
    }

    public int addElementRaw(String str, Object ud) {
        content.add(new Entry(str, ud));
        return content.size() - 1;
    }

    @Override
    public void setDisabled(boolean dis) {
        disabled = dis;

        if(dis)
            selected = -1;
    }

    @Override
    public void disable() {
        disabled = true;
        selected = -1;
    }

    @Override
    public boolean mouseMove(double mouseX, double mouseY) {
        int sel = -1;
        if(!disabled && mouseX >= x + 1 && mouseX <= x + width - 6 && mouseY >= y + 2 && mouseY <= y + height - 2) {
            int offset = y + 4 - getYOffset();
            sel = (int) ((mouseY - offset) / 12);

            if(sel < 0 || sel >= content.size())
                sel = -1;
        }

        if(selected != sel) {
            selected = sel;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if(!disabled && mouseButton == 0) {
            if(isInScrollbar(mouseX, mouseY)) {
                scrolling = true;
                scrollGrab = mouseY - (y + 1 + scrollPos);
                return true;
            } else if(selected >= 0) {
                parent.actionPerformed(new EntryClick(this));
                return true;
            }

            return false;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        if(!disabled && scrolling) {
            scrolling = false;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScroll(double mouseX, double mouseY, double amount) {
        if(!disabled && !scrolling && contentH > height && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            double disp = 12.d * ((double) (height - 2 - scrollSize)) / ((double) (contentH - height));
            double sp = scrollPos;

            if(amount < 0)
                sp += (int) disp;
            else
                sp -= (int) disp;

            if(sp < 0)
                sp = 0;
            else if(sp > height - 2 - scrollSize)
                sp = height - 2 - scrollSize;

            if(sp != scrollPos)
                scrollPos = sp;

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseClickMove(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if(!disabled && scrolling) {
            double sp = mouseY - scrollGrab - y - 1;
            if(sp < 0)
                sp = 0;
            else if(sp > height - 2 - scrollSize)
                sp = height - 2 - scrollSize;

            if(scrollPos != sp)
                scrollPos = sp;

            return true;
        }

        return false;
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
        if(visible) {
            graphics.fill(x, y, x + width, y + height, WDTheme.INSET_BG);

            graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
            int offset = 4 - getYOffset();
            boolean isDangerSel = (selColor & 0x00FFFFFF) == 0x00FF0000;
            int rowBg = isDangerSel ? WDTheme.DANGER_SOFT : WDTheme.ACCENT_SOFT;
            int rowTextSel = isDangerSel ? 0xFFFCA5A5 : 0xFF93C5FD;

            for(int i = 0; i < content.size(); i++) {
                int pos = i * 12 + offset;

                if(pos + 12 >= 1) {
                    if(pos >= height - 1)
                        break;

                    if (i == selected) {
                        graphics.fill(x + 2, y + pos - 2, x + width - 6, y + pos + 10, rowBg);
                    } else if ((i & 1) == 1) {
                        graphics.fill(x + 2, y + pos - 2, x + width - 6, y + pos + 10, 0x10FFFFFF);
                    }

                    int color = (i == selected) ? rowTextSel : WDTheme.TEXT_PRIMARY;
                    graphics.text(font, content.get(i).text, x + 5, y + pos, color, false);
                    if (i == selected && isDangerSel) {
                        graphics.text(font, "×", x + width - 14, y + pos, 0xFFF87171, false);
                    }
                }
            }
            graphics.disableScissor();

            WDTheme.drawBorder(graphics, x, y, width, height, WDTheme.INSET_BORDER);

            if (contentH > height) {
                int sbY = y + 2 + (int) scrollPos;
                int sbH = Math.max(8, scrollSize - 2);
                fillRect(graphics, x + width - 4, sbY, 2, sbH, (scrolling || isInScrollbar(mouseX, mouseY)) ? WDTheme.ACCENT : 0xFF475569);
            }
        }
    }

    public String getEntryLabel(int id) {
        return content.get(id).text;
    }

    public Object getEntryUserdata(int id) {
        return content.get(id).userdata;
    }

    public int findEntryByLabel(String label) {
        for(int i = 0; i < content.size(); i++) {
            if(content.get(i).text.equals(label))
                return i;
        }

        return -1;
    }

    public int findEntryByUserdata(Object o) {
        if(o == null) {
            for(int i = 0; i < content.size(); i++) {
                if(content.get(i).userdata == null)
                    return i;
            }
        } else {
            for(int i = 0; i < content.size(); i++) {
                if(content.get(i).userdata != null && content.get(i).userdata.equals(o))
                    return i;
            }
        }

        return -1;
    }

    public void setSelectionColor(int selColor) {
        this.selColor = toARGB(selColor);
    }

    public int getSelectionColor() {
        return selColor;
    }

    public int getElementCount() {
        return content.size();
    }

    public void removeElement(int id) {
        if(selected != -1 && id == content.size() - 1)
            selected = -1;

        content.remove(id);
        updateContent();
    }

    public void removeElementRaw(int id) {
        if(selected != -1 && id == content.size() - 1)
            selected = -1;

        content.remove(id);
    }

    public void clear() {
        content.clear();
        scrollPos = 0;
        scrolling = false;
        scrollSize = height - 2;
        selected = -1;
    }

    public void clearRaw() {
        content.clear();
        scrollPos = 0;
        scrolling = false;
        selected = -1;
    }

    @Override
    public void load(JsonOWrapper json) {
        super.load(json);
        width = json.getInt("width", 100);
        height = json.getInt("height", 100);
        selColor = toARGB(json.getColor("selectionColor", 0xFF0080FF));
        updateContent();
    }

}
