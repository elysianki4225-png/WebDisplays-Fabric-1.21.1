/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.gui.controls.Button;
import net.montoyo.wd.client.gui.controls.Control;
import net.montoyo.wd.client.gui.controls.Label;
import net.montoyo.wd.client.gui.loading.FillControl;
import net.montoyo.wd.entity.TileEntityScreen;
import net.montoyo.wd.net.WDNetworkRegistry;
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl;
import net.montoyo.wd.utilities.BlockSide;
import net.montoyo.wd.utilities.Log;
import net.montoyo.wd.utilities.TypeData;
import com.mojang.blaze3d.platform.InputConstants;
import net.montoyo.wd.utilities.Util;
import org.lwjgl.sdl.SDLMouse;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class GuiKeyboard extends WDScreen {

    private static final String WARNING_FNAME = "wd_keyboard_warning.txt";

    private TileEntityScreen tes;
    private BlockSide side;
    private final ArrayList<TypeData> evStack = new ArrayList<>();
    private BlockPos kbPos;
    private boolean showWarning = true;
    private int[] warningPanelBounds;

    @FillControl
    private Label lblInfo;

    @FillControl
    private Button btnOk;

    public GuiKeyboard() {
        super(Component.nullToEmpty(null));
    }

    public GuiKeyboard(TileEntityScreen tes, BlockSide side, BlockPos kbPos) {
        this();
        this.tes = tes;
        this.side = side;
        this.kbPos = kbPos;
    }

    @Override
    protected void addLoadCustomVariables(Map<String, Double> vars) {
        vars.put("showWarning", showWarning ? 1.0 : 0.0);
    }

    private static final Method viveGetPlayer;
    private static final Method viveSetOverlayShowing;

    static {
        Method setOverlay = null;
        Method getPlayer = null;

        String[][] layouts = {
                {"org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler", "org.vivecraft.client_vr.gameplay.VRPlayer"},
                {"org.vivecraft.gameplay.screenhandlers.KeyboardHandler", "org.vivecraft.gameplay.VRPlayer"},
        };

        for (String[] layout : layouts) {
            try {
                Class<?> handler = Class.forName(layout[0]);
                getPlayer = Class.forName(layout[1]).getMethod("get");
                setOverlay = handler.getMethod("setOverlayShowing", boolean.class);
                break;
            } catch (Throwable ignored) {
                getPlayer = null;
                setOverlay = null;
            }
        }

        if (setOverlay == null && FabricLoader.getInstance().isModLoaded("vivecraft"))
            Log.warning("Vivecraft is loaded but its keyboard overlay methods could not be found.");

        viveGetPlayer = getPlayer;
        viveSetOverlayShowing = setOverlay;
    }

    private static void showVivecraftKeyboard(boolean showing) {
        if (viveSetOverlayShowing == null)
            return;

        try {
            if (viveGetPlayer.invoke(null) == null)
                return;

            viveSetOverlayShowing.invoke(null, showing);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void init() {
        super.init();

        if (minecraft.getSingleplayerServer() != null && !minecraft.getSingleplayerServer().isPublished())
            showWarning = false;
        else
            showWarning = !hasUserReadWarning();

        loadFrom(Identifier.fromNamespaceAndPath("webdisplays", "gui/kb_right.json"));

        if (showWarning) {
            int maxLabelW = 0;
            int totalH = 0;

            for (Control ctrl : controls) {
                if (ctrl != lblInfo && ctrl instanceof Label) {
                    if (ctrl.getWidth() > maxLabelW)
                        maxLabelW = ctrl.getWidth();

                    totalH += ctrl.getHeight() + 2;
                    ctrl.setPos((width - ctrl.getWidth()) / 2, 0);
                }
            }

            int btnW = Math.max(120, maxLabelW / 2);
            btnOk.setWidth(btnW);
            btnOk.setPos((width - btnW) / 2, 0);
            totalH += btnOk.getHeight() + 6;

            int y = (height - totalH) / 2;
            for (Control ctrl : controls) {
                if (ctrl != lblInfo) {
                    if (ctrl == btnOk)
                        y += 6;
                    ctrl.setPos(ctrl.getX(), y);
                    y += ctrl.getHeight() + 2;
                }
            }

            int padX = 16;
            int pw = maxLabelW + padX * 2;
            int ph = totalH + 26;
            warningPanelBounds = new int[] { (width - pw) / 2, (height - ph) / 2 - 4, pw, ph };
        } else {
            warningPanelBounds = null;
            grabMouseKeepingScreen();
        }

        defaultBackground = showWarning;
        syncTicks = 5;

        showVivecraftKeyboard(true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
        if (showWarning && warningPanelBounds != null) {
            WDTheme.drawGlassPanel(graphics, warningPanelBounds[0], warningPanelBounds[1], warningPanelBounds[2], warningPanelBounds[3]);
            graphics.fill(warningPanelBounds[0] + 1, warningPanelBounds[1] + 1, warningPanelBounds[0] + warningPanelBounds[2] - 1, warningPanelBounds[1] + 3, WDTheme.DANGER);
        } else if (!showWarning && lblInfo != null && lblInfo.isVisible()) {
            int pillW = lblInfo.getWidth() + 16;
            int pillH = 18;
            int px = lblInfo.getX() - 8;
            int py = lblInfo.getY() - 5;
            graphics.fill(px, py, px + pillW, py + pillH, 0xCC10151E);
            WDTheme.drawBorder(graphics, px, py, pillW, pillH, 0xFF2E384B);
            graphics.fill(px + 1, py + 4, px + 3, py + pillH - 4, WDTheme.ACCENT);
        }
        super.extractRenderState(graphics, mouseX, mouseY, ptt);
    }

    @Override
    public void onClose() {
        showVivecraftKeyboard(false);
        super.onClose();
    }

    private boolean closeRequested = false;

    @Override
    public void tick() {
        super.tick();

        if (closeRequested && minecraft.gui.screen() == this) {
            closeRequested = false;
            minecraft.gui.setScreen(null);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if(quitOnEscape && event.key() == InputConstants.KEY_ESCAPE)
            closeRequested = true;
        addKey(new TypeData(TypeData.Action.PRESS, event.key(), event.modifiers(), event.key()));
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        addKey(new TypeData(TypeData.Action.TYPE, event.codepoint(), currentModifiersMask(), 0));
        return super.charTyped(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        addKey(new TypeData(TypeData.Action.RELEASE, event.key(), event.modifiers(), event.key()));
        return super.keyReleased(event);
    }

    void addKey(TypeData data) {
        tes.type(side, "[" + WebDisplays.GSON.toJson(data) + "]", kbPos);

        evStack.add(data);
        if (!evStack.isEmpty() && !syncRequested())
            requestSync();
    }

    @Override
    protected void sync() {
        if(!evStack.isEmpty()) {
            WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.type(tes, side, WebDisplays.GSON.toJson(evStack), kbPos));
            evStack.clear();
        }
    }

    @GuiSubscribe
    public void onClick(Button.ClickEvent ev) {
        if(showWarning && ev.getSource() == btnOk) {
            writeUserAcknowledge();

            for(Control ctrl: controls) {
                if(ctrl instanceof Label) {
                    Label lbl = (Label) ctrl;
                    lbl.setVisible(!lbl.isVisible());
                }
            }

            btnOk.setDisabled(true);
            btnOk.setVisible(false);
            showWarning = false;
            defaultBackground = false;
            grabMouseKeepingScreen();
        }
    }

    private void grabMouseKeepingScreen() {
        MouseHandler mouse = minecraft.mouseHandler;

        if (!minecraft.isWindowActive() || mouse.isMouseGrabbed())
            return;

        mouse.mouseGrabbed = true;
        SDLMouse.SDL_SetWindowRelativeMouseMode(minecraft.getWindow().handle(), true);
        mouse.setIgnoreFirstMove();
        minecraft.missTime = 10000;
    }

    private boolean hasUserReadWarning() {
        try {
            File f = new File(FabricLoader.getInstance().getGameDir().toString(), WARNING_FNAME);

            if(f.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String str = br.readLine();
                Util.silentClose(br);

                return str != null && str.trim().equalsIgnoreCase("read");
            }
        } catch(Throwable t) {
            Log.warningEx("Can't know if user has already read the warning", t);
        }

        return false;
    }

    private void writeUserAcknowledge() {
        try {
            File f = new File(FabricLoader.getInstance().getGameDir().toString(), WARNING_FNAME);

            BufferedWriter bw = new BufferedWriter(new FileWriter(f));
            bw.write("read\n");
            Util.silentClose(bw);
        } catch(Throwable t) {
            Log.warningEx("Can't write that the user read the warning", t);
        }
    }

    @Override
    public boolean isForBlock(BlockPos bp, BlockSide side) {
        return bp.equals(kbPos) || (bp.equals(tes.getBlockPos()) && side == this.side);
    }

}
