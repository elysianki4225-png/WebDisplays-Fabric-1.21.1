/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

class Vector3f : Cloneable {
    @JvmField
    var x: Float

    @JvmField
    var y: Float

    @JvmField
    var z: Float

    constructor() {
        x = 0.0f
        y = 0.0f
        z = 0.0f
    }

    constructor(`val`: Float) {
        x = `val`
        y = `val`
        z = `val`
    }

    constructor(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    public override fun clone(): Vector3f = Vector3f(x, y, z)

    override fun hashCode(): Int {
        return ((37 + java.lang.Float.floatToRawIntBits(x)) * 31 + java.lang.Float.floatToRawIntBits(y)) * 43 +
            java.lang.Float.floatToRawIntBits(z)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Vector3f) return false
        return other.x == x && other.y == y && other.z == z
    }

    // Add
    fun add(src: Vector3f): Vector3f {
        x += src.x
        y += src.y
        z += src.z
        return this
    }

    fun addMul(src: Vector3f, m: Float): Vector3f {
        x += src.x * m
        y += src.y * m
        z += src.z * m
        return this
    }

    fun add(x: Float, y: Float, z: Float): Vector3f {
        this.x += x
        this.y += y
        this.z += z
        return this
    }

    fun add(xyz: Float): Vector3f {
        x += xyz
        y += xyz
        z += xyz
        return this
    }

    // Sub
    fun sub(src: Vector3f): Vector3f {
        x -= src.x
        y -= src.y
        z -= src.z
        return this
    }

    fun sub(x: Float, y: Float, z: Float): Vector3f {
        this.x -= x
        this.y -= y
        this.z -= z
        return this
    }

    fun sub(xyz: Float): Vector3f {
        x -= xyz
        y -= xyz
        z -= xyz
        return this
    }

    // Mul
    fun neg(): Vector3f {
        x = -x
        y = -y
        z = -z
        return this
    }

    fun mul(`val`: Float): Vector3f {
        x *= `val`
        y *= `val`
        z *= `val`
        return this
    }

    fun div(`val`: Float): Vector3f {
        x /= `val`
        y /= `val`
        z /= `val`
        return this
    }

    fun set(x: Float, y: Float, z: Float): Vector3f {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    fun set(xyz: Float): Vector3f {
        x = xyz
        y = xyz
        z = xyz
        return this
    }

    fun dot(vec: Vector3f): Float = x * vec.x + y * vec.y + z * vec.z

    fun set(x: Double, y: Double, z: Double): Vector3f {
        this.x = x.toFloat()
        this.y = y.toFloat()
        this.z = z.toFloat()
        return this
    }

    fun set(x: Int, y: Int, z: Int): Vector3f {
        this.x = x.toFloat()
        this.y = y.toFloat()
        this.z = z.toFloat()
        return this
    }

    fun set(vec: Vector3f): Vector3f {
        x = vec.x
        y = vec.y
        z = vec.z
        return this
    }

    fun set(vec: Vector3i): Vector3f {
        x = vec.x.toFloat()
        y = vec.y.toFloat()
        z = vec.z.toFloat()
        return this
    }
}
