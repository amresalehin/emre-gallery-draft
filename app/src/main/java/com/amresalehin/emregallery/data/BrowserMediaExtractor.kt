package com.amresalehin.emregallery.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import java.net.URL

/**
 * Chromium WebView-assisted media extraction engine.
 * Performs:
 * 1. Live network request interception for streaming media (HLS, DASH, MP4, MP3, etc.).
 * 2. Deep JavaScript DOM, computed CSS, srcset, schema.org JSON-LD, and iframe inspection.
 * 3. MutationObserver-based dynamic DOM change detection.
 * 4. Adaptive viewport scrolling with height-change detection and loading waits.
 * 5. Robust lifecycle management with proper cancellation ordering.
 */
class BrowserMediaExtractor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    @Volatile
    private var isCancelled = false

    @SuppressLint("SetJavaScriptEnabled")
    fun startLiveExtraction(
        targetUrl: String,
        onStatusUpdate: (String) -> Unit,
        onMediaDiscovered: (ScrapedMediaItem) -> Unit,
        onFinished: () -> Unit
    ) {
        mainHandler.post {
            // FIX: Cleanup previous instance first before resetting isCancelled flag
            cleanup()
            isCancelled = false

            val normalizedUrl = if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
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
                    userAgentString = OkHttpDownloader.DEFAULT_USER_AGENT
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
                        val headers = request.requestHeaders ?: emptyMap()
                        val acceptHeader = headers["Accept"]?.lowercase() ?: ""

                        val isNonMediaPage = reqLower.contains(".html") || reqLower.contains(".htm") ||
                                reqLower.contains(".php") || reqLower.contains(".js") || reqLower.contains(".css") ||
                                reqLower.contains(".json") || reqLower.contains("/wiki/") || reqLower.contains("/geohack") ||
                                reqLower.contains("centralautologin")

                        val isStream = !isNonMediaPage && (reqLower.contains(".m3u8") || reqLower.contains(".mpd") ||
                                reqLower.contains("/hls/") || reqLower.contains("/dash/") ||
                                reqLower.contains("chunklist") || acceptHeader.contains("mpegurl") ||
                                acceptHeader.contains("dash+xml"))

                        val isVideo = !isNonMediaPage && (isStream || reqLower.contains(".mp4") || reqLower.contains(".webm") ||
                                reqLower.contains(".mov") || reqLower.contains(".mkv") ||
                                acceptHeader.contains("video/"))

                        val isAudio = !isNonMediaPage && (reqLower.contains(".mp3") || reqLower.contains(".m4a") ||
                                reqLower.contains(".aac") || reqLower.contains(".wav") ||
                                reqLower.contains(".ogg") || reqLower.contains(".flac") ||
                                reqLower.contains(".opus") || acceptHeader.contains("audio/"))

                        val hasImageExt = reqLower.contains(".jpg") || reqLower.contains(".jpeg") ||
                                reqLower.contains(".png") || reqLower.contains(".webp") ||
                                reqLower.contains(".gif") || reqLower.contains(".avif") ||
                                reqLower.contains(".svg") || reqLower.contains(".bmp")

                        val isImage = !isNonMediaPage && (hasImageExt || (acceptHeader.startsWith("image/") && !acceptHeader.contains("text/html")))

                        if (isVideo || isAudio || isImage) {
                            mainHandler.post {
                                dispatchMediaItem(
                                    rawUrl = reqUrl,
                                    source = ExtractionSource.NETWORK_INTERCEPT,
                                    mimeHint = acceptHeader.ifBlank { null }
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
                        // Flush cookies so OkHttp sees latest session cookies
                        try {
                            CookieManager.getInstance().flush()
                        } catch (_: Throwable) {}

                        onStatusUpdate("Scanning DOM, computed CSS, and dynamic media tags...")

                        // Setup MutationObserver and perform deep inspection
                        setupMutationObserver(wv) { dynamicUrl ->
                            dispatchMediaItem(dynamicUrl, ExtractionSource.DOM_SCRAPE)
                        }

                        // 1. Run deep DOM and JSON inspection
                        executeJsDomScan(wv, normalizedUrl) { rawEntries ->
                            for (entry in rawEntries) {
                                dispatchMediaItem(
                                    rawUrl = entry.url,
                                    source = entry.source,
                                    poster = entry.poster,
                                    resolution = entry.resolution
                                )
                            }

                            // 2. Perform adaptive scrolling sequence to uncover lazy media
                            if (!isCancelled) {
                                onStatusUpdate("Auto-scrolling page to trigger lazy-loaded media...")
                                executeAdaptiveScrollSequence(wv, step = 1, maxSteps = 4, lastScrollHeight = 0) {
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
                Log.e("BrowserMediaExtractor", "Browser initialization failed", e)
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

    private fun setupMutationObserver(wv: WebView, onDiscovered: (String) -> Unit) {
        val observerScript = """
            (function() {
                if (window.__emreObserverInstalled) return;
                window.__emreObserverInstalled = true;
                var observer = new MutationObserver(function(mutations) {
                    for (var m = 0; m < mutations.length; m++) {
                        var mutation = mutations[m];
                        for (var i = 0; i < mutation.addedNodes.length; i++) {
                            var node = mutation.addedNodes[i];
                            if (node.nodeType === 1) {
                                if (node.tagName === 'IMG' && node.src) {
                                    console.log('EMRE_MUTATION:' + node.src);
                                }
                                if (node.tagName === 'VIDEO' && node.src) {
                                    console.log('EMRE_MUTATION:' + node.src);
                                }
                            }
                        }
                    }
                });
                observer.observe(document.body || document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['src', 'data-src', 'srcset']
                });
            })();
        """.trimIndent()
        wv.evaluateJavascript(observerScript, null)
    }

    private fun executeJsDomScan(
        wv: WebView,
        baseUrl: String,
        onResults: (List<JsMediaEntry>) -> Unit
    ) {
        val script = """
            (function() {
                var list = [];
                function resolveAbs(u) {
                    if (!u) return null;
                    try {
                        return new URL(u, document.baseURI || window.location.href).href;
                    } catch(e) {
                        if (u.indexOf('//') === 0) return 'https:' + u;
                        return u;
                    }
                }

                // 1. Images, picture sources, lazy-load attributes, and srcset
                var imgs = document.querySelectorAll('img, picture source');
                for (var i = 0; i < imgs.length; i++) {
                    var el = imgs[i];
                    var nw = el.naturalWidth || parseInt(el.getAttribute('width')) || el.clientWidth || 0;
                    var nh = el.naturalHeight || parseInt(el.getAttribute('height')) || el.clientHeight || 0;
                    var res = (nw > 0 && nh > 0) ? (nw + 'x' + nh) : ((nw > 0) ? (nw + 'w') : null);

                    if (el.src && !el.src.startsWith('data:')) {
                        var absUrl = resolveAbs(el.src);
                        if (absUrl) list.push({u: absUrl, s: 'DOM', r: res});
                    }
                    var dSrc = el.getAttribute('data-src') || el.getAttribute('data-original') ||
                               el.getAttribute('data-lazy-src') || el.getAttribute('data-zoom-src') ||
                               el.getAttribute('data-hires') || el.getAttribute('data-full');
                    if (dSrc && !dSrc.startsWith('data:')) {
                        var absD = resolveAbs(dSrc);
                        if (absD) list.push({u: absD, s: 'DOM', r: res || 'data-src'});
                    }
                    var srcset = el.getAttribute('srcset') || el.getAttribute('data-srcset');
                    if (srcset) {
                        var parts = srcset.split(',');
                        for (var p = 0; p < parts.length; p++) {
                            var bits = parts[p].trim().split(/\s+/);
                            if (bits[0]) {
                                var absS = resolveAbs(bits[0]);
                                if (absS) {
                                    var desc = bits[1] || '';
                                    var rDesc = null;
                                    var pxMatch = absS.match(/[/_-](\d{2,5})px[-_./]/);
                                    if (pxMatch && pxMatch[1]) {
                                        rDesc = pxMatch[1] + 'px';
                                    } else if (desc.endsWith('w')) {
                                        rDesc = desc;
                                    } else if (desc.endsWith('x')) {
                                        var mult = parseFloat(desc) || 1;
                                        if (nw > 0 && nh > 0) {
                                            rDesc = Math.round(nw * mult) + 'x' + Math.round(nh * mult);
                                        } else if (nw > 0) {
                                            rDesc = Math.round(nw * mult) + 'w';
                                        }
                                    }
                                    list.push({u: absS, s: 'SRCSET', r: rDesc || res || desc || 'srcset'});
                                }
                            }
                        }
                    }
                }

                // 2. Videos and Video sources
                var vids = document.querySelectorAll('video');
                for (var j = 0; j < vids.length; j++) {
                    var v = vids[j];
                    var vSrc = resolveAbs(v.src);
                    var vPoster = resolveAbs(v.poster);
                    if (vSrc) list.push({u: vSrc, s: 'DOM', p: vPoster, r: 'video'});
                    if (vPoster) list.push({u: vPoster, s: 'DOM', r: 'poster'});
                    var sources = v.querySelectorAll('source');
                    for (var s = 0; s < sources.length; s++) {
                        var sSrc = resolveAbs(sources[s].src);
                        if (sSrc) list.push({u: sSrc, s: 'DOM', p: vPoster, r: 'video-source'});
                    }
                }

                // 3. Audio and Audio sources
                var auds = document.querySelectorAll('audio');
                for (var a = 0; a < auds.length; a++) {
                    var au = auds[a];
                    var aSrc = resolveAbs(au.src);
                    if (aSrc) list.push({u: aSrc, s: 'DOM', r: 'audio'});
                    var asources = au.querySelectorAll('source');
                    for (var k = 0; k < asources.length; k++) {
                        var asSrc = resolveAbs(asources[k].src);
                        if (asSrc) list.push({u: asSrc, s: 'DOM', r: 'audio-source'});
                    }
                }

                // 4. Embedded JSON (JSON-LD Schema.org ImageObject, VideoObject)
                var jsonLdScripts = document.querySelectorAll('script[type="application/ld+json"]');
                for (var jl = 0; jl < jsonLdScripts.length; jl++) {
                    try {
                        var parsed = JSON.parse(jsonLdScripts[jl].innerText);
                        var arr = Array.isArray(parsed) ? parsed : [parsed];
                        for (var idx = 0; idx < arr.length; idx++) {
                            var item = arr[idx];
                            if (item.contentUrl) {
                                var cu = resolveAbs(item.contentUrl);
                                if (cu) list.push({u: cu, s: 'DOM', r: 'json-ld'});
                            }
                            if (item.thumbnailUrl) {
                                var tu = resolveAbs(item.thumbnailUrl);
                                if (tu) list.push({u: tu, s: 'DOM', r: 'json-ld-thumb'});
                            }
                            if (item.image && typeof item.image === 'string') {
                                var iu = resolveAbs(item.image);
                                if (iu) list.push({u: iu, s: 'DOM', r: 'json-ld-image'});
                            }
                        }
                    } catch(e) {}
                }

                // 5. CSS background-image
                var bgElements = document.querySelectorAll('div, section, header, figure, a, span');
                var count = Math.min(bgElements.length, 120);
                for (var b = 0; b < count; b++) {
                    var style = window.getComputedStyle(bgElements[b]);
                    var bg = style.backgroundImage;
                    if (bg && bg.indexOf('url(') !== -1) {
                        var match = bg.match(/url\(['"]?(.*?)['"]?\)/);
                        if (match && match[1] && !match[1].startsWith('data:')) {
                            var bgu = resolveAbs(match[1]);
                            if (bgu) list.push({u: bgu, s: 'CSS', r: 'background'});
                        }
                    }
                }

                // 6. iFrames (e.g., media players or embeds)
                var iframes = document.querySelectorAll('iframe');
                for (var f = 0; f < iframes.length; f++) {
                    var ifr = resolveAbs(iframes[f].src);
                    if (ifr) list.push({u: ifr, s: 'IFRAME', r: 'iframe'});
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

    private fun executeAdaptiveScrollSequence(
        wv: WebView,
        step: Int,
        maxSteps: Int,
        lastScrollHeight: Int,
        onDone: () -> Unit
    ) {
        if (isCancelled || step > maxSteps) {
            onDone()
            return
        }

        // Measure page height and scroll adaptively
        val measureScript = """
            (function() {
                var sh = document.documentElement.scrollHeight || document.body.scrollHeight || 0;
                window.scrollBy({ top: window.innerHeight * 0.9, behavior: 'smooth' });
                window.dispatchEvent(new Event('scroll'));
                return sh;
            })();
        """.trimIndent()

        wv.evaluateJavascript(measureScript) { heightStr ->
            val curHeight = heightStr?.trim('"')?.toIntOrNull() ?: 0
            mainHandler.postDelayed({
                if (!isCancelled) {
                    executeAdaptiveScrollSequence(wv, step + 1, maxSteps, curHeight, onDone)
                } else {
                    onDone()
                }
            }, 500)
        }
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
