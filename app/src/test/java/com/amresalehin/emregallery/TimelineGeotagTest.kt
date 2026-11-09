package com.amresalehin.emregallery

import com.amresalehin.emregallery.geotag.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config
class TimelineGeotagTest {

    @Test
    fun `test Google Takeout Records json parsing`() {
        val json = """
            {
              "locations": [
                {
                  "timestamp": "2023-05-14T10:00:00.000Z",
                  "latitudeE7": 377749000,
                  "longitudeE7": -1224194000,
                  "accuracy": 15,
                  "altitude": 25.5
                },
                {
                  "timestampMs": "1684059000000",
                  "latitudeE7": 377755000,
                  "longitudeE7": -1224180000,
                  "accuracy": 8
                }
              ]
            }
        """.trimIndent()

        val points = GoogleTimelineParser.parseTimeline(ByteArrayInputStream(json.toByteArray()))
        assertEquals(2, points.size)

        val first = points[0]
        assertEquals(37.7749, first.latitude, 0.0001)
        assertEquals(-122.4194, first.longitude, 0.0001)
        assertEquals(25.5, first.altitudeMeters ?: 0.0, 0.1)
        assertEquals(15f, first.accuracyMeters ?: 0f, 0.1f)
        assertEquals("records", first.source)

        val second = points[1]
        assertEquals(37.7755, second.latitude, 0.0001)
        assertEquals(-122.4180, second.longitude, 0.0001)
    }

