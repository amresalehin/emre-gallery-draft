package com.amresalehin.emregallery.ui

import kotlin.math.abs

/**
 * Authoritative physics, detents, and settlement calculations for the Media Details Drawer.
 *
 * The drawer supports six discrete resting positions:
 * 1. Closed (~0%)
 * 2. Compact (~35%)
 * 3. Medium (~50%)
 * 4. Initial (~65%)
 * 5. Large (~80%)
 * 6. Expanded (~95%)
 *
 * Supports fast, intentional deliberate dismissal directly from any open detent (including Expanded)
 * without stepping through intermediate detents, while preventing accidental closures during
 * ordinary downward drags or small flicks.
 */
object ViewerDrawerPhysics {
    const val DETENT_CLOSED_FRACTION = 0f
    const val DETENT_COMPACT_FRACTION = 0.35f
    const val DETENT_MEDIUM_FRACTION = 0.50f
    const val DETENT_INITIAL_FRACTION = 0.65f
    const val DETENT_LARGE_FRACTION = 0.80f
    const val DETENT_EXPANDED_FRACTION = 0.95f

    // Deliberate dismissal criteria
    // Meaningful travel distance targeting ~20-25% of screen height from gesture start
    const val DISMISS_DISTANCE_FRACTION = 0.22f

    // Minimum downward distance traveled required before any fling can qualify for dismissal
    const val MEANINGFUL_DOWNWARD_TRAVEL_FRACTION = 0.12f

    /**
     * Returns all open resting detent heights in pixels, sorted in ascending order.
     */
    fun getOpenDetentHeights(screenHeightPx: Float): List<Float> {
        return listOf(
            screenHeightPx * DETENT_COMPACT_FRACTION,
            screenHeightPx * DETENT_MEDIUM_FRACTION,
            screenHeightPx * DETENT_INITIAL_FRACTION,
            screenHeightPx * DETENT_LARGE_FRACTION,
            screenHeightPx * DETENT_EXPANDED_FRACTION
        )
    }

    /**
     * Determines the authoritative target height for the drawer at gesture release.
     *
     * @param gestureStartHeightPx Height of the drawer when the current gesture started.
     * @param currentHeightPx Current rendered height of the drawer at touch release.
     * @param upwardVelocityPx Upward velocity in px/s (positive = moving up, negative = moving down).
     * @param screenHeightPx Available screen height in pixels.
     * @param minFlingVelocity Minimum velocity threshold for a fling.
     * @return Target height in pixels: 0f for Closed, or one of the open detents.
     */
    fun calculateTargetHeight(
        gestureStartHeightPx: Float,
        currentHeightPx: Float,
        upwardVelocityPx: Float,
        screenHeightPx: Float,
        minFlingVelocity: Float
    ): Float {
        val openDetents = getOpenDetentHeights(screenHeightPx)
        val compactPx = openDetents[0]
        val initialPx = openDetents[2]
        val expandedPx = openDetents[4]

        val downwardTravel = gestureStartHeightPx - currentHeightPx
        val dismissDistanceThreshold = screenHeightPx * DISMISS_DISTANCE_FRACTION
        val meaningfulDownwardTravel = screenHeightPx * MEANINGFUL_DOWNWARD_TRAVEL_FRACTION
        val decisiveDownwardFling = minFlingVelocity * 1.5f

        // Condition 1: Deliberate downward dismissal by distance
        // User dragged downward by >= ~22% of screen height from gesture start,
        // and is releasing or moving downward (not vigorously reversing upward).
        if (downwardTravel >= dismissDistanceThreshold && upwardVelocityPx <= minFlingVelocity * 0.4f) {
            return 0f // Transition directly to Closed!
        }

        // Condition 2: Decisive downward fling AFTER meaningful downward travel
        // (Must have travelled downward by at least ~12% screen height; strong velocity alone after tiny movement does NOT close)
        if (downwardTravel >= meaningfulDownwardTravel && upwardVelocityPx < -decisiveDownwardFling) {
            return 0f // Transition directly to Closed!
        }

        // Condition 3: Dragged well below Compact and released while moving downward
        if (currentHeightPx < compactPx * 0.55f && downwardTravel > 0f && upwardVelocityPx <= 0f) {
            return 0f
        }

        // --- If dismissal criteria are NOT met, settle at an appropriate OPEN detent ---

        // Upward fling: move to the next higher open detent
        if (upwardVelocityPx > minFlingVelocity) {
            val nextHigher = openDetents.firstOrNull { it > currentHeightPx + 15f }
            return nextHigher ?: expandedPx
        }

        // Downward fling that did NOT meet deliberate dismissal (e.g., Expanded -> Large or flick after small travel):
        // Move to the next lower open detent instead of closing
        if (upwardVelocityPx < -minFlingVelocity) {
            val nextLower = openDetents.lastOrNull { it < currentHeightPx - 15f }
            return nextLower ?: compactPx
        }

        // Ordinary drag with low velocity: settle at nearest open detent without overshoot
        return openDetents.minByOrNull { abs(it - currentHeightPx) } ?: initialPx
    }

    /**
     * Settle to nearest open detent (for cancellation or fallback without closing).
     */
    fun nearestOpenDetent(currentHeightPx: Float, screenHeightPx: Float): Float {
        val openDetents = getOpenDetentHeights(screenHeightPx)
        return openDetents.minByOrNull { abs(it - currentHeightPx) } ?: (screenHeightPx * DETENT_INITIAL_FRACTION)
    }
}
