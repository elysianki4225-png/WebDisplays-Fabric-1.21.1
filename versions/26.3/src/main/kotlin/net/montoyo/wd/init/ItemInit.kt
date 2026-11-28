package net.montoyo.wd.init

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
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

    private fun itemProps(name: String): Item.Properties =
        Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("webdisplays", name)))

    private fun blockItemProps(name: String): Item.Properties =
        itemProps(name).useBlockDescriptionPrefix()

    @JvmField
    internal val COMP_CRAFT_ITEMS: Array<RegistryObject<Item>> = arrayOfNulls<RegistryObject<Item>>(CraftComponent.entries.size) as Array<RegistryObject<Item>>

    @JvmField
    internal val UPGRADE_ITEMS: Array<RegistryObject<Item>> = arrayOfNulls<RegistryObject<Item>>(DefaultUpgrade.entries.size) as Array<RegistryObject<Item>>

    @JvmField
    val CONFIGURATOR: RegistryObject<Item> = ITEMS.register("screencfg") { ItemScreenConfigurator(itemProps("screencfg")) }

    @JvmField
    val OWNERSHIP_THEIF: RegistryObject<Item> = ITEMS.register("ownerthief") { ItemOwnershipThief(itemProps("ownerthief")) }

    @JvmField
    val LINKER: RegistryObject<Item> = ITEMS.register("linker") { ItemLinker(itemProps("linker")) }

    @JvmField
    val MINEPAD: RegistryObject<Item> = ITEMS.register("minepad") { ItemMinePad2(itemProps("minepad")) }

    @JvmField
    val SMARTPHONE: RegistryObject<Item> = ITEMS.register("smartphone") { ItemSmartphone(itemProps("smartphone")) }

    @JvmField
    val LASER_POINTER: RegistryObject<Item> = ITEMS.register("laserpointer") { ItemLaserPointer(itemProps("laserpointer")) }

    init {
        val defaultUpgrades = DefaultUpgrade.entries
        for (i in defaultUpgrades.indices) {
            val upgrade = defaultUpgrades[i]
            val name = "upgrade_" + upgrade.name.lowercase(Locale.ROOT)
            UPGRADE_ITEMS[i] = ITEMS.register(name) { ItemUpgrade(upgrade, itemProps(name)) }
        }

        val components = CraftComponent.entries
        for (i in components.indices) {
            val cc = components[i]
            val name = "craftcomp_" + cc.name.lowercase(Locale.ROOT)
            COMP_CRAFT_ITEMS[i] = ITEMS.register(name) { ItemCraftComponent(itemProps(name)) }
        }
    }

    @JvmField
    val SCREEN: RegistryObject<Item> = ITEMS.register("screen") { BlockItem(BlockInit.blockScreen.get(), blockItemProps("screen")) }

    @JvmField
    val KEYBOARD: RegistryObject<Item> = ITEMS.register("keyboard") { KeyboardItem(BlockInit.blockKeyBoard.get(), blockItemProps("keyboard")) }

    @JvmField
    val REDSTONE_CONTROLLER: RegistryObject<Item> = ITEMS.register("redctrl") { BlockItem(BlockInit.blockRedControl.get(), blockItemProps("redctrl")) }

    @JvmField
    val REMOTE_CONTROLLER: RegistryObject<Item> = ITEMS.register("rctrl") { BlockItem(BlockInit.blockRControl.get(), blockItemProps("rctrl")) }

    @JvmField
    val SERVER: RegistryObject<Item> = ITEMS.register("server") { BlockItem(BlockInit.blockServer.get(), blockItemProps("server")) }

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
