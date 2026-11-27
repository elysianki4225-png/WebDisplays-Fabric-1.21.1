/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.client.gui.controls.Button;
import net.montoyo.wd.client.gui.controls.TextField;
import net.montoyo.wd.client.gui.loading.FillControl;
import net.montoyo.wd.net.WDNetworkRegistry;
import net.montoyo.wd.net.server_bound.C2SMessageRedstoneCtrl;
import net.montoyo.wd.utilities.BlockSide;
import net.montoyo.wd.utilities.Util;
import net.montoyo.wd.utilities.Vector3i;

import org.jetbrains.annotations.Nullable;

public class GuiRedstoneCtrl extends WDScreen {

    private Identifier dimension;
    private Vector3i pos;
    private String risingEdgeURL;
    private String fallingEdgeURL;

    @FillControl
    private TextField tfRisingEdge;

    @FillControl
    private TextField tfFallingEdge;

    @FillControl
    private Button btnOk;

    private int[] panelBounds;

    public GuiRedstoneCtrl(Component component, Identifier d, Vector3i p, String r, String f) {
        super(component);
        dimension = d;
        pos = p;
        risingEdgeURL = r;
        fallingEdgeURL = f;
    }

    @Override
    public void init() {
        super.init();
        loadFrom(Identifier.fromNamespaceAndPath("webdisplays", "gui/redstonectrl.json"));
        panelBounds = centerInPanel(12, 32, 10);
        tfRisingEdge.setText(risingEdgeURL);
        tfFallingEdge.setText(fallingEdgeURL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ptt) {
        if (panelBounds != null) {
            WDTheme.drawGlassPanelWithHeader(
                    graphics,
                    font,
                    panelBounds[0], panelBounds[1], panelBounds[2], panelBounds[3],
                    I18n.get("block.webdisplays.redctrl"),
                    null
            );
        }
        super.extractRenderState(graphics, mouseX, mouseY, ptt);
    }

//    @GuiSubscribe
//    public void onClick(Button.ClickEvent ev) {
//        if(ev.getSource() == btnOk) {
//            API mcef = ((ClientProxy) WebDisplays.PROXY).getMCEF();
//
//            String rising = mcef.punycode(Util.addProtocol(tfRisingEdge.getText()));
//            String falling = mcef.punycode(Util.addProtocol(tfFallingEdge.getText()));
//            WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageRedstoneCtrl(pos, rising, falling));
//        }
//
//        minecraft.setScreen(null);
//    }

    @Override
    public boolean isForBlock(BlockPos bp, BlockSide side) {
        return pos.equalsBlockPos(bp);
    }

    @Nullable
    @Override
    public String getWikiPageName() {
        return "Redstone_Controller";
    }

}
