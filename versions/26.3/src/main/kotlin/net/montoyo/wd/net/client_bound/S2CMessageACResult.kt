/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.client_bound

import net.minecraft.network.FriendlyByteBuf
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.NameUUIDPair

class S2CMessageACResult : Packet {
    private var result: Array<NameUUIDPair>

    constructor(pairs: Array<NameUUIDPair>) {
        result = pairs
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        val cnt = buf.readByte().toInt()
        result = Array(cnt) { NameUUIDPair(buf) }
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeByte(result.size)
        for (pair in result) pair.writeTo(buf)
    }

    override fun handle(ctx: WDContext) {
        if (checkClient(ctx)) {
            ctx.enqueueWork {
                WebDisplays.PROXY.onAutocompleteResult(result)
            }
            ctx.setPacketHandled(true)
        }
    }
}
