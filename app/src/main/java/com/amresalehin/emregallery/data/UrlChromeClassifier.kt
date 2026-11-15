package com.amresalehin.emregallery.data

import java.util.Locale

/**
 * Evaluates URL patterns, titles, and spatial hints to filter out website chrome graphics.
 */
internal object UrlChromeClassifier {

    fun isUiChromeUrl(
        url: String,
        title: String? = null,
        width: Int? = null,
        height: Int? = null,
        isVideoOrAudioOrStream: Boolean = false
    ): Boolean {
        // Videos, audio tracks, and streams are almost never UI chrome
        if (isVideoOrAudioOrStream) return false

        val lowerUrl = url.lowercase(Locale.ROOT)
        val lowerTitle = title?.lowercase(Locale.ROOT) ?: ""
        val cleanPath = lowerUrl.substringBefore("?").substringBefore("#")
        val fileName = cleanPath.substringAfterLast('/')

        // 1. Direct keywords in filename, URL, or title
        for (kw in ChromeMediaRules.CHROME_KEYWORDS) {
            if (fileName.contains(kw) ||
                lowerUrl.contains("/$kw") ||
                lowerUrl.contains("$kw.") ||
                lowerUrl.contains("-$kw-") ||
                lowerUrl.contains("_${kw}_") ||
                lowerTitle.contains(kw)
            ) {
                return true
            }
        }

        // 2. Wikipedia / Wikimedia specific chrome assets
        if (isWikimediaChromeAsset(lowerUrl, fileName)) {
            return true
        }

        // 3. Tiny icon or tracking pixel spatial dimensions
        return ChromeMediaRules.isTinyOrExtremeAspectRatio(width, height)
    }

    private fun isWikimediaChromeAsset(lowerUrl: String, fileName: String): Boolean {
        if (!lowerUrl.contains("wikimedia.org") && !lowerUrl.contains("wikipedia.org")) {
            return false
        }
        return lowerUrl.contains("/static/images/") ||
            fileName.contains("wikipedia-logo") ||
            fileName.contains("wikimedia-button") ||
            fileName.contains("wordmark") ||
            fileName.contains("poweredby_mediawiki") ||
            fileName.contains("magnifying_glass") ||
            fileName.contains("wikisource") ||
            fileName.contains("commons-logo") ||
            (fileName.contains("globe") && fileName.contains("logo"))
    }
}
