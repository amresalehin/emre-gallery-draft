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
        val originalMediaUrl = inputData.getString(KEY_MEDIA_URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "media_${System.currentTimeMillis()}"
        val referer = inputData.getString(KEY_REFERER)
        val itemId = inputData.getString(KEY_ITEM_ID) ?: "item_${System.currentTimeMillis()}"
        val title = inputData.getString(KEY_TITLE) ?: fileName
        val isVideo = inputData.getBoolean(KEY_IS_VIDEO, false)
        val isAudio = inputData.getBoolean(KEY_IS_AUDIO, false)
        val isStream = inputData.getBoolean(KEY_IS_STREAM, false)
        val expectsAudio = inputData.getBoolean(KEY_HAS_AUDIO, false)
        val canonicalMediaId = inputData.getString(KEY_CANONICAL_MEDIA_ID)
        val stackId = inputData.getString(KEY_STACK_ID) ?: "stack_${System.currentTimeMillis()}"
        val stackTitle = inputData.getString(KEY_STACK_TITLE) ?: "Discovered Media"
        val sourceDomain = inputData.getString(KEY_SOURCE_DOMAIN) ?: ""
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: originalMediaUrl

        setProgress(workDataOf(KEY_PROGRESS to 0.05f, KEY_STATUS to "Preparing $title…"))

        try {
            var mediaUrl = originalMediaUrl
            var audioUrl = inputData.getString(KEY_AUDIO_URL)
            var effectiveFormat = inputData.getString(KEY_FORMAT) ?: ""
            var downloadedFile: File? = null
            var errorDiagnostic: String? = null

            // Signed adaptive URLs can expire while queued. Refresh the pair from the original post
            // when the extractor supplied a stable platform media identity.
            if (!audioUrl.isNullOrBlank() && !canonicalMediaId.isNullOrBlank() &&
                GalleryDlEngine.isAvailable() && sourceUrl.startsWith("http", ignoreCase = true)
            ) {
                runCatching {
                    val refreshed = GalleryDlEngine.extractYtDlp(sourceUrl)
                    refreshed.items.firstOrNull { it.canonicalMediaId == canonicalMediaId }?.let { latest ->
                        mediaUrl = latest.url
                        audioUrl = latest.audioUrl ?: audioUrl
                    }
                }.onFailure { Log.w(TAG, "Could not refresh signed media URLs; using queued URLs", it) }
            }

            when {
                !audioUrl.isNullOrBlank() && isVideo -> {
                    setProgress(workDataOf(KEY_PROGRESS to 0.15f, KEY_STATUS to "Downloading video and audio streams…"))
                    downloadedFile = MediaStreamMerger.downloadAndMerge(
                        context = applicationContext,
                        videoUrl = mediaUrl,
                        audioUrl = audioUrl,
                        fileName = fileName,
                        referer = referer
                    )
                    effectiveFormat = "MKV"
                }

                isStream || mediaUrl.contains(".m3u8", true) || mediaUrl.contains(".mpd", true) -> {
                    setProgress(workDataOf(KEY_PROGRESS to 0.15f, KEY_STATUS to "Remuxing adaptive stream…"))
                    downloadedFile = MediaStreamMerger.remuxManifest(
                        context = applicationContext,
                        manifestUrl = mediaUrl,
                        fileName = fileName,
                        referer = referer
                    )
                    effectiveFormat = "MKV"
                }

                else -> {
                    var lastProgressUpdate = 0L
                    val result = OkHttpDownloader.downloadMediaFileDetailed(
                        context = applicationContext,
                        mediaUrl = mediaUrl,
                        fileName = fileName,
                        referer = referer,
                        downloadId = itemId,
                        onProgress = { bytesRead, totalBytes ->
                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 300) {
                                lastProgressUpdate = now
                                val pct = if (totalBytes > 0) {
                                    (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else 0.5f
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
                        errorDiagnostic = result.errorMessage
                    } else {
                        errorDiagnostic = result.errorMessage ?: "Direct media download failed."
                        // Extractor-aware fallback is allowed only for a valid media response.
                        val invalidContent = errorDiagnostic?.let { message ->
                            message.contains("webpage", true) || message.contains("HTML", true) ||
                                message.contains("JSON", true) || message.contains("corrupt", true)
                        } ?: false
                        if (!invalidContent && GalleryDlEngine.isAvailable() &&
                            sourceUrl.startsWith("http", ignoreCase = true)
                        ) {
                            setProgress(workDataOf(KEY_PROGRESS to 0.35f, KEY_STATUS to "Retrying with platform extractor…"))
                            downloadedFile = GalleryDlEngine.downloadMedia(
                                context = applicationContext,
                                mediaUrl = sourceUrl,
                                fileName = fileName,
                                referer = referer
                            )
                        }
                    }
                }
            }

            if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 0L) {
                if (isVideo && expectsAudio && !MediaStreamMerger.hasAudio(downloadedFile)) {
                    downloadedFile.delete()
                    throw IllegalStateException("The source has audio, but the downloaded file contains no audio stream.")
                }

                setProgress(workDataOf(KEY_PROGRESS to 1.0f, KEY_STATUS to "Saved successfully"))
                val storedTitle = if (effectiveFormat == "MKV") downloadedFile.name else title
                val inboxItem = MediaInboxItem(
                    id = itemId,
                    title = storedTitle,
                    localUri = downloadedFile.absolutePath,
                    sourceUrl = sourceUrl,
                    isVideo = isVideo,
                    isAudio = isAudio,
                    isStream = false,
                    format = effectiveFormat,
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
                if (runAttemptCount < 2) Result.retry()
                else Result.failure(workDataOf(KEY_ERROR to finalError))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in MediaDownloadWorker for $originalMediaUrl", e)
            if (runAttemptCount < 2) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
        }
    }

    companion object {
        const val TAG = "MediaDownloadWorker"
        const val WORK_TAG_DOWNLOAD = "media_download"

        const val KEY_MEDIA_URL = "media_url"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_HAS_AUDIO = "has_audio"
        const val KEY_CANONICAL_MEDIA_ID = "canonical_media_id"
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
