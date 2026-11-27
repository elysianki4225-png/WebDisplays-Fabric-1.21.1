/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world player records, replacing the Forge player capability that only tracked the "first run" flag.
 */
public class WDServerData extends SavedData {
    private static final Codec<WDServerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("welcomed", List.of())
                    .forGetter(d -> d.welcomedPlayers.stream().map(UUID::toString).toList())
    ).apply(instance, WDServerData::new));

    private static final SavedDataType<WDServerData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("webdisplays", "webdisplays"),
            WDServerData::new,
            CODEC,
            null
    );

    private final Set<UUID> welcomedPlayers = new HashSet<>();

    private WDServerData() {
    }

    private WDServerData(List<String> welcomed) {
        for (String s : welcomed) {
            try {
                welcomedPlayers.add(UUID.fromString(s));
            } catch (Throwable ignored) {
            }
        }
    }

    public static WDServerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isFirstRun(ServerPlayer player) {
        return !welcomedPlayers.contains(player.getGameProfile().id());
    }

    public void markWelcomed(ServerPlayer player) {
        welcomedPlayers.add(player.getGameProfile().id());
        setDirty();
    }
}
