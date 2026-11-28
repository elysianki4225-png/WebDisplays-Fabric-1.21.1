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

data class BiomeConfig(
    var targetBiome: String = "minecraft:cherry_grove",
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
    var adaptSurfaceBlocks: Boolean = false,
    var adaptWeatherSnow: Boolean = false
) {
    fun copySanitized(): BiomeConfig {
        val c = copy()
        @Suppress("USELESS_ELVIS")
        c.shape = c.shape ?: BiomeShape.RADIUS_BRUSH
        @Suppress("USELESS_ELVIS")
        c.verticalMode = c.verticalMode ?: BiomeVerticalMode.FULL_HEIGHT
        @Suppress("USELESS_ELVIS")
        c.targetBiome = (c.targetBiome ?: "minecraft:cherry_grove").trim().ifEmpty { "minecraft:cherry_grove" }
        @Suppress("USELESS_ELVIS")
        c.sourceBiomeFilter = (c.sourceBiomeFilter ?: "minecraft:plains").trim().ifEmpty { "minecraft:plains" }

        c.radius = c.radius.coerceIn(2, 128)
        c.chunkRadius = c.chunkRadius.coerceIn(0, 4)
        c.surfaceDepthDown = c.surfaceDepthDown.coerceIn(0, 64)
        c.surfaceHeightUp = c.surfaceHeightUp.coerceIn(4, 128)
        c.customMinY = c.customMinY.coerceIn(-64, 320)
        c.customMaxY = c.customMaxY.coerceIn(-64, 320)
        if (c.customMinY > c.customMaxY) {
            val lo = min(c.customMinY, c.customMaxY)
            val hi = max(c.customMinY, c.customMaxY)
            c.customMinY = lo
            c.customMaxY = hi
        }
        c.edgeFeather = c.edgeFeather.coerceIn(0, 24)
        return c
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
