package com.amresalehin.emregallery.ui

import com.amresalehin.emregallery.face.FaceClusterSummary

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
    val totalDetected: Int = 0,
    val failedCount: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val itemsPerMinute: Int = 0,
    val detectionConfidence: Float = 0.60f
)

data class ModelDownloadState(
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false,
    val stage: String = "downloading",
    val bytesReceived: Long = 0L,
    val totalBytes: Long = 0L,
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
    val activeEngineName: String = "Detecting...",
    val primaryModelFailure: String? = null
)

data class IndexingStatusUiState(
    val totalPhotos: Int = 0,
    val countsReady: Boolean = false,
    val ocr: OcrState = OcrState(),
    val faceFinding: FaceFindingState = FaceFindingState(),
    val modelDownload: ModelDownloadState = ModelDownloadState(),
    val clustering: FaceClusteringState = FaceClusteringState(),
    val engine: EngineState = EngineState()
)
