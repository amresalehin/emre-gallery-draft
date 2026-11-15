package com.amresalehin.emregallery.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.amresalehin.emregallery.data.galleryDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Persists URI-indexed boolean lifecycle flags (favourites, hidden, bin, archive) in DataStore.
 */
internal class MediaFlagsPreferencesStore(private val context: Context) {

    private val favouritesKey = stringSetPreferencesKey("favourites")
    private val hiddenKey = stringSetPreferencesKey("hidden")
    private val binKey = stringSetPreferencesKey("bin")
    private val archiveKey = stringSetPreferencesKey("archive")

    val favourites: Flow<Set<String>> = observeSet(favouritesKey)
    val hidden: Flow<Set<String>> = observeSet(hiddenKey)
    val bin: Flow<Set<String>> = observeSet(binKey)
    val archive: Flow<Set<String>> = observeSet(archiveKey)

    private fun observeSet(key: Preferences.Key<Set<String>>): Flow<Set<String>> =
        context.galleryDataStore.data.map { it[key] ?: emptySet() }.distinctUntilChanged()

    suspend fun toggleFavourite(uri: String) {
        context.galleryDataStore.edit { prefs ->
            val set = (prefs[favouritesKey] ?: emptySet()).toMutableSet()
            if (!set.add(uri)) {
                set.remove(uri)
            }
            prefs[favouritesKey] = set
        }
    }

    suspend fun setHidden(uri: String, hiddenVal: Boolean) = updateFlag(hiddenKey, uri, hiddenVal)

    suspend fun setHiddenBatch(uris: Collection<String>, hiddenVal: Boolean) =
        updateFlagBatch(hiddenKey, uris, hiddenVal)

    suspend fun setInBin(uri: String, inBin: Boolean) = updateFlag(binKey, uri, inBin)

    suspend fun setInBinBatch(uris: Collection<String>, inBin: Boolean) =
        updateFlagBatch(binKey, uris, inBin)

    suspend fun setArchived(uri: String, archivedVal: Boolean) = updateFlag(archiveKey, uri, archivedVal)

    suspend fun setArchivedBatch(uris: Collection<String>, archivedVal: Boolean) =
        updateFlagBatch(archiveKey, uris, archivedVal)

    suspend fun removeFromAllFlags(uriSet: Set<String>) {
        if (uriSet.isEmpty()) return
        context.galleryDataStore.edit { prefs ->
            listOf(binKey, hiddenKey, favouritesKey, archiveKey).forEach { key ->
                val updated = (prefs[key] ?: emptySet()).toMutableSet()
                updated.removeAll(uriSet)
                prefs[key] = updated
            }
        }
    }

    private suspend fun updateFlag(key: Preferences.Key<Set<String>>, uri: String, enabled: Boolean) {
        context.galleryDataStore.edit { prefs ->
            val set = (prefs[key] ?: emptySet()).toMutableSet()
            if (enabled) set.add(uri) else set.remove(uri)
            prefs[key] = set
        }
    }

    private suspend fun updateFlagBatch(
        key: Preferences.Key<Set<String>>,
        uris: Collection<String>,
        enabled: Boolean
    ) {
        if (uris.isEmpty()) return
        context.galleryDataStore.edit { prefs ->
            val set = (prefs[key] ?: emptySet()).toMutableSet()
            if (enabled) set.addAll(uris) else set.removeAll(uris.toSet())
            prefs[key] = set
        }
    }
}
