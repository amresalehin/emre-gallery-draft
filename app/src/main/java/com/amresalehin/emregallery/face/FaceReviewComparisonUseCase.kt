package com.amresalehin.emregallery.face

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.RectF
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow


internal fun FaceClusterStore.compareEnginesOnMediaImpl(
    mediaUri: String,
    engineA: String = BuffaloLFaceEngine.ENGINE_ID,
    engineB: String = EnteFaceConstants.ENGINE_ID,
    iouThreshold: Float = 0.40f,
    context: Context? = null
): EngineComparisonResult {
    fun loadFaces(eng: String): List<EngineComparisonFace> {
        val list = mutableListOf<EngineComparisonFace>()
        readableDatabase.rawQuery(
            """SELECT face_index, box_left, box_top, box_right, box_bottom, detection_confidence, quality_score
               FROM faces WHERE media_uri=? AND engine_id=? ORDER BY face_index""",
            arrayOf(mediaUri, eng)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    EngineComparisonFace(
                        faceIndex = cursor.getInt(0),
                        bounds = RectF(cursor.getFloat(1), cursor.getFloat(2), cursor.getFloat(3), cursor.getFloat(4)),
                        confidence = cursor.getFloat(5),
                        quality = cursor.getFloat(6)
                    )
                )
            }
        }
        return list
    }

    var facesA = loadFaces(engineA)
    var facesB = loadFaces(engineB)

    // If stored detections are missing for one or both engines, and context is available, run on-demand inference
    if ((facesA.isEmpty() || facesB.isEmpty()) && context != null && mediaUri.isNotBlank()) {
        val factoryA = FaceEngineRegistry.getFactory(engineA)
        val factoryB = FaceEngineRegistry.getFactory(engineB)
        if (factoryA?.isInstalled(context) == true && factoryB?.isInstalled(context) == true) {
            runCatching {
                val uri = Uri.parse(mediaUri)
                if (facesA.isEmpty()) {
                    factoryA.create(context, FaceRecognitionSettings.detectionConfidence(context, engineA)).use { eng ->
                        facesA = eng.embed(uri).map {
                            EngineComparisonFace(it.faceIndex, it.bounds, it.score, it.quality)
                        }
                    }
                }
                if (facesB.isEmpty()) {
                    factoryB.create(context, FaceRecognitionSettings.detectionConfidence(context, engineB)).use { eng ->
                        facesB = eng.embed(uri).map {
                            EngineComparisonFace(it.faceIndex, it.bounds, it.score, it.quality)
                        }
                    }
                }
            }
        }
    }

    // Calculate overlap count using IoU >= iouThreshold
    var matches = 0
    val matchedB = mutableSetOf<Int>()
    for (a in facesA) {
        for (b in facesB) {
            if (b.faceIndex in matchedB) continue
            if (calculateIoUImpl(a.bounds, b.bounds) >= iouThreshold) {
                matches++
                matchedB.add(b.faceIndex)
                break
            }
        }
    }

    return EngineComparisonResult(
        mediaUri = mediaUri,
        engineA = engineA,
        facesA = facesA,
        engineB = engineB,
        facesB = facesB,
        matchingFaceCount = matches
    )
}

internal fun FaceClusterStore.calculateIoUImpl(a: RectF, b: RectF): Float {
    val interLeft = maxOf(a.left, b.left)
    val interTop = maxOf(a.top, b.top)
    val interRight = minOf(a.right, b.right)
    val interBottom = minOf(a.bottom, b.bottom)
    if (interRight <= interLeft || interBottom <= interTop) return 0f
    val interArea = (interRight - interLeft) * (interBottom - interTop)
    val areaA = (a.right - a.left) * (a.bottom - a.top)
    val areaB = (b.right - b.left) * (b.bottom - b.top)
    val unionArea = areaA + areaB - interArea
    return if (unionArea > 0f) interArea / unionArea else 0f
}

