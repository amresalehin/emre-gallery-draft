package com.amresalehin.emregallery.data

import android.content.Context
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.FaceClusterStore

/**
 * Builds State A discovery items for recognized people clusters, custom named people,
 * and reverse-geocoded photo locations.
 */
internal class DiscoveryPeopleAndPlacesBuilder(
    private val context: Context,
    private val faceStore: FaceClusterStore,
    private val locationCache: PhotoMapLocationCache
) {

    fun buildPeople(
        nonTrashMedia: List<MediaItem>,
        customPeopleMap: Map<String, Set<String>>
    ): List<DiscoveryEntityItem> {
        val personSummaries = runCatching {
            faceStore.personSummaries(includeHidden = false, engineId = BuffaloLFaceEngine.ENGINE_ID)
        }.getOrDefault(emptyList()).filter { it.faceCount > 0 && it.sampleUri != null }

        val peopleItems = mutableListOf<DiscoveryEntityItem>()
        for (summary in personSummaries.take(8)) {
            val name = summary.displayName.ifBlank { "Person ${summary.id.take(4)}" }
            peopleItems.add(
                DiscoveryEntityItem(
                    id = summary.id,
                    title = name,
                    subtitle = "${summary.faceCount} photos",
                    thumbnailUri = summary.sampleUri,
                    isPerson = true,
                    personFaceIndex = summary.portraitFaceIndex,
                    queryAction = name,
                    filterAction = SearchFilterState(selectedPersonId = summary.id)
                )
            )
        }

        for ((_, names) in customPeopleMap) {
            for (name in names) {
                if (name.isNotBlank() && peopleItems.none { it.title.equals(name, ignoreCase = true) }) {
                    val matchingPhotos = nonTrashMedia.filter { customPeopleMap[it.uri]?.contains(name) == true }
                    if (matchingPhotos.isNotEmpty()) {
                        peopleItems.add(
                            DiscoveryEntityItem(
                                id = "custom_person_$name",
                                title = name,
                                subtitle = "${matchingPhotos.size} photos",
                                thumbnailUri = matchingPhotos.firstOrNull()?.uri,
                                isPerson = true,
                                queryAction = name,
                                filterAction = SearchFilterState(selectedPersonId = "name:$name")
                            )
                        )
                    }
                }
            }
        }
        return peopleItems
    }

    suspend fun buildPlaces(nonTrashMedia: List<MediaItem>): List<DiscoveryEntityItem> {
        val cachedLocations = runCatching { locationCache.loadAll() }.getOrDefault(emptyMap())
        val locatedMedia = nonTrashMedia.filter { item -> cachedLocations[item.uri]?.point != null }

        val placeGroups = mutableMapOf<String, MutableList<MediaItem>>()
        for (item in locatedMedia) {
            val point = cachedLocations[item.uri]?.point ?: continue
            val placeName = PlaceLocationResolver.resolvePlace(context, item.uri, point.latitude, point.longitude)
            if (!placeName.isNullOrBlank()) {
                placeGroups.getOrPut(placeName) { mutableListOf() }.add(item)
            }
        }

        return placeGroups.entries.sortedByDescending { it.value.size }.take(6).map { (placeName, items) ->
            DiscoveryEntityItem(
                id = "place_$placeName",
                title = placeName,
                subtitle = "${items.size} photos",
                thumbnailUri = items.firstOrNull()?.uri,
                queryAction = placeName,
                filterAction = SearchFilterState(selectedPlace = placeName)
            )
        }
    }
}
