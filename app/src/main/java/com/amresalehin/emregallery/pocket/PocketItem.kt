package com.amresalehin.emregallery.pocket

import android.net.Uri
import java.io.File
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
    /** Archived bookmarks are retained but hidden from the active library by default. */
    val isArchived: Boolean = false,
    val canonicalUrl: String? = null,
    val siteName: String? = null,
    val faviconUrl: String? = null,
    /** Bounded readable text extracted from the rendered page; used for offline reading and search. */
    val readableContent: String = "",
    /** Absolute path to the locally generated WebView viewport preview. */
    val previewImagePath: String? = null
) {
    val previewImageUri: String?
        get() = previewImagePath
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.fromFile(File(it)).toString() }

    val primaryImage: String?
        get() = previewImageUri ?: imageUrls.firstOrNull()

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
