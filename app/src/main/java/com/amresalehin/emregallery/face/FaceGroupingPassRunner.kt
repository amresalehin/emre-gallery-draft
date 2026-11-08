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


internal data class GroupingPassOutcome(
    val clusteringStage: StageProgress,
    val groupingStatus: String?
)

internal suspend fun FaceIndexingRepository.runPostIndexingGroupingPass(
    store: FaceClusterStore,
    pendingUris: List<String>,
    engineId: String,
    actualDimension: Int,
    recognitionDistance: Float,
    processed: Int,
    totalPending: Int,
    facesFound: Int,
    photosWithFaces: Int,
    failed: Int,
    lastCompletedItemName: String,
    lastCompletedItemStatus: String,
    lastCompletedItemFaces: Int?,
    detectionStage: StageProgress,
    embeddingStage: StageProgress,
    initialClusteringStage: StageProgress,
    failedList: List<Pair<String, String>>,
    onProgress: suspend (FaceIndexingProgress) -> Unit
): GroupingPassOutcome {
    var clusteringStage = initialClusteringStage
    var groupingStatus: String? = null
    val strategy = engineFactory.clusteringStrategy
    if (strategy != null) {
        val faceTotal = store.faceEmbeddingCount(engineId, engineFactory.modelVersion)
        if (store.isGroupingRequired(engineId, engineFactory.modelVersion, strategy.strategyVersion)) {
            store.markGroupingPending(engineId, engineFactory.modelVersion, strategy.strategyVersion, faceTotal)
            clusteringStage = StageProgress(StageState.RUNNING, 0, faceTotal, 0)
            groupingStatus = "Grouping people…"
            onProgress(FaceIndexingProgress(
                processed = processed, total = totalPending, facesFound = facesFound, photosWithFaces = photosWithFaces, failed = failed,
                currentItemName = lastCompletedItemName, currentItemStatus = lastCompletedItemStatus,
                groupingStatus = groupingStatus.orEmpty(), currentItemFaces = lastCompletedItemFaces,
                engineId = engineId, engineName = engineFactory.displayName,
                detectionStage = detectionStage, embeddingStage = embeddingStage,
                clusteringStage = clusteringStage, failedItems = failedList
            ))
            try {
                val groupedPeople = strategy.clusterFaces(
                    store = store, mediaUris = pendingUris, engineId = engineId,
                    modelVersion = engineFactory.modelVersion, embeddingDimension = actualDimension,
                    maxDistance = recognitionDistance,
                    onProgress = { done, total ->
                        val safeTotal = total.coerceAtLeast(0)
                        val safeDone = done.coerceIn(0, safeTotal)
                        val percentage = if (safeTotal > 0) safeDone * 100 / safeTotal else 100
                        clusteringStage = StageProgress(StageState.RUNNING, safeDone, safeTotal, percentage.coerceIn(0, 99))
                        groupingStatus = if (safeTotal > 0) "Grouping people… $safeDone/$safeTotal faces" else "Grouping people…"
                        runBlocking {
                            onProgress(FaceIndexingProgress(
                                processed = processed, total = totalPending, facesFound = facesFound, photosWithFaces = photosWithFaces, failed = failed,
                                currentItemName = lastCompletedItemName, currentItemStatus = lastCompletedItemStatus,
                                groupingStatus = groupingStatus.orEmpty(), currentItemFaces = lastCompletedItemFaces,
                                engineId = engineId, engineName = engineFactory.displayName,
                                detectionStage = detectionStage, embeddingStage = embeddingStage,
                                clusteringStage = clusteringStage, failedItems = failedList
                            ))
                        }
                    }
                )
                store.ensurePersonCovers(engineId)
                val finalCount = store.faceEmbeddingCount(engineId, engineFactory.modelVersion)
                store.markGroupingCompleted(engineId, engineFactory.modelVersion, strategy.strategyVersion, groupedPeople, finalCount)
                clusteringStage = StageProgress(StageState.COMPLETED, faceTotal, faceTotal, 100)
                val unassigned = store.unassignedFaceCount(engineId, engineFactory.modelVersion)
                groupingStatus = "Grouping complete · $groupedPeople people · $unassigned unassigned"
                FaceStoreEvents.notifyAllUpdated()
            } catch (cancelled: CancellationException) {
                runCatching {
                    store.markGroupingPending(engineId, engineFactory.modelVersion, strategy.strategyVersion,
                        store.faceEmbeddingCount(engineId, engineFactory.modelVersion))
                }
                FaceStoreEvents.notifyAllUpdated()
                throw cancelled
            } catch (err: Exception) {
                val detail = err.localizedMessage ?: err.message ?: "Unknown grouping error"
                runCatching {
                    store.markGroupingFailed(engineId, engineFactory.modelVersion, strategy.strategyVersion, detail,
                        store.faceEmbeddingCount(engineId, engineFactory.modelVersion))
                }
                FaceStoreEvents.notifyAllUpdated()
                clusteringStage = StageProgress(StageState.FAILED, clusteringStage.processed, faceTotal, clusteringStage.percentage)
                groupingStatus = "Grouping failed: $detail"
            }
            onProgress(FaceIndexingProgress(
                processed = processed, total = totalPending, facesFound = facesFound, photosWithFaces = photosWithFaces, failed = failed,
                currentItemName = lastCompletedItemName, currentItemStatus = lastCompletedItemStatus,
                groupingStatus = groupingStatus.orEmpty(), currentItemFaces = lastCompletedItemFaces,
                engineId = engineId, engineName = engineFactory.displayName,
                detectionStage = detectionStage, embeddingStage = embeddingStage,
                clusteringStage = clusteringStage, failedItems = failedList
            ))
        } else {
            clusteringStage = StageProgress(StageState.COMPLETED, faceTotal, faceTotal, 100)
        }
    } else if (totalPending > 0) {
        clusteringStage = StageProgress(StageState.COMPLETED, totalPending, totalPending, 100)
    }
    return GroupingPassOutcome(clusteringStage, groupingStatus)
}
