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
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.client.gui.controls.WDEditBox;
import net.montoyo.wd.config.CommonConfig;
import net.montoyo.wd.entity.TileEntityScreen;
import net.montoyo.wd.item.ItemMinePad2;
import net.montoyo.wd.net.WDNetworkRegistry;
import net.montoyo.wd.net.server_bound.C2SMessageMinepadUrl;
import net.montoyo.wd.utilities.BlockSide;
import net.montoyo.wd.utilities.NbtCompat;
import net.montoyo.wd.utilities.Util;
import org.cef.misc.CefCursorType;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLScancode;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static net.fabricmc.api.EnvType.CLIENT;

@Environment(CLIENT)
public class GuiMinePad extends WDScreen {

	private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm");

	private static final int BEZEL_SIDE = 6;
	private static final int BEZEL_TOP = 6;
	private static final int BEZEL_BOTTOM = 6;
	private static final int STATUS_H = 14;
	private static final int ADDR_H = 19;
	private static final int NAV_H = 20;
	private static final int HOME_IND_H = 7;

	private ClientProxy.PadData pad;
	private double vx;
	private double vy;
	private double vw;
	private double vh;

	private int screenX, screenY, screenW, screenH;
	private int addrX, addrY, addrW, addrH;
	private int clearX, clearW, goX, goW;
	private int navY, navH;
	private final int[] navBtnX = new int[5];
	private final int[] navBtnW = new int[5];

	private WDEditBox urlBox;
	private String lastSyncedUrl = "";

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
			int chromeH = BEZEL_TOP + STATUS_H + ADDR_H + NAV_H + HOME_IND_H + BEZEL_BOTTOM;
			double maxH = Math.max(100.0, trueHeight - chromeH - 16.0);
			double maxW = Math.max(80.0, trueWidth - 36.0);
			vh = Math.min(maxH, maxW / phoneRatio);
			vw = vh * phoneRatio;

			int totalScreenH = STATUS_H + ADDR_H + (int) vh + NAV_H + HOME_IND_H;
			int phoneH = BEZEL_TOP + totalScreenH + BEZEL_BOTTOM;
			int phoneTop = (trueHeight - phoneH) / 2;

			vx = (trueWidth - vw) / 2.0;
			vy = phoneTop + BEZEL_TOP + STATUS_H + ADDR_H;

			screenX = (int) vx;
			screenY = phoneTop + BEZEL_TOP;
			screenW = (int) vw;
			screenH = totalScreenH;

			addrX = screenX + 4;
			addrY = screenY + STATUS_H + 2;
			addrW = screenW - 8;
			addrH = ADDR_H - 4;

			goW = 14;
			clearW = 12;
			goX = addrX + addrW - goW - 1;
			clearX = goX - clearW;

			int boxX = addrX + 13;
			int boxW = Math.max(30, clearX - boxX - 2);
			urlBox = new WDEditBox(font, boxX, addrY + 3, boxW, 11, Component.nullToEmpty(""));
			urlBox.setBordered(false);
			urlBox.setMaxLength(65535);
			urlBox.setTextColor(0xFFF8FAFC);
			urlBox.setTextColorUneditable(0xFF94A3B8);
			lastSyncedUrl = getCurrentUrl();
			urlBox.setValue(lastSyncedUrl);
			urlBox.setFocused(false);