    @Test
    fun `test Semantic Location History timelineObjects parsing`() {
        val json = """
            {
              "timelineObjects": [
                {
                  "placeVisit": {
                    "location": {
                      "latitudeE7": 407128000,
                      "longitudeE7": -740060000
                    },
                    "duration": {
                      "startTimestamp": "2023-06-10T14:00:00Z",
                      "endTimestamp": "2023-06-10T16:00:00Z"
                    }
                  }
                },
                {
                  "activitySegment": {
                    "duration": {
                      "startTimestamp": "2023-06-10T16:00:00Z",
                      "endTimestamp": "2023-06-10T17:00:00Z"
                    },
                    "startLocation": {
                      "latitudeE7": 407128000,
                      "longitudeE7": -740060000
                    },
                    "endLocation": {
                      "latitudeE7": 407580000,
                      "longitudeE7": -739855000
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val points = GoogleTimelineParser.parseTimeline(ByteArrayInputStream(json.toByteArray()))
        assertTrue("Should parse place visit and activity segment endpoints", points.size >= 2)

        val placePoint = points.first { it.source.startsWith("place_visit") }
        assertEquals(40.7128, placePoint.latitude, 0.0001)
        assertEquals(-74.0060, placePoint.longitude, 0.0001)
    }

    @Test
    fun `test modern timeline export with geo uri string`() {
        val json = """
            [
              {
                "time": "2023-07-20T08:30:00Z",
                "point": "geo:51.5074,-0.1278"
              },
              {
                "timestamp": "2023-07-20T08:45:00Z",
                "latitude": 51.5080,
                "longitude": -0.1285
              }
            ]
        """.trimIndent()

        val points = GoogleTimelineParser.parseTimeline(ByteArrayInputStream(json.toByteArray()))
        assertEquals(2, points.size)
        assertEquals(51.5074, points[0].latitude, 0.0001)
        assertEquals(-0.1278, points[0].longitude, 0.0001)
        assertEquals(51.5080, points[1].latitude, 0.0001)
        assertEquals(-0.1285, points[1].longitude, 0.0001)
    }

    @Test
    fun `test already geotagged images are skipped`() {
        val existingLat = 48.8566
        val existingLng = 2.3522
        val candidate = ImageGeotagCandidate(
            uriString = "content://media/1",
            displayName = "Paris_Eiffel.jpg",
            dateTimeOriginalMillis = 1684060000000L,
            hasExistingGeotag = true,
            existingLatitude = existingLat,
            existingLongitude = existingLng
        )

        val timelinePoints = listOf(
            TimelineLocationPoint(
                timestampMillis = 1684060000000L,
                latitude = 40.7128,
                longitude = -74.0060
            )
        )

        val config = GeotagConfig(skipAlreadyGeotagged = true)
        val result = TimelineGeotagEngine.matchCandidate(candidate, timelinePoints, config)

        // Must be skipped and retain its existing GPS
        assertEquals(GeotagStatus.ALREADY_GEOTAGGED, result.status)
        assertNotNull(result.matchedPoint)
        assertEquals(existingLat, result.matchedPoint?.latitude ?: 0.0, 0.0001)
        assertEquals(existingLng, result.matchedPoint?.longitude ?: 0.0, 0.0001)
    }

    @Test
    fun `test matching candidate within time window`() {
        val baseTime = 1684060000000L
        val timelinePoints = listOf(
            TimelineLocationPoint(
                timestampMillis = baseTime - 120_000L, // 2 minutes before
                latitude = 35.6762,
                longitude = 139.6503
            ),
            TimelineLocationPoint(
                timestampMillis = baseTime + 300_000L, // 5 minutes after
                latitude = 35.6770,
                longitude = 139.6520
            )
        )

        val unGeotaggedCandidate = ImageGeotagCandidate(
            uriString = "content://media/2",
            displayName = "Tokyo_Tower.jpg",
            dateTimeOriginalMillis = baseTime,
            hasExistingGeotag = false
        )

        val config = GeotagConfig(
            maxTimeDeltaSeconds = 900L,
            interpolateBetweenPoints = false
        )
        val result = TimelineGeotagEngine.matchCandidate(unGeotaggedCandidate, timelinePoints, config)

        assertEquals(GeotagStatus.MATCHED, result.status)
        assertNotNull(result.matchedPoint)
        // Should pick the closest (2 minutes before)
        assertEquals(35.6762, result.matchedPoint?.latitude ?: 0.0, 0.0001)
        assertEquals(120L, result.timeDifferenceSeconds)
    }

    @Test
    fun `test linear interpolation between surrounding timeline points`() {
        val t1 = 1684060000000L
        val t2 = 1684060600000L // 10 minutes later (600,000 ms)
        val p1 = TimelineLocationPoint(t1, latitude = 10.0, longitude = 20.0, altitudeMeters = 100.0)
        val p2 = TimelineLocationPoint(t2, latitude = 20.0, longitude = 40.0, altitudeMeters = 200.0)

        val photoTime = t1 + 300_000L // Exactly halfway (50%)
        val candidate = ImageGeotagCandidate(
            uriString = "content://media/3",
            displayName = "Moving_Train.jpg",
            dateTimeOriginalMillis = photoTime,
            hasExistingGeotag = false
        )

        val config = GeotagConfig(
            interpolateBetweenPoints = true,
            maxInterpolationGapSeconds = 1800L
        )

        val result = TimelineGeotagEngine.matchCandidate(candidate, listOf(p1, p2), config)

        assertEquals(GeotagStatus.MATCHED, result.status)
        assertTrue(result.isInterpolated)
        assertNotNull(result.matchedPoint)
        // 50% between (10, 20) and (20, 40) is (15, 30)
        assertEquals(15.0, result.matchedPoint?.latitude ?: 0.0, 0.0001)
        assertEquals(30.0, result.matchedPoint?.longitude ?: 0.0, 0.0001)
        assertEquals(150.0, result.matchedPoint?.altitudeMeters ?: 0.0, 0.0001)
    }

    @Test
    fun `test unmatched when time delta exceeds maximum allowed window`() {
        val baseTime = 1684060000000L
        val timelinePoints = listOf(
            TimelineLocationPoint(
                timestampMillis = baseTime - 7200_000L, // 2 hours before
                latitude = 35.6762,
                longitude = 139.6503
            )
        )

        val candidate = ImageGeotagCandidate(
            uriString = "content://media/4",
            displayName = "Late_Photo.jpg",
            dateTimeOriginalMillis = baseTime,
            hasExistingGeotag = false
        )

        val config = GeotagConfig(maxTimeDeltaSeconds = 900L) // 15 mins
        val result = TimelineGeotagEngine.matchCandidate(candidate, timelinePoints, config)

        assertEquals(GeotagStatus.UNMATCHED, result.status)
    }

    @Test
    fun `test camera clock drift adjustment`() {
        val timelinePointTime = 1684060000000L
        val timelinePoints = listOf(
            TimelineLocationPoint(
                timestampMillis = timelinePointTime,
                latitude = 52.5200,
                longitude = 13.4050
            )
        )

        // Photo camera clock was 60 seconds slow
        val photoCameraTime = timelinePointTime - 60_000L
        val candidate = ImageGeotagCandidate(
            uriString = "content://media/5",
            displayName = "Drift_Photo.jpg",
            dateTimeOriginalMillis = photoCameraTime,
            hasExistingGeotag = false
        )

        // Set camera clock offset to +60 seconds to correct for the slow camera clock
        val config = GeotagConfig(cameraClockOffsetSeconds = 60L)
        val result = TimelineGeotagEngine.matchCandidate(candidate, timelinePoints, config)

        assertEquals(GeotagStatus.MATCHED, result.status)
        assertEquals(0L, result.timeDifferenceSeconds)
        assertEquals(52.5200, result.matchedPoint?.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun `test EXIF date parsing with timezone offset`() {
        val exifDate = "2023:08:15 14:30:00"
        val offset = "+02:00"

        val millisWithOffset = TimelineGeotagEngine.parseExifDateTime(exifDate, offset)
        assertNotNull(millisWithOffset)

        val millisUtc = TimelineGeotagEngine.parseExifDateTime(exifDate, "+00:00")
        assertNotNull(millisUtc)

        // +02:00 should be 2 hours earlier in UTC epoch millis than +00:00
        val diffHours = (millisUtc!! - millisWithOffset!!) / (1000L * 3600L)
        assertEquals(2L, diffHours)
    }

    @Test
    fun `test GPX export generation`() {
        val points = listOf(
            TimelineLocationPoint(
                timestampMillis = 1684060000000L,
                latitude = 37.7749,
                longitude = -122.4194,
                altitudeMeters = 10.5
            )
        )
        val gpx = TimelineGeotagEngine.exportToGpx(points, "Test Track")
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("lat=\"37.7749\""))
        assertTrue(gpx.contains("lon=\"-122.4194\""))
        assertTrue(gpx.contains("<ele>10.5</ele>"))
        assertTrue(gpx.contains("<name>Test Track</name>"))
    }
}
