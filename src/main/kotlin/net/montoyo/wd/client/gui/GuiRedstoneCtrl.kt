/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.montoyo.wd.client.gui.controls.Button
import net.montoyo.wd.client.gui.controls.TextField
import net.montoyo.wd.client.gui.loading.FillControl
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Vector3i

class GuiRedstoneCtrl(
    component: Component,
    private val dimension: ResourceLocation,
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

    override fun init() {
        super.init()
        loadFrom(ResourceLocation.fromNamespaceAndPath("webdisplays", "gui/redstonectrl.json"))
        tfRisingEdge.setText(risingEdgeURL)
        tfFallingEdge.setText(fallingEdgeURL)
    }

    override fun isForBlock(bp: BlockPos, side: BlockSide): Boolean = pos.equalsBlockPos(bp)

    override fun getWikiPageName(): String = "Redstone_Controller"
}
