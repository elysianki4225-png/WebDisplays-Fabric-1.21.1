package net.montoyo.wd.core;

import net.montoyo.wd.utilities.Vector2i;

/**
 * Adaptive resolution & distance-based LOD calculations for in-world WebDisplays screens.
 */
public final class ScreenResolution {

    private ScreenResolution() {
    }

    /**
     * Computes a crisp, aspect-ratio-preserving default resolution for a screen multiblock.
     *
     * @param size           Multiblock size in blocks (width x height)
     * @param pixelsPerBlock Target pixel density per block
     * @param minTargetEdge  Minimum target pixel count along the longest edge (for small screens like 2x2)
     * @param maxW           Server-configured maximum horizontal resolution
     * @param maxH           Server-configured maximum vertical resolution
     */
    public static Vector2i computeAdaptiveResolution(Vector2i size, int pixelsPerBlock, int minTargetEdge, int maxW, int maxH) {
        double physW = Math.max(0.25, size.x - 4.0 / 16.0);
        double physH = Math.max(0.25, size.y - 4.0 / 16.0);
        double ppb = Math.max(64, pixelsPerBlock);

        double rawW = physW * ppb;
        double rawH = physH * ppb;

        double longest = Math.max(rawW, rawH);
        if (minTargetEdge > 0 && longest < minTargetEdge) {
            double scaleUp = minTargetEdge / longest;
            rawW *= scaleUp;
            rawH *= scaleUp;
        }

        return clampKeepingAspect(rawW, rawH, maxW, maxH);
    }

    /**
     * Computes a quality-tier preset resolution (e.g. 720 for HD, 1080 for FHD)
     * preserving the screen's physical aspect ratio.
     */
    public static Vector2i computePresetResolution(Vector2i size, int targetShortEdge, int maxW, int maxH) {
        double physW = Math.max(0.25, size.x - 4.0 / 16.0);
        double physH = Math.max(0.25, size.y - 4.0 / 16.0);
        double shortPhys = Math.min(physW, physH);
        double scale = Math.max(64, targetShortEdge) / shortPhys;

        return clampKeepingAspect(physW * scale, physH * scale, maxW, maxH);
    }

    /**
     * Clamps width & height proportionally to [maxW, maxH] and rounds to even pixel dimensions.
     */
    public static Vector2i clampKeepingAspect(double rawW, double rawH, int maxW, int maxH) {
        double w = Math.max(64.0, rawW);
        double h = Math.max(64.0, rawH);

        if (maxW > 0 && w > maxW) {
            double scale = maxW / w;
            w = maxW;
            h *= scale;
        }
        if (maxH > 0 && h > maxH) {
            double scale = maxH / h;
            h = maxH;
            w *= scale;
        }

        int finalW = alignEven((int) Math.round(w), 64, maxW > 0 ? maxW : Integer.MAX_VALUE);
        int finalH = alignEven((int) Math.round(h), 64, maxH > 0 ? maxH : Integer.MAX_VALUE);
        return new Vector2i(finalW, finalH);
    }

    /**
     * Computes a client-side LOD render scale based on camera distance and physical screen span.
     */
    public static float computeLodScale(double distSqr, Vector2i size, int logicalW, int logicalH) {
        int maxLogical = Math.max(logicalW, logicalH);
        if (maxLogical <= 360) {
            return 1.0f;
        }

        double dist = Math.sqrt(Math.max(1.0, distSqr));
        double physSpan = Math.max(1.0, Math.max(size.x, size.y) - 0.25);
        double angularSpan = physSpan / dist;

        float target;
        if (dist <= 9.0 || angularSpan >= 0.34) {
            target = 1.0f;
        } else if (dist <= 18.0 || angularSpan >= 0.19) {
            target = 0.75f;
        } else if (dist <= 26.0 || angularSpan >= 0.11) {
            target = 0.5f;
        } else {
            target = 0.35f;
        }

        float minFloor = Math.min(1.0f, 320.0f / (float) maxLogical);
        return Math.max(target, minFloor);
    }

    public static int alignEven(int value, int min, int max) {
        int clamped = Math.max(min, Math.min(max, value));
        int even = clamped & ~1;
        return Math.max(min, even);
    }
}
