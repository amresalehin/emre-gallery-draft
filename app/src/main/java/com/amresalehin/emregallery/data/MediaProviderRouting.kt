package com.amresalehin.emregallery.data

import java.net.URI
import java.util.Locale

/** Deterministic URL-host policy for selecting the single site-specific extractor. */
object MediaProviderRouting {
    private val ytDlpHosts = setOf(
        "youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "dailymotion.com",
        "twitch.tv", "soundcloud.com", "bandcamp.com"
    )

    fun hostOf(url: String): String? = runCatching {
        val normalized = if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else "https://$url"
        URI(normalized).host?.lowercase(Locale.ROOT)?.removePrefix("www.")
    }.getOrNull()

    fun isYtDlpUrl(url: String): Boolean {
        val host = hostOf(url) ?: return false
        val path = runCatching { URI(if (url.startsWith("http", true)) url else "https://$url").path.orEmpty() }
            .getOrDefault("")
        val directStream = path.endsWith(".m3u8", true) || path.endsWith(".mpd", true)
        return directStream || ytDlpHosts.any { host == it || host.endsWith(".$it") }
    }
}
