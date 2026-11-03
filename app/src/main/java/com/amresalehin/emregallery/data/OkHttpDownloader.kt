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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Shared session HTTP client and high-performance streaming downloader powered by OkHttp.
 * Provides:
 * 1. Unified, persistent cookie jar synchronized bidirectionally with Android WebView CookieManager.
 * 2. Shared connection pooling (HTTP/2, keep-alive) and automatic redirect handling.
 * 3. Atomic streaming downloads with media scanner indexing and corrupt file cleanup.
 * 4. Transparent User-Agent and referer header support.
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
            } catch (e: Throwable) {
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
                    // Filter out expired cookies
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
            } catch (e: Throwable) {
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
     * Downloads an image, video, audio file, or binary asset to public downloads using OkHttp.
     * Uses atomic temporary file writing to avoid partial/corrupt files on interrupted downloads.
     */
    suspend fun downloadMediaFile(
        context: Context,
        mediaUrl: String,
        fileName: String,
        referer: String? = null,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder()
            .url(mediaUrl)
            .header("Accept", "*/*")
        if (!referer.isNullOrBlank()) {
            reqBuilder.header("Referer", referer)
        }

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

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Download failed HTTP ${response.code} for $mediaUrl")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val totalBytes = body.contentLength()

                body.byteStream().use { input: InputStream ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        var downloaded = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            onProgress?.invoke(downloaded, totalBytes)
                        }
                        output.flush()
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0L) {
                    if (tempFile.renameTo(finalFile)) {
                        // Notify Android MediaScanner so file appears immediately in Gallery
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(finalFile.absolutePath),
                            null,
                            null
                        )
                        return@withContext finalFile
                    } else {
                        // Fallback copy if rename across filesystems fails
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(finalFile.absolutePath),
                            null,
                            null
                        )
                        return@withContext finalFile
                    }
                } else {
                    tempFile.delete()
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download exception for $mediaUrl", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            null
        }
    }
}
