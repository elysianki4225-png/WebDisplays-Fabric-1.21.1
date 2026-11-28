package com.terrapaver.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min

enum class BiomeShape(val key: String) {
    RADIUS_BRUSH("terrapaver.biome.shape.circle"),
    SQUARE_BRUSH("terrapaver.biome.shape.square"),
    BOX_2POINT("terrapaver.biome.shape.box"),
    CHUNK_GRID("terrapaver.biome.shape.chunks")
}

enum class BiomeVerticalMode(val key: String) {
    FULL_HEIGHT("terrapaver.biome.vmode.full"),
    SURFACE_BAND("terrapaver.biome.vmode.surface"),
    CUSTOM_Y("terrapaver.biome.vmode.custom")
}

enum class BiomeSplitMode(val key: String) {
    SINGLE("terrapaver.biome.split.single"),
    SPLIT_X("terrapaver.biome.split.x"),
    SPLIT_Z("terrapaver.biome.split.z"),
    VORONOI_MIX("terrapaver.biome.split.voronoi")
}

data class BiomeConfig(
    var targetBiome: String = "minecraft:cherry_grove",
    var secondaryBiome: String = "minecraft:meadow",
    var splitPalette: MutableList<String> = mutableListOf("minecraft:cherry_grove", "minecraft:meadow"),
    var splitMode: BiomeSplitMode = BiomeSplitMode.SINGLE,
    var splitPatchScale: Int = 16,
    var targetDimension: String = "",
    var shape: BiomeShape = BiomeShape.RADIUS_BRUSH,
    var radius: Int = 24,
    var chunkRadius: Int = 1,
    var verticalMode: BiomeVerticalMode = BiomeVerticalMode.FULL_HEIGHT,
    var surfaceDepthDown: Int = 8,
    var surfaceHeightUp: Int = 32,
    var customMinY: Int = -64,
    var customMaxY: Int = 320,
    var edgeFeather: Int = 4,
    var filterSourceBiome: Boolean = false,
    var sourceBiomeFilter: String = "minecraft:plains",
    var preserveRiversAndOceans: Boolean = false,
    var overwriteExisting: Boolean = true,
    var adaptSurfaceBlocks: Boolean = false,
    var adaptWeatherSnow: Boolean = false,
    var recentBiomes: MutableList<String> = mutableListOf(),
    var favoriteBiomes: MutableList<String> = mutableListOf(
        "minecraft:cherry_grove",
        "minecraft:plains",
        "minecraft:desert",
        "minecraft:snowy_plains"
    )
) {
    fun copySanitized(): BiomeConfig {
        val c = copy(
            splitPalette = splitPalette?.toMutableList() ?: mutableListOf("minecraft:cherry_grove", "minecraft:meadow"),
            recentBiomes = recentBiomes?.toMutableList() ?: mutableListOf(),
            favoriteBiomes = favoriteBiomes?.toMutableList() ?: mutableListOf()
        )
        @Suppress("USELESS_ELVIS")
        c.shape = c.shape ?: BiomeShape.RADIUS_BRUSH
        @Suppress("USELESS_ELVIS")
        c.verticalMode = c.verticalMode ?: BiomeVerticalMode.FULL_HEIGHT
        @Suppress("USELESS_ELVIS")
        c.splitMode = c.splitMode ?: BiomeSplitMode.SINGLE
        @Suppress("USELESS_ELVIS")
        c.targetBiome = (c.targetBiome ?: "minecraft:cherry_grove").trim().ifEmpty { "minecraft:cherry_grove" }
        @Suppress("USELESS_ELVIS")
        c.secondaryBiome = (c.secondaryBiome ?: "minecraft:meadow").trim().ifEmpty { "minecraft:meadow" }
        @Suppress("USELESS_ELVIS")
        c.targetDimension = (c.targetDimension ?: "").trim()
        @Suppress("USELESS_ELVIS")
        c.sourceBiomeFilter = (c.sourceBiomeFilter ?: "minecraft:plains").trim().ifEmpty { "minecraft:plains" }

        if (c.splitPalette.isEmpty()) {
            c.splitPalette.add(c.targetBiome)
            c.splitPalette.add(c.secondaryBiome)
        }
        c.splitPatchScale = c.splitPatchScale.coerceIn(4, 64)
        c.radius = c.radius.coerceIn(2, 256)
        c.chunkRadius = c.chunkRadius.coerceIn(0, 7)
        c.surfaceDepthDown = c.surfaceDepthDown.coerceIn(0, 128)
        c.surfaceHeightUp = c.surfaceHeightUp.coerceIn(4, 256)
        c.customMinY = c.customMinY.coerceIn(-256, 512)
        c.customMaxY = c.customMaxY.coerceIn(-256, 512)
        if (c.customMinY > c.customMaxY) {
            val lo = min(c.customMinY, c.customMaxY)
            val hi = max(c.customMinY, c.customMaxY)
            c.customMinY = lo
            c.customMaxY = hi
        }
        c.edgeFeather = c.edgeFeather.coerceIn(0, 32)
        return c
    }

    fun recordRecent(biomeId: String) {
        recentBiomes.remove(biomeId)
        recentBiomes.add(0, biomeId)
        while (recentBiomes.size > 6) {
            recentBiomes.removeAt(recentBiomes.lastIndex)
        }
    }

    fun toJson(): String = GSON.toJson(copySanitized())

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()

        var clientConfig: BiomeConfig = BiomeConfig()
            private set

        private fun configPath(): Path =
            FabricLoader.getInstance().configDir.resolve("terrapaver_biome.json")

        fun fromJson(json: String): BiomeConfig {
            return try {
                GSON.fromJson(json, BiomeConfig::class.java)?.copySanitized() ?: BiomeConfig()
            } catch (_: Exception) {
                BiomeConfig()
            }
        }

        fun loadClient(): BiomeConfig {
            val path = configPath()
            if (Files.exists(path)) {
                try {
                    val loaded = GSON.fromJson(Files.readString(path), BiomeConfig::class.java)
                    if (loaded != null) {
                        clientConfig = loaded.copySanitized()
                        return clientConfig
                    }
                } catch (_: Exception) {
                }
            }
            clientConfig = BiomeConfig()
            saveClient(clientConfig)
            return clientConfig
        }

        fun saveClient(cfg: BiomeConfig) {
            clientConfig = cfg.copySanitized()
            val path = configPath()
            try {
                Files.createDirectories(path.parent)
                Files.writeString(path, GSON.toJson(clientConfig))
            } catch (_: Exception) {
            }
        }
    }
}
