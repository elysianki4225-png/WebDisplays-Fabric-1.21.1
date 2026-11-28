/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.data.ServerData
import net.montoyo.wd.init.TileInit
import net.montoyo.wd.utilities.NameUUIDPair
import net.montoyo.wd.utilities.Util

class TileEntityServer(arg2: BlockPos, arg3: BlockState) :
    BlockEntity(TileInit.SERVER.get(), arg2, arg3) {

    private var owner: NameUUIDPair? = null

    override fun loadAdditional(tag: CompoundTag, registryAccess: HolderLookup.Provider) {
        super.loadAdditional(tag, registryAccess)
        owner = Util.readOwnerFromNBT(tag)
    }

    override fun saveAdditional(tag: CompoundTag, registryAccess: HolderLookup.Provider) {
        super.saveAdditional(tag, registryAccess)
        Util.writeOwnerToNBT(tag, owner)
    }

    fun setOwner(ep: Player) {
        owner = NameUUIDPair(ep.gameProfile)
        setChanged()
    }

    fun onPlayerRightClick(ply: Player) {
        val lvl = level ?: return
        if (lvl.isClientSide) {
            return
        }

        val o = owner
        if (WebDisplays.INSTANCE.miniservPort == 0) {
            Util.toast(ply, "noMiniserv")
        } else if (o != null && ply is ServerPlayer) {
            ServerData(blockPos, o).sendTo(ply)
        }
    }
}
