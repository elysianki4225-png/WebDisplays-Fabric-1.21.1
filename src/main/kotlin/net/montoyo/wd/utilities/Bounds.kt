/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

class Bounds(
    @JvmField val minX: Int,
    @JvmField val minY: Int,
    @JvmField val maxX: Int,
    @JvmField val maxY: Int
) {
    fun getWidth(): Int = maxX - minX

    fun getHeight(): Int = maxY - minY
}
