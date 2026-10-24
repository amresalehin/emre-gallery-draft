package com.amresalehin.emregallery.face

import android.content.Context

/** Persistent tuning controls used by the Index Status screen and face indexer. */
object FaceRecognitionSettings {
    private const val PREFS = "face_recognition_settings"
    const val KEY_DETECTION_CONFIDENCE = "detection_confidence"
    const val KEY_RECOGNITION_DISTANCE = "recognition_distance"
    // Kept for one-time migration from older app versions.
    const val KEY_SIMILARITY_THRESHOLD = "similarity_threshold"
    const val KEY_MIN_GROUP_FACES = "min_group_faces"

    const val DEFAULT_DETECTION_CONFIDENCE = 0.70f
    const val DEFAULT_RECOGNITION_DISTANCE = 0.60f
    const val DEFAULT_MIN_GROUP_FACES = 7

    fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun detectionConfidence(context: Context) =
        preferences(context).getFloat(KEY_DETECTION_CONFIDENCE, DEFAULT_DETECTION_CONFIDENCE).coerceIn(0.30f, 0.90f)

    fun recognitionDistance(context: Context): Float {
        val prefs = preferences(context)
        if (prefs.contains(KEY_RECOGNITION_DISTANCE)) {
            return prefs.getFloat(KEY_RECOGNITION_DISTANCE, DEFAULT_RECOGNITION_DISTANCE).coerceIn(0.25f, 0.75f)
        }
        // Older versions stored a minimum cosine similarity. Convert it to cosine distance.
        val migrated = if (prefs.contains(KEY_SIMILARITY_THRESHOLD)) {
            (1f - prefs.getFloat(KEY_SIMILARITY_THRESHOLD, 0.40f)).coerceIn(0.25f, 0.75f)
        } else DEFAULT_RECOGNITION_DISTANCE
        prefs.edit().putFloat(KEY_RECOGNITION_DISTANCE, migrated).apply()
        return migrated
    }

    fun minGroupFaces(context: Context) =
        preferences(context).getInt(KEY_MIN_GROUP_FACES, DEFAULT_MIN_GROUP_FACES).coerceIn(1, 30)
}
