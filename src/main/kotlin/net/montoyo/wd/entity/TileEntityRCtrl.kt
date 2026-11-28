/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.entity

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.data.SetURLData
import net.montoyo.wd.init.TileInit
import net.montoyo.wd.utilities.Util

class TileEntityRCtrl(arg2: BlockPos, arg3: BlockState) :
    TileEntityPeripheralBase(TileInit.REMOTE_CONTROLLER.get(), arg2, arg3) {

    override fun onRightClick(player: Player, hand: InteractionHand): InteractionResult {
        val lvl = level ?: return InteractionResult.SUCCESS
        if (lvl.isClientSide) {
            return InteractionResult.SUCCESS
        }

        if (!isScreenChunkLoaded()) {
            Util.toast(player, "chunkUnloaded")
            return InteractionResult.SUCCESS
        }

        val tes = getConnectedScreen()
        val pos = screenPos
        val side = screenSide
        if (tes == null || pos == null || side == null) {
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

        SetURLData(pos, side, scr.url, blockPos).sendTo(player as ServerPlayer)
        return InteractionResult.SUCCESS
    }
}
