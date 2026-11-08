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


internal suspend fun MediaCollectionsPreferencesManager.saveCustomAlbumsImpl(albums: List<CustomAlbum>) {
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
    context.galleryDataStore.edit { prefs ->
        prefs[customAlbumsKey] = array.toString()
    }
}

internal suspend fun MediaCollectionsPreferencesManager.createCustomAlbumImpl(name: String, parentPath: String = ""): CustomAlbum {
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

internal suspend fun MediaCollectionsPreferencesManager.deleteCustomAlbumImpl(albumId: String) {
    val current = customAlbums.first()
    saveCustomAlbums(current.filterNot { it.id == albumId })
}

internal suspend fun MediaCollectionsPreferencesManager.renameCustomAlbumImpl(albumId: String, newName: String) {
    val current = customAlbums.first()
    saveCustomAlbums(current.map {
        if (it.id == albumId) it.copy(name = newName.trim()) else it
    })
}

internal suspend fun MediaCollectionsPreferencesManager.addMediaToCustomAlbumImpl(albumId: String, uri: String) {
    val current = customAlbums.first()
    saveCustomAlbums(current.map {
        if (it.id == albumId) it.copy(mediaUris = it.mediaUris + uri) else it
    })
}

internal suspend fun MediaCollectionsPreferencesManager.removeMediaFromCustomAlbumImpl(albumId: String, uri: String) {
    val current = customAlbums.first()
    saveCustomAlbums(current.map {
        if (it.id == albumId) it.copy(mediaUris = it.mediaUris - uri) else it
    })
}

internal suspend fun MediaCollectionsPreferencesManager.toggleFavouriteImpl(uri: String) {
    context.galleryDataStore.edit { prefs ->
        val set = (prefs[favouritesKey] ?: emptySet()).toMutableSet()
        if (!set.add(uri)) {
            set.remove(uri)
        }
        prefs[favouritesKey] = set
    }
}

internal suspend fun MediaCollectionsPreferencesManager.setHiddenImpl(uri: String, hiddenVal: Boolean) {
    context.galleryDataStore.edit { prefs ->
        val set = (prefs[hiddenKey] ?: emptySet()).toMutableSet()
        if (hiddenVal) set.add(uri) else set.remove(uri)
        prefs[hiddenKey] = set
    }
}

internal suspend fun MediaCollectionsPreferencesManager.setHiddenBatchImpl(uris: Collection<String>, hiddenVal: Boolean) {
    if (uris.isEmpty()) return
    context.galleryDataStore.edit { prefs ->
        val set = (prefs[hiddenKey] ?: emptySet()).toMutableSet()
        if (hiddenVal) set.addAll(uris) else set.removeAll(uris.toSet())
        prefs[hiddenKey] = set
    }
}

internal suspend fun MediaCollectionsPreferencesManager.setInBinImpl(uri: String, inBin: Boolean) {
    context.galleryDataStore.edit { prefs ->
        val set = (prefs[binKey] ?: emptySet()).toMutableSet()
        if (inBin) set.add(uri) else set.remove(uri)
        prefs[binKey] = set
    }
}

internal suspend fun MediaCollectionsPreferencesManager.setInBinBatchImpl(uris: Collection<String>, inBin: Boolean) {
    if (uris.isEmpty()) return
    context.galleryDataStore.edit { prefs ->
        val set = (prefs[binKey] ?: emptySet()).toMutableSet()
        if (inBin) set.addAll(uris) else set.removeAll(uris.toSet())
        prefs[binKey] = set
    }
}

internal suspend fun MediaCollectionsPreferencesManager.removePermanentlyImpl(uris: Collection<String>) {
    if (uris.isEmpty()) return
    val uriSet = uris.toSet()
    context.galleryDataStore.edit { prefs ->
        val binSet = (prefs[binKey] ?: emptySet()).toMutableSet()
        binSet.removeAll(uriSet)
        prefs[binKey] = binSet

        val hiddenSet = (prefs[hiddenKey] ?: emptySet()).toMutableSet()
        hiddenSet.removeAll(uriSet)
        prefs[hiddenKey] = hiddenSet

        val favSet = (prefs[favouritesKey] ?: emptySet()).toMutableSet()
        favSet.removeAll(uriSet)
        prefs[favouritesKey] = favSet

        val archSet = (prefs[archiveKey] ?: emptySet()).toMutableSet()
        archSet.removeAll(uriSet)
        prefs[archiveKey] = archSet
    }
    val currentAlbums = customAlbums.first()
    val hasChanges = currentAlbums.any { it.mediaUris.any { uri -> uri in uriSet } }
    if (hasChanges) {
        saveCustomAlbums(currentAlbums.map { album ->
            album.copy(mediaUris = album.mediaUris - uriSet)
        })
    }
}

