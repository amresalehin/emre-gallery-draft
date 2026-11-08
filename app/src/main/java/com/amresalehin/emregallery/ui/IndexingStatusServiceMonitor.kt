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


internal fun IndexingStatusViewModel.startServiceMonitoring() {
    viewModelScope.launch {
        IndexingForegroundService.status(context)
        var wasRunning = false
        while (isActive) {
            val prefs = context.getSharedPreferences(
                IndexingForegroundService.STATE_PREFS,
                Context.MODE_PRIVATE
            )
            val task = prefs.getString(IndexingForegroundService.KEY_TASK, "") ?: ""
            val running = prefs.getBoolean(IndexingForegroundService.KEY_RUNNING, false)
            val paused = prefs.getBoolean(IndexingForegroundService.KEY_PAUSED, false)
            val processed = prefs.getInt(IndexingForegroundService.KEY_PROCESSED, 0)
            val total = prefs.getInt(IndexingForegroundService.KEY_TOTAL, 0)
            val faces = prefs.getInt(IndexingForegroundService.KEY_FACES, -1)
            val failed = prefs.getInt(IndexingForegroundService.KEY_FAILED, -1)
            val photosWithFaces = prefs.getInt(IndexingForegroundService.KEY_PHOTOS_WITH_FACES, -1)
            val stage = prefs.getString(IndexingForegroundService.KEY_STAGE, "downloading") ?: "downloading"
            val bytes = prefs.getLong(IndexingForegroundService.KEY_BYTES, 0L)
            val bytesTotal = prefs.getLong(IndexingForegroundService.KEY_BYTES_TOTAL, 0L)
            val speed = prefs.getLong(IndexingForegroundService.KEY_SPEED, 0L)
            val eta = prefs.getLong(IndexingForegroundService.KEY_ETA, 0L)
            val error = prefs.getString(IndexingForegroundService.KEY_ERROR, null)
            val engineId = prefs.getString(IndexingForegroundService.KEY_ENGINE_ID, BuffaloLFaceEngine.ENGINE_ID) ?: BuffaloLFaceEngine.ENGINE_ID
            val currentItem = prefs.getString(IndexingForegroundService.KEY_CURRENT_ITEM_NAME, "") ?: ""
            val currentStatus = prefs.getString(IndexingForegroundService.KEY_CURRENT_ITEM_STATUS, "") ?: ""
            val groupingStatus = prefs.getString(IndexingForegroundService.KEY_GROUPING_STATUS, "") ?: ""

            val detStateStr = prefs.getString(IndexingForegroundService.KEY_DETECTION_STATE, "PENDING") ?: "PENDING"
            val detProcessed = prefs.getInt(IndexingForegroundService.KEY_DETECTION_PROCESSED, 0)
            val detTotal = prefs.getInt(IndexingForegroundService.KEY_DETECTION_TOTAL, 0)

            val embStateStr = prefs.getString(IndexingForegroundService.KEY_EMBEDDING_STATE, "PENDING") ?: "PENDING"
            val embProcessed = prefs.getInt(IndexingForegroundService.KEY_EMBEDDING_PROCESSED, 0)
            val embTotal = prefs.getInt(IndexingForegroundService.KEY_EMBEDDING_TOTAL, 0)

            val cluStateStr = prefs.getString(IndexingForegroundService.KEY_CLUSTERING_STATE, "PENDING") ?: "PENDING"
            val cluProcessed = prefs.getInt(IndexingForegroundService.KEY_CLUSTERING_PROCESSED, 0)
            val cluTotal = prefs.getInt(IndexingForegroundService.KEY_CLUSTERING_TOTAL, 0)

            fun parseStage(stateStr: String, proc: Int, tot: Int): com.amresalehin.emregallery.face.StageProgress {
                val s = runCatching { com.amresalehin.emregallery.face.StageState.valueOf(stateStr) }
                    .getOrDefault(com.amresalehin.emregallery.face.StageState.PENDING)
                val pct = if (tot > 0) (proc * 100 / tot).coerceIn(0, 100) else if (s == com.amresalehin.emregallery.face.StageState.COMPLETED) 100 else 0
                return com.amresalehin.emregallery.face.StageProgress(s, proc, tot, pct)
            }

            val detStage = parseStage(detStateStr, detProcessed, detTotal)
            val embStage = parseStage(embStateStr, embProcessed, embTotal)
            val cluStage = parseStage(cluStateStr, cluProcessed, cluTotal)

            _uiState.update { state ->
                var newOcr = state.ocr
                var newFace = state.faceFinding
                var newEnteFace = state.enteFaceFinding
                var newLocation = state.location
                var newDownload = state.modelDownload
                var newEnteDownload = state.enteModelDownload

                when (task) {
                    "ocr" -> {
                        val ipm = if (running && !paused) {
                            calculateRate(processed - ocrStartProcessed, ocrStartTimeMillis)
                        } else 0
                        newOcr = newOcr.copy(
                            isRunning = running,
                            isPaused = paused,
                            processed = if (total > 0) processed else newOcr.processed,
                            itemsPerMinute = ipm
                        )
                    }
                    "face" -> {
                        if (engineId == EnteFaceConstants.ENGINE_ID) {
                            val ipm = if (running && !paused) {
                                calculateRate(processed - enteFaceStartProcessed, enteFaceStartTimeMillis)
                            } else 0
                            val effectiveProc = if (total > 0) processed else newEnteFace.processed
                            val effectiveFailed = if (failed >= 0) failed else newEnteFace.failedCount
                            val effectiveSucc = if (photosWithFaces >= 0) photosWithFaces else newEnteFace.successfulCount
                            newEnteFace = newEnteFace.copy(
                                isRunning = running,
                                isPaused = paused,
                                processed = effectiveProc,
                                successfulCount = effectiveSucc,
                                totalDetected = if (faces >= 0) faces else newEnteFace.totalDetected,
                                failedCount = effectiveFailed,
                                error = error,
                                itemsPerMinute = ipm,
                                currentItemName = currentItem,
                                currentItemStatus = currentStatus,
                                groupingStatus = groupingStatus,
                                activeEngineId = engineId,
                                detectionStage = detStage,
                                embeddingStage = embStage,
                                clusteringStage = cluStage
                            )
                        } else {
                            val ipm = if (running && !paused) {
                                calculateRate(processed - faceStartProcessed, faceStartTimeMillis)
                            } else 0
                            val effectiveProc = if (total > 0) processed else newFace.processed
                            val effectiveFailed = if (failed >= 0) failed else newFace.failedCount
                            val effectiveSucc = if (photosWithFaces >= 0) photosWithFaces else newFace.successfulCount
                            newFace = newFace.copy(
                                isRunning = running,
                                isPaused = paused,
                                processed = effectiveProc,
                                successfulCount = effectiveSucc,
                                totalDetected = if (faces >= 0) faces else newFace.totalDetected,
                                failedCount = effectiveFailed,
                                error = error,
                                itemsPerMinute = ipm,
                                currentItemName = currentItem,
                                currentItemStatus = currentStatus,
                                groupingStatus = groupingStatus,
                                activeEngineId = engineId,
                                detectionStage = detStage,
                                embeddingStage = embStage,
                                clusteringStage = cluStage
                            )
                        }
                    }
                    "model" -> {
                        if (engineId == EnteFaceConstants.ENGINE_ID) {
                            newEnteDownload = newEnteDownload.copy(
                                isDownloading = running,
                                isPaused = paused,
                                stage = stage,
                                bytesReceived = bytes,
                                totalBytes = bytesTotal,
                                speedBytesPerSec = speed,
                                etaSeconds = eta,
                                engineId = engineId,
                                error = error
                            )
                        } else {
                            newDownload = newDownload.copy(
                                isDownloading = running,
                                isPaused = paused,
                                stage = stage,
                                bytesReceived = bytes,
                                totalBytes = bytesTotal,
                                speedBytesPerSec = speed,
                                etaSeconds = eta,
                                engineId = engineId,
                                error = error
                            )
                        }
                    }
                    "location" -> {
                        val ipm = if (running && !paused) {
                            calculateRate(processed - locationStartProcessed, locationStartTimeMillis)
                        } else 0
                        newLocation = newLocation.copy(
                            isRunning = running,
                            isPaused = paused,
                            processed = if (total > 0) processed else newLocation.processed,
                            locatedCount = if (faces >= 0) faces else newLocation.locatedCount,
                            itemsPerMinute = ipm
                        )
                    }
                }

                if (!running) {
                    newOcr = newOcr.copy(isRunning = false, isPaused = false, itemsPerMinute = 0)
                    newFace = newFace.copy(isRunning = false, isPaused = false, itemsPerMinute = 0, currentItemName = "")
                    newEnteFace = newEnteFace.copy(isRunning = false, isPaused = false, itemsPerMinute = 0, currentItemName = "")
                    newLocation = newLocation.copy(isRunning = false, isPaused = false, itemsPerMinute = 0)
                    newDownload = newDownload.copy(isDownloading = false, isPaused = false)
                    newEnteDownload = newEnteDownload.copy(isDownloading = false, isPaused = false)
                }

                state.copy(
                    ocr = newOcr,
                    faceFinding = newFace,
                    enteFaceFinding = newEnteFace,
                    location = newLocation,
                    modelDownload = newDownload,
                    enteModelDownload = newEnteDownload
                )
            }

            if (wasRunning && !running) {
                refreshCounts()
            }
            wasRunning = running
            delay(1000)
        }
    }
}

internal fun IndexingStatusViewModel.calculateRate(items: Int, startTimeMillis: Long): Int {
    if (items <= 0 || startTimeMillis <= 0L) return 0
    val elapsedSec = (System.currentTimeMillis() - startTimeMillis) / 1000f
    if (elapsedSec < 3f) return 0
    return ((items / elapsedSec) * 60f).roundToInt()
}

