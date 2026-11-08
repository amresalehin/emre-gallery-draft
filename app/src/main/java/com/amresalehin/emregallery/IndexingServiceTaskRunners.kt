package com.amresalehin.emregallery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.amresalehin.emregallery.data.MediaRepository
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.PreferencesRepository
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.BuffaloLFaceEngineFactory
import com.amresalehin.emregallery.face.FaceEngineRegistry
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceIndexingRepository
import com.amresalehin.emregallery.face.FaceProcessingCoordinator
import com.amresalehin.emregallery.face.FaceProcessingOperation
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


internal suspend fun IndexingForegroundService.runOcr(
    languages: List<String>,
    mlKitLanguages: List<String> = listOf("latin"),
    rescanAll: Boolean,
    modelId: String = "fast",
    engineId: String = "tesseract"
) {
    runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
    val isMlKit = engineId.equals("google_mlkit", ignoreCase = true) || engineId.equals("mlkit", ignoreCase = true)
    val model = com.amresalehin.emregallery.ocr.TesseractModel.fromId(modelId)
    val photos = loadPhotos()
    if (rescanAll) withContext(Dispatchers.IO) { TesseractOcr.clearCachedResults(this) }
    val pending = photos.filterNot { TesseractOcr.hasCachedResult(this, Uri.parse(it.uri)) }
    val initialPaused = prefs.getBoolean(KEY_PAUSED, false)
    pauseSignal.value = initialPaused
    updateState(task = TASK_OCR, running = true, paused = initialPaused, processed = photos.size - pending.size, total = photos.size, error = null)
    val engineLabel = if (isMlKit) {
        "Google ML Kit (${com.amresalehin.emregallery.ocr.MlKitLanguage.getDisplayLabel(mlKitLanguages)})"
    } else model.shortName
    postProgress("OCR indexing", "Preparing ${pending.size} images ($engineLabel)", photos.size - pending.size, photos.size)
    var completed = photos.size - pending.size
    // Unconstrained operation: do not artificially clamp cooldown to at least 30ms.
    // Uses the configured throttle cooldown (0ms for unconstrained/turbo).
    val cooldown = FaceRecognitionSettings.cpuThrottleCooldownMillis(this)
    for (item in pending) {
        awaitResume()
        currentCoroutineContext().ensureActive()
        try {
            val mediaUri = Uri.parse(item.uri)
            val text = if (isMlKit) {
                com.amresalehin.emregallery.ocr.MlKitOcr.recognize(this, mediaUri, mlKitLanguages)
            } else {
                TesseractOcr.recognize(this, mediaUri, languages, model)
            }
            TesseractOcr.saveCachedResult(this, mediaUri, text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Failed images are attempted once per run; successful results remain checkpointed.
        }
        completed++
        updateState(processed = completed, total = photos.size)
        if (completed % 4 == 0 || completed == photos.size) {
            val titleLabel = if (isMlKit) "Google ML Kit" else model.title
            postProgress("OCR indexing", "Processed $completed of ${photos.size} images ($titleLabel)", completed, photos.size)
        }
        if (cooldown > 0L) {
            delay(cooldown)
        }
        kotlinx.coroutines.yield()
    }
}

internal suspend fun IndexingForegroundService.runFace(
    rescanAll: Boolean,
    engineId: String = BuffaloLFaceEngine.ENGINE_ID,
    retryFailedOnly: Boolean = false,
    folderFilter: Set<String>? = null
) {
    runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
    val allPhotos = loadPhotos()
    val photos = if (folderFilter != null && folderFilter.isNotEmpty()) {
        allPhotos.filter { FaceRecognitionSettings.normalizeFolderPath(it.relativePath) in folderFilter }
    } else {
        FaceRecognitionSettings.filterMediaForFaceIndexing(applicationContext, allPhotos) { it.relativePath }
    }
    val store = FaceClusterStore(applicationContext)
    val factory = FaceEngineRegistry.getFactory(engineId) ?: BuffaloLFaceEngineFactory
    val serviceHandle = FaceProcessingCoordinator.beginOperation(
        operationId = "foreground_service_face_$engineId",
        type = if (rescanAll) FaceProcessingOperation.RESCANNING else FaceProcessingOperation.SCANNING,
        engineId = engineId,
        description = "Service Face Indexing (${factory.displayName})"
    )
    try {
        if (rescanAll) {
            withContext(Dispatchers.IO) {
                if (FaceRecognitionSettings.isIndexAllFolders(applicationContext) && (folderFilter == null || folderFilter.isEmpty())) {
                    // Reset indexed status for selected engine to reprocess photos without wiping identities
                    store.writableDatabase.delete("indexed_media", "engine_id=?", arrayOf(engineId))
                } else {
                    // Reset indexed status only for the scoped photos
                    for (p in photos) {
                        store.writableDatabase.delete("indexed_media", "media_uri=? AND engine_id=?", arrayOf(p.uri, engineId))
                    }
                }
            }
        }

        val targetUris = if (retryFailedOnly) {
            val failedUris = store.getFailedMedia(engineId).map { it.first }.toSet()
            photos.map { it.uri }.filter { it in failedUris }.toSet()
        } else null

        val relevantPhotos = if (targetUris != null) photos.filter { it.uri in targetUris } else photos
        val alreadyDone = if (retryFailedOnly) 0 else store.indexedMediaCount(relevantPhotos.map { it.uri }, engineId = engineId)
        val initialPaused = prefs.getBoolean(KEY_PAUSED, false)
        pauseSignal.value = initialPaused

        updateState(
            task = TASK_FACE,
            running = true,
            paused = initialPaused,
            processed = alreadyDone,
            total = relevantPhotos.size,
            faces = store.indexedFaceCount(engineId),
            failed = store.failedMediaCount(relevantPhotos.map { it.uri }, engineId),
            photosWithFaces = store.mediaWithFacesCount(relevantPhotos.map { it.uri }, engineId),
        engineId = engineId,
        currentItemName = "Initializing ${factory.displayName}…",
        currentItemStatus = "",
        groupingStatus = "",
        detectionState = "PENDING",
        detectionProcessed = 0,
        detectionTotal = relevantPhotos.size,
        embeddingState = "PENDING",
        embeddingProcessed = 0,
        embeddingTotal = relevantPhotos.size,
        clusteringState = "PENDING",
        clusteringProcessed = 0,
        clusteringTotal = relevantPhotos.size,
        error = null
    )
    postProgress("Face indexing", "Preparing ${factory.displayName}", alreadyDone, relevantPhotos.size)

    FaceIndexingRepository(applicationContext, factory).indexNewMedia(
        media = relevantPhotos,
        store = store,
        detectionThreshold = FaceRecognitionSettings.detectionConfidence(applicationContext, engineId),
        recognitionDistance = FaceRecognitionSettings.recognitionDistance(applicationContext, engineId),
        forceRescanUris = if (retryFailedOnly) targetUris ?: emptySet() else emptySet(),
        targetUrisOnly = targetUris,
        awaitIfResumed = { awaitResume(); currentCoroutineContext().ensureActive() },
        onProgress = { p ->
            val done = (alreadyDone + p.processed).coerceAtMost(relevantPhotos.size)
            updateState(
                processed = done,
                total = relevantPhotos.size,
                faces = store.indexedFaceCount(engineId),
                failed = store.failedMediaCount(relevantPhotos.map { it.uri }, engineId),
                photosWithFaces = p.photosWithFaces,
                engineId = engineId,
                currentItemName = p.currentItemName,
                currentItemStatus = p.currentItemStatus,
                groupingStatus = p.groupingStatus,
                detectionState = p.detectionStage.state.name,
                detectionProcessed = p.detectionStage.processed,
                detectionTotal = p.detectionStage.total,
                embeddingState = p.embeddingStage.state.name,
                embeddingProcessed = p.embeddingStage.processed,
                embeddingTotal = p.embeddingStage.total,
                clusteringState = p.clusteringStage.state.name,
                clusteringProcessed = p.clusteringStage.processed,
                clusteringTotal = p.clusteringStage.total
            )
            if (done % 3 == 0 || done == relevantPhotos.size) {
                val targetName = if (p.currentItemName.isNotBlank()) " · ${p.currentItemName}" else ""
                val statusSnippet = if (p.currentItemStatus.isNotBlank()) " (${p.currentItemStatus})" else ""
                val groupingSnippet = if (p.groupingStatus.isNotBlank()) " · ${p.groupingStatus}" else ""
                postProgress("Face indexing", "Processed $done of ${relevantPhotos.size} images · ${store.indexedFaceCount(engineId)} faces$targetName$statusSnippet$groupingSnippet", done, relevantPhotos.size)
            }
        }
    )
    } finally {
        serviceHandle.close()
    }
}

