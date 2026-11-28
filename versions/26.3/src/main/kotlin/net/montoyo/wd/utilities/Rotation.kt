/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

enum class Rotation(@JvmField val angle: Float, @JvmField val isVertical: Boolean) {
    ROT_0(0.0f, false),
    ROT_90(90.0f, true),
    ROT_180(180.0f, false),
    ROT_270(270.0f, true);

    @JvmField
    val isNull: Boolean = angle == 0.0f

    fun getAngleAsInt(): Int = angle.toInt()
}
