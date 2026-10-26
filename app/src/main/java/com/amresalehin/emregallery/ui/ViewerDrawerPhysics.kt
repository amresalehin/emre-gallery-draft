package com.amresalehin.emregallery.ui

import kotlin.math.abs

/**
 * 3-Anchor Resting Model for Google Photos-Style Overlay Detail Sheet:
 * - Anchor 0 (Closed): Y_closed = Screen Height (completely off-screen, visible height = 0)
 * - Anchor 1 (Half / Peek): Y_half ≈ 45% of screen height (visible height ≈ 55%, primary summary details visible)
 * - Anchor 2 (Fully Expanded): Y_full ≈ 15% from screen top (visible height ≈ 85%, top margin reveals dimmed media)
 */
enum class SheetAnchor {
    CLOSED,
    HALF,
    FULLY_EXPANDED
}

object ViewerDrawerPhysics {
    // Continuous vertical top offset (Y) fractions of screen height:
    const val Y_CLOSED_FRACTION = 1.0f
    const val Y_HALF_FRACTION = 0.45f
    const val Y_FULL_FRACTION = 0.15f

    // Visible height fractions (1f - Y_FRACTION):
    const val DETENT_CLOSED_FRACTION = 0f
    const val DETENT_HALF_FRACTION = 0.55f
    const val DETENT_EXPANDED_FRACTION = 0.85f

    // Velocity thresholds in dp/s
    const val FLING_THRESHOLD_DP = 1000f
    const val EXTREME_FLING_THRESHOLD_DP = 2500f

    fun getYClosed(screenHeightPx: Float): Float = screenHeightPx * Y_CLOSED_FRACTION
    fun getYHalf(screenHeightPx: Float): Float = screenHeightPx * Y_HALF_FRACTION
    fun getYFull(screenHeightPx: Float): Float = screenHeightPx * Y_FULL_FRACTION

    // Settling Midpoints for Low-Velocity Release:
    // Threshold_0_to_1 = (Y_closed + Y_half) / 2
    // Threshold_1_to_2 = (Y_half + Y_full) / 2
    fun getThreshold0To1(screenHeightPx: Float): Float {
        return (getYClosed(screenHeightPx) + getYHalf(screenHeightPx)) / 2f
    }

    fun getThreshold1To2(screenHeightPx: Float): Float {
        return (getYHalf(screenHeightPx) + getYFull(screenHeightPx)) / 2f
    }

    /**
     * Determines the authoritative target top offset Y for the sheet at gesture release.
     *
     * @param startY Top offset Y when the current gesture started.
     * @param currentY Current top offset Y of the sheet at touch release.
     * @param velocityY Vertical velocity in px/s (positive = moving down, negative = moving up).
     * @param screenHeightPx Available screen height in pixels.
     * @param density Display density (to convert dp/s to px/s).
     * @return Target Y in pixels: Y_closed, Y_half, or Y_full.
     */
    fun calculateTargetY(
        startY: Float,
        currentY: Float,
        velocityY: Float,
        screenHeightPx: Float,
        density: Float
    ): Float {
        val yClosed = getYClosed(screenHeightPx)
        val yHalf = getYHalf(screenHeightPx)
        val yFull = getYFull(screenHeightPx)

        val threshold0To1 = getThreshold0To1(screenHeightPx)
        val threshold1To2 = getThreshold1To2(screenHeightPx)

        // Convert velocity into dp/s: vyDp = velocityY / density
        // Negative = upward fling (decreasing Y), positive = downward fling (increasing Y)
        val effectiveDensity = if (density > 0f) density else 1f
        val vyDp = velocityY / effectiveDensity

        // 1. Extreme Upward Fling (Vy < -2500 dp/s) from Closed/lower zone: jump straight to Fully Expanded
        if (vyDp < -EXTREME_FLING_THRESHOLD_DP) {
            return yFull
        }

        // 2. Standard Upward Fling (Vy < -1000 dp/s)
        if (vyDp < -FLING_THRESHOLD_DP) {
            // From Closed (or lower half zone) snaps to Half. From Half snaps to Fully Expanded.
            return if (startY >= threshold0To1 || currentY > threshold0To1) {
                yHalf
            } else {
                yFull
            }
        }

        // 3. Standard Downward Fling (Vy > 1000 dp/s)
        if (vyDp > FLING_THRESHOLD_DP) {
            // From Fully Expanded snaps to Half (does NOT bypass intermediate state on standard fling).
            // From Half snaps to Closed.
            return if (startY <= threshold1To2 && currentY <= threshold0To1) {
                yHalf
            } else {
                yClosed
            }
        }

        // 4. Low-Velocity Drag Release: Settle to the nearest anchor determined by midpoints
        return when {
            currentY > threshold0To1 -> yClosed
            currentY > threshold1To2 -> yHalf
            else -> yFull
        }
    }

    /**
     * Piecewise Scrim Opacity Mapping:
     * - Closed to Half scales from 0.0 to 0.2
     * - Half to Fully Expanded scales from 0.2 to 0.6
     */
    fun calculateScrimAlpha(currentY: Float, screenHeightPx: Float): Float {
        val yClosed = getYClosed(screenHeightPx)
        val yHalf = getYHalf(screenHeightPx)
        val yFull = getYFull(screenHeightPx)

        return when {
            currentY >= yClosed -> 0.0f
            currentY >= yHalf -> {
                val fraction = ((yClosed - currentY) / (yClosed - yHalf)).coerceIn(0f, 1f)
                fraction * 0.2f
            }
            currentY >= yFull -> {
                val fraction = ((yHalf - currentY) / (yHalf - yFull)).coerceIn(0f, 1f)
                0.2f + fraction * 0.4f
            }
            else -> 0.6f
        }
    }

    /**
     * Viewer Chrome Layer Opacity Mapping:
     * Chrome alpha fades out from 1.0 to 0.0 as the sheet progresses from Closed to Half.
     */
    fun calculateChromeAlpha(currentY: Float, screenHeightPx: Float): Float {
        val yClosed = getYClosed(screenHeightPx)
        val yHalf = getYHalf(screenHeightPx)

        return when {
            currentY >= yClosed -> 1.0f
            currentY <= yHalf -> 0.0f
            else -> {
                val fraction = ((yClosed - currentY) / (yClosed - yHalf)).coerceIn(0f, 1f)
                (1.0f - fraction).coerceIn(0f, 1f)
            }
        }
    }
}
