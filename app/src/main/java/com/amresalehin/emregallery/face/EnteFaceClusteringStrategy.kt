package com.amresalehin.emregallery.face

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.RectF
import java.util.UUID

/**
 * Ente-style Linear Incremental Face Clustering Strategy.
 *
 * Characteristics:
 * 1. Linear: Evaluates faces in strict chronological order (oldest photos to newest photos)
 *    to align with natural human identity progression and keep clustering deterministic.
 * 2. Incremental: New observations are matched against established clusters; reprocessing
 *    yields stable clusters without dismantling existing, user-corrected groups.
 * 3. Quality-Aware: Utilizes FaceQualityMetrics (blur, pose, size) to prevent blurry/ambiguous
 *    faces from polluting clean identities. Low-quality or borderline-distance faces generate
 *    review suggestions instead of being forced into confident person clusters.
 * 4. User-Feedback Preserving: Strictly honors manual names, splits, merges, and face exclusions.
 * 5. Strict Provenance Isolation: Only processes embeddings matching [EnteFaceConstants.ENGINE_ID]
 *    and [EnteFaceConstants.MODEL_VERSION].
 */
object EnteFaceClusteringStrategy : FaceClusteringStrategy {

    override val strategyId: String = "ente_linear_incremental_v1"
    override val strategyVersion: Int = 1

    const val DEFAULT_THRESHOLD = EnteFaceConstants.DEFAULT_RECOGNITION_DISTANCE // 0.55f
    const val AMBIGUITY_MARGIN = 0.06f
    const val MIN_QUALITY_FOR_CLUSTER_ANCHOR = 0.60f

    override fun clusterFaces(
        store: FaceClusterStore,
        mediaUris: List<String>,
        engineId: String,
        modelVersion: String,
        embeddingDimension: Int,
        maxDistance: Float,
        onProgress: (processedFaces: Int, totalFaces: Int) -> Unit
    ): Int {
        check(engineId == EnteFaceConstants.ENGINE_ID) {
            "EnteFaceClusteringStrategy must only be run with engine ${EnteFaceConstants.ENGINE_ID}"
        }
        return reclusterChronologically(store, maxDistance, engineId, modelVersion, onProgress)
    }

    data class FaceObservation(
        val mediaUri: String,
        val faceIndex: Int,
        val faceId: String,
        val personId: String?,
        val clusterId: String?,
        val embedding: FloatArray,
        val bounds: RectF,
        val confidence: Float,
        val quality: Float,
        val timestamp: Long
    )

    internal data class WorkingCluster(
        var clusterId: String,
        var personId: String,
        var representativeEmbedding: FloatArray,
        var centroid: FloatArray,
        var maxFaceQuality: Float,
        val faces: MutableList<FaceObservation>
    )

