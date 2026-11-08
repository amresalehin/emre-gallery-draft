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


internal fun FaceClusterStore.addRawFaceObservationImpl(
    mediaUri: String,
    faceIndex: Int,
    rawEmbedding: FloatArray,
    bounds: RectF? = null,
    confidence: Float = 1.0f,
    quality: Float = 1.0f,
    provenance: EmbeddingProvenance = EnteFaceConstants.PROVENANCE,
    qualityMetrics: FaceQualityMetrics? = null
) {
    require(rawEmbedding.isNotEmpty()) { "Face embedding cannot be empty." }
    require(rawEmbedding.size == provenance.dimension) {
        "Embedding dimension mismatch: expected ${provenance.dimension}, got ${rawEmbedding.size}"
    }
    val embedding = normalize(rawEmbedding)
    val faceId = "${provenance.engineId}#${mediaUri}#${faceIndex}"
    val db = writableDatabase

    db.beginTransaction()
    try {
        var existingClusterId: String? = null
        var existingPersonId: String? = null
        // Check if there was an existing face record with the same mediaUri and faceIndex
        db.rawQuery(
            "SELECT cluster_id, person_id FROM faces WHERE media_uri=? AND face_index=? AND engine_id=?",
            arrayOf(mediaUri, faceIndex.toString(), provenance.engineId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                existingClusterId = if (cursor.isNull(0)) null else cursor.getString(0)
                existingPersonId = if (cursor.isNull(1)) null else cursor.getString(1)
            }
        }

        // If not found at same index, check if an existing face in this photo overlaps spatially (IoU >= 0.40)
        if (existingPersonId == null && bounds != null) {
            db.rawQuery(
                "SELECT cluster_id, person_id, box_left, box_top, box_right, box_bottom FROM faces WHERE media_uri=? AND engine_id=? AND person_id IS NOT NULL",
                arrayOf(mediaUri, provenance.engineId)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val prevBounds = RectF(cursor.getFloat(2), cursor.getFloat(3), cursor.getFloat(4), cursor.getFloat(5))
                    if (calculateIoU(bounds, prevBounds) >= 0.40f) {
                        existingClusterId = cursor.getString(0)
                        existingPersonId = cursor.getString(1)
                        break
                    }
                }
            }
        }

        val faceValues = ContentValues().apply {
            put("media_uri", mediaUri)
            put("face_index", faceIndex)
            put("engine_id", provenance.engineId)
            put("face_id", faceId)
            if (existingClusterId != null) put("cluster_id", existingClusterId) else putNull("cluster_id")
            if (existingPersonId != null) put("person_id", existingPersonId) else putNull("person_id")
            put("embedding", floatsToBytes(embedding))
            put("model_version", provenance.modelVersion)
            put("embedding_dim", provenance.dimension)
            put("format_version", provenance.formatVersion)
            put("box_left", bounds?.left ?: 0f)
            put("box_top", bounds?.top ?: 0f)
            put("box_right", bounds?.right ?: 0f)
            put("box_bottom", bounds?.bottom ?: 0f)
            put("detection_confidence", confidence)
            put("quality_score", quality)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("faces", null, faceValues, SQLiteDatabase.CONFLICT_REPLACE)

        val metrics = qualityMetrics ?: FaceQualityMetrics(
            blurScore = quality,
            poseScore = quality,
            sizeRatio = if (bounds != null) (bounds.width() * bounds.height()) / 1_000_000f else 0.05f,
            aspectRatio = if (bounds != null && bounds.height() > 0) bounds.width() / bounds.height() else 0.85f,
            overallQuality = quality
        )
        saveFaceMetrics(db, mediaUri, faceIndex, provenance.engineId, metrics)
        val groupingPending = ContentValues().apply {
            put("engine_id", provenance.engineId)
            put("model_version", provenance.modelVersion)
            put("strategy_version", -1)
            put("status", "pending")
            put("grouped_people", 0)
            put("face_count", -1)
            putNull("error_message")
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("grouping_state", null, groupingPending, SQLiteDatabase.CONFLICT_REPLACE)

        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
internal fun FaceClusterStore.saveFaceMetricsImpl(
    mediaUri: String,
    faceIndex: Int,
    engineId: String,
    metrics: FaceQualityMetrics
) {
    saveFaceMetricsImpl(writableDatabase, mediaUri, faceIndex, engineId, metrics)
}

internal fun FaceClusterStore.saveFaceMetricsImpl(
    db: SQLiteDatabase,
    mediaUri: String,
    faceIndex: Int,
    engineId: String,
    metrics: FaceQualityMetrics
) {
    val vals = ContentValues().apply {
        put("media_uri", mediaUri)
        put("face_index", faceIndex)
        put("engine_id", engineId)
        put("blur_score", metrics.blurScore)
        put("pose_score", metrics.poseScore)
        put("size_ratio", metrics.sizeRatio)
        put("aspect_ratio", metrics.aspectRatio)
        put("overall_quality", metrics.overallQuality)
        put("version", metrics.version)
        put("created_at", System.currentTimeMillis())
    }
    db.insertWithOnConflict("face_metrics_v2", null, vals, SQLiteDatabase.CONFLICT_REPLACE)
}
internal fun FaceClusterStore.getFaceMetricsImpl(
    mediaUri: String,
    faceIndex: Int,
    engineId: String = BuffaloLFaceEngine.ENGINE_ID
): FaceQualityMetrics? {
    return readableDatabase.rawQuery(
        """SELECT blur_score, pose_score, size_ratio, aspect_ratio, overall_quality, version
           FROM face_metrics_v2
           WHERE media_uri=? AND face_index=? AND engine_id=? LIMIT 1""",
        arrayOf(mediaUri, faceIndex.toString(), engineId)
    ).use { cursor ->
        if (cursor.moveToFirst()) {
            FaceQualityMetrics(
                blurScore = cursor.getFloat(0),
                poseScore = cursor.getFloat(1),
                sizeRatio = cursor.getFloat(2),
                aspectRatio = cursor.getFloat(3),
                overallQuality = cursor.getFloat(4),
                version = cursor.getInt(5)
            )
        } else null
    }
}
internal fun FaceClusterStore.unassignedFaceCountImpl(engineId: String, modelVersion: String): Int =
    readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM faces WHERE engine_id=? AND model_version=? AND person_id IS NULL",
        arrayOf(engineId, modelVersion)
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
internal fun FaceClusterStore.faceEmbeddingCountImpl(engineId: String, modelVersion: String): Int =
    readableDatabase.rawQuery("SELECT COUNT(*) FROM faces WHERE engine_id=? AND model_version=?", arrayOf(engineId, modelVersion))
        .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
internal fun FaceClusterStore.isGroupingRequiredImpl(engineId: String, modelVersion: String, strategyVersion: Int): Boolean {
    val count = faceEmbeddingCount(engineId, modelVersion)
    if (count == 0) return false
    return readableDatabase.rawQuery(
        "SELECT status, strategy_version, face_count FROM grouping_state WHERE engine_id=? AND model_version=? LIMIT 1",
        arrayOf(engineId, modelVersion)
    ).use { cursor ->
        !cursor.moveToFirst() || cursor.getString(0) != "completed" ||
            cursor.getInt(1) != strategyVersion || cursor.getInt(2) != count
    }
}
internal fun FaceClusterStore.groupingFailureImpl(engineId: String, modelVersion: String, strategyVersion: Int): String? =
    readableDatabase.rawQuery(
        "SELECT error_message FROM grouping_state WHERE engine_id=? AND model_version=? AND strategy_version=? AND status='failed' LIMIT 1",
        arrayOf(engineId, modelVersion, strategyVersion.toString())
    ).use { cursor ->
        if (!cursor.moveToFirst()) null
        else cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "Grouping failed"
    }
internal fun FaceClusterStore.markGroupingPendingImpl(engineId: String, modelVersion: String, strategyVersion: Int,
    faceCount: Int = faceEmbeddingCount(engineId, modelVersion)
) = saveGroupingStateImpl(engineId, modelVersion, strategyVersion, "pending", 0, faceCount, null)
internal fun FaceClusterStore.markGroupingCompletedImpl(engineId: String, modelVersion: String, strategyVersion: Int, groupedPeople: Int,
    faceCount: Int = faceEmbeddingCount(engineId, modelVersion)
) = saveGroupingStateImpl(engineId, modelVersion, strategyVersion, "completed", groupedPeople, faceCount, null)
internal fun FaceClusterStore.markGroupingFailedImpl(engineId: String, modelVersion: String, strategyVersion: Int, errorMessage: String,
    faceCount: Int = faceEmbeddingCount(engineId, modelVersion)
) = saveGroupingStateImpl(engineId, modelVersion, strategyVersion, "failed", 0, faceCount, errorMessage)

internal fun FaceClusterStore.saveGroupingStateImpl(engineId: String, modelVersion: String, strategyVersion: Int, status: String,
    groupedPeople: Int, faceCount: Int, errorMessage: String?
) {
    val values = ContentValues().apply {
        put("engine_id", engineId); put("model_version", modelVersion)
        put("strategy_version", strategyVersion); put("status", status)
        put("grouped_people", groupedPeople); put("face_count", faceCount)
        if (errorMessage == null) putNull("error_message") else put("error_message", errorMessage)
        put("updated_at", System.currentTimeMillis())
    }
    writableDatabase.insertWithOnConflict("grouping_state", null, values, SQLiteDatabase.CONFLICT_REPLACE)
}

/** Remove only empty unnamed people without a cluster, face, or manual-feedback reference. */
internal fun FaceClusterStore.pruneEmptyAutoPeopleImpl(db: SQLiteDatabase, engineId: String): Int =
    db.delete("people",
        """engine_id=? AND COALESCE(TRIM(name), '')='' AND is_hidden=0
           AND NOT EXISTS (SELECT 1 FROM faces f WHERE f.engine_id=people.engine_id AND f.person_id=people.id)
           AND NOT EXISTS (SELECT 1 FROM clusters c WHERE c.engine_id=people.engine_id AND c.person_id=people.id)
           AND NOT EXISTS (SELECT 1 FROM face_feedback fb WHERE fb.engine_id=people.engine_id
               AND (fb.source_id=people.id OR fb.target_id=people.id))""".trimIndent(),
        arrayOf(engineId))

/**
 * Keep a single representative face for each person. Explicit cover selection sets
 * cover_face_id directly; otherwise choose the highest-quality observation deterministically.
