/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv.client

import net.montoyo.wd.miniserv.OutgoingPacket
import net.montoyo.wd.miniserv.PacketID

class ClientTaskDeleteFile(private val fname: String) : ClientTask<ClientTaskDeleteFile>() {
    private var status: Int = 0

    override fun start() {
        val pkt = OutgoingPacket()
        pkt.writeByte(PacketID.DELETE.ordinal)
        pkt.writeString(fname)
        client.sendPacket(pkt)
    }

    override fun abort() {
    }

    fun onStatusPacket(s: Int) {
        status = s
        client.nextTask()
    }

    fun getStatus(): Int = status
}
