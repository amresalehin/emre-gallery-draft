package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

class PhotoMapClampingAndFitTest {

    @Test
    fun testWorldViewConstantsClampedProperly() {
        assertEquals(85.0511, WORLD_VIEW_LAT_NORTH, 0.0001)
        assertEquals(-85.0511, WORLD_VIEW_LAT_SOUTH, 0.0001)
        assertEquals(-180.0, WORLD_VIEW_LON_WEST, 0.0001)
        assertEquals(180.0, WORLD_VIEW_LON_EAST, 0.0001)
        assertEquals(0.0, WORLD_VIEW_CENTER.latitude, 0.0001)
        assertEquals(0.0, WORLD_VIEW_CENTER.longitude, 0.0001)

        assertEquals(WORLD_VIEW_LAT_NORTH, WORLD_VIEW_BOUNDS.latNorth, 0.0001)
        assertEquals(WORLD_VIEW_LAT_SOUTH, WORLD_VIEW_BOUNDS.latSouth, 0.0001)
        assertEquals(WORLD_VIEW_LON_WEST, WORLD_VIEW_BOUNDS.lonWest, 0.0001)
        assertEquals(WORLD_VIEW_LON_EAST, WORLD_VIEW_BOUNDS.lonEast, 0.0001)
    }

    @Test
    fun testComputeMediaBoundingBoxEmptyPoints() {
        assertNull(computeMediaBoundingBox(emptyList()))
    }

    @Test
    fun testComputeMediaBoundingBoxSinglePoint() {
        val pt = GeoPoint(37.7749, -122.4194)
        val box = computeMediaBoundingBox(listOf(pt))
        assertNotNull(box)
        box!!
        assertTrue(box.latNorth > pt.latitude)
        assertTrue(box.latSouth < pt.latitude)
        assertTrue(box.lonEast > pt.longitude)
        assertTrue(box.lonWest < pt.longitude)
        assertTrue(box.latNorth <= WORLD_VIEW_LAT_NORTH)
        assertTrue(box.latSouth >= WORLD_VIEW_LAT_SOUTH)
    }

    @Test
    fun testComputeMediaBoundingBoxMultiplePointsClamping() {
        val points = listOf(
            GeoPoint(89.0, 190.0), // extreme values beyond Mercator
            GeoPoint(-90.0, -190.0),
            GeoPoint(40.0, 10.0)
        )
        val box = computeMediaBoundingBox(points)
        assertNotNull(box)
        box!!
        // Should clamp strictly to [-85.0511, 85.0511] and [-180, 180]
        assertTrue("latNorth must be <= 85.0511", box.latNorth <= 85.0511)
        assertTrue("latSouth must be >= -85.0511", box.latSouth >= -85.0511)
        assertTrue("lonEast must be <= 180.0", box.lonEast <= 180.0)
        assertTrue("lonWest must be >= -180.0", box.lonWest >= -180.0)
    }

    @Test
    fun testViewportMediaFilteringWithinValidBounds() {
        val photo1 = MediaItem(uri = "content://1", name = "P1", size = 100, dateAddedSeconds = 1000)
        val photo2 = MediaItem(uri = "content://2", name = "P2", size = 100, dateAddedSeconds = 1000)
        val photo3 = MediaItem(uri = "content://3", name = "P3", size = 100, dateAddedSeconds = 1000)

        val located = listOf(
            photo1 to GeoPoint(51.5074, -0.1278), // London
            photo2 to GeoPoint(40.7128, -74.0060), // New York
            photo3 to GeoPoint(-33.8688, 151.2093) // Sydney
        )

        // When bounds encompass the world, all photos are returned
        val worldState = PhotoMapUiState(
            locatedMedia = located,
            visibleBounds = WORLD_VIEW_BOUNDS
        )
        assertEquals(3, worldState.viewportMedia.size)

        // When bounds are localized around London
        val londonBounds = BoundingBox(52.0, 1.0, 51.0, -1.0)
        val londonState = PhotoMapUiState(
            locatedMedia = located,
            visibleBounds = londonBounds
        )
        assertEquals(1, londonState.viewportMedia.size)
        assertEquals("P1", londonState.viewportMedia.first().first.name)
    }
}
