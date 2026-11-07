package com.amresalehin.emregallery.pocket

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.galleryDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID

class PocketRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pocketKey = stringPreferencesKey("pocket_items_data_v1")
    private val browserBackend = PocketBrowserBackend(context)

    val items: Flow<List<PocketItem>> = appContext.galleryDataStore.data.map { prefs ->
        val raw = prefs[pocketKey]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { deserializeItems(raw) }.getOrDefault(emptyList())
        }
    }.distinctUntilChanged()

    suspend fun addItem(item: PocketItem) {
        val current = getCurrentItemsList().toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        saveItems(current)
    }

    suspend fun updateItem(item: PocketItem) {
        val current = getCurrentItemsList().toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index != -1) {
            val previous = current[index]
            val updated = item.copy(
                previewImagePath = item.previewImagePath ?: previous.previewImagePath,
                canonicalUrl = item.canonicalUrl ?: previous.canonicalUrl,
                siteName = item.siteName ?: previous.siteName,
                faviconUrl = item.faviconUrl ?: previous.faviconUrl
            )
            current[index] = updated
            saveItems(current)
            if (previous.previewImagePath != updated.previewImagePath) {
                previous.previewImagePath?.let { oldPath ->
                    withContext(Dispatchers.IO) { runCatching { File(oldPath).delete() } }
                }
            }
        } else {
            addItem(item)
        }
    }

    suspend fun deleteItem(id: String): PocketItem? {
        val current = getCurrentItemsList().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        return if (index != -1) {
            val removed = current.removeAt(index)
            saveItems(current)
            removed.previewImagePath?.let { path ->
                withContext(Dispatchers.IO) { runCatching { File(path).delete() } }
            }
            removed
        } else {
            null
        }
    }

    /**
     * Persist the bookmark first with a safe fallback, then enrich it using a short-lived
     * in-app WebView. A blocked, authenticated, or slow page never prevents bookmarking.
     */
    suspend fun saveFromUrl(
        urlInput: String,
        customTitle: String? = null,
        userNote: String = "",
        customTags: List<String> = emptyList()
    ): PocketItem {
        val normalizedUrl = normalizeWebUrl(urlInput)
        val domain = PocketItem.extractDomain(normalizedUrl)
        val fallbackTitle = customTitle?.takeIf { it.isNotBlank() }
            ?: deriveFallbackTitle(normalizedUrl, domain)
        val tags = if (customTags.isNotEmpty()) {
            customTags
        } else {
            listOf(domain.substringBefore(".").replaceFirstChar { it.uppercase() }, "Saved")
        }

        val initialItem = PocketItem(
            id = UUID.randomUUID().toString(),
            title = fallbackTitle,
            url = normalizedUrl,
            domain = domain,
            timestamp = System.currentTimeMillis(),
            note = userNote,
            tags = tags,
            description = "Article saved from $domain."
        )

        // Save before crawling so the item appears immediately, even when the page is inaccessible.
        addItem(initialItem)

        val metadata = try {
            browserBackend.extract(normalizedUrl, initialItem.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return initialItem

        return applyResolvedMetadata(initialItem, metadata, customTitle) ?: initialItem
    }

    suspend fun saveFromGalleryMedia(
        mediaItem: MediaItem,
        customTitle: String? = null,
        note: String = "",
        tags: List<String> = listOf("Gallery", "Photo")
    ): PocketItem {
        val title = customTitle?.takeIf { it.isNotBlank() } ?: mediaItem.name.substringBeforeLast(".")
        val item = PocketItem(
            id = UUID.randomUUID().toString(),
            title = title,
            url = mediaItem.uri,
            domain = "emregallery.local",
            imageUrls = listOf(mediaItem.uri),
            timestamp = when {
                mediaItem.dateTakenMillis > 0L -> mediaItem.dateTakenMillis
                mediaItem.dateAddedSeconds > 0L -> mediaItem.dateAddedSeconds * 1000L
                else -> System.currentTimeMillis()
            },
            note = note.ifBlank { "Saved from local device camera & gallery collection." },
            tags = tags,
            description = "High resolution photo from device library (${mediaItem.width} × ${mediaItem.height} px, ${mediaItem.mimeType})."
        )
        addItem(item)
        return item
    }

    private suspend fun applyResolvedMetadata(
        fallbackItem: PocketItem,
        metadata: PocketBrowserMetadata,
        customTitle: String?
    ): PocketItem? {
        val current = getCurrentItemsList().toMutableList()
        val index = current.indexOfFirst { it.id == fallbackItem.id }
        if (index == -1) {
            metadata.previewImagePath?.let { path ->
                withContext(Dispatchers.IO) { runCatching { File(path).delete() } }
            }
            return null
        }

        val latestItem = current[index]
        val enrichedItem = latestItem.copy(
            title = if (customTitle.isNullOrBlank() && latestItem.title == fallbackItem.title) {
                metadata.title?.takeIf { it.isNotBlank() } ?: latestItem.title
            } else {
                latestItem.title
            },
            description = if (latestItem.description == fallbackItem.description) {
                metadata.description?.takeIf { it.isNotBlank() } ?: latestItem.description
            } else {
                latestItem.description
            },
            imageUrls = if (latestItem.imageUrls == fallbackItem.imageUrls) {
                metadata.imageUrls.ifEmpty { latestItem.imageUrls }
            } else {
                latestItem.imageUrls
            },
            canonicalUrl = metadata.canonicalUrl ?: latestItem.canonicalUrl,
            siteName = metadata.siteName ?: latestItem.siteName,
            faviconUrl = metadata.faviconUrl ?: latestItem.faviconUrl,
            readableContent = metadata.readableContent?.takeIf { it.isNotBlank() } ?: latestItem.readableContent,
            previewImagePath = metadata.previewImagePath ?: latestItem.previewImagePath
        )

        if (enrichedItem != latestItem) {
            current[index] = enrichedItem
            saveItems(current)
            if (latestItem.previewImagePath != enrichedItem.previewImagePath) {
                latestItem.previewImagePath?.let { oldPath ->
                    withContext(Dispatchers.IO) { runCatching { File(oldPath).delete() } }
                }
            }
        }
        return enrichedItem
    }

    private fun normalizeWebUrl(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "URL cannot be empty" }
        val candidate = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = runCatching { URI(candidate) }.getOrNull()
        require(
            uri != null &&
                (uri.scheme.equals("http", ignoreCase = true) ||
                    uri.scheme.equals("https", ignoreCase = true)) &&
                !uri.host.isNullOrBlank()
        ) { "Enter a valid HTTP or HTTPS URL" }
        return uri.toASCIIString()
    }

    private fun deriveFallbackTitle(url: String, domain: String): String {
        val pathTitle = runCatching {
            val lastSegment = URI(url).path?.trimEnd('/')?.substringAfterLast('/')
            lastSegment
                ?.takeIf { it.isNotBlank() }
                ?.let { it.substringBeforeLast('.', missingDelimiterValue = it) }
                ?.replace('-', ' ')
                ?.replace('_', ' ')
                ?.trim()
        }.getOrNull()

        return pathTitle
            ?.takeIf { it.length >= 3 }
            ?.replaceFirstChar { it.uppercase() }
            ?: domain.replaceFirstChar { it.uppercase() }
    }

    private suspend fun getCurrentItemsList(): List<PocketItem> {
        val prefs = appContext.galleryDataStore.data.first()
        val raw = prefs[pocketKey]
        return if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { deserializeItems(raw) }.getOrDefault(emptyList())
        }
    }

    private suspend fun saveItems(list: List<PocketItem>) {
        val jsonArray = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("domain", item.domain)
                put("timestamp", item.timestamp)
                put("note", item.note)
                put("description", item.description)
                put("isFavorite", item.isFavorite)
                put("isRead", item.isRead)
                put("isArchived", item.isArchived)
                put("canonicalUrl", item.canonicalUrl)
                put("siteName", item.siteName)
                put("faviconUrl", item.faviconUrl)
                put("readableContent", item.readableContent)
                put("previewImagePath", item.previewImagePath)

                val imgArr = JSONArray()
                item.imageUrls.forEach { imgArr.put(it) }
                put("imageUrls", imgArr)

                val tagArr = JSONArray()
                item.tags.forEach { tagArr.put(it) }
                put("tags", tagArr)
            }
            jsonArray.put(obj)
        }
        appContext.galleryDataStore.edit { it[pocketKey] = jsonArray.toString() }
    }

    private fun deserializeItems(rawJson: String): List<PocketItem> {
        val array = JSONArray(rawJson)
        val list = mutableListOf<PocketItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val imgArr = obj.optJSONArray("imageUrls")
            val imgs = mutableListOf<String>()
            if (imgArr != null) {
                for (j in 0 until imgArr.length()) imgs.add(imgArr.getString(j))
            }

            val tagArr = obj.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagArr != null) {
                for (j in 0 until tagArr.length()) tags.add(tagArr.getString(j))
            }

            list.add(
                PocketItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", "Untitled"),
                    url = obj.optString("url", ""),
                    domain = obj.optString("domain", "web"),
                    imageUrls = imgs,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    note = obj.optString("note", ""),
                    tags = tags,
                    description = obj.optString("description", ""),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    isRead = obj.optBoolean("isRead", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    canonicalUrl = obj.optNullableString("canonicalUrl"),
                    siteName = obj.optNullableString("siteName"),
                    faviconUrl = obj.optNullableString("faviconUrl"),
                    readableContent = obj.optString("readableContent", ""),
                    previewImagePath = obj.optNullableString("previewImagePath")
                )
            )
        }
        return list.filterNot { it.id.startsWith("seed-") }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }
}
