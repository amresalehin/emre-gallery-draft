package com.amresalehin.emregallery.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.amresalehin.emregallery.data.galleryDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/**
 * Persists ordered recent search history in DataStore with backward-compatible legacy migration.
 */
internal class RecentSearchesPreferencesStore(private val context: Context) {

    private val legacySearchesKey = androidx.datastore.preferences.core.stringSetPreferencesKey("recent_searches")
    private val searchesKey = stringPreferencesKey("recent_searches_v2")

    val recentSearches: Flow<List<String>> = context.galleryDataStore.data.map { prefs ->
        decodeSearches(prefs, sortLegacy = true)
    }.distinctUntilChanged()

    suspend fun addRecentSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        context.galleryDataStore.edit { prefs ->
            val list = decodeSearches(prefs, sortLegacy = false).toMutableList()
            list.remove(normalized)
            list.add(0, normalized)
            val trimmed = list.take(MAX_RECENT_SEARCHES)
            prefs[searchesKey] = JSONArray(trimmed).toString()
        }
    }

    suspend fun clearRecentSearches() {
        context.galleryDataStore.edit { prefs ->
            prefs.remove(searchesKey)
            prefs.remove(legacySearchesKey)
        }
    }

    private fun decodeSearches(prefs: Preferences, sortLegacy: Boolean): List<String> {
        val rawV2 = prefs[searchesKey]
        if (rawV2 != null) {
            return try {
                val array = JSONArray(rawV2)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
        val legacy = prefs[legacySearchesKey]?.toList() ?: return emptyList()
        return if (sortLegacy) legacy.sorted() else legacy
    }

    private companion object {
        const val MAX_RECENT_SEARCHES = 20
    }
}
