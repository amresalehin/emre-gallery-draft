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

internal fun OkHttpDownloader.validateAndCommitDownloadedFile(
    context: Context,
    mediaUrl: String,
    fileName: String,
    tempFile: File,
    metaFile: File,
    finalFile: File,
    code: Int,
    totalBytes: Long,
    mimeType: String?,
    isResumed: Boolean
): DownloadResult {
    if (!tempFile.exists() || tempFile.length() <= 0L) {
        tempFile.delete()
        metaFile.delete()
        return DownloadResult(
            file = null,
            isSuccessful = false,
            statusCode = code,
            bytesDownloaded = 0L,
            totalBytes = totalBytes,
            errorMessage = "Downloaded file was empty or could not be committed"
        )
    }

    val isHlsManifest = mediaUrl.contains(".m3u8", ignoreCase = true) || fileName.endsWith(".m3u8", ignoreCase = true)
    val isDashManifest = mediaUrl.contains(".mpd", ignoreCase = true) || fileName.endsWith(".mpd", ignoreCase = true)

    // Check if file content begins with HTML tags, Cloudflare/anti-bot challenge, or JSON error object
    val head = ByteArray(1024)
    val headRead = runCatching {
        FileInputStream(tempFile).use { fis -> fis.read(head) }
    }.getOrDefault(-1)

    if (headRead > 0) {
        val headStr = String(head, 0, headRead, Charsets.UTF_8).lowercase(Locale.ROOT)
        val isHtmlPayload = headStr.contains("<!doctype html") || headStr.contains("<html") ||
                headStr.contains("<head") || headStr.contains("<body") || headStr.contains("<title") ||
                headStr.contains("cloudflare") || headStr.contains("ray id:") ||
                headStr.contains("access denied") || headStr.contains("just a moment...") ||
                headStr.contains("ddos protection") || headStr.contains("challenge-running") ||
                headStr.contains("403 forbidden") || headStr.contains("404 not found") ||
                headStr.contains("502 bad gateway") || headStr.contains("503 service")

        if (isHtmlPayload) {
            Log.w(TAG, "Downloaded file for $mediaUrl contains HTML webpage or error text, deleting: ${tempFile.name}")
            tempFile.delete()
            metaFile.delete()
            return DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = code,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                errorMessage = "Downloaded file contains HTML webpage content instead of media"
            )
        }

        val trimmedHead = headStr.trimStart()
        if ((trimmedHead.startsWith("{") || trimmedHead.startsWith("[")) &&
            (trimmedHead.contains("\"error\"") || trimmedHead.contains("\"message\"") ||
             trimmedHead.contains("\"status\"") || trimmedHead.contains("\"code\"") ||
             trimmedHead.contains("\"forbidden\"") || trimmedHead.contains("\"unauthorized\"") ||
             trimmedHead.contains("\"success\":false"))
        ) {
            Log.w(TAG, "Downloaded file for $mediaUrl contains JSON error, deleting: ${tempFile.name}")
            tempFile.delete()
            metaFile.delete()
            return DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = code,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                errorMessage = "Downloaded file contains JSON error response instead of media"
            )
        }

        // Reject raw streaming playlists (.m3u8 or .mpd) from being saved as finished media files
        if (isHlsManifest || isDashManifest || headStr.contains("#extm3u") || headStr.contains("<mpd") || headStr.contains("urn:mpeg:dash:schema")) {
            Log.w(TAG, "Streaming playlist text (.m3u8 / .mpd) cannot be saved as finished media: ${tempFile.name}")
            tempFile.delete()
            metaFile.delete()
            return DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = code,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                errorMessage = "Cannot save streaming playlist text (.m3u8/.mpd) as finished media file"
            )
        }
    }

    // For raster images only: verify BitmapFactory can decode bounds.
    // Do NOT attempt BitmapFactory decoding on AVI, FLV, 3GP, TS, AAC, FLAC, OPUS, WMA or other non-raster formats.
    val isRasterImage = when {
        mimeType != null -> mimeType == "image/jpeg" || mimeType == "image/jpg" ||
                mimeType == "image/png" || mimeType == "image/webp" ||
                mimeType == "image/gif" || mimeType == "image/bmp" || mimeType == "image/x-ms-bmp"
        else -> fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) ||
                fileName.endsWith(".png", true) || fileName.endsWith(".webp", true) ||
                fileName.endsWith(".gif", true) || fileName.endsWith(".bmp", true)
    }

    if (isRasterImage) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempFile.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            Log.w(TAG, "Downloaded image could not be decoded: ${tempFile.name}")
            tempFile.delete()
            metaFile.delete()
            return DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = code,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                errorMessage = "Downloaded file is corrupt or invalid raster image format"
            )
        }
    }

    // Clean up metadata file upon successful completion
    metaFile.delete()

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
        return DownloadResult(
            file = finalFile,
            isSuccessful = true,
            statusCode = code,
            bytesDownloaded = finalFile.length(),
            totalBytes = totalBytes,
            mimeType = mimeType,
            isResumed = isResumed
        )
    }

    tempFile.delete()
    metaFile.delete()
    return DownloadResult(
        file = null,
        isSuccessful = false,
        statusCode = code,
        bytesDownloaded = 0L,
        totalBytes = totalBytes,
        errorMessage = "Downloaded file was empty or could not be committed"
    )
}

