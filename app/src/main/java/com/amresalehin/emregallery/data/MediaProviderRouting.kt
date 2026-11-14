package com.amresalehin.emregallery.data

import java.net.URI
import java.util.Locale

/** Shared Kotlin routing policy for URLs that need yt-dlp page resolution. */
object MediaProviderRouting {
    private val ytDlpPageHosts = setOf(
        "youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "dailymotion.com",
        "twitch.tv", "soundcloud.com", "bandcamp.com", "facebook.com", "instagram.com",
        "twitter.com", "x.com", "bilibili.com", "bitchute.com", "rumble.com",
        "reddit.com", "redd.it", "streamable.com", "loom.com", "kick.com",
        "odysee.com", "archive.org", "mixcloud.com", "coub.com", "liveleak.com"
    )

    fun hostOf(rawUrl: String): String? {
        val parsed = runCatching {
            URI(if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl)
        }.getOrNull() ?: return null
        return parsed.host?.lowercase(Locale.ROOT)?.trimEnd('.')
    }

    fun hostMatches(host: String?, domain: String): Boolean {
        val normalized = host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        val expected = domain.lowercase(Locale.ROOT).trimEnd('.')
        return normalized == expected || normalized.endsWith(".$expected")
    }

    fun isYtDlpPageUrl(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return ytDlpPageHosts.any { hostMatches(host, it) }
    }

    fun isManifestUrl(url: String): Boolean {
        val path = runCatching { URI(url).rawPath.orEmpty() }.getOrDefault("").lowercase(Locale.ROOT)
        return path.endsWith(".m3u8") || path.endsWith(".mpd")
    }

    fun requiresVideoResult(url: String): Boolean = isYtDlpPageUrl(url) || isManifestUrl(url)
}
