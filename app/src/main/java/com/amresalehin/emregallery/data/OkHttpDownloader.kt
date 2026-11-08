package com.amresalehin.emregallery.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Shared session HTTP client and high-performance streaming downloader powered by OkHttp.
 * Provides:
 * 1. Unified, persistent cookie jar synchronized bidirectionally with Android WebView CookieManager.
 * 2. Shared connection pooling (HTTP/2, keep-alive) and automatic redirect handling.
 * 3. Resumable downloads via HTTP Range (RFC 7233) with byte-level progress reporting.
 * 4. Atomic file creation, MIME validation, and corrupt partial download cleanup.
 */
object OkHttpDownloader {

    internal const val TAG = "OkHttpDownloader"
    const val DEFAULT_USER_AGENT = BotDetectionMitigator.STEALTH_USER_AGENT

    /**
     * Shared bidirectional CookieJar bridging Android WebView CookieManager and in-memory cache.
     */
    class SharedSessionCookieJar : CookieJar {
        private val cookieLock = Any()
        private val inMemoryCookies = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) return
            val host = url.host
            val urlString = url.toString()

            synchronized(cookieLock) {
                val currentList = inMemoryCookies.getOrPut(host) { mutableListOf() }
                for (newCookie in cookies) {
                    currentList.removeAll { it.name == newCookie.name }
                    currentList.add(newCookie)
                }

                // Sync into Android WebView CookieManager if available
                try {
                    val cookieManager = CookieManager.getInstance()
                    for (cookie in cookies) {
                        cookieManager.setCookie(urlString, cookie.toString())
                    }
                    cookieManager.flush()
                } catch (_: Throwable) {
                    // Ignore in non-Android or JVM unit test environments
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            val urlString = url.toString()
            val now = System.currentTimeMillis()

            synchronized(cookieLock) {
                val result = mutableListOf<Cookie>()

                // 1. Load from in-memory cache
                inMemoryCookies[host]?.let { list ->
                    list.removeAll { it.expiresAt <= now }
                    result.addAll(list)
                }

                // 2. Load from Android WebView CookieManager if available
                try {
                    val cookieManager = CookieManager.getInstance()
                    val rawCookieStr = cookieManager.getCookie(urlString)
                    if (!rawCookieStr.isNullOrBlank()) {
                        val parsed = rawCookieStr.split(";").mapNotNull { part ->
                            val trimmed = part.trim()
                            if (trimmed.isNotEmpty()) {
                                Cookie.parse(url, trimmed)
                            } else null
                        }
                        for (c in parsed) {
                            if (result.none { it.name == c.name }) {
                                result.add(c)
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // Ignore in unit test environments
                }

                return result
            }
        }

        fun getCookieString(url: String): String {
            val httpUrl = url.toHttpUrlOrNull() ?: return ""
            return loadForRequest(httpUrl).joinToString("; ") { "${it.name}=${it.value}" }
        }

        fun setManualCookie(url: String, cookieName: String, cookieValue: String) {
            val httpUrl = url.toHttpUrlOrNull() ?: return
            val cookie = Cookie.Builder()
                .domain(httpUrl.host)
                .path("/")
                .name(cookieName)
                .value(cookieValue)
                .build()
            saveFromResponse(httpUrl, listOf(cookie))
        }

        fun clearSession() {
            synchronized(cookieLock) {
                inMemoryCookies.clear()
                try {
                    val cm = CookieManager.getInstance()
                    cm.removeAllCookies(null)
                    cm.flush()
                } catch (_: Throwable) {}
            }
        }
    }

    val sharedCookieJar = SharedSessionCookieJar()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(sharedCookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()

                if (original.header("User-Agent").isNullOrBlank()) {
                    requestBuilder.header("User-Agent", DEFAULT_USER_AGENT)
                }
                if (original.header("Accept").isNullOrBlank()) {
                    requestBuilder.header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/*;q=0.8,video/*;q=0.8,audio/*;q=0.8,*/*;q=0.7"
                    )
                }
                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    data class HttpResponse(
        val isSuccessful: Boolean,
        val statusCode: Int,
        val contentType: String,
        val finalUrl: String,
        val bodyString: String? = null,
        val errorMessage: String? = null
    )

    data class DownloadResult(
        val file: File?,
        val isSuccessful: Boolean,
        val statusCode: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val mimeType: String? = null,
        val isResumed: Boolean = false,
        val errorMessage: String? = null,
        val retryAfterSeconds: Long? = null
    )

    /**
     * Data class capturing the result of probing a URL for direct media file headers.
     */
    data class DirectMediaProbeResult(
        val isConfirmedDirectMedia: Boolean,
        val finalUrl: String,
        val contentType: String?,
        val contentDisposition: String?,
        val contentLength: Long?,
        val statusCode: Int
    )

    /**
     * Probes an arbitrary URL using OkHttp (HEAD request with fast Range GET fallback)
     * to verify if the URL points directly to an image, video, audio or binary stream.
     */

    suspend fun probeDirectMedia(
        url: String,
        referer: String? = null,
        userAgent: String = DEFAULT_USER_AGENT,
        extraHeaders: Map<String, String>? = null
    ): DirectMediaProbeResult = probeDirectMediaImpl(url, referer, userAgent, extraHeaders)

    fun parseRetryAfterHeader(header: String?): Long? = parseRetryAfterHeaderImpl(header)

    suspend fun getHtml(
        url: String,
        referer: String? = null,
        userAgent: String = DEFAULT_USER_AGENT,
        extraHeaders: Map<String, String>? = null
    ): HttpResponse = getHtmlImpl(url, referer, userAgent, extraHeaders)

    fun generateDeterministicId(mediaUrl: String, fileName: String): String =
        generateDeterministicIdImpl(mediaUrl, fileName)

    fun getTargetStorageDir(context: Context): File = getTargetStorageDirImpl(context)

    suspend fun downloadMediaFileDetailed(
        context: Context,
        mediaUrl: String,
        fileName: String,
        referer: String? = null,
        downloadId: String? = null,
        headers: Map<String, String>? = null,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): DownloadResult = downloadMediaFileDetailedImpl(context, mediaUrl, fileName, referer, downloadId, headers, onProgress)

    suspend fun downloadMediaFile(
        context: Context,
        mediaUrl: String,
        fileName: String,
        referer: String? = null,
        downloadId: String? = null,
        headers: Map<String, String>? = null,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): File? = downloadMediaFileImpl(context, mediaUrl, fileName, referer, downloadId, headers, onProgress)
}
