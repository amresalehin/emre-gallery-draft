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

/**
 * Persistent background downloader worker powered by Android Jetpack WorkManager.
 * Runs as a foreground service with a unique notification ID to ensure uninterrupted downloading.
 * Incorporates byte-level progress reporting, HTTP Range resumable downloading, headers/cookies propagation,
 * and social video remuxing with yt-dlp.
 */
class MediaDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private fun getWorkerNotificationId(key: String): Int {
        return 4001 + (abs(key.hashCode()) % 50000)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: id.toString()
        val title = inputData.getString(KEY_TITLE) ?: inputData.getString(KEY_FILE_NAME) ?: "Media"
        return createSilentForegroundInfo(title, getWorkerNotificationId(itemId))
    }

    private fun createSilentForegroundInfo(title: String, notificationId: Int): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(SILENT_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    SILENT_CHANNEL_ID,
                    "Media Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Silent background media downloads"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val notification = NotificationCompat.Builder(applicationContext, SILENT_CHANNEL_ID)
            .setContentTitle("Downloading media")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val mediaUrl = inputData.getString(KEY_MEDIA_URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "media_${System.currentTimeMillis()}"
        val referer = inputData.getString(KEY_REFERER)
        val itemId = inputData.getString(KEY_ITEM_ID) ?: "item_${System.currentTimeMillis()}"
        val title = inputData.getString(KEY_TITLE) ?: fileName
        val isVideo = inputData.getBoolean(KEY_IS_VIDEO, false)
        val isAudio = inputData.getBoolean(KEY_IS_AUDIO, false)
        val isStream = inputData.getBoolean(KEY_IS_STREAM, false)
        val format = inputData.getString(KEY_FORMAT) ?: ""
        val stackId = inputData.getString(KEY_STACK_ID) ?: "stack_${System.currentTimeMillis()}"
        val stackTitle = inputData.getString(KEY_STACK_TITLE) ?: "Discovered Media"
        val sourceDomain = inputData.getString(KEY_SOURCE_DOMAIN) ?: ""
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: mediaUrl
        val sourceEngine = inputData.getString(KEY_SOURCE_ENGINE) ?: ""
        val headersJson = inputData.getString(KEY_HEADERS_JSON)

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
            Log.w(TAG, "Could not set silent foreground service notification: ${e.message}")
        }

        setProgress(workDataOf(KEY_PROGRESS to 0.05f, KEY_STATUS to "Initializing download for $title..."))

        try {
            var downloadedFile: File? = null
            var errorDiagnostic: String? = null

            val isSocialUrl = listOf("youtube.com", "youtu.be", "tiktok.com", "instagram.com", "vimeo.com", "twitter.com", "x.com")
                .any { mediaUrl.contains(it, ignoreCase = true) }
            val isStreamingMedia = isStream || mediaUrl.contains(".m3u8", ignoreCase = true) || mediaUrl.contains(".mpd", ignoreCase = true)
            val isYtDlpTarget = sourceEngine == "yt-dlp" || isSocialUrl || isStreamingMedia

            // 1. Native FFmpeg path for HLS/DASH manifests. Social video pages
            // retain yt-dlp because they need site-specific extraction.
            if (isStreamingMedia && FFmpegMediaDownloader.isManifestUrl(mediaUrl)) {
                setProgress(workDataOf(KEY_PROGRESS to 0.15f, KEY_STATUS to "Processing stream with FFmpeg: $title..."))
                val outputName = fileName.substringBeforeLast('.', fileName) + ".mp4"
                val outputDir = applicationContext.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                    ?: applicationContext.filesDir
                val ffmpegOutput = File(outputDir, outputName)
                val ffmpegResult = FFmpegMediaDownloader.download(
                    sourceUrl = mediaUrl,
                    destination = ffmpegOutput,
                    headers = buildMap {
                        putAll(downloadHeaders)
                        if (!referer.isNullOrBlank() && keys.none { it.equals("Referer", true) }) put("Referer", referer)
                        if (!cookieString.isNullOrBlank() && keys.none { it.equals("Cookie", true) }) put("Cookie", cookieString)
                    }
                )
                downloadedFile = ffmpegResult.file
                if (!ffmpegResult.success) {
                    errorDiagnostic = ffmpegResult.diagnostic ?: "FFmpeg stream processing failed"
                    Log.w(TAG, "FFmpeg failed; falling back to yt-dlp: $errorDiagnostic")
                }
            }

            // 2. Social media pages and FFmpeg failures use yt-dlp/gallery-dl.
            if (downloadedFile == null && isYtDlpTarget && GalleryDlEngine.isAvailable()) {
                setProgress(workDataOf(KEY_PROGRESS to 0.2f, KEY_STATUS to "Downloading stream/video: $title..."))
                downloadedFile = GalleryDlEngine.downloadMedia(
                    context = applicationContext,
                    mediaUrl = mediaUrl,
                    fileName = fileName,
                    referer = referer,
                    headers = downloadHeaders,
                    cookies = cookieString
                )
            }

            // 2. Standard or fallback download via OkHttp with HTTP Range resumability
            var okHttpResult: OkHttpDownloader.DownloadResult? = null
            if (downloadedFile == null || !downloadedFile.exists() || downloadedFile.length() == 0L) {
                var lastProgressUpdate = System.currentTimeMillis()
                var lastBytes = 0L
                val result = OkHttpDownloader.downloadMediaFileDetailed(
                    context = applicationContext,
                    mediaUrl = mediaUrl,
                    fileName = fileName,
                    referer = referer,
                    downloadId = itemId,
                    headers = downloadHeaders,
                    onProgress = { bytesRead, totalBytes ->
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 300) {
                            val dt = (now - lastProgressUpdate) / 1000.0
                            val db = bytesRead - lastBytes
                            val speed = if (dt > 0) (db / dt).toLong() else 0L
                            lastBytes = bytesRead
                            lastProgressUpdate = now
                            val pct = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0.5f
                            DownloadActivityManager.updateProgress(itemId, pct, bytesRead, totalBytes, speed)
                            setProgressAsync(
                                workDataOf(
                                    KEY_PROGRESS to pct,
                                    KEY_BYTES_READ to bytesRead,
                                    KEY_TOTAL_BYTES to totalBytes,
                                    KEY_STATUS to "Downloading $title (${bytesRead / 1024} KB)"
                                )
                            )
                        }
                    }
                )
                okHttpResult = result

                if (result.isSuccessful && result.file != null && result.file.exists() && result.file.length() > 0L) {
                    downloadedFile = result.file
                } else {
                    errorDiagnostic = result.errorMessage
                    val isRateLimited = result.statusCode == 429
                    val isInvalidContent = result.errorMessage?.let { msg ->
                        msg.contains("webpage", ignoreCase = true) ||
                        msg.contains("HTML", ignoreCase = true) ||
                        msg.contains("JSON error", ignoreCase = true) ||
                        msg.contains("corrupt or invalid raster", ignoreCase = true)
                    } ?: false

                    // 3. Fallback to Python runner only if OkHttp download failed and NOT 429 rate limit and NOT an invalid non-media response
                    if (!isRateLimited && !isInvalidContent && GalleryDlEngine.isAvailable()) {
                        setProgress(workDataOf(KEY_PROGRESS to 0.4f, KEY_STATUS to "Attempting engine fallback for $title..."))
                        downloadedFile = GalleryDlEngine.downloadMedia(
                            context = applicationContext,
                            mediaUrl = mediaUrl,
                            fileName = fileName,
                            referer = referer,
                            headers = downloadHeaders,
                            cookies = cookieString
                        )
                    }
                }
            }

            if (isStopped) {
                Log.d(TAG, "MediaDownloadWorker was stopped for $mediaUrl")
                return@withContext Result.failure()
            }

            if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 0L) {
                setProgress(workDataOf(KEY_PROGRESS to 1.0f, KEY_STATUS to "Saved successfully"))

                val inboxItem = MediaInboxItem(
                    id = itemId,
                    title = title,
                    localUri = downloadedFile.absolutePath,
                    sourceUrl = mediaUrl,
                    isVideo = isVideo,
                    isAudio = isAudio,
                    isStream = isStream,
                    format = format,
                    sizeBytes = downloadedFile.length(),
                    dateAddedMillis = System.currentTimeMillis()
                )

                MediaInboxRepository.getInstance(applicationContext).addItemToStack(
                    stackId = stackId,
                    item = inboxItem,
                    stackTitle = stackTitle,
                    sourceDomain = sourceDomain,
                    sourceUrl = sourceUrl
                )

                DownloadActivityManager.markFinished(
                    itemId = itemId,
                    localUri = downloadedFile.absolutePath,
                    sizeBytes = downloadedFile.length()
                )

                Result.success(
                    workDataOf(
                        KEY_LOCAL_URI to downloadedFile.absolutePath,
                        KEY_SIZE_BYTES to downloadedFile.length(),
                        KEY_ITEM_ID to itemId
                    )
                )
            } else {
                val finalError = errorDiagnostic ?: "Download failed for $mediaUrl"
                val statusCode = okHttpResult?.statusCode ?: -1
                val retryAfter = okHttpResult?.retryAfterSeconds

                val currentItem = DownloadActivityManager.activities.value.firstOrNull { it.id == itemId }
                val isPausedOrCancelled = isStopped ||
                    currentItem?.status == DownloadActivityStatus.PAUSED ||
                    currentItem?.errorMessage == "Cancelled by user"

                if (!isPausedOrCancelled) {
                    DownloadActivityManager.handleDownloadFailure(
                        context = applicationContext,
                        itemId = itemId,
                        statusCode = statusCode,
                        errorMessage = finalError,
                        retryAfterSeconds = retryAfter
                    )
                }
                Result.failure(workDataOf(KEY_ERROR to finalError))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in MediaDownloadWorker for $mediaUrl", e)
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
            Result.failure(workDataOf(KEY_ERROR to err))
        } finally {
            DownloadQueueManager.markDownloadCompleted(itemId)
        }
    }

    companion object {
        const val TAG = "MediaDownloadWorker"
        const val WORK_TAG_DOWNLOAD = "media_download"

        const val KEY_MEDIA_URL = "media_url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_REFERER = "referer"
        const val KEY_ITEM_ID = "item_id"
        const val KEY_TITLE = "title"
        const val KEY_IS_VIDEO = "is_video"
        const val KEY_IS_AUDIO = "is_audio"
        const val KEY_IS_STREAM = "is_stream"
        const val KEY_FORMAT = "format"
        const val KEY_STACK_ID = "stack_id"
        const val KEY_STACK_TITLE = "stack_title"
        const val KEY_SOURCE_DOMAIN = "source_domain"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_SOURCE_ENGINE = "source_engine"
        const val KEY_HEADERS_JSON = "headers_json"

        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES_READ = "bytes_read"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_STATUS = "status"
        const val KEY_LOCAL_URI = "local_uri"
        const val KEY_SIZE_BYTES = "size_bytes"
        const val KEY_ERROR = "error"

        const val SILENT_CHANNEL_ID = "media_download_silent_channel"
        const val NOTIFICATION_ID = 4001
    }
}
