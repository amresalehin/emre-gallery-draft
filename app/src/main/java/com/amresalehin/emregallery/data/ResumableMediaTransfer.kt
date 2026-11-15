package com.amresalehin.emregallery.data

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

/**
 * Executes resumable HTTP downloads with RFC 7233 Range/If-Range support,
 * Content-Range validation, MIME/manifest rejection, and streaming progress reporting.
 */
internal object ResumableMediaTransfer {

    private const val TAG = "ResumableMediaTransfer"
    private val contentRangeRegex = Regex("""bytes\s+(\d+)-\d+/.*""", RegexOption.IGNORE_CASE)

    fun executeDownload(
        client: OkHttpClient,
        context: Context,
        mediaUrl: String,
        fileName: String,
        referer: String?,
        downloadId: String?,
        headers: Map<String, String>?,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)?
    ): OkHttpDownloader.DownloadResult {
        val targets = MediaDownloadStorage.resolveDownloadTargets(context, mediaUrl, fileName, downloadId)
        val finalFile = targets.finalFile
        val tempFile = targets.tempFile
        val metaFile = targets.metaFile

        val resumeMeta = MediaDownloadStorage.readResumeMetadata(metaFile)
        var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val initialRequest = buildMediaRequest(
            mediaUrl = mediaUrl,
            referer = referer,
            headers = headers,
            existingBytes = existingBytes,
            resumeMeta = resumeMeta
        )

        try {
            var activeResponse = client.newCall(initialRequest).execute()

            // Handle 416 Range Not Satisfiable
            if (activeResponse.code == 416) {
                val cr = activeResponse.header("Content-Range")
                val totalLength = cr?.substringAfterLast('/', "")?.toLongOrNull()
                if (totalLength != null && totalLength > 0L && existingBytes == totalLength &&
                    tempFile.exists() && tempFile.length() == totalLength
                ) {
                    activeResponse.close()
                    return DownloadedMediaValidator.validateAndCommit(
                        context = context,
                        mediaUrl = mediaUrl,
                        fileName = fileName,
                        tempFile = tempFile,
                        metaFile = metaFile,
                        finalFile = finalFile,
                        code = 200,
                        totalBytes = totalLength,
                        mimeType = null,
                        isResumed = true
                    )
                } else {
                    activeResponse.close()
                    existingBytes = 0L
                    activeResponse = reissueFromStart(client, mediaUrl, referer, headers, tempFile, metaFile)
                }
            }

            // Check Content-Range start match before streaming
            if (activeResponse.code == 206 && existingBytes > 0L) {
                val startByte = parseContentRangeStart(activeResponse.header("Content-Range"))
                if (startByte != null && startByte != existingBytes) {
                    Log.w(TAG, "Content-Range start mismatch: expected $existingBytes, got $startByte. Restarting from byte 0.")
                    activeResponse.close()
                    existingBytes = 0L
                    activeResponse = reissueFromStart(client, mediaUrl, referer, headers, tempFile, metaFile)
                }
            }

            activeResponse.use { resp ->
                val code = resp.code
                val isPartial = code == 206
                if (code !in 200..299) {
                    val err = "Server returned HTTP status $code"
                    Log.w(TAG, "Download failed HTTP $code for $mediaUrl")
                    val retryAfterSec = OkHttpDownloader.parseRetryAfterHeader(resp.header("Retry-After"))
                    return OkHttpDownloader.DownloadResult(
                        file = null,
                        isSuccessful = false,
                        statusCode = code,
                        bytesDownloaded = existingBytes,
                        totalBytes = -1L,
                        errorMessage = err,
                        retryAfterSeconds = retryAfterSec
                    )
                }

                val body = resp.body ?: return OkHttpDownloader.DownloadResult(
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
                    return OkHttpDownloader.DownloadResult(
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
                    return OkHttpDownloader.DownloadResult(
                        file = null,
                        isSuccessful = false,
                        statusCode = code,
                        bytesDownloaded = 0L,
                        totalBytes = -1L,
                        errorMessage = "Streaming manifests (.m3u8/.mpd) must be downloaded and remuxed via stream engine"
                    )
                }

                val append = if (isPartial && existingBytes > 0L) {
                    true
                } else {
                    if (existingBytes > 0L) {
                        tempFile.delete()
                        existingBytes = 0L
                    }
                    false
                }

                MediaDownloadStorage.writeResumeMetadata(
                    metaFile = metaFile,
                    etag = resp.header("ETag"),
                    lastModified = resp.header("Last-Modified")
                )

                val totalBytes = resolveTotalBytes(
                    isPartial = isPartial,
                    contentRange = resp.header("Content-Range"),
                    contentLength = body.contentLength(),
                    existingBytes = existingBytes
                )

                val initialBytes = if (append) existingBytes else 0L
                val downloaded = streamToTempFile(
                    input = body.byteStream(),
                    tempFile = tempFile,
                    append = append,
                    initialBytes = initialBytes,
                    totalBytes = totalBytes,
                    onProgress = onProgress
                )

                if (totalBytes > 0L && tempFile.length() != totalBytes) {
                    Log.w(TAG, "Download incomplete: expected $totalBytes bytes, got ${tempFile.length()}")
                    return OkHttpDownloader.DownloadResult(
                        file = null,
                        isSuccessful = false,
                        statusCode = code,
                        bytesDownloaded = tempFile.length(),
                        totalBytes = totalBytes,
                        errorMessage = "Download interrupted or size mismatch ($downloaded of $totalBytes bytes)"
                    )
                }

                return DownloadedMediaValidator.validateAndCommit(
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
        } catch (e: Exception) {
            Log.e(TAG, "Download exception for $mediaUrl", e)
            return OkHttpDownloader.DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = -1,
                bytesDownloaded = existingBytes,
                totalBytes = -1L,
                errorMessage = e.message ?: "Network error during download"
            )
        }
    }

    private fun buildMediaRequest(
        mediaUrl: String,
        referer: String?,
        headers: Map<String, String>?,
        existingBytes: Long = 0L,
        resumeMeta: ResumeMetadata? = null
    ): Request {
        val reqBuilder = Request.Builder()
            .url(mediaUrl)
            .header("Accept", "*/*")

        if (!referer.isNullOrBlank()) {
            reqBuilder.header("Referer", referer)
        }

        headers?.forEach { (k, v) ->
            if (!k.equals("Range", ignoreCase = true) && !k.equals("If-Range", ignoreCase = true)) {
                reqBuilder.header(k, v)
            }
        }

        if (existingBytes > 0L) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
            val etag = resumeMeta?.etag
            val lastMod = resumeMeta?.lastModified
            if (!etag.isNullOrBlank()) {
                reqBuilder.header("If-Range", etag)
            } else if (!lastMod.isNullOrBlank()) {
                reqBuilder.header("If-Range", lastMod)
            }
        }

        return reqBuilder.build()
    }

    private fun reissueFromStart(
        client: OkHttpClient,
        mediaUrl: String,
        referer: String?,
        headers: Map<String, String>?,
        tempFile: File,
        metaFile: File
    ): Response {
        tempFile.delete()
        metaFile.delete()
        val cleanRequest = buildMediaRequest(
            mediaUrl = mediaUrl,
            referer = referer,
            headers = headers
        )
        return client.newCall(cleanRequest).execute()
    }

    private fun parseContentRangeStart(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val match = contentRangeRegex.find(contentRange)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun resolveTotalBytes(
        isPartial: Boolean,
        contentRange: String?,
        contentLength: Long,
        existingBytes: Long
    ): Long {
        return if (isPartial) {
            val rangeTotal = contentRange?.substringAfterLast('/', "")?.toLongOrNull()
            when {
                rangeTotal != null && rangeTotal > 0L -> rangeTotal
                contentLength > 0L -> existingBytes + contentLength
                else -> -1L
            }
        } else {
            if (contentLength > 0L) contentLength else -1L
        }
    }

    private fun streamToTempFile(
        input: InputStream,
        tempFile: File,
        append: Boolean,
        initialBytes: Long,
        totalBytes: Long,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)?
    ): Long {
        var downloaded = initialBytes
        input.use { stream ->
            FileOutputStream(tempFile, append).use { output ->
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    onProgress?.invoke(downloaded, totalBytes)
                }
                output.flush()
            }
        }
        return downloaded
    }
}
