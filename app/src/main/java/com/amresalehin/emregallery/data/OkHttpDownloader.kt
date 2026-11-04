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

    private const val TAG = "OkHttpDownloader"
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * Shared bidirectional CookieJar bridging Android WebView CookieManager and in-memory cache.
     */
    class SharedSessionCookieJar : CookieJar {
        private val inMemoryCookies = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val currentList = inMemoryCookies.getOrPut(host) { mutableListOf() }
            synchronized(currentList) {
                for (newCookie in cookies) {
                    currentList.removeAll { it.name == newCookie.name }
                    currentList.add(newCookie)
                }
            }

            // Sync into Android WebView CookieManager if available
            try {
                val cookieManager = CookieManager.getInstance()
                val urlString = url.toString()
                for (cookie in cookies) {
                    cookieManager.setCookie(urlString, cookie.toString())
                }
                cookieManager.flush()
            } catch (_: Throwable) {
                // Ignore in non-Android or JVM unit test environments
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            val result = mutableListOf<Cookie>()

            // 1. Load from in-memory cache
            inMemoryCookies[host]?.let { list ->
                synchronized(list) {
                    val now = System.currentTimeMillis()
                    val valid = list.filter { it.expiresAt > now }
                    result.addAll(valid)
                }
            }

            // 2. Load from Android WebView CookieManager if available
            try {
                val cookieManager = CookieManager.getInstance()
                val rawCookieStr = cookieManager.getCookie(url.toString())
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
            inMemoryCookies.clear()
            try {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            } catch (_: Throwable) {}
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
        val errorMessage: String? = null
    )

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
                    buffer.clone().readUtf8()
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
     * Downloads an image, video, audio file, or stream to storage with resumable Range support.
     */
    suspend fun downloadMediaFileDetailed(
        context: Context,
        mediaUrl: String,
        fileName: String,
        referer: String? = null,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val emreGalleryDir = File(publicDownloads, "Emre Gallery")
        if (!emreGalleryDir.exists()) {
            emreGalleryDir.mkdirs()
        }
        val targetDir = if (emreGalleryDir.exists() && emreGalleryDir.canWrite()) {
            emreGalleryDir
        } else {
            val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val fallbackFolder = File(appExternal ?: context.filesDir, "Emre Gallery")
            if (!fallbackFolder.exists()) fallbackFolder.mkdirs()
            fallbackFolder
        }

        val sanitizedName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val finalFile = File(targetDir, "${System.currentTimeMillis()}_$sanitizedName")
        val tempFile = File(targetDir, "${finalFile.name}.downloading")

        var existingBytes = 0L
        if (tempFile.exists()) {
            existingBytes = tempFile.length()
        }

        val reqBuilder = Request.Builder()
            .url(mediaUrl)
            .header("Accept", "*/*")

        if (!referer.isNullOrBlank()) {
            reqBuilder.header("Referer", referer)
        }

        // Enable resumable downloads via HTTP Range header if partial data exists
        if (existingBytes > 0L) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
        }

        try {
            var response = client.newCall(reqBuilder.build()).execute()

            // Handle 416 Range Not Satisfiable: partial file may be invalid, restart cleanly
            if (response.code == 416) {
                response.close()
                tempFile.delete()
                existingBytes = 0L
                val cleanRequest = Request.Builder()
                    .url(mediaUrl)
                    .header("Accept", "*/*")
                    .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
                    .build()
                response = client.newCall(cleanRequest).execute()
            }

            response.use { resp ->
                val code = resp.code
                val isPartial = code == 206
                val isSuccess = code in 200..299

                if (!isSuccess) {
                    val err = "Server returned HTTP status $code"
                    Log.w(TAG, "Download failed HTTP $code for $mediaUrl")
                    return@withContext DownloadResult(
                        file = null,
                        isSuccessful = false,
                        statusCode = code,
                        bytesDownloaded = existingBytes,
                        totalBytes = -1L,
                        errorMessage = err
                    )
                }

                val body = resp.body ?: return@withContext DownloadResult(
                    file = null,
                    isSuccessful = false,
                    statusCode = code,
                    bytesDownloaded = existingBytes,
                    totalBytes = -1L,
                    errorMessage = "Empty response body"
                )

                val mimeType = resp.header("Content-Type")?.lowercase()
                val isManifest = mediaUrl.contains(".m3u8", ignoreCase = true) || mediaUrl.contains(".mpd", ignoreCase = true)

                // Reject HTML web pages and API error responses masquerading as media
                if (mimeType != null && !isManifest && (mimeType.startsWith("text/html") || mimeType.startsWith("application/json") ||
                        (mimeType.startsWith("text/plain") && !mediaUrl.contains("playlist", ignoreCase = true)))
                ) {
                    tempFile.delete()
                    Log.w(TAG, "Download rejected for $mediaUrl: server returned Content-Type $mimeType")
                    return@withContext DownloadResult(
                        file = null,
                        isSuccessful = false,
                        statusCode = code,
                        bytesDownloaded = 0L,
                        totalBytes = -1L,
                        errorMessage = "Server returned $mimeType webpage instead of media content"
                    )
                }

                val contentLength = body.contentLength()
                val totalBytes = if (isPartial && contentLength > 0L) {
                    existingBytes + contentLength
                } else if (contentLength > 0L) {
                    contentLength
                } else {
                    -1L
                }

                val append = isPartial && existingBytes > 0L
                var downloaded = if (append) existingBytes else 0L

                body.byteStream().use { input: InputStream ->
                    FileOutputStream(tempFile, append).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            onProgress?.invoke(downloaded, totalBytes)
                        }
                        output.flush()
                    }
                }

                // Verify downloaded file integrity before moving to final destination
                if (tempFile.exists() && tempFile.length() > 0L) {
                    // Check if file content begins with HTML tags
                    val isHtmlFile = runCatching {
                        FileInputStream(tempFile).use { fis ->
                            val head = ByteArray(512)
                            val read = fis.read(head)
                            if (read > 0) {
                                val headStr = String(head, 0, read).lowercase(Locale.ROOT)
                                headStr.contains("<!doctype html") || headStr.contains("<html") || headStr.contains("<head")
                            } else false
                        }
                    }.getOrDefault(false)

                    if (isHtmlFile) {
                        Log.w(TAG, "Downloaded file for $mediaUrl contains HTML text, deleting: ${tempFile.name}")
                        tempFile.delete()
                        return@withContext DownloadResult(
                            file = null,
                            isSuccessful = false,
                            statusCode = code,
                            bytesDownloaded = 0L,
                            totalBytes = -1L,
                            errorMessage = "Downloaded file contains HTML webpage content instead of media"
                        )
                    }

                    // For raster images, verify that BitmapFactory can decode bounds
                    val isRasterImage = !fileName.endsWith(".m3u8", true) && !fileName.endsWith(".mpd", true) &&
                            !fileName.endsWith(".mp4", true) && !fileName.endsWith(".webm", true) &&
                            !fileName.endsWith(".mov", true) && !fileName.endsWith(".mkv", true) &&
                            !fileName.endsWith(".mp3", true) && !fileName.endsWith(".m4a", true) &&
                            !fileName.endsWith(".wav", true) && !fileName.endsWith(".ogg", true) &&
                            !fileName.endsWith(".svg", true)

                    if (isRasterImage) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(tempFile.absolutePath, opts)
                        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                            Log.w(TAG, "Downloaded image could not be decoded: ${tempFile.name}")
                            tempFile.delete()
                            return@withContext DownloadResult(
                                file = null,
                                isSuccessful = false,
                                statusCode = code,
                                bytesDownloaded = 0L,
                                totalBytes = -1L,
                                errorMessage = "Downloaded file is corrupt or invalid image format"
                            )
                        }
                    }

                    val moved = if (tempFile.renameTo(finalFile)) {
                        true
                    } else {
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                        true
                    }

                    if (moved && finalFile.exists() && finalFile.length() > 0L) {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(finalFile.absolutePath),
                            arrayOf(mimeType),
                            null
                        )
                        return@withContext DownloadResult(
                            file = finalFile,
                            isSuccessful = true,
                            statusCode = code,
                            bytesDownloaded = finalFile.length(),
                            totalBytes = totalBytes,
                            mimeType = mimeType,
                            isResumed = append
                        )
                    }
                }

                tempFile.delete()
                DownloadResult(
                    file = null,
                    isSuccessful = false,
                    statusCode = code,
                    bytesDownloaded = 0L,
                    totalBytes = totalBytes,
                    errorMessage = "Downloaded file was empty or could not be committed"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download exception for $mediaUrl", e)
            DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = -1,
                bytesDownloaded = existingBytes,
                totalBytes = -1L,
                errorMessage = e.message ?: "Network error during download"
            )
        }
    }

    /**
     * Backward-compatible convenience method returning File?
     */
    suspend fun downloadMediaFile(
        context: Context,
        mediaUrl: String,
        fileName: String,
        referer: String? = null,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): File? {
        val res = downloadMediaFileDetailed(context, mediaUrl, fileName, referer, onProgress)
        return res.file
    }
}
