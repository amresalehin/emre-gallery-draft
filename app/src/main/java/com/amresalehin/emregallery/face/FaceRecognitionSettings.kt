package com.amresalehin.emregallery.face

import android.content.Context

/** Persistent tuning controls used by the Index Status screen and face indexer. */
object FaceRecognitionSettings {
    private const val PREFS = "face_recognition_settings"
    const val KEY_DETECTION_CONFIDENCE = "detection_confidence"
    const val KEY_SIMILARITY_THRESHOLD = "similarity_threshold"
    const val KEY_MIN_GROUP_FACES = "min_group_faces"

    const val DEFAULT_DETECTION_CONFIDENCE = 0.70f
    const val DEFAULT_SIMILARITY_THRESHOLD = 0.55f
    const val DEFAULT_MIN_GROUP_FACES = 7

    fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun detectionConfidence(context: Context) =
        preferences(context).getFloat(KEY_DETECTION_CONFIDENCE, DEFAULT_DETECTION_CONFIDENCE).coerceIn(0.30f, 0.90f)

    fun similarityThreshold(context: Context) =
        preferences(context).getFloat(KEY_SIMILARITY_THRESHOLD, DEFAULT_SIMILARITY_THRESHOLD).coerceIn(0.35f, 0.75f)

    fun minGroupFaces(context: Context) =
        preferences(context).getInt(KEY_MIN_GROUP_FACES, DEFAULT_MIN_GROUP_FACES).coerceIn(1, 30)
}
