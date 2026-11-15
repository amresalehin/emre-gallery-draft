package com.amresalehin.emregallery.ui

import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.ScrapedMediaItem
import java.util.Locale

/**
 * Comprehensive parser for media dimensions from resolution hints, URL patterns, and titles.
 */
fun parseScrapedMediaDimensions(item: ScrapedMediaItem): Pair<Int?, Int?> {
    val hint = item.resolutionHint?.trim()
    if (!hint.isNullOrBlank()) {
        val match = Regex("""(\d{2,5})\s*[xX×]\s*(\d{2,5})""").find(hint)
        if (match != null) {
            val w = match.groupValues[1].toIntOrNull()
            val h = match.groupValues[2].toIntOrNull()
            if (w != null && h != null && w > 0 && h > 0) return Pair(w, h)
        }
        val pMatch = Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE).find(hint)
        if (pMatch != null) {
            val h = pMatch.groupValues[1].toIntOrNull()
            if (h != null && h > 0) {
                return Pair((h * 16) / 9, h)
            }
        }
        val wMatch = Regex("""(\d{2,5})w""", RegexOption.IGNORE_CASE).find(hint)
        if (wMatch != null) {
            val w = wMatch.groupValues[1].toIntOrNull()
            if (w != null && w > 0) return Pair(w, null)
        }
        val pxMatch = Regex("""(\d{2,5})px""", RegexOption.IGNORE_CASE).find(hint)
        if (pxMatch != null) {
            val w = pxMatch.groupValues[1].toIntOrNull()
            if (w != null && w > 0) return Pair(w, null)
        }
    }

    // 1. WxH in URL or Title (e.g., 800x600, 1920x1080)
    val urlMatch = Regex("""(?:^|[/_.-])(\d{2,5})\s*[xX×]\s*(\d{2,5})(?:[/_.-]|$)""").find(item.url)
        ?: Regex("""(?:^|[/_.-])(\d{2,5})\s*[xX×]\s*(\d{2,5})(?:[/_.-]|$)""").find(item.title)
    if (urlMatch != null) {
        val w = urlMatch.groupValues[1].toIntOrNull()
        val h = urlMatch.groupValues[2].toIntOrNull()
        if (w != null && h != null && w >= 10 && h >= 10) {
            return Pair(w, h)
        }
    }

    // 2. Wikimedia / standard thumbnail pattern: /(\d+)px- or title starting with (\d+)px
    val pxMatch = Regex("""(?:^|[/_-])(\d{2,5})px(?:[-_./]|$)""", RegexOption.IGNORE_CASE).find(item.url)
        ?: Regex("""(?:^|[/_-])(\d{2,5})px(?:[-_./]|$)""", RegexOption.IGNORE_CASE).find(item.title)
    if (pxMatch != null) {
        val w = pxMatch.groupValues[1].toIntOrNull()
        if (w != null && w >= 10) {
            return Pair(w, null)
        }
    }

    // 3. Query params: ?width=800&height=600 or ?w=800&h=600
    val wQuery = Regex("""[?&](?:width|w)=(\d{2,5})""", RegexOption.IGNORE_CASE).find(item.url)?.groupValues?.get(1)?.toIntOrNull()
    val hQuery = Regex("""[?&](?:height|h)=(\d{2,5})""", RegexOption.IGNORE_CASE).find(item.url)?.groupValues?.get(1)?.toIntOrNull()
    if (wQuery != null || hQuery != null) {
        return Pair(wQuery, hQuery)
    }

    // 4. Folder dimension descriptor e.g. /736x/, /474x/, /236x/
    val folderDim = Regex("""/(\d{2,5})x/""").find(item.url)?.groupValues?.get(1)?.toIntOrNull()
    if (folderDim != null && folderDim >= 10) {
        return Pair(folderDim, null)
    }

    return Pair(null, null)
}

/**
 * Evaluates whether a media item satisfies the active dimension filter.
 */
fun passesDimensionFilter(
    item: ScrapedMediaItem,
    targetMinWidth: Int,
    targetMinHeight: Int,
    dynamicDimensions: Map<String, Pair<Int, Int>>
): Boolean {
    // 1. Any Dimensions setting (default 0, 0) matches everything
    if (targetMinWidth <= 0 && targetMinHeight <= 0) return true

    // 2. Audio, Video, and Stream files have no image-based spatial filtering requirements
    if (item.category == MediaCategory.AUDIO || item.category == MediaCategory.VIDEOS ||
        item.category == MediaCategory.STREAMS || item.isVideo || item.isAudio || item.isStream
    ) {
        return true
    }

    // 3. Reject decorative small UI elements, buttons, icons, or donate badges
    val lowerUrl = item.url.lowercase(Locale.ROOT)
    val lowerTitle = item.title.lowercase(Locale.ROOT)
    val isTinyUiMedia = lowerUrl.contains("donate") || lowerUrl.contains("/icon") ||
        lowerUrl.contains("-icon.") || lowerUrl.contains("_icon.") ||
        lowerUrl.contains("favicon") || lowerUrl.contains("badge") ||
        lowerTitle.contains("donate") || lowerTitle.contains("favicon") ||
        lowerTitle.contains("icon")
    if (isTinyUiMedia) {
        return false
    }

    val (w, h) = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(item)

    // Treat unknown dimensions as pending or excluded in strict mode
    if (targetMinWidth > 0 && w == null) return false
    if (targetMinHeight > 0 && h == null) return false

    // When strict filtering is active, require every enabled dimension constraint to be satisfied
    if (targetMinWidth > 0 && (w == null || w < targetMinWidth)) return false
    if (targetMinHeight > 0 && (h == null || h < targetMinHeight)) return false

    return true
}
