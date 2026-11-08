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


internal fun IndexingStatusViewModel.updateEngineStateImpl() {
    val (buffaloName, buffaloFailure) = FaceIndexingRepository.queryEngineInfo(context, BuffaloLFaceEngineFactory)
    val (_, enteFailure) = FaceIndexingRepository.queryEngineInfo(context, EnteFaceEngineFactory)
    val buffaloInstalled = BuffaloLFaceEngine.isModelInstalled(context)
    val enteInstalled = EnteFaceModelManager.isInstalled(context)

    _uiState.update { state ->
        state.copy(
            engine = EngineState(
                isBuffaloLInstalled = buffaloInstalled,
                isEnteInstalled = enteInstalled,
                activeEngineName = buffaloName,
                activeEngineId = FaceEngineRegistry.activeId,
                primaryModelFailure = buffaloFailure,
                enteModelFailure = enteFailure
            )
        )
    }
}

internal fun IndexingStatusViewModel.refreshCountsImpl() {
    updateEngineState()
    viewModelScope.launch {
        _uiState.update { it.copy(countsReady = false) }
        val photos = currentPhotos
        val isAll = FaceRecognitionSettings.isIndexAllFolders(context)
        val selectedFolders = FaceRecognitionSettings.getSelectedFolders(context)
        val facePhotos = FaceRecognitionSettings.filterMediaForFaceIndexing(context, photos) { it.relativePath }
        val facePhotoUris = facePhotos.map { it.uri }

        val result = withContext(Dispatchers.IO) {
            val oCount = photos.count { item ->
                TesseractOcr.hasCachedResult(context, Uri.parse(item.uri))
            }
            val oFiles = TesseractOcr.cachedResultCount(context)

            // Buffalo_L counts (scoped to chosen folders)
            val bCount = faceStore.indexedMediaCount(facePhotoUris, BuffaloLFaceEngine.ENGINE_ID)
            val bWithFaces = faceStore.mediaWithFacesCount(facePhotoUris, BuffaloLFaceEngine.ENGINE_ID)
            val bFaces = faceStore.indexedFaceCount(BuffaloLFaceEngine.ENGINE_ID)
            val bFailures = faceStore.failedMediaCount(facePhotoUris, BuffaloLFaceEngine.ENGINE_ID)
            val bSummaries = faceStore.summaries(BuffaloLFaceEngine.ENGINE_ID)
            val bFailedList = faceStore.getFailedMedia(facePhotoUris, BuffaloLFaceEngine.ENGINE_ID).map { it.first to (it.second ?: "Detection failed") }

            // Ente-Style counts (scoped to chosen folders)
            val eCount = faceStore.indexedMediaCount(facePhotoUris, EnteFaceConstants.ENGINE_ID)
            val eWithFaces = faceStore.mediaWithFacesCount(facePhotoUris, EnteFaceConstants.ENGINE_ID)
            val eFaces = faceStore.indexedFaceCount(EnteFaceConstants.ENGINE_ID)
            val eFailures = faceStore.failedMediaCount(facePhotoUris, EnteFaceConstants.ENGINE_ID)
            val eSummaries = faceStore.summaries(EnteFaceConstants.ENGINE_ID)
            val eFailedList = faceStore.getFailedMedia(facePhotoUris, EnteFaceConstants.ENGINE_ID).map { it.first to (it.second ?: "Detection failed") }

            val locCache = com.amresalehin.emregallery.data.PhotoMapLocationCache(context.applicationContext)
            val locMap = locCache.loadAll()
            val lProcessed = photos.count { locMap.containsKey(it.uri) }
            val lLocated = locMap.values.count { it.point != null }

            FullCountsResult(
                oCount, oFiles,
                bCount, bWithFaces, bFaces, bFailures, bSummaries, bFailedList,
                eCount, eWithFaces, eFaces, eFailures, eSummaries, eFailedList,
                lProcessed, lLocated
            )
        }

        _uiState.update { state ->
            val totalPhotos = photos.size
            val scopedFaceTotal = facePhotos.size
            state.copy(
                countsReady = true,
                totalPhotos = totalPhotos,
                ocr = state.ocr.copy(
                    processed = result.ocrProcessed.coerceIn(0, totalPhotos),
                    cachedCount = result.ocrCached
                ),
                faceFinding = state.faceFinding.copy(
                    processed = (result.buffaloProcessed + result.buffaloFailures).coerceIn(0, scopedFaceTotal),
                    successfulCount = result.buffaloWithFaces.coerceIn(0, scopedFaceTotal),
                    totalDetected = result.buffaloDetected,
                    failedCount = result.buffaloFailures,
                    failedItems = result.buffaloFailedItems,
                    isAllFolders = isAll,
                    selectedFolders = selectedFolders,
                    scopedTotalPhotos = scopedFaceTotal
                ),
                clustering = state.clustering.copy(
                    summaries = result.buffaloSummaries
                ),
                enteFaceFinding = state.enteFaceFinding.copy(
                    processed = (result.enteProcessed + result.enteFailures).coerceIn(0, scopedFaceTotal),
                    successfulCount = result.enteWithFaces.coerceIn(0, scopedFaceTotal),
                    totalDetected = result.enteDetected,
                    failedCount = result.enteFailures,
                    failedItems = result.enteFailedItems,
                    isAllFolders = isAll,
                    selectedFolders = selectedFolders,
                    scopedTotalPhotos = scopedFaceTotal
                ),
                enteClustering = state.enteClustering.copy(
                    summaries = result.enteSummaries
                ),
                location = state.location.copy(
                    processed = result.locProcessed.coerceIn(0, totalPhotos),
                    locatedCount = result.locLocated
                )
            )
        }
    }
}

internal data class FullCountsResult(
    val ocrProcessed: Int,
    val ocrCached: Int,
    val buffaloProcessed: Int,
    val buffaloWithFaces: Int,
    val buffaloDetected: Int,
    val buffaloFailures: Int,
    val buffaloSummaries: List<com.amresalehin.emregallery.face.FaceClusterSummary>,
    val buffaloFailedItems: List<Pair<String, String>>,
    val enteProcessed: Int,
    val enteWithFaces: Int,
    val enteDetected: Int,
    val enteFailures: Int,
    val enteSummaries: List<com.amresalehin.emregallery.face.FaceClusterSummary>,
    val enteFailedItems: List<Pair<String, String>>,
    val locProcessed: Int,
    val locLocated: Int
)

internal fun IndexingStatusViewModel.retryFailedFacesImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
    if (engineId == EnteFaceConstants.ENGINE_ID) {
        _uiState.update { it.copy(enteFaceFinding = it.enteFaceFinding.copy(isRunning = true, isPaused = false, error = null)) }
    } else {
        _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isRunning = true, isPaused = false, error = null)) }
    }
    IndexingForegroundService.retryFailedFaces(context, engineId)
}

