package com.amresalehin.emregallery.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.galleryDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Manages media tags, people assignments, albums, favorites, bin, archive, and recent search history.
 */
class MediaCollectionsPreferencesManager(internal val context: Context) {

    internal val favouritesKey = stringSetPreferencesKey("favourites")
    internal val hiddenKey = stringSetPreferencesKey("hidden")
    internal val binKey = stringSetPreferencesKey("bin")
    internal val archiveKey = stringSetPreferencesKey("archive")
    internal val legacySearchesKey = stringSetPreferencesKey("recent_searches")
    internal val searchesKey = stringPreferencesKey("recent_searches_v2")
    internal val customAlbumsKey = stringPreferencesKey("custom_albums")
    internal val mediaTagsKey = stringPreferencesKey("media_custom_tags")
    internal val mediaPeopleKey = stringPreferencesKey("media_custom_people")

    val favourites: Flow<Set<String>> = context.galleryDataStore.data.map {
        it[favouritesKey] ?: emptySet()
    }.distinctUntilChanged()

    val hidden: Flow<Set<String>> = context.galleryDataStore.data.map {
        it[hiddenKey] ?: emptySet()
    }.distinctUntilChanged()

    val bin: Flow<Set<String>> = context.galleryDataStore.data.map {
        it[binKey] ?: emptySet()
    }.distinctUntilChanged()

    val archive: Flow<Set<String>> = context.galleryDataStore.data.map {
        it[archiveKey] ?: emptySet()
    }.distinctUntilChanged()

    val recentSearches: Flow<List<String>> = context.galleryDataStore.data.map { prefs ->
        val rawV2 = prefs[searchesKey]
        if (rawV2 != null) {
            try {
                val array = JSONArray(rawV2)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            prefs[legacySearchesKey]?.toList()?.sorted() ?: emptyList()
        }
    }.distinctUntilChanged()

    val customAlbums: Flow<List<CustomAlbum>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[customAlbumsKey] ?: "[]"
        try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<CustomAlbum>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                val parentPath = obj.optString("parentPath", "")
                val urisArray = obj.optJSONArray("mediaUris")
                val uris = mutableSetOf<String>()
                if (urisArray != null) {
                    for (j in 0 until urisArray.length()) {
                        uris.add(urisArray.getString(j))
                    }
                }
                if (id.isNotEmpty() && name.isNotEmpty()) {
                    list.add(CustomAlbum(id, name, parentPath, uris))
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }.distinctUntilChanged()

    val customTagsMap: Flow<Map<String, Set<String>>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[mediaTagsKey] ?: "{}"
        try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Set<String>>()
            for (key in obj.keys()) {
                val arr = obj.getJSONArray(key)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    set.add(arr.getString(i))
                }
                map[key] = set
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }.distinctUntilChanged()

    val customPeopleMap: Flow<Map<String, Set<String>>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[mediaPeopleKey] ?: "{}"
        try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Set<String>>()
            for (key in obj.keys()) {
                val arr = obj.getJSONArray(key)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    set.add(arr.getString(i))
                }
                map[key] = set
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }.distinctUntilChanged()


    suspend fun saveCustomAlbums(albums: List<CustomAlbum>) = saveCustomAlbumsImpl(albums)
    suspend fun createCustomAlbum(name: String, parentPath: String = ""): CustomAlbum = createCustomAlbumImpl(name, parentPath)
    suspend fun deleteCustomAlbum(albumId: String) = deleteCustomAlbumImpl(albumId)
    suspend fun renameCustomAlbum(albumId: String, newName: String) = renameCustomAlbumImpl(albumId, newName)
    suspend fun addMediaToCustomAlbum(albumId: String, uri: String) = addMediaToCustomAlbumImpl(albumId, uri)
    suspend fun removeMediaFromCustomAlbum(albumId: String, uri: String) = removeMediaFromCustomAlbumImpl(albumId, uri)
    suspend fun toggleFavourite(uri: String) = toggleFavouriteImpl(uri)
    suspend fun setHidden(uri: String, hiddenVal: Boolean) = setHiddenImpl(uri, hiddenVal)
    suspend fun setHiddenBatch(uris: Collection<String>, hiddenVal: Boolean) = setHiddenBatchImpl(uris, hiddenVal)
    suspend fun setInBin(uri: String, inBin: Boolean) = setInBinImpl(uri, inBin)
    suspend fun setInBinBatch(uris: Collection<String>, inBin: Boolean) = setInBinBatchImpl(uris, inBin)
    suspend fun removePermanently(uris: Collection<String>) = removePermanentlyImpl(uris)

    suspend fun setArchived(uri: String, archivedVal: Boolean) {
        context.galleryDataStore.edit { prefs ->
            val set = (prefs[archiveKey] ?: emptySet()).toMutableSet()
            if (archivedVal) set.add(uri) else set.remove(uri)
            prefs[archiveKey] = set
        }
    }

    suspend fun setArchivedBatch(uris: Collection<String>, archivedVal: Boolean) {
        if (uris.isEmpty()) return
        context.galleryDataStore.edit { prefs ->
            val set = (prefs[archiveKey] ?: emptySet()).toMutableSet()
            if (archivedVal) set.addAll(uris) else set.removeAll(uris.toSet())
            prefs[archiveKey] = set
        }
    }

    suspend fun addRecentSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        context.galleryDataStore.edit { prefs ->
            val list = try {
                val raw = prefs[searchesKey]
                if (raw != null) {
                    val arr = JSONArray(raw)
                    val result = mutableListOf<String>()
                    for (i in 0 until arr.length()) result.add(arr.getString(i))
                    result
                } else {
                    prefs[legacySearchesKey]?.toMutableList() ?: mutableListOf()
                }
            } catch (e: Exception) {
                mutableListOf()
            }.toMutableList()

            list.remove(normalized)
            list.add(0, normalized)
            val trimmed = list.take(20)
            prefs[searchesKey] = JSONArray(trimmed).toString()
        }
    }

    suspend fun clearRecentSearches() {
        context.galleryDataStore.edit { prefs ->
            prefs.remove(searchesKey)
            prefs.remove(legacySearchesKey)
        }
    }

    suspend fun addTagForMedia(uri: String, tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        context.galleryDataStore.edit { prefs ->
            val raw = prefs[mediaTagsKey] ?: "{}"
            val obj = try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
            val arr = obj.optJSONArray(uri) ?: JSONArray()
            val existing = mutableSetOf<String>()
            for (i in 0 until arr.length()) existing.add(arr.getString(i))
            if (existing.add(trimmed)) {
                val newArr = JSONArray(existing)
                obj.put(uri, newArr)
                prefs[mediaTagsKey] = obj.toString()
            }
        }
    }

    suspend fun addPersonForMedia(uri: String, person: String) {
        val trimmed = person.trim()
        if (trimmed.isEmpty()) return
        context.galleryDataStore.edit { prefs ->
            val raw = prefs[mediaPeopleKey] ?: "{}"
            val obj = try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
            val arr = obj.optJSONArray(uri) ?: JSONArray()
            val existing = mutableSetOf<String>()
            for (i in 0 until arr.length()) existing.add(arr.getString(i))
            if (existing.add(trimmed)) {
                val newArr = JSONArray(existing)
                obj.put(uri, newArr)
                prefs[mediaPeopleKey] = obj.toString()
            }
        }
    }
}
