/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.Level
import net.montoyo.wd.client.gui.GuiKeyboard
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.Vector3i

class KeyboardData : GuiData {
    @JvmField
    var pos: Vector3i = Vector3i()

    @JvmField
    var side: BlockSide = BlockSide.BOTTOM

    @JvmField
    var kbX: Int = 0

    @JvmField
    var kbY: Int = 0

    @JvmField
    var kbZ: Int = 0

    constructor()

    constructor(tes: TileEntityScreen, side: BlockSide, kbPos: BlockPos) {
        pos = Vector3i(tes.blockPos)
        this.side = side
        kbX = kbPos.x
        kbY = kbPos.y
        kbZ = kbPos.z
    }

    @Environment(EnvType.CLIENT)
    override fun createGui(old: Screen?, world: Level): Screen? {
        val te = world.getBlockEntity(pos.toBlock())
        if (te !is TileEntityScreen) {
            Log.error("TileEntity at %s is not a screen; can't open keyboard!", pos.toString())
            return null
        }

        return GuiKeyboard(te, side, BlockPos(kbX, kbY, kbZ))
    }

    override fun getName(): String = "Keyboard"

    override fun serialize(buf: FriendlyByteBuf) {
        buf.writeInt(pos.x)
        buf.writeInt(pos.y)
        buf.writeInt(pos.z)
        buf.writeByte(side.ordinal)
        buf.writeInt(kbX)
        buf.writeInt(kbY)
        buf.writeInt(kbZ)
    }

    override fun deserialize(buf: FriendlyByteBuf) {
        pos = Vector3i(buf.readInt(), buf.readInt(), buf.readInt())
        side = BlockSide.entries[buf.readByte().toInt()]
        kbX = buf.readInt()
        kbY = buf.readInt()
        kbZ = buf.readInt()
    }
}