/**
 * Decodes and stores high-resolution Base64 inline data URIs directly to persistent media storage.
 */
internal fun OkHttpDownloader.downloadDataUriMedia(
    context: Context,
    dataUri: String,
    fileName: String,
    downloadId: String?
): DownloadResult {
    return try {
        val targetDir = getTargetStorageDir(context)
        val dataInfo = MediaClassifier.parseDataUriInfo(dataUri)
        val ext = dataInfo?.format?.lowercase(Locale.ROOT) ?: "png"
        val mimeType = dataInfo?.mimeType ?: "image/png"
        val cleanName = if (!downloadId.isNullOrBlank()) {
            val base = downloadId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            if (base.endsWith(".$ext", ignoreCase = true)) base else "$base.$ext"
        } else {
            val sanitized = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            if (sanitized.endsWith(".$ext", ignoreCase = true)) sanitized else "$sanitized.$ext"
        }

        val finalFile = File(targetDir, cleanName)
        val base64Content = dataUri.substringAfter(";base64,", "")
        if (base64Content.isBlank()) {
            return DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = 400,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                errorMessage = "Invalid base64 payload in data URI"
            )
        }

        val decodedBytes = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
        if (decodedBytes == null || decodedBytes.isEmpty()) {
            return DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = 400,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                errorMessage = "Failed to decode base64 data"
            )
        }

        // Validate raster image bounds if raster
        if (mimeType != "image/svg+xml") {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return DownloadResult(
                    file = null,
                    isSuccessful = false,
                    statusCode = 422,
                    bytesDownloaded = 0L,
                    totalBytes = -1L,
                    errorMessage = "Data URI raster image is invalid or corrupt"
                )
            }
        }

        finalFile.writeBytes(decodedBytes)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(finalFile.absolutePath),
            arrayOf(mimeType),
            null
        )

        DownloadResult(
            file = finalFile,
            isSuccessful = true,
            statusCode = 200,
            bytesDownloaded = finalFile.length(),
            totalBytes = finalFile.length(),
            mimeType = mimeType,
            isResumed = false
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to download data URI", e)
        DownloadResult(
            file = null,
            isSuccessful = false,
            statusCode = 500,
            bytesDownloaded = 0L,
            totalBytes = -1L,
            errorMessage = e.message ?: "Failed to write data URI"
        )
    }
}

/**
 * Backward-compatible convenience method returning File?
 */
internal suspend fun OkHttpDownloader.downloadMediaFileImpl(
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
