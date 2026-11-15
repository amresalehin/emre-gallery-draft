package com.amresalehin.emregallery.data

import java.util.Locale

/**
 * Estimates media resolution and compares duplicate scraped media candidates
 * to choose the highest-quality variant.
 */
object MediaQualityEvaluator {

    private val HINT_RES_REGEX = Regex("""(\d{2,5})\s*[xX*×]\s*(\d{2,5})""")
    private val HINT_P_REGEX = Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE)
    private val HINT_W_REGEX = Regex("""(\d{2,5})[wW]""")
    private val HINT_PX_REGEX = Regex("""(\d{2,5})px""", RegexOption.IGNORE_CASE)

    private val URL_RES_REGEX = Regex("""[/_-](\d{2,5})x(\d{2,5})[._/-]""")
    private val URL_FOLDER_DIM_REGEX = Regex("""/(\d{2,5})x/""")
    private val URL_PX_REGEX = Regex("""[/_-](\d{2,5})px[._/-]""")
    private val URL_QUERY_WIDTH_REGEX = Regex("""[?&](?:w|width)=(\d{2,5})""")

    private val FORMAT_PRIORITY = mapOf(
        "PNG" to 4,
        "JPEG" to 3,
        "JPG" to 3,
        "WEBP" to 2,
        "AVIF" to 1
    )

    /**
     * Parses an approximate pixel area (width * height) from a media item's resolution hint or URL,
     * used for deterministic quality comparisons when deduplicating variants.
     */
    fun parsePixelArea(item: ScrapedMediaItem): Long? {
        return parsePixelArea(item.resolutionHint, item.url)
    }

    /**
     * Parses pixel area from resolution hint string or URL path.
     */
    fun parsePixelArea(resolutionHint: String?, url: String?): Long? {
        if (!resolutionHint.isNullOrBlank()) {
            val resMatch = HINT_RES_REGEX.find(resolutionHint)
            if (resMatch != null) {
                val w = resMatch.groupValues[1].toLongOrNull()
                val h = resMatch.groupValues[2].toLongOrNull()
                if (w != null && h != null && w > 0 && h > 0) return w * h
            }
            val pMatch = HINT_P_REGEX.find(resolutionHint)
            if (pMatch != null) {
                val h = pMatch.groupValues[1].toLongOrNull()
                if (h != null && h > 0) return ((h * 16L) / 9L) * h
            }
            val wMatch = HINT_W_REGEX.find(resolutionHint)
            if (wMatch != null) {
                val w = wMatch.groupValues[1].toLongOrNull()
                if (w != null && w > 0) return w * w
            }
            val pxMatch = HINT_PX_REGEX.find(resolutionHint)
            if (pxMatch != null) {
                val px = pxMatch.groupValues[1].toLongOrNull()
                if (px != null && px > 0) return px * px
            }
        }

        if (!url.isNullOrBlank()) {
            if (url.contains("/originals/", ignoreCase = true)) {
                return 25_000_000L // Original master file ranks highest
            }
            val urlResMatch = URL_RES_REGEX.find(url)
            if (urlResMatch != null) {
                val w = urlResMatch.groupValues[1].toLongOrNull()
                val h = urlResMatch.groupValues[2].toLongOrNull()
                if (w != null && h != null && w > 0 && h > 0) return w * h
            }
            val folderDimMatch = URL_FOLDER_DIM_REGEX.find(url)
            if (folderDimMatch != null) {
                val w = folderDimMatch.groupValues[1].toLongOrNull()
                if (w != null && w > 0) return w * w
            }
            val pxUrlMatch = URL_PX_REGEX.find(url)
            if (pxUrlMatch != null) {
                val px = pxUrlMatch.groupValues[1].toLongOrNull()
                if (px != null && px > 0) return px * px
            }
            val queryWidthMatch = URL_QUERY_WIDTH_REGEX.find(url)
            if (queryWidthMatch != null) {
                val w = queryWidthMatch.groupValues[1].toLongOrNull()
                if (w != null && w > 0) return w * w
            }
        }
        return null
    }

    /**
     * Compares two duplicate scraped media items to determine which offers superior quality
     * (e.g. master original over thumbnail, higher pixel resolution, larger file size, preferred format).
     * Returns > 0 if [a] is higher quality, < 0 if [b] is higher quality, 0 if equivalent.
     */
    fun compareQuality(a: ScrapedMediaItem, b: ScrapedMediaItem): Int {
        // 1. Originals master URL preference
        val aIsOriginals = a.url.contains("/originals/", ignoreCase = true)
        val bIsOriginals = b.url.contains("/originals/", ignoreCase = true)
        if (aIsOriginals && !bIsOriginals) return 1
        if (!aIsOriginals && bIsOriginals) return -1

        // 2. Direct upload vs thumbnail/transcoded path
        val aIsThumb = a.url.contains("/thumb/") || a.url.contains("/transcoded/")
        val bIsThumb = b.url.contains("/thumb/") || b.url.contains("/transcoded/")
        if (!aIsThumb && bIsThumb) return 1
        if (aIsThumb && !bIsThumb) return -1

        // 3. Pixel area from resolutionHint or URL
        val aArea = parsePixelArea(a)
        val bArea = parsePixelArea(b)
        if (aArea != null && bArea != null && aArea != bArea) {
            return aArea.compareTo(bArea)
        }
        if (aArea != null && bArea == null) return 1
        if (aArea == null && bArea != null) return -1

        // 4. File size in bytes
        val aSize = a.sizeBytes ?: 0L
        val bSize = b.sizeBytes ?: 0L
        if (aSize != bSize) {
            return aSize.compareTo(bSize)
        }

        // 5. Format preference for master downloads (JPEG / JPG / PNG over lossy WEBP / AVIF)
        val aFmt = FORMAT_PRIORITY[a.format.uppercase(Locale.ROOT)] ?: 0
        val bFmt = FORMAT_PRIORITY[b.format.uppercase(Locale.ROOT)] ?: 0
        if (aFmt != bFmt) {
            return aFmt.compareTo(bFmt)
        }

        // 6. Prefer candidate with existing thumbnail preview
        if (a.thumbnailUrl != null && b.thumbnailUrl == null) return 1
        if (a.thumbnailUrl == null && b.thumbnailUrl != null) return -1

        return 0
    }
}
