package com.amresalehin.emregallery.data

import java.util.Locale

/**
 * Validates whether a URL represents a genuine standalone media asset rather than
 * an ad/tracker beacon, streaming slice fragment, or non-media HTML/API webpage.
 */
object MediaUrlValidator {

    private val TRACKING_PATTERNS = listOf(
        "google-analytics", "analytics", "doubleclick", "pixel", "1x1", "tracking",
        "beacon", "telemetry", "hotjar", "clarity.ms", "stats.wp.com", "facebook.com/tr"
    )

    private val PLACEHOLDER_ASSET_TOKENS = listOf(
        "favicon.ico", "/avatar_default", "/blank.gif",
        "transparent.gif", "spacer.gif", "spinner.gif"
    )

    private val NON_MEDIA_DOCUMENT_EXTENSIONS = listOf(
        ".html", ".htm", ".php", ".jsp", ".asp", ".aspx",
        ".js", ".css", ".json", ".xml", ".txt", ".do"
    )

    /**
     * Detects if a URL is an unmerged streaming chunk, segment, or initialization fragment.
     * Standalone downloading of individual video chunks causes broken, partial, and multiplied downloads.
     */
    fun isMediaFragment(url: String): Boolean = MediaFragmentDetector.isMediaFragment(url)

    /**
     * Checks whether the URL is valid media and not tracking/pixel/non-media page.
     */
    fun isValidMedia(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)

        if (AdBlockEngine.isAdOrTracker(url)) return false
        if (lower.startsWith("data:")) return DataUriParser.isSubstantialDataUri(url)

        if (TRACKING_PATTERNS.any { lower.contains(it) }) return false
        if (PLACEHOLDER_ASSET_TOKENS.any { lower.contains(it) }) return false

        val cleanPath = lower.substringBefore("?").substringBefore("#")
        if (NON_MEDIA_DOCUMENT_EXTENSIONS.any { cleanPath.endsWith(it) }) return false
        if (NonMediaHostClassifier.isNonMediaEndpoint(cleanPath)) return false
        if (NonMediaHostClassifier.isNonMediaHostPage(url, lower, cleanPath)) return false
        if (isMediaFragment(url)) return false

        return true
    }
}
