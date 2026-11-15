package com.amresalehin.emregallery.data

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine

class BrowserProvider(context: Context) : ExtractionProvider {
    private val browserExtractor = BrowserMediaExtractor(context)

    override fun isApplicable(context: MediaRequestContext): Boolean = true

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            browserExtractor.cleanup()
        }
        val discoveredList = mutableListOf<ScrapedMediaItem>()
        browserExtractor.startLiveExtraction(
            targetUrl = context.url,
            onStatusUpdate = onStatusUpdate,
            onMediaDiscovered = { item ->
                discoveredList.add(item)
                onItemDiscovered(item)
            },
            onFinished = {
                if (continuation.isActive) {
                    continuation.resume(
                        MediaExtractionResult(items = discoveredList),
                        onCancellation = { browserExtractor.cleanup() }
                    )
                }
                browserExtractor.cleanup()
            }
        )
    }

    fun cleanup() {
        browserExtractor.cleanup()
    }
}
