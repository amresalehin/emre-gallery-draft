package com.amresalehin.emregallery.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.amresalehin.emregallery.data.ImageDimensionProber
import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaClassifier
import com.amresalehin.emregallery.data.ScrapedMediaItem

internal data class DownloaderResultsSummary(
    val filteredItems: List<ScrapedMediaItem>,
    val categoryTabs: List<Triple<String, ImageVector, Int>>
)

internal fun filterAndSortScrapedResults(
    allItems: List<ScrapedMediaItem>,
    selectedFilter: String?,
    sortOption: String,
    minWidthFilter: Float,
    minHeightFilter: Float,
    showSvg: Boolean,
    minKbInput: String,
    dynamicDimensions: Map<String, Pair<Int, Int>>
): DownloaderResultsSummary {
    val minKbVal = minKbInput.toFloatOrNull() ?: 0f
    val minBytesThreshold = (minKbVal * 1024L).toLong()
    val targetMinWidth = minWidthFilter.toInt()
    val targetMinHeight = minHeightFilter.toInt()

    val dimensionFiltered = allItems.filter { item ->
        if (!showSvg && MediaClassifier.isSvg(item.url, item.mimeType, item.format)) {
            return@filter false
        }
        if (minBytesThreshold > 0L) {
            val knownSize = item.sizeBytes ?: ImageDimensionProber.getCachedSize(item.url)
            if (knownSize != null && knownSize < minBytesThreshold) {
                return@filter false
            }
        }
        if (targetMinWidth > 0 || targetMinHeight > 0) {
            if (!passesDimensionFilter(item, targetMinWidth, targetMinHeight, dynamicDimensions)) {
                return@filter false
            }
        }
        true
    }

    val imageCount = dimensionFiltered.count { it.category == MediaCategory.IMAGES }
    val videoCount = dimensionFiltered.count { it.category == MediaCategory.VIDEOS }
    val audioCount = dimensionFiltered.count { it.category == MediaCategory.AUDIO }
    val streamCount = dimensionFiltered.count { it.category == MediaCategory.STREAMS }

    val categoryFiltered = when (selectedFilter) {
        "Images" -> dimensionFiltered.filter { it.category == MediaCategory.IMAGES }
        "Videos" -> dimensionFiltered.filter { it.category == MediaCategory.VIDEOS }
        "Audio" -> dimensionFiltered.filter { it.category == MediaCategory.AUDIO }
        "Streams" -> dimensionFiltered.filter { it.category == MediaCategory.STREAMS }
        else -> dimensionFiltered
    }

    val sortedItems = when (sortOption) {
        "Title (A-Z)" -> categoryFiltered.sortedBy { it.title.lowercase() }
        "Videos First" -> categoryFiltered.sortedByDescending { it.isVideo }
        "Images First" -> categoryFiltered.sortedBy { it.isVideo || it.isAudio || it.isStream }
        "Streams First" -> categoryFiltered.sortedByDescending { it.isStream }
        else -> categoryFiltered
    }

    val categories = listOf(
        Triple("Images", Icons.Default.Image, imageCount),
        Triple("Videos", Icons.Default.Videocam, videoCount),
        Triple("Audio", Icons.Default.MusicNote, audioCount),
        Triple("Streams", Icons.Default.SmartDisplay, streamCount)
    )

    return DownloaderResultsSummary(
        filteredItems = sortedItems,
        categoryTabs = categories
    )
}
