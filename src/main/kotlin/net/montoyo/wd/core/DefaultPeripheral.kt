/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

import net.minecraft.util.StringRepresentable
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.montoyo.wd.entity.TileEntityKeyboard
import net.montoyo.wd.entity.TileEntityRCtrl
import net.montoyo.wd.entity.TileEntityRedCtrl
import net.montoyo.wd.entity.TileEntityServer
import net.montoyo.wd.init.BlockInit
import java.util.function.Supplier

enum class DefaultPeripheral(
    private val peripheralName: String,
    private val wikiName: String,
    private val teClass: BlockEntityType.BlockEntitySupplier<out BlockEntity>,
    private val bClass: Supplier<out Block>
) : StringRepresentable {
    KEYBOARD("keyboard", "Keyboard", BlockEntityType.BlockEntitySupplier(::TileEntityKeyboard), BlockInit.blockKeyBoard), // WITH FACING (< 3)
    REMOTE_CONTROLLER("remotectrl", "Remote_Controller", BlockEntityType.BlockEntitySupplier(::TileEntityRCtrl), BlockInit.blockRControl), // WITHOUT FACING (>= 3)
    REDSTONE_CONTROLLER("redstonectrl", "Redstone_Controller", BlockEntityType.BlockEntitySupplier(::TileEntityRedCtrl), BlockInit.blockRedControl),
    SERVER("server", "Server", BlockEntityType.BlockEntitySupplier(::TileEntityServer), BlockInit.blockServer);

    fun getBlockClass(): Supplier<out Block> = bClass

    fun getTEClass(): BlockEntityType.BlockEntitySupplier<out BlockEntity> = teClass

    fun hasFacing(): Boolean = ordinal < 3

    fun toMetadata(facing: Int): Int {
        var ret = ordinal
        if (ret < 3) {
            ret = ret or (facing shl 2)
        } else {
            ret = (((ret + 1) and 3) shl 2) or 3
        }
        return ret
    }

    override fun getSerializedName(): String = "default_peripheral_$peripheralName"

    companion object {
        @JvmStatic
        fun fromMetadata(meta: Int): DefaultPeripheral {
            return if ((meta and 3) == 3) {
                entries[(((meta shr 2) and 3) or 4) - 1] // Without facing
            } else {
                entries[meta and 3] // With facing
            }
        }
    }
}
