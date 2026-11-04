package com.amresalehin.emregallery.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.util.UUID

/**
 * Manages persistent media downloads through Android Jetpack WorkManager.
 * Schedules, tracks, and cancels background downloads with network constraints.
 */
object DownloadQueueManager {

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueueDownload(
        context: Context,
        item: ScrapedMediaItem,
        referer: String? = null,
        stackId: String? = null,
        stackTitle: String? = null,
        sourceDomain: String? = null,
        sourceUrl: String? = null
    ): UUID {
        val sId = stackId ?: "stack_${System.currentTimeMillis()}"
        val sTitle = stackTitle ?: sourceDomain ?: "Discovered Media"
        val domain = sourceDomain ?: ""
        val url = sourceUrl ?: item.url

        val inputData = Data.Builder()
            .putString(MediaDownloadWorker.KEY_MEDIA_URL, item.url)
            .putString(MediaDownloadWorker.KEY_FILE_NAME, item.title)
            .putString(MediaDownloadWorker.KEY_REFERER, referer)
            .putString(MediaDownloadWorker.KEY_ITEM_ID, item.id)
            .putString(MediaDownloadWorker.KEY_TITLE, item.title)
            .putBoolean(MediaDownloadWorker.KEY_IS_VIDEO, item.isVideo)
            .putBoolean(MediaDownloadWorker.KEY_IS_AUDIO, item.isAudio)
            .putBoolean(MediaDownloadWorker.KEY_IS_STREAM, item.isStream)
            .putString(MediaDownloadWorker.KEY_FORMAT, item.format)
            .putString(MediaDownloadWorker.KEY_STACK_ID, sId)
            .putString(MediaDownloadWorker.KEY_STACK_TITLE, sTitle)
            .putString(MediaDownloadWorker.KEY_SOURCE_DOMAIN, domain)
            .putString(MediaDownloadWorker.KEY_SOURCE_URL, url)
            .build()

        val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setConstraints(networkConstraints)
            .setInputData(inputData)
            .addTag(MediaDownloadWorker.WORK_TAG_DOWNLOAD)
            .addTag("item_${item.id}")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(item),
            ExistingWorkPolicy.KEEP,
            request
        )
        return request.id
    }

    fun enqueueBatch(
        context: Context,
        items: List<ScrapedMediaItem>,
        referer: String? = null,
        stackId: String? = null,
        stackTitle: String? = null,
        sourceDomain: String? = null,
        sourceUrl: String? = null
    ): List<UUID> {
        val sId = stackId ?: "stack_${System.currentTimeMillis()}"
        val sTitle = stackTitle ?: sourceDomain ?: "Discovered Media"
        val domain = sourceDomain ?: ""
        val url = sourceUrl ?: referer ?: ""

        val workManager = WorkManager.getInstance(context)
        // Deduplicate both within this batch and against any queued/running download,
        // including a second request from the in-app browser or a rapid repeated tap.
        val distinctItems = MediaClassifier.deduplicateScrapedItems(items)
            .distinctBy(::uniqueWorkName)
        val requests = distinctItems.map { item ->
            val inputData = Data.Builder()
                .putString(MediaDownloadWorker.KEY_MEDIA_URL, item.url)
                .putString(MediaDownloadWorker.KEY_FILE_NAME, item.title)
                .putString(MediaDownloadWorker.KEY_REFERER, referer)
                .putString(MediaDownloadWorker.KEY_ITEM_ID, item.id)
                .putString(MediaDownloadWorker.KEY_TITLE, item.title)
                .putBoolean(MediaDownloadWorker.KEY_IS_VIDEO, item.isVideo)
                .putBoolean(MediaDownloadWorker.KEY_IS_AUDIO, item.isAudio)
                .putBoolean(MediaDownloadWorker.KEY_IS_STREAM, item.isStream)
                .putString(MediaDownloadWorker.KEY_FORMAT, item.format)
                .putString(MediaDownloadWorker.KEY_STACK_ID, sId)
                .putString(MediaDownloadWorker.KEY_STACK_TITLE, sTitle)
                .putString(MediaDownloadWorker.KEY_SOURCE_DOMAIN, domain)
                .putString(MediaDownloadWorker.KEY_SOURCE_URL, url)
                .build()

            OneTimeWorkRequestBuilder<MediaDownloadWorker>()
                .setConstraints(networkConstraints)
                .setInputData(inputData)
                .addTag(MediaDownloadWorker.WORK_TAG_DOWNLOAD)
                .addTag("item_${item.id}")
                .build()
        }

        requests.forEachIndexed { index, request ->
            workManager.enqueueUniqueWork(
                uniqueWorkName(distinctItems[index]),
                ExistingWorkPolicy.KEEP,
                request
            )
        }
        return requests.map { it.id }
    }

    /**
     * Stable WorkManager identity for one logical media asset.
     *
     * URL tracking/sizing parameters are normalized by MediaClassifier so the same media
     * discovered through multiple routes maps to one unfinished work request.
     */
    internal fun uniqueWorkName(item: ScrapedMediaItem): String {
        val identity = "${item.category.name}:${MediaClassifier.deduplicationKey(item.url)}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { byte -> "%02x".format(byte) }
        return "media-download-$digest"
    }

    fun getActiveDownloadsFlow(context: Context): Flow<List<WorkInfo>> {
        return WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(MediaDownloadWorker.WORK_TAG_DOWNLOAD)
    }

    fun cancelDownload(context: Context, workId: UUID) {
        WorkManager.getInstance(context).cancelWorkById(workId)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(MediaDownloadWorker.WORK_TAG_DOWNLOAD)
    }
}
