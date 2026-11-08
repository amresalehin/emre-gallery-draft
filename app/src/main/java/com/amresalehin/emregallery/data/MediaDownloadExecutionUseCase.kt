package com.amresalehin.emregallery.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.abs


internal suspend fun MediaDownloadWorker.executeDownloadWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
    val mediaUrl = inputData.getString(MediaDownloadWorker.KEY_MEDIA_URL) ?: return@withContext Result.failure()
    val fileName = inputData.getString(MediaDownloadWorker.KEY_FILE_NAME) ?: "media_${System.currentTimeMillis()}"
    val referer = inputData.getString(MediaDownloadWorker.KEY_REFERER)
    val itemId = inputData.getString(MediaDownloadWorker.KEY_ITEM_ID) ?: "item_${System.currentTimeMillis()}"
    val title = inputData.getString(MediaDownloadWorker.KEY_TITLE) ?: fileName
    val isVideo = inputData.getBoolean(MediaDownloadWorker.KEY_IS_VIDEO, false)
    val isAudio = inputData.getBoolean(MediaDownloadWorker.KEY_IS_AUDIO, false)
    val isStream = inputData.getBoolean(MediaDownloadWorker.KEY_IS_STREAM, false)
    val format = inputData.getString(MediaDownloadWorker.KEY_FORMAT) ?: ""
    val stackId = inputData.getString(MediaDownloadWorker.KEY_STACK_ID) ?: "stack_${System.currentTimeMillis()}"
    val stackTitle = inputData.getString(MediaDownloadWorker.KEY_STACK_TITLE) ?: "Discovered Media"
    val sourceDomain = inputData.getString(MediaDownloadWorker.KEY_SOURCE_DOMAIN) ?: ""
    val sourceUrl = inputData.getString(MediaDownloadWorker.KEY_SOURCE_URL) ?: mediaUrl
    val sourceEngine = inputData.getString(MediaDownloadWorker.KEY_SOURCE_ENGINE) ?: ""
    val headersJson = inputData.getString(MediaDownloadWorker.KEY_HEADERS_JSON)

    val downloadHeaders: Map<String, String> = runCatching {
        if (!headersJson.isNullOrBlank()) {
            val obj = JSONObject(headersJson)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getString(k)
            }
            map
        } else emptyMap()
    }.getOrDefault(emptyMap())

    val cookieString = OkHttpDownloader.sharedCookieJar.getCookieString(mediaUrl).takeIf { it.isNotBlank() }
        ?: downloadHeaders["Cookie"]

    try {
        setForeground(getForegroundInfo())
    } catch (e: Exception) {
        Log.w(MediaDownloadWorker.TAG, "Could not set silent foreground service notification: ${e.message}")
    }

    setProgress(workDataOf(MediaDownloadWorker.KEY_PROGRESS to 0.05f, MediaDownloadWorker.KEY_STATUS to "Initializing download for $title..."))

    try {
        var downloadedFile: File? = null
        var errorDiagnostic: String? = null

        setProgress(workDataOf(MediaDownloadWorker.KEY_PROGRESS to 0.15f, MediaDownloadWorker.KEY_STATUS to "Downloading $title..."))
        DownloadActivityManager.updateProgress(itemId, 0.15f, 0L, 0L, 0L)

        val isDirectHttpAsset = isVideo.not() && isStream.not() && (
                mediaUrl.startsWith("http://", ignoreCase = true) ||
                mediaUrl.startsWith("https://", ignoreCase = true) ||
                mediaUrl.startsWith("data:image/", ignoreCase = true)
        ) && (
            sourceEngine.contains("HTML", ignoreCase = true) ||
            sourceEngine.contains("DOM", ignoreCase = true) ||
            sourceEngine.contains("Network", ignoreCase = true) ||
            sourceEngine.contains("Srcset", ignoreCase = true) ||
            sourceEngine.contains("Direct", ignoreCase = true) ||
            mediaUrl.contains(".jpg", ignoreCase = true) ||
            mediaUrl.contains(".png", ignoreCase = true) ||
            mediaUrl.contains(".webp", ignoreCase = true) ||
            mediaUrl.contains(".gif", ignoreCase = true) ||
            mediaUrl.contains(".svg", ignoreCase = true) ||
            mediaUrl.contains(".mp3", ignoreCase = true) ||
            mediaUrl.contains(".m4a", ignoreCase = true) ||
            mediaUrl.contains(".wav", ignoreCase = true) ||
            mediaUrl.contains(".flac", ignoreCase = true) ||
            (mediaUrl.contains(".mp4", ignoreCase = true) && !sourceUrl.contains("youtube.com", ignoreCase = true) && !sourceUrl.contains("youtu.be", ignoreCase = true))
        )

        val targetUrl = if (sourceUrl.contains("youtube.com", ignoreCase = true) || sourceUrl.contains("youtu.be", ignoreCase = true)) {
            sourceUrl
        } else {
            mediaUrl
        }

        // Strategy 1: Direct HTTP media via OkHttp (fast path, byte-level ranges, resumable)
        if (isDirectHttpAsset) {
            setProgress(workDataOf(MediaDownloadWorker.KEY_PROGRESS to 0.25f, MediaDownloadWorker.KEY_STATUS to "Downloading via OkHttp: $title..."))
            val okResult = OkHttpDownloader.downloadMediaFileDetailed(
                context = applicationContext,
                mediaUrl = mediaUrl,
                fileName = fileName,
                referer = referer ?: sourceUrl,
                downloadId = itemId,
                headers = downloadHeaders,
                onProgress = { bytesRead, totalBytes ->
                    val ratio = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0.1f, 0.95f) else 0.5f
                    DownloadActivityManager.updateProgress(itemId, ratio, bytesRead, totalBytes, 0L)
                }
            )
            if (okResult.isSuccessful && okResult.file != null && okResult.file.exists() && okResult.file.length() > 0L) {
                downloadedFile = okResult.file
            }
        }

        // Strategy 2: Originating extractor (yt-dlp or gallery-dl) when required or as fallback
        if ((downloadedFile == null || !downloadedFile.exists() || downloadedFile.length() == 0L) && GalleryDlEngine.isAvailable()) {
            setProgress(workDataOf(MediaDownloadWorker.KEY_PROGRESS to 0.35f, MediaDownloadWorker.KEY_STATUS to "Downloading via extractor engine: $title..."))
            downloadedFile = GalleryDlEngine.downloadMedia(
                context = applicationContext,
                mediaUrl = targetUrl,
                fileName = fileName,
                referer = referer ?: sourceUrl,
                headers = downloadHeaders,
                cookies = cookieString
            )
            // If targetUrl != mediaUrl and extractor returned null, retry with mediaUrl directly
            if ((downloadedFile == null || !downloadedFile.exists() || downloadedFile.length() == 0L) && targetUrl != mediaUrl) {
                downloadedFile = GalleryDlEngine.downloadMedia(
                    context = applicationContext,
                    mediaUrl = mediaUrl,
                    fileName = fileName,
                    referer = referer ?: sourceUrl,
                    headers = downloadHeaders,
                    cookies = cookieString
                )
            }
        }

        // Strategy 3: Final fallback to OkHttp for any URL that specialized extractors couldn't capture
        if ((downloadedFile == null || !downloadedFile.exists() || downloadedFile.length() == 0L) && !isDirectHttpAsset) {
            setProgress(workDataOf(MediaDownloadWorker.KEY_PROGRESS to 0.5f, MediaDownloadWorker.KEY_STATUS to "Trying direct download fallback: $title..."))
            val fallbackResult = OkHttpDownloader.downloadMediaFileDetailed(
                context = applicationContext,
                mediaUrl = mediaUrl,
                fileName = fileName,
                referer = referer ?: sourceUrl,
                downloadId = itemId,
                headers = downloadHeaders
            )
            if (fallbackResult.isSuccessful && fallbackResult.file != null && fallbackResult.file.exists() && fallbackResult.file.length() > 0L) {
                downloadedFile = fallbackResult.file
            } else if (errorDiagnostic == null) {
                errorDiagnostic = fallbackResult.errorMessage
            }
        }

        if (isStopped) {
            Log.d(MediaDownloadWorker.TAG, "MediaDownloadWorker was stopped for $mediaUrl")
            return@withContext Result.failure()
        }

        if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 0L) {
            setProgress(workDataOf(MediaDownloadWorker.KEY_PROGRESS to 1.0f, MediaDownloadWorker.KEY_STATUS to "Saved successfully"))

            DownloadActivityManager.markFinished(
                itemId = itemId,
                localUri = downloadedFile.absolutePath,
                sizeBytes = downloadedFile.length()
            )

            Result.success(
                workDataOf(
                    MediaDownloadWorker.KEY_LOCAL_URI to downloadedFile.absolutePath,
                    MediaDownloadWorker.KEY_SIZE_BYTES to downloadedFile.length(),
                    MediaDownloadWorker.KEY_ITEM_ID to itemId
                )
            )
        } else {
            val finalError = errorDiagnostic ?: "Download failed via yt-dlp and gallery-dl for $mediaUrl"
            val currentItem = DownloadActivityManager.activities.value.firstOrNull { it.id == itemId }
            val isPausedOrCancelled = isStopped ||
                currentItem?.status == DownloadActivityStatus.PAUSED ||
                currentItem?.errorMessage == "Cancelled by user"

            if (!isPausedOrCancelled) {
                DownloadActivityManager.handleDownloadFailure(
                    context = applicationContext,
                    itemId = itemId,
                    statusCode = -1,
                    errorMessage = finalError,
                    retryAfterSeconds = null
                )
            }
            Result.failure(workDataOf(MediaDownloadWorker.KEY_ERROR to finalError))
        }
    } catch (e: Exception) {
        Log.e(MediaDownloadWorker.TAG, "Error in MediaDownloadWorker for $mediaUrl", e)
        val err = e.message ?: "Unknown error"
        val currentItem = DownloadActivityManager.activities.value.firstOrNull { it.id == itemId }
        val isPausedOrCancelled = isStopped ||
            e is kotlinx.coroutines.CancellationException ||
            currentItem?.status == DownloadActivityStatus.PAUSED ||
            currentItem?.errorMessage == "Cancelled by user"

        if (!isPausedOrCancelled) {
            DownloadActivityManager.handleDownloadFailure(
                context = applicationContext,
                itemId = itemId,
                statusCode = -1,
                errorMessage = err,
                retryAfterSeconds = null
            )
        }
        Result.failure(workDataOf(MediaDownloadWorker.KEY_ERROR to err))
    } finally {
        DownloadQueueManager.markDownloadCompleted(itemId)
    }
}
