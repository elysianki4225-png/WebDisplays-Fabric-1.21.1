/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv

import net.montoyo.wd.utilities.Log
import java.nio.ByteBuffer

class PacketReader {
    private val sizeArray = ByteArray(4)
    private var packetData: ByteArray? = null
    private var pos = 0
    private var needSize = true

    fun readFrom(buf: ByteBuffer): Boolean {
        if (needSize) {
            if (readByteArray(sizeArray, buf)) {
                var packetSize = ((sizeArray[0].toInt() and 0xFF) shl 24) or
                    ((sizeArray[1].toInt() and 0xFF) shl 16) or
                    ((sizeArray[2].toInt() and 0xFF) shl 8) or
                    (sizeArray[3].toInt() and 0xFF)
                needSize = false
                pos = 0

                if (packetSize < 5 || packetSize > 70000) {
                    Log.warning("Got invalid packet of size %d, things won't go well...", packetSize)
                    return true // Abort packet reading
                }

                packetSize -= 4
                packetData = ByteArray(packetSize)
            } else {
                return false
            }
        }

        return readByteArray(packetData!!, buf)
    }

    private fun readByteArray(dst: ByteArray, src: ByteBuffer): Boolean {
        val remaining = dst.size - pos
        val read = minOf(remaining, src.remaining())

        src.get(dst, pos, read)
        pos += read

        return pos >= dst.size
    }

    fun getPacketData(): ByteArray? = packetData

    fun reset() {
        packetData = null
        pos = 0
        needSize = true
    }
}
