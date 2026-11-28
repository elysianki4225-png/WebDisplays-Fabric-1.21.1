/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.montoyo.wd.client.gui.controls.Button
import net.montoyo.wd.client.gui.controls.TextField
import net.montoyo.wd.client.gui.loading.FillControl
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Vector3i

class GuiRedstoneCtrl(
    component: Component,
    private val dimension: Identifier,
    private val pos: Vector3i,
    private val risingEdgeURL: String,
    private val fallingEdgeURL: String
) : WDScreen(component) {
    @FillControl
    private lateinit var tfRisingEdge: TextField

    @FillControl
    private lateinit var tfFallingEdge: TextField

    @FillControl
    private lateinit var btnOk: Button

    private var panelBounds: IntArray? = null

    override fun init() {
        super.init()
        loadFrom(Identifier.fromNamespaceAndPath("webdisplays", "gui/redstonectrl.json"))
        panelBounds = centerInPanel(12, 32, 10)
        tfRisingEdge.setText(risingEdgeURL)
        tfFallingEdge.setText(fallingEdgeURL)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        val pb = panelBounds
        if (pb != null) {
            WDTheme.drawGlassPanelWithHeader(
                graphics,
                font,
                pb[0], pb[1], pb[2], pb[3],
                I18n.get("block.webdisplays.redctrl"),
                null
            )
        }
        super.extractRenderState(graphics, mouseX, mouseY, ptt)
    }

    override fun isForBlock(bp: BlockPos, side: BlockSide): Boolean = pos.equalsBlockPos(bp)

    override fun getWikiPageName(): String = "Redstone_Controller"
}
