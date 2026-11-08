package com.amresalehin.emregallery.face

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.RectF
import java.util.UUID


internal data class EnteInitialClusteringData(
    val observations: List<EnteFaceClusteringStrategy.FaceObservation>,
    val exclusions: Map<String, Set<String>>,
    val userNamedPeople: Map<String, String>,
    val hiddenPeople: Set<String>,
    val userCoverMedia: Map<String, String>,
    val lockedPeople: Set<String>,
    val lockedObs: List<EnteFaceClusteringStrategy.FaceObservation>,
    val fluidObs: List<EnteFaceClusteringStrategy.FaceObservation>,
    val workingClusters: MutableList<EnteFaceClusteringStrategy.WorkingCluster>
)

internal fun EnteFaceClusteringStrategy.loadInitialClusteringData(
    db: SQLiteDatabase,
    engineId: String,
    modelVersion: String
): EnteInitialClusteringData {
        // 1. Fetch all faces for this engine with photo date ordering (chronological)
        val observations = mutableListOf<FaceObservation>()
        db.rawQuery(
            """SELECT f.media_uri, f.face_index, f.face_id, f.person_id, f.cluster_id,
                      f.embedding, f.box_left, f.box_top, f.box_right, f.box_bottom,
                      f.detection_confidence, f.quality_score,
                      COALESCE(m.indexed_at, f.created_at) AS sort_time
               FROM faces f
               LEFT JOIN indexed_media m ON f.media_uri = m.media_uri AND m.engine_id = f.engine_id
               WHERE f.engine_id=? AND f.model_version=?
               ORDER BY sort_time ASC, f.media_uri ASC, f.face_index ASC""",
            arrayOf(engineId, modelVersion)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val uri = cursor.getString(0)
                val idx = cursor.getInt(1)
                val faceId = cursor.getString(2) ?: "${engineId}#${uri}#${idx}"
                val personId = if (cursor.isNull(3)) null else cursor.getString(3)
                val clusterId = if (cursor.isNull(4)) null else cursor.getString(4)
                val emb = FaceClusterStore.bytesToFloats(cursor.getBlob(5))
                val bounds = RectF(cursor.getFloat(6), cursor.getFloat(7), cursor.getFloat(8), cursor.getFloat(9))
                val conf = cursor.getFloat(10)
                val qual = cursor.getFloat(11)
                val time = cursor.getLong(12)
                observations.add(
                    FaceObservation(uri, idx, faceId, personId, clusterId, emb, bounds, conf, qual, time)
                )
            }
        }

        if (observations.isEmpty()) {
            store.pruneEmptyAutoPeople(db, engineId)
            db.setTransactionSuccessful()
            onProgress(0, 0)
            return 0
        }

        // 2. Load manual feedback exclusions: (source_id -> excluded target person IDs)
        val exclusions = mutableMapOf<String, MutableSet<String>>()
        db.rawQuery(
            """SELECT source_id, target_id FROM face_feedback
               WHERE engine_id=? AND feedback_type IN ('REJECTED_MATCH', 'REMOVE_FACE_FROM_PERSON')""",
            arrayOf(engineId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val src = cursor.getString(0)
                val tgt = cursor.getString(1)
                exclusions.getOrPut(src) { mutableSetOf() }.add(tgt)
            }
        }

        // 3. Load user-assigned person names, hidden flags, and covers
        val userNamedPeople = mutableMapOf<String, String>()
        val hiddenPeople = mutableSetOf<String>()
        val userCoverMedia = mutableMapOf<String, String>()
        val lockedPeople = mutableSetOf<String>()

        db.rawQuery("SELECT id, name, is_hidden, cover_media_uri FROM people WHERE engine_id=?", arrayOf(engineId)).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1).orEmpty()
                val hidden = cursor.getInt(2) == 1
                val cover = if (cursor.isNull(3)) null else cursor.getString(3)
                if (name.isNotBlank() && !name.equals("Unknown", ignoreCase = true)) {
                    userNamedPeople[id] = name
                    lockedPeople.add(id)
                }
                if (hidden) {
                    hiddenPeople.add(id)
                    lockedPeople.add(id)
                }
                if (!cover.isNullOrBlank()) {
                    userCoverMedia[id] = cover
                }
            }
        }

        // 4. Locked faces: faces explicitly assigned by user, merged, split, or in named/hidden people
        val explicitlyAssignedFaceToPerson = mutableMapOf<String, String>()
        db.rawQuery(
            "SELECT source_id, target_id, feedback_type FROM face_feedback WHERE engine_id=?",
            arrayOf(engineId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val src = cursor.getString(0)
                val tgt = cursor.getString(1)
                val type = cursor.getString(2)
                when (type) {
                    "ASSIGN_FACE_TO_PERSON" -> {
                        explicitlyAssignedFaceToPerson[src] = tgt
                        if (tgt.isNotBlank()) lockedPeople.add(tgt)
                    }
                    "REMOVE_FACE_FROM_PERSON" -> {
                        if (tgt.isNotBlank()) lockedPeople.add(tgt)
                    }
                    "MERGE_PEOPLE" -> {
                        if (src.isNotBlank()) lockedPeople.add(src)
                        if (tgt.isNotBlank()) lockedPeople.add(tgt)
                    }
                    "SPLIT_CLUSTER" -> {
                        if (tgt.isNotBlank()) lockedPeople.add(tgt)
                    }
                }
            }
        }

        val workingClusters = mutableListOf<WorkingCluster>()

        // Partition locked faces from fluid faces
        val (lockedObs, fluidObs) = observations.partition { obs ->
            explicitlyAssignedFaceToPerson.containsKey(obs.faceId) ||
            (obs.personId != null && obs.personId in lockedPeople)
        }

        // Seed working clusters from user-named / explicitly locked persons
        val lockedByPerson = lockedObs.groupBy { obs ->
            explicitlyAssignedFaceToPerson[obs.faceId] ?: obs.personId ?: "unknown_${obs.faceId}"
        }

        for ((pId, faces) in lockedByPerson) {
            val clusterId = UUID.randomUUID().toString()
            val dim = faces.first().embedding.size
            val centroid = computeMeanNormalized(faces.map { it.embedding }, dim)
            val highestQualFace = faces.maxByOrNull { it.quality } ?: faces.first()
            workingClusters.add(
                WorkingCluster(
                    clusterId = clusterId,
                    personId = pId,
                    representativeEmbedding = FaceClusterStore.normalize(highestQualFace.embedding),
                    centroid = centroid,
                    maxFaceQuality = highestQualFace.quality,
                    faces = faces.toMutableList()
                )
            )
        }

    return EnteInitialClusteringData(
        observations = observations,
        exclusions = exclusions,
        userNamedPeople = userNamedPeople,
        hiddenPeople = hiddenPeople,
        userCoverMedia = userCoverMedia,
        lockedPeople = lockedPeople,
        lockedObs = lockedObs,
        fluidObs = fluidObs,
        workingClusters = workingClusters
    )
}
