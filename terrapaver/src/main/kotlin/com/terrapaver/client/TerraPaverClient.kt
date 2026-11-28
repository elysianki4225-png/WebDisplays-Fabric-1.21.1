package com.terrapaver.client

import com.terrapaver.client.gui.M3BiomeScreen
import com.terrapaver.client.gui.M3PavingScreen
import com.terrapaver.config.BiomeConfig
import com.terrapaver.config.PavingConfig
import com.terrapaver.network.TerraPaverNet
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object TerraPaverClient : ClientModInitializer {
    private lateinit var openGuiKey: KeyMapping
    private lateinit var openBiomeGuiKey: KeyMapping

    override fun onInitializeClient() {
        PavingConfig.loadClient()
        BiomeConfig.loadClient()

        openGuiKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.terrapaver.open_gui", GLFW.GLFW_KEY_P, "key.categories.terrapaver")
        )
        openBiomeGuiKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.terrapaver.open_biome_gui", GLFW.GLFW_KEY_B, "key.categories.terrapaver")
        )

        TerraPaverClientHooks.openSettingsCallback = {
            val mc = Minecraft.getInstance()
            mc.execute {
                mc.setScreen(M3PavingScreen())
            }
        }
        TerraPaverClientHooks.openBiomeCallback = {
            val mc = Minecraft.getInstance()
            mc.execute {
                mc.setScreen(M3BiomeScreen())
            }
        }

        TerraPaverNet.registerClientReceivers(
            onStatus = { ClientPavingState.updateStatus(it) },
            onSelection = { ClientPavingState.updateSelection(it) },
            onPreview = { ClientPavingState.updatePreview(it) },
            onBiomeState = { ClientBiomeState.updateState(it) },
            onBiomeCatalog = { ClientBiomeState.updateCatalog(it) }
        )

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            while (openGuiKey.consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(M3PavingScreen())
                }
            }
            while (openBiomeGuiKey.consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(M3BiomeScreen())
                }
            }
        }

        HudRenderCallback.EVENT.register { guiGraphics, deltaTracker ->
            PavingHudOverlay.render(guiGraphics, deltaTracker)
            BiomeHudOverlay.render(guiGraphics, deltaTracker)
        }

        WorldRenderEvents.AFTER_TRANSLUCENT.register { context ->
            RoutePreviewRenderer.render(context)
            BiomePreviewRenderer.render(context)
        }
    }
}
