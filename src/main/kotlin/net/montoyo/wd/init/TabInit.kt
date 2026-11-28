/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.init

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.registry.DeferredRegister
import net.montoyo.wd.registry.RegistryObject

object TabInit {
    @JvmField
    val TABS: DeferredRegister<CreativeModeTab> = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "webdisplays")

    @JvmField
    val MAIN_TAB: RegistryObject<CreativeModeTab> = TABS.register("main") {
        FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.webdisplays"))
            .icon { ItemStack(ItemInit.SCREEN.get()) }
            .displayItems { _, output ->
                // core items
                output.accept(ItemInit.SCREEN.get())
                output.accept(ItemInit.KEYBOARD.get())
                output.accept(ItemInit.LINKER.get())
                // remote control
                output.accept(ItemInit.REMOTE_CONTROLLER.get())
                // redstone stuff
                output.accept(ItemInit.REDSTONE_CONTROLLER.get())
                // admin tools
                output.accept(ItemInit.OWNERSHIP_THEIF.get())
                // tool items
                output.accept(ItemInit.SERVER.get())
                output.accept(ItemInit.CONFIGURATOR.get())
                output.accept(ItemInit.MINEPAD.get())
                output.accept(ItemInit.SMARTPHONE.get())
                output.accept(ItemInit.LASER_POINTER.get())

                // upgrades
                for (i in 0 until ItemInit.countUpgrades()) output.accept(ItemInit.getUpgradeItem(i).get())
                // cc
                for (i in 0 until ItemInit.countCompCraftItems()) output.accept(ItemInit.getComputerCraftItem(i).get())
            }
            .build()
    }

    @JvmStatic
    fun init() {
        TABS.register()
    }
}
