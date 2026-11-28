package net.montoyo.wd

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.montoyo.wd.core.DroppedPadLightManager
import net.montoyo.wd.core.WDServerData
import net.montoyo.wd.miniserv.server.Server
import net.montoyo.wd.net.WDDistributor
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.client_bound.S2CMessageServerInfo
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.Util
import java.io.File
import java.io.IOException

object WebDisplaysFabric : ModInitializer {
    const val MOD_ID = "webdisplays"

    @JvmField
    var currentServer: MinecraftServer? = null

    private val serverStartedDimensions = mutableListOf<ResourceKey<Level>>()

    override fun onInitialize() {
        WebDisplays.initialize()

        ServerLifecycleEvents.SERVER_STARTED.register { server -> currentServer = server }
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            DroppedPadLightManager.clearAll(server)
            currentServer = null
            try {
                Server.getInstance().stopServer()
            } catch (e: IOException) {
                Log.warningEx("Could not stop the miniserv.", e)
            }
        }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            DroppedPadLightManager.tickServer(server)
        }

        ServerWorldEvents.LOAD.register(::onWorldLoad)
        ServerWorldEvents.UNLOAD.register(::onWorldUnload)

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> onPlayerLogin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            Server.getInstance().getClientManager().revokeClientKey(handler.player.gameProfile.id)
        }

        ServerMessageEvents.CHAT_MESSAGE.register { message, player, _ ->
            onServerChat(message.signedContent(), player)
        }
    }

    private fun onWorldLoad(server: MinecraftServer, world: ServerLevel) {
        if (world.dimension() != Level.OVERWORLD || WebDisplays.INSTANCE.miniservPort == 0) return

        val worldDir = server.serverDirectory.toFile()
        val sv = Server.getInstance()

        if (world.dimension() !in serverStartedDimensions) {
            sv.setPort(WebDisplays.INSTANCE.miniservPort)
            sv.setDirectory(File(worldDir, "wd_filehost"))
            sv.start()
            serverStartedDimensions += world.dimension()
        }
    }

    private fun onWorldUnload(server: MinecraftServer, world: ServerLevel) {
        if (world.dimension() != Level.OVERWORLD) return

        try {
            Server.getInstance().stopServer()
        } catch (e: IOException) {
            Log.warningEx("Could not stop the miniserv.", e)
        }
        serverStartedDimensions -= world.dimension()
    }

    private fun onPlayerLogin(player: ServerPlayer) {
        val data = WDServerData.get(player.server)

        if (data.isFirstRun(player)) {
            Util.toast(player, ChatFormatting.LIGHT_PURPLE, "welcome1")
            Util.toast(player, ChatFormatting.LIGHT_PURPLE, "welcome2")
            Util.toast(player, ChatFormatting.LIGHT_PURPLE, "welcome3")
            data.markWelcomed(player)
        }

        val message = S2CMessageServerInfo(WebDisplays.INSTANCE.miniservPort)
        WDNetworkRegistry.INSTANCE.send(WDDistributor.PLAYER.with { player }, message)
    }

    private fun onServerChat(rawMessage: String, player: ServerPlayer) {
        val cleaned = rawMessage
            .replace(Regex("\\s+"), " ")
            .lowercase()
            .filterNot { it in ".,;!?:'\"`" }

        if (cleaned == "ironic he could save others from death but not himself") {
            val ply: Player = player
            val sound = WebDisplays.INSTANCE.soundIronic ?: return
            ply.level().playSound(
                null,
                ply.x,
                ply.y,
                ply.z,
                sound,
                SoundSource.PLAYERS,
                1.0f,
                1.0f
            )
        }
    }

    @JvmStatic
    fun hasPlayerAdvancement(player: ServerPlayer, id: ResourceLocation): Boolean {
        val server = currentServer ?: return false
        val adv = server.advancements.get(id) ?: return false
        return player.advancements.getOrStartProgress(adv).isDone
    }
}
