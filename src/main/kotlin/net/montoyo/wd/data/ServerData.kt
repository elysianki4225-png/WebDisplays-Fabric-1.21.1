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
import net.montoyo.wd.client.gui.GuiServer
import net.montoyo.wd.net.BufferUtils
import net.montoyo.wd.utilities.NameUUIDPair
import net.montoyo.wd.utilities.Vector3i

class ServerData : GuiData {
    @JvmField
    var pos: Vector3i = Vector3i()

    @JvmField
    var owner: NameUUIDPair = NameUUIDPair()

    constructor()

    constructor(bp: BlockPos, owner: NameUUIDPair) {
        pos = Vector3i(bp)
        this.owner = owner
    }

    @Environment(EnvType.CLIENT)
    override fun createGui(old: Screen?, world: Level): Screen {
        return GuiServer(pos, owner)
    }

    override fun getName(): String = "Server"

    override fun serialize(buf: FriendlyByteBuf) {
        BufferUtils.writeVec3i(buf, pos)
        owner.writeTo(buf)
    }

    override fun deserialize(buf: FriendlyByteBuf) {
        pos = BufferUtils.readVec3i(buf)
        owner = NameUUIDPair(buf)
    }
}
