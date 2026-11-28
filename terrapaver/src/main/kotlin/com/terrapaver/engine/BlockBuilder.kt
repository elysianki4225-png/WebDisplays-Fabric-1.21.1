package com.terrapaver.engine

import com.terrapaver.config.LampLayout
import com.terrapaver.config.LampStyle
import com.terrapaver.config.PavingConfig
import com.terrapaver.config.PavingMode
import com.terrapaver.config.RiverStrategy
import com.terrapaver.config.RoadStyle
import com.terrapaver.config.TunnelShape
import com.terrapaver.config.TunnelWallStyle
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LanternBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.SlabType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class UndoSnapshot(val maxEntries: Int = 260_000) {
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
    private val snapshot: UndoSnapshot,
    private val streamer: ChunkStreamer
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
        val lampBase: BlockState,
        val lampPost: BlockState,
        val lampArm: BlockState,
        val lampHead: BlockState,
        val tunnelWall: BlockState,
        val tunnelLight: BlockState,
        val viaductGirder: BlockState,
        val viaductPillar: BlockState,
        val slopeSlab: BlockState
    )

    private val palette: Palette = buildPalette()

    private fun resolveTunnelWallBlock(): BlockState = when (cfg.tunnelWallStyle) {
        TunnelWallStyle.SMOOTH_BASALT -> Blocks.SMOOTH_BASALT.defaultBlockState()
        TunnelWallStyle.STONE_BRICKS -> Blocks.STONE_BRICKS.defaultBlockState()
        TunnelWallStyle.POLISHED_DEEPSLATE -> Blocks.POLISHED_DEEPSLATE.defaultBlockState()
        TunnelWallStyle.WHITE_CONCRETE -> Blocks.WHITE_CONCRETE.defaultBlockState()
    }

    private fun buildPalette(): Palette {
        val chosenTunnelWall = resolveTunnelWallBlock()
        val topSlab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)
        val andesiteTopSlab = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)
        val hangingLantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)

        val (lampBase, lampPost, lampArm, lampHead) = when (cfg.lampStyle) {
            LampStyle.MODERN_CANTILEVER -> arrayOf(
                Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                Blocks.STONE_BRICK_WALL.defaultBlockState(),
                topSlab,
                hangingLantern
            )
            LampStyle.CLASSIC_POST -> arrayOf(
                Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
                Blocks.SPRUCE_FENCE.defaultBlockState(),
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                hangingLantern
            )
            LampStyle.INDUSTRIAL_BEACON -> arrayOf(
                Blocks.POLISHED_ANDESITE.defaultBlockState(),
                Blocks.ANDESITE_WALL.defaultBlockState(),
                andesiteTopSlab,
                Blocks.SEA_LANTERN.defaultBlockState()
            )
        }

        if (cfg.pavingMode == PavingMode.CREATE_RAIL || cfg.roadStyle == RoadStyle.CREATE_BALLAST) {
            return Palette(
                deck = Blocks.GRAVEL.defaultBlockState(),
                shoulder = Blocks.POLISHED_ANDESITE.defaultBlockState(),
                centerMark = Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
                edgeMark = Blocks.ANDESITE.defaultBlockState(),
                subBase = Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
                curb = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                guardrail = Blocks.ANDESITE_WALL.defaultBlockState(),
                lampBase = lampBase,
                lampPost = lampPost,
                lampArm = lampArm,
                lampHead = lampHead,
                tunnelWall = chosenTunnelWall,
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
                lampBase = lampBase,
                lampPost = lampPost,
                lampArm = lampArm,
                lampHead = lampHead,
                tunnelWall = chosenTunnelWall,
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
                lampBase = lampBase,
                lampPost = lampPost,
                lampArm = lampArm,
                lampHead = lampHead,
                tunnelWall = chosenTunnelWall,
                tunnelLight = Blocks.SEA_LANTERN.defaultBlockState(),
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
                lampBase = lampBase,
                lampPost = lampPost,
                lampArm = lampArm,
                lampHead = lampHead,
                tunnelWall = chosenTunnelWall,
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
        if (old.`is`(Blocks.BEDROCK)) return
        snapshot.record(mPos, old)
        val flags = Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE
        if (cfg.dropExcavatedBlocks && !state.isAir && !old.isAir && old.fluidState.isEmpty) {
            level.destroyBlock(mPos, true)
        }
        level.setBlock(mPos, state, flags)
        placedCount++
    }

    fun buildStation(stations: List<RouteStation>, index: Int) {
        val st = stations[index]
        val prev = stations.getOrNull(index - 1)
        val next = stations.getOrNull(index + 1)
        val roadHalfW = cfg.roadWidth / 2.0
        val outerHalfW = roadHalfW + if (cfg.addGuardrails && (st.type == StationType.VIADUCT || st.type == StationType.CUT)) 1.0 else 0.5

        when (st.type) {
            StationType.TUNNEL -> buildTunnelSlice(st, prev, next, index)
            StationType.VIADUCT -> buildViaductSlice(st, prev, next, index, outerHalfW)
            StationType.SURFACE, StationType.CUT -> buildSurfaceSlice(st, prev, next, index)
        }
    }

    private fun buildSurfaceSlice(
        st: RouteStation,
        prev: RouteStation?,
        next: RouteStation?,
        index: Int
    ) {
        val roadHalfW = cfg.roadWidth / 2.0
        val clearHalfW = roadHalfW + if (st.type == StationType.CUT) 1.5 else 0.75
        val clearHeight = if (cfg.pavingMode == PavingMode.CREATE_RAIL) 6 else 5
        val stepUpHere = prev != null && st.roadY > prev.roadY
        val stepDownNext = next != null && next.roadY < st.roadY

        forEachOffsetPoint(st, clearHalfW, 0.5) { bx, bz, t ->
            val absT = abs(t)
            val isOnRoad = absT <= roadHalfW + 0.05

            for (dy in 1..clearHeight) {
                setBlockSafe(bx, st.roadY + dy, bz, Blocks.AIR.defaultBlockState())
            }
            if (st.type == StationType.CUT && absT > roadHalfW + 0.25) {
                setBlockSafe(bx, st.roadY, bz, palette.subBase)
                if (cfg.addGuardrails) {
                    setBlockSafe(bx, st.roadY + 1, bz, palette.guardrail)
                }
            } else if (isOnRoad) {
                val deckBlock = pickDeckBlock(absT, roadHalfW, index)
                setBlockSafe(bx, st.roadY, bz, deckBlock)
                // Smooth half-slab slope transition on road grade steps
                if (cfg.smoothSlabs && cfg.pavingMode == PavingMode.ROAD && (stepUpHere || stepDownNext) && absT < roadHalfW - 0.3) {
                    setBlockSafe(bx, st.roadY - 1, bz, palette.subBase)
                }
                for (d in 1..4) {
                    mPos.set(bx, st.roadY - d, bz)
                    val cur = level.getBlockState(mPos)
                    if (ChunkStreamer.isTerrainSolid(cur)) break
                    setBlockSafe(bx, st.roadY - d, bz, palette.subBase)
                }
            }
        }

        // Smooth natural earthwork terrain blending beyond the roadbed/cut so road integrates naturally into Earth!
        if (cfg.terrainBlending) {
            applyTerrainBlending(st, clearHalfW)
        }

        placeStationStreetLights(st, index, roadHalfW + 0.85, isViaduct = false)
    }

    private fun isProtectedRoadBlock(state: BlockState): Boolean {
        return state == palette.deck ||
            state == palette.shoulder ||
            state == palette.centerMark ||
            state == palette.edgeMark ||
            state == palette.curb ||
            state == palette.guardrail ||
            state == palette.lampBase ||
            state == palette.viaductGirder
    }

    private fun applyTerrainBlending(st: RouteStation, edgeHalfW: Double) {
        val blendW = cfg.blendRadius

        for (side in intArrayOf(-1, 1)) {
            for (step in 1..blendW) {
                val t = (edgeHalfW + step.toDouble()) * side
                val bx = (st.x + st.px * t).roundToInt()
                val bz = (st.z + st.pz * t).roundToInt()
                mPos.set(bx, st.roadY, bz)
                if (isProtectedRoadBlock(level.getBlockState(mPos))) continue
                val col = streamer.sampleColumn(bx, bz)
                if (col.hasLiquid) continue

                val desiredTopY = when {
                    // Embankment sloping down gently toward ground
                    col.groundY < st.roadY - step -> st.roadY - step
                    // Cut hillside terraced gently upward away from road
                    col.topSolidY > st.roadY + step + 1 -> st.roadY + step + 1
                    else -> continue
                }

                val naturalTop = col.surfaceBlock
                val naturalSub = if (naturalTop.`is`(Blocks.GRASS_BLOCK)) Blocks.DIRT.defaultBlockState() else naturalTop

                if (col.groundY < st.roadY - step) {
                    // Fill embankment smoothly
                    for (y in (col.groundY + 1)..desiredTopY) {
                        mPos.set(bx, y, bz)
                        if (isProtectedRoadBlock(level.getBlockState(mPos))) break
                        setBlockSafe(bx, y, bz, if (y == desiredTopY) naturalTop else naturalSub)
                    }
                    if (col.hasSnowCover && naturalTop.`is`(Blocks.GRASS_BLOCK)) {
                        setBlockSafe(bx, desiredTopY + 1, bz, Blocks.SNOW.defaultBlockState())
                    }
                } else {
                    // Grade down steep cut face into smooth 45-deg terraced slope
                    for (y in (desiredTopY + 1)..(col.topSolidY + 2)) {
                        mPos.set(bx, y, bz)
                        if (isProtectedRoadBlock(level.getBlockState(mPos))) continue
                        setBlockSafe(bx, y, bz, Blocks.AIR.defaultBlockState())
                    }
                    setBlockSafe(bx, desiredTopY, bz, naturalTop)
                    if (col.hasSnowCover && naturalTop.`is`(Blocks.GRASS_BLOCK)) {
                        setBlockSafe(bx, desiredTopY + 1, bz, Blocks.SNOW.defaultBlockState())
                    }
                }
            }
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

        placeStationStreetLights(st, index, outerHalfW, isViaduct = true)
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

            for (dx in -1..1) {
                for (dz in -1..1) {
                    setBlockSafe(cx + dx, st.roadY - 2, cz + dz, palette.viaductGirder)
                }
            }

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
                    for (fx in -1..1) {
                        for (fz in -1..1) {
                            setBlockSafe(cx + fx, y, cz + fz, palette.viaductGirder)
                        }
                    }
                    break
                }
            }
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
        val placeWalls = cfg.addTunnelWalls || isUnderwater
        val isLightStation = cfg.addTunnelLights && (index % cfg.tunnelLightInterval == 0)
        val wallLightDy = (innerH / 2).coerceIn(2, max(2, innerH - 1))

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
                    val shell = !inside && (
                        isInsideTunnelSection(absT, dy, shellHalfW, innerH + 1) ||
                            isInsideTunnelSection(absT, max(1, dy - 1), shellHalfW, innerH + 1)
                        )

                    if (dy == -1) {
                        if (absT <= shellHalfW) {
                            setBlockSafe(bx, wy, bz, palette.subBase)
                        }
                    } else if (dy == 0) {
                        if (absT <= roadHalfW + 0.05) {
                            setBlockSafe(bx, wy, bz, pickDeckBlock(absT, roadHalfW, index))
                        } else if (absT <= innerHalfW + 0.05) {
                            setBlockSafe(bx, wy, bz, palette.shoulder)
                        } else if (placeWalls && absT <= shellHalfW) {
                            setBlockSafe(bx, wy, bz, palette.tunnelWall)
                        }
                    } else if (inside) {
                        val isCeilingLight = isLightStation && dy == innerH && absT <= 0.75
                        if (isCeilingLight) {
                            setBlockSafe(bx, wy, bz, palette.tunnelLight)
                        } else {
                            setBlockSafe(bx, wy, bz, Blocks.AIR.defaultBlockState())
                        }
                    } else if (shell) {
                        val isSkylight = isUnderwater && dy >= innerH && absT <= 1.2
                        val isCrownShellLight = isLightStation && dy >= innerH && absT <= 0.85
                        val isSideWallLight = isLightStation && dy == wallLightDy && absT >= innerHalfW - 0.1
                        when {
                            isCrownShellLight || isSideWallLight -> setBlockSafe(bx, wy, bz, palette.tunnelLight)
                            isSkylight -> setBlockSafe(bx, wy, bz, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState())
                            placeWalls -> setBlockSafe(
                                bx,
                                wy,
                                bz,
                                if (isPortal) palette.viaductGirder else palette.tunnelWall
                            )
                        }
                    }
                }
                t += 0.5
            }
        }

        if (isPortal && cfg.addTunnelLights) {
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
            TunnelShape.RECTANGULAR -> !(dy == height && absT > halfW - 0.8)
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
            return when {
                absT <= 0.6 -> if (index % 2 == 0) palette.centerMark else palette.deck
                absT >= roadHalfW - 0.6 -> palette.shoulder
                else -> palette.deck
            }
        }

        if (!cfg.addLaneMarkings) {
            return if (absT >= roadHalfW - 0.5) palette.shoulder else palette.deck
        }

        // Smooth centerline markings (only place clean dashed line when near true center <= 0.45)
        val isCenter = cfg.roadWidth >= 5 && absT <= 0.48
        val isEdgeLine = cfg.roadWidth >= 7 && absT in (roadHalfW - 1.15)..(roadHalfW - 0.45)
        return when {
            isCenter && (index % 6 < 3) -> palette.centerMark
            isEdgeLine -> palette.edgeMark
            absT >= roadHalfW - 0.42 -> palette.shoulder
            else -> palette.deck
        }
    }

    private fun placeStationStreetLights(st: RouteStation, index: Int, edgeOffset: Double, isViaduct: Boolean) {
        val layout = cfg.activeLampLayout()
        if (layout == LampLayout.OFF) return
        val interval = cfg.lightInterval
        val halfInterval = max(2, interval / 2)

        when (layout) {
            LampLayout.DUAL_PAIRED -> {
                if (index % interval == 0) {
                    placeArchitecturalLamp(st, edgeOffset, 1, isViaduct)
                    placeArchitecturalLamp(st, edgeOffset, -1, isViaduct)
                }
            }
            LampLayout.DUAL_STAGGERED -> {
                if (index % interval == 0) {
                    placeArchitecturalLamp(st, edgeOffset, 1, isViaduct)
                } else if (index % interval == halfInterval) {
                    placeArchitecturalLamp(st, edgeOffset, -1, isViaduct)
                }
            }
            LampLayout.SINGLE_SIDE -> {
                if (index % interval == 0) {
                    placeArchitecturalLamp(st, edgeOffset, 1, isViaduct)
                }
            }
            LampLayout.OFF -> Unit
        }
    }

    private fun placeArchitecturalLamp(st: RouteStation, edgeOffset: Double, sideSign: Int, isViaduct: Boolean) {
        val postOff = edgeOffset * sideSign
        val armOff = (edgeOffset - 1.0) * sideSign

        val lx = (st.x + st.px * postOff).roundToInt()
        val lz = (st.z + st.pz * postOff).roundToInt()
        val ax = (st.x + st.px * armOff).roundToInt()
        val az = (st.z + st.pz * armOff).roundToInt()

        // Ground foundation column beneath lamp plinth if on slope or beside viaduct
        if (!isViaduct) {
            for (d in 1..4) {
                mPos.set(lx, st.roadY - d, lz)
                if (ChunkStreamer.isTerrainSolid(level.getBlockState(mPos))) break
                setBlockSafe(lx, st.roadY - d, lz, palette.subBase)
            }
            setBlockSafe(lx, st.roadY, lz, palette.lampBase)
        } else {
            setBlockSafe(lx, st.roadY + 1, lz, palette.lampBase)
        }

        val baseY = if (isViaduct) st.roadY + 2 else st.roadY + 1
        setBlockSafe(lx, baseY, lz, palette.lampBase)
        setBlockSafe(lx, baseY + 1, lz, palette.lampPost)
        setBlockSafe(lx, baseY + 2, lz, palette.lampPost)
        setBlockSafe(lx, baseY + 3, lz, palette.lampPost)

        if (cfg.lampStyle == LampStyle.INDUSTRIAL_BEACON) {
            setBlockSafe(lx, baseY + 4, lz, palette.lampBase)
            setBlockSafe(ax, baseY + 4, az, palette.lampArm)
            setBlockSafe(ax, baseY + 3, az, palette.lampHead)
        } else {
            // Architectural horizontal cantilever arm reaching inward over road shoulder + hanging lantern!
            setBlockSafe(lx, baseY + 4, lz, palette.lampBase)
            setBlockSafe(ax, baseY + 4, az, palette.lampArm)
            setBlockSafe(ax, baseY + 3, az, palette.lampHead)
        }
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
