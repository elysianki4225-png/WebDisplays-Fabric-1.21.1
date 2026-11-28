/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.loading

import com.google.gson.JsonArray

class JsonAWrapper(
    private val array: JsonArray,
    private val variables: Map<String, Double>
) {
    fun size(): Int = array.size()

    fun getString(i: Int): String = array.get(i).asString

    fun getInt(i: Int): Int = array.get(i).asInt

    fun getLong(i: Int): Long = array.get(i).asLong

    fun getFloat(i: Int): Float = array.get(i).asFloat

    fun getDouble(i: Int): Double = array.get(i).asDouble

    fun getBool(i: Int): Boolean = array.get(i).asBoolean

    fun getObject(i: Int): JsonOWrapper = JsonOWrapper(array.get(i).asJsonObject, variables)

    fun getArray(i: Int): JsonAWrapper = JsonAWrapper(array.get(i).asJsonArray, variables)

    fun getArray(): JsonArray = array
}
