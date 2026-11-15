package com.amresalehin.emregallery.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Facade managing download activity tracking, progress updates, retry transitions,
 * and synchronization with [MediaInboxRepository].
 */
object DownloadActivityManager {
    private const val TAG = "DownloadActivityManager"

    private val _activities = MutableStateFlow<List<DownloadActivityItem>>(emptyList())
    val activities: StateFlow<List<DownloadActivityItem>> = _activities.asStateFlow()

    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            val ctx = context.applicationContext
            appContext = ctx
            val loaded = DownloadActivityStore.load(ctx)
            val cleaned = DownloadActivityStore.sanitizeOnStartup(loaded)
            _activities.value = cleaned
            persistActivities()
            syncWithInboxRepository()
            DownloadActivityRetryScheduler.resumePersistedRetries(
                context = ctx,
                activities = cleaned,
                onScheduleItem = ::scheduleRetryExecution
            )
        }
    }

    private fun persistActivities() {
        val ctx = appContext ?: return
        DownloadActivityStore.persist(ctx, _activities.value)
    }

    /**
     * Synchronizes and merges finished items from [MediaInboxRepository].
     */
    fun syncWithInboxRepository() {
        val ctx = appContext ?: return
        _activities.value = DownloadActivityInboxSync.buildMergedActivities(ctx, _activities.value)
    }

    fun recordQueued(
        id: String,
        title: String,
        mediaUrl: String,
        thumbnailUrl: String? = null,
        isVideo: Boolean = false,
        isAudio: Boolean = false,
        isStream: Boolean = false,
        format: String = "JPG",
        workId: String? = null,
        isRetry: Boolean = false,
        stackId: String? = null,
        stackTitle: String? = null,
        sourceDomain: String? = null,
        sourceUrl: String? = null
    ) {
        val current = _activities.value.toMutableList()
        val existing = current.firstOrNull { it.id == id }
        current.removeAll { it.id == id }
        current.add(
            0,
            DownloadActivityItem(
                id = id,
                workId = workId,
                title = title,
                mediaUrl = mediaUrl,
                thumbnailUrl = thumbnailUrl ?: mediaUrl,
                isVideo = isVideo,
                isAudio = isAudio,
                isStream = isStream,
                format = format.ifBlank { if (isVideo) "MP4" else "JPG" },
                status = DownloadActivityStatus.DOWNLOADING,
                progress = existing?.progress ?: 0.05f,
                bytesRead = existing?.bytesRead ?: 0L,
                totalBytes = existing?.totalBytes ?: 0L,
                retryCount = if (isRetry) (existing?.retryCount ?: 0) else 0,
                maxRetries = existing?.maxRetries ?: 5,
                timestampMillis = System.currentTimeMillis(),
                stackId = stackId ?: existing?.stackId,
                stackTitle = stackTitle ?: existing?.stackTitle,
                sourceDomain = sourceDomain ?: existing?.sourceDomain,
                sourceUrl = sourceUrl ?: existing?.sourceUrl
            )
        )
        _activities.value = current
        persistActivities()
    }

    fun recordBatchQueued(
        items: List<ScrapedMediaItem>,
        workIds: List<UUID>? = null,
        stackId: String? = null,
        stackTitle: String? = null,
        sourceDomain: String? = null,
        sourceUrl: String? = null
    ) {
        val current = _activities.value.toMutableList()
        val newItems = items.mapIndexed { index, item ->
            val wId = workIds?.getOrNull(index)?.toString()
            DownloadActivityItem(
                id = item.id,
                workId = wId,
                title = item.title,
                mediaUrl = item.url,
                thumbnailUrl = item.thumbnailUrl ?: item.url,
                isVideo = item.isVideo,
                isAudio = item.isAudio,
                isStream = item.isStream,
                format = item.format.ifBlank { if (item.isVideo) "MP4" else "JPG" },
                status = DownloadActivityStatus.DOWNLOADING,
                progress = 0.05f,
                timestampMillis = System.currentTimeMillis(),
                stackId = stackId,
                stackTitle = stackTitle,
                sourceDomain = sourceDomain,
                sourceUrl = sourceUrl
            )
        }
        val newIds = newItems.map { it.id }.toSet()
        current.removeAll { it.id in newIds }
        current.addAll(0, newItems)
        _activities.value = current
        persistActivities()
    }

    fun updateProgress(
        itemId: String,
        progress: Float,
        bytesRead: Long,
        totalBytes: Long,
        speedBytesPerSec: Long = 0L
    ) {
        val current = _activities.value
        val index = current.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val updated = current.toMutableList()
            val existing = updated[index]
            updated[index] = existing.copy(
                status = DownloadActivityStatus.DOWNLOADING,
                progress = progress.coerceIn(0f, 1f),
                bytesRead = bytesRead,
                totalBytes = if (totalBytes > 0) totalBytes else existing.totalBytes,
                speedBytesPerSec = speedBytesPerSec
            )
            _activities.value = updated
        }
    }

    fun markFinished(
        itemId: String,
        localUri: String,
        sizeBytes: Long
    ) {
        val current = _activities.value.toMutableList()
        val index = current.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(
                status = DownloadActivityStatus.FINISHED,
                progress = 1.0f,
                localUri = localUri,
                bytesRead = sizeBytes,
                totalBytes = sizeBytes,
                timestampMillis = System.currentTimeMillis()
            )
        }
        _activities.value = current
        persistActivities()
        syncWithInboxRepository()
    }

    fun isTemporaryFailure(statusCode: Int, errorMessage: String?): Boolean {
        return DownloadActivityRetryScheduler.isTemporaryFailure(statusCode, errorMessage)
    }

    fun calculateRetryDelaySeconds(attempt: Int, retryAfterSeconds: Long?): Long {
        return DownloadActivityRetryScheduler.calculateRetryDelaySeconds(attempt, retryAfterSeconds)
    }

    /**
     * Handles download failure. Returns true if an automatic retry has been scheduled.
     * Stops automatic retries once max retries (5) are exhausted and permits manual retry.
     */
    fun handleDownloadFailure(
        context: Context,
        itemId: String,
        statusCode: Int,
        errorMessage: String?,
        retryAfterSeconds: Long?
    ): Boolean {
        val current = _activities.value.toMutableList()
        val index = current.indexOfFirst { it.id == itemId }
        if (index < 0) return false

        val item = current[index]
        if (item.status == DownloadActivityStatus.PAUSED ||
            item.status == DownloadActivityStatus.FINISHED ||
            item.errorMessage == "Cancelled by user"
        ) {
            Log.d(TAG, "Ignoring failure callback for item $itemId with status ${item.status}")
            return false
        }

        val isTransient = isTemporaryFailure(statusCode, errorMessage)
        val nextAttempt = item.retryCount + 1

        if (isTransient && nextAttempt <= item.maxRetries) {
            val delaySec = calculateRetryDelaySeconds(nextAttempt, retryAfterSeconds)
            val nextRetryEpoch = System.currentTimeMillis() + (delaySec * 1000L)
            current[index] = item.copy(
                status = DownloadActivityStatus.RETRYING,
                retryCount = nextAttempt,
                maxRetries = 5,
                nextRetryTimestampMillis = nextRetryEpoch,
                retryDelaySeconds = delaySec,
                errorMessage = null
            )
            _activities.value = current
            persistActivities()
            scheduleRetryExecution(context, itemId, delaySec)
            return true
        } else {
            DownloadActivityRetryScheduler.cancel(itemId)
            current[index] = item.copy(
                status = DownloadActivityStatus.FAILED,
                nextRetryTimestampMillis = null,
                retryDelaySeconds = null,
                errorMessage = errorMessage ?: "Download failed"
            )
            _activities.value = current
            persistActivities()
            return false
        }
    }

    fun scheduleRetryExecution(context: Context, itemId: String, delaySeconds: Long) {
        DownloadActivityRetryScheduler.schedule(itemId, delaySeconds) {
            val current = _activities.value.firstOrNull { it.id == itemId }
            if (current != null && current.status == DownloadActivityStatus.RETRYING) {
                executeScheduledRetry(context, current)
            }
        }
    }

    private fun executeScheduledRetry(context: Context, item: DownloadActivityItem) {
        val current = _activities.value.toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index < 0) return

        val existing = current[index]
        if (existing.status == DownloadActivityStatus.DOWNLOADING) return

        current[index] = existing.copy(
            status = DownloadActivityStatus.DOWNLOADING,
            nextRetryTimestampMillis = null,
            retryDelaySeconds = null
        )
        _activities.value = current
        persistActivities()

        val workId = DownloadQueueManager.enqueueDownload(
            context = context,
            item = item.toScrapedMediaItem(),
            referer = item.sourceUrl,
            stackId = item.stackId,
            stackTitle = item.stackTitle,
            sourceDomain = item.sourceDomain,
            sourceUrl = item.sourceUrl,
            isRetry = true
        )
        val reloaded = _activities.value.toMutableList()
        val idx = reloaded.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            reloaded[idx] = reloaded[idx].copy(workId = workId.toString())
            _activities.value = reloaded
            persistActivities()
        }
    }

    fun markFailed(
        itemId: String,
        errorMessage: String
    ) {
        val current = _activities.value.toMutableList()
        val index = current.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(
                status = DownloadActivityStatus.FAILED,
                errorMessage = errorMessage
            )
        }
        _activities.value = current
        persistActivities()
    }

    fun pauseDownload(context: Context, itemId: String) {
        DownloadActivityRetryScheduler.cancel(itemId)
        val current = _activities.value.toMutableList()
        val index = current.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val existing = current[index]
            existing.workId?.let { wId ->
                runCatching {
                    DownloadQueueManager.cancelDownload(context, UUID.fromString(wId))
                }
            }
            current[index] = existing.copy(
                status = DownloadActivityStatus.PAUSED,
                nextRetryTimestampMillis = null,
                retryDelaySeconds = null
            )
            _activities.value = current
            persistActivities()
        }
    }

    fun resumeDownload(context: Context, itemId: String) {
        retryDownload(context, itemId)
    }

    fun retryDownload(context: Context, itemId: String) {
        val current = _activities.value.toMutableList()
        val index = current.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val item = current[index]
            DownloadActivityRetryScheduler.cancel(itemId)

            val newWorkId = DownloadQueueManager.enqueueDownload(
                context = context,
                item = item.toScrapedMediaItem(),
                referer = item.sourceUrl,
                stackId = item.stackId,
                stackTitle = item.stackTitle,
                sourceDomain = item.sourceDomain,
                sourceUrl = item.sourceUrl,
                isRetry = true
            )
            current[index] = item.copy(
                status = DownloadActivityStatus.DOWNLOADING,
                workId = newWorkId.toString(),
                progress = if (item.bytesRead > 0 && item.totalBytes > 0) item.bytesRead.toFloat() / item.totalBytes.toFloat() else 0.05f,
                errorMessage = null,
                retryCount = 0,
                nextRetryTimestampMillis = null,
                retryDelaySeconds = null,
                timestampMillis = System.currentTimeMillis()
            )
            _activities.value = current
            persistActivities()
        }
    }

    fun retryAllFailed(context: Context) {
        val failed = _activities.value.filter { it.isFailed }
        for (f in failed) {
            retryDownload(context, f.id)
        }
    }

    fun deleteActivity(itemId: String) {
        DownloadActivityRetryScheduler.cancel(itemId)
        val current = _activities.value.toMutableList()
        current.removeAll { it.id == itemId }
        _activities.value = current
        persistActivities()
    }

    fun cancelAllActive(context: Context) {
        DownloadActivityRetryScheduler.cancelAll()
        DownloadQueueManager.cancelAll(context)
        val current = _activities.value.toMutableList()
        for (i in current.indices) {
            if (current[i].status == DownloadActivityStatus.DOWNLOADING ||
                current[i].status == DownloadActivityStatus.RETRYING ||
                current[i].status == DownloadActivityStatus.PAUSED
            ) {
                current[i] = current[i].copy(
                    status = DownloadActivityStatus.FAILED,
                    errorMessage = "Cancelled by user",
                    nextRetryTimestampMillis = null,
                    retryDelaySeconds = null
                )
            }
        }
        _activities.value = current
        persistActivities()
    }

    fun clearFinished() {
        val current = _activities.value.toMutableList()
        current.removeAll { it.status == DownloadActivityStatus.FINISHED }
        _activities.value = current
        persistActivities()
    }

    fun formatBytes(bytes: Long): String = DownloadActivityFormatters.formatBytes(bytes)

    fun formatSpeed(speedBytesPerSec: Long): String = DownloadActivityFormatters.formatSpeed(speedBytesPerSec)

    fun formatDate(timestampMillis: Long): String = DownloadActivityFormatters.formatDate(timestampMillis)
}
