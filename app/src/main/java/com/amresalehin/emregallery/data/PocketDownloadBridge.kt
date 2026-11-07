package com.amresalehin.emregallery.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.amresalehin.emregallery.pocket.PocketItem
import com.amresalehin.emregallery.pocket.PocketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    private val attachmentKey = stringPreferencesKey("pocket_download_attachments_v1")

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
            val key = attachmentKey
            val prefs = context.galleryDataStore.data.first()
            val all = runCatching { JSONArray(prefs[key] ?: "[]") }.getOrDefault(JSONArray())
            val record = JSONObject(attachment).apply {
                put("sourceUrl", mediaUrl)
            }
            all.put(record)
            context.galleryDataStore.edit { it[key] = all.toString() }

            // Also expose externally requested downloads in Pocket as a source item.
            val source = normalizedSource.ifBlank { mediaUrl }
            val domain = sourceDomain.ifBlank { PocketItem.extractDomain(source) }
            repository.saveFromUrl(source, customTitle = title.ifBlank { domain })
            val created = repository.items.first().firstOrNull { it.url.equals(source, ignoreCase = true) }
            if (created != null) {
                repository.updateItem(created.copy(attachments = created.attachments + attachment))
            }
        }
    }
}
