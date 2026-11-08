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


// --- Face Ingestion & Clustering ---
internal fun FaceClusterStore.addFaceImpl(
    mediaUri: String,
    faceIndex: Int,
    rawEmbedding: FloatArray,
    maxDistance: Float = 0.60f,
    bounds: RectF? = null,
    confidence: Float = 1.0f,
    quality: Float = 1.0f,
    provenance: EmbeddingProvenance = EmbeddingProvenance.BUFFALO_L,
    qualityMetrics: FaceQualityMetrics? = null
): String {
    require(rawEmbedding.isNotEmpty()) { "Face embedding cannot be empty." }
    require(rawEmbedding.size == provenance.dimension) {
        "Embedding dimension mismatch: expected ${provenance.dimension}, got ${rawEmbedding.size}"
    }
    val embedding = normalize(rawEmbedding)
    val faceId = "${provenance.engineId}#${mediaUri}#${faceIndex}"
    val db = writableDatabase

    // 1. Check if observation already processed for this engine with current model version
    var priorClusterId: String? = null
    var priorPersonId: String? = null
    val existingRow = db.rawQuery(
        "SELECT cluster_id, person_id, model_version FROM faces WHERE media_uri=? AND face_index=? AND engine_id=?",
        arrayOf(mediaUri, faceIndex.toString(), provenance.engineId)
    ).use { cursor ->
        if (cursor.moveToFirst()) {
            val cId = cursor.getString(0)
            val pId = if (cursor.isNull(1)) null else cursor.getString(1)
            val mVer = cursor.getString(2)
            Triple(cId, pId, mVer)
        } else null
    }
    if (existingRow != null) {
        priorClusterId = existingRow.first
        priorPersonId = existingRow.second
        if (existingRow.third == provenance.modelVersion) {
            return existingRow.first
        }
    }

    // 2. Check user feedback exclusions for this face in this engine
    val excludedPersonIds = mutableSetOf<String>()
    db.rawQuery(
        """SELECT target_id FROM face_feedback
           WHERE source_id=? AND engine_id=? AND feedback_type IN ('REJECTED_MATCH', 'REMOVE_FACE_FROM_PERSON')""",
        arrayOf(faceId, provenance.engineId)
    ).use { cursor ->
        while (cursor.moveToNext()) {
            excludedPersonIds.add(cursor.getString(0))
        }
    }

    // 3. Search best candidate among compatible clusters for this engine only
    var bestClusterId: String? = null
    var bestPersonId: String? = null
    var bestDistance = Float.POSITIVE_INFINITY
    var secondBestDistance = Float.POSITIVE_INFINITY

    db.rawQuery(
        """SELECT c.id, c.centroid, c.person_id
           FROM clusters c
           WHERE c.engine_id=? AND c.model_version=? AND c.embedding_dim=?""",
        arrayOf(provenance.engineId, provenance.modelVersion, provenance.dimension.toString())
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val clusterId = cursor.getString(0)
            val centroid = bytesToFloats(cursor.getBlob(1))
            val personId = if (cursor.isNull(2)) null else cursor.getString(2)

            if (personId != null && personId in excludedPersonIds) continue

            val distance = cosineDistance(embedding, centroid)
            if (distance < bestDistance) {
                secondBestDistance = bestDistance
                bestDistance = distance
                bestClusterId = clusterId
                bestPersonId = personId
            } else if (distance < secondBestDistance) {
                secondBestDistance = distance
            }
        }
    }

    val effectiveThreshold = if (quality < 0.40f) maxDistance * 0.85f else maxDistance
    val isConfidentMatch = bestClusterId != null && bestDistance <= effectiveThreshold
    val isAmbiguous = isConfidentMatch && (secondBestDistance - bestDistance < 0.05f) && bestDistance > (effectiveThreshold * 0.8f)

    db.beginTransaction()
    try {
        val assignedClusterId: String
        val assignedPersonId: String

        if (priorClusterId != null) {
            assignedClusterId = priorClusterId
            assignedPersonId = priorPersonId ?: priorClusterId
        } else if (isConfidentMatch && !isAmbiguous && bestClusterId != null) {
            val clusterId = bestClusterId
            assignedClusterId = clusterId
            assignedPersonId = bestPersonId ?: clusterId
        } else {
            val newId = UUID.randomUUID().toString()
            assignedClusterId = newId
            assignedPersonId = newId

            val personValues = ContentValues().apply {
                put("id", newId)
                put("name", "")
                put("cover_media_uri", mediaUri)
                put("is_hidden", 0)
                put("engine_id", provenance.engineId)
                put("created_at", System.currentTimeMillis())
                put("updated_at", System.currentTimeMillis())
            }
            db.insertOrThrow("people", null, personValues)

            val clusterValues = ContentValues().apply {
                put("id", newId)
                put("person_id", newId)
                put("display_name", "")
                put("centroid", floatsToBytes(embedding))
                put("embedding_dim", provenance.dimension)
                put("engine_id", provenance.engineId)
                put("model_version", provenance.modelVersion)
                put("face_count", 0)
                put("representative_face_id", faceId)
                put("created_at", System.currentTimeMillis())
                put("updated_at", System.currentTimeMillis())
            }
            db.insertOrThrow("clusters", null, clusterValues)

            if (bestPersonId != null && bestDistance <= maxDistance * 1.15f) {
                val suggestionValues = ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("face_id", faceId)
                    put("media_uri", mediaUri)
                    put("face_index", faceIndex)
                    put("engine_id", provenance.engineId)
                    put("suggested_person_id", bestPersonId)
                    put("distance", bestDistance)
                    put("status", "pending")
                    put("created_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict("review_suggestions", null, suggestionValues, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }

        val faceValues = ContentValues().apply {
            put("media_uri", mediaUri)
            put("face_index", faceIndex)
            put("engine_id", provenance.engineId)
            put("face_id", faceId)
            put("cluster_id", assignedClusterId)
            put("person_id", assignedPersonId)
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

        updateCentroidIncremental(db, assignedClusterId, embedding, provenance.engineId, provenance.modelVersion)
        db.setTransactionSuccessful()
        return assignedClusterId
    } finally {
        db.endTransaction()
    }
}

/**
 * Stores a raw detected face observation without immediately assigning a final cluster.
 * Used by modern incremental clustering pipelines.
 */
