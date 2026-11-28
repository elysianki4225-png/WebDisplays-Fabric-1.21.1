package net.montoyo.wd.block.item

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.montoyo.wd.block.BlockKeyboardLeft
import net.montoyo.wd.init.BlockInit

class KeyboardItem(block: Block, properties: Properties) : BlockItem(block, properties) {
    override fun placeBlock(context: BlockPlaceContext, state: BlockState): Boolean {
        val facing = context.horizontalDirection
        val leftState = state.setValue(BlockKeyboardLeft.FACING, facing)
        val d = BlockKeyboardLeft.mapDirection(facing)

        if (isValid(context.clickedPos, context.level, leftState, d)) {
            val kbRight = BlockInit.blockKbRight.get()
            val rightState = kbRight.defaultBlockState().setValue(BlockKeyboardLeft.FACING, facing)
            if (!context.level.setBlock(context.clickedPos.relative(d), rightState, 11)) return false
            return context.level.setBlock(context.clickedPos, leftState, 11)
        } else if (isValid(context.clickedPos.relative(d.opposite, 2), context.level, leftState, d)) {
            val kbRight = BlockInit.blockKbRight.get()
            val rightState = kbRight.defaultBlockState().setValue(BlockKeyboardLeft.FACING, facing)
            if (!context.level.setBlock(context.clickedPos, rightState, 11)) return false
            return context.level.setBlock(context.clickedPos.relative(d.opposite), leftState, 11)
        }
        return false
    }

    private fun isValid(pos: BlockPos, level: Level, state: BlockState, d: Direction): Boolean =
        level.getBlockState(pos.relative(d)).isAir
}
