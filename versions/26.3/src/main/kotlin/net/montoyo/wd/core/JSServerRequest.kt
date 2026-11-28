/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

import net.minecraft.network.FriendlyByteBuf
import net.montoyo.wd.utilities.Util

enum class JSServerRequest(vararg requestTypes: Class<*>) {
    CLEAR_REDSTONE,
    SET_REDSTONE_AT(Integer::class.java, Integer::class.java, java.lang.Boolean::class.java);

    private val requestTypes: Array<out Class<*>> = requestTypes

    @Suppress("DEPRECATION")
    fun serialize(buf: FriendlyByteBuf, data: Array<Any>): Boolean {
        if (data.size != requestTypes.size) return false

        for (i in data.indices) {
            if (data[i].javaClass != requestTypes[i]) return false
            Util.serialize(buf, data[i])
        }

        return true
    }

    @Suppress("DEPRECATION")
    fun deserialize(buf: FriendlyByteBuf): Array<Any>? {
        val ret = arrayOfNulls<Any>(requestTypes.size)

        try {
            for (i in requestTypes.indices) {
                ret[i] = Util.unserialize(buf, requestTypes[i])
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return ret as Array<Any>
    }

    companion object {
        @JvmStatic
        fun fromID(id: Int): JSServerRequest? {
            return if (id >= 0 && id < entries.size) entries[id] else null
        }
    }
}
