package com.terrapaver.client

import com.terrapaver.network.TerraPaverNet
import net.minecraft.core.BlockPos
import kotlin.math.hypot
import kotlin.math.roundToInt

object ClientPavingState {
    val waypoints = ArrayList<BlockPos>()
    var canUndo = false

    var status: TerraPaverNet.S2CPavingStatus? = null
    var lastStatusTimestampMs: Long = 0L

    var preview: TerraPaverNet.S2CRoutePreview? = null

    fun hasEnoughWaypoints(): Boolean = waypoints.size >= 2

    fun totalSpanBlocks(): Int {
        if (waypoints.size < 2) return 0
        var sum = 0.0
        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            sum += hypot((b.x - a.x).toDouble(), (b.z - a.z).toDouble())
        }
        return sum.roundToInt()
    }

    fun updateSelection(pkt: TerraPaverNet.S2CSyncSelection) {
        waypoints.clear()
        waypoints.addAll(pkt.waypoints)
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
