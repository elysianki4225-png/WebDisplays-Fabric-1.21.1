package net.montoyo.wd.miniserv

import net.minecraft.server.level.ServerPlayer

object SyncPlugin {
    private val syncedPlayers = HashMap<ServerPlayer, MutableList<ServerPlayer>>()
    private val URL = HashMap<ServerPlayer, String>()

    @JvmStatic
    fun syncPlayers(players: List<ServerPlayer>) {
        for (player in players) {
            val syncedPlayersList = ArrayList(players)
            syncedPlayersList.remove(player)
            syncedPlayers[player] = syncedPlayersList
        }
    }

    @JvmStatic
    fun getPlayerString(player: ServerPlayer): String? = URL[player]

    @JvmStatic
    fun setPlayerString(player: ServerPlayer, value: String) {
        URL[player] = value
        val syncedPlayersList = syncedPlayers[player]
        if (syncedPlayersList != null) {
            for (syncedPlayer in syncedPlayersList) {
                if (!syncedPlayer.hasDisconnected()) {
                    // Legacy sync hook preserved
                }
            }
        }
    }
}
