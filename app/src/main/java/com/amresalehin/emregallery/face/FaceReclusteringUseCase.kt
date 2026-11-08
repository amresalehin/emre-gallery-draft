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


// --- Safe, Non-Destructive Reclustering (Engine-Isolated) ---
internal fun FaceClusterStore.reclusterAllSafelyImpl(
    maxDistance: Float = 0.60f,
    engineId: String = BuffaloLFaceEngine.ENGINE_ID,
    modelVersion: String = BuffaloLFaceEngine.MODEL_VERSION
): Int = FaceProcessingCoordinator.withProcessing(
    operationId = "reclusterAllSafely_${System.nanoTime()}",
    type = FaceProcessingOperation.GROUPING,
    engineId = engineId,
    description = "Reclustering all faces"
) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        data class FaceEntry(
            val mediaUri: String,
            val faceIndex: Int,
            val faceId: String,
            val personId: String?,
            val clusterId: String?,
            val embedding: FloatArray,
            val bounds: RectF,
            val confidence: Float,
            val quality: Float
        )

        val engineFaces = mutableListOf<FaceEntry>()
        db.rawQuery(
            """SELECT media_uri, face_index, face_id, person_id, cluster_id, embedding,
                      box_left, box_top, box_right, box_bottom, detection_confidence, quality_score
               FROM faces
               WHERE engine_id=? AND model_version=?""",
            arrayOf(engineId, modelVersion)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val mediaUri = cursor.getString(0)
                val faceIndex = cursor.getInt(1)
                val faceId = cursor.getString(2) ?: "${engineId}#${mediaUri}#${faceIndex}"
                val personId = if (cursor.isNull(3)) null else cursor.getString(3)
                val clusterId = if (cursor.isNull(4)) null else cursor.getString(4)
                val emb = bytesToFloats(cursor.getBlob(5))
                val bounds = RectF(cursor.getFloat(6), cursor.getFloat(7), cursor.getFloat(8), cursor.getFloat(9))
                val conf = cursor.getFloat(10)
                val qual = cursor.getFloat(11)
                engineFaces += FaceEntry(mediaUri, faceIndex, faceId, personId, clusterId, emb, bounds, conf, qual)
            }
        }

        if (engineFaces.isEmpty()) {
            pruneEmptyAutoPeople(db, engineId)
            db.setTransactionSuccessful()
            return 0
        }

        // Identify faces and people locked by manual feedback or manual identity corrections
        val lockedFaceIds = mutableSetOf<String>()
        val lockedPersonIds = mutableSetOf<String>()

        db.rawQuery(
            "SELECT source_id, target_id, feedback_type FROM face_feedback WHERE engine_id=?",
            arrayOf(engineId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val src = cursor.getString(0)
                val tgt = cursor.getString(1)
                val type = cursor.getString(2)
                when (type) {
                    "ASSIGN_FACE_TO_PERSON", "REMOVE_FACE_FROM_PERSON" -> {
                        lockedFaceIds.add(src)
                        if (tgt.isNotBlank()) lockedPersonIds.add(tgt)
                    }
                    "MERGE_PEOPLE" -> {
                        if (src.isNotBlank()) lockedPersonIds.add(src)
                        if (tgt.isNotBlank()) lockedPersonIds.add(tgt)
                    }
                    "SPLIT_CLUSTER" -> {
                        if (tgt.isNotBlank()) lockedPersonIds.add(tgt)
                    }
                }
            }
        }

        val preservedPeopleNames = mutableMapOf<String, String>()
        val preservedPeopleHidden = mutableMapOf<String, Int>()
        val preservedPeopleCover = mutableMapOf<String, String?>()
        db.rawQuery("SELECT id, name, is_hidden, cover_media_uri FROM people WHERE engine_id=?", arrayOf(engineId)).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1).orEmpty()
                val isHidden = cursor.getInt(2)
                val cover = if (cursor.isNull(3)) null else cursor.getString(3)
                preservedPeopleNames[id] = name
                preservedPeopleHidden[id] = isHidden
                preservedPeopleCover[id] = cover
                if (name.isNotBlank() && !name.equals("Unknown", ignoreCase = true)) {
                    lockedPersonIds.add(id)
                }
                if (isHidden == 1) {
                    lockedPersonIds.add(id)
                }
            }
        }

        class TempCluster(
            var id: String,
            var personId: String,
            var centroid: FloatArray,
            val faces: MutableList<FaceEntry>
        )

        val tempClusters = mutableListOf<TempCluster>()

        val (lockedOrNamedFaces, fluidFaces) = engineFaces.partition {
            it.faceId in lockedFaceIds || (it.personId != null && it.personId in lockedPersonIds)
        }

        val groupedByPerson = lockedOrNamedFaces.groupBy { it.personId ?: UUID.randomUUID().toString() }
        for ((personId, faces) in groupedByPerson) {
            val clusterId = UUID.randomUUID().toString()
            val dim = faces.first().embedding.size
            val mean = FloatArray(dim)
            faces.forEach { f ->
                val norm = normalize(f.embedding)
                for (i in 0 until dim) mean[i] += norm[i]
            }
            val centroid = normalize(mean)
            tempClusters.add(TempCluster(clusterId, personId, centroid, faces.toMutableList()))
        }

        for (face in fluidFaces) {
            val normVec = normalize(face.embedding)
            var best: TempCluster? = null
            var bestDist = Float.POSITIVE_INFINITY
            for (c in tempClusters) {
                val dist = cosineDistance(normVec, c.centroid)
                if (dist < bestDist) {
                    bestDist = dist
                    best = c
                }
            }

            val effThresh = if (face.quality < 0.40f) maxDistance * 0.85f else maxDistance
            if (best != null && bestDist <= effThresh) {
                best.faces.add(face)
                val dim = normVec.size
                val mean = FloatArray(dim)
                for (f in best.faces) {
                    val v = normalize(f.embedding)
                    for (i in 0 until dim) mean[i] += v[i]
                }
                best.centroid = normalize(mean)
            } else {
                val newClusterId = UUID.randomUUID().toString()
                val newPersonId = UUID.randomUUID().toString()
                tempClusters.add(TempCluster(newClusterId, newPersonId, normVec.copyOf(), mutableListOf(face)))
            }
        }

        // Keep prior IDs for unchanged unnamed groups, but don't recycle a locked identity.
        val usedPersonIds = mutableSetOf<String>()
        val usedClusterIds = mutableSetOf<String>()
        for (cluster in tempClusters) {
            if (cluster.personId !in lockedPersonIds) {
                val priorId = cluster.faces.mapNotNull { it.personId }.groupingBy { it }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .firstOrNull { it.key !in lockedPersonIds && it.key !in usedPersonIds }?.key
                if (priorId != null) cluster.personId = priorId
            }
            if (!usedPersonIds.add(cluster.personId)) {
                cluster.personId = UUID.randomUUID().toString()
                usedPersonIds.add(cluster.personId)
            }
            val priorCluster = cluster.faces.mapNotNull { it.clusterId }.groupingBy { it }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull { it.key !in usedClusterIds }?.key
            if (priorCluster != null) cluster.id = priorCluster
            if (!usedClusterIds.add(cluster.id)) {
                cluster.id = UUID.randomUUID().toString()
                usedClusterIds.add(cluster.id)
            }
        }

        // Only delete clusters belonging to this specific engine
        db.delete("clusters", "engine_id=? AND model_version=?", arrayOf(engineId, modelVersion))

        val now = System.currentTimeMillis()
        val dim = engineFaces.first().embedding.size
        for (c in tempClusters) {
            val existingName = preservedPeopleNames[c.personId].orEmpty()
            val existingHidden = preservedPeopleHidden[c.personId] ?: 0
            val existingCover = preservedPeopleCover[c.personId] ?: c.faces.firstOrNull()?.mediaUri
            val personVals = ContentValues().apply {
                put("id", c.personId)
                put("name", existingName)
                put("is_hidden", existingHidden)
                put("cover_media_uri", existingCover)
                put("engine_id", engineId)
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertWithOnConflict("people", null, personVals, SQLiteDatabase.CONFLICT_REPLACE)

            val clusterVals = ContentValues().apply {
                put("id", c.id)
                put("person_id", c.personId)
                put("display_name", existingName)
                put("centroid", floatsToBytes(c.centroid))
                put("face_count", c.faces.size)
                put("embedding_dim", dim)
                put("engine_id", engineId)
                put("model_version", modelVersion)
                put("representative_face_id", c.faces.firstOrNull()?.faceId)
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertOrThrow("clusters", null, clusterVals)

            for (f in c.faces) {
                val faceVals = ContentValues().apply {
                    put("cluster_id", c.id)
                    put("person_id", c.personId)
                }
                db.update(
                    "faces",
                    faceVals,
                    "media_uri=? AND face_index=? AND engine_id=?",
                    arrayOf(f.mediaUri, f.faceIndex.toString(), engineId)
                )
            }
        }
        pruneEmptyAutoPeople(db, engineId)

        db.setTransactionSuccessful()
        return tempClusters.size
    } finally {
        db.endTransaction()
    }
}
internal fun FaceClusterStore.reclusterAllImpl(maxDistance: Float = 0.60f): Int = reclusterAllSafely(maxDistance)
internal fun FaceClusterStore.resetClustersOnlyImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID) = FaceProcessingCoordinator.withProcessing(
    operationId = "resetClustersOnly_${System.nanoTime()}",
    type = FaceProcessingOperation.FACE_DB_MUTATION,
    engineId = engineId,
    description = "Resetting face clusters"
) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        db.delete("clusters", "engine_id=?", arrayOf(engineId))
        val faceReset = ContentValues().apply {
            putNull("cluster_id")
            putNull("person_id")
        }
        db.update("faces", faceReset, "engine_id=?", arrayOf(engineId))
        db.delete("people", "engine_id=?", arrayOf(engineId))
        db.delete("review_suggestions", "engine_id=?", arrayOf(engineId))
        db.delete("grouping_state", "engine_id=?", arrayOf(engineId))
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
    FaceStoreEvents.notifyAllUpdated()
}
internal fun FaceClusterStore.clearAllFaceDataImpl() {
    val db = writableDatabase
    db.beginTransaction()
    try {
        db.delete("faces", null, null)
        db.delete("clusters", null, null)
        db.delete("people", null, null)
        db.delete("face_feedback", null, null)
        db.delete("review_suggestions", null, null)
        db.delete("indexed_media", null, null)
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
