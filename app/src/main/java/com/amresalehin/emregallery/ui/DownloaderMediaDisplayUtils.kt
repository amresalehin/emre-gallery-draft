package com.amresalehin.emregallery.ui

import com.amresalehin.emregallery.data.CmafTrackAssociation
import com.amresalehin.emregallery.data.ScrapedMediaItem

/** Displays source dimensions or a truthful unavailable label; never fabricates a size. */
internal fun resolveScrapedMediaDimensionLabel(
    item: ScrapedMediaItem,
    dynamicDimensions: Map<String, Pair<Int, Int>>
): String {
    val hint = item.resolutionHint?.trim()?.takeIf {
        it.isNotBlank() && !it.equals("Network", true) && !it.equals("Unknown", true) &&
            !it.equals("40 × 40", true) && !it.equals("40x40", true) &&
            !it.equals("40 x 40", true)
    }

    // Audio bitrates and codecs are not spatial dimensions. Older captures may
    // still carry the former generic "40 × 40" placeholder in resolutionHint.
    if (item.isAudio) return hint ?: "Audio only"

    // Ignore the legacy placeholder while still letting the parser inspect a real
    // size embedded in the URL or title.
    val isLegacyPlaceholder = item.resolutionHint?.filterNot { it.isWhitespace() }
        ?.replace('×', 'x')
        ?.equals("40x40", ignoreCase = true) == true
    val parseItem = if (isLegacyPlaceholder) item.copy(resolutionHint = null) else item
    val dimensions = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(parseItem).let { (w, h) ->
        if (w != null && h != null) w to h else null
    }

    if (dimensions != null && dimensions.first > 0 && dimensions.second > 0) {
        return "${dimensions.first} × ${dimensions.second}"
    }
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
