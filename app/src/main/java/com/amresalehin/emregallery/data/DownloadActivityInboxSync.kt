package com.amresalehin.emregallery.data

import android.content.Context

/**
 * Synchronizes completed media items from [MediaInboxRepository] with active, retrying,
 * paused, and failed activity items, ordering them consistently by status and timestamp.
 */
internal object DownloadActivityInboxSync {

    fun buildMergedActivities(
        context: Context,
        currentActivities: List<DownloadActivityItem>
    ): List<DownloadActivityItem> {
        val repo = MediaInboxRepository.getInstance(context)
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

        val activeOrFailed = currentActivities.filter { it.status != DownloadActivityStatus.FINISHED }
        val activeIds = activeOrFailed.map { it.id }.toSet()
        val uniqueFinished = finishedItems.filter { it.id !in activeIds }

        return (activeOrFailed + uniqueFinished).sortedWith(
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
}
