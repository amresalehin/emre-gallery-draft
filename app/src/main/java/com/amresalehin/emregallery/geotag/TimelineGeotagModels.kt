package com.amresalehin.emregallery.geotag

/** A timestamped Timeline observation with its originating segment retained. */
data class TimelineLocationPoint(
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val source: String = "timeline",
    val segmentId: String? = null,
    val timestampEstimated: Boolean = false,
    val interpolationEligible: Boolean = true,
    val intervalEndMillis: Long? = null
)

data class ImageGeotagCandidate(
    val uriString: String,
    val displayName: String,
    val filePath: String? = null,
    /** Resolved capture time; retained as this property name for call-site compatibility. */
    val dateTimeOriginalMillis: Long? = null,
    val hasExistingGeotag: Boolean = false,
    val existingLatitude: Double? = null,
    val existingLongitude: Double? = null,
    val isVideo: Boolean = false,
    val timezoneOffsetString: String? = null,
    val resolvedTimestampSource: String? = null,
    val timestampEstimated: Boolean = false
)

enum class GeotagStatus { ALREADY_GEOTAGGED, MATCHED, UNMATCHED, NO_TIMESTAMP, ERROR }

/** Provenance stored alongside app-cached Timeline-derived coordinates. */
data class TimelineMatchProvenance(
    val source: String = "Google Maps Timeline",
    val matchMethod: String,
    val timestampSource: String? = null,
    val captureTimestampMillis: Long? = null,
    val beforeTimestampMillis: Long? = null,
    val afterTimestampMillis: Long? = null,
    val sampleGapSeconds: Long? = null,
    val timestampEstimated: Boolean = false,
    val exifWritten: Boolean = false
) {
    val methodLabel: String
        get() = when (matchMethod) {
            "EXACT" -> "Exact timestamp"
            "VISIT" -> "Stationary visit"
            "PATH_INTERPOLATION" -> "Path interpolation"
            "INTERPOLATION" -> "Point interpolation"
            "NEAREST" -> "Nearest observation"
            else -> matchMethod.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }
    val shortLabel: String get() = "$source · $methodLabel"
}

data class GeotagResultItem(
    val candidate: ImageGeotagCandidate,
    val status: GeotagStatus,
    val matchedPoint: TimelineLocationPoint? = null,
    val timeDifferenceSeconds: Long? = null,
    val isInterpolated: Boolean = false,
    val errorMessage: String? = null,
    val matchMethod: String? = null,
    val beforePoint: TimelineLocationPoint? = null,
    val afterPoint: TimelineLocationPoint? = null,
    val sampleGapSeconds: Long? = null
) {
    fun toProvenance(exifWritten: Boolean = false): TimelineMatchProvenance =
        TimelineMatchProvenance(
            matchMethod = matchMethod ?: if (isInterpolated) "INTERPOLATION" else "NEAREST",
            timestampSource = candidate.resolvedTimestampSource,
            captureTimestampMillis = candidate.dateTimeOriginalMillis,
            beforeTimestampMillis = beforePoint?.timestampMillis,
            afterTimestampMillis = afterPoint?.timestampMillis,
            sampleGapSeconds = sampleGapSeconds,
            timestampEstimated = candidate.timestampEstimated ||
                beforePoint?.timestampEstimated == true || afterPoint?.timestampEstimated == true,
            exifWritten = exifWritten
        )
}

data class TimelineParseSummary(
    val fileName: String,
    val totalPoints: Int,
    val earliestTimestampMillis: Long? = null,
    val latestTimestampMillis: Long? = null,
    val formatDetected: String = "Google Takeout"
)

data class GeotagJobProgress(
    val totalImages: Int = 0,
    val processedCount: Int = 0,
    val skippedAlreadyTagged: Int = 0,
    val successfullyTagged: Int = 0,
    val unmatchedCount: Int = 0,
    val noTimestampCount: Int = 0,
    val errorCount: Int = 0,
    val currentImageName: String? = null,
    val isCompleted: Boolean = false,
    val isRunning: Boolean = false
)

data class GeotagConfig(
    val cameraClockOffsetSeconds: Long = 0L,
    val maxTimeDeltaSeconds: Long = 900L,
    val interpolateBetweenPoints: Boolean = true,
    /** Maximum gap between samples in a single path segment; default 30 minutes. */
    val maxInterpolationGapSeconds: Long = 1800L,
    val skipAlreadyGeotagged: Boolean = true,
    /** Embedded media metadata is only changed when explicitly enabled. */
    val writeToExif: Boolean = false
)
