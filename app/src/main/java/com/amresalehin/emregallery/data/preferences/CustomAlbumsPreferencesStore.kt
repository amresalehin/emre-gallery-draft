package com.amresalehin.emregallery.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * Persists custom album definitions and their media URI memberships in DataStore.
 */
internal class CustomAlbumsPreferencesStore(private val context: Context) {

    private val customAlbumsKey = stringPreferencesKey("custom_albums")

    val customAlbums: Flow<List<CustomAlbum>> = context.galleryDataStore.data.map { prefs ->
        decodeAlbums(prefs[customAlbumsKey] ?: "[]")
    }.distinctUntilChanged()

    suspend fun saveCustomAlbums(albums: List<CustomAlbum>) {
        val array = encodeAlbums(albums)
        context.galleryDataStore.edit { prefs ->
            prefs[customAlbumsKey] = array.toString()
        }
    }

    suspend fun createCustomAlbum(name: String, parentPath: String = ""): CustomAlbum {
        val current = customAlbums.first()
        val newAlbum = CustomAlbum(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            parentPath = parentPath.trim(),
            mediaUris = emptySet()
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

    suspend fun removeUrisFromAllAlbums(uriSet: Set<String>) {
        if (uriSet.isEmpty()) return
        val currentAlbums = customAlbums.first()
        val hasChanges = currentAlbums.any { it.mediaUris.any { uri -> uri in uriSet } }
        if (hasChanges) {
            saveCustomAlbums(currentAlbums.map { album ->
                album.copy(mediaUris = album.mediaUris - uriSet)
            })
        }
    }

    private fun decodeAlbums(raw: String): List<CustomAlbum> = try {
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
    } catch (_: Exception) {
        emptyList()
    }

    private fun encodeAlbums(albums: List<CustomAlbum>): JSONArray {
        val array = JSONArray()
        for (album in albums) {
            val obj = JSONObject()
            obj.put("id", album.id)
            obj.put("name", album.name)
            obj.put("parentPath", album.parentPath)
            val urisArr = JSONArray()
            for (uri in album.mediaUris) {
                urisArr.put(uri)
            }
            obj.put("mediaUris", urisArr)
            array.put(obj)
        }
        return array
    }
}
