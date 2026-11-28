package net.montoyo.wd.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.config.ClientConfig
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.WDNetworkRegistry

object WebDisplaysFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        WebDisplays.PROXY = ClientProxy().apply {
            preInit()
            init()
            postInit()
        }

        ClientConfig.init()

        WDNetworkRegistry.initClient()
        ClientProxy.registerKeybinds()
        ClientProxy.onClientSetup()
        ClientProxy.registerClientEvents()

        // Replaces Forge's BlockEntity onLoad/onChunkUnloaded hooks.
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register { blockEntity, _ ->
            if (blockEntity is TileEntityScreen) blockEntity.onClientLoad()
        }

        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register { blockEntity, _ ->
            if (blockEntity is TileEntityScreen) blockEntity.onClientUnload()
        }
    }
}
