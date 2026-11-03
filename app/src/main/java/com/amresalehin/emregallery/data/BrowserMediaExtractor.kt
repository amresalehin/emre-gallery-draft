package com.amresalehin.emregallery.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import java.net.URL

/**
 * Browser-assisted media extraction engine utilizing Android WebView.
 * Performs:
 * 1. Live network request interception for streaming media (HLS, DASH, MP4, MP3, etc.).
 * 2. Deep JavaScript DOM, computed CSS, srcset, and iframe inspection.
 * 3. Automatic page scrolling to trigger dynamic lazy loading.
 */
class BrowserMediaExtractor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var isCancelled = false

    @SuppressLint("SetJavaScriptEnabled")
    fun startLiveExtraction(
        targetUrl: String,
        onStatusUpdate: (String) -> Unit,
        onMediaDiscovered: (ScrapedMediaItem) -> Unit,
        onFinished: () -> Unit
    ) {
        mainHandler.post {
            isCancelled = false
            cleanup()

            val normalizedUrl = if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                "https://$targetUrl"
            } else {
                targetUrl
            }

            val domain = runCatching { URL(normalizedUrl).host }.getOrDefault("")
            onStatusUpdate("Initializing Chromium browser engine...")

            try {
                val wv = WebView(context.applicationContext)
                webView = wv

                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadsImagesAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }

                wv.webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (isCancelled) return
                        if (newProgress < 100) {
                            onStatusUpdate("Loading page: $newProgress% (monitoring network requests)...")
                        }
                    }
                }

                val seenKeys = mutableSetOf<String>()

                fun dispatchMediaItem(
                    rawUrl: String,
                    source: ExtractionSource,
                    poster: String? = null,
                    resolution: String? = null,
                    mimeHint: String? = null
                ) {
                    if (isCancelled) return
                    val resolved = MediaClassifier.resolveUrl(rawUrl, normalizedUrl) ?: return
                    if (!MediaClassifier.isValidMedia(resolved)) return

                    val key = MediaClassifier.canonicalKey(resolved)
                    synchronized(seenKeys) {
                        if (!seenKeys.add(key)) return
                    }

                    val classification = MediaClassifier.classify(resolved, mimeHint)
                    val title = MediaClassifier.deriveFileName(resolved, classification.format, seenKeys.size)

                    val item = ScrapedMediaItem(
                        id = "browser_${seenKeys.size}_${System.currentTimeMillis()}",
                        url = resolved,
                        title = title,
                        isVideo = classification.isVideo,
                        isAudio = classification.isAudio,
                        isStream = classification.isStream,
                        category = classification.category,
                        format = classification.format,
                        thumbnailUrl = if (classification.category == MediaCategory.IMAGES) resolved else poster,
                        source = source.label,
                        mimeType = classification.mimeType,
                        resolutionHint = resolution
                    )
                    onMediaDiscovered(item)
                }

                wv.webViewClient = object : WebViewClient() {

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (isCancelled || request == null) return null

                        val reqUrl = request.url.toString()
                        val reqLower = reqUrl.lowercase()

                        // Detect media network stream or file requests
                        val isVideoOrStream = reqLower.contains(".m3u8") || reqLower.contains(".mpd") ||
                                reqLower.contains(".mp4") || reqLower.contains(".webm") ||
                                reqLower.contains(".mov") || reqLower.contains(".mkv") ||
                                reqLower.contains("/hls/") || reqLower.contains("/dash/") ||
                                reqLower.contains("chunklist")

                        val isAudio = reqLower.contains(".mp3") || reqLower.contains(".m4a") ||
                                reqLower.contains(".aac") || reqLower.contains(".wav") ||
                                reqLower.contains(".ogg") || reqLower.contains(".flac") ||
                                reqLower.contains(".opus")

                        val isImage = reqLower.contains(".jpg") || reqLower.contains(".jpeg") ||
                                reqLower.contains(".png") || reqLower.contains(".webp") ||
                                reqLower.contains(".gif") || reqLower.contains(".avif")

                        if (isVideoOrStream || isAudio || isImage) {
                            val acceptHeader = request.requestHeaders?.get("Accept")
                            mainHandler.post {
                                dispatchMediaItem(
                                    rawUrl = reqUrl,
                                    source = ExtractionSource.NETWORK_INTERCEPT,
                                    mimeHint = acceptHeader
                                )
                            }
                        }

                        // Let the WebView continue loading normally
                        return null
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        if (isCancelled) return
                        onStatusUpdate("Browser loaded page. Active network interception listening...")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (isCancelled) return
                        onStatusUpdate("Scanning DOM, computed CSS, and dynamic media tags...")

                        // 1. Run deep DOM and CSS inspection
                        executeJsDomScan(wv, normalizedUrl) { rawEntries ->
                            for (entry in rawEntries) {
                                dispatchMediaItem(
                                    rawUrl = entry.url,
                                    source = entry.source,
                                    poster = entry.poster,
                                    resolution = entry.resolution
                                )
                            }

                            // 2. Perform automated scrolling sequence to uncover lazy media
                            if (!isCancelled) {
                                onStatusUpdate("Auto-scrolling page to trigger lazy-loaded media...")
                                executeAutoScrollSequence(wv, step = 1, maxSteps = 3) {
                                    // Final scan after scrolling
                                    if (!isCancelled) {
                                        executeJsDomScan(wv, normalizedUrl) { postScrollEntries ->
                                            for (entry in postScrollEntries) {
                                                dispatchMediaItem(
                                                    rawUrl = entry.url,
                                                    source = entry.source,
                                                    poster = entry.poster,
                                                    resolution = entry.resolution
                                                )
                                            }
                                            onStatusUpdate("Live browser extraction completed.")
                                            onFinished()
                                        }
                                    } else {
                                        onFinished()
                                    }
                                }
                            } else {
                                onFinished()
                            }
                        }
                    }
                }

                wv.loadUrl(normalizedUrl)
            } catch (e: Exception) {
                onStatusUpdate("Browser initialization failed: ${e.localizedMessage}")
                onFinished()
            }
        }
    }

    private data class JsMediaEntry(
        val url: String,
        val source: ExtractionSource,
        val poster: String? = null,
        val resolution: String? = null
    )

    private fun executeJsDomScan(
        wv: WebView,
        baseUrl: String,
        onResults: (List<JsMediaEntry>) -> Unit
    ) {
        val script = """
            (function() {
                var list = [];
                // 1. Images, picture sources, data-src, and srcset
                var imgs = document.querySelectorAll('img, picture source');
                for (var i = 0; i < imgs.length; i++) {
                    var el = imgs[i];
                    if (el.src && !el.src.startsWith('data:')) {
                        var res = (el.naturalWidth && el.naturalHeight) ? (el.naturalWidth + 'x' + el.naturalHeight) : null;
                        list.push({u: el.src, s: 'DOM', r: res});
                    }
                    var dSrc = el.getAttribute('data-src') || el.getAttribute('data-original') || el.getAttribute('data-lazy-src') || el.getAttribute('data-zoom-src');
                    if (dSrc && !dSrc.startsWith('data:')) {
                        list.push({u: dSrc, s: 'DOM', r: 'data-src'});
                    }
                    var srcset = el.getAttribute('srcset') || el.getAttribute('data-srcset');
                    if (srcset) {
                        var parts = srcset.split(',');
                        for (var p = 0; p < parts.length; p++) {
                            var bits = parts[p].trim().split(/\s+/);
                            if (bits[0]) list.push({u: bits[0], s: 'SRCSET', r: bits[1] || 'srcset'});
                        }
                    }
                }

                // 2. Videos and Video sources
                var vids = document.querySelectorAll('video');
                for (var j = 0; j < vids.length; j++) {
                    var v = vids[j];
                    if (v.src) list.push({u: v.src, s: 'DOM', p: v.poster || null, r: 'video'});
                    if (v.poster) list.push({u: v.poster, s: 'DOM', r: 'poster'});
                    var sources = v.querySelectorAll('source');
                    for (var s = 0; s < sources.length; s++) {
                        if (sources[s].src) list.push({u: sources[s].src, s: 'DOM', p: v.poster || null, r: 'video-source'});
                    }
                }

                // 3. Audio and Audio sources
                var auds = document.querySelectorAll('audio');
                for (var a = 0; a < auds.length; a++) {
                    var au = auds[a];
                    if (au.src) list.push({u: au.src, s: 'DOM', r: 'audio'});
                    var asources = au.querySelectorAll('source');
                    for (var k = 0; k < asources.length; k++) {
                        if (asources[k].src) list.push({u: asources[k].src, s: 'DOM', r: 'audio-source'});
                    }
                }

                // 4. CSS background-image
                var bgElements = document.querySelectorAll('div, section, header, figure, a, span');
                var count = Math.min(bgElements.length, 100);
                for (var b = 0; b < count; b++) {
                    var style = window.getComputedStyle(bgElements[b]);
                    var bg = style.backgroundImage;
                    if (bg && bg.indexOf('url(') !== -1) {
                        var match = bg.match(/url\(['"]?(.*?)['"]?\)/);
                        if (match && match[1] && !match[1].startsWith('data:')) {
                            list.push({u: match[1], s: 'CSS', r: 'background'});
                        }
                    }
                }

                // 5. iFrames (e.g., media players or embeds)
                var iframes = document.querySelectorAll('iframe');
                for (var f = 0; f < iframes.length; f++) {
                    if (iframes[f].src) list.push({u: iframes[f].src, s: 'IFRAME', r: 'iframe'});
                }

                return JSON.stringify(list);
            })();
        """.trimIndent()

        wv.evaluateJavascript(script) { resultJson ->
            if (resultJson.isNullOrBlank() || resultJson == "null") {
                onResults(emptyList())
                return@evaluateJavascript
            }

            try {
                // The evaluateJavascript returns a JSON string, which may be JSON-escaped
                val unescaped = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                    org.json.JSONTokener(resultJson).nextValue().toString()
                } else {
                    resultJson
                }

                val jsonArray = JSONArray(unescaped)
                val entries = mutableListOf<JsMediaEntry>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val rawUrl = obj.optString("u", "")
                    if (rawUrl.isNotBlank()) {
                        val sTag = obj.optString("s", "DOM")
                        val source = when (sTag) {
                            "CSS" -> ExtractionSource.CSS_BACKGROUND
                            "SRCSET" -> ExtractionSource.SRCSET
                            "IFRAME" -> ExtractionSource.IFRAME
                            else -> ExtractionSource.DOM_SCRAPE
                        }
                        val poster = obj.optString("p", "").ifBlank { null }
                        val res = obj.optString("r", "").ifBlank { null }
                        entries.add(JsMediaEntry(rawUrl, source, poster, res))
                    }
                }
                onResults(entries)
            } catch (_: Exception) {
                onResults(emptyList())
            }
        }
    }

    private fun executeAutoScrollSequence(
        wv: WebView,
        step: Int,
        maxSteps: Int,
        onDone: () -> Unit
    ) {
        if (isCancelled || step > maxSteps) {
            onDone()
            return
        }

        // Scroll down in viewport steps to trigger IntersectionObserver and scroll events
        val scrollScript = """
            (function() {
                window.scrollBy({ top: window.innerHeight * 0.9, behavior: 'smooth' });
                window.dispatchEvent(new Event('scroll'));
            })();
        """.trimIndent()

        wv.evaluateJavascript(scrollScript, null)

        mainHandler.postDelayed({
            if (!isCancelled) {
                executeAutoScrollSequence(wv, step + 1, maxSteps, onDone)
            } else {
                onDone()
            }
        }, 400)
    }

    fun cleanup() {
        isCancelled = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            webView?.stopLoading()
            webView?.webViewClient = WebViewClient()
            webView?.webChromeClient = WebChromeClient()
            webView?.destroy()
        } catch (_: Exception) {
        } finally {
            webView = null
        }
    }
}
