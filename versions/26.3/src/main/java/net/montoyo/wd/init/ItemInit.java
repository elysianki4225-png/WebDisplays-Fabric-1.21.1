package net.montoyo.wd.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.montoyo.wd.block.item.KeyboardItem;
import net.montoyo.wd.core.CraftComponent;
import net.montoyo.wd.core.DefaultUpgrade;
import net.montoyo.wd.item.*;
import net.montoyo.wd.registry.DeferredRegister;
import net.montoyo.wd.registry.RegistryObject;

import java.util.Locale;

@SuppressWarnings({"unchecked", "unused"})
public class ItemInit {
    public static void init() {
        ITEMS.register();
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "webdisplays");

    private static Item.Properties itemProps(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("webdisplays", name)));
    }

    private static Item.Properties blockItemProps(String name) {
        return itemProps(name).useBlockDescriptionPrefix();
    }

    protected static final RegistryObject<Item>[] COMP_CRAFT_ITEMS = new RegistryObject[CraftComponent.values().length];
    protected static final RegistryObject<Item>[] UPGRADE_ITEMS = new RegistryObject[DefaultUpgrade.values().length];

    public static final RegistryObject<Item> CONFIGURATOR = ITEMS.register("screencfg", () -> new ItemScreenConfigurator(itemProps("screencfg")));
    public static final RegistryObject<Item> OWNERSHIP_THEIF = ITEMS.register("ownerthief", () -> new ItemOwnershipThief(itemProps("ownerthief")));
    public static final RegistryObject<Item> LINKER = ITEMS.register("linker", () -> new ItemLinker(itemProps("linker")));
    public static final RegistryObject<Item> MINEPAD = ITEMS.register("minepad", () -> new ItemMinePad2(itemProps("minepad")));
    public static final RegistryObject<Item> SMARTPHONE = ITEMS.register("smartphone", () -> new ItemSmartphone(itemProps("smartphone")));
    public static final RegistryObject<Item> LASER_POINTER = ITEMS.register("laserpointer", () -> new ItemLaserPointer(itemProps("laserpointer")));

    static {
        DefaultUpgrade[] defaultUpgrades = DefaultUpgrade.values();
        for (int i = 0; i < defaultUpgrades.length; i++) {
            DefaultUpgrade upgrade = defaultUpgrades[i];
            String name = "upgrade_" + upgrade.name().toLowerCase(Locale.ROOT);
            UPGRADE_ITEMS[i] = ITEMS.register(name, () -> new ItemUpgrade(upgrade, itemProps(name)));
        }

        CraftComponent[] components = CraftComponent.values();
        for (int i = 0; i < components.length; i++) {
            CraftComponent cc = components[i];
            String name = "craftcomp_" + cc.name().toLowerCase(Locale.ROOT);
            COMP_CRAFT_ITEMS[i] = ITEMS.register(name, () -> new ItemCraftComponent(itemProps(name)));
        }
    }

    public static final RegistryObject<Item> SCREEN = ITEMS.register("screen", () -> new BlockItem(BlockInit.blockScreen.get(), blockItemProps("screen")));

    public static final RegistryObject<Item> KEYBOARD = ITEMS.register("keyboard", () -> new KeyboardItem(BlockInit.blockKeyBoard.get(), blockItemProps("keyboard")));
    public static final RegistryObject<Item> REDSTONE_CONTROLLER = ITEMS.register("redctrl", () -> new BlockItem(BlockInit.blockRedControl.get(), blockItemProps("redctrl")));
    public static final RegistryObject<Item> REMOTE_CONTROLLER = ITEMS.register("rctrl", () -> new BlockItem(BlockInit.blockRControl.get(), blockItemProps("rctrl")));
    public static final RegistryObject<Item> SERVER = ITEMS.register("server", () -> new BlockItem(BlockInit.blockServer.get(), blockItemProps("server")));

    public static RegistryObject<Item> getComputerCraftItem(int index) {
        return COMP_CRAFT_ITEMS[index];
    }

    public static RegistryObject<Item> getUpgradeItem(int index) {
        return UPGRADE_ITEMS[index];
    }

    public static int countCompCraftItems() {
        return COMP_CRAFT_ITEMS.length;
    }

    public static int countUpgrades() {
        return UPGRADE_ITEMS.length;
    }

    public static boolean isCompCraftItem(Item item) {
        for (RegistryObject<Item> itemRegistryObject : COMP_CRAFT_ITEMS)
            if (item == itemRegistryObject.get())
                return true;
        return false;
    }
}
