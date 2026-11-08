package com.amresalehin.emregallery.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class DownloadActivityStatus {
    DOWNLOADING,
    RETRYING,
    PAUSED,
    FAILED,
    FINISHED
}

data class DownloadActivityItem(
    val id: String,
    val workId: String? = null,
    val title: String,
    val mediaUrl: String,
    val thumbnailUrl: String? = null,
    val localUri: String? = null,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val isStream: Boolean = false,
    val format: String = "MP4",
    val status: DownloadActivityStatus = DownloadActivityStatus.DOWNLOADING,
    val progress: Float = 0f,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val errorMessage: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val durationHint: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val nextRetryTimestampMillis: Long? = null,
    val retryDelaySeconds: Long? = null,
    val stackId: String? = null,
    val stackTitle: String? = null,
    val sourceDomain: String? = null,
    val sourceUrl: String? = null
) {
    val isFailed: Boolean get() = status == DownloadActivityStatus.FAILED
    val isDownloading: Boolean get() = status == DownloadActivityStatus.DOWNLOADING
    val isFinished: Boolean get() = status == DownloadActivityStatus.FINISHED
    val isPaused: Boolean get() = status == DownloadActivityStatus.PAUSED
    val isRetrying: Boolean get() = status == DownloadActivityStatus.RETRYING
}

object DownloadActivityManager {
    internal const val TAG = "DownloadActivityManager"
    internal const val PREFS_NAME = "download_activity_tracker_prefs"
    internal const val KEY_ACTIVITIES_JSON = "saved_activities_json"

    internal val _activities = MutableStateFlow<List<DownloadActivityItem>>(emptyList())
    val activities: StateFlow<List<DownloadActivityItem>> = _activities.asStateFlow()

    internal var appContext: Context? = null

    internal val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    internal val activeRetryJobs = ConcurrentHashMap<String, Job>()

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            loadPersistedActivities()
            // 1. Sanitize any stale false 4K labels from persisted items
            // 2. Ensure any items marked failed prematurely from temporary 429 errors resume as RETRYING under downloading filter
            val cleaned = _activities.value.map { item ->
                var updated = item
                if (!item.isVideo && item.format.contains("4K", ignoreCase = true)) {
                    val clean = if (item.mediaUrl.lowercase().contains(".png") || item.title.lowercase().endsWith(".png")) "PNG" else "JPG"
                    updated = updated.copy(format = clean)
                }
                if (updated.status == DownloadActivityStatus.FAILED && isTemporaryFailure(-1, updated.errorMessage) && updated.retryCount < updated.maxRetries) {
                    val nextAttempt = updated.retryCount + 1
                    val delaySec = calculateRetryDelaySeconds(nextAttempt, null)
                    updated = updated.copy(
                        status = DownloadActivityStatus.RETRYING,
                        retryCount = nextAttempt,
                        maxRetries = 5,
                        nextRetryTimestampMillis = System.currentTimeMillis() + (delaySec * 1000L),
                        retryDelaySeconds = delaySec,
                        errorMessage = null
                    )
                }
                updated
            }
            _activities.value = cleaned
            syncWithStorageDir(context)
            persistActivities()
            syncWithInboxRepository()
            resumePersistedRetries(context.applicationContext)
        }
    }

    private fun resumePersistedRetries(context: Context) {
        val now = System.currentTimeMillis()
        val retryingItems = _activities.value.filter { it.status == DownloadActivityStatus.RETRYING }
        for (item in retryingItems) {
            val nextTime = item.nextRetryTimestampMillis
            if (nextTime != null) {
                val remainingMillis = nextTime - now
                val remainingSec = if (remainingMillis > 0) (remainingMillis + 999L) / 1000L else 0L
                scheduleRetryExecution(context, item.id, remainingSec)
            } else {
                scheduleRetryExecution(context, item.id, 0L)
            }
        }
    }


    fun syncWithInboxRepository() = syncWithInboxRepositoryImpl()
    fun syncWithStorageDir(context: Context? = appContext) = syncWithStorageDirImpl(context)

    fun recordQueued(
        id: String,
        workId: String? = null,
        title: String,
        mediaUrl: String,
        thumbnailUrl: String? = null,
        isVideo: Boolean = false,
        isAudio: Boolean = false,
        isStream: Boolean = false,
        format: String = "MP4",
        durationHint: String? = null,
        stackId: String? = null,
        stackTitle: String? = null,
        sourceDomain: String? = null,
        sourceUrl: String? = null
    ) = recordQueuedImpl(id, workId, title, mediaUrl, thumbnailUrl, isVideo, isAudio, isStream, format, durationHint, stackId, stackTitle, sourceDomain, sourceUrl)

    fun recordBatchQueued(items: List<DownloadActivityItem>) = recordBatchQueuedImpl(items)

    fun updateProgress(itemId: String, progress: Float, bytesRead: Long, totalBytes: Long, speedBytesPerSec: Long = 0L) =
        updateProgressImpl(itemId, progress, bytesRead, totalBytes, speedBytesPerSec)

    fun markFinished(itemId: String, localUri: String, sizeBytes: Long) =
        markFinishedImpl(itemId, localUri, sizeBytes)

    fun isTemporaryFailure(statusCode: Int, errorMessage: String?): Boolean =
        isTemporaryFailureImpl(statusCode, errorMessage)

    fun calculateRetryDelaySeconds(attempt: Int, retryAfterSeconds: Long?): Long =
        calculateRetryDelaySecondsImpl(attempt, retryAfterSeconds)

    fun handleDownloadFailure(
        context: Context,
        itemId: String,
        statusCode: Int = -1,
        errorMessage: String?,
        retryAfterSeconds: Long? = null
    ) = handleDownloadFailureImpl(context, itemId, statusCode, errorMessage, retryAfterSeconds)

    fun scheduleRetryExecution(context: Context, itemId: String, delaySeconds: Long) =
        scheduleRetryExecutionImpl(context, itemId, delaySeconds)

    fun markFailed(itemId: String, errorMessage: String) = markFailedImpl(itemId, errorMessage)
    fun pauseDownload(context: Context, itemId: String) = pauseDownloadImpl(context, itemId)
    fun resumeDownload(context: Context, itemId: String) = resumeDownloadImpl(context, itemId)
    fun retryDownload(context: Context, itemId: String) = retryDownloadImpl(context, itemId)
    fun retryAllFailed(context: Context) = retryAllFailedImpl(context)
    fun deleteActivity(itemId: String) = deleteActivityImpl(itemId)
    fun cancelAllActive(context: Context) = cancelAllActiveImpl(context)
    fun clearFinished() = clearFinishedImpl()
    fun formatBytes(bytes: Long): String = formatBytesImpl(bytes)
    fun formatSpeed(speedBytesPerSec: Long): String = formatSpeedImpl(speedBytesPerSec)
    fun formatDate(timestampMillis: Long): String = formatDateImpl(timestampMillis)
}
