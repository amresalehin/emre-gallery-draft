package com.amresalehin.emregallery.ui

import com.amresalehin.emregallery.data.CmafTrackAssociation
import com.amresalehin.emregallery.data.ScrapedMediaItem

/** Displays actual/source-supplied dimensions only; never fabricates a spatial measurement. */
internal fun resolveScrapedMediaDimensionLabel(
    item: ScrapedMediaItem,
    dynamicDimensions: Map<String, Pair<Int, Int>>
): String {
    val dimensions = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(item).let { (w, h) ->
        if (w != null && h != null) w to h else null
    }
    if (dimensions != null && dimensions.first > 0 && dimensions.second > 0) {
        return "${dimensions.first} × ${dimensions.second}"
    }
    val hint = item.resolutionHint?.trim()?.takeIf {
        it.isNotBlank() && !it.equals("Network", true) && !it.equals("Unknown", true) &&
            !it.equals("40 × 40", true) && !it.equals("40x40", true)
    }
    if (item.isAudio) return hint ?: "Audio only"
    if (item.isVideo || item.isStream) return hint ?: "Resolution unavailable"
    return hint ?: "Dimensions unavailable"
}

internal fun resolveScrapedMediaCategoryLabel(item: ScrapedMediaItem): String = when {
    CmafTrackAssociation.requiresAudio(item) -> "Video + Audio"
    item.isVideo -> "Videos"
    item.isAudio -> "Audio"
    item.isStream -> "Streams"
    else -> "Images"
}
