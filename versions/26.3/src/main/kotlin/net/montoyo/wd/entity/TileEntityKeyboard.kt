/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.entity

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.feline.Ocelot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.data.KeyboardData
import net.montoyo.wd.init.TileInit
import net.montoyo.wd.utilities.Util

class TileEntityKeyboard(arg2: BlockPos, arg3: BlockState) :
    TileEntityPeripheralBase(TileInit.KEYBOARD.get(), arg2, arg3) {

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

        if ((scr.rightsFor(player) and ScreenRights.INTERACT) == 0) {
            Util.toast(player, "restrictions")
            return InteractionResult.SUCCESS
        }

        KeyboardData(tes, side, blockPos).sendTo(player as ServerPlayer)
        return InteractionResult.SUCCESS
    }

    fun simulateCat(ent: Entity) {
        val lvl = level ?: return
        if (!isScreenChunkLoaded()) {
            return
        }

        val tes = getConnectedScreen()
        val side = screenSide
        if (tes != null && side != null) {
            val scr = tes.getScreen(side) ?: return
            val ok = if (ent is Player) {
                (scr.rightsFor(ent) and ScreenRights.INTERACT) != 0
            } else {
                (scr.otherRights and ScreenRights.INTERACT) != 0
            }

            if (ok) {
                val rnd = RANDOM_CHARS[(Math.random() * RANDOM_CHARS.length.toDouble()).toInt()]
                tes.type(side, "t$rnd", blockPos)

                val ownerPair = scr.owner ?: return
                val owner = lvl.getPlayerByUUID(ownerPair.uuid)
                if (owner is ServerPlayer && ent is Ocelot) {
                    WebDisplays.INSTANCE.criterionKeyboardCat?.trigger(owner)
                }
            }
        }
    }

    companion object {
        private const val RANDOM_CHARS = "AZERTYUIOPQSDFGHJKLMWXCVBNazertyuiopqsdfghjklmwxcvbn0123456789" //Yes I have an AZERTY keyboard, u care?
    }
}
