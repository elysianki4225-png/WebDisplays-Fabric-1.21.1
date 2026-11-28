package com.terrapaver.client

import com.terrapaver.client.gui.M3PavingScreen
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

    override fun onInitializeClient() {
        PavingConfig.loadClient()

        openGuiKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.terrapaver.open_gui", GLFW.GLFW_KEY_P, "key.categories.terrapaver")
        )

        TerraPaverClientHooks.openSettingsCallback = {
            val mc = Minecraft.getInstance()
            mc.execute {
                mc.setScreen(M3PavingScreen())
            }
        }

        TerraPaverNet.registerClientReceivers(
            onStatus = { ClientPavingState.updateStatus(it) },
            onSelection = { ClientPavingState.updateSelection(it) },
            onPreview = { ClientPavingState.updatePreview(it) }
        )

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            while (openGuiKey.consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(M3PavingScreen())
                }
            }
        }

        HudRenderCallback.EVENT.register { guiGraphics, deltaTracker ->
            PavingHudOverlay.render(guiGraphics, deltaTracker)
        }

        WorldRenderEvents.AFTER_TRANSLUCENT.register { context ->
            RoutePreviewRenderer.render(context)
        }
    }
}
