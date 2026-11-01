package com.amresalehin.emregallery

import com.amresalehin.emregallery.ui.normalizedExportDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorExportSizingTest {

    @Test
    fun keepsSmallRequestedDimensionsUnchanged() {
        assertEquals(3200 to 2400, normalizedExportDimensions(3200, 2400))
    }

    @Test
    fun capsVeryLargeOutputByPixelAndSideLimits() {
        val (width, height) = normalizedExportDimensions(100_000, 50_000)
        assertTrue(width in 1..16_384)
        assertTrue(height in 1..16_384)
        assertTrue(width.toLong() * height.toLong() <= 40_000_000L)
        assertEquals(2.0, width.toDouble() / height.toDouble(), 0.002)
    }

    @Test
    fun squareLargeRequestDoesNotRoundAbovePixelCap() {
        val (width, height) = normalizedExportDimensions(100_000, 100_000)
        assertTrue(width.toLong() * height.toLong() <= 40_000_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroWidth() {
        normalizedExportDimensions(0, 1080)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeHeight() {
        normalizedExportDimensions(1920, -1)
    }
}
