/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.montoyo.wd.core.DefaultPeripheral;
import net.montoyo.wd.entity.TileEntityInterfaceBase;
import net.montoyo.wd.entity.TileEntityPeripheralBase;
import net.montoyo.wd.entity.TileEntityServer;
import net.montoyo.wd.item.ItemLinker;
import net.montoyo.wd.net.WDDistributor;
import net.montoyo.wd.net.WDNetworkRegistry;
import net.montoyo.wd.net.client_bound.S2CMessageCloseGui;
import net.montoyo.wd.utilities.Log;
import org.jetbrains.annotations.Nullable;

public class BlockPeripheral extends WDBlockContainer {
    DefaultPeripheral type;

    public BlockPeripheral(DefaultPeripheral type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        BlockEntityType.BlockEntitySupplier<? extends BlockEntity> cls = type.getTEClass();
        if (cls == null)
            return null;

        try {
            return cls.create(pos, state);
        } catch (Throwable t) {
            Log.errorEx("Couldn't instantiate peripheral TileEntity:", t);
        }

        return null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        return handleUse(state, world, pos, player, InteractionHand.MAIN_HAND, hit);
    }

    @Override
    public InteractionResult useItemOn(ItemStack heldItem, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (heldItem.isEmpty())
            return InteractionResult.TRY_WITH_EMPTY_HAND;

        return handleUse(state, world, pos, player, hand, hit);
    }

    private InteractionResult handleUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown())
            return InteractionResult.FAIL;

        if (player.getItemInHand(hand).getItem() instanceof ItemLinker)
            return InteractionResult.FAIL;

        BlockEntity te = world.getBlockEntity(pos);

        if (te instanceof TileEntityPeripheralBase)
            return ((TileEntityPeripheralBase) te).onRightClick(player, hand);
        else if (te instanceof TileEntityServer) {
            ((TileEntityServer) te).onPlayerRightClick(player);
            return InteractionResult.SUCCESS;
        } else
            return InteractionResult.FAIL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (world.isClientSide())
            return;

        if (placer instanceof Player) {
            BlockEntity te = world.getBlockEntity(pos);

            if (te instanceof TileEntityServer)
                ((TileEntityServer) te).setOwner((Player) placer);
            else if (te instanceof TileEntityInterfaceBase)
                ((TileEntityInterfaceBase) te).setOwner((Player) placer);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, net.minecraft.world.level.block.Block neighborType, @Nullable Orientation orientation, boolean isMoving) {
        BlockEntity te = world.getBlockEntity(pos);
        if (te instanceof TileEntityPeripheralBase)
            ((TileEntityPeripheralBase) te).onNeighborChange(neighborType, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        WDNetworkRegistry.INSTANCE.send(WDDistributor.NEAR.with(() -> point(level, pos)), new S2CMessageCloseGui(pos));
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    public static WDDistributor.TargetPoint point(Player exclude, Level world, BlockPos bp) {
        return new WDDistributor.TargetPoint((ServerPlayer) exclude, bp.getX(), bp.getY(), bp.getZ(), 64.0, world.dimension());
    }

    public static WDDistributor.TargetPoint point(Level world, BlockPos bp) {
        return new WDDistributor.TargetPoint(bp.getX(), bp.getY(), bp.getZ(), 64.0, world.dimension());
    }
}
