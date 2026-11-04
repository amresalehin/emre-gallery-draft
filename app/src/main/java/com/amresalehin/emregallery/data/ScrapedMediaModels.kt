package com.amresalehin.emregallery.data

data class MediaProvenance(
    val extractorEngine: String = "Extractor",
    val pageUrl: String = "",
    val domSelector: String? = null
)

/**
 * One logical downloadable asset. Split adaptive video is one item with a video URL and audioUrl.
 */
data class ScrapedMediaItem(
    val id: String,
    val url: String,
    val title: String,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val isStream: Boolean = false,
    val category: MediaCategory = when {
        isStream -> MediaCategory.STREAMS
        isVideo -> MediaCategory.VIDEOS
        isAudio -> MediaCategory.AUDIO
        else -> MediaCategory.IMAGES
    },
    val format: String = when {
        isStream -> "HLS/DASH"
        isVideo -> "MP4"
        isAudio -> "AUDIO"
        else -> "IMAGE"
    },
    val thumbnailUrl: String? = null,
    val source: String = "Extractor",
    val mimeType: String = when {
        isStream -> "application/x-mpegURL"
        isVideo -> "video/mp4"
        isAudio -> "audio/mpeg"
        else -> "image/jpeg"
    },
    val resolutionHint: String? = null,
    val sizeBytes: Long? = null,
    val provenance: MediaProvenance = MediaProvenance(extractorEngine = source),
    val metadata: Map<String, String> = emptyMap(),
    val downloadHeaders: Map<String, String> = emptyMap(),
    val detectedAt: Long = System.currentTimeMillis(),
    val audioUrl: String? = null,
    val hasAudio: Boolean? = null,
    val canonicalMediaId: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val audioCodec: String? = null,
    val videoCodec: String? = null
)

data class ScrapeResult(
    val sourceUrl: String,
    val pageTitle: String,
    val domain: String,
    val items: List<ScrapedMediaItem>,
    val errorMessage: String? = null
)
