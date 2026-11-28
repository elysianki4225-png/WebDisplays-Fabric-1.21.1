package net.montoyo.wd.init

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

    // Register tile entities
    @JvmField
    val SCREEN_BLOCK_ENTITY: RegistryObject<BlockEntityType<TileEntityScreen>> = TILE_TYPES.register("screen") {
        BlockEntityType.Builder.of(::TileEntityScreen, BlockInit.blockScreen.get()).build(null)
    }

    @JvmField
    val KEYBOARD: RegistryObject<BlockEntityType<*>> = TILE_TYPES.register("kb_left") {
        BlockEntityType.Builder.of(::TileEntityKeyboard, BlockInit.blockKeyBoard.get()).build(null)
    }

    @JvmField
    val REMOTE_CONTROLLER: RegistryObject<BlockEntityType<*>> = TILE_TYPES.register("rctrl") {
        BlockEntityType.Builder.of(::TileEntityRCtrl, BlockInit.blockRControl.get()).build(null)
    }

    @JvmField
    val REDSTONE_CONTROLLER: RegistryObject<BlockEntityType<*>> = TILE_TYPES.register("redctrl") {
        BlockEntityType.Builder.of(::TileEntityRedCtrl, BlockInit.blockRedControl.get()).build(null)
    }

    @JvmField
    val SERVER: RegistryObject<BlockEntityType<*>> = TILE_TYPES.register("server") {
        BlockEntityType.Builder.of(::TileEntityServer, BlockInit.blockServer.get()).build(null)
    }

    @JvmStatic
    fun init() {
        TILE_TYPES.register()
    }
}
