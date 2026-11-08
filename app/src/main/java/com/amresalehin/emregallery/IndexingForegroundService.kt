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

/**
 * Owns long-running indexing jobs. No indexing coroutine is tied to a Compose lifecycle.
 * Completed media are checkpointed to the OCR cache / face-index database, so a later run
 * naturally continues with remaining media.
 */
class IndexingForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var taskJob: Job? = null
    internal val pauseSignal = MutableStateFlow(false)
    internal val prefs by lazy { getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE) }

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
        if (action == ACTION_CANCEL) {
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
            prefs.edit().putBoolean(KEY_PAUSED, true).apply()
            updateState(paused = true)
            refreshNotification("Paused")
            return START_NOT_STICKY
        }
        if (action == ACTION_RESUME) {
            pauseSignal.value = false
            prefs.edit().putBoolean(KEY_PAUSED, false).apply()
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
            ACTION_START_LOCATION -> "Map location indexing"
            ACTION_DOWNLOAD_MODEL -> "Downloading face model"
            else -> "Media indexing"
        }
        startForegroundCompat(buildNotification(title, "Starting…", 0, 0))
        if (taskJob?.isActive == true) return START_NOT_STICKY

        taskJob = serviceScope.launch {
            try {
                when (action) {
                    ACTION_START_OCR -> runOcr(
                        languages = intent?.getStringArrayListExtra(EXTRA_LANGUAGES)?.ifEmpty { arrayListOf("eng") } ?: arrayListOf("eng"),
                        mlKitLanguages = intent?.getStringArrayListExtra(EXTRA_MLKIT_LANGUAGES)?.ifEmpty { arrayListOf("latin") } ?: arrayListOf("latin"),
                        rescanAll = intent?.getBooleanExtra(EXTRA_RESCAN_ALL, false) == true,
                        modelId = intent?.getStringExtra(EXTRA_MODEL) ?: "fast",
                        engineId = intent?.getStringExtra(EXTRA_ENGINE_ID) ?: "tesseract"
                    )
                    ACTION_START_FACE -> runFace(
                        rescanAll = intent?.getBooleanExtra(EXTRA_RESCAN_ALL, false) == true,
                        engineId = intent?.getStringExtra(EXTRA_ENGINE_ID) ?: BuffaloLFaceEngine.ENGINE_ID,
                        retryFailedOnly = intent?.getBooleanExtra(EXTRA_RETRY_FAILED_ONLY, false) == true,
                        folderFilter = intent?.getStringArrayListExtra(EXTRA_FOLDERS)?.toSet()
                    )
                    ACTION_START_LOCATION -> runLocation(
                        intent?.getBooleanExtra(EXTRA_RESCAN_ALL, false) == true
                    )
                    ACTION_DOWNLOAD_MODEL -> downloadModel(
                        intent?.getBooleanExtra(EXTRA_FORCE, false) == true,
                        intent?.getStringExtra(EXTRA_ENGINE_ID) ?: BuffaloLFaceEngine.ENGINE_ID
                    )
                }
                prefs.edit().putBoolean(KEY_PAUSED, false).apply()
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
        const val KEY_SPEED = "speed"
        const val KEY_ETA = "eta"
        const val KEY_CURRENT_ITEM_NAME = "current_item_name"
        const val KEY_CURRENT_ITEM_STATUS = "current_item_status"
        const val KEY_GROUPING_STATUS = "grouping_status"
        const val KEY_DETECTION_STATE = "detection_state"
        const val KEY_DETECTION_PROCESSED = "detection_processed"
        const val KEY_DETECTION_TOTAL = "detection_total"
        const val KEY_EMBEDDING_STATE = "embedding_state"
        const val KEY_EMBEDDING_PROCESSED = "embedding_processed"
        const val KEY_EMBEDDING_TOTAL = "embedding_total"
        const val KEY_CLUSTERING_STATE = "clustering_state"
        const val KEY_CLUSTERING_PROCESSED = "clustering_processed"
        const val KEY_CLUSTERING_TOTAL = "clustering_total"
        const val KEY_ERROR = "error"
        const val KEY_ENGINE_ID = "engine_id"
        const val KEY_PHOTOS_WITH_FACES = "photos_with_faces"

        const val TASK_LOCATION = "location"
        internal const val TASK_OCR = "ocr"
        internal const val TASK_FACE = "face"
        internal const val TASK_MODEL = "model"
        internal const val CHANNEL_ID = "media_indexing"
        internal const val NOTIFICATION_ID = 4107
        internal const val ACTION_START_OCR = "com.amresalehin.emregallery.indexing.START_OCR"
        internal const val ACTION_START_FACE = "com.amresalehin.emregallery.indexing.START_FACE"
        internal const val ACTION_START_LOCATION = "com.amresalehin.emregallery.indexing.START_LOCATION"
        internal const val ACTION_DOWNLOAD_MODEL = "com.amresalehin.emregallery.indexing.DOWNLOAD_MODEL"
        internal const val ACTION_PAUSE = "com.amresalehin.emregallery.indexing.PAUSE"
        internal const val ACTION_RESUME = "com.amresalehin.emregallery.indexing.RESUME"
        internal const val ACTION_CANCEL = "com.amresalehin.emregallery.indexing.CANCEL"
        internal const val ACTION_STATUS = "com.amresalehin.emregallery.indexing.STATUS"
        internal const val EXTRA_LANGUAGES = "languages"
        internal const val EXTRA_MLKIT_LANGUAGES = "mlkit_languages"
        internal const val EXTRA_RESCAN_ALL = "rescan_all"
        internal const val EXTRA_MODEL = "ocr_model"
        internal const val EXTRA_FORCE = "force"
        const val EXTRA_ENGINE_ID = "engine_id"
        const val EXTRA_RETRY_FAILED_ONLY = "retry_failed_only"
        const val EXTRA_FOLDERS = "face_indexing_folders"

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
            modelId: String = "fast",
            engineId: String = "tesseract",
            mlKitLanguages: List<String> = listOf("latin")
        ) =
            command(context, ACTION_START_OCR) {
                putStringArrayListExtra(EXTRA_LANGUAGES, ArrayList(languages))
                putStringArrayListExtra(EXTRA_MLKIT_LANGUAGES, ArrayList(mlKitLanguages))
                putExtra(EXTRA_RESCAN_ALL, rescanAll)
                putExtra(EXTRA_MODEL, modelId)
                putExtra(EXTRA_ENGINE_ID, engineId)
            }

        fun startFace(
            context: Context,
            rescanAll: Boolean = false,
            engineId: String = BuffaloLFaceEngine.ENGINE_ID,
            folders: List<String>? = null
        ) =
            command(context, ACTION_START_FACE) {
                putExtra(EXTRA_RESCAN_ALL, rescanAll)
                putExtra(EXTRA_ENGINE_ID, engineId)
                if (folders != null) {
                    putStringArrayListExtra(EXTRA_FOLDERS, ArrayList(folders))
                }
            }

        fun retryFailedFaces(
            context: Context,
            engineId: String = BuffaloLFaceEngine.ENGINE_ID
        ) =
            command(context, ACTION_START_FACE) {
                putExtra(EXTRA_RETRY_FAILED_ONLY, true)
                putExtra(EXTRA_ENGINE_ID, engineId)
            }

        fun startLocation(context: Context, rescanAll: Boolean = false) =
            command(context, ACTION_START_LOCATION) {
                putExtra(EXTRA_RESCAN_ALL, rescanAll)
            }

        fun downloadModel(
            context: Context,
            force: Boolean = false,
            engineId: String = BuffaloLFaceEngine.ENGINE_ID
        ) =
            command(context, ACTION_DOWNLOAD_MODEL) {
                putExtra(EXTRA_FORCE, force)
                putExtra(EXTRA_ENGINE_ID, engineId)
            }

        fun pause(context: Context) = command(context, ACTION_PAUSE)
        fun resume(context: Context) = command(context, ACTION_RESUME)
        fun cancel(context: Context) = command(context, ACTION_CANCEL)
        fun status(context: Context) = command(context, ACTION_STATUS)
    }
}
