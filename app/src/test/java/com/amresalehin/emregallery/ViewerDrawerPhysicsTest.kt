package com.amresalehin.emregallery

import com.amresalehin.emregallery.ui.ViewerDrawerPhysics
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerDrawerPhysicsTest {

    private val screenHeight = 2400f
    private val density = 2.5f

    private val yClosed = ViewerDrawerPhysics.getYClosed(screenHeight) // 2400f
    private val yHalf = ViewerDrawerPhysics.getYHalf(screenHeight)     // 1080f
    private val yFull = ViewerDrawerPhysics.getYFull(screenHeight)     // 360f

    private val threshold0To1 = ViewerDrawerPhysics.getThreshold0To1(screenHeight) // (2400 + 1080) / 2 = 1740f
    private val threshold1To2 = ViewerDrawerPhysics.getThreshold1To2(screenHeight) // (1080 + 360) / 2 = 720f

    @Test
    fun testLowVelocitySettlingByMidpoints() {
        // Below Threshold 0->1 (> 1740): settles to Closed
        val settleClosed = ViewerDrawerPhysics.calculateTargetY(
            startY = yClosed,
            currentY = threshold0To1 + 50f,
            velocityY = 0f,
            screenHeightPx = screenHeight,
            density = density
        )
        assertEquals("Release below threshold0To1 must settle to Closed", yClosed, settleClosed, 1f)

        // Between Threshold 0->1 and Threshold 1->2 (720 < Y <= 1740): settles to Half
        val settleHalf = ViewerDrawerPhysics.calculateTargetY(
            startY = yClosed,
            currentY = threshold0To1 - 50f,
            velocityY = 0f,
            screenHeightPx = screenHeight,
            density = density
        )
        assertEquals("Release between midpoints must settle to Half", yHalf, settleHalf, 1f)

        // Above Threshold 1->2 (<= 720): settles to Fully Expanded
        val settleFull = ViewerDrawerPhysics.calculateTargetY(
            startY = yHalf,
            currentY = threshold1To2 - 50f,
            velocityY = 0f,
            screenHeightPx = screenHeight,
            density = density
        )
        assertEquals("Release above threshold1To2 must settle to Fully Expanded", yFull, settleFull, 1f)
    }

    @Test
    fun testStandardUpwardFling() {
        // Upward fling (-1200 dp/s = -3000 px/s with density=2.5) from Closed: snaps to Half
        val flingFromClosed = ViewerDrawerPhysics.calculateTargetY(
            startY = yClosed,
            currentY = yClosed - 200f,
            velocityY = -1200f * density,
            screenHeightPx = screenHeight,
            density = density
        )
        assertEquals("Standard upward fling from Closed snaps to Half", yHalf, flingFromClosed, 1f)

        // Upward fling (-1200 dp/s) from Half: snaps to Fully Expanded
        val flingFromHalf = ViewerDrawerPhysics.calculateTargetY(
            startY = yHalf,
            currentY = yHalf - 150f,
            velocityY = -1200f * density,
            screenHeightPx = screenHeight,
            density = density
        )
        assertEquals("Standard upward fling from Half snaps to Fully Expanded", yFull, flingFromHalf, 1f)
    }

    @Test
    fun testExtremeUpwardFlingJumpsStraightToFull() {
        // Extreme upward fling (-2800 dp/s) from Closed jumps straight to Fully Expanded
        val extremeFling = ViewerDrawerPhysics.calculateTargetY(
            startY = yClosed,
            currentY = yClosed - 200f,
            velocityY = -2800f * density,
            screenHeightPx = screenHeight,
            density = density
        )
        assertEquals("Extreme upward fling from Closed jumps straight to Fully Expanded", yFull, extremeFling, 1f)
    }

    @Test
    fun testStandardDownwardFling() {
        // Downward fling (+1200 dp/s) from Fully Expanded: snaps to Half (does not bypass intermediate state)
        val flingFromFull = ViewerDrawerPhysics.calculateTargetY(
            startY = yFull,
            currentY = yFull + 150f,
            velocityY = 1200f * density,
            screenHeightPx = screenHeight,
            density = density
        )
        assertEquals("Standard downward fling from Full snaps to Half", yHalf, flingFromFull, 1f)

        // Downward fling (+1200 dp/s) from Half: snaps to Closed
        val flingFromHalf = ViewerDrawerPhysics.calculateTargetY(
            startY = yHalf,
            currentY = yHalf + 200f,
            velocityY = 1200f * density,
            screenHeightPx = screenHeight,
            density = density
        )
        assertEquals("Standard downward fling from Half snaps to Closed", yClosed, flingFromHalf, 1f)
    }

    @Test
    fun testPiecewiseScrimAlpha() {
        // At Closed: alpha = 0.0
        val scrimClosed = ViewerDrawerPhysics.calculateScrimAlpha(yClosed, screenHeight)
        assertEquals(0.0f, scrimClosed, 0.001f)

        // At Half: alpha = 0.2
        val scrimHalf = ViewerDrawerPhysics.calculateScrimAlpha(yHalf, screenHeight)
        assertEquals(0.2f, scrimHalf, 0.001f)

        // Midpoint between Closed and Half: alpha = 0.1
        val midClosedHalf = (yClosed + yHalf) / 2f
        val scrimMid1 = ViewerDrawerPhysics.calculateScrimAlpha(midClosedHalf, screenHeight)
        assertEquals(0.1f, scrimMid1, 0.01f)

        // At Fully Expanded: alpha = 0.6
        val scrimFull = ViewerDrawerPhysics.calculateScrimAlpha(yFull, screenHeight)
        assertEquals(0.6f, scrimFull, 0.001f)

        // Midpoint between Half and Full: alpha = 0.4
        val midHalfFull = (yHalf + yFull) / 2f
        val scrimMid2 = ViewerDrawerPhysics.calculateScrimAlpha(midHalfFull, screenHeight)
        assertEquals(0.4f, scrimMid2, 0.01f)
    }

    @Test
    fun testViewerChromeAlpha() {
        // At Closed: alpha = 1.0
        val chromeClosed = ViewerDrawerPhysics.calculateChromeAlpha(yClosed, screenHeight)
        assertEquals(1.0f, chromeClosed, 0.001f)

        // Midpoint between Closed and Half: alpha = 0.5
        val midClosedHalf = (yClosed + yHalf) / 2f
        val chromeMid = ViewerDrawerPhysics.calculateChromeAlpha(midClosedHalf, screenHeight)
        assertEquals(0.5f, chromeMid, 0.01f)

        // At Half: alpha = 0.0
        val chromeHalf = ViewerDrawerPhysics.calculateChromeAlpha(yHalf, screenHeight)
        assertEquals(0.0f, chromeHalf, 0.001f)

        // At Full: alpha = 0.0
        val chromeFull = ViewerDrawerPhysics.calculateChromeAlpha(yFull, screenHeight)
        assertEquals(0.0f, chromeFull, 0.001f)
    }
}
