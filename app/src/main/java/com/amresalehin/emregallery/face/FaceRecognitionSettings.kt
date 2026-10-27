package com.amresalehin.emregallery.face

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persistent tuning controls used by the Index Status screen and face indexer. */
object FaceRecognitionSettings {
    private const val PREFS = "face_recognition_settings"
    private const val MANUAL_CLUSTERING_STRATEGY_VERSION = 1
    const val KEY_DETECTION_CONFIDENCE = "detection_confidence"
    const val KEY_RECOGNITION_DISTANCE = "recognition_distance"
    // Kept for one-time migration from older app versions.
    const val KEY_SIMILARITY_THRESHOLD = "similarity_threshold"
    const val KEY_MIN_GROUP_FACES = "min_group_faces"

    const val DEFAULT_DETECTION_CONFIDENCE = 0.70f
    const val DEFAULT_RECOGNITION_DISTANCE = 0.60f
    const val DEFAULT_MIN_GROUP_FACES = 2

    const val COMPARISON_IOU_THRESHOLD = 0.40f
    const val LOW_DETECTION_CONFIDENCE_THRESHOLD = 0.35f

    const val KEY_ACTIVE_ENGINE = "active_face_engine"
    private const val KEY_ACTIVE_ENGINE_SELECTION_INITIALIZED = "active_face_engine_selection_initialized"
    const val KEY_ENTE_DETECTION_CONFIDENCE = "ente_detection_confidence"
    const val KEY_ENTE_RECOGNITION_DISTANCE = "ente_recognition_distance"
    const val KEY_ENTE_MIN_GROUP_FACES = "ente_min_group_faces"

    private val activeEngineIdState = MutableStateFlow("")

    /** Observable engine selection shared by the indexing card and viewer drawer. */
    fun activeEngineFlow(context: Context): StateFlow<String> {
        activeEngineIdState.value = getActiveEngine(context)
        return activeEngineIdState.asStateFlow()
    }

    fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun getActiveEngine(context: Context): String {
        val prefs = preferences(context)
        val selected = prefs.getString(KEY_ACTIVE_ENGINE, null)
        val engineId = selected?.takeIf {
            it == BuffaloLFaceEngine.ENGINE_ID || it == EnteFaceConstants.ENGINE_ID
        } ?: if (!prefs.getBoolean(KEY_ACTIVE_ENGINE_SELECTION_INITIALIZED, false)) {
            // Upgrade old installs smoothly when exactly one engine is already installed.
            // On a fresh install (or when both engines exist), require an explicit choice.
            val buffaloInstalled = BuffaloLFaceEngine.isModelInstalled(context)
            val enteInstalled = EnteFaceModelManager.isInstalled(context)
            val inferred = when {
                buffaloInstalled && !enteInstalled -> BuffaloLFaceEngine.ENGINE_ID
                enteInstalled && !buffaloInstalled -> EnteFaceConstants.ENGINE_ID
                else -> ""
            }
            prefs.edit().putBoolean(KEY_ACTIVE_ENGINE_SELECTION_INITIALIZED, true).apply()
            if (inferred.isNotBlank()) prefs.edit().putString(KEY_ACTIVE_ENGINE, inferred).apply()
            inferred
        } else {
            ""
        }
        activeEngineIdState.value = engineId
        return engineId
    }

    fun setActiveEngine(context: Context, engineId: String) {
        require(engineId == BuffaloLFaceEngine.ENGINE_ID || engineId == EnteFaceConstants.ENGINE_ID) {
            "Unsupported face engine: $engineId"
        }
        preferences(context).edit()
            .putString(KEY_ACTIVE_ENGINE, engineId)
            .putBoolean(KEY_ACTIVE_ENGINE_SELECTION_INITIALIZED, true)
            .apply()
        activeEngineIdState.value = engineId
        FaceEngineRegistry.setActiveEngine(engineId)
    }

    fun detectionConfidence(context: Context, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Float {
        val prefs = preferences(context)
        return if (engineId == EnteFaceConstants.ENGINE_ID) {
            prefs.getFloat(KEY_ENTE_DETECTION_CONFIDENCE, EnteFaceConstants.DEFAULT_DETECTION_CONFIDENCE)
                .coerceIn(0.30f, 0.90f)
        } else {
            prefs.getFloat(KEY_DETECTION_CONFIDENCE, DEFAULT_DETECTION_CONFIDENCE).coerceIn(0.30f, 0.90f)
        }
    }

    fun setDetectionConfidence(context: Context, engineId: String, value: Float) {
        val rounded = (value.coerceIn(0.30f, 0.90f) * 100f).let { kotlin.math.round(it) / 100f }
        val key = if (engineId == EnteFaceConstants.ENGINE_ID) KEY_ENTE_DETECTION_CONFIDENCE else KEY_DETECTION_CONFIDENCE
        preferences(context).edit().putFloat(key, rounded).apply()
    }

    fun recognitionDistance(context: Context, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Float {
        val prefs = preferences(context)
        if (engineId == EnteFaceConstants.ENGINE_ID) {
            return prefs.getFloat(KEY_ENTE_RECOGNITION_DISTANCE, EnteFaceConstants.DEFAULT_RECOGNITION_DISTANCE)
                .coerceIn(0.25f, 0.75f)
        }
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

    fun setRecognitionDistance(context: Context, engineId: String, value: Float) {
        val rounded = (value.coerceIn(0.25f, 0.75f) * 100f).let { kotlin.math.round(it) / 100f }
        if (recognitionDistance(context, engineId) == rounded) return
        val key = if (engineId == EnteFaceConstants.ENGINE_ID) KEY_ENTE_RECOGNITION_DISTANCE else KEY_RECOGNITION_DISTANCE
        preferences(context).edit().putFloat(key, rounded).apply()

        // Invalidate both the manual-clustering checkpoint and the repository's
        // threshold-aware checkpoint. The next grouping run must not reuse old clusters.
        val store = FaceClusterStore(context.applicationContext)
        val modelVersion = if (engineId == EnteFaceConstants.ENGINE_ID) {
            EnteFaceConstants.MODEL_VERSION
        } else {
            BuffaloLFaceEngine.MODEL_VERSION
        }
        store.markGroupingPending(engineId, modelVersion, MANUAL_CLUSTERING_STRATEGY_VERSION)
        val strategyVersion = if (engineId == EnteFaceConstants.ENGINE_ID) {
            EnteFaceClusteringStrategy.strategyVersion
        } else {
            MANUAL_CLUSTERING_STRATEGY_VERSION
        }
        store.markGroupingPending(
            engineId,
            modelVersion,
            groupingRevision(strategyVersion, rounded)
        )
        FaceStoreEvents.notifyAllUpdated()
    }

    fun minGroupFaces(context: Context, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int {
        val prefs = preferences(context)
        return if (engineId == EnteFaceConstants.ENGINE_ID) {
            prefs.getInt(KEY_ENTE_MIN_GROUP_FACES, DEFAULT_MIN_GROUP_FACES).coerceIn(1, 30)
        } else {
            prefs.getInt(KEY_MIN_GROUP_FACES, DEFAULT_MIN_GROUP_FACES).coerceIn(1, 30)
        }
    }

    fun setMinGroupFaces(context: Context, engineId: String, value: Int) {
        val coerced = value.coerceIn(1, 30)
        val key = if (engineId == EnteFaceConstants.ENGINE_ID) KEY_ENTE_MIN_GROUP_FACES else KEY_MIN_GROUP_FACES
        preferences(context).edit().putInt(key, coerced).apply()
    }
}
