package net.montoyo.wd.init

import net.minecraft.core.registries.Registries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.montoyo.wd.block.item.KeyboardItem
import net.montoyo.wd.core.CraftComponent
import net.montoyo.wd.core.DefaultUpgrade
import net.montoyo.wd.item.ItemCraftComponent
import net.montoyo.wd.item.ItemLaserPointer
import net.montoyo.wd.item.ItemLinker
import net.montoyo.wd.item.ItemMinePad2
import net.montoyo.wd.item.ItemOwnershipThief
import net.montoyo.wd.item.ItemScreenConfigurator
import net.montoyo.wd.item.ItemSmartphone
import net.montoyo.wd.item.ItemUpgrade
import net.montoyo.wd.registry.DeferredRegister
import net.montoyo.wd.registry.RegistryObject
import java.util.Locale

@Suppress("UNCHECKED_CAST", "unused")
object ItemInit {
    @JvmStatic
    fun init() {
        ITEMS.register()
    }

    @JvmField
    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(Registries.ITEM, "webdisplays")

    @JvmField
    internal val COMP_CRAFT_ITEMS: Array<RegistryObject<Item>> = arrayOfNulls<RegistryObject<Item>>(CraftComponent.entries.size) as Array<RegistryObject<Item>>

    @JvmField
    internal val UPGRADE_ITEMS: Array<RegistryObject<Item>> = arrayOfNulls<RegistryObject<Item>>(DefaultUpgrade.entries.size) as Array<RegistryObject<Item>>

    @JvmField
    val CONFIGURATOR: RegistryObject<Item> = ITEMS.register("screencfg") { ItemScreenConfigurator(Item.Properties()) }

    @JvmField
    val OWNERSHIP_THEIF: RegistryObject<Item> = ITEMS.register("ownerthief") { ItemOwnershipThief(Item.Properties()) }

    @JvmField
    val LINKER: RegistryObject<Item> = ITEMS.register("linker") { ItemLinker(Item.Properties()) }

    @JvmField
    val MINEPAD: RegistryObject<Item> = ITEMS.register("minepad") { ItemMinePad2(Item.Properties()) }

    @JvmField
    val SMARTPHONE: RegistryObject<Item> = ITEMS.register("smartphone") { ItemSmartphone(Item.Properties()) }

    @JvmField
    val LASER_POINTER: RegistryObject<Item> = ITEMS.register("laserpointer") { ItemLaserPointer(Item.Properties()) }

    init {
        val defaultUpgrades = DefaultUpgrade.entries
        for (i in defaultUpgrades.indices) {
            val upgrade = defaultUpgrades[i]
            UPGRADE_ITEMS[i] = ITEMS.register("upgrade_" + upgrade.name.lowercase(Locale.ROOT)) { ItemUpgrade(upgrade) }
        }

        val components = CraftComponent.entries
        for (i in components.indices) {
            val cc = components[i]
            COMP_CRAFT_ITEMS[i] = ITEMS.register("craftcomp_" + cc.name.lowercase(Locale.ROOT)) { ItemCraftComponent(Item.Properties()) }
        }
    }

    @JvmField
    val SCREEN: RegistryObject<Item> = ITEMS.register("screen") { BlockItem(BlockInit.blockScreen.get(), Item.Properties()) }

    @JvmField
    val KEYBOARD: RegistryObject<Item> = ITEMS.register("keyboard") { KeyboardItem(BlockInit.blockKeyBoard.get(), Item.Properties()) }

    @JvmField
    val REDSTONE_CONTROLLER: RegistryObject<Item> = ITEMS.register("redctrl") { BlockItem(BlockInit.blockRedControl.get(), Item.Properties()) }

    @JvmField
    val REMOTE_CONTROLLER: RegistryObject<Item> = ITEMS.register("rctrl") { BlockItem(BlockInit.blockRControl.get(), Item.Properties()) }

    @JvmField
    val SERVER: RegistryObject<Item> = ITEMS.register("server") { BlockItem(BlockInit.blockServer.get(), Item.Properties()) }

    @JvmStatic
    fun getComputerCraftItem(index: Int): RegistryObject<Item> = COMP_CRAFT_ITEMS[index]

    @JvmStatic
    fun getUpgradeItem(index: Int): RegistryObject<Item> = UPGRADE_ITEMS[index]

    @JvmStatic
    fun countCompCraftItems(): Int = COMP_CRAFT_ITEMS.size

    @JvmStatic
    fun countUpgrades(): Int = UPGRADE_ITEMS.size

    @JvmStatic
    fun isCompCraftItem(item: Item): Boolean {
        for (itemRegistryObject in COMP_CRAFT_ITEMS) {
            if (item == itemRegistryObject.get()) return true
        }
        return false
    }
}
