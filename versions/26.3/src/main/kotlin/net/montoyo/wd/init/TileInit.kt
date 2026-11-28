package net.montoyo.wd.init

import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.montoyo.wd.entity.TileEntityKeyboard
import net.montoyo.wd.entity.TileEntityRCtrl
import net.montoyo.wd.entity.TileEntityRedCtrl
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.entity.TileEntityServer
import net.montoyo.wd.registry.DeferredRegister
import net.montoyo.wd.registry.RegistryObject

object TileInit {
    @JvmField
    val TILE_TYPES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "webdisplays")

    @JvmField
    val SCREEN_BLOCK_ENTITY: RegistryObject<BlockEntityType<TileEntityScreen>> = TILE_TYPES.register("screen") {
        FabricBlockEntityTypeBuilder.create(::TileEntityScreen, BlockInit.blockScreen.get()).build()
    }

    @JvmField
    val KEYBOARD: RegistryObject<BlockEntityType<*>> = TILE_TYPES.register("kb_left") {
        FabricBlockEntityTypeBuilder.create(::TileEntityKeyboard, BlockInit.blockKeyBoard.get()).build()
    }

    @JvmField
    val REMOTE_CONTROLLER: RegistryObject<BlockEntityType<*>> = TILE_TYPES.register("rctrl") {
        FabricBlockEntityTypeBuilder.create(::TileEntityRCtrl, BlockInit.blockRControl.get()).build()
    }

    @JvmField
    val REDSTONE_CONTROLLER: RegistryObject<BlockEntityType<*>> = TILE_TYPES.register("redctrl") {
        FabricBlockEntityTypeBuilder.create(::TileEntityRedCtrl, BlockInit.blockRedControl.get()).build()
    }

    @JvmField
    val SERVER: RegistryObject<BlockEntityType<*>> = TILE_TYPES.register("server") {
        FabricBlockEntityTypeBuilder.create(::TileEntityServer, BlockInit.blockServer.get()).build()
    }

    @JvmStatic
    fun init() {
        TILE_TYPES.register()
    }
}
