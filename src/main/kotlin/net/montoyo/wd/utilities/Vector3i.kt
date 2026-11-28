/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos

class Vector3i : Cloneable {
    @JvmField
    var x: Int

    @JvmField
    var y: Int

    @JvmField
    var z: Int

    constructor() {
        x = 0
        y = 0
        z = 0
    }

    constructor(`val`: Int) {
        x = `val`
        y = `val`
        z = `val`
    }

    constructor(x: Int, y: Int, z: Int) {
        this.x = x
        this.y = y
        this.z = z
    }

    constructor(bp: BlockPos) {
        x = bp.x
        y = bp.y
        z = bp.z
    }

    constructor(bb: ByteBuf) {
        x = bb.readInt()
        y = bb.readInt()
        z = bb.readInt()
    }

    public override fun clone(): Vector3i = Vector3i(x, y, z)

    override fun hashCode(): Int = ((37 + x) * 31 + y) * 43 + z

    override fun equals(other: Any?): Boolean {
        if (other !is Vector3i) return false
        return other.x == x && other.y == y && other.z == z
    }

    fun equalsBlockPos(bp: BlockPos): Boolean = bp.x == x && bp.y == y && bp.z == z

    override fun toString(): String = "X: $x, Y: $y, Z: $z"

    // Add
    fun add(src: Vector3i): Vector3i {
        x += src.x
        y += src.y
        z += src.z
        return this
    }

    fun addMul(src: Vector3i, mul: Int): Vector3i {
        x += src.x * mul
        y += src.y * mul
        z += src.z * mul
        return this
    }

    fun add(x: Int, y: Int, z: Int): Vector3i {
        this.x += x
        this.y += y
        this.z += z
        return this
    }

    fun add(xyz: Int): Vector3i {
        x += xyz
        y += xyz
        z += xyz
        return this
    }

    // Sub
    fun sub(src: Vector3i): Vector3i {
        x -= src.x
        y -= src.y
        z -= src.z
        return this
    }

    fun sub(x: Int, y: Int, z: Int): Vector3i {
        this.x -= x
        this.y -= y
        this.z -= z
        return this
    }

    fun sub(xyz: Int): Vector3i {
        x -= xyz
        y -= xyz
        z -= xyz
        return this
    }

    // Mul
    fun neg(): Vector3i {
        x = -x
        y = -y
        z = -z
        return this
    }

    fun mul(`val`: Int): Vector3i {
        x *= `val`
        y *= `val`
        z *= `val`
        return this
    }

    fun div(`val`: Int): Vector3i {
        x /= `val`
        y /= `val`
        z /= `val`
        return this
    }

    fun set(x: Int, y: Int, z: Int): Vector3i {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    fun set(x: Double, y: Double, z: Double): Vector3i {
        this.x = x.toInt()
        this.y = y.toInt()
        this.z = z.toInt()
        return this
    }

    fun set(x: Float, y: Float, z: Float): Vector3i {
        this.x = x.toInt()
        this.y = y.toInt()
        this.z = z.toInt()
        return this
    }

    fun set(`val`: Int): Vector3i {
        x = `val`
        y = `val`
        z = `val`
        return this
    }

    fun set(`val`: Vector3i): Vector3i {
        x = `val`.x
        y = `val`.y
        z = `val`.z
        return this
    }

    fun set(vec: Vector3f): Vector3i {
        x = vec.x.toInt()
        y = vec.y.toInt()
        z = vec.z.toInt()
        return this
    }

    fun dot(vec: Vector3i): Int = x * vec.x + y * vec.y + z * vec.z

    fun toFloat(): Vector3f = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())

    fun toBlock(): BlockPos = BlockPos(x, y, z)

    fun toBlock(bp: BlockPos.MutableBlockPos) {
        bp.set(x, y, z)
    }

    fun getChunkLocalPos(): Int {
        val lx = x and 15
        val ly = y and 255
        val lz = z and 15
        return (ly shl 8) or (lz shl 4) or lx
    }

    fun writeTo(bb: ByteBuf) {
        bb.writeInt(x)
        bb.writeInt(y)
        bb.writeInt(z)
    }
}
