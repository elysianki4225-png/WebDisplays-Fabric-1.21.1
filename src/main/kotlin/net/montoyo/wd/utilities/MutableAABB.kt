/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

import net.minecraft.world.phys.AABB
import kotlin.math.max
import kotlin.math.min

class MutableAABB : AABB {
    constructor() : super(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    constructor(pos: Vector3i) : super(
        pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
        pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()
    )

    constructor(a: Vector3i, b: Vector3i) : super(
        a.x.toDouble(), a.y.toDouble(), a.z.toDouble(),
        b.x.toDouble(), b.y.toDouble(), b.z.toDouble()
    )

    constructor(bb: AABB) : super(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ)

    constructor(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) : super(
        x1, y1, z1, x2, y2, z2
    )

    fun expand(vec: Vector3i): MutableAABB {
        if (vec.x > maxX) maxX = vec.x.toDouble()
        else if (vec.x < minX) minX = vec.x.toDouble()

        if (vec.y > maxY) maxY = vec.y.toDouble()
        else if (vec.y < minY) minY = vec.y.toDouble()

        if (vec.z > maxZ) maxZ = vec.z.toDouble()
        else if (vec.z < minZ) minZ = vec.z.toDouble()

        return this
    }

    override fun move(x: Double, y: Double, z: Double): AABB {
        minX += x
        minY += y
        minZ += z
        maxX += x
        maxY += y
        maxZ += z
        return this
    }

    fun toMc(): AABB = AABB(minX, minY, minZ, maxX, maxY, maxZ)

    fun setAndCheck(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) {
        minX = min(x1, x2)
        minY = min(y1, y2)
        minZ = min(z1, z2)

        maxX = max(x1, x2)
        maxY = max(y1, y2)
        maxZ = max(z1, z2)
    }

    fun expand(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) {
        minX = min(minX, min(x1, x2))
        minY = min(minY, min(y1, y2))
        minZ = min(minZ, min(z1, z2))

        maxX = max(maxX, max(x1, x2))
        maxY = max(maxY, max(y1, y2))
        maxZ = max(maxZ, max(z1, z2))
    }
}
