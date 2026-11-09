package com.amresalehin.emregallery.geotag

import android.content.Context

/** Shared persisted matching and EXIF-write preferences for all Geo Timeline entry points. */
object TimelineGeotagPreferences {
    private const val PREFS = "timeline_geotag_preferences"
    private const val KEY_CLOCK = "camera_clock_offset_seconds"
    private const val KEY_DELTA = "max_time_delta_seconds"
    private const val KEY_INTERPOLATE = "interpolate_between_points"
    private const val KEY_GAP = "max_interpolation_gap_seconds"
    private const val KEY_SKIP = "skip_already_geotagged"
    private const val KEY_WRITE = "write_to_exif"

    fun read(context: Context): GeotagConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return GeotagConfig(
            cameraClockOffsetSeconds = prefs.getLong(KEY_CLOCK, 0L),
            maxTimeDeltaSeconds = prefs.getLong(KEY_DELTA, 900L),
            interpolateBetweenPoints = prefs.getBoolean(KEY_INTERPOLATE, true),
            maxInterpolationGapSeconds = prefs.getLong(KEY_GAP, 1800L).coerceIn(60L, 3600L),
            skipAlreadyGeotagged = prefs.getBoolean(KEY_SKIP, true),
            writeToExif = prefs.getBoolean(KEY_WRITE, false)
        )
    }

    fun write(context: Context, config: GeotagConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_CLOCK, config.cameraClockOffsetSeconds)
            .putLong(KEY_DELTA, config.maxTimeDeltaSeconds)
            .putBoolean(KEY_INTERPOLATE, config.interpolateBetweenPoints)
            .putLong(KEY_GAP, config.maxInterpolationGapSeconds.coerceIn(60L, 3600L))
            .putBoolean(KEY_SKIP, config.skipAlreadyGeotagged)
            .putBoolean(KEY_WRITE, config.writeToExif)
            .apply()
    }
}