			navY = (int) (vy + vh);
			navH = NAV_H;
			for (int i = 0; i < 5; i++) {
				int x0 = screenX + (i * screenW) / 5;
				int x1 = screenX + ((i + 1) * screenW) / 5;
				navBtnX[i] = x0;
				navBtnW[i] = Math.max(1, x1 - x0);
			}
		} else {
			vw = ((double) width) - 32.0f;
			vh = vw / WebDisplays.PAD_RATIO;
			if (vh > height - 40.0) {
				vh = Math.max(80.0, height - 40.0);
				vw = vh * WebDisplays.PAD_RATIO;
			}
			vx = (width - vw) / 2.0;
			vy = (height - vh) / 2.0;
			urlBox = null;
		}

		super.init();

		if (pad != null && pad.view instanceof MCEFBrowser browser) {
			browser.setCursor(CefCursorType.fromId(pad.activeCursor));
			browser.setCursorChangeListener((id) -> {
				pad.activeCursor = id;
				browser.setCursor(CefCursorType.fromId(id));
			});
		}
	}

	private String getCurrentUrl() {
		if (pad != null && pad.view != null) {
			String u = pad.view.getURL();
			if (u != null && !u.isEmpty())
				return u;
		}
		return CommonConfig.Browser.homepage;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
		int bx = (int) vx;
		int by = (int) vy;
		int bw = (int) vw;
		int bh = (int) vh;

		if (pad != null && pad.mobile) {
			int fx = screenX - BEZEL_SIDE;
			int fy = screenY - BEZEL_TOP;
			int fw = screenW + BEZEL_SIDE * 2;
			int fh = screenH + BEZEL_TOP + BEZEL_BOTTOM;

			// Side physical buttons (left: silent switch + vol up + vol down; right: power button)
			graphics.fill(fx - 2, fy + 26, fx, fy + 34, 0xFF64748B);
			graphics.fill(fx - 2, fy + 40, fx, fy + 56, 0xFF64748B);
			graphics.fill(fx - 2, fy + 60, fx, fy + 76, 0xFF64748B);
			graphics.fill(fx + fw, fy + 44, fx + fw + 2, fy + 64, 0xFF64748B);

			WDTheme.drawDropShadow(graphics, fx, fy, fw, fh);
			graphics.fill(fx, fy, fx + fw, fy + fh, 0xFF2B3240);
			WDTheme.drawBorder(graphics, fx, fy, fw, fh, 0xFF475266);
			graphics.fill(fx + 1, fy + 1, fx + fw - 1, fy + 2, 0x33FFFFFF);

			// Inner OLED glass screen surface
			graphics.fill(screenX - 1, screenY - 1, screenX + screenW + 1, screenY + screenH + 1, 0xFF06090E);
			graphics.fill(screenX, screenY, screenX + screenW, screenY + screenH, 0xFF0F172A);

			// 1. Status bar (Clock, Dynamic Island, Signal & Battery)
			String timeStr = LocalTime.now().format(CLOCK_FMT);
			int clockX = screenX + 4;
			graphics.text(font, timeStr, clockX, screenY + 3, 0xFFE2E8F0, false);

			int batX = screenX + screenW - 16;
			int batY = screenY + 4;
			graphics.fill(batX, batY, batX + 11, batY + 6, 0xFF94A3B8);
			graphics.fill(batX + 1, batY + 1, batX + 10, batY + 5, 0xFF0F172A);
			graphics.fill(batX + 2, batY + 2, batX + 9, batY + 4, 0xFF22C55E);
			graphics.fill(batX + 11, batY + 2, batX + 12, batY + 4, 0xFF94A3B8);

			int sigX = batX - 11;
			graphics.fill(sigX, batY + 4, sigX + 2, batY + 6, 0xFFE2E8F0);
			graphics.fill(sigX + 3, batY + 3, sigX + 5, batY + 6, 0xFFE2E8F0);
			graphics.fill(sigX + 6, batY + 1, sigX + 8, batY + 6, 0xFFE2E8F0);

			int leftLimit = clockX + font.width(timeStr) + 4;
			int rightLimit = sigX - 4;
			int availIslandW = rightLimit - leftLimit;
			int islandW = Math.min(36, Math.min(availIslandW, Math.max(16, screenW / 5)));
			int islandY = screenY + 3;
			if (islandW >= 14) {
				int idealCenterX = screenX + (screenW - islandW) / 2;
				int islandX = Math.max(leftLimit, Math.min(rightLimit - islandW, idealCenterX));
				graphics.fill(islandX, islandY, islandX + islandW, islandY + 8, 0xFF05070B);
				graphics.fill(islandX + 1, islandY - 1, islandX + islandW - 1, islandY + 9, 0xFF05070B);
				int camX = islandX + islandW - 7;
				graphics.fill(camX, islandY + 2, camX + 4, islandY + 6, 0xFF0F172A);
				graphics.fill(camX + 1, islandY + 3, camX + 3, islandY + 5, 0xFF1D4ED8);
				graphics.fill(camX + 1, islandY + 3, camX + 2, islandY + 4, 0xFF60A5FA);
			}

			// 2. Top mobile browser address bar
			boolean addrFocus = urlBox != null && urlBox.isFocused();
			boolean addrHover = inRect(mouseX, mouseY, addrX, addrY, addrW, addrH);
			int addrBorder = addrFocus ? 0xFF38BDF8 : (addrHover ? 0xFF475569 : 0xFF293548);
			int addrBg = addrFocus ? 0xFF162032 : 0xFF1E293B;
			graphics.fill(addrX, addrY, addrX + addrW, addrY + addrH, addrBorder);
			graphics.fill(addrX + 1, addrY + 1, addrX + addrW - 1, addrY + addrH - 1, addrBg);

			String curUrl = addrFocus && urlBox != null ? urlBox.getValue() : getCurrentUrl();
			boolean isHttps = curUrl != null && curUrl.startsWith("https://");
			int iconColor = isHttps ? 0xFF22C55E : 0xFF60A5FA;
			graphics.fill(addrX + 5, addrY + 6, addrX + 10, addrY + 10, iconColor);
			graphics.fill(addrX + 6, addrY + 4, addrX + 9, addrY + 6, iconColor);
			graphics.fill(addrX + 7, addrY + 5, addrX + 8, addrY + 6, addrBg);

			if (addrFocus && urlBox != null) {
				urlBox.extractRenderState(graphics, mouseX, mouseY, ptt);

				boolean clrHover = inRect(mouseX, mouseY, clearX, addrY, clearW, addrH);
				graphics.text(font, "x", clearX + 3, addrY + 3, clrHover ? 0xFFF87171 : 0xFF94A3B8, false);

				boolean goHover = inRect(mouseX, mouseY, goX, addrY, goW, addrH);
				graphics.fill(goX, addrY + 2, goX + goW - 1, addrY + addrH - 2, goHover ? 0xFF0284C7 : 0xFF2563EB);
				graphics.text(font, ">", goX + 4, addrY + 3, 0xFFFFFFFF, false);
			} else {
				int maxLabelW = addrW - 22;
				String display = curUrl == null ? "" : curUrl;
				if (display.isEmpty()) {
					display = I18n.get("webdisplays.smartphone.url_hint");
					graphics.text(font, font.plainSubstrByWidth(display, maxLabelW), addrX + 14, addrY + 4, 0xFF64748B, false);
				} else {
					if (font.width(display) > maxLabelW) {
						display = font.plainSubstrByWidth(display, Math.max(8, maxLabelW - font.width("..."))) + "...";
					}
					graphics.text(font, display, addrX + 14, addrY + 4, 0xFFE2E8F0, false);
				}
			}

			// 3. Browser viewport
			graphics.fill(screenX, by - 1, screenX + screenW, by, 0xFF1E293B);
			if (pad.view instanceof MCEFBrowser browser && browser.getRenderer().isReady()) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, browser.getRenderer().getTextureIdentifier(), bx, by, 0.0f, 0.0f, bw, bh, bw, bh);
			}

			// 4. Bottom mobile browser navigation bar
			graphics.fill(screenX, navY, screenX + screenW, navY + navH, 0xFF0B1120);
			graphics.fill(screenX, navY, screenX + screenW, navY + 1, 0xFF1E293B);

			boolean canBack = pad.view != null && pad.view.canGoBack();
			boolean canFwd = pad.view != null && pad.view.canGoForward();

			for (int i = 0; i < 5; i++) {
				int nbx = navBtnX[i];
				int nbw = navBtnW[i];
				boolean enabled = (i != 0 || canBack) && (i != 1 || canFwd);
				boolean hover = enabled && inRect(mouseX, mouseY, nbx, navY + 2, nbw, navH - 4);
				if (hover) {
					graphics.fill(nbx + 2, navY + 2, nbx + nbw - 2, navY + navH - 2, 0xFF1E293B);
				}
				int col = !enabled ? 0xFF475569 : (hover ? 0xFF38BDF8 : 0xFFCBD5E1);
				drawNavIcon(graphics, i, nbx + nbw / 2, navY + navH / 2, col);
			}

			// 5. Bottom gesture home indicator pill
			int indY = navY + navH;
			graphics.fill(screenX, indY, screenX + screenW, indY + HOME_IND_H, 0xFF0B1120);
			int pillW = Math.max(26, screenW / 3);
			int pillX = screenX + (screenW - pillW) / 2;
			graphics.fill(pillX, indY + 2, pillX + pillW, indY + 4, 0x9994A3B8);

			String hoverTip = getHoveredNavTooltip(mouseX, mouseY);
			if (hoverTip != null) {
				drawTooltip(graphics, Collections.singletonList(hoverTip), mouseX, mouseY);
			}
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

	private void drawNavIcon(GuiGraphicsExtractor g, int idx, int cx, int cy, int col) {
		switch (idx) {
			case 0 -> { // Back chevron <
				g.fill(cx - 3, cy, cx - 1, cy + 1, col);
				g.fill(cx - 2, cy - 1, cx, cy, col);
				g.fill(cx - 1, cy - 2, cx + 1, cy - 1, col);
				g.fill(cx, cy - 3, cx + 2, cy - 2, col);
				g.fill(cx - 2, cy + 1, cx, cy + 2, col);
				g.fill(cx - 1, cy + 2, cx + 1, cy + 3, col);
				g.fill(cx, cy + 3, cx + 2, cy + 4, col);
			}
			case 1 -> { // Forward chevron >
				g.fill(cx + 1, cy, cx + 3, cy + 1, col);
				g.fill(cx, cy - 1, cx + 2, cy, col);
				g.fill(cx - 1, cy - 2, cx + 1, cy - 1, col);
				g.fill(cx - 2, cy - 3, cx, cy - 2, col);
				g.fill(cx, cy + 1, cx + 2, cy + 2, col);
				g.fill(cx - 1, cy + 2, cx + 1, cy + 3, col);
				g.fill(cx - 2, cy + 3, cx, cy + 4, col);
			}
			case 2 -> { // Reload loop
				g.fill(cx - 2, cy - 3, cx + 2, cy - 2, col);
				g.fill(cx - 3, cy - 2, cx - 2, cy + 2, col);
				g.fill(cx - 2, cy + 2, cx + 2, cy + 3, col);
				g.fill(cx + 2, cy + 1, cx + 3, cy + 2, col);
				g.fill(cx + 1, cy - 4, cx + 3, cy - 1, col);
				g.fill(cx + 3, cy - 3, cx + 4, cy - 2, col);
			}
			case 3 -> { // Home house
				g.fill(cx, cy - 4, cx + 1, cy - 3, col);
				g.fill(cx - 1, cy - 3, cx + 2, cy - 2, col);
				g.fill(cx - 2, cy - 2, cx + 3, cy - 1, col);
				g.fill(cx - 3, cy - 1, cx + 4, cy, col);
				g.fill(cx - 2, cy, cx + 3, cy + 3, col);
				g.fill(cx - 1, cy + 1, cx + 1, cy + 3, 0xFF0B1120);
			}
			case 4 -> { // Menu / URL & Power (...)
				g.fill(cx - 4, cy - 1, cx - 2, cy + 1, col);
				g.fill(cx - 1, cy - 1, cx + 1, cy + 1, col);
				g.fill(cx + 2, cy - 1, cx + 4, cy + 1, col);
			}
		}
	}

	private String getHoveredNavTooltip(int mouseX, int mouseY) {
		if (!inRect(mouseX, mouseY, screenX, navY, screenW, navH))
			return null;
		for (int i = 0; i < 5; i++) {
			if (inRect(mouseX, mouseY, navBtnX[i], navY, navBtnW[i], navH)) {
				return switch (i) {
					case 0 -> I18n.get("webdisplays.smartphone.back");
					case 1 -> I18n.get("webdisplays.smartphone.forward");
					case 2 -> I18n.get("webdisplays.smartphone.reload");
					case 3 -> I18n.get("webdisplays.smartphone.home");
					case 4 -> I18n.get("webdisplays.smartphone.menu");
					default -> null;
				};
			}
		}
		return null;
	}

	private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private ItemStack findSmartphoneStack() {
		if (minecraft == null || minecraft.player == null)
			return ItemStack.EMPTY;

		ItemStack fallback = ItemStack.EMPTY;
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = minecraft.player.getItemInHand(hand);
			if (stack.getItem() instanceof ItemMinePad2 padItem && padItem.isMobileLayout()) {
				if (pad != null && pad.id != null) {
					CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
					UUID itemUuid = NbtCompat.getUUID(tag, "PadID");
					if (pad.id.equals(itemUuid))
						return stack;
				}
				if (fallback.isEmpty())
					fallback = stack;
			}
		}
		var inv = minecraft.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.getItem() instanceof ItemMinePad2 padItem && padItem.isMobileLayout()) {
				if (pad != null && pad.id != null) {
					CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
					UUID itemUuid = NbtCompat.getUUID(tag, "PadID");
					if (pad.id.equals(itemUuid))
						return stack;
				}
				if (fallback.isEmpty())
					fallback = stack;
			}
		}
		return fallback;
	}

	private void navigateToUrl(String rawUrl) {
		if (rawUrl == null)
			return;
		String trimmed = rawUrl.trim();
		if (trimmed.isEmpty())
			return;

		try {
			TileEntityScreen.url(trimmed);
		} catch (IOException ignored) {
			return;
		}

		String url = Util.addProtocol(trimmed);
		ItemStack target = findSmartphoneStack();
		if (!target.isEmpty()) {
			CompoundTag tag = target.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
			UUID uuid = NbtCompat.getUUID(tag, "PadID");
			if (uuid == null)
				uuid = pad != null ? pad.id : UUID.randomUUID();
			if (uuid != null) {
				NbtCompat.putUUID(tag, "PadID", uuid);
				tag.putString("PadURL", url);
				tag.putBoolean("PadMobile", true);
				target.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageMinepadUrl(uuid, url));
			}
		}

		if (pad != null && pad.view != null) {
			pad.view.loadURL(WebDisplays.applyBlacklist(url));
		}
		lastSyncedUrl = url;
		if (urlBox != null)
			urlBox.setValue(url);
	}

	private void openUrlGuiFromPhone() {
		ItemStack target = findSmartphoneStack();
		minecraft.gui.setScreen(new GuiSetURL2(target, getCurrentUrl(), pad));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (pad != null && pad.mobile && urlBox != null && urlBox.isFocused()) {
			int key = event.key();
			if (key == InputConstants.KEY_ESCAPE) {
				urlBox.setFocused(false);
				return true;
			}
			if (key == InputConstants.KEY_RETURN || key == SDLScancode.SDL_SCANCODE_RETURN || key == SDLScancode.SDL_SCANCODE_KP_ENTER) {
				navigateToUrl(urlBox.getValue());
				urlBox.setFocused(false);
				return true;
			}
			urlBox.keyPressed(event);
			return true;
		}
		return this.keyChanged(event, true) || super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (pad != null && pad.mobile && urlBox != null && urlBox.isFocused()) {
			urlBox.keyReleased(event);
			return true;
		}
		return this.keyChanged(event, false) || super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (pad != null && pad.mobile && urlBox != null && urlBox.isFocused()) {
			urlBox.charTyped(event);
			return true;
		}
		if (pad != null && pad.view != null) {
			((MCEFBrowser) pad.view).sendKeyTyped((char) event.codepoint(), currentModifiersMask());
			return true;
		} else {
			return super.charTyped(event);
		}
	}

	/* copied from MCEF */
	public boolean keyChanged(KeyEvent event, boolean pressed) {
		assert minecraft != null;
		int keyCode = event.key();
		int scanCode = event.key();
		int modifiers = event.modifiers();

		if (keyCode == InputConstants.KEY_ESCAPE) {
			minecraft.gui.setScreen(null);
			return true;
		}

		InputConstants.Key iuKey = InputConstants.getKey(event);
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

	private static int toCefMouseButton(int mcButton) {
		if (mcButton == InputConstants.MOUSE_BUTTON_LEFT) return 0;
		if (mcButton == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
		if (mcButton == InputConstants.MOUSE_BUTTON_MIDDLE) return 2;
		return mcButton;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && pad != null && pad.mobile) {
			if (inRect(mouseX, mouseY, addrX, addrY, addrW, addrH)) {
				if (urlBox != null) {
					if (urlBox.isFocused()) {
						if (inRect(mouseX, mouseY, clearX, addrY, clearW, addrH)) {
							urlBox.setValue("");
							urlBox.setFocused(true);
							if (pad.view != null)
								pad.view.setFocus(false);
							return true;
						}
						if (inRect(mouseX, mouseY, goX, addrY, goW, addrH)) {
							navigateToUrl(urlBox.getValue());
							urlBox.setFocused(false);
							return true;
						}
					} else {
						urlBox.setValue(getCurrentUrl());
						urlBox.setFocused(true);
					}
					urlBox.mouseClicked(event, doubleClick);
					if (pad.view != null)
						pad.view.setFocus(false);
				}
				return true;
			}

			if (urlBox != null && urlBox.isFocused()) {
				urlBox.setFocused(false);
			}

			if (inRect(mouseX, mouseY, screenX, navY, screenW, navH)) {
				for (int i = 0; i < 5; i++) {
					if (inRect(mouseX, mouseY, navBtnX[i], navY, navBtnW[i], navH)) {
						switch (i) {
							case 0 -> {
								if (pad.view != null && pad.view.canGoBack())
									pad.view.goBack();
							}
							case 1 -> {
								if (pad.view != null && pad.view.canGoForward())
									pad.view.goForward();
							}
							case 2 -> {
								if (pad.view != null)
									pad.view.reload();
							}
							case 3 -> navigateToUrl(CommonConfig.Browser.homepage);
							case 4 -> openUrlGuiFromPhone();
						}
						return true;
					}
				}
			}
		}

		mouse(toCefMouseButton(event.button()), true, (int) mouseX, (int) mouseY, 0);
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pad != null && pad.mobile && urlBox != null && urlBox.isFocused()) {
			urlBox.mouseReleased(event);
		}
		mouse(toCefMouseButton(event.button()), false, (int) event.x(), (int) event.y(), 0);
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
		if (pad == null || pad.view == null) {
			minecraft.gui.setScreen(null);
			return;
		}

		if (pad.mobile && urlBox != null && !urlBox.isFocused()) {
			String cur = getCurrentUrl();
			if (!cur.equals(lastSyncedUrl)) {
				lastSyncedUrl = cur;
				urlBox.setValue(cur);
			}
		}
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
