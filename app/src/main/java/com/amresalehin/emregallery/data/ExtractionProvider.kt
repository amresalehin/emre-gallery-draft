package com.amresalehin.emregallery.data

import java.net.URL

data class MediaRequestContext(
    val url: String,
    val domain: String
) {
    companion object {
        fun create(targetUrl: String): MediaRequestContext {
            val trimmed = targetUrl.trim()
            val normalized = if (
                !trimmed.startsWith("http://", ignoreCase = true) &&
                !trimmed.startsWith("https://", ignoreCase = true)
            ) {
                "https://$trimmed"
            } else {
                trimmed
            }
            val domain = runCatching { URL(normalized).host }.getOrDefault("")
            return MediaRequestContext(url = normalized, domain = domain)
        }
    }
}

data class MediaExtractionResult(
    val items: List<ScrapedMediaItem> = emptyList(),
    val pageTitle: String? = null,
    val errorMessage: String? = null
)

interface ExtractionProvider {
    fun isApplicable(context: MediaRequestContext): Boolean
    suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult
}
