/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.WebDisplaysFabric
import net.montoyo.wd.config.CommonConfig
import net.montoyo.wd.core.CraftComponent
import net.montoyo.wd.core.HasAdvancement

class ItemCraftComponent(properties: Properties) : ItemMulti(CraftComponent::class.java, properties), WDItem {
    init {
        // Hide the bad extension card from the creative tab
        creativeTabItems.clear(CraftComponent.BADEXTCARD.ordinal)
    }

    override fun onCraftedBy(stack: ItemStack, level: Level, player: Player) {
        super.onCraftedBy(stack, level, player)

        if (!CommonConfig.hardRecipes || !CraftComponent.EXTCARD.makeItemStack().`is`(stack.item) || hasPadBreak(level, player)) {
            return
        }

        stack.damageValue = CraftComponent.BADEXTCARD.ordinal

        if (!level.isClientSide) {
            level.playSound(null, player.x, player.y, player.z, SoundEvents.ITEM_BREAK, SoundSource.MASTER, 1.0f, 1.0f)
        }
    }

    override fun getWikiName(isStack: ItemStack): String = isStack.item.getName(isStack).string

    companion object {
        private fun hasPadBreak(level: Level, player: Player): Boolean {
            if (level.isClientSide) {
                return WebDisplays.PROXY.hasClientPlayerAdvancement(WebDisplays.ADV_PAD_BREAK) == HasAdvancement.YES
            }
            return player is ServerPlayer && WebDisplaysFabric.hasPlayerAdvancement(player, WebDisplays.ADV_PAD_BREAK)
        }
    }
}
