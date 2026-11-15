package com.amresalehin.emregallery.ui

import com.amresalehin.emregallery.data.ScrapedMediaItem

internal data class ScrapedCardDisplayMeta(
    val titleText: String,
    val dimText: String,
    val categoryText: String,
    val formatText: String
)

internal fun resolveScrapedCardMeta(
    item: ScrapedMediaItem,
    dynamicDimensions: Map<String, Pair<Int, Int>>
): ScrapedCardDisplayMeta {
    val parsedDim = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(item).let { (w, h) ->
        if (w != null && h != null) (w to h) else null
    }
    val dimText = if (parsedDim != null && parsedDim.first > 0 && parsedDim.second > 0) {
        "${parsedDim.first} × ${parsedDim.second}"
    } else {
        item.resolutionHint ?: "40 × 40"
    }
    val categoryText = when {
        item.isVideo -> "Videos"
        item.isAudio -> "Audio"
        item.isStream -> "Streams"
        else -> "Images"
    }
    val formatText = if (!item.isVideo && item.format.contains("4K", ignoreCase = true)) {
        "JPG"
    } else {
        val raw = item.format.uppercase().trim()
        when {
            raw == "JPEG" -> "JPG"
            raw.isNotBlank() -> raw
            item.isVideo -> "MP4"
            item.isAudio -> "MP3"
            item.isStream -> "M3U8"
            else -> "JPG"
        }
    }
    return ScrapedCardDisplayMeta(
        titleText = item.title.ifBlank { "media_${item.id.takeLast(6)}" },
        dimText = dimText,
        categoryText = categoryText,
        formatText = formatText
    )
}
