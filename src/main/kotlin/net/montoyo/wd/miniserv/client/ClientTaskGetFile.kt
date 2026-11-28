/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv.client

import net.montoyo.wd.miniserv.Constants
import net.montoyo.wd.miniserv.OutgoingPacket
import net.montoyo.wd.miniserv.PacketID
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

class ClientTaskGetFile(private val uuid: UUID, private val fname: String) : ClientTask<ClientTaskGetFile>() {
    private var response: Int = 0
    private var hasResponse: Boolean = false
    private val responseLock = ReentrantLock()
    private val gotResponse: Condition = responseLock.newCondition()

    private val dataLock = ReentrantLock()
    private val dataChanged: Condition = dataLock.newCondition()
    private var data: ByteArray? = null
    private var dataLen: Int = 0

    override fun start() {
        val pkt = OutgoingPacket()
        pkt.writeByte(PacketID.GET_FILE.ordinal)
        pkt.writeLong(uuid.mostSignificantBits)
        pkt.writeLong(uuid.leastSignificantBits)
        pkt.writeString(fname)
        pkt.writeBoolean(true)

        client.sendPacket(pkt)
    }

    override fun abort() {
        responseLock.lock()
        if (!hasResponse) {
            response = Constants.GETF_STATUS_CONNECTION_LOST
            hasResponse = true
            gotResponse.signal()
        }
        responseLock.unlock()
        onData(ByteArray(0), -1) // This will trigger an error
    }

    fun onGetFileResponse(status: Int) {
        var triggerError = false
        responseLock.lock()

        if (hasResponse) {
            if (status != 0) triggerError = true
        } else {
            response = status
            hasResponse = true
            gotResponse.signal()

            if (response != 0) client.nextTask()
        }

        responseLock.unlock()

        if (triggerError) onData(ByteArray(0), -1)
    }

    fun waitForResponse(): Int {
        responseLock.lock()
        val t = System.currentTimeMillis()

        while (!hasResponse) {
            if (System.currentTimeMillis() - t > 10000) {
                responseLock.unlock()
                cancel()
                return Constants.GETF_STATUS_TIMED_OUT
            }

            try {
                gotResponse.await(100, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
            }
        }

        responseLock.unlock()
        return response
    }

    fun onData(data: ByteArray, len: Int) {
        dataLock.lock()
        while (this.data != null) {
            try {
                dataChanged.await()
            } catch (_: InterruptedException) {
            }
        }

        this.data = data
        dataLen = len
        dataChanged.signal()
        dataLock.unlock()

        if (len <= 0) client.nextTask()
    }

    fun waitForData(): ByteArray {
        dataLock.lock()
        val t = System.currentTimeMillis()

        while (data == null) {
            if (System.currentTimeMillis() - t > 10000) {
                data = ByteArray(0)
                dataLen = -1
                dataLock.unlock()
                cancel()
                return data!!
            }

            try {
                dataChanged.await(100, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
            }
        }

        dataLock.unlock() // This won't change until data is null again
        return data!!
    }

    fun getDataLength(): Int = dataLen

    fun nextData() {
        dataLock.lock()
        data = null
        dataChanged.signal()
        dataLock.unlock()
    }

    fun getFileName(): String = fname
}
