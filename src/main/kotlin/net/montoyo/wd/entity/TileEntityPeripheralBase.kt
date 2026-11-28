/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.montoyo.wd.core.IPeripheral
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.Vector3i
import java.util.Objects

abstract class TileEntityPeripheralBase(
    arg: BlockEntityType<*>,
    arg2: BlockPos,
    arg3: BlockState
) : BlockEntity(arg, arg2, arg3), IPeripheral {

    var screenPos: Vector3i? = null
        protected set

    var screenSide: BlockSide? = null
        protected set

    override fun loadAdditional(tag: CompoundTag, registryAccess: HolderLookup.Provider) {
        super.loadAdditional(tag, registryAccess)

        if (tag.contains("WDScreen", 10)) {
            val scr = tag.getCompound("WDScreen")
            screenPos = Vector3i(scr.getInt("X"), scr.getInt("Y"), scr.getInt("Z"))
            screenSide = BlockSide.values()[scr.getByte("Side").toInt()]
        } else {
            screenPos = null
            screenSide = null
        }
    }

    override fun saveAdditional(tag: CompoundTag, registryAccess: HolderLookup.Provider) {
        super.saveAdditional(tag, registryAccess)

        val pos = screenPos
        val side = screenSide
        if (pos != null && side != null) {
            val scr = CompoundTag()
            scr.putInt("X", pos.x)
            scr.putInt("Y", pos.y)
            scr.putInt("Z", pos.z)
            scr.putByte("Side", side.ordinal.toByte())

            tag.put("WDScreen", scr)
        }
    }

    override fun connect(world_: Level, blockPos: BlockPos, blockState: BlockState, pos: Vector3i, side: BlockSide): Boolean {
        val te = world_.getBlockEntity(pos.toBlock())
        if (te !is TileEntityScreen) {
            Log.error("TileEntityPeripheralBase.connect(): Tile entity at %s is not a screen!", pos.toString())
            return false
        }

        if (te.getScreen(side) == null) {
            Log.error("TileEntityPeripheralBase.connect(): There is no screen at %s on side %s!", pos.toString(), side.toString())
            return false
        }

        screenPos = pos
        screenSide = side
        setChanged()
        return true
    }

    fun isLinked(): Boolean = screenPos != null && screenSide != null

    fun isScreenChunkLoaded(): Boolean {
        val pos = screenPos
        if (pos == null || screenSide == null) {
            return true
        }

        val chunk = Objects.requireNonNull(getLevel())!!.chunkSource.getChunk(pos.x shr 4, pos.z shr 4, true)
        return chunk != null && !chunk.isEmpty
    }

    fun getConnectedScreen(): TileEntityScreen? {
        val pos = screenPos ?: return null
        val side = screenSide ?: return null

        val te = level?.getBlockEntity(pos.toBlock())
        if (te !is TileEntityScreen || te.getScreen(side) == null) {
            screenPos = null
            screenSide = null
            setChanged()
            return null
        }

        return te
    }

    fun getConnectedScreenEx(): TileEntityScreen? {
        val pos = screenPos ?: return null
        val side = screenSide ?: return null

        val te = level?.getBlockEntity(pos.toBlock())
        if (te !is TileEntityScreen || te.getScreen(side) == null) {
            return null
        }

        return te
    }

    open fun onNeighborChange(neighborType: Block, neighborPos: BlockPos) {
    }

    open fun onRightClick(player: Player, hand: InteractionHand): InteractionResult {
        return InteractionResult.PASS
    }
}
