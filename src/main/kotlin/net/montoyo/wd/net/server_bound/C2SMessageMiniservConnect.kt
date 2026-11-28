/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.server_bound

import net.minecraft.network.FriendlyByteBuf
import net.montoyo.wd.miniserv.server.Server
import net.montoyo.wd.net.BufferUtils
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.net.client_bound.S2CMessageMiniservKey

class C2SMessageMiniservConnect : Packet {
    private var modulus: ByteArray
    private var exponent: ByteArray

    constructor(mod: ByteArray, exp: ByteArray) {
        modulus = mod
        exponent = exp
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        modulus = BufferUtils.readBytes(buf)
        exponent = BufferUtils.readBytes(buf)
    }

    override fun write(buf: FriendlyByteBuf) {
        BufferUtils.writeBytes(buf, modulus)
        BufferUtils.writeBytes(buf, exponent)
    }

    override fun handle(ctx: WDContext) {
        if (checkServer(ctx)) {
            try {
                val cliMgr = Server.getInstance().getClientManager()
                val encKey = cliMgr.encryptClientKey(ctx.sender!!.gameProfile.id, modulus, exponent)

                if (encKey != null) {
                    respond(ctx, S2CMessageMiniservKey(encKey))
                }

                ctx.setPacketHandled(true)
            } catch (err: Throwable) {
                err.printStackTrace()
                throw RuntimeException(err)
            }
        }
    }
}
