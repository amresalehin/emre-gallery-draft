package com.amresalehin.emregallery.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URL

data class MediaRequestContext(val url: String, val domain: String) {
    companion object {
        fun create(targetUrl: String): MediaRequestContext {
            val trimmed = targetUrl.trim()
            val normalized = if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
                "https://$trimmed"
            } else trimmed
            return MediaRequestContext(normalized, runCatching { URL(normalized).host }.getOrDefault(""))
        }
    }
}

/**
 * Single-authority extractor coordinator.
 *
 * Platform extractors are authoritative. Page-wide HTML and Chromium interception are excluded
 * because they enumerate posters, previews, navigation art, fragments, and recommendations.
 */
class DualMediaExtractor(@Suppress("unused") private val context: Context) {
    data class ExtractionState(
        val isRunning: Boolean = false,
        val statusMessage: String = "",
        val phase: ExtractionPhase = ExtractionPhase.IDLE,
        val pageTitle: String = "",
        val domain: String = "",
        val items: List<ScrapedMediaItem> = emptyList(),
        val errorMessage: String? = null
    )

    enum class ExtractionPhase {
        IDLE, DIRECT_MEDIA, GALLERY_DL, COMPLETED, ERROR
    }

    private val _state = MutableStateFlow(ExtractionState())
    val state: StateFlow<ExtractionState> = _state.asStateFlow()
    private var extractionJob: Job? = null

    fun extract(targetUrl: String, scope: CoroutineScope) {
        cancel()
        if (!MediaClassifier.isValidWebUrl(targetUrl.trim())) {
            _state.value = ExtractionState(
                phase = ExtractionPhase.ERROR,
                errorMessage = "Please enter a valid URL (for example, https://example.com)."
            )
            return
        }

        val request = MediaRequestContext.create(targetUrl)
        _state.value = ExtractionState(
            isRunning = true,
            statusMessage = "Resolving media source…",
            phase = ExtractionPhase.DIRECT_MEDIA,
            domain = request.domain
        )

        extractionJob = scope.launch(Dispatchers.IO) {
            try {
                val direct = directMediaItem(request)
                if (direct != null) {
                    publish(listOf(direct), request, direct.title, "Direct media link resolved.")
                    return@launch
                }

                _state.value = _state.value.copy(
                    phase = ExtractionPhase.GALLERY_DL,
                    statusMessage = "Checking platform extractors…"
                )
                val support = GalleryDlEngine.checkSupport(request.url)
                var result: GalleryDlExtractResult? = null

                if (support.ytDlpSupported) {
                    _state.value = _state.value.copy(statusMessage = "Extracting primary media with yt-dlp…")
                    val ytResult = GalleryDlEngine.extractYtDlp(request.url)
                    if (ytResult.success && ytResult.items.isNotEmpty()) {
                        result = ytResult
                    } else {
                        Log.i(TAG, "yt-dlp returned no media; trying gallery-dl fallback: ${ytResult.errorMessage}")
                    }
                }

                if ((result == null || result.items.isEmpty()) && support.galleryDlSupported) {
                    _state.value = _state.value.copy(statusMessage = "Extracting gallery items with gallery-dl…")
                    result = GalleryDlEngine.extractGalleryDl(request.url)
                }

                val resolved = MediaResolver.resolve(result?.items.orEmpty())
                if (resolved.isNotEmpty()) {
                    publish(
                        resolved, request, result?.pageTitle.orEmpty(),
                        "Extraction complete. Found ${resolved.size} unique media item(s)."
                    )
                } else {
                    val error = result?.errorMessage?.takeIf { it.isNotBlank() }
                        ?: if (!support.isSupported) {
                            "This URL is not supported by the installed gallery-dl or yt-dlp extractors."
                        } else {
                            "No primary downloadable media was returned by the platform extractor."
                        }
                    _state.value = _state.value.copy(
                        isRunning = false,
                        phase = ExtractionPhase.ERROR,
                        statusMessage = error,
                        errorMessage = error
                    )
                }
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isRunning = false, phase = ExtractionPhase.IDLE, statusMessage = "Extraction stopped.")
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Media extraction failed", error)
                val message = error.localizedMessage ?: "Media extraction failed."
                _state.value = _state.value.copy(
                    isRunning = false, phase = ExtractionPhase.ERROR, statusMessage = message, errorMessage = message
                )
            }
        }
    }

    private fun directMediaItem(request: MediaRequestContext): ScrapedMediaItem? {
        if (!MediaClassifier.isValidMedia(request.url)) return null
        val resolved = MediaClassifier.resolveOriginalMedia(request.url)
        val classification = MediaClassifier.classify(resolved.mediaUrl)
        if (!(classification.isVideo || classification.isAudio || classification.isStream ||
                classification.category == MediaCategory.IMAGES)
        ) return null

        return ScrapedMediaItem(
            id = "direct_${resolved.mediaUrl.hashCode()}",
            url = resolved.mediaUrl,
            title = resolved.cleanTitle ?: MediaClassifier.deriveFileName(resolved.mediaUrl, classification.format, 0),
            isVideo = classification.isVideo,
            isAudio = classification.isAudio,
            isStream = classification.isStream,
            category = classification.category,
            format = classification.format,
            thumbnailUrl = resolved.thumbnailUrl ?: if (classification.category == MediaCategory.IMAGES) resolved.mediaUrl else null,
            mimeType = classification.mimeType,
            source = "Direct Link",
            provenance = MediaProvenance("Direct URL", request.url),
            metadata = mapOf("source_url" to request.url),
            downloadHeaders = mapOf("User-Agent" to OkHttpDownloader.DEFAULT_USER_AGENT, "Referer" to request.url)
        )
    }

    private fun publish(items: List<ScrapedMediaItem>, request: MediaRequestContext, title: String, status: String) {
        _state.value = _state.value.copy(
            isRunning = false,
            phase = ExtractionPhase.COMPLETED,
            pageTitle = title.ifBlank { _state.value.pageTitle.ifBlank { request.domain } },
            domain = request.domain,
            items = MediaResolver.resolve(items),
            statusMessage = status,
            errorMessage = null
        )
    }

    fun addDiscoveredItems(newList: List<ScrapedMediaItem>, title: String? = null, domain: String? = null) {
        val resolved = MediaResolver.resolve(_state.value.items + newList)
        _state.value = _state.value.copy(
            items = resolved,
            pageTitle = title?.takeIf { it.isNotBlank() } ?: _state.value.pageTitle,
            domain = domain?.takeIf { it.isNotBlank() } ?: _state.value.domain,
            statusMessage = "Showing ${resolved.size} unique media item(s)."
        )
    }

    fun addDiscoveredItem(item: ScrapedMediaItem) = addDiscoveredItems(listOf(item))

    fun cancel() {
        extractionJob?.cancel()
        extractionJob = null
        if (_state.value.isRunning) {
            _state.value = _state.value.copy(isRunning = false, phase = ExtractionPhase.IDLE, statusMessage = "Extraction stopped.")
        }
    }

    fun reset() {
        cancel()
        _state.value = ExtractionState()
    }

    fun cleanup() = reset()

    companion object {
        private const val TAG = "DualMediaExtractor"
    }
}
