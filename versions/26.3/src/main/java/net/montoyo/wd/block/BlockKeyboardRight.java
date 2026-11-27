/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.montoyo.wd.core.IPeripheral;
import net.montoyo.wd.entity.TileEntityKeyboard;
import net.montoyo.wd.item.ItemLinker;
import net.montoyo.wd.net.WDDistributor;
import net.montoyo.wd.net.WDNetworkRegistry;
import net.montoyo.wd.net.client_bound.S2CMessageCloseGui;
import net.montoyo.wd.utilities.BlockSide;
import net.montoyo.wd.utilities.Vector3i;
import org.jetbrains.annotations.NotNull;

import static net.montoyo.wd.block.BlockKeyboardLeft.KEYBOARD_AABBS;
import static net.montoyo.wd.block.BlockPeripheral.point;

// TODO: merge into KeyboardLeft
public class BlockKeyboardRight extends Block implements IPeripheral {

    public static final EnumProperty<Direction> FACING = BlockKeyboardLeft.FACING;

    public BlockKeyboardRight(Properties properties) {
        super(properties);
    }

    private static void removeLeftPiece(BlockState state, Level world, BlockPos pos) {
        BlockPos relative = pos.relative(BlockKeyboardLeft.mapDirection(state.getValue(FACING).getOpposite()));

        BlockState ns = world.getBlockState(relative);
        if (ns.getBlock() instanceof BlockKeyboardLeft)
            world.setBlock(relative, Blocks.AIR.defaultBlockState(), 3);
    }

    public static void remove(BlockState state, Level world, BlockPos pos, boolean setState, boolean drop) {
        removeLeftPiece(state, world, pos);
        if (setState)
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        WDNetworkRegistry.INSTANCE.send(WDDistributor.NEAR.with(() -> point(world, pos)), new S2CMessageCloseGui(pos));
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        remove(state, level, pos, false, false);
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return KEYBOARD_AABBS[state.getValue(FACING).ordinal() - 2];
    }

    @Override
    public boolean connect(Level world, BlockPos pos, BlockState state, Vector3i scrPos, BlockSide scrSide) {
        TileEntityKeyboard keyboard = BlockKeyboardLeft.getTileEntity(state, world, pos);
        return keyboard != null && keyboard.connect(world, pos, state, scrPos, scrSide);
    }

    @Override
    public void stepOn(Level world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClientSide() && Math.random() < 0.25) {
            TileEntityKeyboard tek = BlockKeyboardLeft.getTileEntity(state, world, pos);
            if (tek != null)
                tek.simulateCat(entity);
        }
        super.stepOn(world, pos, state, entity);
    }

    @Override
    public @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return handleUse(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
    }

    @Override
    public @NotNull InteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (heldItem.isEmpty())
            return InteractionResult.TRY_WITH_EMPTY_HAND;

        return handleUse(state, level, pos, player, hand, hit);
    }

    private @NotNull InteractionResult handleUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof ItemLinker)
            return InteractionResult.PASS;

        TileEntityKeyboard tek = BlockKeyboardLeft.getTileEntity(state, level, pos);
        if (tek != null)
            return tek.onRightClick(player, hand);

        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }
}
