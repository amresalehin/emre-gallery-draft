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

internal fun OkHttpDownloader.processActiveDownloadResponse(
    activeResponse: okhttp3.Response,
    context: Context,
    mediaUrl: String,
    fileName: String,
    tempFile: File,
    metaFile: File,
    finalFile: File,
    existingBytes: Long,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)?
): OkHttpDownloader.DownloadResult {
        activeResponse.use { resp ->
            val code = resp.code
            val isPartial = code == 206
            val isSuccess = code in 200..299

            if (!isSuccess) {
                val err = "Server returned HTTP status $code"
                Log.w(TAG, "Download failed HTTP $code for $mediaUrl")
                val retryAfterHeader = resp.header("Retry-After")
                val retryAfterSec = parseRetryAfterHeader(retryAfterHeader)
                return DownloadResult(
                    file = null,
                    isSuccessful = false,
                    statusCode = code,
                    bytesDownloaded = existingBytes,
                    totalBytes = -1L,
                    errorMessage = err,
                    retryAfterSeconds = retryAfterSec
                )
            }

            val body = resp.body ?: return DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = code,
                bytesDownloaded = existingBytes,
                totalBytes = -1L,
                errorMessage = "Empty response body"
            )

            val rawMime = resp.header("Content-Type")?.lowercase(Locale.ROOT)
            val mimeType = rawMime?.substringBefore(";")?.trim()

            val isHlsManifest = mediaUrl.contains(".m3u8", ignoreCase = true) || fileName.endsWith(".m3u8", ignoreCase = true)
            val isDashManifest = mediaUrl.contains(".mpd", ignoreCase = true) || fileName.endsWith(".mpd", ignoreCase = true)
            val isManifest = isHlsManifest || isDashManifest

            // 1. Unconditionally reject HTML pages and JSON error responses even for manifest URLs
            if (mimeType != null && (
                    mimeType.startsWith("text/html") ||
                    mimeType.startsWith("application/json") ||
                    (mimeType.startsWith("text/plain") && !isHlsManifest && !mediaUrl.contains("playlist", ignoreCase = true))
                )
            ) {
                tempFile.delete()
                metaFile.delete()
                Log.w(TAG, "Download rejected for $mediaUrl: server returned Content-Type $mimeType")
                return DownloadResult(
                    file = null,
                    isSuccessful = false,
                    statusCode = code,
                    bytesDownloaded = 0L,
                    totalBytes = -1L,
                    errorMessage = "Server returned $mimeType webpage or error response instead of media content"
                )
            }

            // 2. Reject streaming manifest URLs (.m3u8/.mpd) from direct OkHttp file commits
            if (isManifest) {
                tempFile.delete()
                metaFile.delete()
                return DownloadResult(
                    file = null,
                    isSuccessful = false,
                    statusCode = code,
                    bytesDownloaded = 0L,
                    totalBytes = -1L,
                    errorMessage = "Streaming manifests (.m3u8/.mpd) must be downloaded and remuxed via stream engine"
                )
            }

            val contentRange = resp.header("Content-Range")
            val append = if (isPartial && existingBytes > 0L) {
                true
            } else {
                // Server returned HTTP 200 (ignored Range request) or restarted cleanly:
                if (existingBytes > 0L) {
                    tempFile.delete()
                    existingBytes = 0L
                }
                false
            }

            // Store or update ETag / Last-Modified metadata for next resume attempt
            val currentEtag = resp.header("ETag")
            val currentLastModified = resp.header("Last-Modified")
            if (!currentEtag.isNullOrBlank() || !currentLastModified.isNullOrBlank()) {
                runCatching {
                    metaFile.writeText("etag=${currentEtag.orEmpty()}\nlast_modified=${currentLastModified.orEmpty()}")
                }
            }

            val contentLength = body.contentLength()
            val totalBytes = if (isPartial) {
                val rangeTotal = contentRange?.substringAfterLast('/', "")?.toLongOrNull()
                if (rangeTotal != null && rangeTotal > 0L) {
                    rangeTotal
                } else if (contentLength > 0L) {
                    existingBytes + contentLength
                } else {
                    -1L
                }
            } else {
                if (contentLength > 0L) contentLength else -1L
            }

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

            // Verify actual downloaded size before committing the file
            if (totalBytes > 0L && tempFile.length() != totalBytes) {
                Log.w(TAG, "Download incomplete: expected $totalBytes bytes, got ${tempFile.length()}")
                return DownloadResult(
                    file = null,
                    isSuccessful = false,
                    statusCode = code,
                    bytesDownloaded = tempFile.length(),
                    totalBytes = totalBytes,
                    errorMessage = "Download interrupted or size mismatch ($downloaded of $totalBytes bytes)"
                )
            }

            return validateAndCommitDownloadedFile(
                context = context,
                mediaUrl = mediaUrl,
                fileName = fileName,
                tempFile = tempFile,
                metaFile = metaFile,
                finalFile = finalFile,
                code = code,
                totalBytes = totalBytes,
                mimeType = mimeType,
                isResumed = append
            )
        }

}
