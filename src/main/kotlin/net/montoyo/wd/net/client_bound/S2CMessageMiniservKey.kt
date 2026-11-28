/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.client_bound

import net.minecraft.network.FriendlyByteBuf
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.client.ClientProxy
import net.montoyo.wd.miniserv.client.Client
import net.montoyo.wd.net.BufferUtils
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.Log

class S2CMessageMiniservKey : Packet {
    private var encryptedKey: ByteArray

    constructor(key: ByteArray) {
        encryptedKey = key
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        encryptedKey = BufferUtils.readBytes(buf)
    }

    override fun write(buf: FriendlyByteBuf) {
        BufferUtils.writeBytes(buf, encryptedKey)
    }

    override fun handle(ctx: WDContext) {
        if (checkClient(ctx)) {
            if (Client.getInstance().decryptKey(encryptedKey)) {
                Log.info("Successfully received and decrypted key, starting miniserv client...")
                val proxy = WebDisplays.PROXY
                if (proxy is ClientProxy) {
                    proxy.startMiniservClient()
                }
            }

            ctx.setPacketHandled(true)
        }
    }
}
