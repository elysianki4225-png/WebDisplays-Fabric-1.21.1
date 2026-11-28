/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.server_bound

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.net.WDDistributor
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.client_bound.S2CMessageACResult
import net.montoyo.wd.utilities.NameUUIDPair

class C2SMessageACQuery : Packet, Runnable {
    private var player: ServerPlayer? = null
    private var beginning: String
    private var matchExact: Boolean

    constructor(beg: String, exact: Boolean) {
        beginning = beg
        matchExact = exact
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        beginning = buf.readUtf()
        matchExact = buf.readBoolean()
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(beginning)
        buf.writeBoolean(matchExact)
    }

    override fun run() {
        val profiles = WebDisplays.PROXY.getOnlineGameProfiles()
        val result: Array<NameUUIDPair> = if (matchExact) {
            profiles.filter { it.name.equals(beginning, ignoreCase = true) }
                .map(::NameUUIDPair)
                .toTypedArray()
        } else {
            val lBeg = beginning.lowercase()
            profiles.filter { it.name.lowercase().startsWith(lBeg) }
                .map(::NameUUIDPair)
                .toTypedArray()
        }

        WDNetworkRegistry.INSTANCE.send(WDDistributor.PLAYER.with { player!! }, S2CMessageACResult(result))
    }

    override fun handle(ctx: WDContext) {
        if (checkServer(ctx)) {
            player = ctx.sender
            ctx.enqueueWork(this)
            ctx.setPacketHandled(true)
        }
    }
}
