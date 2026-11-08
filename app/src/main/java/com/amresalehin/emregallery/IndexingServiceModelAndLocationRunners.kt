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


internal suspend fun IndexingForegroundService.downloadModel(force: Boolean, engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
    val factory = FaceEngineRegistry.getFactory(engineId) ?: BuffaloLFaceEngineFactory
    val serviceHandle = FaceProcessingCoordinator.beginOperation(
        operationId = "foreground_service_model_$engineId",
        type = FaceProcessingOperation.MODEL_DOWNLOAD,
        engineId = engineId,
        description = "Service Model Download (${factory.displayName})"
    )
    try {
        updateState(
            task = TASK_MODEL,
            running = true,
            paused = false,
            processed = 0,
            total = 100,
            bytes = 0L,
            bytesTotal = 0L,
            speed = 0L,
            eta = 0L,
            engineId = engineId,
            error = null
        )
        postProgress("Downloading face model", "Starting download for ${factory.displayName}", 0, 100)

        FaceIndexingRepository(applicationContext, factory).downloadModel(
            force = force,
            onProgress = { received, total ->
                updateState(
                    bytes = received,
                    bytesTotal = total,
                    processed = if (total > 0) (received * 100 / total).toInt().coerceIn(0, 100) else 0,
                    total = 100,
                    engineId = engineId
                )
                postProgress("Downloading face model", "${received / (1024 * 1024)} MB of ${(total / (1024 * 1024)).coerceAtLeast(1)} MB", if (total > 0) (received * 100 / total).toInt().coerceIn(0, 100) else 0, 100)
            },
            onStage = { stage ->
                updateState(stage = stage, engineId = engineId)
                postProgress("Preparing face model", when (stage) {
                    "connecting" -> "Connecting to model repository…"
                    "verifying" -> "Verifying cryptographic checksums…"
                    "extracting" -> "Extracting model archive…"
                    "installing" -> "Installing model files…"
                    "ready" -> "${factory.displayName} ready"
                    else -> "Preparing model…"
                }, -1, 0)
            },
            awaitIfResumed = { awaitResume(); currentCoroutineContext().ensureActive() },
            onDetailedProgress = { detail ->
                updateState(
                    bytes = detail.bytesReceived,
                    bytesTotal = detail.totalBytes,
                    speed = detail.speedBytesPerSec,
                    eta = detail.etaSeconds,
                    stage = detail.stage,
                    processed = if (detail.totalBytes > 0) (detail.bytesReceived * 100 / detail.totalBytes).toInt().coerceIn(0, 100) else 0,
                    total = 100,
                    engineId = engineId
                )
            }
        )
    } finally {
        serviceHandle.close()
    }
}

internal suspend fun IndexingForegroundService.awaitResume() {
    pauseSignal.first { paused -> !paused }
    currentCoroutineContext().ensureActive()
}

internal suspend fun IndexingForegroundService.loadPhotos(): List<MediaItem> {
    val prefsRepo = PreferencesRepository(applicationContext)
    val hiddenUris = runCatching { prefsRepo.hidden.first() }.getOrDefault(emptySet())
    val binUris = runCatching { prefsRepo.bin.first() }.getOrDefault(emptySet())
    return MediaRepository(contentResolver).loadMedia().filterNot {
        it.isVideo || it.isTrashed || it.uri in hiddenUris || it.uri in binUris
    }
}


internal suspend fun IndexingForegroundService.runLocation(rescanAll: Boolean) {
    runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
    val photos = loadPhotos()
    val cache = com.amresalehin.emregallery.data.PhotoMapLocationCache(applicationContext)
    if (rescanAll) withContext(Dispatchers.IO) { cache.clearAll() }
    val cached = withContext(Dispatchers.IO) { cache.loadAll() }
    val pending = if (rescanAll) photos else photos.filterNot { cached.containsKey(it.uri) }
    var locatedCount = cached.values.count { it.point != null }
    val doneCount = photos.size - pending.size
    val initialPaused = prefs.getBoolean(KEY_PAUSED, false)
    pauseSignal.value = initialPaused
    updateState(task = TASK_LOCATION, running = true, paused = initialPaused, processed = doneCount, total = photos.size, faces = locatedCount, error = null)
    postProgress("Map location indexing", "Scanning ${pending.size} photos for GPS geotags", doneCount, photos.size)
    var completed = doneCount
    for (item in pending) {
        awaitResume()
        currentCoroutineContext().ensureActive()
        val point = withContext(Dispatchers.IO) {
            com.amresalehin.emregallery.data.PhotoMapLocationCache.extractLocation(applicationContext, item.uri, isVideo = item.isVideo)
        }
        withContext(Dispatchers.IO) {
            cache.save(item.uri, point)
        }
        if (point != null) locatedCount++
        completed++
        updateState(processed = completed, total = photos.size, faces = locatedCount)
        if (completed % 8 == 0 || completed == photos.size) {
            postProgress("Map location indexing", "Scanned $completed of ${photos.size} photos · $locatedCount geotagged", completed, photos.size)
        }
        // Continuous unconstrained operation without artificial thermal cooldown
        kotlinx.coroutines.yield()
    }
}
