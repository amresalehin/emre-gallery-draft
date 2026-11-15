package com.amresalehin.emregallery.data

/**
 * Thread-safe tracker that deduplicates discovered [ScrapedMediaItem]s, preserves the highest-quality
 * variant per canonical URL, and dispatches accepted items to listeners and [DownloaderSession].
 */
internal class BrowserDiscoveredMediaTracker(
    private val normalizedBaseUrl: String,
    private val isCancelled: () -> Boolean,
    private val onMediaDiscovered: (ScrapedMediaItem) -> Unit
) {
    private val seenMap = mutableMapOf<String, ScrapedMediaItem>()

    fun buildAndDispatch(
        rawUrl: String,
        source: ExtractionSource,
        poster: String? = null,
        resolution: String? = null,
        mimeHint: String? = null
    ) {
        if (isCancelled()) return
        val indexHint = synchronized(seenMap) { seenMap.size }
        val candidateItem = MediaCaptureEngine.buildMediaItem(
            rawUrl = rawUrl,
            source = source,
            baseUrl = normalizedBaseUrl,
            poster = poster,
            resolutionHint = resolution,
            mimeHint = mimeHint,
            indexHint = indexHint
        ) ?: return

        recordAndDispatch(candidateItem)
    }

    fun recordAndDispatchAll(items: List<ScrapedMediaItem>) {
        for (item in items) {
            recordAndDispatch(item)
        }
    }

    fun recordAndDispatch(item: ScrapedMediaItem) {
        if (isCancelled()) return
        val key = MediaDeduplicator.canonicalKey(item.url)
        synchronized(seenMap) {
            val existing = seenMap[key]
            if (existing != null && MediaQualityEvaluator.compareQuality(item, existing) <= 0) {
                return
            }
            seenMap[key] = item
        }

        onMediaDiscovered(item)
        DownloaderSession.addDiscoveredItem(item)
    }
}
