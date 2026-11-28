package com.terrapaver.engine

import com.terrapaver.config.AlignmentMode
import com.terrapaver.config.ObstacleMode
import com.terrapaver.config.PavingConfig
import com.terrapaver.config.PavingMode
import com.terrapaver.config.RiverStrategy
import net.minecraft.core.BlockPos
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class StationType(val code: Byte) {
    SURFACE(0),
    CUT(1),
    TUNNEL(2),
    VIADUCT(3)
}

data class RouteStation(
    val x: Double,
    val z: Double,
    val tx: Double,
    val tz: Double,
    val px: Double,
    val pz: Double,
    val roadY: Int,
    val groundY: Int,
    val waterTopY: Int,
    val hasLiquid: Boolean,
    val type: StationType,
    val isHalfSlab: Boolean = false,
    val arcLength: Double = 0.0
)

/**
 * TerraPaver AI v2 — High-resolution 3D Grade-Aware Civil Engineering Route Planner.
 *
 * Pipeline per leg:
 * 1. High-density 4m-8m Terrain Intelligence Field (ground height, liquid/frozen rivers, local slope, ridge prominence).
 * 2. Coupled 3D State-Space A* (x, z, heading8, roadY) weighing cut/fill, tunnel bore, viaducts, passes, and curvature.
 * 3. Douglas-Peucker simplification + Centripetal Catmull-Rom G2 spline + 1.0m arc-length resampling.
 * 4. 40-iteration variational vertical profile solver (parabolic vertical curves + grade clamping + median tunnel filter).
 */
