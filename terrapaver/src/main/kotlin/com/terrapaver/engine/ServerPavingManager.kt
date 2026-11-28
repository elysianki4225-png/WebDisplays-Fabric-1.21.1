package com.terrapaver.engine

import com.terrapaver.config.PavingConfig
import com.terrapaver.network.TerraPaverNet
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

object ServerPavingManager {
    private data class Selection(
        var start: BlockPos? = null,
        var end: BlockPos? = null
    )

    private class ActiveJob(
        val playerId: UUID,
        val level: ServerLevel,
        val start: BlockPos,
        val end: BlockPos,
        val cfg: PavingConfig
    ) {
        val streamer = ChunkStreamer(level, cfg.enableUnloadedChunks)
        val planner = IncrementalRoutePlanner(streamer, start, end, cfg)
        val snapshot = UndoSnapshot()
        val builder = BlockBuilder(level, cfg, snapshot)

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

    private val selections = HashMap<UUID, Selection>()
    private val activeJobs = HashMap<UUID, ActiveJob>()
    private val undoStacks = HashMap<UUID, ArrayDeque<UndoEntry>>()

    fun handleSurveyorClick(player: ServerPlayer, clickedPos: BlockPos) {
        val job = activeJobs[player.uuid]
        if (job != null) {
            togglePause(player)
            return
        }

        val sel = selections.getOrPut(player.uuid) { Selection() }
        when {
            sel.start == null -> {
                sel.start = clickedPos.immutable()
                sel.end = null
                player.displayClientMessage(
                    Component.translatable("terrapaver.msg.start_set", clickedPos.x, clickedPos.y, clickedPos.z),
                    true
                )
                syncSelection(player)
            }
            sel.end == null -> {
                sel.end = clickedPos.immutable()
                player.displayClientMessage(
                    Component.translatable("terrapaver.msg.end_set", clickedPos.x, clickedPos.y, clickedPos.z),
                    true
                )
                syncSelection(player)
                sendQuickPreview(player, sel.start!!, sel.end!!, PavingConfig.clientConfig)
            }
            else -> {
                // 3rd click starts paving immediately with current config!
                startJob(player, sel.start!!, sel.end!!, PavingConfig.clientConfig)
            }
        }
    }

    fun handleSurveyorAirClick(player: ServerPlayer) {
        val job = activeJobs[player.uuid]
        if (job != null) {
            togglePause(player)
            return
        }
        val sel = selections.getOrPut(player.uuid) { Selection() }
        if (sel.start != null && sel.end != null) {
            startJob(player, sel.start!!, sel.end!!, PavingConfig.clientConfig)
        } else {
            val pos = player.blockPosition().below()
            handleSurveyorClick(player, pos)
        }
    }

    fun setStart(player: ServerPlayer, pos: BlockPos) {
        val sel = selections.getOrPut(player.uuid) { Selection() }
        sel.start = pos.immutable()
        player.displayClientMessage(
            Component.translatable("terrapaver.msg.start_set", pos.x, pos.y, pos.z),
            true
        )
        syncSelection(player)
    }

    fun setEndAndSync(player: ServerPlayer, pos: BlockPos) {
        val sel = selections.getOrPut(player.uuid) { Selection() }
        if (sel.start == null) {
            sel.start = player.blockPosition().below().immutable()
        }
        sel.end = pos.immutable()
        player.displayClientMessage(
            Component.translatable("terrapaver.msg.end_set", pos.x, pos.y, pos.z),
            true
        )
        syncSelection(player)
        sendQuickPreview(player, sel.start!!, sel.end!!, PavingConfig.clientConfig)
    }

    fun extendAhead(player: ServerPlayer, distance: Int) {
        val sel = selections.getOrPut(player.uuid) { Selection() }
        val s = sel.start ?: player.blockPosition().below().immutable().also { sel.start = it }
        val look = player.lookAngle
        val horizLen = kotlin.math.hypot(look.x, look.z).coerceAtLeast(1e-4)
        val dx = look.x / horizLen
        val dz = look.z / horizLen
        val ex = (s.x + dx * distance).roundToInt()
        val ez = (s.z + dz * distance).roundToInt()
        val ey = s.y
        sel.end = BlockPos(ex, ey, ez)
        syncSelection(player)
        sendQuickPreview(player, s, sel.end!!, PavingConfig.clientConfig)
        player.displayClientMessage(
            Component.translatable("terrapaver.msg.end_set", ex, ey, ez),
            true
        )
    }

    fun clearSelection(player: ServerPlayer) {
        selections.remove(player.uuid)
        syncSelection(player)
        TerraPaverNet.sendToPlayer(
            player,
            TerraPaverNet.S2CRoutePreview(IntArray(0), IntArray(0), IntArray(0), ByteArray(0))
        )
    }

    fun startJob(player: ServerPlayer, start: BlockPos, end: BlockPos, cfg: PavingConfig) {
        val sanitized = cfg.copySanitized()
        activeJobs.remove(player.uuid)?.streamer?.releaseAll()

        val sel = selections.getOrPut(player.uuid) { Selection() }
        sel.start = start.immutable()
        sel.end = end.immutable()

        val level = player.serverLevel()
        val job = ActiveJob(player.uuid, level, start.immutable(), end.immutable(), sanitized)
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
                headX = job.start.x,
                headY = job.start.y,
                headZ = job.start.z
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

    fun sendQuickPreview(player: ServerPlayer, start: BlockPos, end: BlockPos, cfg: PavingConfig) {
        val dx = (end.x - start.x).toDouble()
        val dz = (end.z - start.z).toDouble()
        val dist = kotlin.math.hypot(dx, dz).coerceAtLeast(1.0)
        val count = kotlin.math.ceil(dist / 4.0).toInt().coerceIn(2, 512)
        val xs = IntArray(count)
        val ys = IntArray(count)
        val zs = IntArray(count)
        val kinds = ByteArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / (count - 1).toDouble()
            xs[i] = (start.x + dx * t).roundToInt()
            ys[i] = (start.y + 1 + (end.y - start.y) * t).roundToInt()
            zs[i] = (start.z + dz * t).roundToInt()
            kinds[i] = StationType.SURFACE.code
        }
        TerraPaverNet.sendToPlayer(player, TerraPaverNet.S2CRoutePreview(xs, ys, zs, kinds))
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
                    job.streamer.ensureAreaLoaded(st.x.roundToInt(), st.z.roundToInt(), 20)
                    job.builder.buildStation(job.stations, job.cursor)
                    job.cursor++
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
                            headX = job.end.x,
                            headY = job.end.y,
                            headZ = job.end.z
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
        val sel = selections[player.uuid]
        val canUndo = undoStacks[player.uuid]?.isNotEmpty() == true
        TerraPaverNet.sendToPlayer(
            player,
            TerraPaverNet.S2CSyncSelection(
                hasStart = sel?.start != null,
                start = sel?.start ?: BlockPos.ZERO,
                hasEnd = sel?.end != null,
                end = sel?.end ?: BlockPos.ZERO,
                canUndo = canUndo
            )
        )
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
                headX = curStation?.x?.roundToInt() ?: job.start.x,
                headY = curStation?.roadY ?: job.start.y,
                headZ = curStation?.z?.roundToInt() ?: job.start.z
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
