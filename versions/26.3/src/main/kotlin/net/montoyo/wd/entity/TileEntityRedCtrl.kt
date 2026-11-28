/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.entity

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.data.RedstoneCtrlData
import net.montoyo.wd.init.TileInit
import net.montoyo.wd.utilities.Util
import java.io.IOException

class TileEntityRedCtrl(arg2: BlockPos, arg3: BlockState) :
    TileEntityPeripheralBase(TileInit.REDSTONE_CONTROLLER.get(), arg2, arg3) {

    private var risingEdgeURL: String = ""
    private var fallingEdgeURL: String = ""
    private var state: Boolean = false

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)

        risingEdgeURL = input.getStringOr("RisingEdgeURL", "")
        fallingEdgeURL = input.getStringOr("FallingEdgeURL", "")
        state = input.getBooleanOr("Powered", false)
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        output.putString("RisingEdgeURL", risingEdgeURL)
        output.putString("FallingEdgeURL", fallingEdgeURL)
        output.putBoolean("Powered", state)
    }

    override fun onRightClick(player: Player, hand: InteractionHand): InteractionResult {
        val lvl = level ?: return InteractionResult.SUCCESS
        if (lvl.isClientSide()) {
            return InteractionResult.SUCCESS
        }

        if (!isScreenChunkLoaded()) {
            Util.toast(player, "chunkUnloaded")
            return InteractionResult.SUCCESS
        }

        val tes = getConnectedScreen()
        val side = screenSide
        if (tes == null || side == null) {
            Util.toast(player, "notLinked")
            return InteractionResult.SUCCESS
        }

        val scr = tes.getScreen(side) ?: run {
            Util.toast(player, "notLinked")
            return InteractionResult.SUCCESS
        }

        if ((scr.rightsFor(player) and ScreenRights.CHANGE_URL) == 0) {
            Util.toast(player, "restrictions")
            return InteractionResult.SUCCESS
        }

        RedstoneCtrlData(lvl.dimension().identifier(), blockPos, risingEdgeURL, fallingEdgeURL).sendTo(player as ServerPlayer)
        return InteractionResult.SUCCESS
    }

    override fun onNeighborChange(neighborType: Block, neighborPos: BlockPos) {
        val lvl = level ?: return
        val hasPower = lvl.hasNeighborSignal(blockPos) || lvl.hasNeighborSignal(blockPos.above()) //Same as dispenser

        if (hasPower != state) {
            state = hasPower

            if (state) { //Rising edge
                changeURL(risingEdgeURL)
            } else { //Falling edge
                changeURL(fallingEdgeURL)
            }
        }
    }

    fun setURLs(r: String, f: String) {
        risingEdgeURL = r.trim()
        fallingEdgeURL = f.trim()
        setChanged()
    }

    private fun changeURL(url: String) {
        val lvl = level ?: return
        if (lvl.isClientSide() || url.isEmpty()) {
            return
        }

        if (isScreenChunkLoaded()) {
            val tes = getConnectedScreen()
            val side = screenSide
            if (tes != null && side != null) {
                try {
                    tes.setScreenURL(side, url)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        }
    }
}
