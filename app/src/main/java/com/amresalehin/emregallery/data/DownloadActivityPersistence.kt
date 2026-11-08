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


internal fun DownloadActivityManager.getPrefs(): SharedPreferences? {
    return appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

internal fun DownloadActivityManager.persistActivities() {
    val prefs = getPrefs() ?: return
    try {
        val jsonArray = JSONArray()
        // Persist all activity items including finished downloads up to 500
        val toSave = _activities.value.take(500)
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
                put("retryCount", item.retryCount)
                put("maxRetries", item.maxRetries)
                put("nextRetryTimestampMillis", item.nextRetryTimestampMillis ?: -1L)
                put("retryDelaySeconds", item.retryDelaySeconds ?: -1L)
                put("stackId", item.stackId ?: "")
                put("stackTitle", item.stackTitle ?: "")
                put("sourceDomain", item.sourceDomain ?: "")
                put("sourceUrl", item.sourceUrl ?: "")
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_ACTIVITIES_JSON, jsonArray.toString()).apply()
    } catch (e: Exception) {
        Log.e(TAG, "Error persisting download activities", e)
    }
}

internal fun DownloadActivityManager.loadPersistedActivities() {
    val prefs = getPrefs() ?: return
    val rawJson = prefs.getString(KEY_ACTIVITIES_JSON, null) ?: return
    try {
        val jsonArray = JSONArray(rawJson)
        val list = mutableListOf<DownloadActivityItem>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                val statusStr = obj.optString("status", DownloadActivityStatus.FAILED.name)
                val status = runCatching { DownloadActivityStatus.valueOf(statusStr) }.getOrDefault(DownloadActivityStatus.FAILED)
                val isVid = obj.optBoolean("isVideo", false)
                val isAud = obj.optBoolean("isAudio", false)
                val rawFmt = obj.optString("format", "").trim()
                val cleanFormat = if (!isVid && rawFmt.contains("4K", ignoreCase = true)) {
                    if (isAud) "MP3" else "JPG"
                } else if (rawFmt.isNotBlank()) {
                    rawFmt
                } else {
                    when {
                        isVid -> "MP4"
                        isAud -> "MP3"
                        else -> "JPG"
                    }
                }
                val rawProgress = obj.optDouble("progress", 0.0).toFloat()
                val safeProgress = if (rawProgress.isNaN() || rawProgress.isInfinite() || rawProgress < 0f) 0f else rawProgress.coerceIn(0f, 1f)

                list.add(
                    DownloadActivityItem(
                        id = obj.optString("id").takeIf { it.isNotBlank() } ?: "persisted_${System.currentTimeMillis()}_$i",
                        workId = obj.optString("workId").takeIf { it.isNotBlank() },
                        title = obj.optString("title", "Media"),
                        mediaUrl = obj.optString("mediaUrl", ""),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        localUri = obj.optString("localUri").takeIf { it.isNotBlank() },
                        isVideo = isVid,
                        isAudio = isAud,
                        isStream = obj.optBoolean("isStream", false),
                        format = cleanFormat,
                        status = status,
                        progress = safeProgress,
                        bytesRead = obj.optLong("bytesRead", 0L),
                        totalBytes = obj.optLong("totalBytes", 0L),
                        speedBytesPerSec = obj.optLong("speedBytesPerSec", 0L),
                        errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() },
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                        durationHint = obj.optString("durationHint").takeIf { it.isNotBlank() },
                        retryCount = obj.optInt("retryCount", 0),
                        maxRetries = obj.optInt("maxRetries", 5),
                        nextRetryTimestampMillis = obj.optLong("nextRetryTimestampMillis", -1L).takeIf { it > 0L },
                        retryDelaySeconds = obj.optLong("retryDelaySeconds", -1L).takeIf { it > 0L },
                        stackId = obj.optString("stackId").takeIf { it.isNotBlank() },
                        stackTitle = obj.optString("stackTitle").takeIf { it.isNotBlank() },
                        sourceDomain = obj.optString("sourceDomain").takeIf { it.isNotBlank() },
                        sourceUrl = obj.optString("sourceUrl").takeIf { it.isNotBlank() }
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing persisted item index $i", e)
            }
        }
        _activities.value = list.distinctBy { it.id }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading persisted download activities", e)
    }
}

/**
 * Synchronizes and merges finished items from the real MediaInboxRepository.
 */
