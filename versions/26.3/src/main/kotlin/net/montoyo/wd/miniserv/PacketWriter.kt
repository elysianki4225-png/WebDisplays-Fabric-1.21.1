/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv

import java.nio.ByteBuffer
import kotlin.math.min

class PacketWriter {
    private val sizeBytes = ByteArray(4)
    private var packet: ByteArray? = null
    private var pos = 0
    private var needToWriteSize = true

    fun writeTo(bb: ByteBuffer): Boolean {
        val pkt = packet ?: return true // Next packet please

        if (needToWriteSize) {
            if (writeByteArray(bb, sizeBytes)) {
                needToWriteSize = false
                pos = 0
            } else {
                return false
            }
        }

        if (writeByteArray(bb, pkt)) {
            packet = null
            return true
        }
        return false
    }

    private fun writeByteArray(dst: ByteBuffer, src: ByteArray): Boolean {
        val remaining = src.size - pos
        val written = min(dst.remaining(), remaining)
        dst.put(src, pos, written)

        pos += written
        return pos >= src.size
    }

    fun reset(pkt: ByteArray) {
        val len = pkt.size + 4
        sizeBytes[0] = ((len shr 24) and 0xFF).toByte()
        sizeBytes[1] = ((len shr 16) and 0xFF).toByte()
        sizeBytes[2] = ((len shr 8) and 0xFF).toByte()
        sizeBytes[3] = (len and 0xFF).toByte()

        packet = pkt
        pos = 0
        needToWriteSize = true
    }

    fun clear() {
        packet = null
        pos = 0
        needToWriteSize = true
    }
}
