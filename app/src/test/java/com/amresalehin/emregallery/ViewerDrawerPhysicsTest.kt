package com.amresalehin.emregallery

import com.amresalehin.emregallery.ui.ViewerDrawerPhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ViewerDrawerPhysicsTest {

    private val screenHeight = 2400f
    private val minFling = 500f

    private val compact = screenHeight * ViewerDrawerPhysics.DETENT_COMPACT_FRACTION // 840
    private val medium = screenHeight * ViewerDrawerPhysics.DETENT_MEDIUM_FRACTION // 1200
    private val initial = screenHeight * ViewerDrawerPhysics.DETENT_INITIAL_FRACTION // 1560
    private val large = screenHeight * ViewerDrawerPhysics.DETENT_LARGE_FRACTION // 1920
    private val expanded = screenHeight * ViewerDrawerPhysics.DETENT_EXPANDED_FRACTION // 2280

    @Test
    fun testSmallDownwardDragSettlesAtNearestOpenDetent() {
        // Small downward drag from Expanded: 2280 -> 2200 (travel = 80px, < 12%)
        val resultExpanded = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = expanded,
            currentHeightPx = expanded - 80f,
            upwardVelocityPx = 0f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Small drag from Expanded must settle back at Expanded", expanded, resultExpanded, 1f)

        // Small downward drag from Large: 1920 -> 1850 (travel = 70px)
        val resultLarge = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = large,
            currentHeightPx = large - 70f,
            upwardVelocityPx = 0f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Small drag from Large must settle back at Large", large, resultLarge, 1f)

        // Small downward drag from Initial: 1560 -> 1500 (travel = 60px)
        val resultInitial = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = initial,
            currentHeightPx = initial - 60f,
            upwardVelocityPx = 0f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Small drag from Initial must settle back at Initial", initial, resultInitial, 1f)

        // Small downward drag from Medium: 1200 -> 1140 (travel = 60px)
        val resultMedium = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = medium,
            currentHeightPx = medium - 60f,
            upwardVelocityPx = 0f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Small drag from Medium must settle back at Medium", medium, resultMedium, 1f)

        // Small downward drag from Compact: 840 -> 800 (travel = 40px)
        val resultCompact = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = compact,
            currentHeightPx = compact - 40f,
            upwardVelocityPx = 0f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Small drag from Compact must settle back at Compact and not snap to Closed", compact, resultCompact, 1f)
    }

    @Test
    fun testDeliberateDownwardDismissalFromAllOpenDetents() {
        // Deliberate dismissal travel >= 22% of screen height (528px)
        val deliberateTravel = screenHeight * 0.24f // 576px

        // From Expanded
        val closeFromExpanded = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = expanded,
            currentHeightPx = expanded - deliberateTravel,
            upwardVelocityPx = -200f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Deliberate dismissal from Expanded must transition directly to Closed (0f)", 0f, closeFromExpanded, 0.01f)

        // From Large
        val closeFromLarge = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = large,
            currentHeightPx = large - deliberateTravel,
            upwardVelocityPx = -200f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Deliberate dismissal from Large must transition directly to Closed (0f)", 0f, closeFromLarge, 0.01f)

        // From Initial
        val closeFromInitial = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = initial,
            currentHeightPx = initial - deliberateTravel,
            upwardVelocityPx = -200f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Deliberate dismissal from Initial must transition directly to Closed (0f)", 0f, closeFromInitial, 0.01f)

        // From Medium
        val closeFromMedium = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = medium,
            currentHeightPx = medium - deliberateTravel,
            upwardVelocityPx = -200f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Deliberate dismissal from Medium must transition directly to Closed (0f)", 0f, closeFromMedium, 0.01f)

        // From Compact
        val closeFromCompact = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = compact,
            currentHeightPx = compact - deliberateTravel,
            upwardVelocityPx = -200f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Deliberate dismissal from Compact must transition directly to Closed (0f)", 0f, closeFromCompact, 0.01f)
    }

    @Test
    fun testFastDownwardGestureAfterMinimalTravelDoesNotClose() {
        // High downward velocity (-1200 px/s) after only tiny downward travel (travel = 50px = ~2% screen height)
        val result = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = expanded,
            currentHeightPx = expanded - 50f,
            upwardVelocityPx = -1200f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        // Strong velocity alone after minimal travel must NOT close the drawer!
        assertNotEquals("Strong velocity after tiny travel must not close drawer", 0f, result)
        assertEquals("Should move to next lower detent (Large)", large, result, 1f)
    }

    @Test
    fun testDecisiveDownwardFlingAfterMeaningfulTravelCloses() {
        // Travel = 15% of screen height (> 12% meaningful travel) and decisive fling (-1000 px/s)
        val meaningfulTravel = screenHeight * 0.15f // 360px
        val result = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = expanded,
            currentHeightPx = expanded - meaningfulTravel,
            upwardVelocityPx = -1000f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Decisive downward fling after meaningful travel must close drawer", 0f, result, 0.01f)
    }

    @Test
    fun testDragFromExpandedTowardLargeDoesNotClose() {
        // User drags from Expanded (2280) to Large (1920): travel = 360px (~15% screen height) with low velocity
        val result = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = expanded,
            currentHeightPx = large,
            upwardVelocityPx = 0f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Dragging from Expanded to Large must settle at Large", large, result, 1f)
    }

    @Test
    fun testUpwardFlingMovesToNextHigherDetent() {
        // At Medium, flinging upward (+800 px/s)
        val result = ViewerDrawerPhysics.calculateTargetHeight(
            gestureStartHeightPx = medium,
            currentHeightPx = medium + 50f,
            upwardVelocityPx = 800f,
            screenHeightPx = screenHeight,
            minFlingVelocity = minFling
        )
        assertEquals("Upward fling from Medium must settle at Initial", initial, result, 1f)
    }
}
