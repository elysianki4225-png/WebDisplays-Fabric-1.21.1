package com.terrapaver.client

import com.terrapaver.network.TerraPaverNet
import net.minecraft.core.BlockPos

object ClientBiomeState {
    var hasCorner1: Boolean = false
        private set

    var corner1: BlockPos = BlockPos.ZERO
        private set

    var canUndo: Boolean = false
        private set

    fun updateState(msg: TerraPaverNet.S2CBiomeState) {
        hasCorner1 = msg.hasCorner1
        corner1 = msg.corner1
        canUndo = msg.canUndo
    }
}
