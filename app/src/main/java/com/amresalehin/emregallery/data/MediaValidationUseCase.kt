package com.amresalehin.emregallery.data

import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

internal fun MediaClassifier.isValidMediaImpl(url: String): Boolean {
    val lower = url.lowercase(Locale.ROOT)

    // 1. Check AdBlockEngine for ad networks and tracking scripts
    if (AdBlockEngine.isAdOrTracker(url)) {
        return false
    }

    // 2. Data URI validation: allow substantial artwork, reject tiny tracking pixels
    if (lower.startsWith("data:")) {
        return isSubstantialDataUri(url)
    }

    for (pattern in TRACKING_PATTERNS) {
        if (lower.contains(pattern)) return false
    }
    if (lower.contains("favicon.ico") || lower.contains("/avatar_default") ||
        lower.contains("/blank.gif") || lower.contains("transparent.gif") ||
        lower.contains("spacer.gif") || lower.contains("spinner.gif")
    ) {
        return false
    }
    val cleanPath = lower.substringBefore("?").substringBefore("#")
    // Reject web document, script, and non-media endpoint paths
    if (cleanPath.endsWith(".html") || cleanPath.endsWith(".htm") || cleanPath.endsWith(".php") ||
        cleanPath.endsWith(".jsp") || cleanPath.endsWith(".asp") || cleanPath.endsWith(".aspx") ||
        cleanPath.endsWith(".js") || cleanPath.endsWith(".css") || cleanPath.endsWith(".json") ||
        cleanPath.endsWith(".xml") || cleanPath.endsWith(".txt") || cleanPath.endsWith(".do")
    ) {
        return false
    }

    // Reject non-media API, stats, logging, telemetry, and embed document endpoints
    if (cleanPath.contains("/api/") || cleanPath.contains("/apis/") || cleanPath.contains("/youtubei/") ||
        cleanPath.contains("/stats/") || cleanPath.contains("/log") || cleanPath.contains("/logging") ||
        cleanPath.contains("/ptracking") || cleanPath.contains("/beacon") || cleanPath.contains("/telemetry") ||
        cleanPath.contains("/track") || cleanPath.contains("/metrics") || cleanPath.contains("/collect") ||
        cleanPath.contains("generate_204") || cleanPath.contains("/recaptcha") ||
        cleanPath.contains("play.google.com/log") ||
        ((cleanPath.contains("/embed/") || cleanPath.contains("/iframe")) && !KNOWN_MEDIA_EXTENSIONS.any { cleanPath.endsWith(".$it") })
    ) {
        return false
    }

    val candidate = when {
        lower.startsWith("http://") || lower.startsWith("https://") -> url
        lower.startsWith("//") -> "https:$url"
        lower.startsWith("/") -> "https://placeholder.invalid$url"
        else -> "https://placeholder.invalid/$url"
    }
    val uri = runCatching { URI(candidate) }.getOrNull()
    val host = uri?.host?.lowercase(Locale.ROOT)?.takeIf { it != "placeholder.invalid" } ?: ""
    val path = uri?.path?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: cleanPath

    if (host == "accounts.google.com" || host == "play.google.com" || host == "myactivity.google.com" ||
        host == "consent.youtube.com" || host == "consent.google.com"
    ) {
        return false
    }

    // YouTube HTML pages (shorts, watch, channel, embed, feed) are web documents, not direct media items
    if ((host.contains("youtube.com") || host.contains("youtu.be")) &&
        (path.contains("/watch") || path.contains("/shorts/") || path.contains("/channel/") ||
         path.contains("/feed/") || path.contains("/embed/") || path.contains("/results")) &&
        !KNOWN_MEDIA_EXTENSIONS.any { cleanPath.endsWith(".$it") }
    ) {
        return false
    }

    // Host/path-aware non-media webpage rejections:
    val isWikiHost = host.contains("wikipedia.org") || host.contains("wikimedia.org") ||
            host.contains("wiktionary.org") || host.contains("wikidata.org") ||
            host.contains("wikibooks.org") || host.contains("wikiquote.org") ||
            host.contains("wikisource.org") || host.contains("wikinews.org") ||
            host.contains("wikivoyage.org") || host.contains("mediawiki.org")

    if (isWikiHost && (path.contains("/geohack") || path.contains("centralautologin") || path.contains("/api.php"))) {
        return false
    }
    // Reddit thread / comments pages (not direct media CDNs)
    if ((host.contains("reddit.com") || host.contains("redd.it")) &&
        !host.startsWith("i.") && !host.startsWith("v.") && !host.startsWith("preview.") &&
        path.contains("/comments/")
    ) {
        return false
    }
    // Pinterest pin discussion pages (not direct image CDN)
    if ((host.contains("pinterest.com") || host.contains("pin.it")) &&
        !host.startsWith("i.") &&
        path.contains("/pin/")
    ) {
        return false
    }
    // Imgur gallery web albums (not direct CDN images)
    if (host.contains("imgur.com") && !host.startsWith("i.") &&
        (path.contains("/gallery/") || path.startsWith("/gallery") || path.startsWith("/a/"))
    ) {
        return false
    }
    // Twitter/X status pages (not direct media CDNs)
    if ((host.contains("twitter.com") || host.contains("x.com")) &&
        !host.startsWith("pbs.") && !host.startsWith("video.") &&
        path.contains("/status/")
    ) {
        return false
    }
    // MediaWiki / Wikipedia article and discussion pages (HTML pages)
    if (path.contains("/wiki/file:") || path.contains("/wiki/image:") || path.contains("/wiki/special:")) {
        return false
    }
    if (isWikiHost && !host.contains("upload.wikimedia.org") && !host.contains("thumb.wikimedia.org")) {
        if (path.contains("/wiki/") || path.contains("/w/index.php") || path.contains("/w/api.php") ||
            path.contains("/file:") || path.contains("/image:") ||
            cleanPath.contains("file:") || cleanPath.contains("image:")
        ) {
            return false
        }
    }
    if (path.startsWith("/w/index.php") || path.contains("/w/index.php")) {
        return false
    }
    if (isMediaFragment(url)) {
        return false
    }
    return true
}
