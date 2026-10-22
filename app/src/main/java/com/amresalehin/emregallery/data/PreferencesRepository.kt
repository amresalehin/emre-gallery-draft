package com.amresalehin.emregallery.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.galleryDataStore by preferencesDataStore(name = "emre_gallery")

/** Small local settings store. Media items are represented by stable content URI strings. */
class PreferencesRepository(private val context: Context) {
    private val favouritesKey = stringSetPreferencesKey("favourites")
    private val hiddenKey = stringSetPreferencesKey("hidden")
    private val binKey = stringSetPreferencesKey("bin")
    private val searchesKey = stringSetPreferencesKey("recent_searches")
    private val themeKey = stringPreferencesKey("theme")
    private val customAlbumsKey = stringPreferencesKey("custom_albums")
    private val archiveKey = stringSetPreferencesKey("archive")
    private val sortOrderKey = stringPreferencesKey("sort_order")
    private val groupByKey = stringPreferencesKey("group_by")
    private val gridColumnsKey = stringPreferencesKey("grid_columns")
    private val gridStyleKey = stringPreferencesKey("grid_style")

    val favourites: Flow<Set<String>> = context.galleryDataStore.data.map { it[favouritesKey] ?: emptySet() }
    val hidden: Flow<Set<String>> = context.galleryDataStore.data.map { it[hiddenKey] ?: emptySet() }
    val bin: Flow<Set<String>> = context.galleryDataStore.data.map { it[binKey] ?: emptySet() }
    val archive: Flow<Set<String>> = context.galleryDataStore.data.map { it[archiveKey] ?: emptySet() }
    val recentSearches: Flow<List<String>> = context.galleryDataStore.data.map { it[searchesKey]?.toList().orEmpty() }
    val theme: Flow<String> = context.galleryDataStore.data.map { it[themeKey] ?: "system" }
    val sortOrder: Flow<String> = context.galleryDataStore.data.map { it[sortOrderKey] ?: "date_desc" }
    val groupBy: Flow<String> = context.galleryDataStore.data.map { it[groupByKey] ?: "day" }
    val gridColumns: Flow<Int> = context.galleryDataStore.data.map { it[gridColumnsKey]?.toIntOrNull() ?: 3 }
    val gridStyle: Flow<String> = context.galleryDataStore.data.map { it[gridStyleKey] ?: "masonry" }

