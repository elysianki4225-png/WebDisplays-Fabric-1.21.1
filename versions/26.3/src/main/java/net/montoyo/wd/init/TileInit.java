package net.montoyo.wd.init;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.montoyo.wd.entity.*;
import net.montoyo.wd.registry.DeferredRegister;
import net.montoyo.wd.registry.RegistryObject;

public class TileInit {

    public static final DeferredRegister<BlockEntityType<?>> TILE_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "webdisplays");

    public static final RegistryObject<BlockEntityType<TileEntityScreen>> SCREEN_BLOCK_ENTITY = TILE_TYPES
            .register("screen", () -> FabricBlockEntityTypeBuilder
                    .create(TileEntityScreen::new, BlockInit.blockScreen.get()).build());

    public static final RegistryObject<BlockEntityType<?>> KEYBOARD = TILE_TYPES.register("kb_left", () -> FabricBlockEntityTypeBuilder
            .create(TileEntityKeyboard::new, BlockInit.blockKeyBoard.get()).build());

    public static final RegistryObject<BlockEntityType<?>> REMOTE_CONTROLLER = TILE_TYPES.register("rctrl",
            () -> FabricBlockEntityTypeBuilder.create(TileEntityRCtrl::new, BlockInit.blockRControl.get()).build());

    public static final RegistryObject<BlockEntityType<?>> REDSTONE_CONTROLLER = TILE_TYPES.register("redctrl",
            () -> FabricBlockEntityTypeBuilder.create(TileEntityRedCtrl::new, BlockInit.blockRedControl.get()).build());

    public static final RegistryObject<BlockEntityType<?>> SERVER = TILE_TYPES.register("server",
            () -> FabricBlockEntityTypeBuilder.create(TileEntityServer::new, BlockInit.blockServer.get()).build());

    public static void init() {
        TILE_TYPES.register();
    }
}
