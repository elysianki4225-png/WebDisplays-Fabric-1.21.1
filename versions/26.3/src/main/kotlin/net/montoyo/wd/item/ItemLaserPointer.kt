/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.client.ScreenInputController

class ItemLaserPointer(properties: Properties) : Item(properties.stacksTo(1)), WDItem {
    override fun getWikiName(isStack: ItemStack): String = isStack.hoverName.string

    companion object {
        @Environment(EnvType.CLIENT)
        @JvmStatic
        fun tick(mc: Minecraft) {
            ScreenInputController.tick(mc)
        }

        @Environment(EnvType.CLIENT)
        @JvmStatic
        fun deselect(mc: Minecraft) {
            ScreenInputController.releaseAll(mc)
        }

        @Environment(EnvType.CLIENT)
        @JvmStatic
        fun isOn(): Boolean = ScreenInputController.isAnyButtonPressed()
    }
}
