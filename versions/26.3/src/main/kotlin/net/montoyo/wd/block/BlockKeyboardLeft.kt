/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.montoyo.wd.core.DefaultPeripheral
import net.montoyo.wd.entity.TileEntityKeyboard
import net.montoyo.wd.item.ItemLinker
import net.montoyo.wd.net.WDDistributor
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.client_bound.S2CMessageCloseGui

class BlockKeyboardLeft(properties: Properties) : BlockPeripheral(DefaultPeripheral.KEYBOARD, properties) {
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(*Companion.properties)
    }

    override fun stepOn(world: Level, pos: BlockPos, state: BlockState, entity: Entity) {
        if (!world.isClientSide() && Math.random() < 0.25) {
            getTileEntity(state, world, pos)?.simulateCat(entity)
        }
        super.stepOn(world, pos, state, entity)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult = handleKeyboardUse(state, level, pos, player, InteractionHand.MAIN_HAND, hit)

    override fun useItemOn(
        heldItem: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        if (heldItem.isEmpty) return InteractionResult.TRY_WITH_EMPTY_HAND
        return handleKeyboardUse(state, level, pos, player, hand, hit)
    }

    private fun handleKeyboardUse(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        if (player.getItemInHand(hand).item is ItemLinker) return InteractionResult.PASS
        val tek = getTileEntity(state, level, pos)
        return tek?.onRightClick(player, hand) ?: InteractionResult.PASS
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        KEYBOARD_AABBS[state.getValue(FACING).ordinal - 2]

    override fun getOcclusionShape(state: BlockState): VoxelShape = Shapes.empty()

    override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, moved: Boolean) {
        remove(state, level, pos, false, false)
        super.affectNeighborsAfterRemoval(state, level, pos, moved)
    }

    companion object {
        @JvmField
        val TYPE: EnumProperty<DefaultPeripheral> = EnumProperty.create("type", DefaultPeripheral::class.java)

        @JvmField
        val FACING: EnumProperty<Direction> = EnumProperty.create(
            "facing",
            Direction::class.java,
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
        )

        @JvmField
        val KEYBOARD_AABBS: Array<VoxelShape> = arrayOf(
            Shapes.box(0.0, 0.0, 3.0 / 16.0, 1.0, 1.0 / 16.0, 1.0),
            Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0 / 16.0, 13.0 / 16.0),
            Shapes.box(3.0 / 16.0, 0.0, 0.0, 1.0, 1.0 / 16.0, 1.0),
            Shapes.box(0.0, 0.0, 0.0, 13.0 / 16.0, 1.0 / 16.0, 1.0)
        )

        private val properties: Array<Property<*>> = arrayOf(TYPE, FACING)

        @JvmStatic
        fun getTileEntity(state: BlockState, world: Level, pos: BlockPos): TileEntityKeyboard? {
            if (state.block is BlockKeyboardLeft) {
                val te = world.getBlockEntity(pos)
                if (te is TileEntityKeyboard) return te
            }

            val relative = pos.relative(mapDirection(state.getValue(FACING).opposite))
            val ns = world.getBlockState(relative)

            if (ns.block is BlockPeripheral) {
                val te = world.getBlockEntity(relative)
                if (te is TileEntityKeyboard) return te
            }

            return null
        }

        @JvmStatic
        fun mapDirection(facing: Direction): Direction = when (facing) {
            Direction.NORTH -> Direction.EAST
            Direction.EAST -> Direction.SOUTH
            Direction.SOUTH -> Direction.WEST
            Direction.WEST -> Direction.NORTH
            else -> facing
        }

        private fun removeRightPiece(state: BlockState, world: Level, pos: BlockPos) {
            val relative = pos.relative(mapDirection(state.getValue(FACING)))
            val ns = world.getBlockState(relative)
            if (ns.block is BlockKeyboardRight) {
                world.setBlock(relative, Blocks.AIR.defaultBlockState(), 3)
            }
        }

        @JvmStatic
        fun remove(state: BlockState, world: Level, pos: BlockPos, setState: Boolean, drop: Boolean) {
            removeRightPiece(state, world, pos)
            if (setState) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)
            }
            WDNetworkRegistry.INSTANCE.send(WDDistributor.NEAR.with { point(world, pos) }, S2CMessageCloseGui(pos))
        }
    }
}