// --- Review Suggestions ---
internal fun FaceClusterStore.getPendingSuggestionsImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID): List<ReviewSuggestion> {
    val result = mutableListOf<ReviewSuggestion>()
    readableDatabase.rawQuery(
        """SELECT s.id, s.face_id, s.media_uri, s.face_index, s.suggested_person_id,
                  COALESCE(p.name, 'Person'), s.distance, s.engine_id
           FROM review_suggestions s
           JOIN people p ON s.suggested_person_id = p.id
           WHERE s.status = 'pending' AND s.engine_id = ?
           ORDER BY s.distance ASC LIMIT 50""".trimIndent(),
        arrayOf(engineId)
    ).use { cursor ->
        while (cursor.moveToNext()) {
            result += ReviewSuggestion(
                id = cursor.getString(0),
                faceId = cursor.getString(1),
                mediaUri = cursor.getString(2),
                faceIndex = cursor.getInt(3),
                suggestedPersonId = cursor.getString(4),
                suggestedPersonName = cursor.getString(5),
                distance = cursor.getFloat(6),
                engineId = cursor.getString(7)
            )
        }
    }
    return result
}
internal fun FaceClusterStore.acceptSuggestionImpl(suggestionId: String): Boolean {
    val db = writableDatabase
    db.beginTransaction()
    try {
        var faceId: String? = null
        var suggestedPersonId: String? = null
        var engineId = BuffaloLFaceEngine.ENGINE_ID

        db.rawQuery(
            "SELECT face_id, suggested_person_id, engine_id FROM review_suggestions WHERE id=?",
            arrayOf(suggestionId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                faceId = cursor.getString(0)
                suggestedPersonId = cursor.getString(1)
                engineId = cursor.getString(2)
            }
        }

        val fId = faceId
        val pId = suggestedPersonId
        if (fId == null || pId == null) return false

        val parts = fId.split("#")
        // parts: engineId#mediaUri#faceIndex or mediaUri#faceIndex
        val uri = if (parts.size >= 3) parts[1] else parts[0]
        val index = if (parts.size >= 3) parts[2].toIntOrNull() ?: 0 else parts.getOrNull(1)?.toIntOrNull() ?: 0

        val vals = ContentValues().apply { put("person_id", pId) }
        db.update("faces", vals, "media_uri=? AND face_index=? AND engine_id=?", arrayOf(uri, index.toString(), engineId))
        recordFeedback(db, "ASSIGN_FACE_TO_PERSON", fId, pId, engineId = engineId)

        val statusVal = ContentValues().apply { put("status", "accepted") }
        db.update("review_suggestions", statusVal, "id=?", arrayOf(suggestionId))

        db.setTransactionSuccessful()
        return true
    } finally {
        db.endTransaction()
        FaceStoreEvents.notifyAllUpdated()
    }
}
internal fun FaceClusterStore.rejectSuggestionImpl(suggestionId: String): Boolean {
    val db = writableDatabase
    db.beginTransaction()
    try {
        var faceId: String? = null
        var suggestedPersonId: String? = null
        var engineId = BuffaloLFaceEngine.ENGINE_ID

        db.rawQuery(
            "SELECT face_id, suggested_person_id, engine_id FROM review_suggestions WHERE id=?",
            arrayOf(suggestionId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                faceId = cursor.getString(0)
                suggestedPersonId = cursor.getString(1)
                engineId = cursor.getString(2)
            }
        }

        val fId = faceId
        val pId = suggestedPersonId
        if (fId != null && pId != null) {
            recordFeedback(db, "REJECTED_MATCH", fId, pId, engineId = engineId)
        }

        val statusVal = ContentValues().apply { put("status", "rejected") }
        db.update("review_suggestions", statusVal, "id=?", arrayOf(suggestionId))

        db.setTransactionSuccessful()
        return true
    } finally {
        db.endTransaction()
        FaceStoreEvents.notifyAllUpdated()
    }
}

