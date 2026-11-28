/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.client_bound

import net.minecraft.network.FriendlyByteBuf
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.miniserv.client.Client
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext

class S2CMessageServerInfo : Packet {
    private var miniservPort: Int

    constructor(msPort: Int) {
        miniservPort = msPort
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        miniservPort = buf.readShort().toInt()
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeShort(miniservPort)
    }

    override fun handle(ctx: WDContext) {
        if (checkClient(ctx)) {
            try {
                WebDisplays.PROXY.setMiniservClientPort(miniservPort)
                val message = Client.getInstance().beginConnection()
                if (message != null) {
                    respond(ctx, message)
                }
                ctx.setPacketHandled(true)
            } catch (err: Throwable) {
                err.printStackTrace()
                throw RuntimeException(err)
            }
        }
    }
}
