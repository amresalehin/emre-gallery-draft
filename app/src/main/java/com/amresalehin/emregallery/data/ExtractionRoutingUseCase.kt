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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URL

/**

internal fun ExtractionCoordinator.extractImpl(targetUrl: String, scope: CoroutineScope, directOnly: Boolean = false) {
    cancel()

    val trimmed = targetUrl.trim()
    if (!MediaClassifier.isValidWebUrl(trimmed)) {
        _state.value = ExtractionState(
            errorMessage = "Please enter a valid URL (e.g. https://example.com)",
            isRunning = false,
            items = emptyList()
        )
        return
    }

    val requestContext = MediaRequestContext.create(trimmed)
    val domain = requestContext.domain
    val expectation = determineExpectation(requestContext.url)

    _state.value = ExtractionState(
        isRunning = true,
        statusMessage = "Analyzing URL route...",
        phase = ExtractionPhase.DIRECT_MEDIA,
        domain = domain,
        items = emptyList()
    )

    extractionJob = scope.launch {
        val itemsMap = LinkedHashMap<String, ScrapedMediaItem>()
        val itemsMutex = Mutex()
        var lastDiagnosticError: String? = null

        suspend fun updateItems(
            newList: List<ScrapedMediaItem>,
            title: String? = null,
            msg: String? = null,
            phase: ExtractionPhase? = null
        ) = itemsMutex.withLock {
            val modified = MediaNormalizer.mergeCandidates(itemsMap, newList)
            if (modified || msg != null || phase != null || title != null) {
                val currentItems = itemsMap.values.toList()
                _state.value = _state.value.copy(
                    items = currentItems,
                    pageTitle = if (_state.value.pageTitle.isBlank() && !title.isNullOrBlank()) title else _state.value.pageTitle,
                    statusMessage = msg ?: _state.value.statusMessage,
                    phase = phase ?: _state.value.phase
                )
                DownloaderSession.addDiscoveredItems(currentItems)
                if (!title.isNullOrBlank()) DownloaderSession.setPageTitle(title)
                if (msg != null) DownloaderSession.setStatusMessage(msg)
            }
        }

        suspend fun updateStatus(status: String, phase: ExtractionPhase? = null) = itemsMutex.withLock {
            _state.value = _state.value.copy(
                statusMessage = status,
                phase = phase ?: _state.value.phase
            )
        }

        try {
            // Step 1: OkHttp Direct-Media Fast Path Probe
            updateStatus("Probing URL headers with OkHttp...", ExtractionPhase.DIRECT_MEDIA)
            val directProbe = OkHttpDownloader.probeDirectMedia(requestContext.url)

            if (directProbe.isConfirmedDirectMedia) {
                val finalUrl = directProbe.finalUrl
                val ct = directProbe.contentType
                val cd = directProbe.contentDisposition
                val cl = directProbe.contentLength

                val classification = MediaClassifier.classify(finalUrl, mimeHint = ct)
                val derivedTitle = if (!cd.isNullOrBlank() && cd.contains("filename=", ignoreCase = true)) {
                    cd.substringAfter("filename=").replace("\"", "").trim()
                } else {
                    MediaClassifier.deriveFileName(finalUrl, classification.format, 0)
                }

                val directItem = ScrapedMediaItem(
                    id = "direct_${System.currentTimeMillis()}_${(finalUrl.hashCode() and 0xFFFF)}",
                    url = finalUrl,
                    title = derivedTitle,
                    isVideo = classification.isVideo,
                    isAudio = classification.isAudio,
                    isStream = classification.isStream,
                    category = classification.category,
                    format = classification.format,
                    mimeType = classification.mimeType,
                    sizeBytes = cl,
                    source = "Direct HTTP",
                    provenance = MediaProvenance(extractorEngine = "OkHttp Direct", pageUrl = requestContext.url)
                )

                updateItems(listOf(directItem), title = derivedTitle, phase = ExtractionPhase.COMPLETED)
                finishSuccess(itemsMap.size)
                return@launch
            }

            // Step 2: Inconclusive / Not direct media -> Concurrent Multi-Engine Discovery
            updateStatus("Initiating concurrent discovery (yt-dlp, gallery-dl, Browser Session)...", ExtractionPhase.YT_DLP)

            val supportInfo = withContext(Dispatchers.IO) {
                GalleryDlEngine.checkSupport(requestContext.url)
            }

            val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(requestContext.url).takeIf { it.isNotBlank() }
            val options = GalleryDlOptions(cookies = cookies)

            // Launch concurrent discovery jobs:
            // Rule 1: No exclusive extractor selection. Both yt-dlp and gallery-dl run concurrently.
            // Rule 2: Browser discovery is complementary. Runs to discover dynamic network requests and DOM media.
            // Rule 5: One unified candidate manager.
            val ytdlpJob = launch(Dispatchers.IO) {
                if (GalleryDlEngine.isYtDlpAvailable()) {
                    try {
                        val res = GalleryDlEngine.extractYtDlp(requestContext.url, options)
                        if (res.success && res.items.isNotEmpty()) {
                            updateItems(res.items, title = res.pageTitle, msg = "Discovered ${res.items.size} items via yt-dlp")
                        } else if (!res.errorMessage.isNullOrBlank()) {
                            lastDiagnosticError = res.errorMessage
                        }
                    } catch (e: Exception) {
                        Log.w("ExtractionCoordinator", "Concurrent yt-dlp error", e)
                    }
                }
            }

            val galleryDlJob = launch(Dispatchers.IO) {
                if (GalleryDlEngine.isGalleryDlAvailable()) {
                    try {
                        val res = GalleryDlEngine.extractGalleryDl(requestContext.url, options)
                        if (res.success && res.items.isNotEmpty()) {
                            updateItems(res.items, title = res.pageTitle, msg = "Discovered ${res.items.size} items via gallery-dl")
                        } else if (!res.errorMessage.isNullOrBlank() && lastDiagnosticError == null) {
                            lastDiagnosticError = res.errorMessage
                        }
                    } catch (e: Exception) {
                        Log.w("ExtractionCoordinator", "Concurrent gallery-dl error", e)
                    }
                }
            }

            val browserJob = launch(Dispatchers.Main) {
                try {
                    val browserRes = BrowserSession.extractHeadless(
                        context = context,
                        targetUrl = requestContext.url,
                        onStatusUpdate = { s -> launch { updateStatus(s, ExtractionPhase.BROWSER_INTERCEPTING) } },
                        onMediaDiscovered = { item -> launch { updateItems(listOf(item)) } }
                    )
                    if (browserRes.items.isNotEmpty()) {
                        updateItems(browserRes.items, title = browserRes.pageTitle)
                    } else if (lastDiagnosticError == null) {
                        lastDiagnosticError = browserRes.errorMessage
                    }
                } catch (e: Exception) {
                    Log.w("ExtractionCoordinator", "Concurrent BrowserSession error", e)
                }
            }

            // Await all concurrent discovery channels
            ytdlpJob.join()
            galleryDlJob.join()
            browserJob.join()

            // Check bot challenges or login requirement if candidates are still empty
            val needsSignInDirect = BotDetectionMitigator.indicatesSignInRequired(lastDiagnosticError)
            val isSupportedByEngines = supportInfo.isSupported
            val needsCookiesOrBot = BotDetectionMitigator.indicatesCookiesOrBotVerificationNeeded(lastDiagnosticError) ||
                    (isSupportedByEngines && itemsMap.isEmpty() && cookies.isNullOrBlank())

            if (needsSignInDirect && itemsMap.isEmpty()) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    requiresSignIn = true,
                    signInUrl = requestContext.url,
                    statusMessage = "Sign-in required for $domain",
                    errorMessage = "Sign in to $domain to access and download this media."
                )
                return@launch
            } else if (needsCookiesOrBot && itemsMap.isEmpty()) {
                updateStatus("Resolving session cookies in background for $domain...", ExtractionPhase.YT_DLP)
                _state.value = _state.value.copy(isObtainingCookiesInBackground = true)

                val cookieRes = withContext(Dispatchers.Main) {
                    BrowserSession.fetchCookiesInBackground(
                        context = context,
                        targetUrl = requestContext.url,
                        onStatusUpdate = { s -> launch { updateStatus(s) } }
                    )
                }
                _state.value = _state.value.copy(isObtainingCookiesInBackground = false)

                if (cookieRes.requiresSignIn) {
                    _state.value = _state.value.copy(
                        isRunning = false,
                        requiresSignIn = true,
                        signInUrl = cookieRes.signInUrl ?: requestContext.url,
                        statusMessage = "Sign-in required for $domain",
                        errorMessage = "Sign in to $domain to access and download this media."
                    )
                    return@launch
                } else if (!cookieRes.cookies.isNullOrBlank()) {
                    val updatedOptions = options.copy(cookies = cookieRes.cookies)
                    updateStatus("Cookies refreshed. Re-probing with yt-dlp & gallery-dl...", ExtractionPhase.YT_DLP)

                    val retryYt = withContext(Dispatchers.IO) {
                        try {
                            GalleryDlEngine.extractYtDlp(requestContext.url, updatedOptions)
                        } catch (e: Exception) {
                            GalleryDlExtractResult(success = false, errorMessage = e.localizedMessage)
                        }
                    }
                    if (retryYt.success && retryYt.items.isNotEmpty()) {
                        updateItems(retryYt.items, title = retryYt.pageTitle)
                    }

                    val retryGdl = withContext(Dispatchers.IO) {
                        try {
                            GalleryDlEngine.extractGalleryDl(requestContext.url, updatedOptions)
                        } catch (e: Exception) {
                            GalleryDlExtractResult(success = false, errorMessage = e.localizedMessage)
                        }
                    }
                    if (retryGdl.success && retryGdl.items.isNotEmpty()) {
                        updateItems(retryGdl.items, title = retryGdl.pageTitle)
                    }
                }
            }

            // Evaluate merged discovery results
            if (itemsMap.isNotEmpty()) {
                finishSuccess(itemsMap.size)
                return@launch
            }

            // Final Evaluation if no items discovered
            val finalReason: String = lastDiagnosticError?.takeIf { it.isNotBlank() }
                ?: "No media items discovered by any discovery engine."

            _state.value = _state.value.copy(
                isRunning = false,
                phase = ExtractionPhase.ERROR,
                statusMessage = finalReason,
                errorMessage = finalReason
            )
        } catch (ce: CancellationException) {
            _state.value = _state.value.copy(
                isRunning = false,
                phase = ExtractionPhase.IDLE,
                statusMessage = "Extraction stopped"
            )
        } catch (e: Exception) {
            Log.e("ExtractionCoordinator", "Routing error", e)
            val err = e.localizedMessage ?: "Extraction failed"
            _state.value = _state.value.copy(
                isRunning = false,
                phase = ExtractionPhase.ERROR,
                statusMessage = "Extraction failed: $err",
                errorMessage = err
            )
        }
    }
}

internal fun ExtractionCoordinator.finishSuccess(count: Int) {
    _state.value = _state.value.copy(
        isRunning = false,
        phase = ExtractionPhase.COMPLETED,
        statusMessage = "Extraction completed. Discovered $count media items."
    )
}