    val customAlbums: Flow<List<CustomAlbum>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[customAlbumsKey] ?: "[]"
        runCatching {
            val array = JSONArray(raw)
            val list = mutableListOf<CustomAlbum>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.optString("name", "Untitled")
                val parentPath = obj.optString("parentPath", "")
                val urisArr = obj.optJSONArray("mediaUris")
                val uris = mutableSetOf<String>()
                if (urisArr != null) {
                    for (j in 0 until urisArr.length()) {
                        uris.add(urisArr.getString(j))
                    }
                }
                list.add(CustomAlbum(id = id, name = name, parentPath = parentPath, mediaUris = uris))
            }
            list
        }.getOrDefault(emptyList())
    }

    private suspend fun saveCustomAlbums(albums: List<CustomAlbum>) {
        val array = JSONArray()
        albums.forEach { album ->
            val obj = JSONObject()
            obj.put("id", album.id)
            obj.put("name", album.name)
            obj.put("parentPath", album.parentPath)
            val urisArr = JSONArray()
            album.mediaUris.forEach { urisArr.put(it) }
            obj.put("mediaUris", urisArr)
            array.put(obj)
        }
        context.galleryDataStore.edit { it[customAlbumsKey] = array.toString() }
    }

    suspend fun createCustomAlbum(name: String, parentPath: String = ""): CustomAlbum {
        val current = customAlbums.first()
        val newAlbum = CustomAlbum(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            parentPath = parentPath.trim()
        )
        saveCustomAlbums(current + newAlbum)
        return newAlbum
    }

    suspend fun deleteCustomAlbum(albumId: String) {
        val current = customAlbums.first()
        saveCustomAlbums(current.filterNot { it.id == albumId })
    }

    suspend fun renameCustomAlbum(albumId: String, newName: String) {
        val current = customAlbums.first()
        saveCustomAlbums(current.map {
            if (it.id == albumId) it.copy(name = newName.trim()) else it
        })
    }

    suspend fun addMediaToCustomAlbum(albumId: String, uri: String) {
        val current = customAlbums.first()
        saveCustomAlbums(current.map {
            if (it.id == albumId) it.copy(mediaUris = it.mediaUris + uri) else it
        })
    }

    suspend fun removeMediaFromCustomAlbum(albumId: String, uri: String) {
        val current = customAlbums.first()
        saveCustomAlbums(current.map {
            if (it.id == albumId) it.copy(mediaUris = it.mediaUris - uri) else it
        })
    }

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
    suspend fun setArchived(uri: String, archived: Boolean) = context.galleryDataStore.edit {
        val values = (it[archiveKey] ?: emptySet()).toMutableSet()
        if (archived) values.add(uri) else values.remove(uri)
        it[archiveKey] = values
    }
    suspend fun addRecentSearch(query: String) = context.galleryDataStore.edit {
        val values = (it[searchesKey] ?: emptySet()).toMutableList()
        values.remove(query)
        values.add(0, query)
        it[searchesKey] = values.take(12).toSet()
    }
    suspend fun clearRecentSearches() = context.galleryDataStore.edit { it.remove(searchesKey) }
    suspend fun setTheme(value: String) = context.galleryDataStore.edit { it[themeKey] = value }
    suspend fun setSortOrder(value: String) = context.galleryDataStore.edit { it[sortOrderKey] = value }
    suspend fun setGroupBy(value: String) = context.galleryDataStore.edit { it[groupByKey] = value }
    suspend fun setGridColumns(value: Int) = context.galleryDataStore.edit { it[gridColumnsKey] = value.toString() }
    suspend fun setGridStyle(value: String) = context.galleryDataStore.edit { it[gridStyleKey] = value }

    private val mediaTagsKey = stringPreferencesKey("media_custom_tags")
    private val mediaPeopleKey = stringPreferencesKey("media_custom_people")

    val customTagsMap: Flow<Map<String, Set<String>>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[mediaTagsKey] ?: "{}"
        runCatching {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Set<String>>()
            obj.keys().forEach { uri ->
                val arr = obj.getJSONArray(uri)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                map[uri] = set
            }
            map
        }.getOrDefault(emptyMap())
    }

    suspend fun addTagForMedia(uri: String, tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        context.galleryDataStore.edit { prefs ->
            val raw = prefs[mediaTagsKey] ?: "{}"
            val obj = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            val existing = mutableSetOf<String>()
            if (obj.has(uri)) {
                val arr = obj.getJSONArray(uri)
                for (i in 0 until arr.length()) existing.add(arr.getString(i))
            }
            existing.add(trimmed)
            val newArr = JSONArray()
            existing.forEach { newArr.put(it) }
            obj.put(uri, newArr)
            prefs[mediaTagsKey] = obj.toString()
        }
    }

    val customPeopleMap: Flow<Map<String, Set<String>>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[mediaPeopleKey] ?: "{}"
        runCatching {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Set<String>>()
            obj.keys().forEach { uri ->
                val arr = obj.getJSONArray(uri)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                map[uri] = set
            }
            map
        }.getOrDefault(emptyMap())
    }

    suspend fun addPersonForMedia(uri: String, person: String) {
        val trimmed = person.trim()
        if (trimmed.isBlank()) return
        context.galleryDataStore.edit { prefs ->
            val raw = prefs[mediaPeopleKey] ?: "{}"
            val obj = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            val existing = mutableSetOf<String>()
            if (obj.has(uri)) {
                val arr = obj.getJSONArray(uri)
                for (i in 0 until arr.length()) existing.add(arr.getString(i))
            }
            existing.add(trimmed)
            val newArr = JSONArray()
            existing.forEach { newArr.put(it) }
            obj.put(uri, newArr)
            prefs[mediaPeopleKey] = obj.toString()
        }
    }
}
