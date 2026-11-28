/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.server_bound

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.entity.player.Player
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.entity.TileEntityRedCtrl
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector3i

class C2SMessageRedstoneCtrl : Packet, Runnable {
    private var player: Player? = null
    private lateinit var pos: Vector3i
    private lateinit var risingEdgeURL: String
    private lateinit var fallingEdgeURL: String

    constructor()

    constructor(p: Vector3i, r: String, f: String) {
        pos = p
        risingEdgeURL = r
        fallingEdgeURL = f
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        pos = Vector3i(buf)
        risingEdgeURL = buf.readUtf()
        fallingEdgeURL = buf.readUtf()
    }

    override fun run() {
        val ply = player ?: return
        val world = ply.level()
        val blockPos = pos.toBlock()
        val maxRange = 4.5

        if (ply.distanceToSqr(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble()) > maxRange * maxRange) {
            return
        }

        val te = world.getBlockEntity(blockPos)
        if (te !is TileEntityRedCtrl) return

        if (!te.isScreenChunkLoaded) {
            Util.toast(ply, "chunkUnloaded")
            return
        }

        val tes = te.connectedScreen ?: return
        if ((tes.getScreen(te.screenSide).rightsFor(ply) and ScreenRights.CHANGE_URL) == 0) return

        te.setURLs(risingEdgeURL, fallingEdgeURL)
    }

    override fun write(buf: FriendlyByteBuf) {
        pos.writeTo(buf)
        buf.writeUtf(risingEdgeURL)
        buf.writeUtf(fallingEdgeURL)
    }

    override fun handle(ctx: WDContext) {
        if (checkServer(ctx)) {
            player = ctx.sender
            ctx.enqueueWork(this)
            ctx.setPacketHandled(true)
        }
    }
}
