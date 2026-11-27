/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
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
import org.lwjgl.glfw.GLFW;

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
			double maxH = Math.max(120.0, height - 54.0);
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

	private static void addRect(BufferBuilder bb, double x, double y, double w, double h) {
		bb.addVertex((float) x, (float) y, 0.0f).setColor(255, 255, 255, 255);
		bb.addVertex((float) (x + w), (float) y, 0.0f).setColor(255, 255, 255, 255);
		bb.addVertex((float) (x + w), (float) (y + h), 0.0f).setColor(255, 255, 255, 255);
		bb.addVertex((float) x, (float) (y + h), 0.0f).setColor(255, 255, 255, 255);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float ptt) {
		width = trueWidth;
		height = trueHeight;
		renderBackground(graphics, 0, 0, 0.0F);
		width = (int) vw;
		height = (int) vh;

		if (pad != null && pad.mobile) {
			renderMobileFrame(graphics, mouseX, mouseY);
		} else {
			RenderSystem.disableCull();
			RenderSystem.setShader(GameRenderer::getPositionColorShader);
			RenderSystem.setShaderColor(0.73f, 0.73f, 0.73f, 1.0f);

			BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			addRect(bb, vx, vy - 16, vw, 16);
			addRect(bb, vx, vy + vh, vw, 16);
			addRect(bb, vx - 16, vy, 16, vh);
			addRect(bb, vx + vw, vy, 16, vh);
			BufferUploader.drawWithShader(bb.buildOrThrow());
		}

		if (pad != null && pad.view instanceof MCEFBrowser browser) {
			RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
			RenderSystem.enableDepthTest();
			RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
			RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());
			BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			double x1 = vx;
			double y1 = vy;
			double x2 = vx + vw;
			double y2 = vy + vh;
			buffer.addVertex(graphics.pose().last().pose(), (float) x1, (float) y1, 0.0f).setUv(0.0F, 0.0F).setColor(255, 255, 255, 255);
			buffer.addVertex(graphics.pose().last().pose(), (float) x2, (float) y1, 0.0f).setUv(1.0F, 0.0F).setColor(255, 255, 255, 255);
			buffer.addVertex(graphics.pose().last().pose(), (float) x2, (float) y2, 0.0f).setUv(1.0F, 1.0F).setColor(255, 255, 255, 255);
			buffer.addVertex(graphics.pose().last().pose(), (float) x1, (float) y2, 0.0f).setUv(0.0F, 1.0F).setColor(255, 255, 255, 255);
			BufferUploader.drawWithShader(buffer.buildOrThrow());
		}

		RenderSystem.enableCull();
	}

	private void renderMobileFrame(GuiGraphics graphics, int mouseX, int mouseY) {
		int bx = (int) vx;
		int by = (int) vy;
		int bw = (int) vw;
		int bh = (int) vh;

		int padSide = 8;
		int padTop = 14;
		int padBottom = 22;

		int fx = bx - padSide;
		int fy = by - padTop;
		int fw = bw + padSide * 2;
		int fh = bh + padTop + padBottom;

		// Side buttons
		graphics.fill(fx - 2, fy + 28, fx, fy + 44, 0xFF64748B);
		graphics.fill(fx - 2, fy + 48, fx, fy + 64, 0xFF64748B);
		graphics.fill(fx + fw, fy + 34, fx + fw + 2, fy + 52, 0xFF64748B);

		// Outer chassis & bevel
		graphics.fill(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, 0xFF1A1E27);
		graphics.fill(fx, fy, fx + fw, fy + fh, 0xFF2E3440);
		graphics.fill(fx, fy, fx + fw, fy + 1, 0xFF5E6980);
		graphics.fill(fx, fy, fx + 1, fy + fh, 0xFF4C566A);

		// Top speaker + selfie camera
		int spkW = Math.min(28, Math.max(16, bw / 5));
		int spkX = bx + (bw - spkW) / 2;
		graphics.fill(spkX, fy + 5, spkX + spkW, fy + 7, 0xFF161A22);
		graphics.fill(spkX + spkW + 5, fy + 5, spkX + spkW + 8, fy + 8, 0xFF0A0D14);
		graphics.fill(spkX + spkW + 6, fy + 6, spkX + spkW + 7, fy + 7, 0xFF38BDF8);

		// Screen inner border
		graphics.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF0D1117);

		// Bottom smartphone navigation bar: Back, URL, Reload
		navY = by + bh + 4;
		navH = 14;
		int btnGap = 4;
		int totalBtnW = Math.max(60, bw - 8);
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
	}

	private void drawPhoneNavButton(GuiGraphics graphics, int x, int y, int w, int h, String label, int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		int bg = hover ? 0xFF3B4559 : 0xFF232834;
		int border = hover ? 0xFF38BDF8 : 0xFF475569;
		graphics.fill(x, y, x + w, y + h, border);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
		int textColor = hover ? 0xFFE0F2FE : 0xFFCBD5E1;
		int tw = font.width(label);
		graphics.drawString(font, label, x + (w - tw) / 2, y + (h - 8) / 2, textColor, false);
	}

	private boolean inRect(double mx, double my, int x, int y, int w, int h) {
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
			minecraft.setScreen(new GuiSetURL2(target, curUrl));
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return this.keyChanged(keyCode, scanCode, modifiers, true) || super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		return this.keyChanged(keyCode, scanCode, modifiers, false) || super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (pad != null && pad.view != null) {
			((MCEFBrowser) pad.view).sendKeyTyped(codePoint, modifiers);
			return true;
		} else {
			return super.charTyped(codePoint, modifiers);
		}
	}

	/* copied from MCEF */
	public boolean keyChanged(int keyCode, int scanCode, int modifiers, boolean pressed) {
		assert minecraft != null;
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			minecraft.setScreen(null);
			return true;
		}

		InputConstants.Key iuKey = InputConstants.getKey(keyCode, scanCode);
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
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && pad != null && pad.mobile) {
			if (inRect(mouseX, mouseY, backX, navY, backW, navH)) {
				if (pad.view != null && pad.view.canGoBack())
					pad.view.goBack();
				return true;
			}
			if (inRect(mouseX, mouseY, urlX, navY, urlW, navH)) {
				openUrlGuiFromPhone();
				return true;
			}
			if (inRect(mouseX, mouseY, reloadX, navY, reloadW, navH)) {
				if (pad.view != null)
					pad.view.reload();
				return true;
			}
		}

		mouse(button, true, (int) mouseX, (int) mouseY, 0);
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		mouse(button, false, (int) mouseX, (int) mouseY, 0);
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
		if (pad != null && pad.view instanceof MCEFBrowser browser) {
			double mx = (mouseX - vx) / vw;
			double my = (mouseY - vy) / vh;
			if (mx >= 0 && mx <= 1 && my >= 0 && my <= 1) {
				int sx = (int) (mx * pad.resX);
				int sy = (int) (my * pad.resY);
				browser.sendMouseWheel(sx, sy, amount, (hasControlDown() && !hasAltDown() && !hasShiftDown()) ? GLFW.GLFW_MOD_CONTROL : 0);
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
		String keystr = GLFW.glfwGetKeyName(keyCode, scanCode);
		if (keystr == null) {
			keystr = "\0";
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER) {
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
			minecraft.setScreen(null);
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
