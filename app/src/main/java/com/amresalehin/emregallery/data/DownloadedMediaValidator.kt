package com.amresalehin.emregallery.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * Validates downloaded temporary payloads (rejecting HTML/JSON/streaming manifests and corrupt raster images)
 * and atomically commits valid files to persistent storage.
 */
internal object DownloadedMediaValidator {

    private const val TAG = "DownloadedMediaValidator"

    fun validateAndCommit(
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
    ): OkHttpDownloader.DownloadResult {
        if (!tempFile.exists() || tempFile.length() <= 0L) {
            tempFile.delete()
            metaFile.delete()
            return OkHttpDownloader.DownloadResult(
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

        val headError = inspectPayloadHeader(tempFile, mediaUrl, isHlsManifest, isDashManifest)
        if (headError != null) {
            tempFile.delete()
            metaFile.delete()
            return OkHttpDownloader.DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = code,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                errorMessage = headError
            )
        }

        // For raster images only: verify BitmapFactory can decode bounds.
        // Do NOT attempt BitmapFactory decoding on AVI, FLV, 3GP, TS, AAC, FLAC, OPUS, WMA or other non-raster formats.
        if (isRasterImage(fileName, mimeType)) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                Log.w(TAG, "Downloaded image could not be decoded: ${tempFile.name}")
                tempFile.delete()
                metaFile.delete()
                return OkHttpDownloader.DownloadResult(
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

        val moved = commitTempFile(tempFile, finalFile)
        if (moved && finalFile.exists() && finalFile.length() > 0L) {
            MediaDownloadStorage.scanMediaFile(context, finalFile, mimeType)
            return OkHttpDownloader.DownloadResult(
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
        return OkHttpDownloader.DownloadResult(
            file = null,
            isSuccessful = false,
            statusCode = code,
            bytesDownloaded = 0L,
            totalBytes = totalBytes,
            errorMessage = "Downloaded file was empty or could not be committed"
        )
    }

    private fun inspectPayloadHeader(
        tempFile: File,
        mediaUrl: String,
        isHlsManifest: Boolean,
        isDashManifest: Boolean
    ): String? {
        val head = ByteArray(1024)
        val headRead = runCatching {
            FileInputStream(tempFile).use { fis -> fis.read(head) }
        }.getOrDefault(-1)

        if (headRead <= 0) return null

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
            return "Downloaded file contains HTML webpage content instead of media"
        }

        val trimmedHead = headStr.trimStart()
        val isJsonError = (trimmedHead.startsWith("{") || trimmedHead.startsWith("[")) && (
            trimmedHead.contains("\"error\"") || trimmedHead.contains("\"message\"") ||
                trimmedHead.contains("\"status\"") || trimmedHead.contains("\"code\"") ||
                trimmedHead.contains("\"forbidden\"") || trimmedHead.contains("\"unauthorized\"") ||
                trimmedHead.contains("\"success\":false")
            )

        if (isJsonError) {
            Log.w(TAG, "Downloaded file for $mediaUrl contains JSON error, deleting: ${tempFile.name}")
            return "Downloaded file contains JSON error response instead of media"
        }

        if (isHlsManifest || isDashManifest || headStr.contains("#extm3u") ||
            headStr.contains("<mpd") || headStr.contains("urn:mpeg:dash:schema")
        ) {
            Log.w(TAG, "Streaming playlist text (.m3u8 / .mpd) cannot be saved as finished media: ${tempFile.name}")
            return "Cannot save streaming playlist text (.m3u8/.mpd) as finished media file"
        }

        return null
    }

    private fun isRasterImage(fileName: String, mimeType: String?): Boolean {
        return if (mimeType != null) {
            mimeType == "image/jpeg" || mimeType == "image/jpg" ||
                mimeType == "image/png" || mimeType == "image/webp" ||
                mimeType == "image/gif" || mimeType == "image/bmp" || mimeType == "image/x-ms-bmp"
        } else {
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) ||
                fileName.endsWith(".png", true) || fileName.endsWith(".webp", true) ||
                fileName.endsWith(".gif", true) || fileName.endsWith(".bmp", true)
        }
    }

    private fun commitTempFile(tempFile: File, finalFile: File): Boolean {
        return if (tempFile.renameTo(finalFile)) {
            true
        } else {
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
            true
        }
    }
}
