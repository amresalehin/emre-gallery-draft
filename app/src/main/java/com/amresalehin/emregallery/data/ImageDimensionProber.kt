package com.amresalehin.emregallery.data

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight background prober for media items with undetermined dimensions.
 * Uses OkHttp with HTTP Range 0-8191 and enforces a hard limit of 8192 bytes read from stream.
 */
object ImageDimensionProber {
    private val dimCache = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val sizeCache = ConcurrentHashMap<String, Long>()

    suspend fun probe(url: String, referer: String? = null): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val cleanUrl = if (url.startsWith("//")) "https:$url" else url
        dimCache[cleanUrl]?.let { return@withContext it }

        try {
            val reqBuilder = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", OkHttpDownloader.DEFAULT_USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Range", "bytes=0-8191")

            if (!referer.isNullOrBlank()) {
                reqBuilder.header("Referer", referer)
            } else if (cleanUrl.contains("wikimedia.org") || cleanUrl.contains("wikipedia.org")) {
                reqBuilder.header("Referer", "https://en.wikipedia.org/")
            }

            OkHttpDownloader.client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val contentRange = resp.header("Content-Range")
                    val totalSize = if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                        contentRange.substringAfterLast("/").trim().toLongOrNull()
                    } else if (resp.code == 200) {
                        resp.header("Content-Length")?.toLongOrNull()
                    } else {
                        null
                    }

                    if (totalSize != null && totalSize > 0L) {
                        sizeCache[cleanUrl] = totalSize
                    }

                    val stream = resp.body?.byteStream() ?: return@withContext null
                    val maxProbeBytes = 8192
                    val buffer = ByteArray(maxProbeBytes)
                    var totalRead = 0
                    while (totalRead < maxProbeBytes) {
                        val read = stream.read(buffer, totalRead, maxProbeBytes - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    if (totalRead > 0) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(buffer, 0, totalRead, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            val dims = Pair(opts.outWidth, opts.outHeight)
                            dimCache[cleanUrl] = dims
                            return@withContext dims
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        null
    }

    fun getCached(url: String): Pair<Int, Int>? {
        val cleanUrl = if (url.startsWith("//")) "https:$url" else url
        return dimCache[cleanUrl]
    }

    fun getCachedSize(url: String): Long? {
        val cleanUrl = if (url.startsWith("//")) "https:$url" else url
        return sizeCache[cleanUrl]
    }
}
