/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType
import java.util.UUID

/**
 * Per-world player records, replacing the Forge player capability that only tracked the "first run" flag.
 */
class WDServerData : SavedData {
    private val welcomedPlayers = HashSet<UUID>()

    private constructor()

    private constructor(welcomed: List<String>) {
        for (s in welcomed) {
            try {
                welcomedPlayers.add(UUID.fromString(s))
            } catch (_: Throwable) {
            }
        }
    }

    fun isFirstRun(player: ServerPlayer): Boolean =
        !welcomedPlayers.contains(player.gameProfile.id())

    fun markWelcomed(player: ServerPlayer) {
        welcomedPlayers.add(player.gameProfile.id())
        setDirty()
    }

    companion object {
        private val CODEC: Codec<WDServerData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.listOf().optionalFieldOf("welcomed", emptyList())
                    .forGetter { d -> d.welcomedPlayers.map(UUID::toString) }
            ).apply(instance, ::WDServerData)
        }

        private val TYPE: SavedDataType<WDServerData> = SavedDataType(
            Identifier.fromNamespaceAndPath("webdisplays", "webdisplays"),
            ::WDServerData,
            CODEC,
            null
        )

        @JvmStatic
        fun get(server: MinecraftServer): WDServerData =
            server.overworld().dataStorage.computeIfAbsent(TYPE)
    }
}
