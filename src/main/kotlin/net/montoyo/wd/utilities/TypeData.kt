/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

import com.google.gson.annotations.SerializedName

class TypeData(
    private val a: Action,
    private val k: Int,
    private val m: Int,
    private val s: Int
) {
    enum class Action {
        @SerializedName("i")
        INVALID,

        @SerializedName("p")
        PRESS,

        @SerializedName("r")
        RELEASE,

        @SerializedName("t")
        TYPE
    }

    constructor() : this(Action.INVALID, 0, 0, 0)

    fun getAction(): Action = a

    fun getKeyCode(): Int = k

    fun getModifier(): Int = m

    fun getScanCode(): Int = s
}
