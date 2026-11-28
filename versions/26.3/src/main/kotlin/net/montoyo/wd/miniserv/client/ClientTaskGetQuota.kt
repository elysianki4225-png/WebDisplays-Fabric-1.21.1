/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv.client

import net.montoyo.wd.miniserv.OutgoingPacket
import net.montoyo.wd.miniserv.PacketID

class ClientTaskGetQuota : ClientTask<ClientTaskGetQuota>() {
    private var quota: Long = 0
    private var maxQuota: Long = 0

    init {
        runCallbackOnMcThread = true
    }

    override fun start() {
        val pkt = OutgoingPacket()
        pkt.writeByte(PacketID.QUOTA.ordinal)
        client.sendPacket(pkt)
    }

    override fun abort() {
    }

    fun onQuotaData(q: Long, m: Long) {
        quota = q
        maxQuota = m
        client.nextTask()
    }

    fun getMaxQuota(): Long = maxQuota

    fun getQuota(): Long = quota
}
