package com.amresalehin.emregallery.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amresalehin.emregallery.IndexingForegroundService
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.BuffaloLFaceEngineFactory
import com.amresalehin.emregallery.face.EnteFaceClusteringStrategy
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.EnteFaceEngineFactory
import com.amresalehin.emregallery.face.EnteFaceModelManager
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceEngineRegistry
import com.amresalehin.emregallery.face.FaceIndexingRepository
import com.amresalehin.emregallery.face.FaceStoreEvents
import com.amresalehin.emregallery.face.StageProgress
import com.amresalehin.emregallery.face.StageState
import com.amresalehin.emregallery.face.FaceProcessingCoordinator
import com.amresalehin.emregallery.face.FaceProcessingOperation
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt


// --- Tuning Settings ---

internal fun IndexingStatusViewModel.updateDetectionConfidenceImpl(confidence: Float, engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
    val rounded = (confidence * 100).roundToInt() / 100f
    FaceRecognitionSettings.setDetectionConfidence(context, engineId, rounded)
    _uiState.update {
        if (engineId == EnteFaceConstants.ENGINE_ID) {
            it.copy(enteFaceFinding = it.enteFaceFinding.copy(detectionConfidence = rounded))
        } else {
            it.copy(faceFinding = it.faceFinding.copy(detectionConfidence = rounded))
        }
    }
}

internal fun IndexingStatusViewModel.updateMinGroupFacesImpl(minFaces: Int, engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
    val coerced = minFaces.coerceIn(1, 30)
    FaceRecognitionSettings.setMinGroupFaces(context, engineId, coerced)
    _uiState.update {
        if (engineId == EnteFaceConstants.ENGINE_ID) {
            it.copy(enteClustering = it.enteClustering.copy(minGroupFaces = coerced))
        } else {
            it.copy(clustering = it.clustering.copy(minGroupFaces = coerced))
        }
    }
}

internal fun IndexingStatusViewModel.updateClusterDistanceImpl(distance: Float, engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
    val rounded = (distance * 100).roundToInt() / 100f
    FaceRecognitionSettings.setRecognitionDistance(context, engineId, rounded)
    _uiState.update {
        if (engineId == EnteFaceConstants.ENGINE_ID) {
            it.copy(enteClustering = it.enteClustering.copy(clusterDistance = rounded))
        } else {
            it.copy(clustering = it.clustering.copy(clusterDistance = rounded))
        }
    }
}

// --- Location Actions ---

internal fun IndexingStatusViewModel.toggleLocationIndexingImpl() {
    val state = _uiState.value
    if (!state.countsReady || currentPhotos.isEmpty()) return
    if (state.location.isRunning) {
        val paused = !state.location.isPaused
        _uiState.update { it.copy(location = it.location.copy(isPaused = paused)) }
        if (paused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
        return
    }
    if (state.ocr.isRunning || state.faceFinding.isRunning || state.enteFaceFinding.isRunning ||
        state.modelDownload.isDownloading || state.enteModelDownload.isDownloading
    ) return
    if (state.location.processed >= currentPhotos.size) return

    locationStartTimeMillis = System.currentTimeMillis()
    locationStartProcessed = state.location.processed
    _uiState.update { it.copy(location = it.location.copy(isRunning = true, isPaused = false)) }
    IndexingForegroundService.startLocation(context, rescanAll = false)
}

internal fun IndexingStatusViewModel.stopLocationIndexingImpl() {
    IndexingForegroundService.cancel(context)
    _uiState.update { it.copy(location = it.location.copy(isRunning = false, isPaused = false)) }
}

internal fun IndexingStatusViewModel.clearAndReindexLocationImpl() {
    locationStartTimeMillis = System.currentTimeMillis()
    locationStartProcessed = 0
    _uiState.update { it.copy(location = it.location.copy(isRunning = true, isPaused = false, processed = 0, locatedCount = 0)) }
    IndexingForegroundService.startLocation(context, rescanAll = true)
}

// --- OCR Actions ---

internal fun IndexingStatusViewModel.toggleOcrImpl() {
    val state = _uiState.value
    if (!state.countsReady || currentPhotos.isEmpty()) return
    if (state.ocr.isRunning) {
        val paused = !state.ocr.isPaused
        _uiState.update { it.copy(ocr = it.ocr.copy(isPaused = paused)) }
        if (paused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
        return
    }
    if (state.location.isRunning || state.faceFinding.isRunning || state.enteFaceFinding.isRunning ||
        state.modelDownload.isDownloading || state.enteModelDownload.isDownloading
    ) return
    if (state.ocr.processed >= currentPhotos.size) return

    ocrStartTimeMillis = System.currentTimeMillis()
    ocrStartProcessed = state.ocr.processed
    _uiState.update { it.copy(ocr = it.ocr.copy(isRunning = true, isPaused = false)) }
    IndexingForegroundService.startOcr(
        context = context,
        languages = currentOcrLanguages.toList(),
        rescanAll = false,
        modelId = currentOcrModel,
        engineId = currentOcrEngine,
        mlKitLanguages = currentMlKitLanguages.toList()
    )
}

internal fun IndexingStatusViewModel.stopOcrImpl() {
    IndexingForegroundService.cancel(context)
    _uiState.update { it.copy(ocr = it.ocr.copy(isRunning = false, isPaused = false)) }
}

internal fun IndexingStatusViewModel.clearAndReindexOcrImpl() {
    ocrStartTimeMillis = System.currentTimeMillis()
    ocrStartProcessed = 0
    _uiState.update { it.copy(ocr = it.ocr.copy(isRunning = true, isPaused = false, processed = 0)) }
    IndexingForegroundService.startOcr(
        context = context,
        languages = currentOcrLanguages.toList(),
        rescanAll = true,
        modelId = currentOcrModel,
        engineId = currentOcrEngine,
        mlKitLanguages = currentMlKitLanguages.toList()
    )
}
