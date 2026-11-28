package com.terrapaver.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil
import kotlin.math.max

enum class PavingMode(val key: String) {
    ROAD("terrapaver.mode.road"),
    CREATE_RAIL("terrapaver.mode.create_rail")
}

enum class RoadStyle(val key: String) {
    ASPHALT("terrapaver.style.asphalt"),
    STONE_HIGHWAY("terrapaver.style.stone_highway"),
    COBBLE_TRAIL("terrapaver.style.cobble_trail"),
    CREATE_BALLAST("terrapaver.style.create_ballast")
}

enum class AlignmentMode(val key: String) {
    TERRAIN_SMART("terrapaver.align.smart"),
    DIRECT("terrapaver.align.direct")
}

enum class RiverStrategy(val key: String) {
    AUTO_VIADUCT("terrapaver.river.viaduct"),
    SHIELD_TUNNEL("terrapaver.river.tunnel")
}

enum class TunnelShape(val key: String) {
    ARCH("terrapaver.shape.arch"),
    CIRCULAR("terrapaver.shape.circular"),
    RECTANGULAR("terrapaver.shape.rectangular")
}

data class PavingConfig(
    var pavingMode: PavingMode = PavingMode.ROAD,
    var roadWidth: Int = 5,
    var roadStyle: RoadStyle = RoadStyle.ASPHALT,
    var alignmentMode: AlignmentMode = AlignmentMode.TERRAIN_SMART,
    var maxSlopeRun: Int = 4,
    var riverStrategy: RiverStrategy = RiverStrategy.AUTO_VIADUCT,
    var autoTunnelSize: Boolean = true,
    var tunnelWidth: Int = 7,
    var tunnelHeight: Int = 6,
    var tunnelShape: TunnelShape = TunnelShape.ARCH,
    var tunnelCutThreshold: Int = 4,
    var viaductPillarSpacing: Int = 8,
    var addGuardrails: Boolean = true,
    var addLaneMarkings: Boolean = true,
    var addStreetLights: Boolean = true,
    var lightInterval: Int = 12,
    var blocksPerTick: Int = 128,
    var enableUnloadedChunks: Boolean = true,
    var dropExcavatedBlocks: Boolean = false,
    var showRoutePreview: Boolean = true,
    var m3SeedTheme: Int = 0
) {
    fun sanitize(): PavingConfig {
        roadWidth = roadWidth.coerceIn(3, 15)
        maxSlopeRun = maxSlopeRun.coerceIn(2, 8)
        tunnelWidth = tunnelWidth.coerceIn(5, 21)
        tunnelHeight = tunnelHeight.coerceIn(4, 15)
        tunnelCutThreshold = tunnelCutThreshold.coerceIn(2, 12)
        viaductPillarSpacing = viaductPillarSpacing.coerceIn(4, 16)
        lightInterval = lightInterval.coerceIn(6, 24)
        blocksPerTick = blocksPerTick.coerceIn(24, 512)
        m3SeedTheme = m3SeedTheme.coerceIn(0, 3)
        return this
    }

    fun effectiveTunnelWidth(): Int {
        if (!autoTunnelSize) return tunnelWidth.coerceIn(5, 21)
        val margin = if (pavingMode == PavingMode.CREATE_RAIL) 4 else 2
        return (roadWidth + margin).coerceIn(5, 21)
    }

    fun effectiveTunnelHeight(): Int {
        if (!autoTunnelSize) return tunnelHeight.coerceIn(4, 15)
        val minH = if (pavingMode == PavingMode.CREATE_RAIL) 6 else 5
        val proportional = ceil(effectiveTunnelWidth() * 0.72).toInt()
        return max(minH, proportional).coerceIn(4, 15)
    }

    fun copySanitized(): PavingConfig = copy().sanitize()

    fun toJson(): String = GSON.toJson(sanitize())

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()

        private val configPath: Path by lazy {
            FabricLoader.getInstance().configDir.resolve("terrapaver.json")
        }

        @Volatile
        var clientConfig: PavingConfig = PavingConfig()
            private set

        fun loadClient(): PavingConfig {
            try {
                if (Files.exists(configPath)) {
                    val parsed = GSON.fromJson(Files.readString(configPath), PavingConfig::class.java)
                    if (parsed != null) {
                        clientConfig = parsed.sanitize()
                        return clientConfig
                    }
                }
            } catch (_: Throwable) {
            }
            clientConfig = PavingConfig().sanitize()
            return clientConfig
        }

        fun saveClient(cfg: PavingConfig) {
            clientConfig = cfg.copySanitized()
            try {
                Files.createDirectories(configPath.parent)
                Files.writeString(configPath, GSON.toJson(clientConfig))
            } catch (_: Throwable) {
            }
        }

        fun fromJson(json: String): PavingConfig {
            return try {
                GSON.fromJson(json, PavingConfig::class.java)?.sanitize() ?: PavingConfig()
            } catch (_: Throwable) {
                PavingConfig()
            }
        }
    }
}
