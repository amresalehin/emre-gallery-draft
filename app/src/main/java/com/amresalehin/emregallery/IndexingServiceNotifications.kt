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


internal fun IndexingForegroundService.updateState(
    task: String? = null,
    running: Boolean? = null,
    paused: Boolean? = null,
    processed: Int? = null,
    total: Int? = null,
    faces: Int? = null,
    failed: Int? = null,
    stage: String? = null,
    bytes: Long? = null,
    bytesTotal: Long? = null,
    speed: Long? = null,
    eta: Long? = null,
    engineId: String? = null,
    currentItemName: String? = null,
    currentItemStatus: String? = null,
    groupingStatus: String? = null,
    detectionState: String? = null,
    detectionProcessed: Int? = null,
    detectionTotal: Int? = null,
    embeddingState: String? = null,
    embeddingProcessed: Int? = null,
    embeddingTotal: Int? = null,
    clusteringState: String? = null,
    clusteringProcessed: Int? = null,
    clusteringTotal: Int? = null,
    photosWithFaces: Int? = null,
    error: String? = null
) {
    prefs.edit().apply {
        if (task != null) putString(KEY_TASK, task)
        if (running != null) putBoolean(KEY_RUNNING, running)
        if (paused != null) putBoolean(KEY_PAUSED, paused)
        if (processed != null) putInt(KEY_PROCESSED, processed)
        if (total != null) putInt(KEY_TOTAL, total)
        if (faces != null) putInt(KEY_FACES, faces)
        if (failed != null) putInt(KEY_FAILED, failed)
        if (photosWithFaces != null) putInt(KEY_PHOTOS_WITH_FACES, photosWithFaces)
        if (stage != null) putString(KEY_STAGE, stage)
        if (bytes != null) putLong(KEY_BYTES, bytes)
        if (bytesTotal != null) putLong(KEY_BYTES_TOTAL, bytesTotal)
        if (speed != null) putLong(KEY_SPEED, speed)
        if (eta != null) putLong(KEY_ETA, eta)
        if (engineId != null) putString(KEY_ENGINE_ID, engineId)
        if (currentItemName != null) putString(KEY_CURRENT_ITEM_NAME, currentItemName)
        if (currentItemStatus != null) putString(KEY_CURRENT_ITEM_STATUS, currentItemStatus)
        if (groupingStatus != null) putString(KEY_GROUPING_STATUS, groupingStatus)
        if (detectionState != null) putString(KEY_DETECTION_STATE, detectionState)
        if (detectionProcessed != null) putInt(KEY_DETECTION_PROCESSED, detectionProcessed)
        if (detectionTotal != null) putInt(KEY_DETECTION_TOTAL, detectionTotal)
        if (embeddingState != null) putString(KEY_EMBEDDING_STATE, embeddingState)
        if (embeddingProcessed != null) putInt(KEY_EMBEDDING_PROCESSED, embeddingProcessed)
        if (embeddingTotal != null) putInt(KEY_EMBEDDING_TOTAL, embeddingTotal)
        if (clusteringState != null) putString(KEY_CLUSTERING_STATE, clusteringState)
        if (clusteringProcessed != null) putInt(KEY_CLUSTERING_PROCESSED, clusteringProcessed)
        if (clusteringTotal != null) putInt(KEY_CLUSTERING_TOTAL, clusteringTotal)
        putString(KEY_ERROR, error)
        apply()
    }
    FaceProcessingCoordinator.refreshState()
}

internal fun IndexingForegroundService.postProgress(title: String, message: String, progress: Int, max: Int) {
    updateState()
    val notification = buildNotification(title, message, progress, max)
    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(NOTIFICATION_ID, notification)
}

internal fun IndexingForegroundService.refreshNotification(message: String) {
    val title = when (prefs.getString(KEY_TASK, "")) {
        TASK_OCR -> "OCR indexing"
        TASK_FACE -> "Face indexing"
        TASK_LOCATION -> "Map location indexing"
        TASK_MODEL -> "Downloading face model"
        else -> "Media indexing"
    }
    val p = prefs.getInt(KEY_PROCESSED, 0)
    val t = prefs.getInt(KEY_TOTAL, 0)
    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(title, message, p, t))
}

internal fun IndexingForegroundService.startForegroundCompat(notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else startForeground(NOTIFICATION_ID, notification)
}

internal fun IndexingForegroundService.buildNotification(title: String, message: String, progress: Int, max: Int): Notification {
    val b = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle(title).setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setOngoing(true).setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
    if (progress >= 0 && max > 0) b.setProgress(max, progress.coerceIn(0, max), false)
    else b.setProgress(0, 0, true)
    return b.build()
}

