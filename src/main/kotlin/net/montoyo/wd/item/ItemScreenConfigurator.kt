/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.montoyo.wd.block.BlockScreen
import net.montoyo.wd.data.ScreenConfigData
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Multiblock
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector3i

class ItemScreenConfigurator(properties: Properties) : Item(properties.stacksTo(1)), WDItem {
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player!!
        if (player.isShiftKeyDown || context.level.getBlockState(context.clickedPos).block !is BlockScreen) {
            return InteractionResult.PASS
        }

        if (context.level.isClientSide) return InteractionResult.SUCCESS

        val origin = Vector3i(context.clickedPos)
        val side = BlockSide.values()[context.horizontalDirection.opposite.ordinal]

        Multiblock.findOrigin(context.level, origin, side, null)
        val te = context.level.getBlockEntity(origin.toBlock())

        if (te !is TileEntityScreen) {
            Util.toast(player, "turnOn")
            return InteractionResult.SUCCESS
        }

        val scr = te.getScreen(side)
        if (scr == null) {
            Util.toast(player, "turnOn")
        } else {
            ScreenConfigData(origin, side, scr).sendTo(player as ServerPlayer)
        }

        return InteractionResult.SUCCESS
    }

    override fun getWikiName(isStack: ItemStack): String = isStack.item.getName(isStack).string
}