class IncrementalRoutePlanner(
    private val streamer: ChunkStreamer,
    private val waypoints: List<BlockPos>,
    private val cfg: PavingConfig
) {
    private data class Point2D(val x: Double, val z: Double)

    private enum class Stage {
        INIT_LEG,
        LEG_FIELD_SAMPLE,
        LEG_FIELD_ANALYZE,
        LEG_SOLVE_3D,
        RESAMPLE,
        SURVEY_STATIONS,
        OPTIMIZE_PROFILE,
        DONE
    }

    private var stage = Stage.INIT_LEG
    private var legIndex = 0
    private val allPathNodes = ArrayList<Point2D>()
    private var rawStations = ArrayList<Point2D>()
    private var samples = ArrayList<ColumnSample>()
    private var finalStations = ArrayList<RouteStation>()
    private var surveyCursor = 0

    // High-resolution terrain intelligence field for the current leg
    private var legStart = BlockPos.ZERO
    private var legEnd = BlockPos.ZERO
    private var gridStep = 4
    private var minGx = 0
    private var maxGx = 0
    private var minGz = 0
    private var maxGz = 0
    private var gridW = 0
    private var gridH = 0
    private var gridGround = IntArray(0)
    private var gridWater = BooleanArray(0)
    private var gridWaterTop = IntArray(0)
    private var gridSlope = FloatArray(0)
    private var gridProminence = FloatArray(0)
    private var gridSampleCursor = 0
    private var totalGridCells = 0

    var progress: Float = 0f
        private set

    fun isDone(): Boolean = stage == Stage.DONE

    fun getStations(): List<RouteStation> = finalStations

    fun stepBudget(budgetOps: Int) {
        when (stage) {
            Stage.INIT_LEG -> initLeg()
            Stage.LEG_FIELD_SAMPLE -> stepGridSampling(budgetOps)
            Stage.LEG_FIELD_ANALYZE -> analyzeTerrainField()
            Stage.LEG_SOLVE_3D -> solve3DRouting()
            Stage.RESAMPLE -> resampleCenterline()
            Stage.SURVEY_STATIONS -> stepSurveyStations(budgetOps)
            Stage.OPTIMIZE_PROFILE -> optimizeProfile()
            Stage.DONE -> Unit
        }
    }

    private fun initLeg() {
        if (waypoints.size < 2 || legIndex >= waypoints.size - 1) {
            if (allPathNodes.isEmpty() && waypoints.isNotEmpty()) {
                allPathNodes.add(Point2D(waypoints.first().x + 0.5, waypoints.first().z + 0.5))
            }
            stage = Stage.RESAMPLE
            progress = 0.42f
            return
        }

        legStart = waypoints[legIndex]
        legEnd = waypoints[legIndex + 1]
        val dx = (legEnd.x - legStart.x).toDouble()
        val dz = (legEnd.z - legStart.z).toDouble()
        val dist = hypot(dx, dz)

        if (cfg.alignmentMode == AlignmentMode.DIRECT || dist < 16.0) {
            appendLegNodes(
                listOf(
                    Point2D(legStart.x + 0.5, legStart.z + 0.5),
                    Point2D(legEnd.x + 0.5, legEnd.z + 0.5)
                )
            )
            legIndex++
            progress = 0.40f * (legIndex.toFloat() / max(1, waypoints.size - 1).toFloat())
            return
        }

        // High-resolution terrain grid for deep AI understanding
        gridStep = when {
            dist < 180.0 -> 4
            dist < 480.0 -> 6
            dist < 900.0 -> 8
            else -> max(12, (dist / 120.0).toInt())
        }
        val pad = min(160, max(56, (dist * 0.34).toInt()))
        val minX = min(legStart.x, legEnd.x) - pad
        val maxX = max(legStart.x, legEnd.x) + pad
        val minZ = min(legStart.z, legEnd.z) - pad
        val maxZ = max(legStart.z, legEnd.z) + pad

        minGx = Math.floorDiv(minX, gridStep)
        maxGx = Math.floorDiv(maxX, gridStep)
        minGz = Math.floorDiv(minZ, gridStep)
        maxGz = Math.floorDiv(maxZ, gridStep)
        gridW = (maxGx - minGx + 1).coerceAtLeast(5)
        gridH = (maxGz - minGz + 1).coerceAtLeast(5)
        totalGridCells = gridW * gridH
        gridGround = IntArray(totalGridCells)
        gridWater = BooleanArray(totalGridCells)
        gridWaterTop = IntArray(totalGridCells)
        gridSlope = FloatArray(totalGridCells)
        gridProminence = FloatArray(totalGridCells)
        gridSampleCursor = 0
        stage = Stage.LEG_FIELD_SAMPLE
    }

    private fun stepGridSampling(budgetOps: Int) {
        val ops = max(32, budgetOps)
        var done = 0
        while (gridSampleCursor < totalGridCells && done < ops) {
            val iz = gridSampleCursor / gridW
            val ix = gridSampleCursor % gridW
            val wx = (minGx + ix) * gridStep + gridStep / 2
            val wz = (minGz + iz) * gridStep + gridStep / 2
            val sample = streamer.sampleColumn(wx, wz)
            gridGround[gridSampleCursor] = sample.groundY
            gridWater[gridSampleCursor] = sample.hasLiquid
            gridWaterTop[gridSampleCursor] = sample.waterTopY
            gridSampleCursor++
            done++
        }
        val legBase = legIndex.toFloat() / max(1, waypoints.size - 1).toFloat()
        val legSpan = 1f / max(1, waypoints.size - 1).toFloat()
        progress = 0.32f * (legBase + legSpan * (gridSampleCursor.toFloat() / totalGridCells.coerceAtLeast(1)))
        if (gridSampleCursor >= totalGridCells) {
            stage = Stage.LEG_FIELD_ANALYZE
        }
    }

    private fun analyzeTerrainField() {
        // Compute local terrain slope magnitude & saddle/ridge prominence across the field
        for (iz in 0 until gridH) {
            for (ix in 0 until gridW) {
                val idx = iz * gridW + ix
                val gL = gridGround[iz * gridW + max(0, ix - 1)]
                val gR = gridGround[iz * gridW + min(gridW - 1, ix + 1)]
                val gU = gridGround[max(0, iz - 1) * gridW + ix]
                val gD = gridGround[min(gridH - 1, iz + 1) * gridW + ix]
                val gxSlope = abs(gR - gL) / (2.0 * gridStep)
                val gzSlope = abs(gD - gU) / (2.0 * gridStep)
                gridSlope[idx] = hypot(gxSlope, gzSlope).toFloat()

                // Local 5x5 neighborhood mean vs cell height -> negative = valley/pass, positive = peak/ridge
                var sum = 0
                var cnt = 0
                for (dz in -2..2) {
                    val nz = (iz + dz).coerceIn(0, gridH - 1)
                    for (dx in -2..2) {
                        val nx = (ix + dx).coerceIn(0, gridW - 1)
                        sum += gridGround[nz * gridW + nx]
                        cnt++
                    }
                }
                val mean = sum.toFloat() / cnt.toFloat()
                gridProminence[idx] = (gridGround[idx] - mean)
            }
        }
        stage = Stage.LEG_SOLVE_3D
    }

    private fun solve3DRouting() {
        val dx = (legEnd.x - legStart.x).toDouble()
        val dz = (legEnd.z - legStart.z).toDouble()
        val dist = hypot(dx, dz).coerceAtLeast(1.0)

        // Check straight-line terrain profile first
        var maxRelief = 0
        var highGain = 0
        var maxSlopeAlongLine = 0f
        var waterCells = 0
        val lineSamples = max(8, (dist / gridStep).toInt())
        for (k in 0..lineSamples) {
            val t = k.toDouble() / lineSamples.toDouble()
            val wx = (legStart.x + dx * t).roundToInt()
            val wz = (legStart.z + dz * t).roundToInt()
            val gx = (Math.floorDiv(wx, gridStep) - minGx).coerceIn(0, gridW - 1)
            val gz = (Math.floorDiv(wz, gridStep) - minGz).coerceIn(0, gridH - 1)
            val idx = gz * gridW + gx
            val gy = gridGround[idx]
            val baseLine = legStart.y + (legEnd.y - legStart.y) * t
            val gain = (gy - baseLine).roundToInt()
            if (gain > highGain) highGain = gain
            maxRelief = max(maxRelief, abs(gain))
            maxSlopeAlongLine = max(maxSlopeAlongLine, gridSlope[idx])
            if (gridWater[idx]) waterCells++
        }

        // Flat plains or calm river crossings stay dead-straight & architectural
        val maxSlope = 1.0 / cfg.maxSlopeRun.toDouble()
        val dyAbs = abs(legEnd.y - legStart.y).toDouble()
        val neededSlope = dyAbs / dist

        if (cfg.alignmentMode == AlignmentMode.AI_AUTO && highGain <= 5 && maxRelief <= 7 &&
            maxSlopeAlongLine < 0.35f && neededSlope <= maxSlope * 0.92
        ) {
            appendLegNodes(
                listOf(
                    Point2D(legStart.x + 0.5, legStart.z + 0.5),
                    Point2D(legEnd.x + 0.5, legEnd.z + 0.5)
                )
            )
            legIndex++
            stage = Stage.INIT_LEG
            return
        }

        // Steep-slope solver ("如果坡度太斜也想办法"):
        // Whenever the direct climb exceeds maxSlope (or explicit CONTOUR_SWITCHBACK is active),
        // synthesize a wide-radius, non-overlapping switchback / horseshoe ramp so grade stays <= maxSlope!
        val tooSteepDirect = neededSlope > maxSlope * 0.95 && dyAbs >= 4.0 && waterCells < lineSamples / 2
        val wantsSwitchback = cfg.alignmentMode == AlignmentMode.CONTOUR_SWITCHBACK && highGain >= 12 && dist >= 45.0 && waterCells < lineSamples / 3
        if (tooSteepDirect || wantsSwitchback) {
            val switchback = buildGentleSwitchback(dx, dz, dist, dyAbs, maxSlope, highGain)
            if (switchback != null) {
                appendLegNodes(switchback)
                legIndex++
                stage = Stage.INIT_LEG
                return
            }
        }

        // Coupled 3D State-Space A*: state = cellIdx * 8 + headingDir(0..7)
        val sx = (Math.floorDiv(legStart.x, gridStep) - minGx).coerceIn(0, gridW - 1)
        val sz = (Math.floorDiv(legStart.z, gridStep) - minGz).coerceIn(0, gridH - 1)
        val ex = (Math.floorDiv(legEnd.x, gridStep) - minGx).coerceIn(0, gridW - 1)
        val ez = (Math.floorDiv(legEnd.z, gridStep) - minGz).coerceIn(0, gridH - 1)

        val startCell = sz * gridW + sx
        val endCell = ez * gridW + ex

        val dirs = arrayOf(
            intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(-1, 1),
            intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1)
        )
        // Initial approximate heading toward target
        val initDir = closestDir8(dx, dz, dirs)

        val numStates = totalGridCells * 8
        val gScore = DoubleArray(numStates) { Double.POSITIVE_INFINITY }
        val stateY = DoubleArray(numStates)
        val stateSlopeSign = IntArray(numStates)
        val cameFrom = IntArray(numStates) { -1 }
        val closed = BooleanArray(numStates)

        data class Node3D(val state: Int, val f: Double) : Comparable<Node3D> {
            override fun compareTo(other: Node3D): Int = f.compareTo(other.f)
        }

        val startState = startCell * 8 + initDir
        gScore[startState] = 0.0
        stateY[startState] = (legStart.y + 1).toDouble()
        val startH = hypot(((ex - sx) * gridStep).toDouble(), ((ez - sz) * gridStep).toDouble())
        val open = PriorityQueue<Node3D>()
        open.add(Node3D(startState, startH))

        val tunnelH = cfg.effectiveTunnelHeight()
        val maxSlope = 1.0 / cfg.maxSlopeRun.toDouble()
        val neededSlope = abs(legEnd.y - legStart.y) / dist
        val effectiveSlope = max(maxSlope, neededSlope + 0.03)

        var bestEndState = -1
        var bestEndCost = Double.POSITIVE_INFINITY

        while (open.isNotEmpty()) {
            val cur = open.poll()
            val cs = cur.state
            if (closed[cs]) continue
            closed[cs] = true

            val cCell = cs / 8
            val cDir = cs % 8
            if (cCell == endCell) {
                if (gScore[cs] < bestEndCost) {
                    bestEndCost = gScore[cs]
                    bestEndState = cs
                }
                break
            }

            val cx = cCell % gridW
            val cz = cCell / gridW
            val curY = stateY[cs]
            val curSlopeSign = stateSlopeSign[cs]

            for (nd in 0 until 8) {
                // Disallow sharp reversals (>= 135 deg) to guarantee smooth highway/rail curvature!
                val dAng = abs(nd - cDir).let { min(it, 8 - it) }
                if (dAng >= 3) continue

                val nx = cx + dirs[nd][0]
                val nz = cz + dirs[nd][1]
                if (nx !in 0 until gridW || nz !in 0 until gridH) continue

                val nCell = nz * gridW + nx
                val ns = nCell * 8 + nd
                if (closed[ns]) continue

                val stepDist = hypot(dirs[nd][0].toDouble(), dirs[nd][1].toDouble()) * gridStep
                val maxDy = stepDist * effectiveSlope

                val ng = gridGround[nCell].toDouble()
                val isWater = gridWater[nCell]
                val waterTop = gridWaterTop[nCell].toDouble()

                // Target elevation at neighbor: weigh natural terrain vs smooth glide toward destination Y
                val remDist = hypot(((ex - nx) * gridStep).toDouble(), ((ez - nz) * gridStep).toDouble())
                val endY = (legEnd.y + 1).toDouble()
                val idealY = when {
                    isWater && cfg.riverStrategy == RiverStrategy.AUTO_VIADUCT ->
                        max(waterTop + 3.0, ng + 4.0)
                    isWater && cfg.riverStrategy == RiverStrategy.SHIELD_TUNNEL ->
                        ng - tunnelH - 2.0
                    cfg.obstacleMode == ObstacleMode.PREFER_TUNNEL && (ng - curY) > tunnelH + 1.0 ->
                        curY // Hold steady grade straight into mountain tunnel!
                    else -> {
                        val glideT = (stepDist / max(stepDist, remDist + stepDist)).coerceIn(0.0, 0.35)
                        val lineTarget = curY * (1.0 - glideT) + endY * glideT
                        val groundTarget = ng + 1.0
                        // Blend ground-hugging vs smooth direct elevation glide
                        val wGround = if (cfg.obstacleMode == ObstacleMode.SURFACE_ONLY) 0.80 else 0.55
                        groundTarget * wGround + lineTarget * (1.0 - wGround)
                    }
                }

                // Clamp neighbor roadY within physical slope grade AND arrive cleanly near legEnd
                val maxEndReach = remDist * effectiveSlope + 2.0
                val ny = idealY
                    .coerceIn(curY - maxDy, curY + maxDy)
                    .coerceIn(endY - maxEndReach, endY + maxEndReach)

                // Civil Engineering Cost Model
                val earthDiff = (ng + 1.0) - ny // >0 = cut/tunnel, <0 = fill/viaduct
                val earthworkCost = when {
                    isWater -> {
                        if (cfg.riverStrategy == RiverStrategy.SHIELD_TUNNEL) stepDist * 1.15 else stepDist * 0.95
                    }
                    earthDiff > tunnelH + cfg.tunnelCutThreshold -> {
                        // Deep under mountain: tunnel vs disallowed surface
                        when (cfg.obstacleMode) {
                            ObstacleMode.SURFACE_ONLY -> stepDist * (4.0 + earthDiff * 0.45) // Strong incentive to flank/pass!
                            ObstacleMode.PREFER_TUNNEL -> stepDist * 0.42 // Bold mountain tunneling!
                            ObstacleMode.AI_AUTO -> stepDist * 0.85 // Balanced: bores narrow ridges, flanks huge massifs!
                        }
                    }
                    earthDiff > 0.5 -> {
                        // Open cut excavation
                        stepDist * (0.20 * earthDiff)
                    }
                    earthDiff < -2.0 -> {
                        // Elevated viaduct / embankment
                        stepDist * (0.28 * (-earthDiff))
                    }
                    else -> 0.0
                }

                val dy = ny - curY
                val newSlopeSign = when {
                    dy > 0.25 -> 1
                    dy < -0.25 -> -1
                    else -> 0
                }
                val verticalOscCost = if (curSlopeSign != 0 && newSlopeSign != 0 && curSlopeSign != newSlopeSign) {
                    stepDist * 0.85
                } else {
                    abs(dy) * 0.45
                }

                val slopeRoughPenalty = gridSlope[nCell] * stepDist * 0.95
                val ridgePassBonus = if (cfg.obstacleMode != ObstacleMode.PREFER_TUNNEL) {
                    max(0f, gridProminence[nCell]) * stepDist * 0.14
                } else 0.0

                val turnCost = when (dAng) {
                    0 -> 0.0
                    1 -> gridStep * 0.35
                    else -> gridStep * 1.65
                }

                // Keep safe lateral clearance from earlier multi-waypoint legs so roads never self-intersect
                val wx = (minGx + nx) * gridStep + gridStep * 0.5
                val wz = (minGz + nz) * gridStep + gridStep * 0.5
                var prevOverlapPenalty = 0.0
                if (allPathNodes.size > 16) {
                    val minClear = cfg.roadWidth + 10.0
                    val limitIdx = allPathNodes.size - 12
                    var k = 0
                    while (k < limitIdx) {
                        val pn = allPathNodes[k]
                        val pd = hypot(wx - pn.x, wz - pn.z)
                        if (pd < minClear) {
                            prevOverlapPenalty = max(prevOverlapPenalty, (minClear - pd) * stepDist * 3.5)
                        }
                        k += 4
                    }
                }

                val stepCost = stepDist + earthworkCost + verticalOscCost + slopeRoughPenalty + ridgePassBonus + turnCost + prevOverlapPenalty
                val tentativeG = gScore[cs] + stepCost

                if (tentativeG < gScore[ns]) {
                    cameFrom[ns] = cs
                    gScore[ns] = tentativeG
                    stateY[ns] = ny
                    stateSlopeSign[ns] = newSlopeSign
                    val h = remDist + abs(endY - ny) * 0.35
                    open.add(Node3D(ns, tentativeG + h))
                }
            }
        }

        val rawPoints = ArrayList<Point2D>()
        if (bestEndState >= 0) {
            val revCells = ArrayList<Int>()
            var s = bestEndState
            while (s != -1) {
                revCells.add(s / 8)
                if (s == startState) break
                s = cameFrom[s]
            }
            revCells.reverse()
            rawPoints.add(Point2D(legStart.x + 0.5, legStart.z + 0.5))
            for (k in 1 until revCells.size - 1) {
                val cell = revCells[k]
                val ix = cell % gridW
                val iz = cell / gridW
                rawPoints.add(
                    Point2D(
                        (minGx + ix) * gridStep + gridStep * 0.5,
                        (minGz + iz) * gridStep + gridStep * 0.5
                    )
                )
            }
            rawPoints.add(Point2D(legEnd.x + 0.5, legEnd.z + 0.5))
        } else {
            rawPoints.add(Point2D(legStart.x + 0.5, legStart.z + 0.5))
            rawPoints.add(Point2D(legEnd.x + 0.5, legEnd.z + 0.5))
        }

        // Simplify staircase grid nodes -> smooth with true Centripetal Catmull-Rom spline!
        val simplified = douglasPeucker(rawPoints, max(1.8, gridStep * 0.45))
        val smoothSpline = centripetalCatmullRom(simplified, 8)
        appendLegNodes(smoothSpline)

        legIndex++
        stage = Stage.INIT_LEG
    }

    private fun buildGentleSwitchback(
        dx: Double,
        dz: Double,
        dist: Double,
        dyAbs: Double,
        maxSlope: Double,
        highGain: Int
    ): List<Point2D>? {
        val ux = dx / dist
        val uz = dz / dist
        val px = -uz
        val pz = ux

        fun sampleGridHeight(wx: Double, wz: Double): Int {
            val gx = (Math.floorDiv(wx.roundToInt(), gridStep) - minGx).coerceIn(0, gridW - 1)
            val gz = (Math.floorDiv(wz.roundToInt(), gridStep) - minGz).coerceIn(0, gridH - 1)
            return gridGround[gz * gridW + gx]
        }

        val midX = legStart.x + dx * 0.5
        val midZ = legStart.z + dz * 0.5
        val probeOff = min(80.0, max(24.0, dist * 0.35))
        val leftH = sampleGridHeight(midX + px * probeOff, midZ + pz * probeOff)
        val rightH = sampleGridHeight(midX - px * probeOff, midZ - pz * probeOff)
        val centerH = sampleGridHeight(midX, midZ)
        val favoredSide = if (leftH <= rightH) 1.0 else -1.0

        val requiredLen = max(dist * 1.15, dyAbs / maxSlope)
        val minLegGap = max(20.0, cfg.roadWidth * 2.6 + 9.0)

        // Case A: Terrain has a broad flanking valley around a central ridge and grade fits a single wide arc
        if (min(leftH, rightH) + 8 < centerH && requiredLen <= hypot(dist, probeOff * 1.75)) {
            val amp = max(minLegGap, min(96.0, sqrt(max(0.0, requiredLen * requiredLen - dist * dist)) * 0.58))
            val ctrl = listOf(
                Point2D(legStart.x + 0.5, legStart.z + 0.5),
                Point2D(legStart.x + dx * 0.30 + px * amp * favoredSide, legStart.z + dz * 0.30 + pz * amp * favoredSide),
                Point2D(legStart.x + dx * 0.70 + px * amp * favoredSide, legStart.z + dz * 0.70 + pz * amp * favoredSide),
                Point2D(legEnd.x + 0.5, legEnd.z + 0.5)
            )
            return centripetalCatmullRom(ctrl, 14)
        }

        // Case B: Very steep cliff / short horizontal distance -> wide horseshoe / sweeping multi-bend switchback
        if (dist < minLegGap * 1.8) {
            val amp = max(minLegGap * 1.15, min(92.0, requiredLen * 0.42))
            val ctrl = listOf(
                Point2D(legStart.x + 0.5, legStart.z + 0.5),
                Point2D(legStart.x + dx * 0.15 + px * amp * 0.70 * favoredSide, legStart.z + dz * 0.15 + pz * amp * 0.70 * favoredSide),
                Point2D(legStart.x + dx * 0.50 + px * amp * favoredSide, legStart.z + dz * 0.50 + pz * amp * favoredSide),
                Point2D(legStart.x + dx * 0.85 + px * amp * 0.70 * favoredSide, legStart.z + dz * 0.85 + pz * amp * 0.70 * favoredSide),
                Point2D(legEnd.x + 0.5, legEnd.z + 0.5)
            )
            return centripetalCatmullRom(ctrl, 14)
        }

        // Case C: Multi-bend S-curve switchback with guaranteed minLegGap spacing between bends!
        val maxBends = max(1, min(3, (dist / minLegGap).toInt()))
        var chosenBends = 1
        var chosenAmp = minLegGap
        for (bends in 1..maxBends) {
            val segL = dist / (bends + 1).toDouble()
            val segTarget = requiredLen / (bends + 1).toDouble()
            val neededAmp = if (segTarget > segL) sqrt(segTarget * segTarget - segL * segL) * 0.55 else minLegGap * 0.85
            chosenBends = bends
            chosenAmp = neededAmp.coerceIn(minLegGap, 92.0)
            if (neededAmp <= 68.0) break
        }
        if (highGain >= 14 && chosenAmp < minLegGap) {
            chosenAmp = minLegGap
        }

        val ctrl = ArrayList<Point2D>(chosenBends + 2)
        ctrl.add(Point2D(legStart.x + 0.5, legStart.z + 0.5))
        for (b in 1..chosenBends) {
            val t = b.toDouble() / (chosenBends + 1).toDouble()
            val sign = if (b % 2 == 1) favoredSide else -favoredSide
            ctrl.add(Point2D(legStart.x + dx * t + px * chosenAmp * sign, legStart.z + dz * t + pz * chosenAmp * sign))
        }
        ctrl.add(Point2D(legEnd.x + 0.5, legEnd.z + 0.5))
        return centripetalCatmullRom(ctrl, 14)
    }

    private fun appendLegNodes(pts: List<Point2D>) {
        if (pts.isEmpty()) return
        for (p in pts) {
            val prev = allPathNodes.lastOrNull()
            if (prev == null || hypot(p.x - prev.x, p.z - prev.z) > 0.4) {
                allPathNodes.add(p)
            }
        }
    }

    private fun resampleCenterline() {
        val out = ArrayList<Point2D>()
        out.add(allPathNodes.first())
        var carry = 0.0
        val step = 1.0

        for (i in 0 until allPathNodes.size - 1) {
            val a = allPathNodes[i]
            val b = allPathNodes[i + 1]
            val dx = b.x - a.x
            val dz = b.z - a.z
            val segLen = hypot(dx, dz)
            if (segLen < 1e-4) continue

            val ux = dx / segLen
            val uz = dz / segLen
            var d = step - carry
            while (d <= segLen) {
                out.add(Point2D(a.x + ux * d, a.z + uz * d))
                d += step
            }
            carry = segLen - (d - step)
        }
        val last = allPathNodes.last()
        val prev = out.last()
        if (hypot(last.x - prev.x, last.z - prev.z) > 0.25) {
            out.add(last)
        }
        rawStations = out
        samples = ArrayList(out.size)
        surveyCursor = 0
        progress = 0.46f
        stage = Stage.SURVEY_STATIONS
    }

    private fun stepSurveyStations(budgetOps: Int) {
        val ops = max(24, budgetOps)
        var done = 0
        while (surveyCursor < rawStations.size && done < ops) {
            val pt = rawStations[surveyCursor]
            val ix = pt.x.roundToInt()
            val iz = pt.z.roundToInt()
            samples.add(streamer.sampleColumn(ix, iz))
            surveyCursor++
            done++
        }
        progress = 0.46f + 0.44f * (surveyCursor.toFloat() / rawStations.size.coerceAtLeast(1))
        if (surveyCursor >= rawStations.size) {
            stage = Stage.OPTIMIZE_PROFILE
        }
    }

    private fun optimizeProfile() {
        val n = rawStations.size
        if (n == 0) {
            stage = Stage.DONE
            progress = 1f
            return
        }

        val tunnelH = cfg.effectiveTunnelHeight()
        val target = DoubleArray(n)
        val anchorWeight = DoubleArray(n) { 0.14 }

        for (i in 0 until n) {
            val s = samples[i]
            target[i] = when {
                s.hasLiquid && cfg.riverStrategy == RiverStrategy.SHIELD_TUNNEL -> {
                    anchorWeight[i] = 0.28
                    (s.groundY - tunnelH - 2).toDouble()
                }
                s.hasLiquid && cfg.riverStrategy == RiverStrategy.AUTO_VIADUCT -> {
                    anchorWeight[i] = 0.30
                    max(s.waterTopY + 3, s.groundY + 4).toDouble()
                }
                else -> (s.groundY + 1).toDouble()
            }
        }

        // Pin user waypoints firmly
        val pinned = BooleanArray(n)
        target[0] = (waypoints.first().y + 1).toDouble()
        pinned[0] = true
        anchorWeight[0] = 1.0
        target[n - 1] = (waypoints.last().y + 1).toDouble()
        pinned[n - 1] = true
        anchorWeight[n - 1] = 1.0

        for (w in 1 until waypoints.size - 1) {
            val wp = waypoints[w]
            var bestIdx = 0
            var bestDist = Double.POSITIVE_INFINITY
            for (i in 0 until n) {
                val d = hypot(rawStations[i].x - (wp.x + 0.5), rawStations[i].z - (wp.z + 0.5))
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            target[bestIdx] = (wp.y + 1).toDouble()
            pinned[bestIdx] = true
            anchorWeight[bestIdx] = 0.90
        }

        // 40-iteration Global Variational Vertical Curve Optimizer (parabolic vertical curves + grade projection)
        val grade = target.copyOf()
        val maxStep = 1.0 / cfg.maxSlopeRun.toDouble()
        val neededTotalSlope = abs(target[n - 1] - target[0]) / max(1, n - 1).toDouble()
        val strictStep = max(maxStep, neededTotalSlope + 0.02)

        for (iter in 0 until 40) {
            val prevG = grade.copyOf()
            for (i in 1 until n - 1) {
                if (pinned[i]) continue
                val lap = 0.5 * (prevG[i - 1] + prevG[i + 1])
                val biharmonic = if (i >= 2 && i <= n - 3) {
                    (4.0 * (prevG[i - 1] + prevG[i + 1]) - (prevG[i - 2] + prevG[i + 2])) / 6.0
                } else lap
                val smoothPull = 0.65 * biharmonic + 0.35 * lap
                val w = anchorWeight[i]
                grade[i] = prevG[i] + 0.55 * (w * (target[i] - prevG[i]) + (1.0 - w) * (smoothPull - prevG[i]))
            }
            // Enforce bidirectional grade constraint projection
            for (i in 1 until n) {
                grade[i] = grade[i].coerceIn(grade[i - 1] - strictStep, grade[i - 1] + strictStep)
            }
            for (i in n - 2 downTo 0) {
                if (!pinned[i]) {
                    grade[i] = grade[i].coerceIn(grade[i + 1] - strictStep, grade[i + 1] + strictStep)
                }
            }
        }

        // In SURFACE_ONLY mode, lift deep trenches gently while STILL respecting slope limits!
        if (cfg.obstacleMode == ObstacleMode.SURFACE_ONLY) {
            for (i in 1 until n - 1) {
                val s = samples[i]
                if (!s.hasLiquid && s.groundY - grade[i] > 4.0) {
                    grade[i] = grade[i] + min(3.0, (s.groundY - grade[i]) * 0.45)
                }
            }
            for (pass in 0 until 8) {
                val src = grade.copyOf()
                for (i in 1 until n - 1) {
                    if (!pinned[i]) {
                        grade[i] = (src[i - 1] + src[i] * 2.0 + src[i + 1]) * 0.25
                    }
                }
            }
            for (i in 1 until n) {
                grade[i] = grade[i].coerceIn(grade[i - 1] - strictStep, grade[i - 1] + strictStep)
            }
            for (i in n - 2 downTo 0) {
                if (!pinned[i]) {
                    grade[i] = grade[i].coerceIn(grade[i + 1] - strictStep, grade[i + 1] + strictStep)
                }
            }
        }

        // 5-station median ground envelope so tiny dirt bumps / trees never trigger fake tunnels
        val terrainEnv = IntArray(n) { i ->
            val win = IntArray(5)
            for (k in -2..2) {
                win[k + 2] = samples[(i + k).coerceIn(0, n - 1)].topSolidY
            }
            win.sort()
            win[2]
        }

        val types = Array(n) { StationType.SURFACE }
        for (i in 0 until n) {
            val s = samples[i]
            val ry = grade[i].roundToInt()
            val clearance = ry - s.groundY
            val overburden = terrainEnv[i] - (ry + tunnelH)
            val allowMountainTunnel = cfg.obstacleMode != ObstacleMode.SURFACE_ONLY
            types[i] = when {
                s.hasLiquid && cfg.riverStrategy == RiverStrategy.SHIELD_TUNNEL && ry <= s.waterTopY + 1 ->
                    StationType.TUNNEL
                clearance >= 2 ->
                    StationType.VIADUCT
                allowMountainTunnel && overburden >= cfg.tunnelCutThreshold ->
                    StationType.TUNNEL
                s.topSolidY > ry ->
                    StationType.CUT
                else ->
                    StationType.SURFACE
            }
        }

        // Reject short fake tunnels unless underwater shield tunnel
        val minTunnelLen = if (cfg.obstacleMode == ObstacleMode.AI_AUTO) 8 else 4
        var runStart = -1
        for (i in 0..n) {
            val isMountainTunnel = i < n && types[i] == StationType.TUNNEL && !samples[i].hasLiquid
            if (isMountainTunnel) {
                if (runStart < 0) runStart = i
            } else if (runStart >= 0) {
                if (i - runStart < minTunnelLen) {
                    for (j in runStart until i) {
                        val ry = grade[j].roundToInt()
                        types[j] = if (samples[j].topSolidY > ry) StationType.CUT else StationType.SURFACE
                    }
                }
                runStart = -1
            }
        }

        // Smooth out 1-2 block station type flickers
        for (i in 1 until n - 1) {
            if (types[i - 1] == types[i + 1] && types[i] != types[i - 1]) {
                types[i] = types[i - 1]
            }
        }
        for (i in 1 until n - 2) {
            if (types[i - 1] == types[i + 2] && types[i] == types[i + 1] && types[i] != types[i - 1]) {
                types[i] = types[i - 1]
                types[i + 1] = types[i - 1]
            }
        }

        finalStations = ArrayList(n)
        var accArc = 0.0
        for (i in 0 until n) {
            val cur = rawStations[i]
            if (i > 0) {
                accArc += hypot(cur.x - rawStations[i - 1].x, cur.z - rawStations[i - 1].z)
            }
            val prev = rawStations[(i - 1).coerceAtLeast(0)]
            val next = rawStations[(i + 1).coerceAtMost(n - 1)]
            var dx = next.x - prev.x
            var dz = next.z - prev.z
            val len = hypot(dx, dz)
            if (len < 1e-4) {
                dx = 1.0
                dz = 0.0
            } else {
                dx /= len
                dz /= len
            }
            val px = -dz
            val pz = dx
            val s = samples[i]

            var roadY = grade[i].roundToInt()
            var isHalfSlab = false
            if (cfg.smoothSlabs && cfg.pavingMode == PavingMode.ROAD &&
                (types[i] == StationType.SURFACE || types[i] == StationType.CUT)
            ) {
                val halfStep = (grade[i] * 2.0).roundToInt()
                if (halfStep % 2 != 0) {
                    roadY = (halfStep + 1) / 2
                    isHalfSlab = true
                } else {
                    roadY = halfStep / 2
                    isHalfSlab = false
                }
            }

            finalStations.add(
                RouteStation(
                    x = cur.x,
                    z = cur.z,
                    tx = dx,
                    tz = dz,
                    px = px,
                    pz = pz,
                    roadY = roadY,
                    groundY = s.groundY,
                    waterTopY = s.waterTopY,
                    hasLiquid = s.hasLiquid,
                    type = types[i],
                    isHalfSlab = isHalfSlab,
                    arcLength = accArc
                )
            )
        }

        progress = 1f
        stage = Stage.DONE
    }

    companion object {
        private fun closestDir8(dx: Double, dz: Double, dirs: Array<IntArray>): Int {
            var best = 0
            var bestDot = Double.NEGATIVE_INFINITY
            val len = hypot(dx, dz).coerceAtLeast(1e-6)
            val ux = dx / len
            val uz = dz / len
            for (i in dirs.indices) {
                val dl = hypot(dirs[i][0].toDouble(), dirs[i][1].toDouble())
                val dot = (dirs[i][0] / dl) * ux + (dirs[i][1] / dl) * uz
                if (dot > bestDot) {
                    bestDot = dot
                    best = i
                }
            }
            return best
        }

        private fun douglasPeucker(pts: List<Point2D>, epsilon: Double): List<Point2D> {
            if (pts.size <= 2) return pts
            var maxD = 0.0
            var idx = 0
            val a = pts.first()
            val b = pts.last()
            val abx = b.x - a.x
            val abz = b.z - a.z
            val len = hypot(abx, abz).coerceAtLeast(1e-6)

            for (i in 1 until pts.size - 1) {
                val p = pts[i]
                val d = abs((p.x - a.x) * (-abz) + (p.z - a.z) * abx) / len
                if (d > maxD) {
                    maxD = d
                    idx = i
                }
            }
            return if (maxD > epsilon) {
                val left = douglasPeucker(pts.subList(0, idx + 1), epsilon)
                val right = douglasPeucker(pts.subList(idx, pts.size), epsilon)
                val merged = ArrayList<Point2D>(left.size + right.size - 1)
                merged.addAll(left)
                for (k in 1 until right.size) merged.add(right[k])
                merged
            } else {
                listOf(a, b)
            }
        }

        /**
         * Centripetal Catmull-Rom Spline (alpha = 0.5) — guarantees zero cusps/loops and smooth G2 curvature.
         */
        private fun centripetalCatmullRom(pts: List<Point2D>, subdivisionsPerSeg: Int): List<Point2D> {
            if (pts.size <= 2) return pts
            val padded = ArrayList<Point2D>(pts.size + 2)
            padded.add(Point2D(2 * pts[0].x - pts[1].x, 2 * pts[0].z - pts[1].z))
            padded.addAll(pts)
            val n = pts.size
            padded.add(Point2D(2 * pts[n - 1].x - pts[n - 2].x, 2 * pts[n - 1].z - pts[n - 2].z))

            val out = ArrayList<Point2D>(pts.size * subdivisionsPerSeg)
            out.add(pts.first())

            fun tj(ti: Double, p0: Point2D, p1: Point2D): Double {
                val d = hypot(p1.x - p0.x, p1.z - p0.z).coerceAtLeast(1e-4)
                return ti + sqrt(d)
            }

            for (i in 1 until padded.size - 2) {
                val p0 = padded[i - 1]
                val p1 = padded[i]
                val p2 = padded[i + 1]
                val p3 = padded[i + 2]

                val t0 = 0.0
                val t1 = tj(t0, p0, p1)
                val t2 = tj(t1, p1, p2)
                val t3 = tj(t2, p2, p3)

                for (s in 1..subdivisionsPerSeg) {
                    val t = t1 + (t2 - t1) * (s.toDouble() / subdivisionsPerSeg.toDouble())
                    val a1x = ((t1 - t) / (t1 - t0)) * p0.x + ((t - t0) / (t1 - t0)) * p1.x
                    val a1z = ((t1 - t) / (t1 - t0)) * p0.z + ((t - t0) / (t1 - t0)) * p1.z
                    val a2x = ((t2 - t) / (t2 - t1)) * p1.x + ((t - t1) / (t2 - t1)) * p2.x
                    val a2z = ((t2 - t) / (t2 - t1)) * p1.z + ((t - t1) / (t2 - t1)) * p2.z
                    val a3x = ((t3 - t) / (t3 - t2)) * p2.x + ((t - t2) / (t3 - t2)) * p3.x
                    val a3z = ((t3 - t) / (t3 - t2)) * p2.z + ((t - t2) / (t3 - t2)) * p3.z

                    val b1x = ((t2 - t) / (t2 - t0)) * a1x + ((t - t0) / (t2 - t0)) * a2x
                    val b1z = ((t2 - t) / (t2 - t0)) * a1z + ((t - t0) / (t2 - t0)) * a2z
                    val b2x = ((t3 - t) / (t3 - t1)) * a2x + ((t - t1) / (t3 - t1)) * a3x
                    val b2z = ((t3 - t) / (t3 - t1)) * a2z + ((t - t1) / (t3 - t1)) * a3z

                    val cx = ((t2 - t) / (t2 - t1)) * b1x + ((t - t1) / (t2 - t1)) * b2x
                    val cz = ((t2 - t) / (t2 - t1)) * b1z + ((t - t1) / (t2 - t1)) * b2z
                    out.add(Point2D(cx, cz))
                }
            }
            return out
        }
    }
}
