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


internal fun DownloadActivityManager.isTemporaryFailureImpl(statusCode: Int, errorMessage: String?): Boolean {
    if (statusCode == 429) return true
    if (statusCode in listOf(500, 502, 503, 504)) return true
    if (statusCode == -1) {
        val msg = errorMessage?.lowercase(Locale.ROOT).orEmpty()
        if (msg.contains("timeout") ||
            msg.contains("reset") ||
            msg.contains("connection") ||
            msg.contains("failed to connect") ||
            msg.contains("network") ||
            msg.contains("socket") ||
            msg.contains("broken pipe") ||
            msg.contains("429")
        ) {
            return true
        }
    }
    return false
}

/**
 * Calculates retry delay respecting server Retry-After header if present;
 * otherwise applies exponential backoff with jitter on the schedule:
 * Attempt 1: 2s
 * Attempt 2: 4s
 * Attempt 3: 8s
 * Attempt 4: 16s
 * Attempt 5: 32s
 * Jitter adds random duration (up to 50% of base), preventing server thundering-herd.
 */
internal fun DownloadActivityManager.calculateRetryDelaySecondsImpl(attempt: Int, retryAfterSeconds: Long?): Long {
    if (retryAfterSeconds != null && retryAfterSeconds > 0L) {
        return retryAfterSeconds.coerceIn(1L, 300L)
    }
    val baseDelays = listOf(2L, 4L, 8L, 16L, 32L)
    val index = (attempt - 1).coerceIn(0, baseDelays.size - 1)
    val base = baseDelays[index]
    val maxJitter = (base * 0.5).toLong().coerceAtLeast(1L)
    val jitter = (0..maxJitter).random()
    return base + jitter
}

/**
 * Handles download failure. Returns true if an automatic retry has been scheduled.
 * Stops automatic retries once max retries (5) are exhausted and permits manual retry.
 */
internal fun DownloadActivityManager.handleDownloadFailureImpl(
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
            errorMessage = null // Never mark as failed until all 5 automatic retries conclude
        )
        _activities.value = current
        persistActivities()
        scheduleRetryExecution(context, itemId, delaySec)
        return true
    } else {
        // Retries exhausted or non-transient error: stop automatic attempts, allow manual retry
        activeRetryJobs[itemId]?.cancel()
        activeRetryJobs.remove(itemId)
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

/**
 * Schedules retry execution with deduplication, preventing duplicate retry jobs.
 */
internal fun DownloadActivityManager.scheduleRetryExecutionImpl(context: Context, itemId: String, delaySeconds: Long) {
    // Prevent duplicate retry requests
    activeRetryJobs[itemId]?.cancel()

    val job = scope.launch {
        if (delaySeconds > 0L) {
            delay(delaySeconds * 1000L)
        }
        val current = _activities.value.firstOrNull { it.id == itemId }
        if (current != null && current.status == DownloadActivityStatus.RETRYING) {
            executeScheduledRetry(context, current)
        }
        activeRetryJobs.remove(itemId)
    }
    activeRetryJobs[itemId] = job
}

internal fun DownloadActivityManager.executeScheduledRetry(context: Context, item: DownloadActivityItem) {
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

    val scraped = ScrapedMediaItem(
        id = item.id,
        url = item.mediaUrl,
        title = item.title,
        thumbnailUrl = item.thumbnailUrl,
        isVideo = item.isVideo,
        isAudio = item.isAudio,
        isStream = item.isStream,
        format = item.format
    )
    val workId = DownloadQueueManager.enqueueDownload(
        context = context,
        item = scraped,
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


internal fun DownloadActivityManager.retryDownloadImpl(context: Context, itemId: String) {
    val current = _activities.value.toMutableList()
    val index = current.indexOfFirst { it.id == itemId }
    if (index >= 0) {
        val item = current[index]
        // Cancel any pending automatic retry to prevent duplicate requests
        activeRetryJobs[itemId]?.cancel()
        activeRetryJobs.remove(itemId)

        val scraped = ScrapedMediaItem(
            id = item.id,
            url = item.mediaUrl,
            title = item.title,
            thumbnailUrl = item.thumbnailUrl,
            isVideo = item.isVideo,
            isAudio = item.isAudio,
            isStream = item.isStream,
            format = item.format
        )
        val newWorkId = DownloadQueueManager.enqueueDownload(
            context = context,
            item = scraped,
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
            retryCount = 0, // Reset attempt count on manual retry
            nextRetryTimestampMillis = null,
            retryDelaySeconds = null,
            timestampMillis = System.currentTimeMillis()
        )
        _activities.value = current
        persistActivities()
    }
}

internal fun DownloadActivityManager.retryAllFailedImpl(context: Context) {
    val failed = _activities.value.filter { it.isFailed }
    for (f in failed) {
        retryDownload(context, f.id)
    }
}