internal fun DownloadActivityManager.syncWithInboxRepositoryImpl() {
    val ctx = appContext ?: return
    val repo = MediaInboxRepository.getInstance(ctx)
    val stacks = repo.stacks.value
    val finishedItems = mutableListOf<DownloadActivityItem>()
    val seenIds = mutableSetOf<String>()

    for (stack in stacks) {
        for (item in stack.items) {
            val candidateId = item.id.ifBlank { "${stack.id}_${item.localUri.hashCode()}" }
            val uniqueId = if (seenIds.add(candidateId)) {
                candidateId
            } else {
                val fallback = "${stack.id}_${candidateId}_${item.localUri.hashCode()}"
                seenIds.add(fallback)
                fallback
            }

            finishedItems.add(
                DownloadActivityItem(
                    id = uniqueId,
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
                    durationHint = if (item.isVideo) "HD" else null,
                    stackId = stack.id,
                    stackTitle = stack.title
                )
            )
        }
    }

    val currentActivities = _activities.value
    val existingFinished = currentActivities.filter { it.status == DownloadActivityStatus.FINISHED }
    val activeOrFailed = currentActivities.filter { it.status != DownloadActivityStatus.FINISHED }
    val activeIds = activeOrFailed.map { it.id }.toSet()
    val allFinished = (existingFinished + finishedItems).filter { it.id !in activeIds }.distinctBy { it.id }

    // Combined ordered: downloading first, retrying second, paused third, failed fourth, then finished newest first
    _activities.value = (activeOrFailed + allFinished).distinctBy { it.id }.sortedWith(
        compareBy<DownloadActivityItem> {
            when (it.status) {
                DownloadActivityStatus.DOWNLOADING -> 0
                DownloadActivityStatus.RETRYING -> 1
                DownloadActivityStatus.PAUSED -> 2
                DownloadActivityStatus.FAILED -> 3
                DownloadActivityStatus.FINISHED -> 4
            }
        }.thenByDescending { it.timestampMillis }
    )
}

/**
 * Scans the download storage directory and synthesizes finished activity items
 * for any completed files saved on disk, ensuring past downloads always display.
 */
internal fun DownloadActivityManager.syncWithStorageDirImpl(context: Context? = appContext) {
    val ctx = context ?: return
    try {
        val storageDir = OkHttpDownloader.getTargetStorageDir(ctx)
        if (!storageDir.exists() || !storageDir.isDirectory) return
        val files = storageDir.listFiles() ?: return

        val current = _activities.value.toMutableList()
        val existingLocalUris = current.mapNotNull { it.localUri }.toSet()
        val existingTitles = current.map { it.title.lowercase(Locale.ROOT) }.toSet()

        var modified = false
        for (file in files) {
            if (file.isFile && file.length() > 0 && !file.name.endsWith(".download") && !file.name.endsWith(".tmp")) {
                val fileUri = Uri.fromFile(file).toString()
                val fileName = file.name
                if (fileUri !in existingLocalUris && fileName.lowercase(Locale.ROOT) !in existingTitles) {
                    val isVideo = fileName.endsWith(".mp4", true) || fileName.endsWith(".webm", true) || fileName.endsWith(".mkv", true)
                    val isAudio = fileName.endsWith(".mp3", true) || fileName.endsWith(".m4a", true) || fileName.endsWith(".wav", true) || fileName.endsWith(".aac", true)
                    val ext = file.extension.uppercase(Locale.ROOT).ifBlank { if (isVideo) "MP4" else if (isAudio) "MP3" else "JPG" }

                    current.add(
                        DownloadActivityItem(
                            id = "storage_${file.name.hashCode()}_${file.lastModified()}",
                            title = fileName,
                            mediaUrl = fileUri,
                            thumbnailUrl = fileUri,
                            localUri = fileUri,
                            isVideo = isVideo,
                            isAudio = isAudio,
                            isStream = false,
                            format = ext,
                            status = DownloadActivityStatus.FINISHED,
                            progress = 1.0f,
                            bytesRead = file.length(),
                            totalBytes = file.length(),
                            timestampMillis = file.lastModified()
                        )
                    )
                    modified = true
                }
            }
        }

        if (modified) {
            _activities.value = current.distinctBy { it.id }.sortedWith(
                compareBy<DownloadActivityItem> {
                    when (it.status) {
                        DownloadActivityStatus.DOWNLOADING -> 0
                        DownloadActivityStatus.RETRYING -> 1
                        DownloadActivityStatus.PAUSED -> 2
                        DownloadActivityStatus.FAILED -> 3
                        DownloadActivityStatus.FINISHED -> 4
                    }
                }.thenByDescending { it.timestampMillis }
            )
            persistActivities()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing with storage dir", e)
    }
}

