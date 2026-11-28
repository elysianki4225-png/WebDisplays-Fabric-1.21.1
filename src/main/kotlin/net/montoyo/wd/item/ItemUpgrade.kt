/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.core.DefaultUpgrade
import net.montoyo.wd.core.IUpgrade
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.utilities.BlockSide

class ItemUpgrade(
    @JvmField val type: DefaultUpgrade
) : ItemMulti(DefaultUpgrade::class.java, Properties()), IUpgrade, WDItem {
    override fun onInstall(tes: TileEntityScreen, screenSide: BlockSide, player: Player?, isStack: ItemStack) {
    }

    override fun onRemove(tes: TileEntityScreen, screenSide: BlockSide, player: Player?, isStack: ItemStack): Boolean {
        if (DefaultUpgrade.LASERMOUSE.matchesLaserMouse(isStack)) {
            tes.clearLaserUser(screenSide)
        }
        return false
    }

    override fun isSameUpgrade(myStack: ItemStack, otherStack: ItemStack): Boolean {
        val item0 = myStack.item
        val item1 = otherStack.item
        return item0 is ItemUpgrade && item1 is ItemUpgrade && item0.type == item1.type
    }

    override fun getJSName(isStack: ItemStack): String {
        val item = isStack.item
        return if (item is ItemUpgrade) "webdisplays:${item.type}" else "webdisplays:null"
    }

    override fun getWikiName(isStack: ItemStack): String = isStack.item.getName(isStack).string
}
