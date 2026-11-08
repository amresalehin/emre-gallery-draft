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

internal suspend fun OkHttpDownloader.downloadMediaFileDetailedImpl(
    context: Context,
    mediaUrl: String,
    fileName: String,
    referer: String? = null,
    downloadId: String? = null,
    headers: Map<String, String>? = null,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
): DownloadResult = withContext(Dispatchers.IO) {
    // Handle Base64 inline data URIs directly without network calls
    if (mediaUrl.startsWith("data:image/", ignoreCase = true)) {
        return@withContext downloadDataUriMedia(context, mediaUrl, fileName, downloadId)
    }

    val targetDir = getTargetStorageDir(context)

    val sanitizedName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val deterministicBase = if (!downloadId.isNullOrBlank()) {
        val cleanId = downloadId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val ext = sanitizedName.substringAfterLast('.', "")
        if (ext.isNotEmpty() && !cleanId.endsWith(".$ext", ignoreCase = true)) {
            "${cleanId}.$ext"
        } else {
            cleanId
        }
    } else {
        generateDeterministicId(mediaUrl, sanitizedName)
    }

    val finalFile = File(targetDir, deterministicBase)
    val tempFile = File(targetDir, "$deterministicBase.downloading")
    val metaFile = File(targetDir, "$deterministicBase.meta")

    var savedEtag: String? = null
    var savedLastModified: String? = null

    if (metaFile.exists()) {
        runCatching {
            metaFile.readLines().forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    when (parts[0].trim().lowercase(Locale.ROOT)) {
                        "etag" -> savedEtag = parts[1].trim().takeIf { it.isNotEmpty() }
                        "last_modified" -> savedLastModified = parts[1].trim().takeIf { it.isNotEmpty() }
                    }
                }
            }
        }
    }

    var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

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

    // Enable resumable downloads via HTTP Range header if partial data exists
    if (existingBytes > 0L) {
        reqBuilder.header("Range", "bytes=$existingBytes-")
        val etag = savedEtag
        val lastMod = savedLastModified
        if (!etag.isNullOrBlank()) {
            reqBuilder.header("If-Range", etag)
        } else if (!lastMod.isNullOrBlank()) {
            reqBuilder.header("If-Range", lastMod)
        }
    }

    try {
        var response = client.newCall(reqBuilder.build()).execute()

        // Handle 416 Range Not Satisfiable:
        if (response.code == 416) {
            val cr = response.header("Content-Range")
            val totalLength = cr?.substringAfterLast('/', "")?.toLongOrNull()
            if (totalLength != null && totalLength > 0L && existingBytes == totalLength && tempFile.exists() && tempFile.length() == totalLength) {
                response.close()
                val commitRes = validateAndCommitDownloadedFile(
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
                return@withContext commitRes
            } else {
                response.close()
                tempFile.delete()
                metaFile.delete()
                existingBytes = 0L
                val cleanRequest = Request.Builder()
                    .url(mediaUrl)
                    .header("Accept", "*/*")
                    .apply {
                        if (!referer.isNullOrBlank()) header("Referer", referer)
                        headers?.forEach { (k, v) ->
                            if (!k.equals("Range", ignoreCase = true) && !k.equals("If-Range", ignoreCase = true)) {
                                header(k, v)
                            }
                        }
                    }
                    .build()
                response = client.newCall(cleanRequest).execute()
            }
        }

        var activeResponse = response
        var shouldRestartFromZero = false

        // Check Content-Range start match before entering processing
        if (activeResponse.code == 206 && existingBytes > 0L) {
            val cr = activeResponse.header("Content-Range")
            if (!cr.isNullOrBlank()) {
                val rangeRegex = Regex("""bytes\s+(\d+)-\d+/.*""", RegexOption.IGNORE_CASE)
                val match = rangeRegex.find(cr)
                val startByte = match?.groupValues?.get(1)?.toLongOrNull()
                if (startByte != null && startByte != existingBytes) {
                    Log.w(TAG, "Content-Range start mismatch: expected $existingBytes, got $startByte. Restarting from byte 0.")
                    shouldRestartFromZero = true
                }
            }
        }

        if (shouldRestartFromZero) {
            activeResponse.close()
            tempFile.delete()
            metaFile.delete()
            existingBytes = 0L
            val cleanRequest = Request.Builder()
                .url(mediaUrl)
                .header("Accept", "*/*")
                .apply {
                    if (!referer.isNullOrBlank()) header("Referer", referer)
                    headers?.forEach { (k, v) ->
                        if (!k.equals("Range", ignoreCase = true) && !k.equals("If-Range", ignoreCase = true)) {
                            header(k, v)
                        }
                    }
                }
                .build()
            activeResponse = client.newCall(cleanRequest).execute()
        }

            processActiveDownloadResponse(
                activeResponse = activeResponse,
                context = context,
                mediaUrl = mediaUrl,
                fileName = fileName,
                tempFile = tempFile,
                metaFile = metaFile,
                finalFile = finalFile,
                existingBytes = existingBytes,
                onProgress = onProgress
            )
        } catch (e: Exception) {
            Log.e(TAG, "Download exception for $mediaUrl", e)
            OkHttpDownloader.DownloadResult(
                file = null,
                isSuccessful = false,
                statusCode = -1,
                bytesDownloaded = existingBytes,
                totalBytes = -1L,
                errorMessage = e.message ?: "Network error during download"
            )
        }
    }
