package net.montoyo.wd.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.montoyo.wd.block.BlockKeyboardLeft;
import net.montoyo.wd.block.BlockKeyboardRight;
import net.montoyo.wd.block.BlockPeripheral;
import net.montoyo.wd.block.BlockScreen;
import net.montoyo.wd.core.DefaultPeripheral;
import net.montoyo.wd.registry.DeferredRegister;
import net.montoyo.wd.registry.RegistryObject;

public class BlockInit {

    public static void init() {
        BLOCKS.register();
    }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "webdisplays");

    private static BlockBehaviour.Properties props(String name) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                .strength(1.5f, 10.f)
                .pushReaction(Blocks.OBSIDIAN.defaultBlockState().getPistonPushReaction())
                .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("webdisplays", name)));
    }

    public static final RegistryObject<BlockScreen> blockScreen = BLOCKS.register("screen", () -> new BlockScreen(props("screen")));

    public static final RegistryObject<BlockKeyboardLeft> blockKeyBoard = BLOCKS.register("kb_left", () -> new BlockKeyboardLeft(props("kb_left")));
    public static final RegistryObject<BlockKeyboardRight> blockKbRight = BLOCKS.register("kb_right", () -> new BlockKeyboardRight(props("kb_right")));

    public static final RegistryObject<BlockPeripheral> blockRedControl = BLOCKS.register("redctrl", () -> new BlockPeripheral(DefaultPeripheral.REDSTONE_CONTROLLER, props("redctrl")));
    public static final RegistryObject<BlockPeripheral> blockRControl = BLOCKS.register("rctrl", () -> new BlockPeripheral(DefaultPeripheral.REMOTE_CONTROLLER, props("rctrl")));
    public static final RegistryObject<BlockPeripheral> blockServer = BLOCKS.register("server", () -> new BlockPeripheral(DefaultPeripheral.SERVER, props("server")));
}
