package net.montoyo.wd.core

import net.montoyo.wd.utilities.Vector2i
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Adaptive resolution & distance-based LOD calculations for in-world WebDisplays screens.
 */
object ScreenResolution {
    /**
     * Computes a crisp, aspect-ratio-preserving default resolution for a screen multiblock.
     */
    @JvmStatic
    fun computeAdaptiveResolution(
        size: Vector2i,
        pixelsPerBlock: Int,
        minTargetEdge: Int,
        maxW: Int,
        maxH: Int
    ): Vector2i {
        val physW = max(0.25, size.x - 4.0 / 16.0)
        val physH = max(0.25, size.y - 4.0 / 16.0)
        val ppb = max(64, pixelsPerBlock).toDouble()

        var rawW = physW * ppb
        var rawH = physH * ppb

        val longest = max(rawW, rawH)
        if (minTargetEdge > 0 && longest < minTargetEdge) {
            val scaleUp = minTargetEdge / longest
            rawW *= scaleUp
            rawH *= scaleUp
        }

        return clampKeepingAspect(rawW, rawH, maxW, maxH)
    }

    /**
     * Computes a quality-tier preset resolution (e.g. 720 for HD, 1080 for FHD)
     * preserving the screen's physical aspect ratio.
     */
    @JvmStatic
    fun computePresetResolution(size: Vector2i, targetShortEdge: Int, maxW: Int, maxH: Int): Vector2i {
        val physW = max(0.25, size.x - 4.0 / 16.0)
        val physH = max(0.25, size.y - 4.0 / 16.0)
        val shortPhys = min(physW, physH)
        val scale = max(64, targetShortEdge) / shortPhys

        return clampKeepingAspect(physW * scale, physH * scale, maxW, maxH)
    }

    /**
     * Clamps width & height proportionally to [maxW, maxH] and rounds to even pixel dimensions.
     */
    @JvmStatic
    fun clampKeepingAspect(rawW: Double, rawH: Double, maxW: Int, maxH: Int): Vector2i {
        var w = max(64.0, rawW)
        var h = max(64.0, rawH)

        if (maxW > 0 && w > maxW) {
            val scale = maxW / w
            w = maxW.toDouble()
            h *= scale
        }
        if (maxH > 0 && h > maxH) {
            val scale = maxH / h
            h = maxH.toDouble()
            w *= scale
        }

        val finalW = alignEven(w.roundToInt(), 64, if (maxW > 0) maxW else Int.MAX_VALUE)
        val finalH = alignEven(h.roundToInt(), 64, if (maxH > 0) maxH else Int.MAX_VALUE)
        return Vector2i(finalW, finalH)
    }

    /**
     * Computes a client-side LOD render scale based on camera distance and physical screen span.
     */
    @JvmStatic
    fun computeLodScale(distSqr: Double, size: Vector2i, logicalW: Int, logicalH: Int): Float {
        val maxLogical = max(logicalW, logicalH)
        if (maxLogical <= 360) return 1.0f

        val dist = sqrt(max(1.0, distSqr))
        val physSpan = max(1.0, max(size.x, size.y) - 0.25)
        val angularSpan = physSpan / dist

        val target = when {
            dist <= 9.0 || angularSpan >= 0.34 -> 1.0f
            dist <= 18.0 || angularSpan >= 0.19 -> 0.75f
            dist <= 26.0 || angularSpan >= 0.11 -> 0.5f
            else -> 0.35f
        }

        val minFloor = min(1.0f, 320.0f / maxLogical.toFloat())
        return max(target, minFloor)
    }

    @JvmStatic
    fun alignEven(value: Int, minVal: Int, maxVal: Int): Int {
        val clamped = max(minVal, min(maxVal, value))
        val even = clamped and 1.inv()
        return max(minVal, even)
    }
}
