/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.client_bound

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide

class S2CMessageCloseGui : Packet {
    private var blockPos: BlockPos
    private var blockSide: BlockSide?

    constructor(bp: BlockPos) {
        blockPos = bp
        blockSide = null
    }

    constructor(bp: BlockPos, side: BlockSide?) {
        blockPos = bp
        blockSide = side
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        blockPos = BlockPos(buf.readInt(), buf.readInt(), buf.readInt())
        val b = buf.readByte().toInt()
        blockSide = if (b <= 0) null else BlockSide.values()[b - 1]
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeInt(blockPos.x)
        buf.writeInt(blockPos.y)
        buf.writeInt(blockPos.z)

        val side = blockSide
        if (side == null) buf.writeByte(0)
        else buf.writeByte(side.ordinal + 1)
    }

    override fun handle(ctx: WDContext) {
        if (checkClient(ctx)) {
            ctx.enqueueWork {
                val side = blockSide
                if (side == null) {
                    for (s in BlockSide.values()) {
                        WebDisplays.PROXY.closeGui(blockPos, s)
                    }
                } else {
                    WebDisplays.PROXY.closeGui(blockPos, side)
                }
            }
            ctx.setPacketHandled(true)
        }
    }
}
