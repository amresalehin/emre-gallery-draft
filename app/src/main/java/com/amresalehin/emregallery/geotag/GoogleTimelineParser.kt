package com.amresalehin.emregallery.geotag

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs

/**
 * High-performance streaming parser for Google Maps Timeline / Location History JSON files.
 * Handles:
 * 1. Classic Google Takeout "Records.json" / "Location History.json" (with locations array and latitudeE7 / longitudeE7)
 * 2. Semantic Location History monthly files (with timelineObjects: placeVisit and activitySegment)
 * 3. Modern Android/iOS on-device Timeline exports (semanticSegments, rawSignals, and bare segment arrays)
 */
object GoogleTimelineParser {

    /**
     * Parses an input stream of a Google Timeline JSON export.
     *
     * @param inputStream The source stream from file or content URI
     * @param minTimestampMillis Optional minimum timestamp to filter points (with safety buffer)
     * @param maxTimestampMillis Optional maximum timestamp to filter points (with safety buffer)
     * @param onProgress Optional callback reporting number of points parsed so far
     * @return List of parsed [TimelineLocationPoint] sorted chronologically
     */
    fun parseTimeline(
        inputStream: InputStream,
        minTimestampMillis: Long? = null,
        maxTimestampMillis: Long? = null,
        onProgress: ((pointsCount: Int) -> Unit)? = null
    ): List<TimelineLocationPoint> {
        val points = mutableListOf<TimelineLocationPoint>()
        val reader = JsonReader(InputStreamReader(inputStream, Charsets.UTF_8))
        reader.isLenient = true

        try {
            when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> {
                    parseRootObject(reader, points, minTimestampMillis, maxTimestampMillis, onProgress)
                }
                JsonToken.BEGIN_ARRAY -> {
                    parseArrayOfObjects(reader, points, minTimestampMillis, maxTimestampMillis, onProgress)
                }
                else -> {
                    // Unexpected token
                }
            }
        } finally {
            runCatching { reader.close() }
        }

