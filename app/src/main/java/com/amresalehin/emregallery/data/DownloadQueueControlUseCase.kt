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


internal fun DownloadActivityManager.recordQueuedImpl(
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

internal fun DownloadActivityManager.recordBatchQueuedImpl(
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

internal fun DownloadActivityManager.updateProgressImpl(
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

internal fun DownloadActivityManager.markFinishedImpl(
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

/**
 * Determines whether an error is a transient failure eligible for automatic retry.
 * Handles HTTP 429 (Too Many Requests), HTTP 5xx server errors, and network/socket timeouts.
 */

internal fun DownloadActivityManager.markFailedImpl(
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

internal fun DownloadActivityManager.pauseDownloadImpl(context: Context, itemId: String) {
    activeRetryJobs[itemId]?.cancel()
    activeRetryJobs.remove(itemId)
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

internal fun DownloadActivityManager.resumeDownloadImpl(context: Context, itemId: String) {
    retryDownload(context, itemId)
}


internal fun DownloadActivityManager.deleteActivityImpl(itemId: String) {
    activeRetryJobs[itemId]?.cancel()
    activeRetryJobs.remove(itemId)
    val current = _activities.value.toMutableList()
    current.removeAll { it.id == itemId }
    _activities.value = current
    persistActivities()
}

internal fun DownloadActivityManager.cancelAllActiveImpl(context: Context) {
    for ((_, job) in activeRetryJobs) {
        job.cancel()
    }
    activeRetryJobs.clear()
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

internal fun DownloadActivityManager.clearFinishedImpl() {
    val current = _activities.value.toMutableList()
    current.removeAll { it.status == DownloadActivityStatus.FINISHED }
    _activities.value = current
    persistActivities()
}

internal fun DownloadActivityManager.formatBytesImpl(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2fGB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.2fMB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1fKB", kb)
        else -> "$bytes B"
    }
}

internal fun DownloadActivityManager.formatSpeedImpl(speedBytesPerSec: Long): String {
    if (speedBytesPerSec <= 0) return "0.00MB/s"
    val mb = speedBytesPerSec / (1024.0 * 1024.0)
    return if (mb >= 0.05) {
        String.format(Locale.US, "%.2fMB/s", mb)
    } else {
        val kb = speedBytesPerSec / 1024.0
        String.format(Locale.US, "%.1fKB/s", kb)
    }
}

internal fun DownloadActivityManager.formatDateImpl(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "Recent"
    val now = System.currentTimeMillis()
    val diff = now - timestampMillis
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = runCatching { timeFormat.format(Date(timestampMillis)) }.getOrDefault("")

    return when {
        diff in 0..(24 * 3600 * 1000L) -> if (timeStr.isNotBlank()) "Today, $timeStr" else "Today"
        diff in (24 * 3600 * 1000L)..(48 * 3600 * 1000L) -> if (timeStr.isNotBlank()) "Yesterday, $timeStr" else "Yesterday"
        else -> {
            runCatching {
                val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                dateFormat.format(Date(timestampMillis))
            }.getOrDefault("Recent")
        }
    }
}
