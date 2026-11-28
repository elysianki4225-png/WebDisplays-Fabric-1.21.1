package net.montoyo.wd.init

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.montoyo.wd.block.BlockKeyboardLeft
import net.montoyo.wd.block.BlockKeyboardRight
import net.montoyo.wd.block.BlockPeripheral
import net.montoyo.wd.block.BlockScreen
import net.montoyo.wd.core.DefaultPeripheral
import net.montoyo.wd.registry.DeferredRegister
import net.montoyo.wd.registry.RegistryObject

object BlockInit {
    @JvmStatic
    fun init() {
        BLOCKS.register()
    }

    @JvmField
    val BLOCKS: DeferredRegister<Block> = DeferredRegister.create(Registries.BLOCK, "webdisplays")

    @JvmField
    val blockScreen: RegistryObject<BlockScreen> =
        BLOCKS.register("screen") { BlockScreen(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)) }

    @JvmField
    val blockKeyBoard: RegistryObject<BlockKeyboardLeft> =
        BLOCKS.register("kb_left") { BlockKeyboardLeft() }

    @JvmField
    val blockKbRight: RegistryObject<BlockKeyboardRight> =
        BLOCKS.register("kb_right") { BlockKeyboardRight() }

    @JvmField
    val blockRedControl: RegistryObject<BlockPeripheral> =
        BLOCKS.register("redctrl") { BlockPeripheral(DefaultPeripheral.REDSTONE_CONTROLLER) }

    @JvmField
    val blockRControl: RegistryObject<BlockPeripheral> =
        BLOCKS.register("rctrl") { BlockPeripheral(DefaultPeripheral.REMOTE_CONTROLLER) }

    @JvmField
    val blockServer: RegistryObject<BlockPeripheral> =
        BLOCKS.register("server") { BlockPeripheral(DefaultPeripheral.SERVER) }
}
