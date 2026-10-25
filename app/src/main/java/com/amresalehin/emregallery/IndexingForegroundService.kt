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
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceIndexingRepository
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

/**
 * Owns long-running indexing jobs. No indexing coroutine is tied to a Compose lifecycle.
 * Completed media are checkpointed to the OCR cache / face-index database, so a later run
 * naturally continues with remaining media.
 */
class IndexingForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var taskJob: Job? = null
    private val pauseSignal = MutableStateFlow(false)
    private val prefs by lazy { getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Media indexing", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Progress for OCR, face indexing, and model downloads"; setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_STATUS
        if (action == ACTION_CANCEL && taskJob != null) {
            taskJob?.cancel()
            taskJob = null
            pauseSignal.value = false
            updateState(running = false, paused = false, error = "Cancelled")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == ACTION_PAUSE) {
            pauseSignal.value = true
            updateState(paused = true)
            refreshNotification("Paused")
            return START_NOT_STICKY
        }
        if (action == ACTION_RESUME) {
            pauseSignal.value = false
            updateState(paused = false)
            refreshNotification("Resuming")
            return START_NOT_STICKY
        }
        if (action == ACTION_STATUS) {
            // Reconcile stale persisted state after process death without restarting work.
            if (taskJob?.isActive != true) {
                updateState(running = false, paused = false)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val title = when (action) {
            ACTION_START_OCR -> "OCR indexing"
            ACTION_START_FACE -> "Face indexing"
            ACTION_DOWNLOAD_MODEL -> "Downloading face model"
            else -> "Media indexing"
        }
        startForegroundCompat(buildNotification(title, "Starting…", 0, 0))
        if (taskJob?.isActive == true) return START_NOT_STICKY

        taskJob = serviceScope.launch {
            try {
                when (action) {
                    ACTION_START_OCR -> runOcr(
                        intent?.getStringArrayListExtra(EXTRA_LANGUAGES)?.ifEmpty { arrayListOf("eng") } ?: arrayListOf("eng"),
                        intent?.getBooleanExtra(EXTRA_RESCAN_ALL, false) == true,
                        intent?.getStringExtra(EXTRA_MODEL) ?: "fast"
                    )
                    ACTION_START_FACE -> runFace(
                        intent?.getBooleanExtra(EXTRA_RESCAN_ALL, false) == true,
                        intent?.getBooleanExtra(EXTRA_ALLOW_NATIVE, false) == true
                    )
                    ACTION_DOWNLOAD_MODEL -> downloadModel(intent?.getBooleanExtra(EXTRA_FORCE, false) == true)
                }
                updateState(running = false, paused = false, error = null)
            } catch (cancelled: CancellationException) {
                updateState(running = false, paused = false, error = "Cancelled")
                throw cancelled
            } catch (e: Exception) {
                updateState(running = false, paused = false, error = e.localizedMessage ?: "Indexing failed")
            } finally {
                taskJob = null
                pauseSignal.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runOcr(languages: List<String>, rescanAll: Boolean, modelId: String = "fast") {
        val model = com.amresalehin.emregallery.ocr.TesseractModel.fromId(modelId)
        val photos = loadPhotos()
        if (rescanAll) withContext(Dispatchers.IO) { TesseractOcr.clearCachedResults(this@IndexingForegroundService) }
        val pending = photos.filterNot { TesseractOcr.hasCachedResult(this, Uri.parse(it.uri)) }
        updateState(task = TASK_OCR, running = true, paused = false, processed = photos.size - pending.size, total = photos.size, error = null)
        postProgress("OCR indexing", "Preparing ${pending.size} images (${model.shortName})", photos.size - pending.size, photos.size)
        var completed = photos.size - pending.size
        for (item in pending) {
            awaitResume()
            currentCoroutineContext().ensureActive()
            try {
                val text = TesseractOcr.recognize(this, Uri.parse(item.uri), languages, model)
                TesseractOcr.saveCachedResult(this, Uri.parse(item.uri), text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Failed images are attempted once per run; successful results remain checkpointed.
            }
            completed++
            updateState(processed = completed, total = photos.size)
            postProgress("OCR indexing", "Processed $completed of ${photos.size} images (${model.title})", completed, photos.size)
        }
    }

    private suspend fun runFace(rescanAll: Boolean, allowNative: Boolean) {
        val photos = loadPhotos()
        val store = FaceClusterStore(applicationContext)
        if (rescanAll) withContext(Dispatchers.IO) { store.clearAllFaceData() }
        val alreadyDone = store.indexedMediaCount(photos.map { it.uri })
        updateState(task = TASK_FACE, running = true, paused = false, processed = alreadyDone, total = photos.size, faces = store.indexedFaceCount(), failed = store.failedMediaCount(), error = null)
        postProgress("Face indexing", "Preparing face detector", alreadyDone, photos.size)
        FaceIndexingRepository(applicationContext).indexNewMedia(
            media = photos,
            store = store,
            allowNativeFallback = allowNative,
            detectionThreshold = FaceRecognitionSettings.detectionConfidence(applicationContext),
            recognitionDistance = FaceRecognitionSettings.recognitionDistance(applicationContext),
            awaitIfResumed = { awaitResume(); currentCoroutineContext().ensureActive() },
            onProgress = { p ->
                val done = (alreadyDone + p.processed).coerceAtMost(photos.size)
                updateState(processed = done, total = photos.size, faces = store.indexedFaceCount(), failed = store.failedMediaCount())
                postProgress("Face indexing", "Processed $done of ${photos.size} images · ${store.indexedFaceCount()} faces found", done, photos.size)
            }
        )
    }

    private suspend fun downloadModel(force: Boolean) {
        updateState(task = TASK_MODEL, running = true, paused = false, processed = 0, total = 100, bytes = 0L, bytesTotal = 0L, error = null)
        postProgress("Downloading face model", "Starting download", 0, 100)
        FaceIndexingRepository(applicationContext).downloadModel(
            force = force,
            onProgress = { received, total ->
                updateState(bytes = received, bytesTotal = total, processed = if (total > 0) (received * 100 / total).toInt().coerceIn(0, 100) else 0, total = 100)
                postProgress("Downloading face model", "${received / (1024 * 1024)} MB of ${(total / (1024 * 1024)).coerceAtLeast(1)} MB", if (total > 0) (received * 100 / total).toInt().coerceIn(0, 100) else 0, 100)
            },
            onStage = { stage ->
                updateState(stage = stage)
                postProgress("Preparing face model", when (stage) {
                    "extracting" -> "Extracting model archive…"
                    "installing" -> "Installing model files…"
                    "ready" -> "Face model ready"
                    else -> "Preparing model…"
                }, -1, 0)
            },
            awaitIfResumed = { awaitResume(); currentCoroutineContext().ensureActive() }
        )
    }

    private suspend fun awaitResume() {
        pauseSignal.first { paused -> !paused }
        currentCoroutineContext().ensureActive()
    }

    private fun loadPhotos(): List<MediaItem> =
        MediaRepository(contentResolver).loadMedia().filterNot { it.isVideo }

    private fun updateState(
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
            if (stage != null) putString(KEY_STAGE, stage)
            if (bytes != null) putLong(KEY_BYTES, bytes)
            if (bytesTotal != null) putLong(KEY_BYTES_TOTAL, bytesTotal)
            putString(KEY_ERROR, error)
            apply()
        }
    }

    private fun postProgress(title: String, message: String, progress: Int, max: Int) {
        updateState()
        val notification = buildNotification(title, message, progress, max)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun refreshNotification(message: String) {
        val title = when (prefs.getString(KEY_TASK, "")) {
            TASK_OCR -> "OCR indexing"
            TASK_FACE -> "Face indexing"
            TASK_MODEL -> "Downloading face model"
            else -> "Media indexing"
        }
        val p = prefs.getInt(KEY_PROCESSED, 0)
        val t = prefs.getInt(KEY_TOTAL, 0)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(title, message, p, t))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, message: String, progress: Int, max: Int): Notification {
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

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val STATE_PREFS = "indexing_service_state"
        const val KEY_TASK = "task"
        const val KEY_RUNNING = "running"
        const val KEY_PAUSED = "paused"
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        const val KEY_FACES = "faces"
        const val KEY_FAILED = "failed"
        const val KEY_STAGE = "stage"
        const val KEY_BYTES = "bytes"
        const val KEY_BYTES_TOTAL = "bytes_total"
        const val KEY_ERROR = "error"

        private const val TASK_OCR = "ocr"
        private const val TASK_FACE = "face"
        private const val TASK_MODEL = "model"
        private const val CHANNEL_ID = "media_indexing"
        private const val NOTIFICATION_ID = 4107
        private const val ACTION_START_OCR = "com.amresalehin.emregallery.indexing.START_OCR"
        private const val ACTION_START_FACE = "com.amresalehin.emregallery.indexing.START_FACE"
        private const val ACTION_DOWNLOAD_MODEL = "com.amresalehin.emregallery.indexing.DOWNLOAD_MODEL"
        private const val ACTION_PAUSE = "com.amresalehin.emregallery.indexing.PAUSE"
        private const val ACTION_RESUME = "com.amresalehin.emregallery.indexing.RESUME"
        private const val ACTION_CANCEL = "com.amresalehin.emregallery.indexing.CANCEL"
        private const val ACTION_STATUS = "com.amresalehin.emregallery.indexing.STATUS"
        private const val EXTRA_LANGUAGES = "languages"
        private const val EXTRA_RESCAN_ALL = "rescan_all"
        private const val EXTRA_MODEL = "ocr_model"
        private const val EXTRA_ALLOW_NATIVE = "allow_native"
        private const val EXTRA_FORCE = "force"

        private fun command(context: Context, action: String, configure: Intent.() -> Unit = {}) {
            val intent = Intent(context, IndexingForegroundService::class.java).setAction(action).apply(configure)
            if (action == ACTION_PAUSE || action == ACTION_RESUME || action == ACTION_CANCEL || action == ACTION_STATUS) {
                context.startService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun startOcr(
            context: Context,
            languages: List<String>,
            rescanAll: Boolean = false,
            modelId: String = "fast"
        ) =
            command(context, ACTION_START_OCR) {
                putStringArrayListExtra(EXTRA_LANGUAGES, ArrayList(languages))
                putExtra(EXTRA_RESCAN_ALL, rescanAll)
                putExtra(EXTRA_MODEL, modelId)
            }

        fun startFace(context: Context, rescanAll: Boolean = false, allowNative: Boolean = false) =
            command(context, ACTION_START_FACE) {
                putExtra(EXTRA_RESCAN_ALL, rescanAll)
                putExtra(EXTRA_ALLOW_NATIVE, allowNative)
            }

        fun downloadModel(context: Context, force: Boolean = false) =
            command(context, ACTION_DOWNLOAD_MODEL) { putExtra(EXTRA_FORCE, force) }

        fun pause(context: Context) = command(context, ACTION_PAUSE)
        fun resume(context: Context) = command(context, ACTION_RESUME)
        fun cancel(context: Context) = command(context, ACTION_CANCEL)
        fun status(context: Context) = command(context, ACTION_STATUS)
    }
}
