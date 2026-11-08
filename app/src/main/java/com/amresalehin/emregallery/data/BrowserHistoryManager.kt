package com.amresalehin.emregallery.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.UUID

data class BrowserSite(
    val url: String,
    val title: String,
    val domain: String,
    val visitCount: Int = 1,
    val lastVisitedMillis: Long = System.currentTimeMillis()
)

data class BrowserShortcut(
    val id: String,
    val title: String,
    val url: String,
    val iconKey: String = ""
)

data class BrowserTabItem(
    val id: String,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manages persistent browser history, shortcuts (flexible pinning), and tabs.
 */
class BrowserHistoryManager(internal val context: Context) {

    internal val historyKey = stringPreferencesKey("browser_sites_history_v1")
    private val searchQueriesKey = stringPreferencesKey("browser_downloader_recent_searches_v1")
    internal val shortcutsKey = stringPreferencesKey("browser_pinned_shortcuts_v1")
    internal val tabsKey = stringPreferencesKey("browser_open_tabs_v1")

    val defaultTopSites: List<BrowserSite> = emptyList()

    // 1. Pinned Shortcuts (User-pinned, empty by default)
    val defaultShortcuts: List<BrowserShortcut> = emptyList()

    val shortcuts: Flow<List<BrowserShortcut>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[shortcutsKey]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<BrowserShortcut>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        BrowserShortcut(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.getString("title"),
                            url = obj.getString("url"),
                            iconKey = obj.optString("iconKey", "")
                        )
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }.distinctUntilChanged()


    suspend fun addShortcut(title: String, url: String, iconKey: String = "") =
        addShortcutImpl(title, url, iconKey)

    suspend fun updateShortcut(id: String, newTitle: String, newUrl: String) =
        updateShortcutImpl(id, newTitle, newUrl)

    suspend fun removeShortcut(id: String) = removeShortcutImpl(id)

    val defaultTabs: List<BrowserTabItem> = emptyList()

    val openTabs: Flow<List<BrowserTabItem>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[tabsKey]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<BrowserTabItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        BrowserTabItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.optString("title", "Tab"),
                            url = obj.optString("url", ""),
                            timestamp = obj.optLong("time", System.currentTimeMillis())
                        )
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }.distinctUntilChanged()


    suspend fun saveTabs(tabs: List<BrowserTabItem>) = saveTabsImpl(tabs)

    val defaultRecentSites: List<BrowserSite> = emptyList()

    val recentSearchQueries: Flow<List<String>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[searchQueriesKey]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val q = array.getString(i).trim()
                    if (q.isNotEmpty()) list.add(q)
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }.distinctUntilChanged()

    suspend fun recordSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.galleryDataStore.edit { prefs ->
            val raw = prefs[searchQueriesKey]
            val list = mutableListOf<String>()
            if (!raw.isNullOrBlank()) {
                try {
                    val array = JSONArray(raw)
                    for (i in 0 until array.length()) {
                        val item = array.getString(i).trim()
                        if (item.isNotEmpty() && !item.equals(trimmed, ignoreCase = true)) {
                            list.add(item)
                        }
                    }
                } catch (_: Exception) {}
            }
            list.add(0, trimmed)
            val jsonArray = JSONArray()
            for (q in list.take(15)) {
                jsonArray.put(q)
            }
            prefs[searchQueriesKey] = jsonArray.toString()
        }
    }

    val allSites: Flow<List<BrowserSite>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[historyKey]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<BrowserSite>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        BrowserSite(
                            url = obj.getString("url"),
                            title = obj.optString("title", ""),
                            domain = obj.optString("domain", ""),
                            visitCount = obj.optInt("count", 1),
                            lastVisitedMillis = obj.optLong("time", 0L)
                        )
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }.distinctUntilChanged()

    suspend fun recordVisit(rawUrl: String, title: String? = null) = recordVisitImpl(rawUrl, title)
    suspend fun deleteSite(url: String) = deleteSiteImpl(url)
    suspend fun clearHistory() = clearHistoryImpl()
}
