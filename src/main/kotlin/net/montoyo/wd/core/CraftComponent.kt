/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

import net.minecraft.world.item.ItemStack
import net.montoyo.wd.init.ItemInit

enum class CraftComponent(private val componentName: String, private val wikiName: String) {
    STONEKEY("stonekey", "StoneKey"),
    UPGRADE("upgrade", "Upgrade"),
    PERIPHERAL("peripheral", "Peripheral"),
    BATCELL("batcell", "BatCell"),
    BATPACK("batpack", "BatPack"),
    LASERDIODE("laserdiode", "LaserDiode"),
    BACKLIGHT("backlight", "Backlight"),
    EXTCARD("extcard", "ExtCard"),
    BADEXTCARD("badextcard", "BadExtCard");

    override fun toString(): String = componentName

    fun makeItemStack(): ItemStack = ItemStack(ItemInit.getComputerCraftItem(ordinal).get(), 1)
}
