/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.block

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.montoyo.wd.core.DefaultPeripheral
import net.montoyo.wd.entity.TileEntityInterfaceBase
import net.montoyo.wd.entity.TileEntityPeripheralBase
import net.montoyo.wd.entity.TileEntityServer
import net.montoyo.wd.item.ItemLinker
import net.montoyo.wd.net.WDDistributor
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.client_bound.S2CMessageCloseGui
import net.montoyo.wd.utilities.Log

open class BlockPeripheral(
    @JvmField internal var type: DefaultPeripheral,
    properties: Properties
) : WDBlockContainer(properties) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        val cls = type.getTEClass() ?: return null
        return try {
            cls.create(pos, state)
        } catch (t: Throwable) {
            Log.errorEx("Couldn't instantiate peripheral TileEntity:", t)
            null
        }
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun useWithoutItem(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult = handleUse(state, world, pos, player, InteractionHand.MAIN_HAND, hit)

    override fun useItemOn(
        heldItem: ItemStack,
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        if (heldItem.isEmpty) return InteractionResult.TRY_WITH_EMPTY_HAND
        return handleUse(state, world, pos, player, hand, hit)
    }

    private fun handleUse(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        if (player.isShiftKeyDown) return InteractionResult.FAIL
        if (player.getItemInHand(hand).item is ItemLinker) return InteractionResult.FAIL

        return when (val te = world.getBlockEntity(pos)) {
            is TileEntityPeripheralBase -> te.onRightClick(player, hand)
            is TileEntityServer -> {
                te.onPlayerRightClick(player)
                InteractionResult.SUCCESS
            }
            else -> InteractionResult.FAIL
        }
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        Shapes.block()

    override fun setPlacedBy(
        world: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack
    ) {
        if (world.isClientSide()) return

        if (placer is Player) {
            when (val te = world.getBlockEntity(pos)) {
                is TileEntityServer -> te.setOwner(placer)
                is TileEntityInterfaceBase -> te.setOwner(placer)
            }
        }
    }

    override fun neighborChanged(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        neighborType: Block,
        orientation: Orientation?,
        isMoving: Boolean
    ) {
        val te = world.getBlockEntity(pos)
        if (te is TileEntityPeripheralBase) {
            te.onNeighborChange(neighborType, pos)
        }
    }

    override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, moved: Boolean) {
        WDNetworkRegistry.INSTANCE.send(WDDistributor.NEAR.with { point(level, pos) }, S2CMessageCloseGui(pos))
        super.affectNeighborsAfterRemoval(state, level, pos, moved)
    }

    companion object {
        @JvmStatic
        fun point(exclude: Player, world: Level, bp: BlockPos): WDDistributor.TargetPoint =
            WDDistributor.TargetPoint(exclude as ServerPlayer, bp.x.toDouble(), bp.y.toDouble(), bp.z.toDouble(), 64.0, world.dimension())

        @JvmStatic
        fun point(world: Level, bp: BlockPos): WDDistributor.TargetPoint =
            WDDistributor.TargetPoint(bp.x.toDouble(), bp.y.toDouble(), bp.z.toDouble(), 64.0, world.dimension())
    }
}
