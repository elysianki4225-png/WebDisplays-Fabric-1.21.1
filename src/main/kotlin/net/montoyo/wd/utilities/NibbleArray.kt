/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

class NibbleArray {
    private val data: ByteArray

    constructor(count: Int) {
        val aligned = if ((count and 1) != 0) count + 1 else count
        data = ByteArray(aligned shr 1)
    }

    constructor(d: ByteArray) {
        data = d
    }

    fun get(idx: Int): Int {
        return if ((idx and 1) == 0) {
            (data[idx shr 1].toInt() shr 4) and 0x0F // MSB
        } else {
            data[idx shr 1].toInt() and 0x0F // LSB
        }
    }

    fun set(idx: Int, value: Int) {
        val masked = value and 0x0F
        val byteIdx = idx shr 1

        data[byteIdx] = if ((idx and 1) == 0) {
            ((data[byteIdx].toInt() and 0x0F) or (masked shl 4)).toByte() // MSB
        } else {
            ((data[byteIdx].toInt() and 0xF0) or masked).toByte() // LSB
        }
    }

    fun copyBytes(): ByteArray = data.copyOf()
}
