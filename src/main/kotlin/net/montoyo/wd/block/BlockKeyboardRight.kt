/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.block

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.montoyo.wd.core.IPeripheral
import net.montoyo.wd.item.ItemLinker
import net.montoyo.wd.net.WDDistributor
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.client_bound.S2CMessageCloseGui
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Vector3i

// TODO: merge into KeyboardLeft
class BlockKeyboardRight : Block(Properties.ofFullCopy(Blocks.STONE).strength(1.5f, 10.0f)), IPeripheral {
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!level.isClientSide) {
            remove(state, level, pos, false, false)
        }
        super.onRemove(state, level, pos, newState, moved)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun isCollisionShapeFullBlock(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = false

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        BlockKeyboardLeft.KEYBOARD_AABBS[state.getValue(FACING).ordinal - 2]

    override fun connect(world: Level, pos: BlockPos, state: BlockState, scrPos: Vector3i, scrSide: BlockSide): Boolean {
        val keyboard = BlockKeyboardLeft.getTileEntity(state, world, pos)
        return keyboard != null && keyboard.connect(world, pos, state, scrPos, scrSide)
    }

    override fun entityInside(state: BlockState, world: Level, pos: BlockPos, entity: Entity) {
        val rpos = (entity.y - pos.y.toDouble()) * 16.0
        if (!world.isClientSide && rpos in 1.0..2.0 && Math.random() < 0.25) {
            BlockKeyboardLeft.getTileEntity(state, world, pos)?.simulateCat(entity)
        }
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult = handleUse(state, level, pos, player, InteractionHand.MAIN_HAND, hit)

    override fun useItemOn(
        heldItem: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult {
        if (heldItem.isEmpty) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        return BlockPeripheral.toItemInteractionResult(handleUse(state, level, pos, player, hand, hit))
    }

    private fun handleUse(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        if (player.getItemInHand(hand).item is ItemLinker) return InteractionResult.PASS
        val tek = BlockKeyboardLeft.getTileEntity(state, level, pos)
        return tek?.onRightClick(player, hand) ?: InteractionResult.PASS
    }

    override fun getOcclusionShape(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape =
        Shapes.empty()

    companion object {
        @JvmField
        val FACING: DirectionProperty = BlockKeyboardLeft.FACING

        private fun removeLeftPiece(state: BlockState, world: Level, pos: BlockPos) {
            val relative = pos.relative(BlockKeyboardLeft.mapDirection(state.getValue(FACING).opposite))
            val ns = world.getBlockState(relative)
            if (ns.block is BlockKeyboardLeft) {
                world.setBlock(relative, Blocks.AIR.defaultBlockState(), 3)
            }
        }

        @JvmStatic
        fun remove(state: BlockState, world: Level, pos: BlockPos, setState: Boolean, drop: Boolean) {
            removeLeftPiece(state, world, pos)
            if (setState) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)
            }
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.NEAR.with { BlockPeripheral.point(world, pos) },
                S2CMessageCloseGui(pos)
            )
        }
    }
}
