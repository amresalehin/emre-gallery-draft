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


internal suspend fun BrowserHistoryManager.addShortcutImpl(title: String, url: String, iconKey: String = "") {
    val cleanTitle = title.trim()
    val cleanUrl = MediaClassifier.formatQueryOrUrl(url.trim())
    if (cleanTitle.isBlank() || cleanUrl.isBlank()) return

    context.galleryDataStore.edit { prefs ->
        val currentList = loadShortcutsFromPrefs(prefs[shortcutsKey]).toMutableList()
        // Deduplicate if URL already pinned
        currentList.removeAll { it.url.equals(cleanUrl, ignoreCase = true) }
        val determinedKey = if (iconKey.isNotBlank()) iconKey else determineIconKey(cleanUrl, cleanTitle)
        currentList.add(
            BrowserShortcut(
                id = "sc_${System.currentTimeMillis()}",
                title = cleanTitle,
                url = cleanUrl,
                iconKey = determinedKey
            )
        )
        prefs[shortcutsKey] = serializeShortcuts(currentList)
    }
}

internal suspend fun BrowserHistoryManager.updateShortcutImpl(id: String, newTitle: String, newUrl: String) {
    val cleanTitle = newTitle.trim()
    val cleanUrl = MediaClassifier.formatQueryOrUrl(newUrl.trim())
    if (cleanTitle.isBlank() || cleanUrl.isBlank()) return

    context.galleryDataStore.edit { prefs ->
        val currentList = loadShortcutsFromPrefs(prefs[shortcutsKey]).toMutableList()
        val idx = currentList.indexOfFirst { it.id == id }
        if (idx >= 0) {
            currentList[idx] = currentList[idx].copy(
                title = cleanTitle,
                url = cleanUrl,
                iconKey = determineIconKey(cleanUrl, cleanTitle)
            )
            prefs[shortcutsKey] = serializeShortcuts(currentList)
        }
    }
}

internal suspend fun BrowserHistoryManager.removeShortcutImpl(id: String) {
    context.galleryDataStore.edit { prefs ->
        val currentList = loadShortcutsFromPrefs(prefs[shortcutsKey]).toMutableList()
        currentList.removeAll { it.id == id }
        prefs[shortcutsKey] = serializeShortcuts(currentList)
    }
}

internal fun BrowserHistoryManager.loadShortcutsFromPrefs(raw: String?): List<BrowserShortcut> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
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

internal fun BrowserHistoryManager.serializeShortcuts(list: List<BrowserShortcut>): String {
    val array = JSONArray()
    for (item in list) {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("title", item.title)
        obj.put("url", item.url)
        obj.put("iconKey", item.iconKey)
        array.put(obj)
    }
    return array.toString()
}

internal fun BrowserHistoryManager.determineIconKey(url: String, title: String): String {
    val lower = url.lowercase()
    return when {
        lower.contains("youtube.com") || lower.contains("youtu.be") -> "youtube"
        lower.contains("wikipedia.org") -> "wikipedia"
        lower.contains("github.com") -> "github"
        lower.contains("drive.google.com") -> "drive"
        lower.contains("duckduckgo.com") -> "duckduckgo"
        lower.contains("claude.ai") -> "claude"
        lower.contains("anthropic.com") -> "anthropic"
        else -> ""
    }
}

// 2. Open Tabs (Real user tabs only, empty by default)

internal suspend fun BrowserHistoryManager.saveTabsImpl(tabs: List<BrowserTabItem>) {
    context.galleryDataStore.edit { prefs ->
        val array = JSONArray()
        for (tab in tabs) {
            val obj = JSONObject()
            obj.put("id", tab.id)
            obj.put("title", tab.title)
            obj.put("url", tab.url)
            obj.put("time", tab.timestamp)
            array.put(obj)
        }
        prefs[tabsKey] = array.toString()
    }
}

// 3. Recent History Sites (Real visited sites only, empty by default)
