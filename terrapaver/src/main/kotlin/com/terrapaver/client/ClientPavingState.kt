package com.terrapaver.client

import com.terrapaver.network.TerraPaverNet
import net.minecraft.core.BlockPos

object ClientPavingState {
    var hasStart = false
    var startPos: BlockPos = BlockPos.ZERO
    var hasEnd = false
    var endPos: BlockPos = BlockPos.ZERO
    var canUndo = false

    var status: TerraPaverNet.S2CPavingStatus? = null
    var lastStatusTimestampMs: Long = 0L

    var preview: TerraPaverNet.S2CRoutePreview? = null

    fun updateSelection(pkt: TerraPaverNet.S2CSyncSelection) {
        hasStart = pkt.hasStart
        startPos = pkt.start
        hasEnd = pkt.hasEnd
        endPos = pkt.end
        canUndo = pkt.canUndo
    }

    fun updateStatus(pkt: TerraPaverNet.S2CPavingStatus) {
        status = pkt
        lastStatusTimestampMs = System.currentTimeMillis()
    }

    fun updatePreview(pkt: TerraPaverNet.S2CRoutePreview) {
        preview = if (pkt.xs.isEmpty()) null else pkt
    }

    fun shouldShowHud(): Boolean {
        val st = status ?: return false
        if (st.active) return true
        if (st.phase == TerraPaverNet.S2CPavingStatus.PHASE_DONE) {
            return (System.currentTimeMillis() - lastStatusTimestampMs) < 3500L
        }
        return false
    }
}
