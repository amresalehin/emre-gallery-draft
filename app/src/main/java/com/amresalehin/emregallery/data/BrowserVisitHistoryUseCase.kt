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


internal suspend fun BrowserHistoryManager.recordVisitImpl(rawUrl: String, title: String? = null) {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank() || trimmed == "about:blank" || trimmed.startsWith("javascript:")) return
    val domain = runCatching {
        val host = URL(trimmed).host
        host.removePrefix("www.")
    }.getOrDefault("").ifBlank {
        trimmed.substringAfter("://").substringBefore("/").removePrefix("www.")
    }
    if (domain.isBlank()) return

    val displayTitle = title?.takeIf { it.isNotBlank() && it != "about:blank" && !it.startsWith("http") }
        ?: domain.substringBefore('.').replaceFirstChar { it.uppercase() }

    context.galleryDataStore.edit { prefs ->
        val raw = prefs[historyKey]
        val list = if (raw.isNullOrBlank()) {
            mutableListOf<BrowserSite>()
        } else {
            try {
                val array = JSONArray(raw)
                val l = mutableListOf<BrowserSite>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    l.add(
                        BrowserSite(
                            url = obj.getString("url"),
                            title = obj.optString("title", ""),
                            domain = obj.optString("domain", ""),
                            visitCount = obj.optInt("count", 1),
                            lastVisitedMillis = obj.optLong("time", 0L)
                        )
                    )
                }
                l
            } catch (_: Exception) {
                mutableListOf<BrowserSite>()
            }
        }

        // Find existing site matching normalized domain or url
        val existingIndex = list.indexOfFirst {
            it.domain.equals(domain, ignoreCase = true) || it.url.equals(trimmed, ignoreCase = true)
        }

        if (existingIndex >= 0) {
            val existing = list[existingIndex]
            list[existingIndex] = existing.copy(
                url = trimmed,
                title = if (displayTitle.isNotBlank() && displayTitle != existing.domain) displayTitle else existing.title,
                visitCount = existing.visitCount + 1,
                lastVisitedMillis = System.currentTimeMillis()
            )
        } else {
            list.add(
                0,
                BrowserSite(
                    url = trimmed,
                    title = displayTitle,
                    domain = domain,
                    visitCount = 1,
                    lastVisitedMillis = System.currentTimeMillis()
                )
            )
        }

        // Keep top 50
        val trimmedList = list.sortedByDescending { it.lastVisitedMillis }.take(50)
        val jsonArray = JSONArray()
        for (s in trimmedList) {
            val obj = JSONObject()
            obj.put("url", s.url)
            obj.put("title", s.title)
            obj.put("domain", s.domain)
            obj.put("count", s.visitCount)
            obj.put("time", s.lastVisitedMillis)
            jsonArray.put(obj)
        }
        prefs[historyKey] = jsonArray.toString()
    }
}

internal suspend fun BrowserHistoryManager.deleteSiteImpl(url: String) {
    context.galleryDataStore.edit { prefs ->
        val raw = prefs[historyKey]
        val list = if (raw.isNullOrBlank()) {
            mutableListOf<BrowserSite>()
        } else {
            try {
                val array = JSONArray(raw)
                val l = mutableListOf<BrowserSite>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    l.add(
                        BrowserSite(
                            url = obj.getString("url"),
                            title = obj.optString("title", ""),
                            domain = obj.optString("domain", ""),
                            visitCount = obj.optInt("count", 1),
                            lastVisitedMillis = obj.optLong("time", 0L)
                        )
                    )
                }
                l
            } catch (_: Exception) {
                mutableListOf()
            }
        }
        list.removeAll { it.url.equals(url, ignoreCase = true) }
        val jsonArray = JSONArray()
        for (s in list) {
            val obj = JSONObject()
            obj.put("url", s.url)
            obj.put("title", s.title)
            obj.put("domain", s.domain)
            obj.put("count", s.visitCount)
            obj.put("time", s.lastVisitedMillis)
            jsonArray.put(obj)
        }
        prefs[historyKey] = jsonArray.toString()
    }
}

internal suspend fun BrowserHistoryManager.clearHistoryImpl() {
    context.galleryDataStore.edit { prefs ->
        // Store empty array to explicitly indicate user cleared history
        prefs[historyKey] = "[]"
    }
}
