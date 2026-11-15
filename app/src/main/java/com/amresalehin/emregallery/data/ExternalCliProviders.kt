package com.amresalehin.emregallery.data

class GalleryDlProvider : ExtractionProvider {
    override fun isApplicable(context: MediaRequestContext): Boolean {
        return GalleryDlEngine.isAvailable() && GalleryDlEngine.checkSupport(context.url).isSupported
    }

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult {
        onStatusUpdate("Extracting with gallery-dl...")
        val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(context.url).takeIf { it.isNotBlank() }
        val res = GalleryDlEngine.extractGalleryDl(
            context.url,
            GalleryDlOptions(cookies = cookies)
        )
        if (res.success) {
            for (item in res.items) {
                onItemDiscovered(item)
            }
            return MediaExtractionResult(items = res.items, pageTitle = res.pageTitle)
        }
        return MediaExtractionResult(items = emptyList(), errorMessage = res.errorMessage)
    }
}

class YtDlpProvider : ExtractionProvider {
    override fun isApplicable(context: MediaRequestContext): Boolean {
        if (!GalleryDlEngine.isAvailable()) return false
        val lower = context.url.lowercase()
        val videoHosts = listOf(
            "youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "dailymotion.com",
            "twitch.tv", "soundcloud.com", "bandcamp.com", "facebook.com", "instagram.com"
        )
        return videoHosts.any { lower.contains(it) } || lower.contains(".m3u8") || lower.contains(".mpd")
    }

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult {
        onStatusUpdate("Extracting with yt-dlp...")
        val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(context.url).takeIf { it.isNotBlank() }
        val res = GalleryDlEngine.extractYtDlp(
            context.url,
            GalleryDlOptions(cookies = cookies)
        )
        if (res.success) {
            for (item in res.items) {
                onItemDiscovered(item)
            }
            return MediaExtractionResult(items = res.items, pageTitle = res.pageTitle)
        }
        return MediaExtractionResult(items = emptyList(), errorMessage = res.errorMessage)
    }
}
