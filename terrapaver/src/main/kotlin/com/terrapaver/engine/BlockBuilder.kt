package com.terrapaver.engine

import com.terrapaver.config.PavingConfig
import com.terrapaver.config.PavingMode
import com.terrapaver.config.RiverStrategy
import com.terrapaver.config.RoadStyle
import com.terrapaver.config.TunnelShape
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.SlabType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class UndoSnapshot(val maxEntries: Int = 220_000) {
    private val map = LinkedHashMap<Long, BlockState>(4096)

    fun record(pos: BlockPos, oldState: BlockState) {
        if (map.size >= maxEntries) return
        val key = pos.asLong()
        if (!map.containsKey(key)) {
            map[key] = oldState
        }
    }

    fun entries(): Set<Map.Entry<Long, BlockState>> = map.entries
    fun size(): Int = map.size
}

class BlockBuilder(
    private val level: ServerLevel,
    private val cfg: PavingConfig,
    private val snapshot: UndoSnapshot
) {
    private val mPos = BlockPos.MutableBlockPos()
    var placedCount: Int = 0
        private set

    private data class Palette(
        val deck: BlockState,
        val shoulder: BlockState,
        val centerMark: BlockState,
        val edgeMark: BlockState,
        val subBase: BlockState,
        val curb: BlockState,
        val guardrail: BlockState,
        val lampPost: BlockState,
        val lampBlock: BlockState,
        val tunnelWall: BlockState,
        val tunnelLight: BlockState,
        val viaductGirder: BlockState,
        val viaductPillar: BlockState,
        val slopeSlab: BlockState
    )

    private val palette: Palette = buildPalette()

    private fun buildPalette(): Palette {
        if (cfg.pavingMode == PavingMode.CREATE_RAIL || cfg.roadStyle == RoadStyle.CREATE_BALLAST) {
            return Palette(
                deck = Blocks.GRAVEL.defaultBlockState(),
                shoulder = Blocks.POLISHED_ANDESITE.defaultBlockState(),
                centerMark = Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
                edgeMark = Blocks.ANDESITE.defaultBlockState(),
                subBase = Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
                curb = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                guardrail = Blocks.ANDESITE_WALL.defaultBlockState(),
                lampPost = Blocks.ANDESITE_WALL.defaultBlockState(),
                lampBlock = Blocks.LANTERN.defaultBlockState(),
                tunnelWall = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                tunnelLight = Blocks.SEA_LANTERN.defaultBlockState(),
                viaductGirder = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                viaductPillar = Blocks.STONE_BRICKS.defaultBlockState(),
                slopeSlab = Blocks.ANDESITE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
            )
        }

        return when (cfg.roadStyle) {
            RoadStyle.ASPHALT -> Palette(
                deck = Blocks.BLACK_CONCRETE.defaultBlockState(),
                shoulder = Blocks.GRAY_CONCRETE.defaultBlockState(),
                centerMark = Blocks.YELLOW_CONCRETE.defaultBlockState(),
                edgeMark = Blocks.WHITE_CONCRETE.defaultBlockState(),
                subBase = Blocks.COBBLESTONE.defaultBlockState(),
                curb = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                guardrail = Blocks.STONE_BRICK_WALL.defaultBlockState(),
                lampPost = Blocks.STONE_BRICK_WALL.defaultBlockState(),
                lampBlock = Blocks.LANTERN.defaultBlockState(),
                tunnelWall = Blocks.SMOOTH_BASALT.defaultBlockState(),
                tunnelLight = Blocks.SEA_LANTERN.defaultBlockState(),
                viaductGirder = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                viaductPillar = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                slopeSlab = Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
            )
            RoadStyle.STONE_HIGHWAY -> Palette(
                deck = Blocks.STONE_BRICKS.defaultBlockState(),
                shoulder = Blocks.POLISHED_ANDESITE.defaultBlockState(),
                centerMark = Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
                edgeMark = Blocks.SMOOTH_STONE.defaultBlockState(),
                subBase = Blocks.COBBLESTONE.defaultBlockState(),
                curb = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                guardrail = Blocks.STONE_BRICK_WALL.defaultBlockState(),
                lampPost = Blocks.STONE_BRICK_WALL.defaultBlockState(),
                lampBlock = Blocks.LANTERN.defaultBlockState(),
                tunnelWall = Blocks.STONE_BRICKS.defaultBlockState(),
                tunnelLight = Blocks.GLOWSTONE.defaultBlockState(),
                viaductGirder = Blocks.STONE_BRICKS.defaultBlockState(),
                viaductPillar = Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                slopeSlab = Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
            )
            RoadStyle.COBBLE_TRAIL, RoadStyle.CREATE_BALLAST -> Palette(
                deck = Blocks.COBBLESTONE.defaultBlockState(),
                shoulder = Blocks.ANDESITE.defaultBlockState(),
                centerMark = Blocks.GRAVEL.defaultBlockState(),
                edgeMark = Blocks.COBBLESTONE.defaultBlockState(),
                subBase = Blocks.COBBLESTONE.defaultBlockState(),
                curb = Blocks.COBBLESTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                guardrail = Blocks.SPRUCE_FENCE.defaultBlockState(),
                lampPost = Blocks.SPRUCE_FENCE.defaultBlockState(),
                lampBlock = Blocks.LANTERN.defaultBlockState(),
                tunnelWall = Blocks.COBBLESTONE.defaultBlockState(),
                tunnelLight = Blocks.GLOWSTONE.defaultBlockState(),
                viaductGirder = Blocks.STONE_BRICKS.defaultBlockState(),
                viaductPillar = Blocks.COBBLESTONE.defaultBlockState(),
                slopeSlab = Blocks.COBBLESTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
            )
        }
    }

    private fun setBlockSafe(x: Int, y: Int, z: Int, state: BlockState) {
        if (y < level.minBuildHeight || y >= level.maxBuildHeight) return
        mPos.set(x, y, z)
        val old = level.getBlockState(mPos)
        if (old == state) return
        // Protect bedrock
        if (old.`is`(Blocks.BEDROCK)) return
        snapshot.record(mPos, old)
        val flags = Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE
        if (cfg.dropExcavatedBlocks && !state.isAir && !old.isAir && old.fluidState.isEmpty) {
            level.destroyBlock(mPos, true)
        }
        level.setBlock(mPos, state, flags)
        placedCount++
    }

    fun buildStation(
        stations: List<RouteStation>,
        index: Int
    ) {
        val st = stations[index]
        val prev = stations.getOrNull(index - 1)
        val next = stations.getOrNull(index + 1)
        val isSteppingUp = prev != null && st.roadY > prev.roadY
        val isSteppingDown = next != null && next.roadY < st.roadY

        val roadHalfW = cfg.roadWidth / 2.0
        val outerHalfW = roadHalfW + if (cfg.addGuardrails && (st.type == StationType.VIADUCT || st.type == StationType.CUT)) 1.0 else 0.5

        when (st.type) {
            StationType.TUNNEL -> buildTunnelSlice(st, prev, next, index)
            StationType.VIADUCT -> buildViaductSlice(st, prev, next, index, outerHalfW)
            StationType.SURFACE, StationType.CUT -> buildSurfaceSlice(st, prev, next, index, isSteppingUp, isSteppingDown)
        }
    }

    private fun buildSurfaceSlice(
        st: RouteStation,
        prev: RouteStation?,
        next: RouteStation?,
        index: Int,
        stepUp: Boolean,
        stepDown: Boolean
    ) {
        val roadHalfW = cfg.roadWidth / 2.0
        val clearHalfW = roadHalfW + if (st.type == StationType.CUT) 1.5 else 0.75
        val clearHeight = if (cfg.pavingMode == PavingMode.CREATE_RAIL) 6 else 5

        forEachOffsetPoint(st, clearHalfW, 0.5) { bx, bz, t ->
            val absT = abs(t)
            val isOnRoad = absT <= roadHalfW + 0.05

            // Clear headroom above road
            for (dy in 1..clearHeight) {
                setBlockSafe(bx, st.roadY + dy, bz, Blocks.AIR.defaultBlockState())
            }
            // Cutback slope walls if in CUT
            if (st.type == StationType.CUT && absT > roadHalfW + 0.25) {
                setBlockSafe(bx, st.roadY, bz, palette.subBase)
                setBlockSafe(bx, st.roadY + 1, bz, palette.curb)
                if (cfg.addGuardrails) {
                    setBlockSafe(bx, st.roadY + 1, bz, palette.guardrail)
                }
            } else if (isOnRoad) {
                val deckBlock = pickDeckBlock(absT, roadHalfW, index)
                setBlockSafe(bx, st.roadY, bz, deckBlock)
                // Compacted sub-base down to solid ground (max 4 deep)
                for (d in 1..4) {
                    mPos.set(bx, st.roadY - d, bz)
                    val cur = level.getBlockState(mPos)
                    if (ChunkStreamer.isTerrainSolid(cur)) break
                    setBlockSafe(bx, st.roadY - d, bz, palette.subBase)
                }
                if (cfg.pavingMode == PavingMode.ROAD && (stepUp || stepDown) && absT < roadHalfW - 0.5) {
                    // Optional smooth half-slab step at leading edge when climbing
                    // Keep full block at st.roadY so continuous riding works smoothly
                }
            }
        }

        // Embankment slope taper & optional street lights
        if (cfg.addStreetLights && index % cfg.lightInterval == 0 && cfg.roadWidth >= 3) {
            placeLampPost(st, roadHalfW + 1.0)
        }
    }

    private fun buildViaductSlice(
        st: RouteStation,
        prev: RouteStation?,
        next: RouteStation?,
        index: Int,
        outerHalfW: Double
    ) {
        val roadHalfW = cfg.roadWidth / 2.0

        forEachOffsetPoint(st, outerHalfW + 0.5, 0.5) { bx, bz, t ->
            val absT = abs(t)
            val isEdge = absT > roadHalfW + 0.05

            // Clear deck headroom
            val clearH = if (cfg.pavingMode == PavingMode.CREATE_RAIL) 6 else 5
            for (dy in 1..clearH) {
                if (!(isEdge && dy == 1 && cfg.addGuardrails)) {
                    setBlockSafe(bx, st.roadY + dy, bz, Blocks.AIR.defaultBlockState())
                }
            }

            if (isEdge) {
                setBlockSafe(bx, st.roadY, bz, palette.viaductGirder)
                setBlockSafe(bx, st.roadY - 1, bz, palette.viaductGirder)
                if (cfg.addGuardrails) {
                    setBlockSafe(bx, st.roadY + 1, bz, palette.guardrail)
                }
            } else {
                setBlockSafe(bx, st.roadY, bz, pickDeckBlock(absT, roadHalfW, index))
                setBlockSafe(bx, st.roadY - 1, bz, palette.viaductGirder)
                // Under-deck central structural spine
                if (absT <= max(1.0, roadHalfW - 1.5)) {
                    setBlockSafe(bx, st.roadY - 2, bz, palette.viaductGirder)
                }
            }
        }

        val isViaductStart = prev == null || prev.type != StationType.VIADUCT
        val isViaductEnd = next == null || next.type != StationType.VIADUCT
        val isPierStation = isViaductStart || isViaductEnd || (index % cfg.viaductPillarSpacing == 0)

        if (isPierStation && st.roadY - st.groundY >= 2) {
            buildViaductPier(st, roadHalfW)
        }

        if (cfg.addStreetLights && index % cfg.lightInterval == 0) {
            placeLampPost(st, outerHalfW)
        }
    }

    private fun buildViaductPier(st: RouteStation, roadHalfW: Double) {
        val pierOffsets = if (cfg.roadWidth >= 7) {
            doubleArrayOf(-(roadHalfW - 1.0), roadHalfW - 1.0)
        } else {
            doubleArrayOf(0.0)
        }

        for (off in pierOffsets) {
            val cx = (st.x + st.px * off).roundToInt()
            val cz = (st.z + st.pz * off).roundToInt()

            // Flared capital directly beneath girder
            for (dx in -1..1) {
                for (dz in -1..1) {
                    setBlockSafe(cx + dx, st.roadY - 2, cz + dz, palette.viaductGirder)
                }
            }

            // Pier shaft down to solid riverbed/ground (+ 1 block anchor)
            var bottomY = st.roadY - 3
            for (y in (st.roadY - 3) downTo level.minBuildHeight) {
                bottomY = y
                mPos.set(cx, y, cz)
                val existing = level.getBlockState(mPos)
                val isSolid = ChunkStreamer.isTerrainSolid(existing)
                setBlockSafe(cx, y, cz, palette.viaductPillar)
                if (cfg.roadWidth >= 5) {
                    setBlockSafe(cx + 1, y, cz, palette.viaductPillar)
                    setBlockSafe(cx, y, cz + 1, palette.viaductPillar)
                }
                if (isSolid && y <= st.groundY) {
                    // Footing cap
                    for (fx in -1..1) {
                        for (fz in -1..1) {
                            setBlockSafe(cx + fx, y, cz + fz, palette.viaductGirder)
                        }
                    }
                    break
                }
            }
            // Extra anchor below footing
            setBlockSafe(cx, bottomY - 1, cz, palette.viaductGirder)
        }
    }

    private fun buildTunnelSlice(
        st: RouteStation,
        prev: RouteStation?,
        next: RouteStation?,
        index: Int
    ) {
        val innerW = cfg.effectiveTunnelWidth()
        val innerH = cfg.effectiveTunnelHeight()
        val innerHalfW = innerW / 2.0
        val shellHalfW = innerHalfW + 1.2
        val roadHalfW = cfg.roadWidth / 2.0

        val isPortal = (prev == null || prev.type != StationType.TUNNEL) ||
            (next == null || next.type != StationType.TUNNEL)
        val isUnderwater = st.hasLiquid && cfg.riverStrategy == RiverStrategy.SHIELD_TUNNEL

        // Sample transverse offsets + sub-steps along tangent to eliminate any diagonal shell leaks
        for (fwdStep in 0..1) {
            val sx = st.x + st.tx * (fwdStep * 0.5)
            val sz = st.z + st.tz * (fwdStep * 0.5)

            var t = -shellHalfW
            while (t <= shellHalfW + 0.01) {
                val bx = (sx + st.px * t).roundToInt()
                val bz = (sz + st.pz * t).roundToInt()
                val absT = abs(t)

                for (dy in -1..(innerH + 1)) {
                    val wy = st.roadY + dy
                    val inside = isInsideTunnelSection(absT, dy, innerHalfW, innerH)
                    val shell = !inside && isInsideTunnelSection(absT, dy, shellHalfW, innerH + 1)

                    if (dy == -1) {
                        // Tunnel invert / foundation slab
                        if (absT <= shellHalfW) {
                            setBlockSafe(bx, wy, bz, palette.subBase)
                        }
                    } else if (dy == 0) {
                        if (absT <= roadHalfW + 0.05) {
                            setBlockSafe(bx, wy, bz, pickDeckBlock(absT, roadHalfW, index))
                        } else if (absT <= innerHalfW + 0.05) {
                            setBlockSafe(bx, wy, bz, palette.shoulder)
                        } else if (shell) {
                            setBlockSafe(bx, wy, bz, palette.tunnelWall)
                        }
                    } else if (inside) {
                        // Clear air inside bore
                        setBlockSafe(bx, wy, bz, Blocks.AIR.defaultBlockState())
                    } else if (shell) {
                        val isSkylight = isUnderwater && dy >= innerH && absT <= 1.2
                        val isCeilingLight = (index % cfg.lightInterval == 0) && dy >= innerH && absT <= 0.75
                        val wallBlock = when {
                            isCeilingLight -> palette.tunnelLight
                            isSkylight -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()
                            isPortal -> palette.viaductGirder
                            else -> palette.tunnelWall
                        }
                        setBlockSafe(bx, wy, bz, wallBlock)
                    }
                }
                t += 0.5
            }
        }

        // Portal warning lights at tunnel entrances/exits
        if (isPortal) {
            for (side in intArrayOf(-1, 1)) {
                val lx = (st.x + st.px * (innerHalfW + 0.6) * side).roundToInt()
                val lz = (st.z + st.pz * (innerHalfW + 0.6) * side).roundToInt()
                setBlockSafe(lx, st.roadY + 3, lz, palette.tunnelLight)
            }
        }
    }

    private fun isInsideTunnelSection(absT: Double, dy: Int, halfW: Double, height: Int): Boolean {
        if (dy <= 0 || dy > height || absT > halfW) return false
        return when (cfg.tunnelShape) {
            TunnelShape.RECTANGULAR -> {
                !(dy == height && absT > halfW - 0.8)
            }
            TunnelShape.ARCH -> {
                val wallH = max(2.0, height * 0.45)
                if (dy <= wallH) {
                    true
                } else {
                    val ry = (dy - wallH) / max(1.0, height - wallH)
                    val rx = absT / max(1.0, halfW)
                    (rx * rx + ry * ry) <= 1.02
                }
            }
            TunnelShape.CIRCULAR -> {
                val centerY = height * 0.48
                val rY = height * 0.54
                val rx = absT / max(1.0, halfW)
                val ry = (dy - centerY) / max(1.0, rY)
                (rx * rx + ry * ry) <= 1.02
            }
        }
    }

    private fun pickDeckBlock(absT: Double, roadHalfW: Double, index: Int): BlockState {
        if (cfg.pavingMode == PavingMode.CREATE_RAIL || cfg.roadStyle == RoadStyle.CREATE_BALLAST) {
            // Sleepers-spaced ballast accent while leaving top flat for player-placed Create Tracks
            return when {
                absT <= 0.6 -> if (index % 2 == 0) palette.centerMark else palette.deck
                absT >= roadHalfW - 0.6 -> palette.shoulder
                else -> palette.deck
            }
        }

        if (!cfg.addLaneMarkings) {
            return if (absT >= roadHalfW - 0.5) palette.shoulder else palette.deck
        }

        val isCenter = absT <= 0.55 && cfg.roadWidth >= 5
        val isEdgeLine = cfg.roadWidth >= 7 && absT in (roadHalfW - 1.2)..(roadHalfW - 0.45)
        return when {
            isCenter && (index % 6 < 3) -> palette.centerMark
            isEdgeLine -> palette.edgeMark
            absT >= roadHalfW - 0.4 -> palette.shoulder
            else -> palette.deck
        }
    }

    private fun placeLampPost(st: RouteStation, offset: Double) {
        val side = if (((st.x.toInt() + st.z.toInt()) and 1) == 0) 1.0 else 1.0
        val lx = (st.x + st.px * offset * side).roundToInt()
        val lz = (st.z + st.pz * offset * side).roundToInt()
        setBlockSafe(lx, st.roadY + 1, lz, palette.lampPost)
        setBlockSafe(lx, st.roadY + 2, lz, palette.lampPost)
        setBlockSafe(lx, st.roadY + 3, lz, palette.lampPost)
        setBlockSafe(lx, st.roadY + 4, lz, palette.lampBlock)
    }

    private inline fun forEachOffsetPoint(
        st: RouteStation,
        halfW: Double,
        step: Double,
        action: (bx: Int, bz: Int, offset: Double) -> Unit
    ) {
        val seen = HashSet<Long>(32)
        for (fwdStep in 0..1) {
            val sx = st.x + st.tx * (fwdStep * 0.5)
            val sz = st.z + st.tz * (fwdStep * 0.5)
            var t = -halfW
            while (t <= halfW + 0.01) {
                val bx = (sx + st.px * t).roundToInt()
                val bz = (sz + st.pz * t).roundToInt()
                val key = (bx.toLong() shl 32) or (bz.toLong() and 0xFFFFFFFFL)
                if (seen.add(key)) {
                    action(bx, bz, t)
                }
                t += step
            }
        }
    }
}
