/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Per-world player records, replacing the Forge player capability that only tracked the "first run" flag.
 */
class WDServerData : SavedData() {
    private val welcomedPlayers = HashSet<UUID>()

    fun isFirstRun(player: ServerPlayer): Boolean =
        !welcomedPlayers.contains(player.gameProfile.id)

    fun markWelcomed(player: ServerPlayer) {
        welcomedPlayers.add(player.gameProfile.id)
        setDirty()
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        for (uuid in welcomedPlayers) {
            list.add(StringTag.valueOf(uuid.toString()))
        }

        tag.put("welcomed", list)
        return tag
    }

    companion object {
        private const val NAME = "webdisplays"

        @JvmStatic
        fun load(tag: CompoundTag, registries: HolderLookup.Provider): WDServerData {
            val data = WDServerData()

            if (tag.contains("welcomed", Tag.TAG_LIST.toInt())) {
                val list = tag.getList("welcomed", Tag.TAG_STRING.toInt())
                for (i in 0 until list.size) {
                    try {
                        data.welcomedPlayers.add(UUID.fromString(list.getString(i)))
                    } catch (_: Throwable) {
                    }
                }
            }

            return data
        }

        @JvmStatic
        fun get(server: MinecraftServer): WDServerData {
            // DataFixTypes must not be null here: DimensionDataStorage#readTagFromDisk dereferences it unconditionally.
            val factory = Factory(::WDServerData, ::load, DataFixTypes.LEVEL)
            return server.overworld().dataStorage.computeIfAbsent(factory, NAME)
        }
    }
}
