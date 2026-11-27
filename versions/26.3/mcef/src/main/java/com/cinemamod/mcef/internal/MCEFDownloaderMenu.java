package com.cinemamod.mcef.internal;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MCEFDownloaderMenu extends Screen {
    private final Screen menu;

    public MCEFDownloaderMenu(Screen menu) {
        super(Component.literal("MCEF is downloading required libraries..."));
        this.menu = menu;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;

        int progressBarHeight = 14;
        int progressBarWidth = Math.max(60, width / 3);
        int barLeft = cx - progressBarWidth / 2;
        int barTop = cy - progressBarHeight / 2;

        graphics.fill(barLeft, barTop, barLeft + progressBarWidth, barTop + progressBarHeight, -1);
        graphics.fill(barLeft + 2, barTop + 2, barLeft + progressBarWidth - 2, barTop + progressBarHeight - 2, -16777215);
        int fillW = (int) ((progressBarWidth - 4) * MCEFDownloadListener.INSTANCE.getProgress());
        graphics.fill(barLeft + 4, barTop + 4, barLeft + 4 + Math.max(0, fillW), barTop + progressBarHeight - 4, -1);

        String[] text = new String[]{
                MCEFDownloadListener.INSTANCE.getTask(),
                Math.round(MCEFDownloadListener.INSTANCE.getProgress() * 100) + "%",
        };

        int oSet = ((font.lineHeight / 2) + ((font.lineHeight + 2) * (text.length + 2))) + 4;
        int ty = cy - oSet;
        String header = ChatFormatting.GOLD + title.getString();
        graphics.text(font, header, cx - font.width(title.getString()) / 2, ty, 0xFFFFFFFF);

        for (int i = 0; i < text.length; i++) {
            ty += font.lineHeight + 2;
            if (i == 1) {
                ty += font.lineHeight + 2;
            }
            graphics.text(font, text[i], cx - font.width(text[i]) / 2, ty, 0xFFFFFFFF);
        }
    }

    @Override
    public void tick() {
        if (MCEFDownloadListener.INSTANCE.isDone() || MCEFDownloadListener.INSTANCE.isFailed()) {
            onClose();
            Minecraft.getInstance().gui.setScreen(menu);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
