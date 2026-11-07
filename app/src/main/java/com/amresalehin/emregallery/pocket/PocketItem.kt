package com.amresalehin.emregallery.pocket

import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Data model for saved articles, bookmarks, and visual clippings in the Pocket library.
 */
data class PocketItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val domain: String = extractDomain(url),
    val imageUrls: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val isFavorite: Boolean = false,
    val isRead: Boolean = false,
    /** JSON-encoded downloaded media attachments; actual files remain in app storage. */
    val attachments: List<String> = emptyList()
) {
    val primaryImage: String?
        get() = imageUrls.firstOrNull()

    val formattedDateTime: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy · h:mm a", Locale.US)
            return sdf.format(Date(timestamp))
        }

    val faviconSymbol: String
        get() = when {
            domain.contains("wikipedia", ignoreCase = true) -> "W"
            domain.contains("britannica", ignoreCase = true) -> "🏛"
            domain.contains("google", ignoreCase = true) -> "G"
            domain.contains("researchgate", ignoreCase = true) -> "Rᵍ"
            domain.contains("github", ignoreCase = true) -> "⌗"
            domain.contains("medium", ignoreCase = true) -> "M"
            domain.contains("nationalgeographic", ignoreCase = true) -> "□"
            else -> domain.take(1).uppercase(Locale.ROOT).ifEmpty { "✦" }
        }

    companion object {
        fun extractDomain(url: String): String {
            return try {
                val cleaned = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "https://$url"
                } else {
                    url
                }
                val host = URI(cleaned).host ?: ""
                host.removePrefix("www.")
            } catch (_: Exception) {
                url.substringBefore("/").removePrefix("www.")
            }
        }
    }
}
