/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.client.gui.controls.Button;
import net.montoyo.wd.client.gui.controls.TextField;
import net.montoyo.wd.client.gui.loading.FillControl;
import net.montoyo.wd.entity.TileEntityScreen;
import net.montoyo.wd.item.ItemMinePad2;
import net.montoyo.wd.net.WDNetworkRegistry;
import net.montoyo.wd.net.server_bound.C2SMessageMinepadUrl;
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl;
import net.montoyo.wd.utilities.BlockSide;
import net.montoyo.wd.utilities.NbtCompat;
import net.montoyo.wd.utilities.Util;
import net.montoyo.wd.utilities.Vector3i;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class GuiSetURL2 extends WDScreen {

	private TileEntityScreen tileEntity;
	private BlockSide screenSide;
	private Vector3i remoteLocation;

	private ItemStack stack;
	private final boolean isPad;
	private final ClientProxy.PadData returnPad;

	private final String screenURL;
	private int[] panelBounds;

	@FillControl
	private TextField tfURL;

	@FillControl
	private Button btnShutDown;

	@FillControl
	private Button btnCancel;

	@FillControl
	private Button btnOk;

	public GuiSetURL2(TileEntityScreen tes, BlockSide side, String url, Vector3i rl) {
		super(Component.nullToEmpty(null));
		tileEntity = tes;
		screenSide = side;
		remoteLocation = rl;
		isPad = false;
		stack = null;
		returnPad = null;
		screenURL = url;
	}

	public GuiSetURL2(ItemStack is, String url) {
		this(is, url, null);
	}

	public GuiSetURL2(ItemStack is, String url, ClientProxy.PadData returnPad) {
		super(Component.nullToEmpty(null));
		isPad = true;
		stack = is;
		this.returnPad = returnPad;
		screenURL = url;
	}

	@Override
	public void init() {
		super.init();
		loadFrom(Identifier.fromNamespaceAndPath("webdisplays", "gui/seturl.json"));
		panelBounds = centerInPanel(12, 32, 10);
		tfURL.setText(screenURL == null ? "" : screenURL);
		tfURL.focus();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
		if (panelBounds != null) {
			boolean isPhone = isMobilePhone();
			String titleKey = isPad
					? (isPhone ? "webdisplays.gui.seturl.title_phone" : "webdisplays.gui.seturl.title_pad")
					: "webdisplays.gui.seturl.title";
			String title = I18n.get(titleKey);
			WDTheme.drawGlassPanelWithHeader(
					graphics,
					font,
					panelBounds[0], panelBounds[1], panelBounds[2], panelBounds[3],
					title,
					isPad ? (isPhone ? "phone" : "minePad") : null
			);
		}
		super.extractRenderState(graphics, mouseX, mouseY, ptt);
	}

	@Override
	protected void addLoadCustomVariables(Map<String, Double> vars) {
		vars.put("isPad", isPad ? 1.0 : 0.0);
	}

	private boolean isMobilePhone() {
		if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemMinePad2 padItem && padItem.isMobileLayout())
			return true;
		return returnPad != null && returnPad.mobile;
	}

	private void closeToParent() {
		if (returnPad != null && returnPad.view != null)
			minecraft.gui.setScreen(new GuiMinePad(returnPad));
		else
			minecraft.gui.setScreen(null);
	}

	protected UUID getUUID() {
		if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemMinePad2) {
			CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
			UUID uuid = NbtCompat.getUUID(tag, "PadID");
			if (uuid == null) {
				uuid = (returnPad != null && returnPad.id != null) ? returnPad.id : UUID.randomUUID();
				NbtCompat.putUUID(tag, "PadID", uuid);
				stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			}

			return uuid;
		}
		if (returnPad != null && returnPad.id != null)
			return returnPad.id;
		throw new RuntimeException("Get UUID is being called for a non-minepad UI");
	}

	@GuiSubscribe
	public void onButtonClicked(Button.ClickEvent ev) {
		if (ev.getSource() == btnCancel)
			closeToParent();
		else if (ev.getSource() == btnOk)
			validate(tfURL.getText());
		else if (ev.getSource() == btnShutDown) {
			if (isPad) {
				UUID uuid = getUUID();
				WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageMinepadUrl(
						uuid,
						""
				));
				if (stack != null && !stack.isEmpty()) {
					CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
					NbtCompat.removeUUID(tag, "PadID");
					stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				}
				((ClientProxy) WebDisplays.PROXY).closePad(uuid);
			}

			minecraft.gui.setScreen(null);
		}
	}

	@GuiSubscribe
	public void onEnterPressed(TextField.EnterPressedEvent ev) {
		validate(ev.getText());
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (quitOnEscape && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
			closeToParent();
			return true;
		}
		return super.keyPressed(event);
	}

	private void validate(String rawUrl) {
		String url = rawUrl == null ? "" : rawUrl.trim();
		if (!url.isEmpty()) {
			try {
				TileEntityScreen.url(url);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			url = Util.addProtocol(url);

			if (isPad) {
				UUID uuid = getUUID();
				boolean mobile = isMobilePhone();
				WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageMinepadUrl(uuid, url));
				if (stack != null && !stack.isEmpty()) {
					CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
					NbtCompat.putUUID(tag, "PadID", uuid);
					tag.putString("PadURL", url);
					if (mobile)
						tag.putBoolean("PadMobile", true);
					stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				}

				ClientProxy proxy = (ClientProxy) WebDisplays.PROXY;
				ClientProxy.PadData pd = returnPad != null ? returnPad : proxy.getOrCreatePad(uuid, url, mobile);

				if (pd != null && pd.view != null) {
					pd.view.loadURL(WebDisplays.applyBlacklist(url));
				}
				if ((returnPad != null || mobile) && pd != null && pd.view != null) {
					minecraft.gui.setScreen(new GuiMinePad(pd));
					return;
				}
			} else
				WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.setURL(tileEntity, screenSide, url, remoteLocation));
		}

		closeToParent();
	}

	@Override
	public boolean isForBlock(BlockPos bp, BlockSide side) {
		return (remoteLocation != null && remoteLocation.equalsBlockPos(bp))
				|| (tileEntity != null && bp.equals(tileEntity.getBlockPos()) && side == screenSide);
	}

}
