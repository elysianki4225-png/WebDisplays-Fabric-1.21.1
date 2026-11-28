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
import net.montoyo.wd.client.gui.GuiSetURL2
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.BufferUtils
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.Vector3i

class SetURLData : GuiData {
    @JvmField
    var pos: Vector3i = Vector3i()

    @JvmField
    var side: BlockSide = BlockSide.BOTTOM

    @JvmField
    var url: String = ""

    @JvmField
    var isRemote: Boolean = false

    @JvmField
    var remoteLocation: Vector3i = Vector3i()

    constructor()

    constructor(pos: Vector3i, side: BlockSide, url: String) {
        this.pos = pos
        this.side = side
        this.url = url
        isRemote = false
        remoteLocation = Vector3i()
    }

    constructor(pos: Vector3i, side: BlockSide, url: String, rl: BlockPos) {
        this.pos = pos
        this.side = side
        this.url = url
        isRemote = true
        remoteLocation = Vector3i(rl)
    }

    @Environment(EnvType.CLIENT)
    override fun createGui(old: Screen?, world: Level): Screen? {
        val te = world.getBlockEntity(pos.toBlock())
        if (te !is TileEntityScreen) {
            Log.error("TileEntity at %s is not a screen; can't open gui!", pos.toString())
            return null
        }

        return GuiSetURL2(te, side, url, if (isRemote) remoteLocation else null)
    }

    override fun getName(): String = "SetURL"

    override fun serialize(buf: FriendlyByteBuf) {
        BufferUtils.writeVec3i(buf, pos)
        BufferUtils.writeEnum(buf, side, 1.toByte())
        buf.writeUtf(url)
        buf.writeBoolean(isRemote)
        if (isRemote) BufferUtils.writeVec3i(buf, remoteLocation)
    }

    override fun deserialize(buf: FriendlyByteBuf) {
        pos = BufferUtils.readVec3i(buf)
        side = BufferUtils.readEnum(buf, { v -> BlockSide.entries[v] }, 1.toByte()) as BlockSide
        url = buf.readUtf()
        isRemote = buf.readBoolean()
        remoteLocation = if (isRemote) BufferUtils.readVec3i(buf) else Vector3i()
    }
}
