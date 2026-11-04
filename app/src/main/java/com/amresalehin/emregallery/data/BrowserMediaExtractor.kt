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

                val seenMap = mutableMapOf<String, ScrapedMediaItem>()

                fun dispatchMediaItem(
                    rawUrl: String,
                    source: ExtractionSource,
                    poster: String? = null,
                    resolution: String? = null,
                    mimeHint: String? = null
                ) {
                    if (isCancelled) return
                    val trimmed = rawUrl.trim()
                    if (trimmed.startsWith("blob:", ignoreCase = true)) return
                    if (trimmed.startsWith("data:", ignoreCase = true) && !MediaClassifier.isSubstantialDataUri(trimmed)) return
                    if (MediaClassifier.isMediaFragment(trimmed)) return
                    if (AdBlockEngine.isAdOrTracker(trimmed)) return

                    val resolved = MediaClassifier.resolveUrl(trimmed, normalizedUrl) ?: return
                    if (!MediaClassifier.isValidMedia(resolved)) return
                    if (MediaClassifier.isMediaFragment(resolved)) return

                    val resolvedInfo = MediaClassifier.resolveOriginalMedia(resolved)
                    val cleanedUrl = MediaClassifier.cleanMediaUrl(resolvedInfo.mediaUrl)
                    if (MediaClassifier.isMediaFragment(cleanedUrl)) return

                    val classification = MediaClassifier.classify(cleanedUrl, mimeHint)
                    val isVidAudStream = classification.isVideo || classification.isAudio || classification.isStream
                    val title = resolvedInfo.cleanTitle ?: MediaClassifier.deriveFileName(cleanedUrl, classification.format, seenMap.size)

                    if (ContentAwareMediaFilter.isUiChromeUrl(
                            url = cleanedUrl,
                            title = title,
                            isVideoOrAudioOrStream = isVidAudStream
                        )
                    ) {
                        return
                    }

                    val thumb = resolvedInfo.thumbnailUrl ?: poster ?: if (classification.category == MediaCategory.IMAGES) resolved else poster

                    val headers = mutableMapOf<String, String>()
                    headers["Referer"] = normalizedUrl
                    headers["User-Agent"] = OkHttpDownloader.DEFAULT_USER_AGENT
                    val cookieStr = OkHttpDownloader.sharedCookieJar.getCookieString(normalizedUrl)
                    if (cookieStr.isNotBlank()) {
                        headers["Cookie"] = cookieStr
                    }

                    val metadataMap = mutableMapOf<String, String>()
                    resolution?.let { metadataMap["resolution"] = it }
                    mimeHint?.let { metadataMap["mime_hint"] = it }
                    metadataMap["source_url"] = normalizedUrl

                    val key = MediaClassifier.canonicalKey(cleanedUrl)
                    val candidateItem = ScrapedMediaItem(
                        id = "browser_${seenMap.size}_${System.currentTimeMillis()}",
                        url = cleanedUrl,
                        title = title,
                        isVideo = classification.isVideo,
                        isAudio = classification.isAudio,
                        isStream = classification.isStream,
                        category = classification.category,
                        format = classification.format,
                        thumbnailUrl = thumb,
                        source = source.label,
                        mimeType = classification.mimeType,
                        resolutionHint = resolution,
                        provenance = MediaProvenance(
                            extractorEngine = "Browser Chromium (${source.label})",
                            pageUrl = normalizedUrl,
                            domSelector = if (source == ExtractionSource.NETWORK_INTERCEPT) "network_request" else null
                        ),
                        metadata = metadataMap,
                        downloadHeaders = headers
                    )

                    synchronized(seenMap) {
                        val existing = seenMap[key]
                        if (existing != null) {
                            if (MediaClassifier.compareMediaQuality(candidateItem, existing) <= 0) {
                                return
                            }
                        }
                        seenMap[key] = candidateItem
                    }

                    onMediaDiscovered(candidateItem)
                }

                wv.addJavascriptInterface(
                    EmreMediaBridge { rawUrl ->
                        mainHandler.post {
                            dispatchMediaItem(rawUrl, ExtractionSource.DOM_SCRAPE)
                        }
                    },
                    "EmreBridge"
                )

                wv.webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (isCancelled) return
                        if (newProgress < 100) {
                            onStatusUpdate("Loading page: $newProgress% (monitoring network requests)...")
                        }
                    }
                }

                wv.webViewClient = object : WebViewClient() {

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (isCancelled || request == null) return null

                        val reqUrl = request.url.toString()

                        // 1. Block ad networks and tracking requests with instant zero payload
                        if (AdBlockEngine.isAdOrTracker(reqUrl)) {
                            return AdBlockEngine.createEmptyResponse()
                        }

                        // 2. Reject streaming fragments and slices
                        if (MediaClassifier.isMediaFragment(reqUrl)) {
                            return null
                        }

                        val reqLower = reqUrl.lowercase()
                        val headers = request.requestHeaders ?: emptyMap()
                        val acceptHeader = headers["Accept"]?.lowercase() ?: ""

                        val isNonMediaPage = reqLower.contains(".html") || reqLower.contains(".htm") ||
                                reqLower.contains(".php") || reqLower.contains(".js") || reqLower.contains(".css") ||
                                reqLower.contains(".json") || reqLower.contains("/wiki/") || reqLower.contains("/geohack") ||
                                reqLower.contains("centralautologin")

                        if (isNonMediaPage) return null

                        val isStream = reqLower.contains(".m3u8") || reqLower.contains(".mpd") ||
                                reqLower.contains("chunklist") || acceptHeader.contains("mpegurl") ||
                                acceptHeader.contains("dash+xml")

                        val isVideo = isStream || reqLower.contains(".mp4") || reqLower.contains(".webm") ||
                                reqLower.contains(".mov") || reqLower.contains(".mkv") ||
                                acceptHeader.contains("video/")

                        val isAudio = reqLower.contains(".mp3") || reqLower.contains(".m4a") ||
                                reqLower.contains(".aac") || reqLower.contains(".wav") ||
                                reqLower.contains(".ogg") || reqLower.contains(".flac") ||
                                reqLower.contains(".opus") || acceptHeader.contains("audio/")

                        val hasImageExt = reqLower.contains(".jpg") || reqLower.contains(".jpeg") ||
                                reqLower.contains(".png") || reqLower.contains(".webp") ||
                                reqLower.contains(".gif") || reqLower.contains(".avif") ||
                                reqLower.contains(".svg") || reqLower.contains(".bmp")

                        val isImage = hasImageExt || (acceptHeader.startsWith("image/") && !acceptHeader.contains("text/html"))

                        if (isVideo || isAudio || isImage) {
                            val cleanReqUrl = if (isVideo) MediaClassifier.cleanMediaUrl(reqUrl) else reqUrl
                            if (!MediaClassifier.isMediaFragment(cleanReqUrl) && !AdBlockEngine.isAdOrTracker(cleanReqUrl)) {
                                mainHandler.post {
                                    dispatchMediaItem(
                                        rawUrl = cleanReqUrl,
                                        source = ExtractionSource.NETWORK_INTERCEPT,
                                        mimeHint = acceptHeader.ifBlank { null }
                                    )
                                }
                            }
                        }

                        // Let the WebView continue loading normally
                        return null
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val reqUrl = request?.url?.toString() ?: return false
                        val lower = reqUrl.lowercase()
                        if (!lower.startsWith("http://") && !lower.startsWith("https://") &&
                            !lower.startsWith("about:") && !lower.startsWith("data:")
                        ) {
                            return true // Suppress external app launches and ERR_UNKNOWN_URL_SCHEME in background extractor
                        }
                        return false
                    }

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val reqUrl = url ?: return false
                        val lower = reqUrl.lowercase()
                        if (!lower.startsWith("http://") && !lower.startsWith("https://") &&
                            !lower.startsWith("about:") && !lower.startsWith("data:")
                        ) {
                            return true
                        }
                        return false
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
                                executeAdaptiveScrollSequence(wv) {
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

    class EmreMediaBridge(private val onMediaFound: (String) -> Unit) {
        @android.webkit.JavascriptInterface
        fun reportMedia(url: String?) {
            if (!url.isNullOrBlank()) {
                onMediaFound(url)
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

                function report(u) {
                    if (!u || u.indexOf('data:') === 0) return;
                    try {
                        var abs = new URL(u, document.baseURI || window.location.href).href;
                        if (window.EmreBridge && window.EmreBridge.reportMedia) {
                            window.EmreBridge.reportMedia(abs);
                        }
                    } catch(e) {}
                }

                function checkNode(node) {
                    if (!node || node.nodeType !== 1) return;
                    var tag = node.tagName ? node.tagName.toUpperCase() : '';
                    if (tag === 'IMG') {
                        report(node.src);
                        report(node.getAttribute('data-src'));
                        report(node.getAttribute('data-original'));
                        report(node.getAttribute('data-lazy-src'));
                        var srcset = node.getAttribute('srcset') || node.getAttribute('data-srcset');
                        if (srcset) {
                            var parts = srcset.split(',');
                            for (var i = 0; i < parts.length; i++) {
                                var bit = parts[i].trim().split(/\s+/)[0];
                                if (bit) report(bit);
                            }
                        }
                    } else if (tag === 'VIDEO') {
                        var chosenV = null;
                        if (node.src && node.src.indexOf('blob:') !== 0 && node.src.indexOf('data:') !== 0) {
                            chosenV = node.src;
                        } else if (node.querySelectorAll) {
                            var vSources = node.querySelectorAll('source');
                            for (var vs = 0; vs < vSources.length; vs++) {
                                var vsSrc = vSources[vs].src;
                                if (vsSrc && vsSrc.indexOf('blob:') !== 0 && vsSrc.indexOf('data:') !== 0) {
                                    chosenV = vsSrc;
                                    break;
                                }
                            }
                        }
                        if (chosenV) report(chosenV);
                        report(node.getAttribute('poster'));
                    } else if (tag === 'AUDIO') {
                        var chosenA = null;
                        if (node.src && node.src.indexOf('blob:') !== 0 && node.src.indexOf('data:') !== 0) {
                            chosenA = node.src;
                        } else if (node.querySelectorAll) {
                            var aSources = node.querySelectorAll('source');
                            for (var as = 0; as < aSources.length; as++) {
                                var asSrc = aSources[as].src;
                                if (asSrc && asSrc.indexOf('blob:') !== 0 && asSrc.indexOf('data:') !== 0) {
                                    chosenA = asSrc;
                                    break;
                                }
                            }
                        }
                        if (chosenA) report(chosenA);
                    } else if (tag === 'SOURCE') {
                        var parentTag = node.parentNode ? (node.parentNode.tagName || '').toUpperCase() : '';
                        if (parentTag !== 'VIDEO' && parentTag !== 'AUDIO') {
                            report(node.src);
                            report(node.getAttribute('srcset'));
                        }
                    }

                    if (node.querySelectorAll) {
                        var children = node.querySelectorAll('img, video, audio, source');
                        for (var c = 0; c < children.length; c++) {
                            checkNode(children[c]);
                        }
                    }
                }

                var observer = new MutationObserver(function(mutations) {
                    for (var m = 0; m < mutations.length; m++) {
                        var mut = mutations[m];
                        if (mut.type === 'childList') {
                            for (var i = 0; i < mut.addedNodes.length; i++) {
                                checkNode(mut.addedNodes[i]);
                            }
                        } else if (mut.type === 'attributes') {
                            checkNode(mut.target);
                        }
                    }
                });

                observer.observe(document.body || document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['src', 'data-src', 'data-original', 'data-lazy-src', 'srcset', 'data-srcset', 'poster']
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

                function isChromeElement(el) {
                    if (!el) return false;
                    var chromeParent = el.closest('header, nav, footer, aside, menu, form, [role="banner"], [role="navigation"], [role="contentinfo"], .header, .site-header, .nav, .navbar, .site-nav, .menu, #header, #nav, #navbar, #footer, #mw-navigation, #p-logo, .footer, .site-footer, .breadcrumb, .breadcrumbs, .pagination, .toolbar, .ad, .ads, .advertisement, .cookie, .cookie-banner, .consent');
                    if (chromeParent) {
                        var contentParent = el.closest('article, main, [role="main"], #content, #main-content, .mw-parser-output, figure, .gallery, .entry-content, .post-content');
                        if (!contentParent || chromeParent.contains(contentParent) === false) {
                            return true;
                        }
                    }
                    var idAndClass = ((el.id || '') + ' ' + (el.className || '') + ' ' + (el.getAttribute('alt') || '')).toLowerCase();
                    if (/logo|favicon|avatar_default|badge|donate|donation|fundraising|paypal|magnifying-glass|nav-icon|menu-icon|close-icon|arrow-|chevron|bullet|spinner|1x1/.test(idAndClass)) {
                        return true;
                    }
                    var nw = el.naturalWidth || parseInt(el.getAttribute('width')) || el.clientWidth || 0;
                    var nh = el.naturalHeight || parseInt(el.getAttribute('height')) || el.clientHeight || 0;
                    if (nw > 0 && nh > 0) {
                        if (nw <= 48 && nh <= 48) return true;
                        if (nw <= 32 || nh <= 32) return true;
                        if ((nw / nh > 12) || (nh / nw > 12)) return true;
                    }
                    return false;
                }

                // 1. Images, picture sources, lazy-load attributes, and srcset (content-aware)
                var imgs = document.querySelectorAll('img, picture source');
                for (var i = 0; i < imgs.length; i++) {
                    var el = imgs[i];
                    if (isChromeElement(el)) continue;

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

                // 2. Videos and Video sources (deduplicated per video element, ignoring blobs)
                var vids = document.querySelectorAll('video');
                for (var j = 0; j < vids.length; j++) {
                    var v = vids[j];
                    var vPoster = resolveAbs(v.poster);
                    var chosenUrl = null;
                    if (v.src && !v.src.startsWith('blob:') && !v.src.startsWith('data:')) {
                        chosenUrl = resolveAbs(v.src);
                    }
                    if (!chosenUrl) {
                        var sources = v.querySelectorAll('source');
                        for (var s = 0; s < sources.length; s++) {
                            var sRaw = sources[s].src;
                            if (sRaw && !sRaw.startsWith('blob:') && !sRaw.startsWith('data:')) {
                                chosenUrl = resolveAbs(sRaw);
                                break;
                            }
                        }
                    }
                    if (chosenUrl) {
                        list.push({u: chosenUrl, s: 'DOM', p: vPoster, r: 'video'});
                    }
                    if (vPoster && !chosenUrl) {
                        list.push({u: vPoster, s: 'DOM', r: 'poster'});
                    }
                }

                // 3. Audio and Audio sources (deduplicated per audio element, ignoring blobs)
                var auds = document.querySelectorAll('audio');
                for (var a = 0; a < auds.length; a++) {
                    var au = auds[a];
                    var chosenAudio = null;
                    if (au.src && !au.src.startsWith('blob:') && !au.src.startsWith('data:')) {
                        chosenAudio = resolveAbs(au.src);
                    }
                    if (!chosenAudio) {
                        var asources = au.querySelectorAll('source');
                        for (var k = 0; k < asources.length; k++) {
                            var asRaw = asources[k].src;
                            if (asRaw && !asRaw.startsWith('blob:') && !asRaw.startsWith('data:')) {
                                chosenAudio = resolveAbs(asRaw);
                                break;
                            }
                        }
                    }
                    if (chosenAudio) {
                        list.push({u: chosenAudio, s: 'DOM', r: 'audio'});
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

                // 5. CSS background-image (content containers only, size > 120x120)
                var bgElements = document.querySelectorAll('main, article, figure, [role="main"], #content, .gallery, .photo, .hero');
                var count = Math.min(bgElements.length, 60);
                for (var b = 0; b < count; b++) {
                    var bEl = bgElements[b];
                    if (bEl.clientWidth >= 120 && bEl.clientHeight >= 120 && !isChromeElement(bEl)) {
                        var style = window.getComputedStyle(bEl);
                        var bg = style.backgroundImage;
                        if (bg && bg.indexOf('url(') !== -1) {
                            var match = bg.match(/url\(['"]?(.*?)['"]?\)/);
                            if (match && match[1] && !match[1].startsWith('data:')) {
                                var bgu = resolveAbs(match[1]);
                                if (bgu) list.push({u: bgu, s: 'CSS', r: 'background'});
                            }
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
        step: Int = 1,
        maxSteps: Int = 12,
        stagnantCount: Int = 0,
        lastScrollY: Int = -1,
        onDone: () -> Unit
    ) {
        if (isCancelled || step > maxSteps || stagnantCount >= 2) {
            onDone()
            return
        }

        val scrollScript = """
            (function() {
                var doc = document.documentElement || document.body;
                var scrollY = Math.round(window.pageYOffset || doc.scrollTop || 0);
                var innerHeight = window.innerHeight || 0;
                var scrollHeight = doc.scrollHeight || 0;
                var remaining = scrollHeight - (scrollY + innerHeight);

                var scrollDelta = Math.max(innerHeight * 0.85, 300);
                window.scrollBy({ top: scrollDelta, behavior: 'smooth' });
                window.dispatchEvent(new Event('scroll'));

                return JSON.stringify({
                    scrollY: scrollY,
                    scrollHeight: scrollHeight,
                    remaining: remaining
                });
            })();
        """.trimIndent()

        wv.evaluateJavascript(scrollScript) { resultJson ->
            if (isCancelled) {
                onDone()
                return@evaluateJavascript
            }

            var currentScrollY = lastScrollY
            var remaining = 1000
            runCatching {
                val unescaped = if (resultJson != null && resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                    org.json.JSONTokener(resultJson).nextValue().toString()
                } else resultJson ?: "{}"
                val obj = org.json.JSONObject(unescaped)
                currentScrollY = obj.optInt("scrollY", lastScrollY)
                remaining = obj.optInt("remaining", 1000)
            }

            val isStagnant = lastScrollY != -1 && Math.abs(currentScrollY - lastScrollY) < 10
            val newStagnantCount = if (isStagnant) stagnantCount + 1 else 0

            if (remaining <= 5 || newStagnantCount >= 2) {
                onDone()
            } else {
                mainHandler.postDelayed({
                    if (!isCancelled) {
                        executeAdaptiveScrollSequence(
                            wv = wv,
                            step = step + 1,
                            maxSteps = maxSteps,
                            stagnantCount = newStagnantCount,
                            lastScrollY = currentScrollY,
                            onDone = onDone
                        )
                    } else {
                        onDone()
                    }
                }, 500)
            }
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