        // Sort chronologically
        points.sortBy { it.timestampMillis }
        return points
    }

    private fun parseRootObject(
        reader: JsonReader,
        points: MutableList<TimelineLocationPoint>,
        minTimestampMillis: Long?,
        maxTimestampMillis: Long?,
        onProgress: ((Int) -> Unit)?
    ) {
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "locations" -> {
                    parseLocationsArray(reader, points, minTimestampMillis, maxTimestampMillis, onProgress)
                }
                "timelineObjects" -> {
                    parseTimelineObjectsArray(reader, points, minTimestampMillis, maxTimestampMillis, onProgress)
                }
                "semanticSegments" -> {
                    parseArrayOfObjects(reader, points, minTimestampMillis, maxTimestampMillis, onProgress)
                }
                "rawSignals" -> {
                    parseRawSignalsArray(reader, points, minTimestampMillis, maxTimestampMillis, onProgress)
                }
                else -> {
                    reader.skipValue()
                }
            }
        }
        reader.endObject()
    }

    private fun parseArrayOfObjects(
        reader: JsonReader,
        points: MutableList<TimelineLocationPoint>,
        minTimestampMillis: Long?,
        maxTimestampMillis: Long?,
        onProgress: ((Int) -> Unit)?
    ) {
        reader.beginArray()
        var count = 0
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                parseGenericTimelineItem(reader, points, minTimestampMillis, maxTimestampMillis)
                count++
                if (count % 2000 == 0) {
                    onProgress?.invoke(points.size)
                }
            } else {
                reader.skipValue()
            }
        }
        reader.endArray()
        onProgress?.invoke(points.size)
    }

    /**
     * Parses standard Takeout "locations": [ { "timestamp": "...", "latitudeE7": ..., "longitudeE7": ... } ]
     */
    private fun parseLocationsArray(
        reader: JsonReader,
        points: MutableList<TimelineLocationPoint>,
        minTimestampMillis: Long?,
        maxTimestampMillis: Long?,
        onProgress: ((Int) -> Unit)?
    ) {
        reader.beginArray()
        var scanned = 0
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            var tsMillis: Long? = null
            var latE7: Long? = null
            var lngE7: Long? = null
            var latDirect: Double? = null
            var lngDirect: Double? = null
            var accuracy: Float? = null
            var altitude: Double? = null

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "timestamp" -> {
                        tsMillis = parseTimestampToMillis(reader.nextString())
                    }
                    "timestampMs" -> {
                        tsMillis = reader.nextString().toLongOrNull()
                    }
                    "latitudeE7" -> {
                        latE7 = reader.nextLong()
                    }
                    "longitudeE7" -> {
                        lngE7 = reader.nextLong()
                    }
                    "latitude" -> {
                        latDirect = reader.nextDouble()
                    }
                    "longitude" -> {
                        lngDirect = reader.nextDouble()
                    }
                    "accuracy" -> {
                        accuracy = reader.nextDouble().toFloat()
                    }
                    "altitude" -> {
                        altitude = reader.nextDouble()
                    }
                    else -> {
                        reader.skipValue()
                    }
                }
            }
            reader.endObject()

            val finalLat = when {
                latE7 != null -> e7ToDegrees(latE7, 900_000_000L)
                latDirect != null -> latDirect
                else -> null
            }
            val finalLng = when {
                lngE7 != null -> e7ToDegrees(lngE7, 1_800_000_000L)
                lngDirect != null -> lngDirect
                else -> null
            }

            if (tsMillis != null && finalLat != null && finalLng != null && isValidCoordinate(finalLat, finalLng)) {
                if (isWithinRange(tsMillis, minTimestampMillis, maxTimestampMillis)) {
                    points.add(
                        TimelineLocationPoint(
                            timestampMillis = tsMillis,
                            latitude = finalLat,
                            longitude = finalLng,
                            altitudeMeters = altitude,
                            accuracyMeters = accuracy,
                            source = "records"
                        )
                    )
                }
            }

            scanned++
            if (scanned % 5000 == 0) {
                onProgress?.invoke(points.size)
            }
        }
        reader.endArray()
        onProgress?.invoke(points.size)
    }

    /**
     * Parses Semantic Location History "timelineObjects": [ { "activitySegment": ... }, { "placeVisit": ... } ]
     */
    private fun parseTimelineObjectsArray(
        reader: JsonReader,
        points: MutableList<TimelineLocationPoint>,
        minTimestampMillis: Long?,
        maxTimestampMillis: Long?,
        onProgress: ((Int) -> Unit)?
    ) {
        reader.beginArray()
        var scanned = 0
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "placeVisit" -> parsePlaceVisit(reader, points, minTimestampMillis, maxTimestampMillis)
                    "activitySegment" -> parseActivitySegment(reader, points, minTimestampMillis, maxTimestampMillis)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            scanned++
            if (scanned % 500 == 0) {
                onProgress?.invoke(points.size)
            }
        }
        reader.endArray()
        onProgress?.invoke(points.size)
    }

    private fun parsePlaceVisit(
        reader: JsonReader,
        points: MutableList<TimelineLocationPoint>,
        minTimestampMillis: Long?,
        maxTimestampMillis: Long?
    ) {
        reader.beginObject()
        var startTs: Long? = null
        var endTs: Long? = null
        var lat: Double? = null
        var lng: Double? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "location" -> {
                    val pair = parseLocationSubObject(reader)
                    lat = pair?.first
                    lng = pair?.second
                }
                "duration" -> {
                    val dur = parseDurationSubObject(reader)
                    startTs = dur.first
                    endTs = dur.second
                }
                "centerLatE7" -> {
                    lat = e7ToDegrees(reader.nextLong(), 900_000_000L)
                }
                "centerLngE7" -> {
                    lng = e7ToDegrees(reader.nextLong(), 1_800_000_000L)
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
            val ts = startTs ?: endTs
            if (ts != null && isWithinRange(ts, minTimestampMillis, maxTimestampMillis)) {
                points.add(
                    TimelineLocationPoint(
                        timestampMillis = ts,
                        latitude = lat,
                        longitude = lng,
                        source = "place_visit",
                        segmentId = "visit:${ts}:${endTs ?: ts}",
                        interpolationEligible = false,
                        intervalEndMillis = endTs
                    )
                )
                // If there's an end time separated from start time, add the end point as well
                if (endTs != null && endTs > ts && isWithinRange(endTs, minTimestampMillis, maxTimestampMillis)) {
                    points.add(
                        TimelineLocationPoint(
                            timestampMillis = endTs,
                            latitude = lat,
                            longitude = lng,
                            source = "place_visit_end",
                            segmentId = "visit:${ts}:${endTs}",
                            interpolationEligible = false
                        )
                    )
                }
            }
        }
    }

    private fun parseActivitySegment(
        reader: JsonReader,
        points: MutableList<TimelineLocationPoint>,
        minTimestampMillis: Long?,
        maxTimestampMillis: Long?
    ) {
        reader.beginObject()
        var startTs: Long? = null
        var endTs: Long? = null
        var startCoord: Pair<Double, Double>? = null
        var endCoord: Pair<Double, Double>? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "duration" -> {
                    val dur = parseDurationSubObject(reader)
                    startTs = dur.first
                    endTs = dur.second
                }
                "startLocation" -> {
                    startCoord = parseLocationSubObject(reader)
                }
                "endLocation" -> {
                    endCoord = parseLocationSubObject(reader)
                }
                "waypointPath" -> {
                    parseWaypointPath(reader, points, startTs, endTs, minTimestampMillis, maxTimestampMillis)
                }
                "simplifiedRawPath" -> {
                    parseSimplifiedRawPath(reader, points, minTimestampMillis, maxTimestampMillis)
                }
                "timelinePath" -> {
                    parseTimelinePath(reader, points, startTs, endTs, minTimestampMillis, maxTimestampMillis)
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        // If waypoints didn't populate, at least capture start and end locations
        if (startTs != null && startCoord != null && isValidCoordinate(startCoord.first, startCoord.second)) {
            if (isWithinRange(startTs, minTimestampMillis, maxTimestampMillis)) {
                points.add(
                    TimelineLocationPoint(
                        timestampMillis = startTs,
                        latitude = startCoord.first,
                        longitude = startCoord.second,
                        source = "activity_start"
                    )
                )
            }
        }
        if (endTs != null && endCoord != null && isValidCoordinate(endCoord.first, endCoord.second)) {
            if (isWithinRange(endTs, minTimestampMillis, maxTimestampMillis)) {
                points.add(
                    TimelineLocationPoint(
                        timestampMillis = endTs,
                        latitude = endCoord.first,
                        longitude = endCoord.second,
                        source = "activity_end"
                    )
                )
            }
        }
    }

    private fun parseWaypointPath(
        reader: JsonReader,
        points: MutableList<TimelineLocationPoint>,
        startTs: Long?,
        endTs: Long?,
        minTimestampMillis: Long?,
        maxTimestampMillis: Long?
    ) {
        reader.beginObject()
        val coords = mutableListOf<Pair<Double, Double>>()
        while (reader.hasNext()) {
            if (reader.nextName() == "waypoints") {
                reader.beginArray()
                while (reader.hasNext()) {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        var lat: Double? = null
                        var lng: Double? = null
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "latE7" -> lat = e7ToDegrees(reader.nextLong(), 900_000_000L)
                                "lngE7" -> lng = e7ToDegrees(reader.nextLong(), 1_800_000_000L)
                                "latitude" -> lat = reader.nextDouble()
                                "longitude" -> lng = reader.nextDouble()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                            coords.add(Pair(lat, lng))
                        }
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()

        val segmentId = "activity:${startTs ?: -1L}:${endTs ?: -1L}"
        // KML-style waypoint timestamps are estimates spread over the activity duration.
        if (startTs != null && endTs != null && coords.isNotEmpty() && endTs > startTs) {
            val totalDuration = endTs - startTs
            coords.forEachIndexed { index, pair ->
                val fraction = (index + 1).toDouble() / (coords.size + 1).toDouble()
                val ts = (startTs + (totalDuration * fraction)).toLong()
                if (isWithinRange(ts, minTimestampMillis, maxTimestampMillis)) {
                    points.add(
                        TimelineLocationPoint(
                            timestampMillis = ts,
                            latitude = pair.first,
                            longitude = pair.second,
                            source = "waypoint",
                            segmentId = segmentId,
                            timestampEstimated = true
                        )
                    )
                }
            }
        }
    }

    private fun parseSimplifiedRawPath(
        reader: JsonReader,
        points: MutableList<TimelineLocationPoint>,
        minTimestampMillis: Long?,
        maxTimestampMillis: Long?
    ) {
        val segmentId = "raw_path:${points.size}"
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "points") {
                reader.beginArray()
                while (reader.hasNext()) {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        var lat: Double? = null
                        var lng: Double? = null
                        var ts: Long? = null
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "latE7" -> lat = e7ToDegrees(reader.nextLong(), 900_000_000L)
                                "lngE7" -> lng = e7ToDegrees(reader.nextLong(), 1_800_000_000L)
                                "latitude" -> lat = reader.nextDouble()
                                "longitude" -> lng = reader.nextDouble()
                                "timestamp" -> ts = parseTimestampToMillis(reader.nextString())
                                "timestampMs" -> ts = reader.nextString().toLongOrNull()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (lat != null && lng != null && ts != null && isValidCoordinate(lat, lng)) {
                            if (isWithinRange(ts, minTimestampMillis, maxTimestampMillis)) {
                                points.add(
                                    TimelineLocationPoint(
                                        timestampMillis = ts,
                                        latitude = lat,
                                        longitude = lng,
                                        source = "raw_path",
                                        segmentId = segmentId
                                    )
                                )
                            }
                        }
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun parseTimelinePath(
        reader: JsonReader,
        points: MutableList<TimelineLocationPoint>,
        startTs: Long?,
        endTs: Long?,
        minTimestampMillis: Long?,
        maxTimestampMillis: Long?
    ) {
        reader.beginArray()
        val segmentId = "activity:${startTs ?: -1L}:${endTs ?: -1L}"
        val pathCoords = mutableListOf<Pair<Double, Double>>()
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "point") {
                        val pointStr = reader.nextString()
                        val parsed = parseGeoUriString(pointStr)
                        if (parsed != null && isValidCoordinate(parsed.first, parsed.second)) {
                            pathCoords.add(parsed)
                        }
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            } else {
                reader.skipValue()
            }
        }
        reader.endArray()

        if (startTs != null && endTs != null && pathCoords.isNotEmpty() && endTs > startTs) {
            val totalDuration = endTs - startTs
            pathCoords.forEachIndexed { index, pair ->
                val fraction = (index + 1).toDouble() / (pathCoords.size + 1).toDouble()
                val ts = (startTs + (totalDuration * fraction)).toLong()
                if (isWithinRange(ts, minTimestampMillis, maxTimestampMillis)) {
                    points.add(
                        TimelineLocationPoint(
                            timestampMillis = ts,
                            latitude = pair.first,
                            longitude = pair.second,
                            source = "timeline_path",
                            segmentId = segmentId,
                            timestampEstimated = true
                        )
                    )
                }
            }
        }
    }

    private fun parseRawSignalsArray(
        reader: JsonReader,
        points: MutableList<TimelineLocationPoint>,
        minTimestampMillis: Long?,
        maxTimestampMillis: Long?,
        onProgress: ((Int) -> Unit)?
    ) {
        reader.beginArray()
        var scanned = 0
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            var timestamp: Long? = null
            var coordinate: Pair<Double, Double>? = null
            var accuracy: Float? = null
            var altitude: Double? = null

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "position" -> {
                        if (reader.peek() == JsonToken.STRING) {
                            coordinate = parseGeoUriString(reader.nextString())
                        } else if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            var lat: Double? = null
                            var lng: Double? = null
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "LatLng", "latLng", "point" -> {
                                        val parsed = parseGeoUriString(reader.nextString())
                                        lat = parsed?.first
                                        lng = parsed?.second
                                    }
                                    "latitudeE7", "latE7" -> lat = e7ToDegrees(reader.nextLong(), 900_000_000L)
                                    "longitudeE7", "lngE7" -> lng = e7ToDegrees(reader.nextLong(), 1_800_000_000L)
                                    "latitude", "lat" -> lat = reader.nextDouble()
                                    "longitude", "lng" -> lng = reader.nextDouble()
                                    "timestamp", "time" -> timestamp = parseTimestampToMillis(reader.nextString())
                                    "accuracyMeters", "accuracy" -> accuracy = reader.nextDouble().toFloat()
                                    "altitudeMeters", "altitude" -> altitude = reader.nextDouble()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                                coordinate = Pair(lat, lng)
                            }
                        } else {
                            reader.skipValue()
                        }
                    }
                    "timestamp", "time" -> timestamp = parseTimestampToMillis(reader.nextString())
                    "timestampMs" -> timestamp = reader.nextString().toLongOrNull()
                    "accuracyMeters", "accuracy" -> accuracy = reader.nextDouble().toFloat()
                    "altitudeMeters", "altitude" -> altitude = reader.nextDouble()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            if (timestamp != null && coordinate != null &&
                isValidCoordinate(coordinate.first, coordinate.second) &&
                isWithinRange(timestamp, minTimestampMillis, maxTimestampMillis)
            ) {
                points.add(
                    TimelineLocationPoint(
                        timestampMillis = timestamp,
                        latitude = coordinate.first,
                        longitude = coordinate.second,
                        accuracyMeters = accuracy,
                        altitudeMeters = altitude,
                        source = "raw_signal"
                    )
                )
            }
            scanned++
            if (scanned % 2000 == 0) onProgress?.invoke(points.size)
        }
        reader.endArray()
        onProgress?.invoke(points.size)
    }

    private fun parseGenericTimelineItem(
        reader: JsonReader,
        points: MutableList<TimelineLocationPoint>,
        minTimestampMillis: Long?,
        maxTimestampMillis: Long?
    ) {
        reader.beginObject()
        var timestamp: Long? = null
        var startTimestamp: Long? = null
        var endTimestamp: Long? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var visitCoordinate: Pair<Double, Double>? = null
        var activityStart: Pair<Double, Double>? = null
        var activityEnd: Pair<Double, Double>? = null
        val pathEntries = mutableListOf<ParsedTimelinePathPoint>()

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "timestamp", "time" -> timestamp = parseTimestampToMillis(reader.nextString())
                "timestampMs" -> timestamp = reader.nextString().toLongOrNull()
                "startTime", "startTimestamp" -> startTimestamp = parseTimestampToMillis(reader.nextString())
                "endTime", "endTimestamp" -> endTimestamp = parseTimestampToMillis(reader.nextString())
                "latitudeE7", "latE7" -> latitude = e7ToDegrees(reader.nextLong(), 900_000_000L)
                "longitudeE7", "lngE7" -> longitude = e7ToDegrees(reader.nextLong(), 1_800_000_000L)
                "latitude", "lat" -> latitude = reader.nextDouble()
                "longitude", "lng" -> longitude = reader.nextDouble()
                "point" -> {
                    val parsed = parseGeoUriString(reader.nextString())
                    if (parsed != null) {
                        latitude = parsed.first
                        longitude = parsed.second
                    }
                }
                "placeLocation", "location", "position" -> {
                    val parsed = parseLocationSubObject(reader)
                    if (parsed != null) {
                        latitude = parsed.first
                        longitude = parsed.second
                    }
                }
                "visit" -> visitCoordinate = parseModernVisit(reader)
                "activity" -> {
                    val endpoints = parseModernActivity(reader)
                    activityStart = endpoints.first
                    activityEnd = endpoints.second
                }
                "timelinePath" -> pathEntries.addAll(parseTimelinePathEntries(reader))
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val pointTimestamp = timestamp ?: startTimestamp
        val segmentId = "semantic:${startTimestamp ?: endTimestamp ?: pointTimestamp ?: points.size}"
        if (pointTimestamp != null && latitude != null && longitude != null &&
            isValidCoordinate(latitude, longitude) &&
            isWithinRange(pointTimestamp, minTimestampMillis, maxTimestampMillis)
        ) {
            points.add(
                TimelineLocationPoint(
                    timestampMillis = pointTimestamp,
                    latitude = latitude,
                    longitude = longitude,
                    source = "generic",
                    segmentId = segmentId,
                    interpolationEligible = false
                )
            )
        }

        if (startTimestamp != null && visitCoordinate != null &&
            isValidCoordinate(visitCoordinate.first, visitCoordinate.second) &&
            isWithinRange(startTimestamp, minTimestampMillis, maxTimestampMillis)
        ) {
            points.add(
                TimelineLocationPoint(
                    timestampMillis = startTimestamp,
                    latitude = visitCoordinate.first,
                    longitude = visitCoordinate.second,
                    source = "visit",
                    segmentId = segmentId,
                    interpolationEligible = false,
                    intervalEndMillis = endTimestamp
                )
            )
        }

        if (startTimestamp != null && activityStart != null &&
            isValidCoordinate(activityStart.first, activityStart.second) &&
            isWithinRange(startTimestamp, minTimestampMillis, maxTimestampMillis)
        ) {
            points.add(
                TimelineLocationPoint(
                    timestampMillis = startTimestamp,
                    latitude = activityStart.first,
                    longitude = activityStart.second,
                    source = "activity_start",
                    segmentId = segmentId,
                    interpolationEligible = false
                )
            )
        }
        if (endTimestamp != null && activityEnd != null &&
            isValidCoordinate(activityEnd.first, activityEnd.second) &&
            isWithinRange(endTimestamp, minTimestampMillis, maxTimestampMillis)
        ) {
            points.add(
                TimelineLocationPoint(
                    timestampMillis = endTimestamp,
                    latitude = activityEnd.first,
                    longitude = activityEnd.second,
                    source = "activity_end",
                    segmentId = segmentId,
                    interpolationEligible = false
                )
            )
        }

        val segmentStartTimestamp = startTimestamp
        val segmentEndTimestamp = endTimestamp
        pathEntries.forEachIndexed { index, entry ->
            val pathTimestamp = entry.timestampMillis
                ?: entry.offsetMinutes?.let { offset ->
                    segmentStartTimestamp?.plus((offset * 60_000.0).toLong())
                }
                ?: if (segmentStartTimestamp != null && segmentEndTimestamp != null &&
                    segmentEndTimestamp > segmentStartTimestamp && pathEntries.size > 1
                ) {
                    val fraction = index.toDouble() / (pathEntries.size - 1).toDouble()
                    (segmentStartTimestamp + ((segmentEndTimestamp - segmentStartTimestamp) * fraction)).toLong()
                } else {
                    segmentStartTimestamp ?: pointTimestamp
                }

            if (pathTimestamp != null && isWithinRange(pathTimestamp, minTimestampMillis, maxTimestampMillis)) {
                points.add(
                    TimelineLocationPoint(
                        timestampMillis = pathTimestamp,
                        latitude = entry.latitude,
                        longitude = entry.longitude,
                        source = "timeline_path",
                        segmentId = segmentId,
                        timestampEstimated = entry.timestampMillis == null && entry.offsetMinutes == null
                    )
                )
            }
        }
    }

    private data class ParsedTimelinePathPoint(
        val latitude: Double,
        val longitude: Double,
        val timestampMillis: Long? = null,
        val offsetMinutes: Double? = null
    )

    private fun parseModernVisit(reader: JsonReader): Pair<Double, Double>? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        reader.beginObject()
        var coordinate: Pair<Double, Double>? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "topCandidate" -> {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "placeLocation", "location" -> coordinate = parseLocationSubObject(reader)
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else reader.skipValue()
                }
                "placeLocation", "location" -> coordinate = parseLocationSubObject(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return coordinate
    }

    private fun parseModernActivity(reader: JsonReader): Pair<Pair<Double, Double>?, Pair<Double, Double>?> {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return Pair(null, null)
        }
        reader.beginObject()
        var start: Pair<Double, Double>? = null
        var end: Pair<Double, Double>? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "start", "startLocation" -> start = parseLocationSubObject(reader)
                "end", "endLocation" -> end = parseLocationSubObject(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Pair(start, end)
    }

    private fun parseTimelinePathEntries(reader: JsonReader): List<ParsedTimelinePathPoint> {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return emptyList()
        }
        val entries = mutableListOf<ParsedTimelinePathPoint>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            var coordinate: Pair<Double, Double>? = null
            var timestamp: Long? = null
            var offsetMinutes: Double? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "point" -> {
                        coordinate = if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                            parseLocationSubObject(reader)
                        } else {
                            parseGeoUriString(reader.nextString())
                        }
                    }
                    "time", "timestamp" -> timestamp = parseTimestampToMillis(reader.nextString())
                    "durationMinutesOffsetFromStartTime" -> {
                        offsetMinutes = reader.nextString().toDoubleOrNull()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (coordinate != null && isValidCoordinate(coordinate.first, coordinate.second)) {
                entries.add(
                    ParsedTimelinePathPoint(
                        latitude = coordinate.first,
                        longitude = coordinate.second,
                        timestampMillis = timestamp,
                        offsetMinutes = offsetMinutes
                    )
                )
            }
        }
        reader.endArray()
        return entries
    }

    private fun parseLocationSubObject(reader: JsonReader): Pair<Double, Double>? {
        if (reader.peek() == JsonToken.STRING) {
            val str = reader.nextString()
            return parseGeoUriString(str)
        }
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        reader.beginObject()
        var lat: Double? = null
        var lng: Double? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "latitudeE7", "latE7" -> lat = e7ToDegrees(reader.nextLong(), 900_000_000L)
                "longitudeE7", "lngE7" -> lng = e7ToDegrees(reader.nextLong(), 1_800_000_000L)
                "latitude", "lat" -> lat = reader.nextDouble()
                "longitude", "lng" -> lng = reader.nextDouble()
                "latLng", "LatLng", "point" -> {
                    val s = reader.nextString()
                    val p = parseGeoUriString(s)
                    if (p != null) {
                        lat = p.first
                        lng = p.second
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return if (lat != null && lng != null && isValidCoordinate(lat, lng)) Pair(lat, lng) else null
    }

    private fun parseDurationSubObject(reader: JsonReader): Pair<Long?, Long?> {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return Pair(null, null)
        }
        reader.beginObject()
        var start: Long? = null
        var end: Long? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "startTimestamp" -> start = parseTimestampToMillis(reader.nextString())
                "endTimestamp" -> end = parseTimestampToMillis(reader.nextString())
                "startTimestampMs" -> start = reader.nextString().toLongOrNull()
                "endTimestampMs" -> end = reader.nextString().toLongOrNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Pair(start, end)
    }

    /**
     * Parses geo:37.4219999,-122.0840575 or 37.4219999,-122.0840575
     */
    fun parseGeoUriString(raw: String): Pair<Double, Double>? {
        val clean = raw.trim()
            .removePrefix("geo:")
            .removePrefix("GEO:")
            .replace("°", "")
            .trim()
        val parts = clean.split(',', ';')
        if (parts.size >= 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lng = parts[1].trim().toDoubleOrNull()
            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                return Pair(lat, lng)
            }
        }
        return null
    }

    /**
     * Parses standard ISO-8601 strings into epoch milliseconds.
     */
    fun parseTimestampToMillis(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null

        value.toLongOrNull()?.let { numeric ->
            val magnitude = if (numeric == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(numeric)
            return when {
                magnitude < 100_000_000_000L -> numeric * 1000L // Unix seconds
                magnitude >= 100_000_000_000_000L -> numeric / 1000L // Microseconds
                else -> numeric // Unix milliseconds
            }
        }

        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                java.time.OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)
                    .toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun e7ToDegrees(value: Long, maxAxisValue: Long): Double {
        // Some Takeout exports encode signed E7 coordinates as unsigned 32-bit integers.
        val signedValue = if (value > maxAxisValue) value - 4_294_967_296L else value
        return signedValue / 10_000_000.0
    }

    private fun isWithinRange(ts: Long, min: Long?, max: Long?): Boolean {
        if (min != null && ts < min) return false
        if (max != null && ts > max) return false
        return true
    }

    fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            !(abs(lat) < 1e-6 && abs(lng) < 1e-6)
    }
}
