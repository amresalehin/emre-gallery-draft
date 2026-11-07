package com.amresalehin.emregallery.pocket

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.amresalehin.emregallery.data.galleryDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class PocketRepository(private val context: Context) {

    private val pocketKey = stringPreferencesKey("pocket_items_data_v1")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val items: Flow<List<PocketItem>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[pocketKey]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { deserializeItems(raw) }.getOrDefault(emptyList())
        }
    }.distinctUntilChanged()

    private suspend fun addItem(item: PocketItem) {
        val current = getCurrentItemsList().toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        saveItems(current)
    }

    suspend fun updateItem(item: PocketItem) {
        val current = getCurrentItemsList().toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index != -1) {
            current[index] = item
            saveItems(current)
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
            removed
        } else {
            null
        }
    }

    suspend fun saveFromUrl(
        urlInput: String,
        customTitle: String? = null,
        userNote: String = "",
        customTags: List<String> = emptyList()
    ): PocketItem = withContext(Dispatchers.IO) {
        val normalizedUrl = if (!urlInput.startsWith("http://") && !urlInput.startsWith("https://")) {
            "https://$urlInput"
        } else {
            urlInput
        }
        val domain = PocketItem.extractDomain(normalizedUrl)

        var title = customTitle.orEmpty()
        var description = ""
        val extractedImages = mutableListOf<String>()

        // Try fetching lightweight web metadata
        try {
            val req = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) EmreGallery/1.0")
                .build()
            val response = httpClient.newCall(req).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (title.isBlank()) {
                    val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(body)
                    if (titleMatcher.find()) {
                        title = titleMatcher.group(1)?.trim()?.replace("&amp;", "&")?.replace("&#39;", "'") ?: ""
                    }
                }
                val descMatcher = Pattern.compile("<meta\\s+(?:name|property)=[\"'](?:description|og:description)[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE).matcher(body)
                if (descMatcher.find()) {
                    description = descMatcher.group(1)?.trim() ?: ""
                }
                val imgMatcher = Pattern.compile("<meta\\s+property=[\"']og:image[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE).matcher(body)
                if (imgMatcher.find()) {
                    val img = imgMatcher.group(1)?.trim()
                    if (!img.isNullOrBlank()) extractedImages.add(img)
                }
            }
        } catch (_: Exception) {
            // Network fallback or offline
        }

        if (title.isBlank()) {
            title = normalizedUrl.substringAfter("://").substringAfter("/").substringBefore("?").replace("-", " ").replace("_", " ").trim()
            if (title.isBlank() || title.length < 3) {
                title = domain.replaceFirstChar { it.uppercase() }
            } else {
                title = title.replaceFirstChar { it.uppercase() }
            }
        }

        val tags = if (customTags.isNotEmpty()) {
            customTags
        } else {
            listOf(domain.substringBefore(".").replaceFirstChar { it.uppercase() }, "Saved")
        }

        val newItem = PocketItem(
            id = UUID.randomUUID().toString(),
            title = title,
            url = normalizedUrl,
            domain = domain,
            imageUrls = extractedImages,
            timestamp = System.currentTimeMillis(),
            note = userNote,
            tags = tags,
            description = description.ifBlank { "Article saved from $domain." }
        )
        addItem(newItem)
        newItem
    }


    /**
     * One-time, non-destructive import from the legacy downloader Saved Library.
     * The old SharedPreferences key and media files are deliberately retained.
     */
    suspend fun migrateLegacySavedLibrary(): MigrationResult = withContext(Dispatchers.IO) {
        val marker = stringPreferencesKey("pocket_legacy_saved_library_migration_v1")
        val prefs = context.galleryDataStore.data.first()
        if (prefs[marker] == "complete") return@withContext MigrationResult(alreadyMigrated = true)

        val legacyPrefs = context.getSharedPreferences("media_inbox_prefs", Context.MODE_PRIVATE)
        val raw = legacyPrefs.getString("inbox_stacks_json", null)
        if (raw.isNullOrBlank()) {
            context.galleryDataStore.edit { it[marker] = "complete" }
            return@withContext MigrationResult()
        }

        val migrated = runCatching {
            val stacks = JSONArray(raw)
            buildList {
                for (i in 0 until stacks.length()) {
                    val stack = stacks.optJSONObject(i) ?: continue
                    val sourceUrl = stack.optString("sourceUrl", "")
                    val attachments = JSONArray()
                    val images = JSONArray()
                    val oldItems = stack.optJSONArray("items") ?: JSONArray()
                    for (j in 0 until oldItems.length()) {
                        val media = oldItems.optJSONObject(j) ?: continue
                        val localUri = media.optString("localUri", "")
                        if (localUri.isBlank()) continue
                        val isVideo = media.optBoolean("isVideo", false)
                        val isAudio = media.optBoolean("isAudio", false)
                        attachments.put(JSONObject().apply {
                            put("id", media.optString("id", UUID.randomUUID().toString()))
                            put("title", media.optString("title", "Downloaded media"))
                            put("localUri", localUri)
                            put("sourceUrl", media.optString("sourceUrl", sourceUrl))
                            put("isVideo", isVideo)
                            put("isAudio", isAudio)
                            put("isStream", media.optBoolean("isStream", false))
                            put("format", media.optString("format", ""))
                            put("sizeBytes", media.optLong("sizeBytes", 0L))
                            put("dateAddedMillis", media.optLong("dateAddedMillis", stack.optLong("timestampMillis", System.currentTimeMillis())))
                        })
                        if (!isVideo && !isAudio) images.put(localUri)
                    }
                    if (sourceUrl.isNotBlank() || attachments.length() > 0) add(JSONObject().apply {
                        put("id", stack.optString("id", UUID.randomUUID().toString()))
                        put("title", stack.optString("title", "Saved media").ifBlank { "Saved media" })
                        put("url", sourceUrl)
                        put("domain", stack.optString("sourceDomain", "").ifBlank { PocketItem.extractDomain(sourceUrl) })
                        put("imageUrls", images)
                        put("timestamp", stack.optLong("timestampMillis", System.currentTimeMillis()))
                        put("note", "")
                        put("tags", JSONArray(listOf("Migrated", "Saved media")))
                        put("description", stack.optString("description", ""))
                        put("isFavorite", false)
                        put("isRead", false)
                        put("attachments", attachments)
                    })
                }
            }
        }.getOrElse { error ->
            return@withContext MigrationResult(error = "Legacy Saved Library import failed: ${error.message ?: "invalid JSON"}")
        }

        val rawPocket = prefs[pocketKey]
        val destination = if (rawPocket.isNullOrBlank()) JSONArray() else runCatching { JSONArray(rawPocket) }.getOrElse {
            return@withContext MigrationResult(error = "Existing Pocket data is invalid; nothing was migrated.")
        }
        val existingIds = mutableSetOf<String>()
        for (i in 0 until destination.length()) existingIds.add(destination.optJSONObject(i)?.optString("id").orEmpty())
        var added = 0
        migrated.forEach { item ->
            if (existingIds.add(item.optString("id"))) {
                destination.put(item)
                added++
            }
        }
        context.galleryDataStore.edit {
            it[pocketKey] = destination.toString()
            it[marker] = "complete"
        }
        MigrationResult(migratedItems = added, sourceRecords = migrated.size)
    }

    data class MigrationResult(
        val migratedItems: Int = 0,
        val sourceRecords: Int = 0,
        val alreadyMigrated: Boolean = false,
        val error: String? = null
    )

    private suspend fun getCurrentItemsList(): List<PocketItem> {
        val prefs = context.galleryDataStore.data.first()
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
                put("attachments", JSONArray(item.attachments.map { JSONObject(it) }))

                val imgArr = JSONArray()
                item.imageUrls.forEach { imgArr.put(it) }
                put("imageUrls", imgArr)

                val tagArr = JSONArray()
                item.tags.forEach { tagArr.put(it) }
                put("tags", tagArr)
            }
            jsonArray.put(obj)
        }
        context.galleryDataStore.edit { it[pocketKey] = jsonArray.toString() }
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
                    attachments = obj.optJSONArray("attachments")?.let { arr ->
                        (0 until arr.length()).mapNotNull { index -> arr.optJSONObject(index)?.toString() }
                    } ?: emptyList()
                )
            )
        }
        return list.filterNot { it.id.startsWith("seed-") }
    }
}
