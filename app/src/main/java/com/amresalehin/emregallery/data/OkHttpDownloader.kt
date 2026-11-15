package com.amresalehin.emregallery.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Shared session HTTP client and high-performance streaming downloader powered by OkHttp.
 * Delegates cookie persistence, storage pathing, data URI writing, resumable transfers,
 * and file validation to focused single-responsibility components.
 */
object OkHttpDownloader {

    private const val TAG = "OkHttpDownloader"
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * Shared bidirectional CookieJar bridging Android WebView CookieManager and in-memory cache.
     */
    class SharedSessionCookieJar(
        private val store: SessionCookieStore = SessionCookieStore()
    ) : CookieJar by store {
        fun getCookieString(url: String): String = store.getCookieString(url)

        fun setManualCookie(url: String, cookieName: String, cookieValue: String) =
            store.setManualCookie(url, cookieName, cookieValue)

        fun clearSession() = store.clearSession()
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
     * Parses the HTTP 'Retry-After' header (RFC 7231/9110) as seconds or HTTP-date.
     */
    fun parseRetryAfterHeader(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val trimmed = header.trim()
        val seconds = trimmed.toLongOrNull()
        if (seconds != null) {
            return seconds.coerceIn(1L, 300L)
        }
        return try {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            val date = format.parse(trimmed)
            if (date != null) {
                val diffSec = (date.time - System.currentTimeMillis()) / 1000L
                if (diffSec > 0) diffSec.coerceIn(1L, 300L) else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Executes an HTTP GET request with OkHttp, following redirects and utilizing shared session cookies.
     */
    suspend fun getHtml(
        url: String,
        referer: String? = null,
        maxBytes: Long = 2 * 1024 * 1024
    ): HttpResponse = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url)
        if (!referer.isNullOrBlank()) {
            reqBuilder.header("Referer", referer)
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                val code = response.code
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                val finalUrl = response.request.url.toString()

                if (!response.isSuccessful) {
                    return@withContext HttpResponse(
                        isSuccessful = false,
                        statusCode = code,
                        contentType = contentType,
                        finalUrl = finalUrl,
                        errorMessage = "Server returned HTTP status $code"
                    )
                }

                val body = response.body
                val responseString = if (body != null) {
                    val source = body.source()
                    source.request(maxBytes)
                    val buffer = source.buffer
                    val toRead = minOf(buffer.size, maxBytes)
                    buffer.clone().readUtf8(toRead)
                } else {
                    ""
                }

                HttpResponse(
                    isSuccessful = true,
                    statusCode = code,
                    contentType = contentType,
                    finalUrl = finalUrl,
                    bodyString = responseString
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getHtml failed for $url", e)
            HttpResponse(
                isSuccessful = false,
                statusCode = -1,
                contentType = "",
                finalUrl = url,
                errorMessage = e.message ?: "Network request failed"
            )
        }
    }

    /**
     * Generates a deterministic file identifier from URL and file name to persist partial downloads across retries.
     */
    fun generateDeterministicId(mediaUrl: String, fileName: String): String =
        MediaDownloadStorage.generateDeterministicId(mediaUrl, fileName)

    /**
     * Resolves and ensures existence of the target storage directory (public Downloads or app external storage).
     */
    fun getTargetStorageDir(context: Context): File =
        MediaDownloadStorage.getTargetStorageDir(context)

    /**
     * Downloads an image, video, audio file, or stream to storage with resumable Range support.
     */
    suspend fun downloadMediaFileDetailed(
        context: Context,
        mediaUrl: String,
        fileName: String,
        referer: String? = null,
        downloadId: String? = null,
        headers: Map<String, String>? = null,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (mediaUrl.startsWith("data:image/", ignoreCase = true)) {
            return@withContext DataUriMediaSaver.save(context, mediaUrl, fileName, downloadId)
        }
        ResumableMediaTransfer.executeDownload(
            client = client,
            context = context,
            mediaUrl = mediaUrl,
            fileName = fileName,
            referer = referer,
            downloadId = downloadId,
            headers = headers,
            onProgress = onProgress
        )
    }

    /**
     * Backward-compatible convenience method returning File?
     */
    suspend fun downloadMediaFile(
        context: Context,
        mediaUrl: String,
        fileName: String,
        referer: String? = null,
        downloadId: String? = null,
        headers: Map<String, String>? = null,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): File? {
        val res = downloadMediaFileDetailed(context, mediaUrl, fileName, referer, downloadId, headers, onProgress)
        return res.file
    }
}
