/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.Identifier
import net.minecraft.world.level.Level
import net.montoyo.wd.client.gui.GuiRedstoneCtrl
import net.montoyo.wd.net.BufferUtils
import net.montoyo.wd.utilities.Vector3i

class RedstoneCtrlData : GuiData {
    @JvmField
    var dimension: Identifier = Identifier.withDefaultNamespace("overworld")

    @JvmField
    var pos: Vector3i = Vector3i()

    @JvmField
    var risingEdgeURL: String = ""

    @JvmField
    var fallingEdgeURL: String = ""

    constructor() : super()

    constructor(d: Identifier, p: BlockPos, r: String, f: String) {
        dimension = d
        pos = Vector3i(p)
        risingEdgeURL = r
        fallingEdgeURL = f
    }

    @Environment(EnvType.CLIENT)
    override fun createGui(old: Screen?, world: Level): Screen {
        return GuiRedstoneCtrl(old!!.title, dimension, pos, risingEdgeURL, fallingEdgeURL)
    }

    override fun getName(): String = "RedstoneCtrl"

    override fun serialize(buf: FriendlyByteBuf) {
        buf.writeUtf(dimension.toString())
        BufferUtils.writeVec3i(buf, pos)
        buf.writeUtf(risingEdgeURL)
        buf.writeUtf(fallingEdgeURL)
    }

    override fun deserialize(buf: FriendlyByteBuf) {
        dimension = Identifier.parse(buf.readUtf())
        pos = BufferUtils.readVec3i(buf)
        risingEdgeURL = buf.readUtf()
        fallingEdgeURL = buf.readUtf()
    }
}
