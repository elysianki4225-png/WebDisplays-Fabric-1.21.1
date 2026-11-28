/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.block

import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.BaseEntityBlock

abstract class WDBlockContainer(arg: Properties) : BaseEntityBlock(arg) {
    fun getItem(): BlockItem? = itemBlock

    companion object {
        @JvmField
        protected var itemBlock: BlockItem? = null
    }
}
