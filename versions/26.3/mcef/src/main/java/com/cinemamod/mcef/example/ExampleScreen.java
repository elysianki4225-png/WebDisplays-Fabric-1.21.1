package com.cinemamod.mcef.example;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

public class ExampleScreen extends Screen {
    private static final int BROWSER_DRAW_OFFSET = 20;

    private MCEFBrowser browser;

    protected ExampleScreen(Component component) {
        super(component);
    }

    @Override
    protected void init() {
        super.init();
        if (browser == null) {
            String url = "https://www.google.com";
            boolean transparent = true;
            browser = MCEF.createBrowser(url, transparent);
            resizeBrowser();
        }
    }

    private int mouseX(double x) {
        return (int) ((x - BROWSER_DRAW_OFFSET) * minecraft.getWindow().getGuiScale());
    }

    private int mouseY(double y) {
        return (int) ((y - BROWSER_DRAW_OFFSET) * minecraft.getWindow().getGuiScale());
    }

    private int scaleX(double x) {
        return (int) ((x - BROWSER_DRAW_OFFSET * 2) * minecraft.getWindow().getGuiScale());
    }

    private int scaleY(double y) {
        return (int) ((y - BROWSER_DRAW_OFFSET * 2) * minecraft.getWindow().getGuiScale());
    }

    private void resizeBrowser() {
        if (width > 100 && height > 100) {
            browser.resize(scaleX(width), scaleY(height));
        }
    }

    @Override
    public void resize(int w, int h) {
        super.resize(w, h);
        resizeBrowser();
    }

    @Override
    public void onClose() {
        browser.close();
        super.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
        super.extractRenderState(guiGraphics, i, j, f);
        if (browser != null && browser.getRenderer().isReady()) {
            int drawW = width - BROWSER_DRAW_OFFSET * 2;
            int drawH = height - BROWSER_DRAW_OFFSET * 2;
            if (drawW > 0 && drawH > 0) {
                guiGraphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        browser.getRenderer().getTextureIdentifier(),
                        BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET,
                        0.0f, 0.0f,
                        drawW, drawH,
                        drawW, drawH
                );
            }
        }
    }

    private static int toCefButton(int mcButton) {
        if (mcButton == InputConstants.MOUSE_BUTTON_LEFT) return 0;
        if (mcButton == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
        if (mcButton == InputConstants.MOUSE_BUTTON_MIDDLE) return 2;
        return mcButton;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        browser.sendMousePress(mouseX(event.x()), mouseY(event.y()), toCefButton(event.button()));
        browser.setFocus(true);
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        browser.sendMouseRelease(mouseX(event.x()), mouseY(event.y()), toCefButton(event.button()));
        browser.setFocus(true);
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        browser.sendMouseMove(mouseX(mouseX), mouseY(mouseY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        browser.sendMouseWheel(mouseX(mouseX), mouseY(mouseY), scrollY, 0);
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        browser.sendKeyPress(event.key(), event.key(), event.modifiers());
        browser.setFocus(true);
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        browser.sendKeyRelease(event.key(), event.key(), event.modifiers());
        browser.setFocus(true);
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (cp == 0) return false;
        browser.sendKeyTyped((char) cp, 0);
        browser.setFocus(true);
        return super.charTyped(event);
    }
}
