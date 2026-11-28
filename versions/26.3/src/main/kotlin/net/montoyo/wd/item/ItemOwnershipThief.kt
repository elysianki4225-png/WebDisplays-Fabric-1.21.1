/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.montoyo.wd.block.BlockScreen
import net.montoyo.wd.config.CommonConfig
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.Multiblock
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector3i

class ItemOwnershipThief(properties: Properties) : Item(properties.stacksTo(1)), WDItem {
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player!!
        if (player.isShiftKeyDown) return InteractionResult.PASS
        if (context.level.isClientSide()) return InteractionResult.SUCCESS

        if (CommonConfig.disableOwnershipThief) {
            Util.toast(player, "otDisabled")
            return InteractionResult.SUCCESS
        }

        val stack = player.getItemInHand(context.hand)
        val thiefTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        if (!thiefTag.isEmpty) {
            if (thiefTag.contains("PosX") && thiefTag.contains("PosY") && thiefTag.contains("PosZ") && thiefTag.contains("Side")) {
                val bp = BlockPos(thiefTag.getIntOr("PosX", 0), thiefTag.getIntOr("PosY", 0), thiefTag.getIntOr("PosZ", 0))
                val side = BlockSide.values()[thiefTag.getByteOr("Side", 0.toByte()).toInt()]

                if (context.level.getBlockState(bp).block !is BlockScreen) return InteractionResult.SUCCESS
                val te = context.level.getBlockEntity(bp) as? TileEntityScreen ?: return InteractionResult.SUCCESS
                val scr = te.getScreen(side) ?: return InteractionResult.SUCCESS

                Log.warning(
                    "Owner of screen at %d %d %d, side %s was changed from %s (UUID %s) to %s (UUID %s)",
                    bp.x, bp.y, bp.z, side.toString(), scr.owner.name, scr.owner.uuid.toString(),
                    player.name, player.gameProfile.id().toString()
                )
                player.setItemInHand(context.hand, ItemStack.EMPTY)
                te.setOwner(side, player)
                Util.toast(player, ChatFormatting.AQUA, "newOwner")
            }

            return InteractionResult.SUCCESS
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

        if (te.getScreen(side) == null) {
            Util.toast(player, "turnOn")
        } else {
            val tag = CompoundTag()
            tag.putInt("PosX", pos.x)
            tag.putInt("PosY", pos.y)
            tag.putInt("PosZ", pos.z)
            tag.putByte("Side", side.ordinal.toByte())

            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            Util.toast(player, ChatFormatting.AQUA, "screenSet")
            Log.warning(
                "Player %s (UUID %s) created an Ownership Thief item for screen at %d %d %d, side %s!",
                player.name, player.gameProfile.id().toString(), pos.x, pos.y, pos.z, side.toString()
            )
        }

        return InteractionResult.SUCCESS
    }

    override fun getWikiName(isStack: ItemStack): String = "Ownership_Thief"
}
