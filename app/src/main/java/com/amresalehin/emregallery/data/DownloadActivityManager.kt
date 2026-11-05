package com.amresalehin.emregallery.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class DownloadActivityStatus {
    DOWNLOADING,
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
    val durationHint: String? = null
) {
    val isFailed: Boolean get() = status == DownloadActivityStatus.FAILED
    val isDownloading: Boolean get() = status == DownloadActivityStatus.DOWNLOADING
    val isFinished: Boolean get() = status == DownloadActivityStatus.FINISHED
    val isPaused: Boolean get() = status == DownloadActivityStatus.PAUSED
}

object DownloadActivityManager {
    private const val TAG = "DownloadActivityManager"
    private const val PREFS_NAME = "download_activity_tracker_prefs"
    private const val KEY_ACTIVITIES_JSON = "saved_activities_json"

    private val _activities = MutableStateFlow<List<DownloadActivityItem>>(emptyList())
    val activities: StateFlow<List<DownloadActivityItem>> = _activities.asStateFlow()

    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            loadPersistedActivities()
            syncWithInboxRepository()
        }
    }

    private fun getPrefs(): SharedPreferences? {
        return appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun persistActivities() {
        val prefs = getPrefs() ?: return
        try {
            val jsonArray = JSONArray()
            // Only persist active, paused, or failed items (finished are in repository)
            val toSave = _activities.value.filter { it.status != DownloadActivityStatus.FINISHED }.take(50)
            for (item in toSave) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("workId", item.workId ?: "")
                    put("title", item.title)
                    put("mediaUrl", item.mediaUrl)
                    put("thumbnailUrl", item.thumbnailUrl ?: "")
                    put("localUri", item.localUri ?: "")
                    put("isVideo", item.isVideo)
                    put("isAudio", item.isAudio)
                    put("isStream", item.isStream)
                    put("format", item.format)
                    put("status", item.status.name)
                    put("progress", item.progress.toDouble())
                    put("bytesRead", item.bytesRead)
                    put("totalBytes", item.totalBytes)
                    put("speedBytesPerSec", item.speedBytesPerSec)
                    put("errorMessage", item.errorMessage ?: "")
                    put("timestampMillis", item.timestampMillis)
                    put("durationHint", item.durationHint ?: "")
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_ACTIVITIES_JSON, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting download activities", e)
        }
    }

    private fun loadPersistedActivities() {
        val prefs = getPrefs() ?: return
        val rawJson = prefs.getString(KEY_ACTIVITIES_JSON, null) ?: return
        try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<DownloadActivityItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val statusStr = obj.optString("status", DownloadActivityStatus.FAILED.name)
                val status = runCatching { DownloadActivityStatus.valueOf(statusStr) }.getOrDefault(DownloadActivityStatus.FAILED)
                list.add(
                    DownloadActivityItem(
                        id = obj.getString("id"),
                        workId = obj.optString("workId").takeIf { it.isNotBlank() },
                        title = obj.optString("title", "Media"),
                        mediaUrl = obj.getString("mediaUrl"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        localUri = obj.optString("localUri").takeIf { it.isNotBlank() },
                        isVideo = obj.optBoolean("isVideo", false),
                        isAudio = obj.optBoolean("isAudio", false),
                        isStream = obj.optBoolean("isStream", false),
                        format = obj.optString("format", "MP4"),
                        status = status,
                        progress = obj.optDouble("progress", 0.0).toFloat(),
                        bytesRead = obj.optLong("bytesRead", 0L),
                        totalBytes = obj.optLong("totalBytes", 0L),
                        speedBytesPerSec = obj.optLong("speedBytesPerSec", 0L),
                        errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() },
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                        durationHint = obj.optString("durationHint").takeIf { it.isNotBlank() }
                    )
                )
            }
            _activities.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading persisted download activities", e)
        }
    }

    /**
     * Synchronizes and merges finished items from the real MediaInboxRepository.
     */
    fun syncWithInboxRepository() {
        val ctx = appContext ?: return
        val repo = MediaInboxRepository.getInstance(ctx)
        val stacks = repo.stacks.value
        val finishedItems = mutableListOf<DownloadActivityItem>()

        for (stack in stacks) {
            for (item in stack.items) {
                finishedItems.add(
                    DownloadActivityItem(
                        id = item.id,
                        title = item.title,
                        mediaUrl = item.sourceUrl,
                        thumbnailUrl = item.localUri,
                        localUri = item.localUri,
                        isVideo = item.isVideo,
                        isAudio = item.isAudio,
                        isStream = item.isStream,
                        format = item.format.ifBlank { if (item.isVideo) "MP4" else "JPEG" },
                        status = DownloadActivityStatus.FINISHED,
                        progress = 1.0f,
                        bytesRead = item.sizeBytes,
                        totalBytes = item.sizeBytes,
                        timestampMillis = item.dateAddedMillis,
                        durationHint = if (item.isVideo) "HD" else null
                    )
                )
            }
        }

        val activeOrFailed = _activities.value.filter { it.status != DownloadActivityStatus.FINISHED }
        val activeIds = activeOrFailed.map { it.id }.toSet()
        val uniqueFinished = finishedItems.filter { it.id !in activeIds }

        // Combined ordered: downloading first, failed second, then finished newest first
        _activities.value = (activeOrFailed + uniqueFinished).sortedWith(
            compareBy<DownloadActivityItem> {
                when (it.status) {
                    DownloadActivityStatus.DOWNLOADING -> 0
                    DownloadActivityStatus.PAUSED -> 1
                    DownloadActivityStatus.FAILED -> 2
                    DownloadActivityStatus.FINISHED -> 3
                }
            }.thenByDescending { it.timestampMillis }
        )
    }

    fun recordQueued(
        id: String,
        title: String,
        mediaUrl: String,
        thumbnailUrl: String? = null,
        isVideo: Boolean = false,
        isAudio: Boolean = false,
        isStream: Boolean = false,
        format: String = "MP4",
        workId: String? = null
    ) {
        val current = _activities.value.toMutableList()
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
                format = format.ifBlank { if (isVideo) "MP4" else "JPEG" },
                status = DownloadActivityStatus.DOWNLOADING,
                progress = 0.05f,
                timestampMillis = System.currentTimeMillis()
            )
        )
        _activities.value = current
        persistActivities()
    }

    fun recordBatchQueued(
        items: List<ScrapedMediaItem>,
        workIds: List<UUID>? = null
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
                format = item.format.ifBlank { if (item.isVideo) "MP4" else "JPEG" },
                status = DownloadActivityStatus.DOWNLOADING,
                progress = 0.05f,
                timestampMillis = System.currentTimeMillis()
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
        val current = _activities.value.toMutableList()
        val index = current.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val existing = current[index]
            existing.workId?.let { wId ->
                runCatching {
                    DownloadQueueManager.cancelDownload(context, UUID.fromString(wId))
                }
            }
            current[index] = existing.copy(status = DownloadActivityStatus.PAUSED)
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
                item = scraped
            )
            current[index] = item.copy(
                status = DownloadActivityStatus.DOWNLOADING,
                workId = newWorkId.toString(),
                progress = 0.05f,
                errorMessage = null,
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
        val current = _activities.value.toMutableList()
        current.removeAll { it.id == itemId }
        _activities.value = current
        persistActivities()
    }

    fun cancelAllActive(context: Context) {
        DownloadQueueManager.cancelAll(context)
        val current = _activities.value.toMutableList()
        for (i in current.indices) {
            if (current[i].status == DownloadActivityStatus.DOWNLOADING || current[i].status == DownloadActivityStatus.PAUSED) {
                current[i] = current[i].copy(status = DownloadActivityStatus.FAILED, errorMessage = "Cancelled by user")
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

    fun formatBytes(bytes: Long): String {
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

    fun formatSpeed(speedBytesPerSec: Long): String {
        if (speedBytesPerSec <= 0) return "0.00MB/s"
        val mb = speedBytesPerSec / (1024.0 * 1024.0)
        return if (mb >= 0.05) {
            String.format(Locale.US, "%.2fMB/s", mb)
        } else {
            val kb = speedBytesPerSec / 1024.0
            String.format(Locale.US, "%.1fKB/s", kb)
        }
    }

    fun formatDate(timestampMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestampMillis
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = timeFormat.format(Date(timestampMillis))

        return when {
            diff < 24 * 3600 * 1000L -> "Today, $timeStr"
            diff < 48 * 3600 * 1000L -> "Yesterday, $timeStr"
            else -> {
                val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                dateFormat.format(Date(timestampMillis))
            }
        }
    }
}
