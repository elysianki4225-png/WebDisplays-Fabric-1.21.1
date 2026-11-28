/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Vector3i

interface IPeripheral {
    fun connect(
        world: Level,
        blockPos: BlockPos,
        blockState: BlockState,
        screenPos: Vector3i,
        screenSide: BlockSide
    ): Boolean
}
