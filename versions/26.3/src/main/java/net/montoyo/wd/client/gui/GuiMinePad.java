/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.utilities.BlockSide;
import org.cef.misc.CefCursorType;
import org.lwjgl.sdl.SDLKeyboard;

import java.util.Optional;

import static net.fabricmc.api.EnvType.CLIENT;

@Environment(CLIENT)
public class GuiMinePad extends WDScreen {

	private ClientProxy.PadData pad;
	private double vx;
	private double vy;
	private double vw;
	private double vh;

	public GuiMinePad() {
		super(Component.nullToEmpty(null));
	}

	public GuiMinePad(ClientProxy.PadData pad) {
		this();
		this.pad = pad;
	}

	int trueWidth, trueHeight;

	@Override
	public void init() {
		vw = ((double) width) - 32.0f;
		vh = vw / WebDisplays.PAD_RATIO;
		vx = 16.0f;
		vy = (((double) height) - vh) / 2.0f;

		trueWidth = width;
		trueHeight = height;

		this.width = (int) vw;
		this.height = (int) vh;

		super.init();

		if (pad != null && pad.view instanceof MCEFBrowser browser) {
			browser.setCursor(CefCursorType.fromId(pad.activeCursor));
			browser.setCursorChangeListener((id) -> {
				pad.activeCursor = id;
				browser.setCursor(CefCursorType.fromId(id));
			});
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
		width = trueWidth;
		height = trueHeight;
		super.extractBackground(graphics, mouseX, mouseY, ptt);
		width = (int) vw;
		height = (int) vh;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
		int bx = (int) vx;
		int by = (int) vy;
		int bw = (int) vw;
		int bh = (int) vh;

		int frameX = bx - 12;
		int frameY = by - 14;
		int frameW = bw + 24;
		int frameH = bh + 30;

		WDTheme.drawDropShadow(graphics, frameX, frameY, frameW, frameH);
		graphics.fill(frameX, frameY, frameX + frameW, frameY + frameH, 0xEC181E29);
		WDTheme.drawBorder(graphics, frameX, frameY, frameW, frameH, 0xFF3C485E);
		graphics.fill(frameX + 1, frameY + 1, frameX + frameW - 1, frameY + 2, 0x28FFFFFF);

		// Top bezel camera dot
		int camX = bx + bw / 2 - 2;
		int camY = frameY + 5;
		graphics.fill(camX, camY, camX + 4, camY + 4, 0xFF0A0D12);
		graphics.fill(camX + 1, camY + 1, camX + 3, camY + 3, 0xFF1F2937);
		graphics.fill(camX + 1, camY + 1, camX + 2, camY + 2, 0xFF38BDF8);

		// Inner screen recess
		graphics.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF070A0F);

		if (pad != null && pad.view instanceof MCEFBrowser browser && browser.getRenderer().isReady()) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, browser.getRenderer().getTextureIdentifier(), bx, by, 0.0f, 0.0f, bw, bh, bw, bh);
		}

		// Bottom home indicator pill
		int pillW = Math.min(44, Math.max(24, bw / 8));
		int pillX = bx + (bw - pillW) / 2;
		int pillY = by + bh + 7;
		graphics.fill(pillX, pillY, pillX + pillW, pillY + 2, 0x889AA7BC);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return this.keyChanged(event.key(), event.key(), event.modifiers(), true) || super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		return this.keyChanged(event.key(), event.key(), event.modifiers(), false) || super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (pad != null && pad.view != null) {
			((MCEFBrowser) pad.view).sendKeyTyped((char) event.codepoint(), currentModifiersMask());
			return true;
		} else {
			return super.charTyped(event);
		}
	}

	/* copied from MCEF */
	public boolean keyChanged(int keyCode, int scanCode, int modifiers, boolean pressed) {
		assert minecraft != null;
		if (keyCode == InputConstants.KEY_ESCAPE) {
			minecraft.gui.setScreen(null);
			return true;
		}

		InputConstants.Key iuKey = InputConstants.getKey(new KeyEvent(keyCode, scanCode, modifiers));
		String keystr = iuKey.getDisplayName().getString();
		if (keystr.length() == 0)
			return false;

		char key = keystr.charAt(keystr.length() - 1);

		if (keystr.equals("Enter")) {
			keyCode = 10;
			key = '\n';
		}

		if (pad != null && pad.view != null) {
			if (pressed)
				((MCEFBrowser) pad.view).sendKeyPress(keyCode, scanCode, modifiers);
			else
				((MCEFBrowser) pad.view).sendKeyRelease(keyCode, scanCode, modifiers);

			if (pressed && key == '\n')
				if (modifiers != 0) ((MCEFBrowser) pad.view).sendKeyTyped('\r', modifiers);
			return true;
		}

		return false;
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		super.mouseMoved(mouseX, mouseY);
		mouse(-1, false, (int) mouseX, (int) mouseY, 0);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		mouse(event.button(), true, (int) event.x(), (int) event.y(), 0);
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		mouse(event.button(), false, (int) event.x(), (int) event.y(), 0);
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
		if (pad != null && pad.view instanceof MCEFBrowser browser) {
			double mx = (mouseX - vx) / vw;
			double my = (mouseY - vy) / vh;
			int sx = (int) (mx * WebDisplays.INSTANCE.padResX);
			int sy = (int) (my * WebDisplays.INSTANCE.padResY);
			Minecraft mc = Minecraft.getInstance();
			browser.sendMouseWheel(sx, sy, amount, (mc.hasControlDown() && !mc.hasAltDown() && !mc.hasShiftDown()) ? InputConstants.MOD_CONTROL : 0);
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
	}

	public void mouse(int btn, boolean pressed, int sx, int sy, double scrollAmount) {
		double mx = (sx - vx) / vw;
		double my = (sy - vy) / vh;

		if (pad != null && pad.view != null && mx >= 0 && mx <= 1) {
			sx = (int) (mx * WebDisplays.INSTANCE.padResX);
			sy = (int) (my * WebDisplays.INSTANCE.padResY);

			if (btn == -1)
				((MCEFBrowser) pad.view).sendMouseMove(sx, sy);
			else if (pressed)
				((MCEFBrowser) pad.view).sendMousePress(sx, sy, btn);
			else
				((MCEFBrowser) pad.view).sendMouseRelease(sx, sy, btn);
			pad.view.setFocus(true);
		}
	}

	public static Optional<Character> getChar(int keyCode, int scanCode) {
		String keystr = SDLKeyboard.SDL_GetKeyName(SDLKeyboard.SDL_GetKeyFromScancode(keyCode, (short) 0, false));
		if (keystr == null) {
			keystr = "\0";
		}
		if (keyCode == InputConstants.KEY_RETURN) {
			keystr = "\n";
		}
		if (keystr.length() == 0) {
			return Optional.empty();
		}

		return Optional.of(keystr.charAt(keystr.length() - 1));
	}

	@Override
	public void tick() {
		if (pad == null || pad.view == null)
			minecraft.gui.setScreen(null);
	}

	@Override
	public boolean isForBlock(BlockPos bp, BlockSide side) {
		return false;
	}

	@Override
	public void removed() {
		super.removed();
		if (pad != null && pad.view instanceof MCEFBrowser browser) {
			browser.setCursor(CefCursorType.POINTER);
			browser.setCursorChangeListener((cursor) -> {
				pad.activeCursor = cursor;
			});
		}
	}

	@Override
	public void onClose() {
		super.onClose();
		removed();
	}
}
