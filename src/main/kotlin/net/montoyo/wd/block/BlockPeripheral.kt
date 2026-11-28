/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.PushReaction
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

open class BlockPeripheral protected constructor(
    @JvmField internal var type: DefaultPeripheral,
    properties: Properties
) : WDBlockContainer(properties) {

    private val peripheralCodec: MapCodec<BlockPeripheral> =
        simpleCodec { props -> BlockPeripheral(type, props) }

    constructor(type: DefaultPeripheral) : this(
        type,
        Properties.ofFullCopy(Blocks.STONE).strength(1.5f, 10.0f).pushReaction(PushReaction.IGNORE)
    )

    override fun codec(): MapCodec<out BlockPeripheral> = peripheralCodec

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
    ): ItemInteractionResult {
        if (heldItem.isEmpty) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        return toItemInteractionResult(handleUse(state, world, pos, player, hand, hit))
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
        if (world.isClientSide) return

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
        neighbor: BlockPos,
        isMoving: Boolean
    ) {
        val te = world.getBlockEntity(pos)
        if (te is TileEntityPeripheralBase) {
            te.onNeighborChange(neighborType, neighbor)
        }
    }

    override fun playerDestroy(
        world: Level,
        player: Player,
        pos: BlockPos,
        state: BlockState,
        blockEntity: BlockEntity?,
        tool: ItemStack
    ) {
        if (!world.isClientSide) {
            WDNetworkRegistry.INSTANCE.send(WDDistributor.NEAR.with { point(world, pos) }, S2CMessageCloseGui(pos))
        }
        super.playerDestroy(world, player, pos, state, blockEntity, tool)
    }

    @Suppress("DEPRECATION")
    override fun wasExploded(level: Level, pos: BlockPos, explosion: Explosion) {
        if (!level.isClientSide) {
            WDNetworkRegistry.INSTANCE.send(WDDistributor.NEAR.with { point(level, pos) }, S2CMessageCloseGui(pos))
        }
        super.wasExploded(level, pos, explosion)
    }

    companion object {
        @JvmStatic
        fun toItemInteractionResult(result: InteractionResult): ItemInteractionResult = when (result) {
            InteractionResult.SUCCESS, InteractionResult.SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS
            InteractionResult.CONSUME, InteractionResult.CONSUME_PARTIAL -> ItemInteractionResult.CONSUME
            InteractionResult.PASS -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            InteractionResult.FAIL -> ItemInteractionResult.FAIL
        }

        @JvmStatic
        fun point(exclude: Player, world: Level, bp: BlockPos): WDDistributor.TargetPoint =
            WDDistributor.TargetPoint(exclude as ServerPlayer, bp.x.toDouble(), bp.y.toDouble(), bp.z.toDouble(), 64.0, world.dimension())

        @JvmStatic
        fun point(world: Level, bp: BlockPos): WDDistributor.TargetPoint =
            WDDistributor.TargetPoint(bp.x.toDouble(), bp.y.toDouble(), bp.z.toDouble(), 64.0, world.dimension())
    }
}
