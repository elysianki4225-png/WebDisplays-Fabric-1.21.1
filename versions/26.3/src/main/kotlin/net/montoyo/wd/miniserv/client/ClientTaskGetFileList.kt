/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv.client

import net.montoyo.wd.miniserv.OutgoingPacket
import net.montoyo.wd.miniserv.PacketID
import java.util.UUID

class ClientTaskGetFileList(private val user: UUID) : ClientTask<ClientTaskGetFileList>() {
    private var files: Array<String>? = null

    init {
        runCallbackOnMcThread = true
    }

    override fun start() {
        val pkt = OutgoingPacket()
        pkt.writeByte(PacketID.LIST.ordinal)
        pkt.writeLong(user.mostSignificantBits)
        pkt.writeLong(user.leastSignificantBits)
        client.sendPacket(pkt)
    }

    override fun abort() {
    }

    fun onFileList(files: Array<String>) {
        this.files = files
        client.nextTask()
    }

    fun getFileList(): Array<String>? = files
}
