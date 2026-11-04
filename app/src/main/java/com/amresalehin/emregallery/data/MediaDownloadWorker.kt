package com.amresalehin.emregallery.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persistent background downloader worker powered by Android Jetpack WorkManager.
 * Ensures media and streaming downloads survive process death, app navigation, and network changes.
 * Incorporates byte-level progress reporting, HTTP Range resumable downloading, and stream remuxing.
 */
class MediaDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

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

        setProgress(workDataOf(KEY_PROGRESS to 0.05f, KEY_STATUS to "Initializing download for $title..."))

        try {
            var downloadedFile: File? = null
            var errorDiagnostic: String? = null

            // 1. For HLS/DASH streaming media, route through stream-specific remuxer (yt-dlp)
            val isStreamingMedia = isStream || mediaUrl.contains(".m3u8", ignoreCase = true) || mediaUrl.contains(".mpd", ignoreCase = true)
            if (isStreamingMedia && GalleryDlEngine.isAvailable()) {
                setProgress(workDataOf(KEY_PROGRESS to 0.2f, KEY_STATUS to "Remuxing stream: $title..."))
                downloadedFile = GalleryDlEngine.downloadMedia(
                    context = applicationContext,
                    mediaUrl = mediaUrl,
                    fileName = fileName,
                    referer = referer
                )
            }

            // 2. Standard or fallback download via OkHttp with HTTP Range resumability
            if (downloadedFile == null || !downloadedFile.exists() || downloadedFile.length() == 0L) {
                var lastProgressUpdate = 0L
                val result = OkHttpDownloader.downloadMediaFileDetailed(
                    context = applicationContext,
                    mediaUrl = mediaUrl,
                    fileName = fileName,
                    referer = referer,
                    onProgress = { bytesRead, totalBytes ->
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 300) {
                            lastProgressUpdate = now
                            val pct = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0.5f
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

                if (result.isSuccessful && result.file != null && result.file.exists() && result.file.length() > 0L) {
                    downloadedFile = result.file
                } else {
                    errorDiagnostic = result.errorMessage
                    // 3. Fallback to Python runner if OkHttp failed and Python is available
                    if (GalleryDlEngine.isAvailable()) {
                        setProgress(workDataOf(KEY_PROGRESS to 0.4f, KEY_STATUS to "Attempting engine fallback for $title..."))
                        downloadedFile = GalleryDlEngine.downloadMedia(
                            context = applicationContext,
                            mediaUrl = mediaUrl,
                            fileName = fileName,
                            referer = referer
                        )
                    }
                }
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

                Result.success(
                    workDataOf(
                        KEY_LOCAL_URI to downloadedFile.absolutePath,
                        KEY_SIZE_BYTES to downloadedFile.length(),
                        KEY_ITEM_ID to itemId
                    )
                )
            } else {
                val finalError = errorDiagnostic ?: "Download failed for $mediaUrl"
                if (runAttemptCount < 2) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf(KEY_ERROR to finalError))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in MediaDownloadWorker for $mediaUrl", e)
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
            }
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

        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES_READ = "bytes_read"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_STATUS = "status"
        const val KEY_LOCAL_URI = "local_uri"
        const val KEY_SIZE_BYTES = "size_bytes"
        const val KEY_ERROR = "error"
    }
}
