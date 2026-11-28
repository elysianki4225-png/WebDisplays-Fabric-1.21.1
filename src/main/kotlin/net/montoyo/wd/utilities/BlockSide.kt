/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

enum class BlockSide(u: Vector3i, r: Vector3i, f: Vector3i) {
    BOTTOM(Vector3i(0, 0, -1), Vector3i(1, 0, 0), Vector3i(0, -1, 0)),
    TOP(Vector3i(0, 0, -1), Vector3i(1, 0, 0), Vector3i(0, 1, 0)),
    NORTH(Vector3i(0, 1, 0), Vector3i(-1, 0, 0), Vector3i(0, 0, -1)),
    SOUTH(Vector3i(0, 1, 0), Vector3i(1, 0, 0), Vector3i(0, 0, 1)),
    WEST(Vector3i(0, 1, 0), Vector3i(0, 0, 1), Vector3i(-1, 0, 0)),
    EAST(Vector3i(0, 1, 0), Vector3i(0, 0, -1), Vector3i(1, 0, 0));

    @JvmField
    val up: Vector3i = u

    @JvmField
    val right: Vector3i = r

    @JvmField
    val forward: Vector3i = f

    @JvmField
    val down: Vector3i = u.clone().neg()

    @JvmField
    val left: Vector3i = r.clone().neg()

    @JvmField
    val backward: Vector3i = f.clone().neg()

    fun reverse(): BlockSide {
        val side = ordinal
        val div = side / 2
        val rest = 1 - side % 2
        return entries[div * 2 + rest]
    }

    companion object {
        @JvmStatic
        fun reverse(side: Int): Int {
            val div = side / 2
            val rest = 1 - side % 2
            return div * 2 + rest
        }

        @JvmStatic
        fun fromInt(s: Int): BlockSide? {
            return if (s < 0 || s >= entries.size) null else entries[s]
        }
    }
}
