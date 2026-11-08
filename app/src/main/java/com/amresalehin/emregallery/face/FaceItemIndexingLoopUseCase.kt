package com.amresalehin.emregallery.face

import android.content.Context
import android.net.Uri
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap


internal data class ItemLoopSummary(
    val processed: Int,
    val facesFound: Int,
    val photosWithFaces: Int,
    val failed: Int,
    val lastCompletedItemName: String,
    val lastCompletedItemStatus: String,
    val lastCompletedItemFaces: Int?,
    val detectionStage: StageProgress,
    val embeddingStage: StageProgress,
    val failedList: List<Pair<String, String>>
)

internal suspend fun FaceIndexingRepository.processPendingPhotosLoop(
    pending: List<MediaItem>,
    store: FaceClusterStore,
    engine: FaceEngine?,
    engineId: String,
    provenance: EmbeddingProvenance,
    recognitionDistance: Float,
    cooldownMillis: Long,
    initialDetectionStage: StageProgress,
    initialEmbeddingStage: StageProgress,
    clusteringStage: StageProgress,
    awaitIfResumed: suspend () -> Unit,
    onProgress: suspend (FaceIndexingProgress) -> Unit
): ItemLoopSummary {
    val totalPending = pending.size
    var processed = 0
    var facesFound = 0
    var photosWithFaces = 0
    var failed = 0
    val failedList = mutableListOf<Pair<String, String>>()
    var lastCompletedItemName = ""
    var lastCompletedItemStatus = ""
    var lastCompletedItemFaces: Int? = null
    var detectionStage = initialDetectionStage
    var embeddingStage = initialEmbeddingStage
        for ((index, item) in pending.withIndex()) {
            awaitIfResumed()
            currentCoroutineContext().ensureActive()

            val itemName = item.name.ifBlank { item.uri.substringAfterLast('/') }
            val currentPct = if (totalPending > 0) (index * 100 / totalPending) else 0

            detectionStage = StageProgress(StageState.RUNNING, index, totalPending, currentPct)
            embeddingStage = StageProgress(StageState.PENDING, index, totalPending, currentPct)
            var lastItemStatus = "Scanning for faces…"
            var currentItemFacesCount: Int? = null

            onProgress(
                FaceIndexingProgress(
                    processed = processed,
                    total = totalPending,
                    facesFound = facesFound,
                    photosWithFaces = photosWithFaces,
                    failed = failed,
                    currentItemName = itemName,
                    currentItemStatus = lastItemStatus,
                    currentItemFaces = null,
                    engineId = engineId,
                    engineName = engineFactory.displayName,
                    detectionStage = detectionStage,
                    embeddingStage = embeddingStage,
                    clusteringStage = clusteringStage,
                    failedItems = failedList.toList()
                )
            )

            try {
                val activeEngine = engine ?: error("Face recognition engine is not ready.")
                val uri = Uri.parse(item.uri)

                // Phase 1: Face Detection
                val rawDetections = activeEngine.detectFaces(uri)
                val detectedCount = rawDetections.size
                currentItemFacesCount = detectedCount

                detectionStage = StageProgress(
                    state = if (index + 1 >= totalPending) StageState.COMPLETED else StageState.RUNNING,
                    processed = index + 1,
                    total = totalPending,
                    percentage = if (totalPending > 0) ((index + 1) * 100 / totalPending) else 100
                )

                if (detectedCount == 0) {
                    // 0 faces found - completed for this item
                    store.markMediaIndexed(item.uri, 0, engineId)
                    store.pruneStaleFacesForMedia(item.uri, engineId, 0)
                    lastItemStatus = "0 faces found"
                    embeddingStage = StageProgress(
                        state = if (index + 1 >= totalPending) StageState.COMPLETED else StageState.RUNNING,
                        processed = index + 1,
                        total = totalPending,
                        percentage = if (totalPending > 0) ((index + 1) * 100 / totalPending) else 100
                    )
                    onProgress(
                        FaceIndexingProgress(
                            processed = processed,
                            total = totalPending,
                            facesFound = facesFound,
                            photosWithFaces = photosWithFaces,
                            failed = failed,
                            currentItemName = itemName,
                            currentItemStatus = lastItemStatus,
                            currentItemFaces = 0,
                            engineId = engineId,
                            engineName = engineFactory.displayName,
                            detectionStage = detectionStage,
                            embeddingStage = embeddingStage,
                            clusteringStage = clusteringStage,
                            failedItems = failedList.toList()
                        )
                    )
                } else {
                    // Phase 2: Embedding Generation
                    embeddingStage = StageProgress(StageState.RUNNING, index, totalPending, currentPct)
                    lastItemStatus = "$detectedCount face${if (detectedCount > 1) "s" else ""} found, generating embeddings…"
                    onProgress(
                        FaceIndexingProgress(
                            processed = processed,
                            total = totalPending,
                            facesFound = facesFound,
                            photosWithFaces = photosWithFaces,
                            failed = failed,
                            currentItemName = itemName,
                            currentItemStatus = lastItemStatus,
                            currentItemFaces = detectedCount,
                            engineId = engineId,
                            engineName = engineFactory.displayName,
                            detectionStage = detectionStage,
                            embeddingStage = embeddingStage,
                            clusteringStage = clusteringStage,
                            failedItems = failedList.toList()
                        )
                    )

                    val detections = activeEngine.embedFaces(uri, rawDetections)
                    detections.forEach { face ->
                        if (engineFactory.clusteringStrategy != null) {
                            store.addRawFaceObservation(
                                mediaUri = item.uri,
                                faceIndex = face.faceIndex,
                                rawEmbedding = face.embedding,
                                bounds = face.bounds,
                                confidence = face.score,
                                quality = face.quality,
                                provenance = provenance,
                                qualityMetrics = face.qualityMetrics
                            )
                        } else {
                            store.addFace(
                                mediaUri = item.uri,
                                faceIndex = face.faceIndex,
                                rawEmbedding = face.embedding,
                                maxDistance = recognitionDistance,
                                bounds = face.bounds,
                                confidence = face.score,
                                quality = face.quality,
                                provenance = provenance,
                                qualityMetrics = face.qualityMetrics
                            )
                        }
                    }
                    store.markMediaIndexed(item.uri, detections.size, engineId)
                    store.pruneStaleFacesForMedia(item.uri, engineId, detections.map { it.faceIndex })
                    facesFound += detections.size
                    photosWithFaces++
                    lastItemStatus = "Indexed ${detections.size} face${if (detections.size > 1) "s" else ""}"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (err: Exception) {
                val errorDetail = err.localizedMessage ?: "Detection/embedding failed"
                store.markMediaFailed(item.uri, engineId, errorDetail)
                failedList.add(item.uri to errorDetail)
                failed++
                lastItemStatus = "Failed: $errorDetail"
            } finally {
                processed++
                lastCompletedItemName = itemName
                lastCompletedItemStatus = lastItemStatus
                lastCompletedItemFaces = currentItemFacesCount
                val completedPct = if (totalPending > 0) (processed * 100 / totalPending) else 100
                detectionStage = StageProgress(
                    state = if (processed >= totalPending) StageState.COMPLETED else StageState.RUNNING,
                    processed = processed,
                    total = totalPending,
                    percentage = completedPct
                )
                embeddingStage = StageProgress(
                    state = if (processed >= totalPending) StageState.COMPLETED else StageState.RUNNING,
                    processed = processed,
                    total = totalPending,
                    percentage = completedPct
                )

                onProgress(
                    FaceIndexingProgress(
                        processed = processed,
                        total = totalPending,
                        facesFound = facesFound,
                        photosWithFaces = photosWithFaces,
                        failed = failed,
                        currentItemName = itemName,
                        currentItemStatus = lastItemStatus,
                        currentItemFaces = currentItemFacesCount,
                        engineId = engineId,
                        engineName = engineFactory.displayName,
                        detectionStage = detectionStage,
                        embeddingStage = embeddingStage,
                        clusteringStage = clusteringStage,
                        failedItems = failedList.toList()
                    )
                )
                // Thermal throttling prevention: duty-cycle cooling pause between photos keeps
                // CPU core temperatures within safe limits and prevents system thermal throttling.
                if (cooldownMillis > 0L) {
                    kotlinx.coroutines.delay(cooldownMillis)
                }
                kotlinx.coroutines.yield()
            }
        }


    return ItemLoopSummary(
        processed = processed,
        facesFound = facesFound,
        photosWithFaces = photosWithFaces,
        failed = failed,
        lastCompletedItemName = lastCompletedItemName,
        lastCompletedItemStatus = lastCompletedItemStatus,
        lastCompletedItemFaces = lastCompletedItemFaces,
        detectionStage = detectionStage,
        embeddingStage = embeddingStage,
        failedList = failedList
    )
}
