/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.utilities.BlockSide

interface IUpgrade {
    fun onInstall(tes: TileEntityScreen, screenSide: BlockSide, player: Player?, stack: ItemStack)
    fun onRemove(tes: TileEntityScreen, screenSide: BlockSide, player: Player?, stack: ItemStack): Boolean // Return true to prevent dropping
    fun isSameUpgrade(myStack: ItemStack, otherStack: ItemStack): Boolean // myStack.getItem() is an instance of this class
    fun getJSName(stack: ItemStack): String // modname:upgradename
}
