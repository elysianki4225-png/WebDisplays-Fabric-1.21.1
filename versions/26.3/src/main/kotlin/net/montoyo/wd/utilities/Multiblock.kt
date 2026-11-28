/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

import net.minecraft.core.BlockPos
import net.minecraft.world.level.LevelAccessor
import net.montoyo.wd.init.BlockInit

object Multiblock {
    enum class OverrideAction {
        NONE,
        SIMULATE,
        IGNORE
    }

    class BlockOverride(
        internal val pos: Vector3i?,
        internal val action: OverrideAction
    ) {
        fun apply(bp: Vector3i, originalResult: Boolean): Boolean {
            return when {
                action == OverrideAction.NONE || bp != pos -> originalResult
                action == OverrideAction.SIMULATE -> true
                else -> false
            }
        }
    }

    @JvmField
    val NULL_OVERRIDE = BlockOverride(null, OverrideAction.NONE)

    // Modifies pos
    @JvmStatic
    fun findOrigin(world: LevelAccessor, pos: Vector3i, side: BlockSide, override: BlockOverride?) {
        val ov = override ?: NULL_OVERRIDE
        val bp = BlockPos.MutableBlockPos()

        // Go left
        do {
            pos.add(side.left)
            pos.toBlock(bp)
        } while (ov.apply(pos, world.getBlockState(bp).block === BlockInit.blockScreen.get()))

        pos.add(side.right)

        // Go down
        do {
            pos.add(side.down)
            pos.toBlock(bp)
        } while (ov.apply(pos, world.getBlockState(bp).block === BlockInit.blockScreen.get()))

        pos.add(side.up)
    }

    // Origin stays constant
    @JvmStatic
    fun measure(world: LevelAccessor, origin: Vector3i, side: BlockSide): Vector2i {
        val ret = Vector2i()
        val pos = origin.clone()
        val bp = BlockPos.MutableBlockPos()
        pos.toBlock(bp)

        // Go up
        do {
            pos.add(side.up)
            pos.toBlock(bp)
            ret.y++
        } while (world.getBlockState(bp).block === BlockInit.blockScreen.get())

        pos.add(side.down)

        // Go right
        do {
            pos.add(side.right)
            pos.toBlock(bp)
            ret.x++
        } while (world.getBlockState(bp).block === BlockInit.blockScreen.get())

        return ret
    }

    // Origin and size stays constant.
    // Returns null if structure is okay, otherwise the erroring block pos.
    @JvmStatic
    fun check(world: LevelAccessor, origin: Vector3i, size: Vector2i, side: BlockSide): Vector3i? {
        val pos = origin.clone()
        val bp = BlockPos.MutableBlockPos()

        // Check inner
        for (y in 0 until size.y) {
            for (x in 0 until size.x) {
                pos.toBlock(bp)
                if (world.getBlockState(bp).block !== BlockInit.blockScreen.get()) return pos // Hole

                pos.add(side.forward)
                pos.toBlock(bp)
                if (world.getBlockState(bp).block === BlockInit.blockScreen.get()) return pos // Back should be empty

                pos.addMul(side.backward, 2)
                pos.toBlock(bp)
                if (world.getBlockState(bp).block === BlockInit.blockScreen.get()) return pos // Front should be empty

                pos.add(side.forward)
                pos.add(side.right)
            }

            pos.addMul(side.left, size.x)
            pos.add(side.up)
        }

        // Check left edge
        pos.set(origin)
        pos.add(side.left)
        for (y in 0 until size.y) {
            pos.toBlock(bp)
            if (world.getBlockState(bp).block === BlockInit.blockScreen.get()) return pos
            pos.add(side.up)
        }

        // Check right edge
        pos.set(origin)
        pos.addMul(side.right, size.x)
        for (y in 0 until size.y) {
            pos.toBlock(bp)
            if (world.getBlockState(bp).block === BlockInit.blockScreen.get()) return pos
            pos.add(side.up)
        }

        // Check bottom edge
        pos.set(origin)
        pos.add(side.down)
        for (x in 0 until size.x) {
            pos.toBlock(bp)
            if (world.getBlockState(bp).block === BlockInit.blockScreen.get()) return pos
            pos.add(side.right)
        }

        // Check top edge
        pos.set(origin)
        pos.addMul(side.up, size.y)
        for (x in 0 until size.x) {
            pos.toBlock(bp)
            if (world.getBlockState(bp).block === BlockInit.blockScreen.get()) return pos
            pos.add(side.right)
        }

        // All good.
        return null
    }
}
