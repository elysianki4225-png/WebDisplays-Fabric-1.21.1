/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv.client

import net.montoyo.wd.miniserv.Constants
import net.montoyo.wd.miniserv.OutgoingPacket
import net.montoyo.wd.miniserv.PacketID
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.UUID

class ClientTaskCheckFile(private val uuid: UUID, private val fname: String) : ClientTask<ClientTaskCheckFile>() {
    private var status: Int = Constants.GETF_STATUS_INTERNAL_ERROR

    init {
        runCallbackOnMcThread = true
    }

    override fun start() {
        val pkt = OutgoingPacket()
        pkt.writeByte(PacketID.GET_FILE.ordinal)
        pkt.writeLong(uuid.mostSignificantBits)
        pkt.writeLong(uuid.leastSignificantBits)
        pkt.writeString(fname)
        pkt.writeBoolean(false)

        client.sendPacket(pkt)
    }

    override fun abort() {
    }

    fun onStatus(s: Int) {
        status = s
        client.nextTask()
    }

    fun getStatus(): Int = status

    fun getURL(): String {
        return try {
            "webdisplays://$uuid/${URLEncoder.encode(fname, "UTF-8")}"
        } catch (ex: UnsupportedEncodingException) {
            ex.printStackTrace()
            "hi"
        }
    }
}
