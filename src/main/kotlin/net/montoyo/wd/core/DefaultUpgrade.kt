/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

import net.minecraft.world.item.ItemStack
import net.montoyo.wd.item.ItemUpgrade

enum class DefaultUpgrade(
    private val upgradeName: String,
    @JvmField val wikiName: String
) {
    LASERMOUSE("lasermouse", "LaserMouse"),
    REDINPUT("redinput", "RedInput"),
    REDOUTPUT("redoutput", "RedOutput"),
    GPS("gps", "GPS");

    override fun toString(): String = upgradeName

    fun matchesLaserMouse(stack: ItemStack): Boolean = matches(stack, LASERMOUSE)

    fun matchesRedInput(stack: ItemStack): Boolean = matches(stack, REDINPUT)

    fun matchesRedOutput(stack: ItemStack): Boolean = matches(stack, REDOUTPUT)

    fun matchesGps(stack: ItemStack): Boolean = matches(stack, GPS)

    companion object {
        @JvmStatic
        protected fun matches(stack: ItemStack, upgrade: DefaultUpgrade): Boolean {
            val item = stack.item
            return item is ItemUpgrade && item.type == upgrade
        }
    }
}
