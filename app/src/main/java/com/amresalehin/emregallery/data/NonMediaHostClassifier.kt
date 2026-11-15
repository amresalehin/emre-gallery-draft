package com.amresalehin.emregallery.data

import java.net.URI
import java.util.Locale

/**
 * Classifies non-media API/tracking endpoints and social/wiki HTML wrapper pages
 * that should not be treated as standalone downloadable media assets.
 */
internal object NonMediaHostClassifier {

    private val BLOCKED_GOOGLE_HOSTS = setOf(
        "accounts.google.com", "play.google.com", "myactivity.google.com",
        "consent.youtube.com", "consent.google.com"
    )

    private val WIKI_HOST_KEYWORDS = listOf(
        "wikipedia.org", "wikimedia.org", "wiktionary.org", "wikidata.org",
        "wikibooks.org", "wikiquote.org", "wikisource.org", "wikinews.org",
        "wikivoyage.org", "mediawiki.org"
    )

    private val TELEMETRY_PATH_TOKENS = listOf(
        "/api/", "/apis/", "/youtubei/", "/stats/", "/log", "/logging",
        "/ptracking", "/beacon", "/telemetry", "/track", "/metrics",
        "/collect", "generate_204", "/recaptcha", "play.google.com/log"
    )

    fun isNonMediaEndpoint(cleanPath: String): Boolean {
        if (TELEMETRY_PATH_TOKENS.any { cleanPath.contains(it) }) {
            return true
        }
        return (cleanPath.contains("/embed/") || cleanPath.contains("/iframe")) &&
            !MediaClassifier.KNOWN_MEDIA_EXTENSIONS.any { cleanPath.endsWith(".$it") }
    }

    fun isNonMediaHostPage(url: String, lower: String, cleanPath: String): Boolean {
        val candidate = when {
            lower.startsWith("http://") || lower.startsWith("https://") -> url
            lower.startsWith("//") -> "https:$url"
            lower.startsWith("/") -> "https://placeholder.invalid$url"
            else -> "https://placeholder.invalid/$url"
        }
        val uri = runCatching { URI(candidate) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.ROOT)?.takeIf { it != "placeholder.invalid" } ?: ""
        val path = uri?.path?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: cleanPath

        if (host in BLOCKED_GOOGLE_HOSTS) return true

        // YouTube HTML pages (shorts, watch, channel, embed, feed) are web documents, not direct media items
        if ((host.contains("youtube.com") || host.contains("youtu.be")) &&
            (path.contains("/watch") || path.contains("/shorts/") || path.contains("/channel/") ||
                path.contains("/feed/") || path.contains("/embed/") || path.contains("/results")) &&
            !MediaClassifier.KNOWN_MEDIA_EXTENSIONS.any { cleanPath.endsWith(".$it") }
        ) {
            return true
        }

        val isWikiHost = WIKI_HOST_KEYWORDS.any { host.contains(it) }
        if (isWikiHost && (path.contains("/geohack") || path.contains("centralautologin") || path.contains("/api.php"))) {
            return true
        }

        // Reddit thread / comments pages (not direct media CDNs)
        if ((host.contains("reddit.com") || host.contains("redd.it")) &&
            !host.startsWith("i.") && !host.startsWith("v.") && !host.startsWith("preview.") &&
            path.contains("/comments/")
        ) {
            return true
        }

        // Pinterest pin discussion pages (not direct image CDN)
        if ((host.contains("pinterest.com") || host.contains("pin.it")) &&
            !host.startsWith("i.") &&
            path.contains("/pin/")
        ) {
            return true
        }

        // Imgur gallery web albums (not direct CDN images)
        if (host.contains("imgur.com") && !host.startsWith("i.") &&
            (path.contains("/gallery/") || path.startsWith("/gallery") || path.startsWith("/a/"))
        ) {
            return true
        }

        // Twitter/X status pages (not direct media CDNs)
        if ((host.contains("twitter.com") || host.contains("x.com")) &&
            !host.startsWith("pbs.") && !host.startsWith("video.") &&
            path.contains("/status/")
        ) {
            return true
        }

        // MediaWiki / Wikipedia article and discussion pages (HTML pages)
        if (path.contains("/wiki/file:") || path.contains("/wiki/image:") || path.contains("/wiki/special:")) {
            return true
        }
        if (isWikiHost && !host.contains("upload.wikimedia.org") && !host.contains("thumb.wikimedia.org")) {
            if (path.contains("/wiki/") || path.contains("/w/index.php") || path.contains("/w/api.php") ||
                path.contains("/file:") || path.contains("/image:") ||
                cleanPath.contains("file:") || cleanPath.contains("image:")
            ) {
                return true
            }
        }
        return path.startsWith("/w/index.php") || path.contains("/w/index.php")
    }
}
