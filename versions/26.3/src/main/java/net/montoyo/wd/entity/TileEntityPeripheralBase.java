/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.montoyo.wd.core.IPeripheral;
import net.montoyo.wd.utilities.BlockSide;
import net.montoyo.wd.utilities.Log;
import net.montoyo.wd.utilities.Vector3i;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

public abstract class TileEntityPeripheralBase extends BlockEntity implements IPeripheral {

    protected Vector3i screenPos;
    protected BlockSide screenSide;

    public TileEntityPeripheralBase(BlockEntityType<?> arg, BlockPos arg2, BlockState arg3) {
        super(arg, arg2, arg3);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        ValueInput scr = input.child("WDScreen").orElse(null);
        if (scr != null) {
            screenPos = new Vector3i(scr.getIntOr("X", 0), scr.getIntOr("Y", 0), scr.getIntOr("Z", 0));
            screenSide = BlockSide.values()[scr.getByteOr("Side", (byte) 0)];
        } else {
            screenPos = null;
            screenSide = null;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        if (screenPos != null && screenSide != null) {
            ValueOutput scr = output.child("WDScreen");
            scr.putInt("X", screenPos.x);
            scr.putInt("Y", screenPos.y);
            scr.putInt("Z", screenPos.z);
            scr.putByte("Side", (byte) screenSide.ordinal());
        }
    }
    
    @Override
    public boolean connect(Level world_, BlockPos blockPos, BlockState blockState, Vector3i pos, BlockSide side) {
        BlockEntity te = world_.getBlockEntity(pos.toBlock());
        if(te == null || !(te instanceof TileEntityScreen)) {
            Log.error("TileEntityPeripheralBase.connect(): Tile entity at %s is not a screen!", pos.toString());
            return false;
        }

        if(((TileEntityScreen) te).getScreen(side) == null) {
            Log.error("TileEntityPeripheralBase.connect(): There is no screen at %s on side %s!", pos.toString(), side.toString());
            return false;
        }

        screenPos = pos;
        screenSide = side;
        setChanged();
        return true;
    }

    public boolean isLinked() {
        return screenPos != null && screenSide != null;
    }

    public boolean isScreenChunkLoaded() {
        if (screenPos == null || screenSide == null)
            return true;

        LevelChunk chunk = Objects.requireNonNull(getLevel()).getChunkSource().getChunk(screenPos.x >> 4, screenPos.z >> 4, true);
        return chunk != null && !chunk.isEmpty();
    }

    @Nullable
    public TileEntityScreen getConnectedScreen() {
        if(screenPos == null || screenSide == null)
            return null;

        BlockEntity te = level.getBlockEntity(screenPos.toBlock());
        if(te == null || !(te instanceof TileEntityScreen) || ((TileEntityScreen) te).getScreen(screenSide) == null) {
            screenPos = null;
            screenSide = null;
            setChanged();
            return null;
        }

        return (TileEntityScreen) te;
    }

    @Nullable
    public TileEntityScreen getConnectedScreenEx() {
        if(screenPos == null || screenSide == null)
            return null;

        BlockEntity te = level.getBlockEntity(screenPos.toBlock());
        if(te == null || !(te instanceof TileEntityScreen) || ((TileEntityScreen) te).getScreen(screenSide) == null)
            return null;

        return (TileEntityScreen) te;
    }

    @Nullable
    public Vector3i getScreenPos() {
        return screenPos;
    }

    @Nullable
    public BlockSide getScreenSide() {
        return screenSide;
    }

    public void onNeighborChange(Block neighborType, BlockPos neighborPos) {
    }

    public InteractionResult onRightClick(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }
}
