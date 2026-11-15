package com.amresalehin.emregallery.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles SharedPreferences JSON persistence and startup sanitization
 * for non-finished [DownloadActivityItem] entries.
 */
internal object DownloadActivityStore {
    private const val TAG = "DownloadActivityStore"
    private const val PREFS_NAME = "download_activity_tracker_prefs"
    private const val KEY_ACTIVITIES_JSON = "saved_activities_json"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun persist(context: Context, activities: List<DownloadActivityItem>) {
        try {
            val jsonArray = JSONArray()
            val toSave = activities.filter { it.status != DownloadActivityStatus.FINISHED }.take(500)
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
            prefs(context).edit().putString(KEY_ACTIVITIES_JSON, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting download activities", e)
        }
    }

    fun load(context: Context): List<DownloadActivityItem> {
        val rawJson = prefs(context).getString(KEY_ACTIVITIES_JSON, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<DownloadActivityItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val statusStr = obj.optString("status", DownloadActivityStatus.FAILED.name)
                val status = runCatching { DownloadActivityStatus.valueOf(statusStr) }
                    .getOrDefault(DownloadActivityStatus.FAILED)
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

                list.add(
                    DownloadActivityItem(
                        id = obj.getString("id"),
                        workId = obj.optString("workId").takeIf { it.isNotBlank() },
                        title = obj.optString("title", "Media"),
                        mediaUrl = obj.getString("mediaUrl"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        localUri = obj.optString("localUri").takeIf { it.isNotBlank() },
                        isVideo = isVid,
                        isAudio = isAud,
                        isStream = obj.optBoolean("isStream", false),
                        format = cleanFormat,
                        status = status,
                        progress = obj.optDouble("progress", 0.0).toFloat(),
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
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading persisted download activities", e)
            emptyList()
        }
    }

    fun sanitizeOnStartup(items: List<DownloadActivityItem>): List<DownloadActivityItem> {
        return items.map { item ->
            var updated = item
            if (!item.isVideo && item.format.contains("4K", ignoreCase = true)) {
                val clean = if (item.mediaUrl.lowercase().contains(".png") || item.title.lowercase().endsWith(".png")) {
                    "PNG"
                } else {
                    "JPG"
                }
                updated = updated.copy(format = clean)
            }
            if (updated.status == DownloadActivityStatus.FAILED &&
                DownloadActivityRetryScheduler.isTemporaryFailure(-1, updated.errorMessage) &&
                updated.retryCount < updated.maxRetries
            ) {
                val nextAttempt = updated.retryCount + 1
                val delaySec = DownloadActivityRetryScheduler.calculateRetryDelaySeconds(nextAttempt, null)
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
    }
}
