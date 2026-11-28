/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.block.BlockScreen
import net.montoyo.wd.core.IPeripheral
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Multiblock
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector3i

class ItemLinker(properties: Properties) : Item(properties.stacksTo(1)), WDItem {
    override fun useOn(context: UseOnContext): InteractionResult {
        if (context.level.isClientSide()) return InteractionResult.SUCCESS

        val player = context.player!!
        val stack = player.getItemInHand(context.hand)
        val data = stack.get(DataComponents.CUSTOM_DATA)
        var tag: CompoundTag? = data?.copyTag()

        if (tag != null) {
            if (tag.contains("ScreenX") && tag.contains("ScreenY") && tag.contains("ScreenZ") && tag.contains("ScreenSide")) {
                val state = context.level.getBlockState(context.clickedPos)
                val target: IPeripheral = if (state.block is IPeripheral) {
                    state.block as IPeripheral
                } else {
                    val te = context.level.getBlockEntity(context.clickedPos)
                    if (te !is IPeripheral) {
                        if (player.isShiftKeyDown) {
                            Util.toast(player, ChatFormatting.GOLD, "linkAbort")
                            stack.remove(DataComponents.CUSTOM_DATA)
                        } else {
                            Util.toast(player, "peripheral")
                        }
                        return InteractionResult.SUCCESS
                    }
                    te
                }

                val tePos = Vector3i(tag.getInt("ScreenX"), tag.getInt("ScreenY"), tag.getInt("ScreenZ"))
                val scrSide = BlockSide.values()[tag.getByte("ScreenSide").toInt()]

                if (target.connect(context.level, context.clickedPos, state, tePos, scrSide)) {
                    Util.toast(player, ChatFormatting.AQUA, "linked")
                    if (player is ServerPlayer) {
                        WebDisplays.INSTANCE.criterionLinkPeripheral?.trigger(player.advancements)
                    }
                } else {
                    Util.toast(player, "linkError")
                }

                stack.remove(DataComponents.CUSTOM_DATA)
                return InteractionResult.SUCCESS
            }
        }

        if (context.level.getBlockState(context.clickedPos).block !is BlockScreen) {
            Util.toast(player, "notAScreen")
            return InteractionResult.SUCCESS
        }

        val pos = Vector3i(context.clickedPos)
        val side = BlockSide.values()[context.clickedFace.ordinal]
        Multiblock.findOrigin(context.level, pos, side, null)

        val te = context.level.getBlockEntity(pos.toBlock())
        if (te !is TileEntityScreen) {
            Util.toast(player, "turnOn")
            return InteractionResult.SUCCESS
        }

        val scr = te.getScreen(side)
        if (scr == null) {
            Util.toast(player, "turnOn")
        } else if ((scr.rightsFor(player) and ScreenRights.MANAGE_UPGRADES) == 0) {
            Util.toast(player, "restrictions")
        } else {
            tag = CompoundTag()
            tag.putInt("ScreenX", pos.x)
            tag.putInt("ScreenY", pos.y)
            tag.putInt("ScreenZ", pos.z)
            tag.putByte("ScreenSide", side.ordinal.toByte())

            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            Util.toast(player, ChatFormatting.AQUA, "screenSet2")
        }

        return InteractionResult.SUCCESS
    }

    override fun getWikiName(isStack: ItemStack): String = isStack.item.getName(isStack).string
}
