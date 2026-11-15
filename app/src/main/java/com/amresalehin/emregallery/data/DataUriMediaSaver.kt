package com.amresalehin.emregallery.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Decodes and stores inline Base64 data:image URIs directly to persistent media storage.
 */
internal object DataUriMediaSaver {

    private const val TAG = "DataUriMediaSaver"

    fun save(
        context: Context,
        dataUri: String,
        fileName: String,
        downloadId: String?
    ): OkHttpDownloader.DownloadResult {
        return try {
            val targetDir = MediaDownloadStorage.getTargetStorageDir(context)
            val dataInfo = DataUriParser.parse(dataUri)
            val ext = dataInfo?.format?.lowercase(Locale.ROOT) ?: "png"
            val mimeType = dataInfo?.mimeType ?: "image/png"
            val cleanName = if (!downloadId.isNullOrBlank()) {
                val base = MediaDownloadStorage.sanitizeFileName(downloadId)
                if (base.endsWith(".$ext", ignoreCase = true)) base else "$base.$ext"
            } else {
                val sanitized = MediaDownloadStorage.sanitizeFileName(fileName)
                if (sanitized.endsWith(".$ext", ignoreCase = true)) sanitized else "$sanitized.$ext"
            }

            val finalFile = File(targetDir, cleanName)
            val base64Content = dataUri.substringAfter(";base64,", "")
            if (base64Content.isBlank()) {
                return OkHttpDownloader.DownloadResult(
                    file = null,
                    isSuccessful = false,
                    statusCode = 400,
                    bytesDownloaded = 0L,
                    totalBytes = -1L,
                    errorMessage = "Invalid base64 payload in data URI"
                )
            }

            val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
            if (decodedBytes == null || decodedBytes.isEmpty()) {
                return OkHttpDownloader.DownloadResult(
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
                    return OkHttpDownloader.DownloadResult(
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
            MediaDownloadStorage.scanMediaFile(context, finalFile, mimeType)

            OkHttpDownloader.DownloadResult(
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
            OkHttpDownloader.DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = 500,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                errorMessage = e.message ?: "Failed to write data URI"
            )
        }
    }
}
