package com.terrapaver.engine

import com.terrapaver.config.PavingConfig
import com.terrapaver.network.TerraPaverNet
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Block
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object ServerPavingManager {
    private class ActiveJob(
        val playerId: UUID,
        val level: ServerLevel,
        val waypoints: List<BlockPos>,
        val cfg: PavingConfig
    ) {
        val streamer = ChunkStreamer(level, cfg.enableUnloadedChunks)
        val planner = IncrementalRoutePlanner(streamer, waypoints, cfg)
        val snapshot = UndoSnapshot()
        val builder = BlockBuilder(level, cfg, snapshot, streamer)

        var paused = false
        var surveying = true
        var stations: List<RouteStation> = emptyList()
        var cursor = 0
        var tunnelSegments = 0
        var viaductSegments = 0
        var syncTicker = 0
    }

    private data class UndoEntry(
        val level: ServerLevel,
        val snapshot: UndoSnapshot
    )

    private val waypointLists = HashMap<UUID, ArrayList<BlockPos>>()
    private val activeJobs = HashMap<UUID, ActiveJob>()
    private val undoStacks = HashMap<UUID, ArrayDeque<UndoEntry>>()

    fun handleSurveyorClick(player: ServerPlayer, clickedPos: BlockPos) {
        val job = activeJobs[player.uuid]
        if (job != null) {
            togglePause(player)
            return
        }

        val list = waypointLists.getOrPut(player.uuid) { ArrayList() }
        val last = list.lastOrNull()
        // Clicking the same last waypoint again starts paving when >= 2 points exist
        if (last != null && list.size >= 2 && last.distManhattan(clickedPos) <= 1) {
            startJob(player, list.toList(), PavingConfig.clientConfig)
            return
        }

        addWaypoint(player, clickedPos)
    }

    fun handleSurveyorAirClick(player: ServerPlayer) {
        val job = activeJobs[player.uuid]
        if (job != null) {
            togglePause(player)
            return
        }
        val list = waypointLists[player.uuid]
        if (list != null && list.size >= 2) {
            startJob(player, list.toList(), PavingConfig.clientConfig)
        } else {
            addWaypoint(player, player.blockPosition().below())
        }
    }

    fun addWaypoint(player: ServerPlayer, pos: BlockPos) {
        val list = waypointLists.getOrPut(player.uuid) { ArrayList() }
        val p = pos.immutable()
        list.add(p)
        syncSelection(player)
        if (list.size >= 2) {
            sendQuickPreview(player, list, PavingConfig.clientConfig)
            player.displayClientMessage(
                Component.translatable("terrapaver.msg.wp_added_ready", list.size, p.x, p.y, p.z),
                true
            )
        } else {
            player.displayClientMessage(
                Component.translatable("terrapaver.msg.wp_added", list.size, p.x, p.y, p.z),
                true
            )
        }
    }

    fun popWaypoint(player: ServerPlayer) {
        val list = waypointLists[player.uuid]
        if (list.isNullOrEmpty()) return
        list.removeAt(list.lastIndex)
        syncSelection(player)
        if (list.size >= 2) {
            sendQuickPreview(player, list, PavingConfig.clientConfig)
        } else {
            TerraPaverNet.sendToPlayer(
                player,
                TerraPaverNet.S2CRoutePreview(IntArray(0), IntArray(0), IntArray(0), ByteArray(0))
            )
        }
    }

    fun setStart(player: ServerPlayer, pos: BlockPos) {
        val list = waypointLists.getOrPut(player.uuid) { ArrayList() }
        list.clear()
        list.add(pos.immutable())
        syncSelection(player)
        player.displayClientMessage(
            Component.translatable("terrapaver.msg.wp_added", 1, pos.x, pos.y, pos.z),
            true
        )
    }

    fun setEndAndSync(player: ServerPlayer, pos: BlockPos) {
        addWaypoint(player, pos)
    }

    fun extendAhead(player: ServerPlayer, distance: Int) {
        val list = waypointLists.getOrPut(player.uuid) { ArrayList() }
        val base = list.lastOrNull() ?: player.blockPosition().below().immutable().also { list.add(it) }
        val look = player.lookAngle
        val horizLen = hypot(look.x, look.z).coerceAtLeast(1e-4)
        val ex = (base.x + look.x / horizLen * distance).roundToInt()
        val ez = (base.z + look.z / horizLen * distance).roundToInt()
        addWaypoint(player, BlockPos(ex, base.y, ez))
    }

    fun clearSelection(player: ServerPlayer) {
        waypointLists.remove(player.uuid)
        syncSelection(player)
        TerraPaverNet.sendToPlayer(
            player,
            TerraPaverNet.S2CRoutePreview(IntArray(0), IntArray(0), IntArray(0), ByteArray(0))
        )
    }

    fun startSelectedJob(player: ServerPlayer, cfg: PavingConfig) {
        val list = waypointLists[player.uuid]
        if (list == null || list.size < 2) {
            player.displayClientMessage(Component.translatable("terrapaver.msg.need_two_points"), true)
            return
        }
        startJob(player, list.toList(), cfg)
    }

    fun startJob(player: ServerPlayer, waypoints: List<BlockPos>, cfg: PavingConfig) {
        if (waypoints.size < 2) {
            player.displayClientMessage(Component.translatable("terrapaver.msg.need_two_points"), true)
            return
        }
        val sanitized = cfg.copySanitized()
        activeJobs.remove(player.uuid)?.streamer?.releaseAll()

        val list = waypointLists.getOrPut(player.uuid) { ArrayList() }
        list.clear()
        for (wp in waypoints) list.add(wp.immutable())

        val level = player.serverLevel()
        val job = ActiveJob(player.uuid, level, list.toList(), sanitized)
        activeJobs[player.uuid] = job

        player.displayClientMessage(Component.translatable("terrapaver.msg.started"), true)
        sendStatus(player, job)
    }

    fun togglePause(player: ServerPlayer) {
        val job = activeJobs[player.uuid] ?: return
        job.paused = !job.paused
        player.displayClientMessage(
            Component.translatable(if (job.paused) "terrapaver.msg.paused" else "terrapaver.msg.resumed"),
            true
        )
        sendStatus(player, job)
    }

    fun cancelJob(player: ServerPlayer) {
        val job = activeJobs.remove(player.uuid) ?: return
        job.streamer.releaseAll()
        if (job.snapshot.size() > 0) {
            pushUndo(player.uuid, job.level, job.snapshot)
        }
        syncSelection(player)
        val first = job.waypoints.first()
        TerraPaverNet.sendToPlayer(
            player,
            TerraPaverNet.S2CPavingStatus(
                active = false,
                paused = false,
                phase = TerraPaverNet.S2CPavingStatus.PHASE_IDLE,
                progress = 0f,
                currentStep = 0,
                totalSteps = 0,
                placedBlocks = job.builder.placedCount,
                tunnelSegments = job.tunnelSegments,
                viaductSegments = job.viaductSegments,
                statusKey = "terrapaver.status.cancelled",
                headX = first.x,
                headY = first.y,
                headZ = first.z
            )
        )
        player.displayClientMessage(Component.translatable("terrapaver.msg.cancelled"), true)
    }

    fun undoLast(player: ServerPlayer) {
        if (activeJobs.containsKey(player.uuid)) {
            cancelJob(player)
        }
        val queue = undoStacks[player.uuid]
        val entry = queue?.removeLastOrNull()
        if (entry == null) {
            player.displayClientMessage(Component.translatable("terrapaver.msg.nothing_undo"), true)
            syncSelection(player)
            return
        }
        val streamer = ChunkStreamer(entry.level, true)
        val mPos = BlockPos.MutableBlockPos()
        var restored = 0
        for ((key, state) in entry.snapshot.entries()) {
            mPos.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key))
            streamer.ensureLoaded(mPos.x, mPos.z)
            entry.level.setBlock(mPos, state, Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE)
            restored++
        }
        streamer.releaseAll()
        syncSelection(player)
        player.displayClientMessage(Component.translatable("terrapaver.msg.undone", restored), true)
    }

    fun sendQuickPreview(player: ServerPlayer, waypoints: List<BlockPos>, cfg: PavingConfig) {
        if (waypoints.size < 2) return
        val xsList = ArrayList<Int>()
        val ysList = ArrayList<Int>()
        val zsList = ArrayList<Int>()
        val kindsList = ArrayList<Byte>()

        for (w in 0 until waypoints.size - 1) {
            val a = waypoints[w]
            val b = waypoints[w + 1]
            val dx = (b.x - a.x).toDouble()
            val dz = (b.z - a.z).toDouble()
            val dist = hypot(dx, dz).coerceAtLeast(1.0)
            val count = kotlin.math.ceil(dist / 4.0).toInt().coerceIn(2, 160)
            for (i in 0 until count) {
                val t = i.toDouble() / (count - 1).toDouble()
                xsList.add((a.x + dx * t).roundToInt())
                ysList.add((a.y + 1 + (b.y - a.y) * t).roundToInt())
                zsList.add((a.z + dz * t).roundToInt())
                kindsList.add(StationType.SURFACE.code)
            }
        }
        TerraPaverNet.sendToPlayer(
            player,
            TerraPaverNet.S2CRoutePreview(
                xsList.toIntArray(),
                ysList.toIntArray(),
                zsList.toIntArray(),
                kindsList.toByteArray()
            )
        )
    }

    fun tick(server: MinecraftServer) {
        if (activeJobs.isEmpty()) return
        val finished = ArrayList<UUID>()

        for ((uuid, job) in activeJobs) {
            val player = server.playerList.getPlayer(uuid)
            if (job.paused) {
                if (player != null && job.syncTicker++ % 10 == 0) sendStatus(player, job)
                continue
            }

            if (job.surveying) {
                job.planner.stepBudget(48)
                if (job.planner.isDone()) {
                    job.surveying = false
                    job.stations = job.planner.getStations()
                    job.tunnelSegments = countStructures(job.stations, StationType.TUNNEL)
                    job.viaductSegments = countStructures(job.stations, StationType.VIADUCT)
                    if (player != null) {
                        sendPlannedPreview(player, job.stations)
                    }
                }
            } else {
                val budget = job.cfg.blocksPerTick
                val startBlocks = job.builder.placedCount
                while (job.cursor < job.stations.size && (job.builder.placedCount - startBlocks) < budget) {
                    val st = job.stations[job.cursor]
                    job.streamer.ensureAreaLoaded(st.x.roundToInt(), st.z.roundToInt(), 24)
                    job.builder.buildStation(job.stations, job.cursor)
                    if (job.cursor >= 4) {
                        job.builder.buildStationDecorations(job.stations, job.cursor - 4)
                    }
                    job.cursor++
                }
                if (job.cursor >= job.stations.size) {
                    val flushStart = (job.stations.size - 4).coerceAtLeast(0)
                    for (k in flushStart until job.stations.size) {
                        val st = job.stations[k]
                        job.streamer.ensureAreaLoaded(st.x.roundToInt(), st.z.roundToInt(), 24)
                        job.builder.buildStationDecorations(job.stations, k)
                    }
                }
            }

            if (!job.surveying && job.cursor >= job.stations.size) {
                finished.add(uuid)
                job.streamer.releaseAll()
                if (job.snapshot.size() > 0) {
                    pushUndo(uuid, job.level, job.snapshot)
                }
                if (player != null) {
                    syncSelection(player)
                    val end = job.waypoints.last()
                    TerraPaverNet.sendToPlayer(
                        player,
                        TerraPaverNet.S2CPavingStatus(
                            active = false,
                            paused = false,
                            phase = TerraPaverNet.S2CPavingStatus.PHASE_DONE,
                            progress = 1f,
                            currentStep = job.stations.size,
                            totalSteps = job.stations.size,
                            placedBlocks = job.builder.placedCount,
                            tunnelSegments = job.tunnelSegments,
                            viaductSegments = job.viaductSegments,
                            statusKey = "terrapaver.status.done",
                            headX = end.x,
                            headY = end.y,
                            headZ = end.z
                        )
                    )
                    player.displayClientMessage(
                        Component.translatable("terrapaver.msg.completed", job.builder.placedCount),
                        true
                    )
                }
            } else if (player != null && job.syncTicker++ % 3 == 0) {
                sendStatus(player, job)
            }
        }

        for (uuid in finished) {
            activeJobs.remove(uuid)
        }
    }

    fun onDisconnect(player: ServerPlayer) {
        activeJobs.remove(player.uuid)?.streamer?.releaseAll()
    }

    fun shutdown(server: MinecraftServer) {
        for (job in activeJobs.values) {
            job.streamer.releaseAll()
        }
        activeJobs.clear()
    }

    private fun pushUndo(uuid: UUID, level: ServerLevel, snapshot: UndoSnapshot) {
        val queue = undoStacks.getOrPut(uuid) { ArrayDeque() }
        queue.addLast(UndoEntry(level, snapshot))
        while (queue.size > 4) {
            queue.removeFirst()
        }
    }

    private fun syncSelection(player: ServerPlayer) {
        val list = waypointLists[player.uuid] ?: emptyList()
        val canUndo = undoStacks[player.uuid]?.isNotEmpty() == true
        TerraPaverNet.sendToPlayer(player, TerraPaverNet.S2CSyncSelection(list.toList(), canUndo))
    }

    private fun sendPlannedPreview(player: ServerPlayer, stations: List<RouteStation>) {
        if (stations.isEmpty()) return
        val stride = max(1, stations.size / 320)
        val count = ((stations.size - 1) / stride) + 1
        val xs = IntArray(count)
        val ys = IntArray(count)
        val zs = IntArray(count)
        val kinds = ByteArray(count)
        var idx = 0
        var i = 0
        while (i < stations.size && idx < count) {
            val st = stations[i]
            xs[idx] = st.x.roundToInt()
            ys[idx] = st.roadY
            zs[idx] = st.z.roundToInt()
            kinds[idx] = st.type.code
            idx++
            i += stride
        }
        TerraPaverNet.sendToPlayer(player, TerraPaverNet.S2CRoutePreview(xs, ys, zs, kinds))
    }

    private fun sendStatus(player: ServerPlayer, job: ActiveJob) {
        val phase = if (job.surveying) {
            TerraPaverNet.S2CPavingStatus.PHASE_SURVEYING
        } else {
            TerraPaverNet.S2CPavingStatus.PHASE_PAVING
        }
        val progress = if (job.surveying) {
            job.planner.progress
        } else {
            job.cursor.toFloat() / max(1, job.stations.size).toFloat()
        }
        val curStation = if (!job.surveying && job.stations.isNotEmpty()) {
            job.stations[job.cursor.coerceIn(0, job.stations.size - 1)]
        } else null

        val statusKey = when {
            job.paused -> "terrapaver.status.paused"
            job.surveying -> "terrapaver.status.surveying"
            curStation?.type == StationType.TUNNEL -> "terrapaver.status.tunnel"
            curStation?.type == StationType.VIADUCT -> "terrapaver.status.viaduct"
            curStation?.type == StationType.CUT -> "terrapaver.status.cut"
            else -> "terrapaver.status.paving"
        }

        val fallback = job.waypoints.first()
        TerraPaverNet.sendToPlayer(
            player,
            TerraPaverNet.S2CPavingStatus(
                active = true,
                paused = job.paused,
                phase = phase,
                progress = progress.coerceIn(0f, 1f),
                currentStep = if (job.surveying) 0 else job.cursor,
                totalSteps = if (job.surveying) 0 else job.stations.size,
                placedBlocks = job.builder.placedCount,
                tunnelSegments = job.tunnelSegments,
                viaductSegments = job.viaductSegments,
                statusKey = statusKey,
                headX = curStation?.x?.roundToInt() ?: fallback.x,
                headY = curStation?.roadY ?: fallback.y,
                headZ = curStation?.z?.roundToInt() ?: fallback.z
            )
        )
    }

    private fun countStructures(stations: List<RouteStation>, target: StationType): Int {
        var count = 0
        var inside = false
        for (st in stations) {
            if (st.type == target) {
                if (!inside) {
                    count++
                    inside = true
                }
            } else {
                inside = false
            }
        }
        return count
    }
}
