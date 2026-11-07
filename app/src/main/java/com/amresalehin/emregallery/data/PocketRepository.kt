package com.amresalehin.emregallery.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dedicated, independent repository for Pocket bookmarks and saved web reading materials.
 * Decoupled completely from Downloader and MediaInbox functionality.
 */
class PocketRepository(private val context: Context) {
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
                        description = obj.optString("description", "")
                    )
                )
            }
            _bookmarks.value = list
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
                                description = obj.optString("description", "")
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) {
                    _bookmarks.value = list
                    persist()
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
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_BOOKMARKS_JSON, jsonArray.toString()).apply()
        } catch (_: Exception) {}
    }

    fun addBookmark(
        title: String,
        url: String,
        domain: String = "",
        description: String = ""
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
            description = description
        )
        val updated = listOf(bookmark) + _bookmarks.value.filter {
            it.id != bookmark.id && (it.sourceUrl.isBlank() || it.sourceUrl != cleanUrl)
        }
        _bookmarks.value = updated
        persist()
        return bookmark
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