    @Synchronized
    fun reclusterChronologically(
        store: FaceClusterStore,
        maxDistance: Float = DEFAULT_THRESHOLD,
        engineId: String = EnteFaceConstants.ENGINE_ID,
        modelVersion: String = EnteFaceConstants.MODEL_VERSION,
        onProgress: (processedFaces: Int, totalFaces: Int) -> Unit = { _, _ -> }
    ): Int {
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            val initData = loadInitialClusteringData(db, engineId, modelVersion)
            val observations = initData.observations
            if (observations.isEmpty()) {
                store.pruneEmptyAutoPeople(db, engineId)
                db.setTransactionSuccessful()
                onProgress(0, 0)
                return 0
            }
            val exclusions = initData.exclusions
            val userNamedPeople = initData.userNamedPeople
            val hiddenPeople = initData.hiddenPeople
            val userCoverMedia = initData.userCoverMedia
            val lockedPeople = initData.lockedPeople
            val lockedObs = initData.lockedObs
            val fluidObs = initData.fluidObs
            val workingClusters = initData.workingClusters

            // 5. Linear Incremental Pass on remaining faces in chronological order
            val suggestionsToInsert = mutableListOf<ContentValues>()

            onProgress(lockedObs.size, observations.size)
            for ((facePosition, face) in fluidObs.withIndex()) {
                val normEmb = FaceClusterStore.normalize(face.embedding)
                val excludedForFace = exclusions[face.faceId] ?: emptySet()

                var bestCluster: WorkingCluster? = null
                var bestDistance = Float.POSITIVE_INFINITY
                var secondBestDistance = Float.POSITIVE_INFINITY

                for (c in workingClusters) {
                    if (c.personId in excludedForFace) continue

                    // In Ente pipeline, compare with cluster representative & centroid
                    val distRep = FaceClusterStore.cosineDistance(normEmb, c.representativeEmbedding)
                    val distCentroid = FaceClusterStore.cosineDistance(normEmb, c.centroid)
                    val effectiveDist = minOf(distRep, distCentroid)

                    if (effectiveDist < bestDistance) {
                        secondBestDistance = bestDistance
                        bestDistance = effectiveDist
                        bestCluster = c
                    } else if (effectiveDist < secondBestDistance) {
                        secondBestDistance = effectiveDist
                    }
                }

                // Dynamic threshold based on face quality:
                // Lower quality faces require stricter proximity to prevent noise pollution.
                val qualityScale = (0.75f + 0.25f * face.quality.coerceIn(0f, 1f))
                val effectiveThresh = maxDistance * qualityScale

                val isMatch = bestCluster != null && bestDistance <= effectiveThresh
                val isAmbiguous = isMatch && ((secondBestDistance - bestDistance) < AMBIGUITY_MARGIN)

                if (bestCluster != null && isMatch && !isAmbiguous) {
                    val cluster = bestCluster
                    cluster.faces.add(face)
                    // Update cluster centroid and representative if new face has higher quality
                    cluster.centroid = computeMeanNormalized(cluster.faces.map { it.embedding }, normEmb.size)
                    if (face.quality > cluster.maxFaceQuality) {
                        cluster.representativeEmbedding = normEmb
                        cluster.maxFaceQuality = face.quality
                    }
                } else if (face.quality < MIN_QUALITY_FOR_CLUSTER_ANCHOR) {
                    // Quality is below anchor threshold; do not mint a brand-new identity.
                    // Generate review suggestion if a candidate cluster exists.
                    if (bestCluster != null && bestDistance <= (maxDistance * 1.5f)) {
                        val suggestion = ContentValues().apply {
                            put("id", UUID.randomUUID().toString())
                            put("face_id", face.faceId)
                            put("media_uri", face.mediaUri)
                            put("face_index", face.faceIndex)
                            put("engine_id", engineId)
                            put("suggested_person_id", bestCluster.personId)
                            put("distance", bestDistance)
                            put("status", "pending")
                            put("created_at", System.currentTimeMillis())
                        }
                        suggestionsToInsert.add(suggestion)
                    }
                } else {
                    // Confident, high-quality face: create a new cluster / person identity
                    val newClusterId = UUID.randomUUID().toString()
                    val newPersonId = UUID.randomUUID().toString()
                    val newCluster = WorkingCluster(
                        clusterId = newClusterId,
                        personId = newPersonId,
                        representativeEmbedding = normEmb.copyOf(),
                        centroid = normEmb.copyOf(),
                        maxFaceQuality = face.quality,
                        faces = mutableListOf(face)
                    )
                    workingClusters.add(newCluster)

                    // If borderline or ambiguous, generate a review suggestion for the best match
                    if (bestCluster != null && bestDistance <= (maxDistance * 1.15f)) {
                        val suggestion = ContentValues().apply {
                            put("id", UUID.randomUUID().toString())
                            put("face_id", face.faceId)
                            put("media_uri", face.mediaUri)
                            put("face_index", face.faceIndex)
                            put("engine_id", engineId)
                            put("suggested_person_id", bestCluster.personId)
                            put("distance", bestDistance)
                            put("status", "pending")
                            put("created_at", System.currentTimeMillis())
                        }
                        suggestionsToInsert.add(suggestion)
                    }
                }
                val processedFaces = lockedObs.size + facePosition + 1
                val interval = maxOf(1, observations.size / 100)
                if (processedFaces % interval == 0 || processedFaces == observations.size) {
                    onProgress(processedFaces, observations.size)
                }
            }


            return reconcileAndPersistWorkingClusters(
                db = db,
                store = store,
                engineId = engineId,
                modelVersion = modelVersion,
                observations = observations,
                workingClusters = workingClusters,
                lockedPeople = lockedPeople,
                userNamedPeople = userNamedPeople,
                hiddenPeople = hiddenPeople,
                userCoverMedia = userCoverMedia,
                suggestionsToInsert = suggestionsToInsert
            )
        } finally {
            db.endTransaction()
        }
    }
}
