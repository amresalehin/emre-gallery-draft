package com.amresalehin.emregallery.face

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.RectF
import java.util.UUID
import kotlin.math.roundToInt

/** A fully processed observation ready for atomic media-generation replacement. */
data class FaceObservationReplacement(
    val faceIndex: Int,
    val embedding: FloatArray,
    val bounds: RectF,
    val confidence: Float,
    val quality: Float,
    val qualityMetrics: FaceQualityMetrics?
)

/** Persist every grouping-affecting input in the existing grouping revision column. */
fun groupingRevision(strategyVersion: Int, recognitionDistance: Float): Int {
    val thresholdRevision = (recognitionDistance.coerceIn(0f, 1f) * 10_000f).roundToInt()
    return strategyVersion.coerceAtLeast(0) * 10_001 + thresholdRevision
}

private data class PriorObservation(
    val faceIndex: Int,
    val clusterId: String?,
    val personId: String?,
    val embedding: FloatArray,
    val bounds: RectF
)

/**
 * Replaces one media item's complete generation in one transaction. Detection and embedding
 * happen before this method is called, so failure or cancellation leaves committed data intact.
 * Identity metadata is reconciled by position, spatial overlap, and embedding similarity rather
 * than blindly following a changed detection order.
 */
@Synchronized
fun FaceClusterStore.replaceMediaFacesAtomically(
    mediaUri: String,
    observations: List<FaceObservationReplacement>,
    provenance: EmbeddingProvenance,
    maxDistance: Float,
    deferClustering: Boolean
) {
    require(observations.map { it.faceIndex }.distinct().size == observations.size) {
        "A media generation cannot contain duplicate face indices."
    }
    observations.forEach { observation ->
        require(observation.embedding.size == provenance.dimension) {
            "Embedding dimension mismatch: expected ${provenance.dimension}, got ${observation.embedding.size}"
        }
    }

    val db = writableDatabase
    val affectedClusters = mutableSetOf<String>()
    db.beginTransaction()
    try {
        val previous = mutableListOf<PriorObservation>()
        db.rawQuery(
            """SELECT face_index, cluster_id, person_id, embedding,
                      box_left, box_top, box_right, box_bottom
               FROM faces WHERE media_uri=? AND engine_id=?""",
            arrayOf(mediaUri, provenance.engineId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                previous += PriorObservation(
                    faceIndex = cursor.getInt(0),
                    clusterId = if (cursor.isNull(1)) null else cursor.getString(1),
                    personId = if (cursor.isNull(2)) null else cursor.getString(2),
                    embedding = bytesToFloats(cursor.getBlob(3)),
                    bounds = RectF(cursor.getFloat(4), cursor.getFloat(5), cursor.getFloat(6), cursor.getFloat(7))
                )
            }
        }
        previous.mapNotNullTo(affectedClusters) { it.clusterId }

        db.delete("review_suggestions", "media_uri=? AND engine_id=?", arrayOf(mediaUri, provenance.engineId))
        db.delete("face_metrics_v2", "media_uri=? AND engine_id=?", arrayOf(mediaUri, provenance.engineId))
        db.delete("faces", "media_uri=? AND engine_id=?", arrayOf(mediaUri, provenance.engineId))

        val consumedPrevious = mutableSetOf<Int>()
        val now = System.currentTimeMillis()
        for (observation in observations) {
            val embedding = normalize(observation.embedding)
            val exact = previous.firstOrNull { it.faceIndex == observation.faceIndex }
            val reconciled = if (exact != null) {
                exact
            } else {
                previous.filter { it.faceIndex !in consumedPrevious }
                    .mapNotNull { old ->
                        val overlap = faceIoU(observation.bounds, old.bounds)
                        val distance = cosineDistance(embedding, normalize(old.embedding))
                        if (overlap >= 0.40f && distance <= 0.35f) old to (overlap - distance) else null
                    }
                    .maxByOrNull { it.second }?.first
            }
            reconciled?.let { consumedPrevious += it.faceIndex }

            var clusterId = if (deferClustering) reconciled?.clusterId else null
            var personId = reconciled?.personId
            if (!deferClustering) {
                clusterId = reconciled?.clusterId
                if (clusterId == null) {
                    val match = findBestCluster(db, embedding, provenance, mediaUri, observation.faceIndex)
                    if (match != null && match.second <= effectiveDistance(maxDistance, observation.quality)) {
                        clusterId = match.first
                    }
                }
                if (clusterId == null) {
                    val newId = UUID.randomUUID().toString()
                    clusterId = newId
                    personId = personId ?: newId
                    db.insertOrThrow("people", null, ContentValues().apply {
                        put("id", newId); put("name", ""); put("cover_media_uri", mediaUri)
                        put("is_hidden", 0); put("engine_id", provenance.engineId)
                        put("created_at", now); put("updated_at", now)
                    })
                    db.insertOrThrow("clusters", null, ContentValues().apply {
                        put("id", newId); put("person_id", personId); put("display_name", "")
                        put("centroid", floatsToBytes(embedding)); put("embedding_dim", provenance.dimension)
                        put("engine_id", provenance.engineId); put("model_version", provenance.modelVersion)
                        put("face_count", 0); put("representative_face_id", faceKey(provenance.engineId, mediaUri, observation.faceIndex))
                        put("created_at", now); put("updated_at", now)
                    })
                } else {
                    affectedClusters += clusterId
                }
                if (personId == null) {
                    personId = db.rawQuery("SELECT person_id FROM clusters WHERE id=?", arrayOf(clusterId)).use {
                        if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else clusterId
                    }
                }
            }

            db.insertOrThrow("faces", null, ContentValues().apply {
                put("media_uri", mediaUri); put("face_index", observation.faceIndex)
                put("engine_id", provenance.engineId)
                put("face_id", faceKey(provenance.engineId, mediaUri, observation.faceIndex))
                if (clusterId == null) putNull("cluster_id") else put("cluster_id", clusterId)
                if (personId == null) putNull("person_id") else put("person_id", personId)
                put("embedding", floatsToBytes(embedding)); put("model_version", provenance.modelVersion)
                put("embedding_dim", provenance.dimension); put("format_version", provenance.formatVersion)
                put("box_left", observation.bounds.left); put("box_top", observation.bounds.top)
                put("box_right", observation.bounds.right); put("box_bottom", observation.bounds.bottom)
                put("detection_confidence", observation.confidence); put("quality_score", observation.quality)
                put("created_at", now)
            })

            val metrics = observation.qualityMetrics ?: FaceQualityMetrics(
                blurScore = observation.quality,
                poseScore = observation.quality,
                sizeRatio = observation.bounds.width() * observation.bounds.height() / 1_000_000f,
                aspectRatio = if (observation.bounds.height() > 0f) observation.bounds.width() / observation.bounds.height() else 0.85f,
                overallQuality = observation.quality
            )
            db.insertOrThrow("face_metrics_v2", null, ContentValues().apply {
                put("media_uri", mediaUri); put("face_index", observation.faceIndex)
                put("engine_id", provenance.engineId); put("blur_score", metrics.blurScore)
                put("pose_score", metrics.poseScore); put("size_ratio", metrics.sizeRatio)
                put("aspect_ratio", metrics.aspectRatio); put("overall_quality", metrics.overallQuality)
                put("version", metrics.version); put("created_at", now)
            })
        }

        affectedClusters.forEach { clusterId ->
            recomputeCentroidInTransaction(db, clusterId, provenance.engineId, provenance.modelVersion)
        }
        pruneEmptyAutoPeople(db, provenance.engineId)
        db.insertWithOnConflict("indexed_media", null, ContentValues().apply {
            put("media_uri", mediaUri); put("engine_id", provenance.engineId); put("indexed_at", now)
            put("face_count", observations.size); put("status", "done"); putNull("error_message")
        }, SQLiteDatabase.CONFLICT_REPLACE)
        if (deferClustering) {
            db.insertWithOnConflict("grouping_state", null, ContentValues().apply {
                put("engine_id", provenance.engineId); put("model_version", provenance.modelVersion)
                put("strategy_version", -1); put("status", "pending"); put("grouped_people", 0)
                put("face_count", -1); putNull("error_message"); put("updated_at", now)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
    FaceThumbnailHelper.clearCaches()
    FaceStoreEvents.notifyAllUpdated()
}

/** Accepts a suggestion using its stored URI/index and repairs both cluster memberships atomically. */
@Synchronized
fun FaceClusterStore.acceptSuggestionAtomically(suggestionId: String): Boolean {
    val db = writableDatabase
    db.beginTransaction()
    try {
        var mediaUri: String? = null
        var faceIndex: Int? = null
        var faceId: String? = null
        var personId: String? = null
        var engineId: String? = null
        db.rawQuery(
            "SELECT media_uri, face_index, face_id, suggested_person_id, engine_id FROM review_suggestions WHERE id=? AND status='pending'",
            arrayOf(suggestionId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                mediaUri = cursor.getString(0); faceIndex = cursor.getInt(1); faceId = cursor.getString(2)
                personId = cursor.getString(3); engineId = cursor.getString(4)
            }
        }
        val uri = mediaUri ?: return false
        val index = faceIndex ?: return false
        val targetPerson = personId ?: return false
        val engine = engineId ?: return false
        val stableFaceId = faceId ?: faceKey(engine, uri, index)

        var oldCluster: String? = null
        var modelVersion: String? = null
        db.rawQuery(
            "SELECT cluster_id, model_version FROM faces WHERE media_uri=? AND face_index=? AND engine_id=?",
            arrayOf(uri, index.toString(), engine)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                oldCluster = if (cursor.isNull(0)) null else cursor.getString(0)
                modelVersion = cursor.getString(1)
            } else return false
        }
        val targetCluster = db.rawQuery(
            "SELECT id FROM clusters WHERE person_id=? AND engine_id=? AND model_version=? ORDER BY face_count DESC, updated_at DESC LIMIT 1",
            arrayOf(targetPerson, engine, modelVersion)
        ).use { if (it.moveToFirst()) it.getString(0) else null } ?: return false

        db.update("faces", ContentValues().apply {
            put("person_id", targetPerson); put("cluster_id", targetCluster)
        }, "media_uri=? AND face_index=? AND engine_id=?", arrayOf(uri, index.toString(), engine))
        db.update("review_suggestions", ContentValues().apply { put("status", "accepted") }, "id=?", arrayOf(suggestionId))
        db.insertWithOnConflict("face_feedback", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("feedback_type", "ASSIGN_FACE_TO_PERSON")
            put("source_id", stableFaceId); put("target_id", targetPerson); put("engine_id", engine)
            put("created_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        oldCluster?.let { recomputeCentroidInTransaction(db, it, engine, modelVersion!!) }
        if (oldCluster != targetCluster) recomputeCentroidInTransaction(db, targetCluster, engine, modelVersion!!)
        pruneEmptyAutoPeople(db, engine)
        db.setTransactionSuccessful()
        FaceThumbnailHelper.clearCaches()
        return true
    } finally {
        db.endTransaction()
        FaceStoreEvents.notifyAllUpdated()
    }
}

/** Complete reset including dependent metrics, grouping checkpoints, caches, and observers. */
@Synchronized
fun FaceClusterStore.clearAllFaceDataCompletely() {
    val db = writableDatabase
    db.beginTransaction()
    try {
        listOf("faces", "face_metrics_v2", "clusters", "people", "face_feedback", "review_suggestions", "indexed_media", "grouping_state").forEach {
            db.delete(it, null, null)
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
    FaceThumbnailHelper.clearCaches()
    FaceStoreEvents.notifyAllUpdated()
}

private fun findBestCluster(
    db: SQLiteDatabase,
    embedding: FloatArray,
    provenance: EmbeddingProvenance,
    mediaUri: String,
    faceIndex: Int
): Pair<String, Float>? {
    val excluded = mutableSetOf<String>()
    db.rawQuery(
        "SELECT target_id FROM face_feedback WHERE source_id=? AND engine_id=? AND feedback_type IN ('REJECTED_MATCH','REMOVE_FACE_FROM_PERSON')",
        arrayOf(faceKey(provenance.engineId, mediaUri, faceIndex), provenance.engineId)
    ).use { while (it.moveToNext()) excluded += it.getString(0) }
    var best: Pair<String, Float>? = null
    db.rawQuery(
        "SELECT id, centroid, person_id FROM clusters WHERE engine_id=? AND model_version=? AND embedding_dim=?",
        arrayOf(provenance.engineId, provenance.modelVersion, provenance.dimension.toString())
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val personId = if (cursor.isNull(2)) null else cursor.getString(2)
            if (personId in excluded) continue
            val distance = FaceClusterStore.cosineDistance(embedding, FaceClusterStore.bytesToFloats(cursor.getBlob(1)))
            if (distance < (best?.second ?: Float.POSITIVE_INFINITY)) best = cursor.getString(0) to distance
        }
    }
    return best
}

private fun recomputeCentroidInTransaction(db: SQLiteDatabase, clusterId: String, engineId: String, modelVersion: String) {
    val embeddings = mutableListOf<FloatArray>()
    db.rawQuery(
        "SELECT embedding FROM faces WHERE cluster_id=? AND engine_id=? AND model_version=?",
        arrayOf(clusterId, engineId, modelVersion)
    ).use { while (it.moveToNext()) embeddings += FaceClusterStore.bytesToFloats(it.getBlob(0)) }
    if (embeddings.isEmpty()) {
        db.delete("clusters", "id=?", arrayOf(clusterId))
        return
    }
    val sum = FloatArray(embeddings.first().size)
    embeddings.forEach { vector -> if (vector.size == sum.size) for (i in sum.indices) sum[i] += vector[i] }
    db.update("clusters", ContentValues().apply {
        put("centroid", FaceClusterStore.floatsToBytes(FaceClusterStore.normalize(sum)))
        put("face_count", embeddings.size); put("updated_at", System.currentTimeMillis())
    }, "id=?", arrayOf(clusterId))
}

private fun faceKey(engineId: String, mediaUri: String, faceIndex: Int) = "$engineId#$mediaUri#$faceIndex"
private fun effectiveDistance(maxDistance: Float, quality: Float) = if (quality < 0.40f) maxDistance * 0.85f else maxDistance
private fun faceIoU(a: RectF, b: RectF): Float {
    val intersection = maxOf(0f, minOf(a.right, b.right) - maxOf(a.left, b.left)) *
        maxOf(0f, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
    val union = a.width() * a.height() + b.width() * b.height() - intersection
    return if (union > 0f) intersection / union else 0f
}
