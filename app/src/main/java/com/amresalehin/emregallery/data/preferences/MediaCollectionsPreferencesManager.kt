package com.amresalehin.emregallery.data.preferences

import android.content.Context
import com.amresalehin.emregallery.data.CustomAlbum
import kotlinx.coroutines.flow.Flow

/**
 * Facade managing media tags, people assignments, albums, favorites, bin, archive, and recent search history
 * by delegating to focused DataStore preference stores.
 */
class MediaCollectionsPreferencesManager(context: Context) {

    private val flagsStore = MediaFlagsPreferencesStore(context)
    private val albumsStore = CustomAlbumsPreferencesStore(context)
    private val annotationsStore = MediaAnnotationsPreferencesStore(context)
    private val searchesStore = RecentSearchesPreferencesStore(context)

    val favourites: Flow<Set<String>> get() = flagsStore.favourites
    val hidden: Flow<Set<String>> get() = flagsStore.hidden
    val bin: Flow<Set<String>> get() = flagsStore.bin
    val archive: Flow<Set<String>> get() = flagsStore.archive
    val recentSearches: Flow<List<String>> get() = searchesStore.recentSearches
    val customAlbums: Flow<List<CustomAlbum>> get() = albumsStore.customAlbums
    val customTagsMap: Flow<Map<String, Set<String>>> get() = annotationsStore.customTagsMap
    val customPeopleMap: Flow<Map<String, Set<String>>> get() = annotationsStore.customPeopleMap

    suspend fun saveCustomAlbums(albums: List<CustomAlbum>) = albumsStore.saveCustomAlbums(albums)

    suspend fun createCustomAlbum(name: String, parentPath: String = ""): CustomAlbum =
        albumsStore.createCustomAlbum(name, parentPath)

    suspend fun deleteCustomAlbum(albumId: String) = albumsStore.deleteCustomAlbum(albumId)

    suspend fun renameCustomAlbum(albumId: String, newName: String) =
        albumsStore.renameCustomAlbum(albumId, newName)

    suspend fun addMediaToCustomAlbum(albumId: String, uri: String) =
        albumsStore.addMediaToCustomAlbum(albumId, uri)

    suspend fun removeMediaFromCustomAlbum(albumId: String, uri: String) =
        albumsStore.removeMediaFromCustomAlbum(albumId, uri)

    suspend fun toggleFavourite(uri: String) = flagsStore.toggleFavourite(uri)

    suspend fun setHidden(uri: String, hiddenVal: Boolean) = flagsStore.setHidden(uri, hiddenVal)

    suspend fun setHiddenBatch(uris: Collection<String>, hiddenVal: Boolean) =
        flagsStore.setHiddenBatch(uris, hiddenVal)

    suspend fun setInBin(uri: String, inBin: Boolean) = flagsStore.setInBin(uri, inBin)

    suspend fun setInBinBatch(uris: Collection<String>, inBin: Boolean) =
        flagsStore.setInBinBatch(uris, inBin)

    suspend fun removePermanently(uris: Collection<String>) {
        if (uris.isEmpty()) return
        val uriSet = uris.toSet()
        flagsStore.removeFromAllFlags(uriSet)
        albumsStore.removeUrisFromAllAlbums(uriSet)
    }

    suspend fun setArchived(uri: String, archivedVal: Boolean) =
        flagsStore.setArchived(uri, archivedVal)

    suspend fun setArchivedBatch(uris: Collection<String>, archivedVal: Boolean) =
        flagsStore.setArchivedBatch(uris, archivedVal)

    suspend fun addRecentSearch(query: String) = searchesStore.addRecentSearch(query)

    suspend fun clearRecentSearches() = searchesStore.clearRecentSearches()

    suspend fun addTagForMedia(uri: String, tag: String) =
        annotationsStore.addTagForMedia(uri, tag)

    suspend fun addPersonForMedia(uri: String, person: String) =
        annotationsStore.addPersonForMedia(uri, person)
}
