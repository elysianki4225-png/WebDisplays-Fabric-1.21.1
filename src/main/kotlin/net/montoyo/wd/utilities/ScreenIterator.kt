/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

import net.minecraft.core.BlockPos

class ScreenIterator(
    pos: BlockPos,
    private val side: BlockSide,
    private val size: Vector2i
) : Iterator<BlockPos> {
    private val vec1 = Vector3i(pos)
    private val vec2 = vec1.clone()
    private val blockPos = BlockPos.MutableBlockPos()
    private var x = 0
    private var y = 0
    private var hasNext = true

    override fun hasNext(): Boolean = hasNext

    override fun next(): BlockPos {
        vec2.toBlock(blockPos)

        if (++x >= size.x) {
            if (++y >= size.y) {
                hasNext = false
            } else {
                x = 0
                vec2.set(vec1.add(side.up))
            }
        } else {
            vec2.add(side.right)
        }

        return blockPos
    }

    fun getX(): Int = x

    fun getY(): Int = y

    fun getIndex(): Int = y * size.x + x
}
