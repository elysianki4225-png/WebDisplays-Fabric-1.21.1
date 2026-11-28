/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.config.CommonConfig
import net.montoyo.wd.core.CraftComponent
import net.montoyo.wd.core.DroppedPadLightManager
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.server_bound.C2SMessageMinepadUrl
import net.montoyo.wd.utilities.NbtCompat
import java.util.UUID

open class ItemMinePad2(properties: Properties) :
    Item(properties.stacksTo(1).durability(0)),
    WDItem,
    IItemEntityTickHandler {

    open fun isMobileLayout(): Boolean = false

    override fun use(world: Level, ply: Player, hand: InteractionHand): InteractionResult {
        val isStack = ply.getItemInHand(hand)
        val tag = isStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()

        if (ply.isShiftKeyDown) {
            if (world.isClientSide()) WebDisplays.PROXY.displaySetPadURLGui(isStack, getURL(isStack))
        } else if (NbtCompat.hasUUID(tag, "PadID")) {
            if (world.isClientSide()) WebDisplays.PROXY.openMinePadGui(NbtCompat.getUUID(tag, "PadID")!!)
        } else if (world.isClientSide()) {
            val uuid = UUID.randomUUID()
            val url = getURL(isStack)
            WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageMinepadUrl(uuid, url))
            NbtCompat.putUUID(tag, "PadID", uuid)
            tag.putString("PadURL", url)
            if (isMobileLayout()) tag.putBoolean("PadMobile", true)
            isStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            if (isMobileLayout()) WebDisplays.PROXY.openMinePadGui(uuid)
        }

        if (isMobileLayout() && world.isClientSide()) {
            WebDisplays.PROXY.onSmartphoneUsed(ply, hand)
        }

        return InteractionResult.SUCCESS
    }

    override fun onEntityItemUpdate(stack: ItemStack, entity: ItemEntity): Boolean {
        if (!entity.level().isClientSide()) {
            if (entity.onGround()) {
                val data = entity.item.get(DataComponents.CUSTOM_DATA)
                val tag = data?.copyTag()

                if (tag != null && tag.contains("ThrowHeight")) {
                    val height = tag.getDoubleOr("ThrowHeight", 0.0)
                    var thrower: UUID? = null

                    if (tag.contains("ThrowerMSB") && tag.contains("ThrowerLSB")) {
                        thrower = UUID(tag.getLongOr("ThrowerMSB", 0L), tag.getLongOr("ThrowerLSB", 0L))
                    }

                    if (NbtCompat.hasUUID(tag, "PadID") || tag.contains("PadURL")) {
                        tag.remove("ThrowerMSB")
                        tag.remove("ThrowerLSB")
                        tag.remove("ThrowHeight")
                        entity.item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                    } else {
                        entity.item.remove(DataComponents.CUSTOM_DATA)
                    }

                    if (thrower != null && height - entity.blockY >= 20.0) {
                        entity.level().playSound(null, entity.blockX.toDouble(), entity.blockY.toDouble(), entity.blockZ.toDouble(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 4.0f, 1.0f)
                        entity.level().addFreshEntity(ItemEntity(entity.level(), entity.blockX.toDouble(), entity.blockY.toDouble(), entity.blockZ.toDouble(), CraftComponent.EXTCARD.makeItemStack()))
                        entity.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION)
                        DroppedPadLightManager.updateDroppedPad(entity, false)

                        val ply = entity.level().getPlayerByUUID(thrower)
                        if (ply is ServerPlayer) {
                            WebDisplays.INSTANCE.criterionPadBreak?.trigger(ply)
                        }
                        return false
                    }
                }
            }

            val curData = entity.item.get(DataComponents.CUSTOM_DATA)
            val isLit = curData != null && NbtCompat.hasUUID(curData.copyTag(), "PadID")
            DroppedPadLightManager.updateDroppedPad(entity, isLit)
        }

        return false
    }

    override fun getWikiName(isStack: ItemStack): String = isStack.hoverName.string

    companion object {
        private fun getURL(isStack: ItemStack): String {
            val data = isStack.get(DataComponents.CUSTOM_DATA)
            return if (data == null || !data.copyTag().contains("PadURL")) {
                CommonConfig.Browser.homepage
            } else {
                data.copyTag().getStringOr("PadURL", CommonConfig.Browser.homepage)
            }
        }
    }
}
