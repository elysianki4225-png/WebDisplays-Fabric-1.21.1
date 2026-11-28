package com.terrapaver.client

import com.terrapaver.network.TerraPaverNet
import net.minecraft.core.BlockPos

data class ClientDimensionInfo(
    val id: String,
    val minY: Int,
    val maxY: Int
)

object ClientBiomeState {
    var hasCorner1: Boolean = false
        private set

    var corner1: BlockPos = BlockPos.ZERO
        private set

    var canUndo: Boolean = false
        private set

    var currentDimension: String = "minecraft:overworld"
        private set

    var serverDimensions: List<ClientDimensionInfo> = listOf(
        ClientDimensionInfo("minecraft:overworld", -64, 320),
        ClientDimensionInfo("minecraft:the_nether", 0, 128),
        ClientDimensionInfo("minecraft:the_end", 0, 256)
    )
        private set

    fun updateState(msg: TerraPaverNet.S2CBiomeState) {
        hasCorner1 = msg.hasCorner1
        corner1 = msg.corner1
        canUndo = msg.canUndo
    }

    fun updateCatalog(msg: TerraPaverNet.S2CBiomeCatalog) {
        currentDimension = msg.currentDimension
        if (msg.dimIds.isNotEmpty()) {
            serverDimensions = msg.dimIds.indices.map { i ->
                ClientDimensionInfo(msg.dimIds[i], msg.minYs[i], msg.maxYs[i])
            }
        }
    }
}
