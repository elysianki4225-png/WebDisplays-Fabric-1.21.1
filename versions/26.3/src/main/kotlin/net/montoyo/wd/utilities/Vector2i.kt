/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

import io.netty.buffer.ByteBuf

class Vector2i {
    @JvmField
    var x: Int

    @JvmField
    var y: Int

    constructor() {
        x = 0
        y = 0
    }

    constructor(`val`: Int) {
        x = `val`
        y = `val`
    }

    constructor(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    constructor(bb: ByteBuf) {
        x = bb.readInt()
        y = bb.readInt()
    }

    fun writeTo(bb: ByteBuf) {
        bb.writeInt(x)
        bb.writeInt(y)
    }
}
