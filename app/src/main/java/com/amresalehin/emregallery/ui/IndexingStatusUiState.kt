package com.amresalehin.emregallery.ui

import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.FaceClusterSummary

import com.amresalehin.emregallery.face.StageProgress
import com.amresalehin.emregallery.face.StageState

data class OcrState(
    val processed: Int = 0,
    val cachedCount: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val itemsPerMinute: Int = 0,
    val model: String = "fast"
)

data class FaceFindingState(
    val processed: Int = 0,
    val successfulCount: Int = 0,
    val totalDetected: Int = 0,
    val failedCount: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val itemsPerMinute: Int = 0,
    val detectionConfidence: Float = 0.60f,
    val currentItemName: String = "",
    val currentItemStatus: String = "",
    val groupingStatus: String = "",
    val activeEngineId: String = BuffaloLFaceEngine.ENGINE_ID,
    val detectionStage: StageProgress = StageProgress(),
    val embeddingStage: StageProgress = StageProgress(),
    val clusteringStage: StageProgress = StageProgress(),
    val failedItems: List<Pair<String, String>> = emptyList()
)

data class LocationIndexingState(
    val processed: Int = 0,
    val locatedCount: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val itemsPerMinute: Int = 0
)

data class ModelDownloadState(
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false,
    val stage: String = "downloading",
    val bytesReceived: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val engineId: String = BuffaloLFaceEngine.ENGINE_ID,
    val error: String? = null
)

data class FaceClusteringState(
    val summaries: List<FaceClusterSummary> = emptyList(),
    val minGroupFaces: Int = 3,
    val clusterDistance: Float = 0.50f,
    val isClusteringRunning: Boolean = false,
    val statusMessage: String? = null
)

data class EngineState(
    val isBuffaloLInstalled: Boolean = false,
    val isEnteInstalled: Boolean = false,
    val activeEngineName: String = "Detecting...",
    val activeEngineId: String = BuffaloLFaceEngine.ENGINE_ID,
    val primaryModelFailure: String? = null,
    val enteModelFailure: String? = null
)

data class IndexingStatusUiState(
    val totalPhotos: Int = 0,
    val countsReady: Boolean = false,
    val ocr: OcrState = OcrState(),
    val faceFinding: FaceFindingState = FaceFindingState(),
    val location: LocationIndexingState = LocationIndexingState(),
    val modelDownload: ModelDownloadState = ModelDownloadState(),
    val clustering: FaceClusteringState = FaceClusteringState(),
    val enteFaceFinding: FaceFindingState = FaceFindingState(detectionConfidence = EnteFaceConstants.DEFAULT_DETECTION_CONFIDENCE),
    val enteModelDownload: ModelDownloadState = ModelDownloadState(),
    val enteClustering: FaceClusteringState = FaceClusteringState(clusterDistance = EnteFaceConstants.DEFAULT_RECOGNITION_DISTANCE),
    val engine: EngineState = EngineState()
)


/**
 * A scan is successful only when every requested photo was processed, none failed,
 * and the grouping stage did not report a failure. "Processed" alone is not success.
 */
internal fun isSuccessfulFaceIndexingCompletion(
    modelInstalled: Boolean,
    isRunning: Boolean,
    isClusteringActive: Boolean,
    totalPhotos: Int,
    processed: Int,
    failedCount: Int,
    clusteringStage: StageProgress,
    groupingStatus: String,
    clusteringStatusMessage: String?
): Boolean {
    if (!modelInstalled || isRunning || isClusteringActive || totalPhotos <= 0 || processed < totalPhotos) {
        return false
    }
    if (failedCount > 0 || clusteringStage.state == StageState.FAILED) return false
    if (groupingStatus.startsWith("Grouping failed", ignoreCase = true)) return false
    val clusterMessage = clusteringStatusMessage.orEmpty()
    if (clusterMessage.startsWith("Clustering error", ignoreCase = true) ||
        clusterMessage.startsWith("Clustering failed", ignoreCase = true)
    ) return false
    return true
}
