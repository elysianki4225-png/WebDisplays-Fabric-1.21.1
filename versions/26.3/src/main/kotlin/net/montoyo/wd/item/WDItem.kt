/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item

import net.minecraft.ChatFormatting
import net.minecraft.client.resources.language.I18n
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.WebDisplays

interface WDItem {
    fun getWikiName(isStack: ItemStack): String?

    companion object {
        @JvmStatic
        fun addInformation(tt: MutableList<String>?) {
            if (tt != null && WebDisplays.PROXY.isShiftDown()) {
                tt.add("${ChatFormatting.GRAY}${I18n.get("item.webdisplays.wiki")}")
            }
        }
    }
}
