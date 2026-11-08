package com.amresalehin.emregallery.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dedicated, independent repository for Pocket bookmarks and saved web reading materials.
 * Decoupled completely from Downloader and MediaInbox functionality.
 * Supports automatic background fetching of link titles, descriptions, and previews.
 */
class PocketRepository(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pocket_bookmarks_prefs", Context.MODE_PRIVATE)

    private val _bookmarks = MutableStateFlow<List<MediaInboxStack>>(emptyList())
    val bookmarks: StateFlow<List<MediaInboxStack>> = _bookmarks.asStateFlow()

    init {
        loadBookmarks()
    }

    private fun loadBookmarks() {
        val rawJson = prefs.getString(KEY_BOOKMARKS_JSON, null)
        if (rawJson.isNullOrBlank()) {
            migrateFromLegacyInboxIfNeeded()
            return
        }

        try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<MediaInboxStack>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    MediaInboxStack(
                        id = obj.getString("id"),
                        title = obj.optString("title", "Untitled Bookmark"),
                        sourceDomain = obj.optString("sourceDomain", ""),
                        sourceUrl = obj.optString("sourceUrl", ""),
                        items = emptyList(), // Pocket bookmarks are strictly decoupled from downloaded media files
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                        description = obj.optString("description", ""),
                        previewImageUrl = obj.optString("previewImageUrl", "")
                    )
                )
            }
            _bookmarks.value = list
            // Automatically fetch missing metadata (titles, descriptions, previews) for bookmarks
            autoFetchMissingMetadata()
        } catch (e: Exception) {
            _bookmarks.value = emptyList()
        }
    }

    private fun migrateFromLegacyInboxIfNeeded() {
        try {
            val legacyPrefs = context.getSharedPreferences("media_inbox_prefs", Context.MODE_PRIVATE)
            val legacyJson = legacyPrefs.getString("inbox_stacks_json", null)
            if (!legacyJson.isNullOrBlank()) {
                val jsonArray = JSONArray(legacyJson)
                val list = mutableListOf<MediaInboxStack>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val itemsArr = obj.optJSONArray("items")
                    val isBookmark = id.startsWith("bookmark_") || itemsArr == null || itemsArr.length() == 0
                    if (isBookmark) {
                        list.add(
                            MediaInboxStack(
                                id = id,
                                title = obj.optString("title", "Untitled Bookmark"),
                                sourceDomain = obj.optString("sourceDomain", ""),
                                sourceUrl = obj.optString("sourceUrl", ""),
                                items = emptyList(),
                                timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                                description = obj.optString("description", ""),
                                previewImageUrl = obj.optString("previewImageUrl", "")
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) {
                    _bookmarks.value = list
                    persist()
                    autoFetchMissingMetadata()
                }
            }
        } catch (_: Exception) {}
    }

    private fun persist() {
        try {
            val jsonArray = JSONArray()
            _bookmarks.value.forEach { bookmark ->
                val obj = JSONObject().apply {
                    put("id", bookmark.id)
                    put("title", bookmark.title)
                    put("sourceDomain", bookmark.sourceDomain)
                    put("sourceUrl", bookmark.sourceUrl)
                    put("timestampMillis", bookmark.timestampMillis)
                    put("description", bookmark.description)
                    put("previewImageUrl", bookmark.previewImageUrl)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_BOOKMARKS_JSON, jsonArray.toString()).apply()
        } catch (_: Exception) {}
    }

    /**
     * Adds a bookmark to Pocket.
     * When autoFetchMetadata is true, automatically queries the web page in the background
     * for its title, description, and link preview image, then smoothly updates the bookmark.
     */
    fun addBookmark(
        title: String,
        url: String,
        domain: String = "",
        description: String = "",
        previewImageUrl: String = "",
        autoFetchMetadata: Boolean = true
    ): MediaInboxStack {
        val cleanUrl = url.trim()
        val cleanDomain = domain.ifBlank { MediaClassifier.cleanDomainFromUrl(cleanUrl) }
        val bookmark = MediaInboxStack(
            id = "bookmark_${System.currentTimeMillis()}",
            title = title.ifBlank { cleanDomain.ifBlank { cleanUrl } },
            sourceDomain = cleanDomain,
            sourceUrl = cleanUrl,
            items = emptyList(),
            timestampMillis = System.currentTimeMillis(),
            description = description,
            previewImageUrl = previewImageUrl
        )
        val updated = listOf(bookmark) + _bookmarks.value.filter {
            it.id != bookmark.id && (it.sourceUrl.isBlank() || it.sourceUrl != cleanUrl)
        }
        _bookmarks.value = updated
        persist()

        if (autoFetchMetadata && cleanUrl.isNotBlank()) {
            fetchAndApplyMetadata(bookmark.id, cleanUrl)
        }

        return bookmark
    }

    /**
     * Automatically fetches metadata (title, description, preview image) for a bookmark and updates it.
     */
    fun fetchAndApplyMetadata(bookmarkId: String, url: String = "") {
        coroutineScope.launch {
            try {
                val current = _bookmarks.value.find { it.id == bookmarkId } ?: return@launch
                val targetUrl = url.ifBlank { current.sourceUrl }
                if (targetUrl.isBlank()) return@launch

                val meta = LinkMetadataFetcher.fetch(targetUrl)
                val latest = _bookmarks.value.find { it.id == bookmarkId } ?: return@launch

                val cleanDomain = latest.sourceDomain.ifBlank { MediaClassifier.cleanDomainFromUrl(targetUrl) }
                val isTitleDefault = latest.title.isBlank() ||
                        latest.title.equals(cleanDomain, ignoreCase = true) ||
                        latest.title.equals(targetUrl, ignoreCase = true) ||
                        latest.title == "Untitled Bookmark"

                val newTitle = if (isTitleDefault && meta.title.isNotBlank()) meta.title else latest.title
                val newDesc = if (latest.description.isBlank() && meta.description.isNotBlank()) meta.description else latest.description
                val newPreview = if (latest.previewImageUrl.isBlank() && meta.previewImageUrl.isNotBlank()) meta.previewImageUrl else latest.previewImageUrl
                val newDomain = if (latest.sourceDomain.isBlank() && meta.domain.isNotBlank()) meta.domain else latest.sourceDomain

                if (newTitle != latest.title || newDesc != latest.description || newPreview != latest.previewImageUrl || newDomain != latest.sourceDomain) {
                    val updated = latest.copy(
                        title = newTitle,
                        description = newDesc,
                        previewImageUrl = newPreview,
                        sourceDomain = newDomain
                    )
                    updateBookmark(updated)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching metadata for bookmark $bookmarkId", e)
            }
        }
    }

    /**
     * Re-fetches the latest title, description, and preview from the web for a given bookmark.
     */
    fun refreshMetadata(bookmarkId: String) {
        coroutineScope.launch {
            try {
                val latest = _bookmarks.value.find { it.id == bookmarkId } ?: return@launch
                if (latest.sourceUrl.isBlank()) return@launch

                val meta = LinkMetadataFetcher.fetch(latest.sourceUrl)
                val updated = latest.copy(
                    title = meta.title.ifBlank { latest.title },
                    description = meta.description.ifBlank { latest.description },
                    previewImageUrl = meta.previewImageUrl.ifBlank { latest.previewImageUrl },
                    sourceDomain = meta.domain.ifBlank { latest.sourceDomain }
                )
                updateBookmark(updated)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing metadata for bookmark $bookmarkId", e)
            }
        }
    }

    /**
     * Checks all stored bookmarks and automatically fetches any missing previews or descriptions.
     */
    fun autoFetchMissingMetadata() {
        coroutineScope.launch {
            val missing = _bookmarks.value.filter {
                it.sourceUrl.isNotBlank() && (it.previewImageUrl.isBlank() || it.description.isBlank())
            }
            for (bm in missing) {
                try {
                    val meta = LinkMetadataFetcher.fetch(bm.sourceUrl)
                    val current = _bookmarks.value.find { it.id == bm.id } ?: continue

                    val cleanDomain = current.sourceDomain.ifBlank { MediaClassifier.cleanDomainFromUrl(bm.sourceUrl) }
                    val isTitleDefault = current.title.isBlank() ||
                            current.title.equals(cleanDomain, ignoreCase = true) ||
                            current.title.equals(bm.sourceUrl, ignoreCase = true) ||
                            current.title == "Untitled Bookmark"

                    val newTitle = if (isTitleDefault && meta.title.isNotBlank()) meta.title else current.title
                    val newDesc = if (current.description.isBlank() && meta.description.isNotBlank()) meta.description else current.description
                    val newPreview = if (current.previewImageUrl.isBlank() && meta.previewImageUrl.isNotBlank()) meta.previewImageUrl else current.previewImageUrl

                    if (newTitle != current.title || newDesc != current.description || newPreview != current.previewImageUrl) {
                        updateBookmark(
                            current.copy(
                                title = newTitle,
                                description = newDesc,
                                previewImageUrl = newPreview
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun updateBookmark(updated: MediaInboxStack) {
        _bookmarks.value = _bookmarks.value.map { if (it.id == updated.id) updated else it }
        persist()
    }

    fun deleteBookmark(bookmarkId: String) {
        _bookmarks.value = _bookmarks.value.filter { it.id != bookmarkId }
        persist()
    }

    fun deleteBookmarks(bookmarkIds: Set<String>) {
        _bookmarks.value = _bookmarks.value.filter { it.id !in bookmarkIds }
        persist()
    }

    fun clearAll() {
        _bookmarks.value = emptyList()
        prefs.edit().remove(KEY_BOOKMARKS_JSON).apply()
    }

    companion object {
        private const val TAG = "PocketRepository"
        private const val KEY_BOOKMARKS_JSON = "pocket_bookmarks_json"

        @Volatile
        private var INSTANCE: PocketRepository? = null

        fun getInstance(context: Context): PocketRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PocketRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
