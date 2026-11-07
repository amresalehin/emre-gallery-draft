package com.amresalehin.emregallery.data

import android.content.Context
import com.amresalehin.emregallery.pocket.PocketItem
import com.amresalehin.emregallery.pocket.PocketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Persists a completed download as an attachment of its source Pocket entry.
 *
 * For legacy/external download requests without a matching Pocket URL, it creates
 * a single Pocket item for the source URL. No legacy file or SharedPreferences
 * record is deleted by this bridge.
 */
object PocketDownloadBridge {
    suspend fun recordCompletedDownload(
        context: Context,
        itemId: String,
        title: String,
        localUri: String,
        mediaUrl: String,
        sourceUrl: String,
        sourceDomain: String,
        isVideo: Boolean,
        isAudio: Boolean,
        isStream: Boolean,
        format: String,
        sizeBytes: Long,
        dateAddedMillis: Long
    ) = withContext(Dispatchers.IO) {
        val repository = PocketRepository(context.applicationContext)
        val current = repository.items.first()
        val normalizedSource = sourceUrl.trim()
        val existing = current.firstOrNull { it.url.equals(normalizedSource, ignoreCase = true) }
        val attachment = JSONObject().apply {
            put("id", itemId)
            put("title", title)
            put("localUri", localUri)
            put("sourceUrl", mediaUrl)
            put("isVideo", isVideo)
            put("isAudio", isAudio)
            put("isStream", isStream)
            put("format", format)
            put("sizeBytes", sizeBytes)
            put("dateAddedMillis", dateAddedMillis)
        }.toString()

        if (existing != null) {
            val updatedAttachments = (existing.attachments + attachment).distinctBy { raw ->
                runCatching { JSONObject(raw).optString("id") }.getOrDefault(raw)
            }
            repository.updateItem(existing.copy(attachments = updatedAttachments))
        } else {
            val source = normalizedSource.ifBlank { mediaUrl }
            val safeUrl = source.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val domain = sourceDomain.ifBlank { PocketItem.extractDomain(source) }
            val generatedId = "download-source-${UUID.nameUUIDFromBytes(source.toByteArray()).toString()}"
            val newItem = com.amresalehin.emregallery.pocket.PocketItem(
                id = generatedId,
                title = title.ifBlank { domain.ifBlank { "Downloaded media" } },
                url = safeUrl.orEmpty(),
                domain = domain,
                timestamp = dateAddedMillis,
                tags = listOf("Downloaded"),
                description = "Downloaded from ${domain.ifBlank { "the web" }}.",
                attachments = listOf(attachment)
            )
            repository.updateItem(newItem)
        }
        }
    }
}
