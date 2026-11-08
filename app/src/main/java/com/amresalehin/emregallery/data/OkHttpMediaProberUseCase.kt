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

internal suspend fun OkHttpDownloader.probeDirectMediaImpl(
    url: String,
    referer: String? = null
): DirectMediaProbeResult = withContext(Dispatchers.IO) {
    try {
        val headReq = Request.Builder()
            .url(url)
            .head()
            .apply {
                if (!referer.isNullOrBlank()) header("Referer", referer)
            }
            .build()

        val headResp = runCatching { client.newCall(headReq).execute() }.getOrNull()
        if (headResp != null && headResp.isSuccessful) {
            headResp.use { resp ->
                val ct = resp.header("Content-Type")?.lowercase(Locale.ROOT)
                val cd = resp.header("Content-Disposition")
                val cl = resp.header("Content-Length")?.toLongOrNull()
                val finalUrl = resp.request.url.toString()
                val isMedia = isMediaContentType(ct) || hasMediaContentDisposition(cd) || isDirectMediaExtension(finalUrl)
                if (isMedia) {
                    return@withContext DirectMediaProbeResult(
                        isConfirmedDirectMedia = true,
                        finalUrl = finalUrl,
                        contentType = ct,
                        contentDisposition = cd,
                        contentLength = cl,
                        statusCode = resp.code
                    )
                }
            }
        }

        // Fallback: fast Range GET (0-1024) to probe server if HEAD was rejected or inconclusive
        val rangeReq = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-1024")
            .apply {
                if (!referer.isNullOrBlank()) header("Referer", referer)
            }
            .build()

        client.newCall(rangeReq).execute().use { resp ->
            val ct = resp.header("Content-Type")?.lowercase(Locale.ROOT)
            val cd = resp.header("Content-Disposition")
            val cl = resp.header("Content-Length")?.toLongOrNull()
            val finalUrl = resp.request.url.toString()
            val isMedia = (resp.code in 200..206) && (isMediaContentType(ct) || hasMediaContentDisposition(cd) || isDirectMediaExtension(finalUrl))
            DirectMediaProbeResult(
                isConfirmedDirectMedia = isMedia,
                finalUrl = finalUrl,
                contentType = ct,
                contentDisposition = cd,
                contentLength = cl,
                statusCode = resp.code
            )
        }
    } catch (e: Exception) {
        DirectMediaProbeResult(
            isConfirmedDirectMedia = false,
            finalUrl = url,
            contentType = null,
            contentDisposition = null,
            contentLength = null,
            statusCode = -1
        )
    }
}

internal fun OkHttpDownloader.isMediaContentType(ct: String?): Boolean {
    if (ct.isNullOrBlank()) return false
    val clean = ct.substringBefore(";").trim().lowercase(Locale.ROOT)
    return clean.startsWith("image/") ||
            clean.startsWith("video/") ||
            clean.startsWith("audio/") ||
            clean == "application/x-mpegurl" ||
            clean == "application/vnd.apple.mpegurl" ||
            clean == "application/dash+xml"
}

internal fun OkHttpDownloader.hasMediaContentDisposition(cd: String?): Boolean {
    if (cd.isNullOrBlank()) return false
    val lower = cd.lowercase(Locale.ROOT)
    return lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".mkv") ||
            lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
            lower.contains(".webp") || lower.contains(".gif") || lower.contains(".mp3") ||
            lower.contains(".m4a") || lower.contains(".flac") || lower.contains(".wav")
}

internal fun OkHttpDownloader.isDirectMediaExtension(url: String): Boolean {
    val clean = url.substringBefore("?").substringBefore("#").lowercase(Locale.ROOT)
    return clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mkv") ||
            clean.endsWith(".mov") || clean.endsWith(".jpg") || clean.endsWith(".jpeg") ||
            clean.endsWith(".png") || clean.endsWith(".webp") || clean.endsWith(".gif") ||
            clean.endsWith(".m3u8") || clean.endsWith(".mpd") || clean.endsWith(".mp3") ||
            clean.endsWith(".m4a") || clean.endsWith(".flac") || clean.endsWith(".wav")
}

/**
 * Parses the HTTP 'Retry-After' header (RFC 7231/9110) as seconds or HTTP-date.
 */
internal fun OkHttpDownloader.parseRetryAfterHeaderImpl(header: String?): Long? {
    if (header.isNullOrBlank()) return null
    val trimmed = header.trim()
    val seconds = trimmed.toLongOrNull()
    if (seconds != null) {
        return seconds.coerceIn(1L, 300L)
    }
    return try {
        val format = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
        val date = format.parse(trimmed)
        if (date != null) {
            val diffSec = (date.time - System.currentTimeMillis()) / 1000L
            if (diffSec > 0) diffSec.coerceIn(1L, 300L) else null
        } else null
    } catch (_: Exception) {
        null
    }
}

/**
 * Executes an HTTP GET request with OkHttp, following redirects and utilizing shared session cookies.
 */
internal suspend fun OkHttpDownloader.getHtmlImpl(
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
internal fun OkHttpDownloader.generateDeterministicIdImpl(mediaUrl: String, fileName: String): String {
    val hash = runCatching {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(mediaUrl.toByteArray(Charsets.UTF_8))
        bytes.take(8).joinToString("") { "%02x".format(it) }
    }.getOrDefault(Integer.toHexString(mediaUrl.hashCode()))
    val base = fileName.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
    val ext = fileName.substringAfterLast('.', "")
    return if (ext.isNotEmpty()) "${base}_$hash.$ext" else "${base}_$hash"
}

/**
 * Resolves and ensures existence of the target storage directory (public Downloads or app external storage).
 */
internal fun OkHttpDownloader.getTargetStorageDirImpl(context: Context): File {
    val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val emreGalleryDir = File(publicDownloads, "Emre Gallery")
    if (!emreGalleryDir.exists()) {
        emreGalleryDir.mkdirs()
    }
    return if (emreGalleryDir.exists() && emreGalleryDir.canWrite()) {
        emreGalleryDir
    } else {
        val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val fallbackFolder = File(appExternal ?: context.filesDir, "Emre Gallery")
        if (!fallbackFolder.exists()) fallbackFolder.mkdirs()
        fallbackFolder
    }
}

/**
 * Downloads an image, video, audio file, or stream to storage with resumable Range support.
 */
