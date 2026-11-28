package com.terrapaver.engine

import com.terrapaver.config.AlignmentMode
import com.terrapaver.config.PavingConfig
import com.terrapaver.config.RiverStrategy
import net.minecraft.core.BlockPos
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    val type: StationType
)

class IncrementalRoutePlanner(
    private val streamer: ChunkStreamer,
    private val waypoints: List<BlockPos>,
    private val cfg: PavingConfig
) {
    private data class Point2D(val x: Double, val z: Double)

    private enum class Stage {
        INIT_LEG,
        LEG_ASTAR_SAMPLE,
        LEG_ASTAR_SOLVE,
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

    // Per-leg coarse grid for A* / AI terrain evaluation
    private var legStart = BlockPos.ZERO
    private var legEnd = BlockPos.ZERO
    private var gridStep = 12
    private var minGx = 0
    private var maxGx = 0
    private var minGz = 0
    private var maxGz = 0
    private var gridW = 0
    private var gridH = 0
    private var gridGround = IntArray(0)
    private var gridWater = BooleanArray(0)
    private var gridSampleCursor = 0
    private var totalGridCells = 0

    var progress: Float = 0f
        private set

    fun isDone(): Boolean = stage == Stage.DONE

    fun getStations(): List<RouteStation> = finalStations

    fun stepBudget(budgetOps: Int) {
        when (stage) {
            Stage.INIT_LEG -> initLeg()
            Stage.LEG_ASTAR_SAMPLE -> stepGridSampling(budgetOps)
            Stage.LEG_ASTAR_SOLVE -> solveLegPath()
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
            progress = 0.35f
            return
        }

        legStart = waypoints[legIndex]
        legEnd = waypoints[legIndex + 1]
        val dx = (legEnd.x - legStart.x).toDouble()
        val dz = (legEnd.z - legStart.z).toDouble()
        val dist = hypot(dx, dz)

        if (cfg.alignmentMode == AlignmentMode.DIRECT || dist < 24.0) {
            appendLegNodes(listOf(Point2D(legStart.x + 0.5, legStart.z + 0.5), Point2D(legEnd.x + 0.5, legEnd.z + 0.5)))
            legIndex++
            progress = 0.35f * (legIndex.toFloat() / (waypoints.size - 1).toFloat())
            return
        }

        gridStep = when {
            dist < 220.0 -> 8
            dist < 700.0 -> 12
            else -> 16
        }
        val pad = min(144, max(48, (dist * 0.25).toInt()))
        val minX = min(legStart.x, legEnd.x) - pad
        val maxX = max(legStart.x, legEnd.x) + pad
        val minZ = min(legStart.z, legEnd.z) - pad
        val maxZ = max(legStart.z, legEnd.z) + pad

        minGx = Math.floorDiv(minX, gridStep)
        maxGx = Math.floorDiv(maxX, gridStep)
        minGz = Math.floorDiv(minZ, gridStep)
        maxGz = Math.floorDiv(maxZ, gridStep)
        gridW = (maxGx - minGx + 1).coerceAtLeast(3)
        gridH = (maxGz - minGz + 1).coerceAtLeast(3)
        totalGridCells = gridW * gridH
        gridGround = IntArray(totalGridCells)
        gridWater = BooleanArray(totalGridCells)
        gridSampleCursor = 0
        stage = Stage.LEG_ASTAR_SAMPLE
    }

    private fun stepGridSampling(budgetOps: Int) {
        val ops = max(16, budgetOps)
        var done = 0
        while (gridSampleCursor < totalGridCells && done < ops) {
            val iz = gridSampleCursor / gridW
            val ix = gridSampleCursor % gridW
            val wx = (minGx + ix) * gridStep + gridStep / 2
            val wz = (minGz + iz) * gridStep + gridStep / 2
            val sample = streamer.sampleColumn(wx, wz)
            gridGround[gridSampleCursor] = sample.groundY
            gridWater[gridSampleCursor] = sample.hasLiquid
            gridSampleCursor++
            done++
        }
        val legBase = legIndex.toFloat() / max(1, waypoints.size - 1).toFloat()
        val legSpan = 1f / max(1, waypoints.size - 1).toFloat()
        progress = 0.30f * (legBase + legSpan * (gridSampleCursor.toFloat() / totalGridCells.coerceAtLeast(1)))
        if (gridSampleCursor >= totalGridCells) {
            stage = Stage.LEG_ASTAR_SOLVE
        }
    }

    private fun solveLegPath() {
        // AI_AUTO inspects straight-line terrain relief to decide:
        // 1. Flat / gentle river / narrow ridge -> Straight engineering / tunnel / viaduct (no awkward squiggly snakes!)
        // 2. Massive mountain / steep plateau -> Contour / Switchback mountain road or smart A* detour!
        val dx = (legEnd.x - legStart.x).toDouble()
        val dz = (legEnd.z - legStart.z).toDouble()
        val dist = hypot(dx, dz).coerceAtLeast(1.0)

        var maxRelief = 0
        var highGain = 0
        var waterCells = 0
        val lineSamples = max(6, (dist / gridStep).toInt())
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
            if (gridWater[idx]) waterCells++
        }

        // If terrain is mostly flat/gentle or mostly river crossing, keep engineering alignment clean & straight!
        if (cfg.alignmentMode == AlignmentMode.AI_AUTO && highGain <= 6 && maxRelief <= 8) {
            appendLegNodes(listOf(Point2D(legStart.x + 0.5, legStart.z + 0.5), Point2D(legEnd.x + 0.5, legEnd.z + 0.5)))
            legIndex++
            stage = Stage.INIT_LEG
            return
        }

        // If CONTOUR_SWITCHBACK or AI_AUTO encounters a towering wide mountain (>16 blocks high and long)
        if ((cfg.alignmentMode == AlignmentMode.CONTOUR_SWITCHBACK || cfg.alignmentMode == AlignmentMode.AI_AUTO) &&
            highGain >= 16 && dist >= 70.0 && waterCells < lineSamples / 3
        ) {
            val switchback = buildContourOrSwitchback(dx, dz, dist, highGain)
            if (switchback != null) {
                appendLegNodes(switchback)
                legIndex++
                stage = Stage.INIT_LEG
                return
            }
        }

        // Smart A* with direction momentum (prevents squiggly zig-zags on uniform flat/water terrain!)
        val sx = (Math.floorDiv(legStart.x, gridStep) - minGx).coerceIn(0, gridW - 1)
        val sz = (Math.floorDiv(legStart.z, gridStep) - minGz).coerceIn(0, gridH - 1)
        val ex = (Math.floorDiv(legEnd.x, gridStep) - minGx).coerceIn(0, gridW - 1)
        val ez = (Math.floorDiv(legEnd.z, gridStep) - minGz).coerceIn(0, gridH - 1)

        val startIdx = sz * gridW + sx
        val endIdx = ez * gridW + ex

        val gScore = DoubleArray(totalGridCells) { Double.POSITIVE_INFINITY }
        val cameFrom = IntArray(totalGridCells) { -1 }
        val closed = BooleanArray(totalGridCells)

        data class Node(val idx: Int, val f: Double) : Comparable<Node> {
            override fun compareTo(other: Node): Int = f.compareTo(other.f)
        }

        val open = PriorityQueue<Node>()
        gScore[startIdx] = 0.0
        open.add(Node(startIdx, hypot((ex - sx).toDouble(), (ez - sz).toDouble()) * gridStep))

        val dirs = arrayOf(
            intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1),
            intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1)
        )

        var found = false
        while (open.isNotEmpty()) {
            val cur = open.poll()
            val ci = cur.idx
            if (ci == endIdx) {
                found = true
                break
            }
            if (closed[ci]) continue
            closed[ci] = true

            val cx = ci % gridW
            val cz = ci / gridW
            val cg = gridGround[ci]
            val prevIdx = cameFrom[ci]
            val prevDx = if (prevIdx >= 0) cx - (prevIdx % gridW) else 0
            val prevDz = if (prevIdx >= 0) cz - (prevIdx / gridW) else 0

            for (d in dirs) {
                val nx = cx + d[0]
                val nz = cz + d[1]
                if (nx !in 0 until gridW || nz !in 0 until gridH) continue
                val ni = nz * gridW + nx
                if (closed[ni]) continue

                val stepDist = hypot(d[0].toDouble(), d[1].toDouble()) * gridStep
                val ng = gridGround[ni]
                val dh = abs(ng - cg).toDouble()
                val waterPenalty = if (gridWater[ni]) {
                    if (cfg.riverStrategy == RiverStrategy.SHIELD_TUNNEL) stepDist * 0.4 else stepDist * 0.25
                } else 0.0
                val slopePenalty = (dh * dh) * 0.14
                val highMountainPenalty = if (cfg.alignmentMode == AlignmentMode.CONTOUR_SWITCHBACK) {
                    max(0, ng - legStart.y - 10) * 1.1
                } else {
                    max(0, ng - legStart.y - 18) * 0.45
                }
                // Momentum penalty keeps flat segments arrow-straight!
                val turnPenalty = if (prevIdx >= 0 && (prevDx != d[0] || prevDz != d[1])) gridStep * 0.35 else 0.0

                val tentativeG = gScore[ci] + stepDist + waterPenalty + slopePenalty + highMountainPenalty + turnPenalty
                if (tentativeG < gScore[ni]) {
                    cameFrom[ni] = ci
                    gScore[ni] = tentativeG
                    val h = hypot((ex - nx).toDouble(), (ez - nz).toDouble()) * gridStep
                    open.add(Node(ni, tentativeG + h))
                }
            }
        }

        val rawPoints = ArrayList<Point2D>()
        if (found) {
            val rev = ArrayList<Int>()
            var c = endIdx
            while (c != -1) {
                rev.add(c)
                if (c == startIdx) break
                c = cameFrom[c]
            }
            rev.reverse()
            rawPoints.add(Point2D(legStart.x + 0.5, legStart.z + 0.5))
            for (k in 1 until rev.size - 1) {
                val idx = rev[k]
                val ix = idx % gridW
                val iz = idx / gridW
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

        var pts: List<Point2D> = simplifyCollinear(rawPoints)
        repeat(2) { pts = chaikinSmooth(pts) }
        appendLegNodes(pts)

        legIndex++
        stage = Stage.INIT_LEG
    }

    private fun buildContourOrSwitchback(dx: Double, dz: Double, dist: Double, highGain: Int): List<Point2D>? {
        // Test flanking left vs right contour arc; if side elevation is lower, route gently around the flank,
        // otherwise weave graceful switchbacks up the mountainside!
        val ux = dx / dist
        val uz = dz / dist
        val px = -uz
        val pz = ux

        val midX = legStart.x + dx * 0.5
        val midZ = legStart.z + dz * 0.5
        val offset = min(96.0, max(28.0, dist * 0.35))

        fun sampleGridHeight(wx: Double, wz: Double): Int {
            val gx = (Math.floorDiv(wx.roundToInt(), gridStep) - minGx).coerceIn(0, gridW - 1)
            val gz = (Math.floorDiv(wz.roundToInt(), gridStep) - minGz).coerceIn(0, gridH - 1)
            return gridGround[gz * gridW + gx]
        }

        val leftH = sampleGridHeight(midX + px * offset, midZ + pz * offset)
        val rightH = sampleGridHeight(midX - px * offset, midZ - pz * offset)
        val centerH = sampleGridHeight(midX, midZ)

        // If flanking valley avoids the mountain peak cleanly, take a sweeping mountain contour curve
        if (min(leftH, rightH) + 8 < centerH) {
            val side = if (leftH <= rightH) 1.0 else -1.0
            val ctrl = listOf(
                Point2D(legStart.x + 0.5, legStart.z + 0.5),
                Point2D(legStart.x + dx * 0.33 + px * offset * 0.85 * side, legStart.z + dz * 0.33 + pz * offset * 0.85 * side),
                Point2D(legStart.x + dx * 0.67 + px * offset * 0.85 * side, legStart.z + dz * 0.67 + pz * offset * 0.85 * side),
                Point2D(legEnd.x + 0.5, legEnd.z + 0.5)
            )
            var pts = ctrl
            repeat(3) { pts = chaikinSmooth(pts) }
            return pts
        }

        // If user explicitly requested switchbacks (or slope requires extra length for gentle climbing)
        if (cfg.alignmentMode == AlignmentMode.CONTOUR_SWITCHBACK && highGain >= 14) {
            val amp = min(48.0, max(22.0, highGain * 1.4))
            val ctrl = listOf(
                Point2D(legStart.x + 0.5, legStart.z + 0.5),
                Point2D(legStart.x + dx * 0.25 + px * amp, legStart.z + dz * 0.25 + pz * amp),
                Point2D(legStart.x + dx * 0.50 - px * amp, legStart.z + dz * 0.50 - pz * amp),
                Point2D(legStart.x + dx * 0.75 + px * amp, legStart.z + dz * 0.75 + pz * amp),
                Point2D(legEnd.x + 0.5, legEnd.z + 0.5)
            )
            var pts = ctrl
            repeat(3) { pts = chaikinSmooth(pts) }
            return pts
        }

        return null
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
        progress = 0.40f
        stage = Stage.SURVEY_STATIONS
    }

    private fun stepSurveyStations(budgetOps: Int) {
        val ops = max(12, budgetOps)
        var done = 0
        while (surveyCursor < rawStations.size && done < ops) {
            val pt = rawStations[surveyCursor]
            val ix = pt.x.roundToInt()
            val iz = pt.z.roundToInt()
            samples.add(streamer.sampleColumn(ix, iz))
            surveyCursor++
            done++
        }
        progress = 0.40f + 0.50f * (surveyCursor.toFloat() / rawStations.size.coerceAtLeast(1))
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

        for (i in 0 until n) {
            val s = samples[i]
            target[i] = when {
                s.hasLiquid && cfg.riverStrategy == RiverStrategy.SHIELD_TUNNEL ->
                    (s.groundY - tunnelH - 2).toDouble()
                s.hasLiquid && cfg.riverStrategy == RiverStrategy.AUTO_VIADUCT ->
                    max(s.waterTopY + 3, s.groundY + 4).toDouble()
                else -> (s.groundY + 1).toDouble()
            }
        }
        target[0] = (waypoints.first().y + 1).toDouble()
        target[n - 1] = (waypoints.last().y + 1).toDouble()

        // Pin intermediate user waypoints gently near their chosen Y
        val wpIndices = ArrayList<Int>()
        wpIndices.add(0)
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
            wpIndices.add(bestIdx)
        }
        wpIndices.add(n - 1)

        val smoothed = target.copyOf()
        for (pass in 0 until 2) {
            val src = smoothed.copyOf()
            for (i in 1 until n - 1) {
                var sum = 0.0
                var weight = 0.0
                for (k in -3..3) {
                    val j = (i + k).coerceIn(0, n - 1)
                    val w = 4.0 - abs(k)
                    sum += src[j] * w
                    weight += w
                }
                smoothed[i] = sum / weight
            }
            smoothed[0] = target[0]
            smoothed[n - 1] = target[n - 1]
        }

        val maxStep = 1.0 / cfg.maxSlopeRun.toDouble()
        val fwd = smoothed.copyOf()
        for (i in 1 until n) {
            fwd[i] = fwd[i].coerceIn(fwd[i - 1] - maxStep, fwd[i - 1] + maxStep)
        }
        val bwd = smoothed.copyOf()
        for (i in n - 2 downTo 0) {
            bwd[i] = bwd[i].coerceIn(bwd[i + 1] - maxStep, bwd[i + 1] + maxStep)
        }
        val grade = DoubleArray(n) { i ->
            when (i) {
                0 -> target[0]
                n - 1 -> target[n - 1]
                else -> (fwd[i] + bwd[i]) * 0.5
            }
        }
        val neededTotalSlope = abs(target[n - 1] - target[0]) / max(1, n - 1).toDouble()
        val strictStep = max(maxStep, neededTotalSlope + 0.02)
        for (i in 1 until n) {
            grade[i] = grade[i].coerceIn(grade[i - 1] - strictStep, grade[i - 1] + strictStep)
        }

        val types = Array(n) { StationType.SURFACE }
        for (i in 0 until n) {
            val s = samples[i]
            val ry = grade[i].roundToInt()
            val clearance = ry - s.groundY
            val overburden = s.topSolidY - (ry + tunnelH)
            types[i] = when {
                s.hasLiquid && cfg.riverStrategy == RiverStrategy.SHIELD_TUNNEL && ry <= s.waterTopY + 1 ->
                    StationType.TUNNEL
                clearance >= 2 ->
                    StationType.VIADUCT
                overburden >= cfg.tunnelCutThreshold ->
                    StationType.TUNNEL
                s.topSolidY > ry ->
                    StationType.CUT
                else ->
                    StationType.SURFACE
            }
        }

        // Merge tiny 1-2 block flickers
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
        for (i in 0 until n) {
            val cur = rawStations[i]
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
            finalStations.add(
                RouteStation(
                    x = cur.x,
                    z = cur.z,
                    tx = dx,
                    tz = dz,
                    px = px,
                    pz = pz,
                    roadY = grade[i].roundToInt(),
                    groundY = s.groundY,
                    waterTopY = s.waterTopY,
                    hasLiquid = s.hasLiquid,
                    type = types[i]
                )
            )
        }

        progress = 1f
        stage = Stage.DONE
    }

    companion object {
        private fun simplifyCollinear(pts: List<Point2D>): List<Point2D> {
            if (pts.size <= 2) return pts
            val out = ArrayList<Point2D>()
            out.add(pts.first())
            var prevAngle = atan2(pts[1].z - pts[0].z, pts[1].x - pts[0].x)
            for (i in 1 until pts.size - 1) {
                val nextAngle = atan2(pts[i + 1].z - pts[i].z, pts[i + 1].x - pts[i].x)
                val diff = abs(nextAngle - prevAngle)
                if (diff > 0.08) {
                    out.add(pts[i])
                    prevAngle = nextAngle
                }
            }
            out.add(pts.last())
            return out
        }

        private fun chaikinSmooth(pts: List<Point2D>): List<Point2D> {
            if (pts.size <= 2) return pts
            val out = ArrayList<Point2D>(pts.size * 2)
            out.add(pts.first())
            for (i in 0 until pts.size - 1) {
                val p0 = pts[i]
                val p1 = pts[i + 1]
                out.add(Point2D(p0.x * 0.75 + p1.x * 0.25, p0.z * 0.75 + p1.z * 0.25))
                out.add(Point2D(p0.x * 0.25 + p1.x * 0.75, p0.z * 0.25 + p1.z * 0.75))
            }
            out.add(pts.last())
            return out
        }
    }
}
