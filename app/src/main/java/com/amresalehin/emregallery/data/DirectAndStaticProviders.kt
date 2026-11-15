package com.amresalehin.emregallery.data

class DirectMediaProvider : ExtractionProvider {
    override fun isApplicable(context: MediaRequestContext): Boolean {
        if (!MediaUrlValidator.isValidMedia(context.url)) return false
        val classification = MediaClassifier.classify(context.url)
        return classification.isVideo || classification.isAudio || classification.isStream ||
            context.url.endsWith(".jpg", true) || context.url.endsWith(".jpeg", true) ||
            context.url.endsWith(".png", true) || context.url.endsWith(".webp", true) ||
            context.url.endsWith(".gif", true) || context.url.endsWith(".svg", true)
    }

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult {
        val resolvedInfo = OriginalMediaResolver.resolve(context.url)
        val downloadUrl = resolvedInfo.mediaUrl
        val classification = MediaClassifier.classify(downloadUrl)
        val fileName = resolvedInfo.cleanTitle ?: MediaClassifier.deriveFileName(downloadUrl, classification.format, 0)
        val thumb = resolvedInfo.thumbnailUrl ?: if (classification.category == MediaCategory.IMAGES) downloadUrl else null
        val item = ScrapedMediaItem(
            id = "direct_${System.currentTimeMillis()}",
            url = downloadUrl,
            title = fileName,
            isVideo = classification.isVideo,
            isAudio = classification.isAudio,
            isStream = classification.isStream,
            category = classification.category,
            format = classification.format,
            thumbnailUrl = thumb,
            mimeType = classification.mimeType,
            source = "Direct Link",
            provenance = MediaProvenance(
                extractorEngine = "Direct URL",
                pageUrl = context.url
            ),
            metadata = mapOf("source_url" to context.url),
            downloadHeaders = mapOf(
                "User-Agent" to OkHttpDownloader.DEFAULT_USER_AGENT,
                "Referer" to context.url
            )
        )
        onItemDiscovered(item)
        return MediaExtractionResult(items = listOf(item), pageTitle = fileName)
    }
}

class StaticHtmlProvider : ExtractionProvider {
    private val scraper = StaticMediaScraper()

    override fun isApplicable(context: MediaRequestContext): Boolean = true

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult {
        onStatusUpdate("Parsing page HTML & DOM...")
        val res = scraper.scrape(context.url)
        for (item in res.items) {
            onItemDiscovered(item)
        }
        return MediaExtractionResult(
            items = res.items,
            pageTitle = res.pageTitle,
            errorMessage = res.errorMessage
        )
    }
}
