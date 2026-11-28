/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.client_bound

import net.minecraft.network.FriendlyByteBuf
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.core.JSServerRequest
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.Log

class S2CMessageJSResponse : Packet {
    private var id: Int = 0
    private var type: JSServerRequest? = null
    private var success: Boolean = false
    private var data: ByteArray? = null
    private var errCode: Int = 0
    private var errString: String? = null

    constructor(id: Int, t: JSServerRequest, d: ByteArray) {
        this.id = id
        type = t
        success = true
        data = d
    }

    constructor(id: Int, t: JSServerRequest, code: Int, err: String) {
        this.id = id
        type = t
        success = false
        errCode = code
        errString = err
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        val id = buf.readInt()
        val type = JSServerRequest.fromID(buf.readByte().toInt())
        val success = buf.readBoolean()

        this.id = id
        this.type = type
        this.success = success

        if (success) {
            val d = ByteArray(buf.readByte().toInt())
            buf.readBytes(d)
            this.data = d
        } else {
            this.errCode = buf.readInt()
            this.errString = buf.readUtf()
        }
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeInt(id)
        buf.writeByte(type!!.ordinal)
        buf.writeBoolean(success)

        if (success) {
            val d = data!!
            buf.writeByte(d.size)
            buf.writeBytes(d) // TODO: Eventually compress this data
        } else {
            buf.writeInt(errCode)
            buf.writeUtf(errString!!)
        }
    }

    override fun handle(ctx: WDContext) {
        if (checkClient(ctx)) {
            ctx.enqueueWork {
                try {
                    if (success) {
                        WebDisplays.PROXY.handleJSResponseSuccess(id, type!!, data!!)
                    } else {
                        WebDisplays.PROXY.handleJSResponseError(id, type!!, errCode, errString!!)
                    }
                } catch (t: Throwable) {
                    Log.warningEx("Could not handle JS response", t)
                }
            }
            ctx.setPacketHandled(true)
        }
    }
}
