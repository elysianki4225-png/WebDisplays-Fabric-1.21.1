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
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.config.CommonConfig;
import net.montoyo.wd.item.ItemMinePad2;
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

	private int navY, navH;
	private int backX, backW;
	private int urlX, urlW;
	private int reloadX, reloadW;

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
		trueWidth = width;
		trueHeight = height;

		if (pad != null && pad.mobile) {
			double phoneRatio = 9.0 / 16.0;
			double maxH = Math.max(120.0, height - 56.0);
			double maxW = Math.max(80.0, width - 48.0);
			vh = Math.min(maxH, maxW / phoneRatio);
			vw = vh * phoneRatio;
			vx = (width - vw) / 2.0;
			vy = (height - vh) / 2.0 - 4.0;
		} else {
			vw = ((double) width) - 32.0f;
			vh = vw / WebDisplays.PAD_RATIO;
			if (vh > height - 40.0) {
				vh = Math.max(80.0, height - 40.0);
				vw = vh * WebDisplays.PAD_RATIO;
			}
			vx = (width - vw) / 2.0;
			vy = (height - vh) / 2.0;
		}

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

		if (pad != null && pad.mobile) {
			int padSide = 9;
			int padTop = 14;
			int padBottom = 24;

			int frameX = bx - padSide;
			int frameY = by - padTop;
			int frameW = bw + padSide * 2;
			int frameH = bh + padTop + padBottom;

			// Side buttons
			graphics.fill(frameX - 2, frameY + 28, frameX, frameY + 44, 0xFF64748B);
			graphics.fill(frameX - 2, frameY + 48, frameX, frameY + 64, 0xFF64748B);
			graphics.fill(frameX + frameW, frameY + 34, frameX + frameW + 2, frameY + 52, 0xFF64748B);

			WDTheme.drawDropShadow(graphics, frameX, frameY, frameW, frameH);
			graphics.fill(frameX, frameY, frameX + frameW, frameY + frameH, 0xF01C222D);
			WDTheme.drawBorder(graphics, frameX, frameY, frameW, frameH, 0xFF475569);
			graphics.fill(frameX + 1, frameY + 1, frameX + frameW - 1, frameY + 2, 0x33FFFFFF);

			// Top speaker notch & front camera dot
			int spkW = Math.min(28, Math.max(16, bw / 5));
			int spkX = bx + (bw - spkW) / 2;
			graphics.fill(spkX, frameY + 5, spkX + spkW, frameY + 7, 0xFF0E1218);
			graphics.fill(spkX + spkW + 5, frameY + 5, spkX + spkW + 8, frameY + 8, 0xFF0A0D12);
			graphics.fill(spkX + spkW + 6, frameY + 6, spkX + spkW + 7, frameY + 7, 0xFF38BDF8);

			// Inner screen recess
			graphics.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF070A0F);

			if (pad.view instanceof MCEFBrowser browser && browser.getRenderer().isReady()) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, browser.getRenderer().getTextureIdentifier(), bx, by, 0.0f, 0.0f, bw, bh, bw, bh);
			}

			// Bottom smartphone nav bar
			navY = by + bh + 5;
			navH = 14;
			int btnGap = 4;
			int totalBtnW = Math.max(60, bw - 6);
			int singleW = (totalBtnW - btnGap * 2) / 3;
			int startX = bx + (bw - (singleW * 3 + btnGap * 2)) / 2;

			backX = startX;
			backW = singleW;
			urlX = startX + singleW + btnGap;
			urlW = singleW;
			reloadX = startX + (singleW + btnGap) * 2;
			reloadW = singleW;

			drawPhoneNavButton(graphics, backX, navY, backW, navH, "< " + I18n.get("webdisplays.smartphone.back"), mouseX, mouseY);
			drawPhoneNavButton(graphics, urlX, navY, urlW, navH, I18n.get("webdisplays.smartphone.url"), mouseX, mouseY);
			drawPhoneNavButton(graphics, reloadX, navY, reloadW, navH, I18n.get("webdisplays.smartphone.reload"), mouseX, mouseY);
		} else {
			int frameX = bx - 12;
			int frameY = by - 14;
			int frameW = bw + 24;
			int frameH = bh + 30;

			WDTheme.drawDropShadow(graphics, frameX, frameY, frameW, frameH);
			graphics.fill(frameX, frameY, frameX + frameW, frameY + frameH, 0xEC181E29);
			WDTheme.drawBorder(graphics, frameX, frameY, frameW, frameH, 0xFF3C485E);
			graphics.fill(frameX + 1, frameY + 1, frameX + frameW - 1, frameY + 2, 0x28FFFFFF);

			int camX = bx + bw / 2 - 2;
			int camY = frameY + 5;
			graphics.fill(camX, camY, camX + 4, camY + 4, 0xFF0A0D12);
			graphics.fill(camX + 1, camY + 1, camX + 3, camY + 3, 0xFF1F2937);
			graphics.fill(camX + 1, camY + 1, camX + 2, camY + 2, 0xFF38BDF8);

			graphics.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF070A0F);

			if (pad != null && pad.view instanceof MCEFBrowser browser && browser.getRenderer().isReady()) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, browser.getRenderer().getTextureIdentifier(), bx, by, 0.0f, 0.0f, bw, bh, bw, bh);
			}

			int pillW = Math.min(44, Math.max(24, bw / 8));
			int pillX = bx + (bw - pillW) / 2;
			int pillY = by + bh + 7;
			graphics.fill(pillX, pillY, pillX + pillW, pillY + 2, 0x889AA7BC);
		}
	}

	private void drawPhoneNavButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String label, int mouseX, int mouseY) {
		boolean hover = inRect(mouseX, mouseY, x, y, w, h);
		int bg = hover ? 0xFF334155 : 0xFF1E2430;
		int border = hover ? 0xFF38BDF8 : 0xFF475569;
		graphics.fill(x, y, x + w, y + h, border);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
		int textColor = hover ? 0xFFE0F2FE : 0xFFCBD5E1;
		int tw = font.width(label);
		graphics.text(font, label, x + (w - tw) / 2, y + (h - 8) / 2, textColor, false);
	}

	private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void openUrlGuiFromPhone() {
		if (minecraft == null || minecraft.player == null)
			return;

		ItemStack target = ItemStack.EMPTY;
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = minecraft.player.getItemInHand(hand);
			if (stack.getItem() instanceof ItemMinePad2 padItem && padItem.isMobileLayout()) {
				target = stack;
				break;
			}
		}

		if (!target.isEmpty()) {
			String curUrl = pad != null && pad.view != null ? pad.view.getURL() : CommonConfig.Browser.homepage;
			if (curUrl == null || curUrl.isEmpty())
				curUrl = CommonConfig.Browser.homepage;
			minecraft.gui.setScreen(new GuiSetURL2(target, curUrl));
		}
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
		if (event.button() == 0 && pad != null && pad.mobile) {
			if (inRect(event.x(), event.y(), backX, navY, backW, navH)) {
				if (pad.view != null && pad.view.canGoBack())
					pad.view.goBack();
				return true;
			}
			if (inRect(event.x(), event.y(), urlX, navY, urlW, navH)) {
				openUrlGuiFromPhone();
				return true;
			}
			if (inRect(event.x(), event.y(), reloadX, navY, reloadW, navH)) {
				if (pad.view != null)
					pad.view.reload();
				return true;
			}
		}

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
			if (mx >= 0 && mx <= 1 && my >= 0 && my <= 1) {
				int sx = (int) (mx * pad.resX);
				int sy = (int) (my * pad.resY);
				Minecraft mc = Minecraft.getInstance();
				browser.sendMouseWheel(sx, sy, amount, (mc.hasControlDown() && !mc.hasAltDown() && !mc.hasShiftDown()) ? InputConstants.MOD_CONTROL : 0);
			}
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
	}

	public void mouse(int btn, boolean pressed, int sx, int sy, double scrollAmount) {
		if (pad == null || pad.view == null)
			return;

		double mx = (sx - vx) / vw;
		double my = (sy - vy) / vh;

		if (mx >= 0 && mx <= 1 && my >= 0 && my <= 1) {
			sx = (int) (mx * pad.resX);
			sy = (int) (my * pad.resY);

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
