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
import org.json.JSONObject

/**
 * Persists custom per-media tag and person annotations in DataStore.
 */
internal class MediaAnnotationsPreferencesStore(private val context: Context) {

    private val mediaTagsKey = stringPreferencesKey("media_custom_tags")
    private val mediaPeopleKey = stringPreferencesKey("media_custom_people")

    val customTagsMap: Flow<Map<String, Set<String>>> = observeStringSetMap(mediaTagsKey)
    val customPeopleMap: Flow<Map<String, Set<String>>> = observeStringSetMap(mediaPeopleKey)

    suspend fun addTagForMedia(uri: String, tag: String) =
        addEntryToMediaMap(mediaTagsKey, uri, tag)

    suspend fun addPersonForMedia(uri: String, person: String) =
        addEntryToMediaMap(mediaPeopleKey, uri, person)

    private fun observeStringSetMap(key: Preferences.Key<String>): Flow<Map<String, Set<String>>> =
        context.galleryDataStore.data.map { prefs ->
            decodeStringSetMap(prefs[key] ?: "{}")
        }.distinctUntilChanged()

    private suspend fun addEntryToMediaMap(
        key: Preferences.Key<String>,
        uri: String,
        value: String
    ) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        context.galleryDataStore.edit { prefs ->
            val raw = prefs[key] ?: "{}"
            val obj = try {
                JSONObject(raw)
            } catch (_: Exception) {
                JSONObject()
            }
            val arr = obj.optJSONArray(uri) ?: JSONArray()
            val existing = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                existing.add(arr.getString(i))
            }
            if (existing.add(trimmed)) {
                obj.put(uri, JSONArray(existing))
                prefs[key] = obj.toString()
            }
        }
    }

    private fun decodeStringSetMap(raw: String): Map<String, Set<String>> = try {
        val obj = JSONObject(raw)
        val map = mutableMapOf<String, Set<String>>()
        for (entryKey in obj.keys()) {
            val arr = obj.getJSONArray(entryKey)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            map[entryKey] = set
        }
        map
    } catch (_: Exception) {
        emptyMap()
    }
}
