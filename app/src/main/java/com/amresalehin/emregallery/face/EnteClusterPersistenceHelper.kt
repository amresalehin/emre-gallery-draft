package com.amresalehin.emregallery.face

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.RectF
import java.util.UUID


internal fun EnteFaceClusteringStrategy.reconcileAndPersistWorkingClusters(
    db: SQLiteDatabase,
    store: FaceClusterStore,
    engineId: String,
    modelVersion: String,
    observations: List<EnteFaceClusteringStrategy.FaceObservation>,
    workingClusters: List<EnteFaceClusteringStrategy.WorkingCluster>,
    lockedPeople: Set<String>,
    userNamedPeople: Map<String, String>,
    hiddenPeople: Set<String>,
    userCoverMedia: Map<String, String>,
    suggestionsToInsert: List<ContentValues>
): Int {
        // Preserve identities for unchanged unnamed groups; locked identity records always win.
        val usedPersonIds = mutableSetOf<String>()
        val usedClusterIds = mutableSetOf<String>()
        for (cluster in workingClusters) {
            if (cluster.personId !in lockedPeople) {
                val priorId = cluster.faces.mapNotNull { it.personId }.groupingBy { it }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .firstOrNull { it.key !in lockedPeople && it.key !in usedPersonIds }?.key
                if (priorId != null) cluster.personId = priorId
            }
            if (!usedPersonIds.add(cluster.personId)) {
                cluster.personId = UUID.randomUUID().toString()
                usedPersonIds.add(cluster.personId)
            }
            val priorCluster = cluster.faces.mapNotNull { it.clusterId }.groupingBy { it }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull { it.key !in usedClusterIds }?.key
            if (priorCluster != null) cluster.clusterId = priorCluster
            if (!usedClusterIds.add(cluster.clusterId)) {
                cluster.clusterId = UUID.randomUUID().toString()
                usedClusterIds.add(cluster.clusterId)
            }
        }

        // 6. Persist Updated Clusters and Faces for this engine
        // Remove previous non-locked clusters for this engine only
        db.delete("clusters", "engine_id=? AND model_version=?", arrayOf(engineId, modelVersion))

        val now = System.currentTimeMillis()
        val dim = observations.first().embedding.size

        for (c in workingClusters) {
            val preservedName = userNamedPeople[c.personId].orEmpty()
            val isHidden = if (hiddenPeople.contains(c.personId)) 1 else 0
            val coverMedia = userCoverMedia[c.personId] ?: (c.faces.maxByOrNull { it.quality }?.mediaUri ?: c.faces.first().mediaUri)

            val personVals = ContentValues().apply {
                put("id", c.personId)
                put("name", preservedName)
                put("cover_media_uri", coverMedia)
                put("is_hidden", isHidden)
                put("engine_id", engineId)
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertWithOnConflict("people", null, personVals, SQLiteDatabase.CONFLICT_REPLACE)

            val clusterVals = ContentValues().apply {
                put("id", c.clusterId)
                put("person_id", c.personId)
                put("display_name", preservedName)
                put("centroid", FaceClusterStore.floatsToBytes(c.centroid))
                put("embedding_dim", dim)
                put("engine_id", engineId)
                put("model_version", modelVersion)
                put("face_count", c.faces.size)
                put("representative_face_id", c.faces.firstOrNull()?.faceId)
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertOrThrow("clusters", null, clusterVals)

            for (f in c.faces) {
                val faceVals = ContentValues().apply {
                    put("cluster_id", c.clusterId)
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

        // Clean up old pending suggestions for this engine and insert new ones
        db.delete("review_suggestions", "engine_id=? AND status='pending'", arrayOf(engineId))
        for (s in suggestionsToInsert) {
            db.insertWithOnConflict("review_suggestions", null, s, SQLiteDatabase.CONFLICT_REPLACE)
        }
        store.pruneEmptyAutoPeople(db, engineId)

        db.setTransactionSuccessful()
        return workingClusters.size
    } finally {
}

internal fun EnteFaceClusteringStrategy.computeMeanNormalized(embeddings: List<FloatArray>, dim: Int): FloatArray {
    val sum = FloatArray(dim)
    for (emb in embeddings) {
        val norm = FaceClusterStore.normalize(emb)
        for (i in 0 until dim) sum[i] += norm[i]
    }
    return FaceClusterStore.normalize(sum)
}
