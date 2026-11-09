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
 * 3. Modern on-device Timeline exports (Timeline.json with rawSignals, waypoints, or geo: coordinates)
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
                latE7 != null -> latE7 / 1e7
                latDirect != null -> latDirect
                else -> null
            }
            val finalLng = when {
                lngE7 != null -> lngE7 / 1e7
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
                    lat = reader.nextLong() / 1e7
                }
                "centerLngE7" -> {
                    lng = reader.nextLong() / 1e7
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
                        source = "place_visit"
                    )
                )
                // If there's an end time separated from start time, add the end point as well
                if (endTs != null && endTs > ts && isWithinRange(endTs, minTimestampMillis, maxTimestampMillis)) {
                    points.add(
                        TimelineLocationPoint(
                            timestampMillis = endTs,
                            latitude = lat,
                            longitude = lng,
                            source = "place_visit_end"
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
                                "latE7" -> lat = reader.nextLong() / 1e7
                                "lngE7" -> lng = reader.nextLong() / 1e7
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

        // Distribute timestamps linearly between startTs and endTs if known
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
                            source = "waypoint"
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
                                "latE7" -> lat = reader.nextLong() / 1e7
                                "lngE7" -> lng = reader.nextLong() / 1e7
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
                                        source = "raw_path"
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
                            source = "timeline_path"
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
            var ts: Long? = null
            var coord: Pair<Double, Double>? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "position" -> {
                        coord = parseLocationSubObject(reader)
                    }
                    "timestamp" -> {
                        ts = parseTimestampToMillis(reader.nextString())
                    }
                    "timestampMs" -> {
                        ts = reader.nextString().toLongOrNull()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            if (ts != null && coord != null && isValidCoordinate(coord.first, coord.second)) {
                if (isWithinRange(ts, minTimestampMillis, maxTimestampMillis)) {
                    points.add(
                        TimelineLocationPoint(
                            timestampMillis = ts,
                            latitude = coord.first,
                            longitude = coord.second,
                            source = "raw_signal"
                        )
                    )
                }
            }
            scanned++
            if (scanned % 2000 == 0) {
                onProgress?.invoke(points.size)
            }
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
        var ts: Long? = null
        var lat: Double? = null
        var lng: Double? = null

        while (reader.hasNext()) {
            when (val key = reader.nextName()) {
                "timestamp", "time", "startTime" -> {
                    ts = parseTimestampToMillis(reader.nextString())
                }
                "timestampMs" -> {
                    ts = reader.nextString().toLongOrNull()
                }
                "latitudeE7", "latE7" -> {
                    lat = reader.nextLong() / 1e7
                }
                "longitudeE7", "lngE7" -> {
                    lng = reader.nextLong() / 1e7
                }
                "latitude", "lat" -> {
                    lat = reader.nextDouble()
                }
                "longitude", "lng" -> {
                    lng = reader.nextDouble()
                }
                "point", "placeLocation" -> {
                    val p = parseGeoUriString(reader.nextString())
                    if (p != null) {
                        lat = p.first
                        lng = p.second
                    }
                }
                "location", "position" -> {
                    val p = parseLocationSubObject(reader)
                    if (p != null) {
                        lat = p.first
                        lng = p.second
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (ts != null && lat != null && lng != null && isValidCoordinate(lat, lng)) {
            if (isWithinRange(ts, minTimestampMillis, maxTimestampMillis)) {
                points.add(
                    TimelineLocationPoint(
                        timestampMillis = ts,
                        latitude = lat,
                        longitude = lng,
                        source = "generic"
                    )
                )
            }
        }
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
                "latitudeE7", "latE7" -> lat = reader.nextLong() / 1e7
                "longitudeE7", "lngE7" -> lng = reader.nextLong() / 1e7
                "latitude", "lat" -> lat = reader.nextDouble()
                "longitude", "lng" -> lng = reader.nextDouble()
                "point", "LatLng" -> {
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
        val clean = raw.trim().removePrefix("geo:").removePrefix("GEO:")
        val parts = clean.split(',', ';', ' ')
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
        val str = raw.trim()
        // If numeric timestamp
        str.toLongOrNull()?.let { return it }

        return try {
            Instant.parse(str).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                java.time.OffsetDateTime.parse(str, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
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
