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


// --- Face Finding Actions (Scoped by Engine) ---

internal fun IndexingStatusViewModel.toggleFaceFindingImpl(
    onNeedsModelDownload: (rescanAll: Boolean) -> Unit,
    engineId: String = BuffaloLFaceEngine.ENGINE_ID
) {
    val state = _uiState.value
    if (!state.countsReady || currentPhotos.isEmpty()) return

    val targetState = if (engineId == EnteFaceConstants.ENGINE_ID) state.enteFaceFinding else state.faceFinding
    val isInstalled = if (engineId == EnteFaceConstants.ENGINE_ID) state.engine.isEnteInstalled else state.engine.isBuffaloLInstalled

    if (targetState.isRunning) {
        val paused = !targetState.isPaused
        if (engineId == EnteFaceConstants.ENGINE_ID) {
            _uiState.update { it.copy(enteFaceFinding = it.enteFaceFinding.copy(isPaused = paused)) }
        } else {
            _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isPaused = paused)) }
        }
        if (paused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
        return
    }

    // Single heavy task constraint: do not run face finding if another heavy job is active
    if (state.ocr.isRunning || state.modelDownload.isDownloading || state.enteModelDownload.isDownloading ||
        state.faceFinding.isRunning || state.enteFaceFinding.isRunning
    ) return

    val scopedFacePhotos = FaceRecognitionSettings.filterMediaForFaceIndexing(context, currentPhotos) { it.relativePath }
    val effectiveFaceTotal = scopedFacePhotos.size

    if (targetState.processed >= effectiveFaceTotal && effectiveFaceTotal > 0) {
        val modelVersion = if (engineId == EnteFaceConstants.ENGINE_ID) EnteFaceConstants.MODEL_VERSION else BuffaloLFaceEngine.MODEL_VERSION
        val strategyVersion = if (engineId == EnteFaceConstants.ENGINE_ID) EnteFaceClusteringStrategy.strategyVersion else MANUAL_CLUSTERING_STRATEGY_VERSION
        if (faceStore.isGroupingRequired(engineId, modelVersion, strategyVersion)) {
            val dist = if (engineId == EnteFaceConstants.ENGINE_ID) state.enteClustering.clusterDistance else state.clustering.clusterDistance
            runFaceClustering(dist, engineId, force = true)
        } else {
            clearAndRescanFaces(onNeedsModelDownload = { onNeedsModelDownload(true) }, engineId = engineId)
        }
        return
    }

    if (!isInstalled) {
        rescanAllRequested = false
        onNeedsModelDownload(false)
        return
    }

    if (engineId == EnteFaceConstants.ENGINE_ID) {
        enteFaceStartTimeMillis = System.currentTimeMillis()
        enteFaceStartProcessed = state.enteFaceFinding.processed
        _uiState.update { it.copy(enteFaceFinding = it.enteFaceFinding.copy(isRunning = true, isPaused = false)) }
    } else {
        faceStartTimeMillis = System.currentTimeMillis()
        faceStartProcessed = state.faceFinding.processed
        _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isRunning = true, isPaused = false)) }
    }
    IndexingForegroundService.startFace(context, rescanAll = false, engineId = engineId)
}

internal fun IndexingStatusViewModel.setFaceIndexingFoldersImpl(allFolders: Boolean, folders: Set<String>) {
    FaceRecognitionSettings.setIndexAllFolders(context, allFolders)
    FaceRecognitionSettings.setSelectedFolders(context, folders)
    refreshCounts()
}

internal fun IndexingStatusViewModel.stopFaceFindingImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
    IndexingForegroundService.cancel(context)
    if (engineId == EnteFaceConstants.ENGINE_ID) {
        _uiState.update { it.copy(enteFaceFinding = it.enteFaceFinding.copy(isRunning = false, isPaused = false, itemsPerMinute = 0)) }
    } else {
        _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isRunning = false, isPaused = false, itemsPerMinute = 0)) }
    }
    refreshCounts()
}

internal fun IndexingStatusViewModel.clearAndRescanFacesImpl(
    onNeedsModelDownload: () -> Unit,
    engineId: String = BuffaloLFaceEngine.ENGINE_ID
) {
    val state = _uiState.value
    if (state.ocr.isRunning || state.faceFinding.isRunning || state.enteFaceFinding.isRunning ||
        state.modelDownload.isDownloading || state.enteModelDownload.isDownloading
    ) return

    val isInstalled = if (engineId == EnteFaceConstants.ENGINE_ID) state.engine.isEnteInstalled else state.engine.isBuffaloLInstalled
    if (!isInstalled) {
        rescanAllRequested = true
        onNeedsModelDownload()
        return
    }

    if (engineId == EnteFaceConstants.ENGINE_ID) {
        enteFaceStartTimeMillis = System.currentTimeMillis()
        enteFaceStartProcessed = 0
        _uiState.update { it.copy(enteFaceFinding = it.enteFaceFinding.copy(isRunning = true, isPaused = false)) }
    } else {
        faceStartTimeMillis = System.currentTimeMillis()
        faceStartProcessed = 0
        _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isRunning = true, isPaused = false)) }
    }
    IndexingForegroundService.startFace(context, rescanAll = true, engineId = engineId)
}

internal fun IndexingStatusViewModel.startModelDownloadImpl(
    force: Boolean = false,
    engineId: String = BuffaloLFaceEngine.ENGINE_ID
) {
    val state = _uiState.value
    if (state.modelDownload.isDownloading || state.enteModelDownload.isDownloading ||
        state.ocr.isRunning || state.faceFinding.isRunning || state.enteFaceFinding.isRunning
    ) return

    if (engineId == EnteFaceConstants.ENGINE_ID) {
        _uiState.update {
            it.copy(
                enteModelDownload = ModelDownloadState(
                    isDownloading = true,
                    isPaused = false,
                    stage = "downloading",
                    bytesReceived = 0L,
                    totalBytes = 0L,
                    error = null
                )
            )
        }
    } else {
        _uiState.update {
            it.copy(
                modelDownload = ModelDownloadState(
                    isDownloading = true,
                    isPaused = false,
                    stage = "downloading",
                    bytesReceived = 0L,
                    totalBytes = 0L,
                    error = null
                )
            )
        }
    }
    IndexingForegroundService.downloadModel(context, force, engineId = engineId)
}

internal fun IndexingStatusViewModel.cancelModelDownloadImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
    IndexingForegroundService.cancel(context)
    if (engineId == EnteFaceConstants.ENGINE_ID) {
        _uiState.update {
            it.copy(enteModelDownload = it.enteModelDownload.copy(isDownloading = false, isPaused = false))
        }
    } else {
        _uiState.update {
            it.copy(modelDownload = it.modelDownload.copy(isDownloading = false, isPaused = false))
        }
    }
}

