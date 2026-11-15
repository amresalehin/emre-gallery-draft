package com.amresalehin.emregallery.data

enum class DownloadActivityStatus {
    DOWNLOADING,
    RETRYING,
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
    val durationHint: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val nextRetryTimestampMillis: Long? = null,
    val retryDelaySeconds: Long? = null,
    val stackId: String? = null,
    val stackTitle: String? = null,
    val sourceDomain: String? = null,
    val sourceUrl: String? = null
) {
    val isFailed: Boolean get() = status == DownloadActivityStatus.FAILED
    val isDownloading: Boolean get() = status == DownloadActivityStatus.DOWNLOADING
    val isFinished: Boolean get() = status == DownloadActivityStatus.FINISHED
    val isPaused: Boolean get() = status == DownloadActivityStatus.PAUSED
    val isRetrying: Boolean get() = status == DownloadActivityStatus.RETRYING
}

internal fun DownloadActivityItem.toScrapedMediaItem(): ScrapedMediaItem {
    return ScrapedMediaItem(
        id = id,
        url = mediaUrl,
        title = title,
        thumbnailUrl = thumbnailUrl,
        isVideo = isVideo,
        isAudio = isAudio,
        isStream = isStream,
        format = format
    )
}
