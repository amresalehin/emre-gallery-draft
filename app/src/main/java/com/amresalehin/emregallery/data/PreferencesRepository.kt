package com.amresalehin.emregallery.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.galleryDataStore by preferencesDataStore(name = "emre_gallery")

/** Small local settings store. Media items are represented by stable content URI strings. */
class PreferencesRepository(private val context: Context) {
    private val favouritesKey = stringSetPreferencesKey("favourites")
    private val hiddenKey = stringSetPreferencesKey("hidden")
    private val binKey = stringSetPreferencesKey("bin")
    private val searchesKey = stringSetPreferencesKey("recent_searches")
    private val themeKey = stringPreferencesKey("theme")

    val favourites: Flow<Set<String>> = context.galleryDataStore.data.map { it[favouritesKey] ?: emptySet() }
    val hidden: Flow<Set<String>> = context.galleryDataStore.data.map { it[hiddenKey] ?: emptySet() }
    val bin: Flow<Set<String>> = context.galleryDataStore.data.map { it[binKey] ?: emptySet() }
    val recentSearches: Flow<List<String>> = context.galleryDataStore.data.map { it[searchesKey]?.toList().orEmpty() }
    val theme: Flow<String> = context.galleryDataStore.data.map { it[themeKey] ?: "system" }

    suspend fun toggleFavourite(uri: String) = context.galleryDataStore.edit {
        val values = (it[favouritesKey] ?: emptySet()).toMutableSet()
        if (!values.add(uri)) values.remove(uri)
        it[favouritesKey] = values
    }
    suspend fun setHidden(uri: String, hidden: Boolean) = context.galleryDataStore.edit {
        val values = (it[hiddenKey] ?: emptySet()).toMutableSet()
        if (hidden) values.add(uri) else values.remove(uri)
        it[hiddenKey] = values
    }
    suspend fun setInBin(uri: String, inBin: Boolean) = context.galleryDataStore.edit {
        val values = (it[binKey] ?: emptySet()).toMutableSet()
        if (inBin) values.add(uri) else values.remove(uri)
        it[binKey] = values
    }
    suspend fun addRecentSearch(query: String) = context.galleryDataStore.edit {
        val values = (it[searchesKey] ?: emptySet()).toMutableList()
        values.remove(query)
        values.add(0, query)
        it[searchesKey] = values.take(12).toSet()
    }
    suspend fun clearRecentSearches() = context.galleryDataStore.edit { it.remove(searchesKey) }
    suspend fun setTheme(value: String) = context.galleryDataStore.edit { it[themeKey] = value }
}
