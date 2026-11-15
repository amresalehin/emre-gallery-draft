package com.amresalehin.emregallery.data

import android.content.Context
import android.net.Uri
import com.amresalehin.emregallery.ocr.TesseractOcr

/**
 * Builds State A category shortcuts (Photos, Videos, OCR Documents, Favorites),
 * omitting shortcuts that have no backing media.
 */
internal class DiscoveryCategoryShortcutsBuilder(private val context: Context) {

    fun buildCategories(
        nonTrashMedia: List<MediaItem>,
        favourites: Set<String>
    ): List<DiscoveryCategory> {
        val categories = mutableListOf<DiscoveryCategory>()

        val photos = nonTrashMedia.filterNot { it.isVideo }
        if (photos.isNotEmpty()) {
            categories.add(
                DiscoveryCategory(
                    id = "cat_photos",
                    title = "Photos",
                    itemCount = photos.size,
                    sampleUris = photos.take(3).map { it.uri },
                    filterAction = SearchFilterState(mediaType = MediaTypeFilter.PHOTOS)
                )
            )
        }

        val videos = nonTrashMedia.filter { it.isVideo }
        if (videos.isNotEmpty()) {
            categories.add(
                DiscoveryCategory(
                    id = "cat_videos",
                    title = "Videos",
                    itemCount = videos.size,
                    sampleUris = videos.take(3).map { it.uri },
                    filterAction = SearchFilterState(mediaType = MediaTypeFilter.VIDEOS)
                )
            )
        }

        val documentItems = nonTrashMedia.filter {
            TesseractOcr.hasCachedResult(context, Uri.parse(it.uri))
        }
        if (documentItems.isNotEmpty()) {
            categories.add(
                DiscoveryCategory(
                    id = "cat_docs",
                    title = "Documents",
                    itemCount = documentItems.size,
                    sampleUris = documentItems.take(3).map { it.uri },
                    queryAction = "document"
                )
            )
        }

        if (favourites.isNotEmpty()) {
            val favItems = nonTrashMedia.filter { it.uri in favourites }
            if (favItems.isNotEmpty()) {
                categories.add(
                    DiscoveryCategory(
                        id = "cat_favs",
                        title = "Favorites",
                        itemCount = favItems.size,
                        sampleUris = favItems.take(3).map { it.uri },
                        filterAction = SearchFilterState(favoritesOnly = true)
                    )
                )
            }
        }

        return categories
    }
}
