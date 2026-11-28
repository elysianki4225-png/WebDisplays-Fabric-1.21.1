/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.block

import com.mojang.serialization.MapCodec
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.phys.BlockHitResult
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.config.CommonConfig
import net.montoyo.wd.core.DefaultUpgrade
import net.montoyo.wd.core.IUpgrade
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.data.SetURLData
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.init.BlockInit
import net.montoyo.wd.item.ItemLaserPointer
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.Multiblock
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector2i
import net.montoyo.wd.utilities.Vector3f
import net.montoyo.wd.utilities.Vector3i

class BlockScreen(properties: Properties) :
    BaseEntityBlock(properties.strength(1.5f, 10.0f).pushReaction(PushReaction.IGNORE)) {

    init {
        registerDefaultState(defaultBlockState().setValue(hasTE, false).setValue(emitting, false))
    }

    override fun codec(): MapCodec<BlockScreen> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(*Companion.properties)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? =
        super.getStateForPlacement(context)

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        level: LevelAccessor,
        currentPos: BlockPos,
        neighborPos: BlockPos
    ): BlockState = super.updateShape(state, direction, neighborState, level, currentPos, neighborPos)

    fun getMetaFromState(state: BlockState): Int {
        var ret = 0
        if (state.getValue(hasTE)) ret = ret or 1
        if (state.getValue(emitting)) ret = ret or 2
        return ret
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (newState.block == state.block) return

        for (value in BlockSide.values()) {
            val vec = Vector3i(pos.x, pos.y, pos.z)
            Multiblock.findOrigin(level, vec, value, null)
            val bp = BlockPos(vec.x, vec.y, vec.z)
            if (bp != pos) {
                level.removeBlockEntity(bp)
                level.setBlock(bp, level.getBlockState(bp).setValue(hasTE, false), 11)
            }
        }

        super.onRemove(state, level, pos, newState, moved)
    }

    override fun useWithoutItem(
        state: BlockState,
        world: Level,
        position: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult = useInternal(state, world, position, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, hit)

    override fun useItemOn(
        heldItem: ItemStack,
        state: BlockState,
        world: Level,
        position: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult {
        if (heldItem.isEmpty) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        return BlockPeripheral.toItemInteractionResult(useInternal(state, world, position, player, hand, heldItem, hit))
    }

    private fun useInternal(
        state: BlockState,
        world: Level,
        position: BlockPos,
        player: Player,
        hand: InteractionHand,
        heldItemArg: ItemStack,
        hit: BlockHitResult
    ): InteractionResult {
        var heldItem: ItemStack? = heldItemArg
        var isUpgrade = false
        if (heldItem!!.isEmpty) {
            heldItem = null
        } else if (heldItem.item is IUpgrade) {
            isUpgrade = true
        } else {
            return InteractionResult.FAIL
        }

        if (heldItem != null && heldItem.item is ItemLaserPointer) return InteractionResult.FAIL

        if (!isUpgrade && hand == InteractionHand.OFF_HAND) return InteractionResult.FAIL
        if (world.isClientSide) return InteractionResult.FAIL

        val sneaking = player.isShiftKeyDown
        val pos = Vector3i(position)
        val side = BlockSide.values()[hit.direction.ordinal]

        Multiblock.findOrigin(world, pos, side, null)
        var te = world.getBlockEntity(pos.toBlock()) as? TileEntityScreen
        val scr = te?.getScreen(side)

        if (te != null && scr != null) {

            if (sneaking) {
                if ((scr.rightsFor(player) and ScreenRights.CHANGE_URL) == 0) {
                    Util.toast(player, "restrictions")
                } else {
                    SetURLData(pos, scr.side, scr.url).sendTo(player as ServerPlayer)
                }
                return InteractionResult.SUCCESS
            } else if (heldItem != null) {
                if (!te.hasUpgrade(side, heldItem)) {
                    if ((scr.rightsFor(player) and ScreenRights.MANAGE_UPGRADES) == 0) {
                        Util.toast(player, "restrictions")
                        return InteractionResult.CONSUME
                    }

                    if (te.addUpgrade(side, heldItem, player, false)) {
                        if (!player.isCreative) heldItem.shrink(1)
                        Util.toast(player, ChatFormatting.AQUA, "upgradeOk")
                        if (player is ServerPlayer) {
                            WebDisplays.INSTANCE.criterionUpgradeScreen?.trigger(player.advancements)
                        }
                    } else {
                        Util.toast(player, "upgradeError")
                    }
                    return InteractionResult.CONSUME
                }
            } else {
                if ((scr.rightsFor(player) and ScreenRights.INTERACT) == 0) {
                    Util.toast(player, "restrictions")
                    return InteractionResult.CONSUME
                }

                val hitX = hit.location.x.toFloat() - pos.x.toFloat()
                val hitY = hit.location.y.toFloat() - pos.y.toFloat()
                val hitZ = hit.location.z.toFloat() - pos.z.toFloat()
                val tmp = Vector2i()

                if (hit2pixels(side, hit.blockPos, Vector3i(hit.blockPos), scr, hitX, hitY, hitZ, tmp)) {
                    te.click(side, tmp)
                }
                return InteractionResult.CONSUME
            }
        } else if (sneaking) {
            Util.toast(player, "turnOn")
            return InteractionResult.SUCCESS
        } else if (heldItem != null) {
            Util.toast(player, "turnOn")
            return InteractionResult.SUCCESS
        }

        val size = Multiblock.measure(world, pos, side)
        if (size.x < 2 || size.y < 2) {
            Util.toast(player, "tooSmall")
            return InteractionResult.SUCCESS
        }
        if (size.x > CommonConfig.Screen.maxScreenSizeX || size.y > CommonConfig.Screen.maxScreenSizeY) {
            Util.toast(player, "tooBig", CommonConfig.Screen.maxScreenSizeX, CommonConfig.Screen.maxScreenSizeY)
            return InteractionResult.SUCCESS
        }

        val err = Multiblock.check(world, pos, size, side)
        if (err != null) {
            Util.toast(player, "invalid", err.toString())
            return InteractionResult.SUCCESS
        }

        Log.info(
            "Player %s (UUID %s) created a screen at %s of size %dx%d",
            player.name,
            player.gameProfile.id.toString(),
            pos.toString(),
            size.x,
            size.y
        )

        if (te == null) {
            val bp = pos.toBlock()
            world.setBlockAndUpdate(bp, world.getBlockState(bp).setValue(hasTE, true))
            te = world.getBlockEntity(bp) as? TileEntityScreen
        }

        te?.addScreen(side, size, null, player, true)
        return InteractionResult.SUCCESS
    }

    override fun neighborChanged(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        block: Block,
        source: BlockPos,
        isMoving: Boolean
    ) {
        if (block != this && !world.isClientSide && !state.getValue(emitting)) {
            for (side in BlockSide.values()) {
                val vec = Vector3i(pos)
                Multiblock.findOrigin(world, vec, side, null)

                val tes = world.getBlockEntity(vec.toBlock()) as? TileEntityScreen
                if (tes != null && tes.hasUpgrade(side, DefaultUpgrade.REDINPUT)) {
                    vec.sub(pos.x, pos.y, pos.z).neg()
                }
            }
        }
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        val meta = getMetaFromState(state)
        return if ((meta and 1) == 0) null else TileEntityScreen(pos, state)
    }

    private fun onDestroy(world: Level, pos: BlockPos, ply: Player?) {
        if (!world.isClientSide) {
            val bp = Vector3i(pos)
            val override = Multiblock.BlockOverride(bp, Multiblock.OverrideAction.SIMULATE)

            for (bs in BlockSide.values()) {
                destroySide(world, bp.clone(), bs, override, ply)
            }
        }
    }

    private fun destroySide(
        world: Level,
        pos: Vector3i,
        side: BlockSide,
        override: Multiblock.BlockOverride,
        source: Player?
    ) {
        Multiblock.findOrigin(world, pos, side, override)
        val bp = pos.toBlock()
        val te = world.getBlockEntity(bp)

        if (te is TileEntityScreen) {
            te.onDestroy(source)
            world.setBlock(bp, world.getBlockState(bp).setValue(hasTE, false), Block.UPDATE_ALL_IMMEDIATE)
        }
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        onDestroy(level, pos, player)
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun setPlacedBy(
        world: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack
    ) {
        if (world.isClientSide) return

        val override = Multiblock.BlockOverride(Vector3i(pos), Multiblock.OverrideAction.IGNORE)
        val neighbors = arrayOf(
            Vector3i(pos.x + 1, pos.y, pos.z),
            Vector3i(pos.x - 1, pos.y, pos.z),
            Vector3i(pos.x, pos.y + 1, pos.z),
            Vector3i(pos.x, pos.y - 1, pos.z),
            Vector3i(pos.x, pos.y, pos.z + 1),
            Vector3i(pos.x, pos.y, pos.z - 1)
        )

        for (neighbor in neighbors) {
            if (world.getBlockState(neighbor.toBlock()).block is BlockScreen) {
                for (bs in BlockSide.values()) {
                    destroySide(world, neighbor.clone(), bs, override, placer as? Player)
                }
            }
        }
    }

    override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
        if (state.getValue(emitting)) 15 else 0

    override fun isSignalSource(state: BlockState): Boolean = state.getValue(emitting)

    companion object {
        @JvmField
        val CODEC: MapCodec<BlockScreen> = simpleCodec(::BlockScreen)

        @JvmField
        val hasTE: BooleanProperty = BooleanProperty.create("haste")

        @JvmField
        val emitting: BooleanProperty = BooleanProperty.create("emitting")

        private val properties: Array<Property<*>> = arrayOf(hasTE, emitting)

        @JvmStatic
        fun isntScreenBlock(world: Level, pos: Vector3i): Boolean =
            world.getBlockState(pos.toBlock()).block != BlockInit.blockScreen.get()

        @JvmStatic
        fun hit2pixels(
            side: BlockSide,
            bpos: BlockPos,
            pos: Vector3i,
            scr: TileEntityScreen.Screen,
            hitXArg: Float,
            hitY: Float,
            hitZArg: Float,
            dst: Vector2i
        ): Boolean {
            var hitX = hitXArg
            var hitZ = hitZArg
            if (side.right.x < 0) hitX -= 1.0f
            if (side.right.z < 0 || side == BlockSide.TOP || side == BlockSide.BOTTOM) hitZ -= 1.0f

            val rel = Vector3f(bpos.x.toFloat(), bpos.y.toFloat(), bpos.z.toFloat())
            rel.sub(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
            rel.add(hitX, hitY, hitZ)

            var cx = rel.dot(side.right.toFloat()) - 2.0f / 16.0f
            var cy = rel.dot(side.up.toFloat()) - 2.0f / 16.0f
            val sw = scr.size.x.toFloat() - 4.0f / 16.0f
            val sh = scr.size.y.toFloat() - 4.0f / 16.0f

            cx /= sw
            cy /= sh

            if (cx in 0.0f..1.0f && cy in 0.0f..1.0f) {
                if (side != BlockSide.BOTTOM) cy = 1.0f - cy

                when (scr.rotation) {
                    net.montoyo.wd.utilities.Rotation.ROT_90 -> cy = 1.0f - cy
                    net.montoyo.wd.utilities.Rotation.ROT_180 -> {
                        cx = 1.0f - cx
                        cy = 1.0f - cy
                    }
                    net.montoyo.wd.utilities.Rotation.ROT_270 -> cx = 1.0f - cx
                    else -> {}
                }

                cx *= scr.resolution.x.toFloat()
                cy *= scr.resolution.y.toFloat()

                if (scr.rotation.isVertical) {
                    dst.x = cy.toInt()
                    dst.y = cx.toInt()
                } else {
                    dst.x = cx.toInt()
                    dst.y = cy.toInt()
                }

                return true
            }

            return false
        }
    }
}
