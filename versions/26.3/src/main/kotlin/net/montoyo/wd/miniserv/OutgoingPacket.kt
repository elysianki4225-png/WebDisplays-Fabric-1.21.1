/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.function.Consumer

class OutgoingPacket {
    private var baos: ByteArrayOutputStream? = ByteArrayOutputStream()
    private var dos: DataOutputStream? = DataOutputStream(baos!!)
    private var onFinish: Consumer<OutgoingPacket>? = null

    fun writeLong(l: Long) {
        try {
            dos!!.writeLong(l)
        } catch (_: IOException) {
        }
    }

    fun writeInt(i: Int) {
        try {
            dos!!.writeInt(i)
        } catch (_: IOException) {
        }
    }

    fun writeByte(b: Int) {
        try {
            dos!!.writeByte(b)
        } catch (_: IOException) {
        }
    }

    fun writeShort(s: Int) {
        try {
            dos!!.writeShort(s)
        } catch (_: IOException) {
        }
    }

    fun writeBoolean(b: Boolean) {
        try {
            dos!!.writeBoolean(b)
        } catch (_: IOException) {
        }
    }

    fun writeBytes(data: ByteArray) {
        try {
            dos!!.write(data)
        } catch (_: IOException) {
        }
    }

    fun writeBytes(data: ByteArray, offset: Int, size: Int) {
        try {
            dos!!.write(data, offset, size)
        } catch (_: IOException) {
        }
    }

    fun writeString(str: String) {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        try {
            dos!!.writeShort(bytes.size)
            dos!!.write(bytes)
        } catch (_: IOException) {
        }
    }

    @Throws(IOException::class)
    fun writeStream(input: InputStream, max: Int): Int {
        var remaining = max
        val buf = ByteArray(8192)

        while (remaining > 0) {
            val read = input.read(buf, 0, minOf(remaining, buf.size))
            if (read <= 0) return max - remaining

            dos!!.write(buf, 0, read)
            remaining -= read
        }

        return max
    }

    fun finish(): ByteArray {
        onFinish?.accept(this)

        val bytes = baos!!.toByteArray()
        baos = null
        dos = null
        return bytes
    }

    fun setOnFinishAction(action: Consumer<OutgoingPacket>?) {
        onFinish = action
    }
}
