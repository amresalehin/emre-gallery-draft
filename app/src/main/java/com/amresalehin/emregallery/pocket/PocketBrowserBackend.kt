package com.amresalehin.emregallery.pocket

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Metadata resolved from the rendered page DOM, rather than from fragile HTML regexes.
 */
internal data class PocketBrowserMetadata(
    val title: String? = null,
    val description: String? = null,
    val imageUrls: List<String> = emptyList(),
    val canonicalUrl: String? = null,
    val siteName: String? = null,
    val faviconUrl: String? = null,
    val previewImagePath: String? = null
)

/**
 * A short-lived, non-interactive in-app browser used by Pocket when a URL is saved.
 *
 * JavaScript is enabled only to let the page render and expose its metadata. No JavaScript
 * bridge is registered, file/content access is disabled, and non-HTTP(S) navigations are blocked.
 * The browser is destroyed as soon as metadata and the viewport preview have been captured.
 */
internal class PocketBrowserBackend(context: Context) {

    private val appContext = context.applicationContext
    private val activityReference = WeakReference(context.findActivity())
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun extract(url: String, bookmarkId: String): PocketBrowserMetadata? {
        if (!isHttpUrl(url)) return null

        val loadedPage = withTimeoutOrNull(PAGE_TIMEOUT_MILLIS) {
            withContext(Dispatchers.Main.immediate) {
                loadPage(url)
            }
        } ?: return null

        val previewPath = loadedPage.previewBitmap?.let { bitmap ->
            try {
                withContext(Dispatchers.IO) {
                    persistPreview(bitmap, bookmarkId)
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }

        return loadedPage.metadata.copy(previewImagePath = previewPath)
    }

    private suspend fun loadPage(url: String): LoadedPage =
        suspendCancellableCoroutine { continuation ->
            val activity = activityReference.get()?.takeUnless { it.isFinishing || it.isDestroyed }
            val decorView = activity?.window?.decorView as? ViewGroup
            val browserHost = runCatching {
                if (activity == null || decorView == null || !decorView.isAttachedToWindow) {
                    null
                } else {
                    FrameLayout(activity).apply {
                        // Keep the live browser behind the app content while still attaching it
                        // to a real window surface so its renderer can produce a page snapshot.
                        alpha = 0.01f
                        isClickable = false
                        isFocusable = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    }.also { host ->
                        decorView.addView(
                            host,
                            0,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                Gravity.TOP or Gravity.START
                            )
                        )
                    }
                }
            }.getOrNull()

            val webView = runCatching { WebView(browserHost?.context ?: appContext) }.getOrNull()
            if (webView == null) {
                browserHost?.let { host -> (host.parent as? ViewGroup)?.removeView(host) }
                continuation.resume(LoadedPage(PocketBrowserMetadata(), null))
                return@suspendCancellableCoroutine
            }
            browserHost?.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            val settled = AtomicBoolean(false)

            fun dispose() {
                runCatching { webView.stopLoading() }
                runCatching { webView.webChromeClient = null }
                runCatching { webView.webViewClient = WebViewClient() }
                runCatching { browserHost?.removeView(webView) }
                runCatching { (browserHost?.parent as? ViewGroup)?.removeView(browserHost) }
                runCatching { webView.destroy() }
            }

            fun complete(result: LoadedPage) {
                if (!settled.compareAndSet(false, true)) {
                    result.previewBitmap?.recycle()
                    return
                }
                dispose()
                if (continuation.isActive) {
                    continuation.resume(result)
                } else {
                    result.previewBitmap?.recycle()
                }
            }

            continuation.invokeOnCancellation {
                mainHandler.post {
                    if (settled.compareAndSet(false, true)) dispose()
                }
            }

            try {
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    loadsImagesAutomatically = true
                    blockNetworkImage = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    textZoom = 100
                    mediaPlaybackRequiresUserGesture = true
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }
                webView.setBackgroundColor(Color.WHITE)
                webView.isFocusable = false
                webView.isFocusableInTouchMode = false
                webView.webChromeClient = WebChromeClient()

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean = !isHttpUrl(request.url.toString())

                    override fun onPageFinished(view: WebView, finishedUrl: String?) {
                        // A short settle window catches metadata inserted just after DOMContentLoaded.
                        mainHandler.postDelayed({
                            if (settled.get()) return@postDelayed

                            val resolvedPageUrl = view.url
                                ?.takeIf(::isHttpUrl)
                                ?: finishedUrl?.takeIf(::isHttpUrl)
                                ?: url

                            view.evaluateJavascript(METADATA_SCRIPT) { rawResult ->
                                if (settled.get()) return@evaluateJavascript

                                val metadata = parseJavascriptResult(rawResult, resolvedPageUrl)
                                val preview = renderViewportPreview(view)
                                complete(
                                    LoadedPage(
                                        metadata = metadata.copy(
                                            canonicalUrl = metadata.canonicalUrl ?: resolvedPageUrl
                                        ),
                                        previewBitmap = preview
                                    )
                                )
                            }
                        }, DOM_SETTLE_DELAY_MILLIS)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        if (request.isForMainFrame) {
                            complete(
                                LoadedPage(
                                    metadata = PocketBrowserMetadata(
                                        canonicalUrl = view.url?.takeIf(::isHttpUrl)
                                    ),
                                    previewBitmap = null
                                )
                            )
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView,
                        handler: SslErrorHandler,
                        error: android.net.http.SslError
                    ) {
                        handler.cancel()
                        complete(LoadedPage(PocketBrowserMetadata(), null))
                    }

                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: android.webkit.RenderProcessGoneDetail
                    ): Boolean {
                        complete(LoadedPage(PocketBrowserMetadata(), null))
                        return true
                    }
                }

                webView.loadUrl(url)
            } catch (_: Throwable) {
                complete(LoadedPage(PocketBrowserMetadata(), null))
            }
        }

    private fun parseJavascriptResult(rawResult: String?, pageUrl: String): PocketBrowserMetadata {
        if (rawResult.isNullOrBlank() || rawResult == "null") {
            return PocketBrowserMetadata(canonicalUrl = pageUrl)
        }

        return runCatching {
            // evaluateJavascript returns a JSON-escaped string; unwrap it before parsing the object.
            val decoded = JSONArray("[$rawResult]").getString(0)
            val json = JSONObject(decoded)
            val images = mutableListOf<String>()
            val array = json.optJSONArray("images")
            if (array != null) {
                for (index in 0 until array.length()) {
                    val candidate = array.optString(index).trim()
                    if (isHttpUrl(candidate) && candidate !in images) images += candidate
                }
            }

            PocketBrowserMetadata(
                title = json.optionalString("title"),
                description = json.optionalString("description"),
                imageUrls = images.take(MAX_IMAGE_URLS),
                canonicalUrl = json.optionalString("canonicalUrl")?.takeIf(::isHttpUrl) ?: pageUrl,
                siteName = json.optionalString("siteName"),
                faviconUrl = json.optionalString("faviconUrl")?.takeIf(::isHttpUrl)
            )
        }.getOrElse {
            PocketBrowserMetadata(canonicalUrl = pageUrl)
        }
    }

    private fun renderViewportPreview(webView: WebView): Bitmap? {
        return runCatching {
            if (webView.width <= 0 || webView.height <= 0) {
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(PREVIEW_WIDTH_PX, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(PREVIEW_HEIGHT_PX, View.MeasureSpec.EXACTLY)
                )
                webView.layout(0, 0, PREVIEW_WIDTH_PX, PREVIEW_HEIGHT_PX)
            }
            webView.scrollTo(0, 0)

            val bitmap = Bitmap.createBitmap(
                PREVIEW_WIDTH_PX,
                PREVIEW_HEIGHT_PX,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            // Render at the device's natural viewport width and downsample to a compact preview.
            val scale = PREVIEW_WIDTH_PX.toFloat() / webView.width.coerceAtLeast(1)
            canvas.scale(scale, scale)
            webView.draw(canvas)

            if (!hasVisibleContent(bitmap)) {
                bitmap.recycle()
                null
            } else {
                bitmap
            }
        }.getOrNull()
    }

    private fun hasVisibleContent(bitmap: Bitmap): Boolean {
        val sampledColors = HashSet<Int>()
        val xStep = (bitmap.width / 12).coerceAtLeast(1)
        val yStep = (bitmap.height / 16).coerceAtLeast(1)
        for (y in 0 until bitmap.height step yStep) {
            for (x in 0 until bitmap.width step xStep) {
                sampledColors += bitmap.getPixel(x, y)
                if (sampledColors.size >= 8) return true
            }
        }
        return false
    }

    private fun persistPreview(bitmap: Bitmap, bookmarkId: String): String? {
        return try {
            val safeId = bookmarkId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                .ifBlank { "bookmark" }
            val directory = File(appContext.filesDir, PREVIEW_DIRECTORY).apply { mkdirs() }
            val destination = File(directory, "$safeId.jpg")
            val temporary = File(directory, "$safeId.jpg.tmp")

            FileOutputStream(temporary).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, PREVIEW_QUALITY, output)) {
                    "Could not encode bookmark preview"
                }
                output.flush()
                output.fd.sync()
            }

            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            destination.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private data class LoadedPage(
        val metadata: PocketBrowserMetadata,
        val previewBitmap: Bitmap?
    )

    private fun isHttpUrl(value: String): Boolean {
        val uri = Uri.parse(value)
        return (uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank()
    }

    private fun JSONObject.optionalString(key: String): String? {
        val value = optString(key).trim()
        return value.takeIf { it.isNotBlank() && it != "null" }
    }

    private companion object {
        const val PAGE_TIMEOUT_MILLIS = 20_000L
        const val DOM_SETTLE_DELAY_MILLIS = 800L
        const val PREVIEW_WIDTH_PX = 480
        const val PREVIEW_HEIGHT_PX = 720
        const val PREVIEW_QUALITY = 84
        const val MAX_IMAGE_URLS = 16
        const val PREVIEW_DIRECTORY = "pocket_previews"

        val METADATA_SCRIPT = """
            (function () {
              const cleanText = (value) => (value || "").replace(/\s+/g, " ").trim();
              const meta = (...selectors) => {
                for (const selector of selectors) {
                  const element = document.querySelector(selector);
                  const value = element && element.getAttribute("content");
                  if (value && cleanText(value)) return cleanText(value);
                }
                return "";
              };
              const toHttpUrl = (candidate) => {
                try {
                  const url = new URL(candidate, document.baseURI);
                  if (url.protocol !== "http:" && url.protocol !== "https:") return "";
                  url.hash = "";
                  return url.href;
                } catch (_) {
                  return "";
                }
              };

              const images = [];
              const addImage = (candidate, element) => {
                const url = toHttpUrl(candidate);
                if (!url || images.includes(url)) return;
                if (element && element.naturalWidth > 0 && element.naturalHeight > 0 &&
                    (element.naturalWidth < 96 || element.naturalHeight < 72)) return;
                images.push(url);
              };

              addImage(meta('meta[property="og:image"]', 'meta[property="og:image:url"]'), null);
              addImage(meta('meta[name="twitter:image"]', 'meta[property="twitter:image"]'), null);
              addImage(meta('meta[name="twitter:image:src"]'), null);
              addImage(meta('link[rel="image_src"]') && document.querySelector('link[rel="image_src"]').href, null);

              const visited = new WeakSet();
              const visitStructuredData = (value) => {
                if (!value || typeof value !== "object" || visited.has(value)) return;
                visited.add(value);
                if (Array.isArray(value)) {
                  value.forEach(visitStructuredData);
                  return;
                }
                if (value.image) {
                  const candidates = Array.isArray(value.image) ? value.image : [value.image];
                  candidates.forEach((image) => {
                    if (typeof image === "string") addImage(image, null);
                    else if (image && typeof image === "object") addImage(image.url || image.contentUrl, null);
                  });
                }
                Object.values(value).forEach(visitStructuredData);
              };
              document.querySelectorAll('script[type="application/ld+json"]').forEach((script) => {
                try { visitStructuredData(JSON.parse(script.textContent || "")); } catch (_) {}
              });

              const imageElements = document.querySelectorAll(
                'article img, main img, [role="main"] img, img'
              );
              for (const image of Array.from(imageElements).slice(0, 80)) {
                addImage(
                  image.currentSrc || image.src || image.getAttribute("data-src") ||
                  image.getAttribute("data-lazy-src") || image.getAttribute("data-original"),
                  image
                );
                if (images.length >= 16) break;
              }

              const faviconElement = document.querySelector(
                'link[rel~="icon"], link[rel="apple-touch-icon"], link[rel="shortcut icon"]'
              );
              const faviconUrl = faviconElement
                ? toHttpUrl(faviconElement.getAttribute("href") || faviconElement.href)
                : toHttpUrl("/favicon.ico");

              const title =
                meta('meta[property="og:title"]', 'meta[name="twitter:title"]') ||
                cleanText(document.querySelector("h1") && document.querySelector("h1").innerText) ||
                cleanText(document.title);
              const description =
                meta('meta[property="og:description"]', 'meta[name="description"]',
                     'meta[name="twitter:description"]') ||
                cleanText(document.querySelector("article p, main p, p") &&
                          (document.querySelector("article p, main p, p").innerText || "").slice(0, 500));
              const canonicalElement = document.querySelector('link[rel="canonical"]');
              const canonicalUrl = canonicalElement
                ? toHttpUrl(canonicalElement.getAttribute("href") || canonicalElement.href)
                : location.href;

              return JSON.stringify({
                title: title,
                description: description,
                images: images.slice(0, 16),
                canonicalUrl: canonicalUrl,
                siteName: meta('meta[property="og:site_name"]'),
                faviconUrl: faviconUrl
              });
            })();
        """.trimIndent()
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) break
        current = next
    }
    return current as? Activity
}
