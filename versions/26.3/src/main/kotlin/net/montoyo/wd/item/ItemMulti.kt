/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item

import net.minecraft.world.item.Item
import java.util.BitSet

open class ItemMulti(cls: Class<out Enum<*>>, properties: Properties) : Item(properties) {
    @JvmField
    protected val values: Array<out Enum<*>> = cls.enumConstants

    @JvmField
    protected val creativeTabItems: BitSet = BitSet(values.size).apply { set(0, values.size) }

    fun getEnumValues(): Array<out Enum<*>> = values
}
