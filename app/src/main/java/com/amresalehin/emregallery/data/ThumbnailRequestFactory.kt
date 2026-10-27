package com.amresalehin.emregallery.data

import android.content.Context
import android.net.Uri
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import java.security.MessageDigest

/** A normalized thumbnail output size in physical pixels. */
data class ThumbnailSize(val width: Int, val height: Int)

/**
 * Shared thumbnail sizing and cache-identity policy.
 *
 * MediaItem exposes byte size but not a reliable modified-time token. A byte-size change
 * invalidates the cache; media replaced in place with the same byte size requires a policy
 * version change or a stronger repository revision token in a future change.
 */
object ThumbnailRequestFactory {
    private const val POLICY_VERSION = 3
    private val buckets = intArrayOf(192, 320, 512, 768)

    fun sizeFor(widthPx: Int, heightPx: Int): ThumbnailSize =
        ThumbnailSize(bucket(widthPx), bucket(heightPx))

    private fun bucket(value: Int): Int {
        val target = value.coerceIn(1, buckets.last())
        return buckets.firstOrNull { it >= target } ?: buckets.last()
    }

    fun cacheKey(
        uri: Uri,
        isVideo: Boolean,
        widthPx: Int,
        heightPx: Int,
        mediaSizeBytes: Long
    ): String {
        val outputSize = sizeFor(widthPx, heightPx)
        val mediaHash = sha256(uri.toString())
        val kind = if (isVideo) "video-frame0" else "still"
        return "thumb:v" + POLICY_VERSION + ":" + kind + ":" + mediaHash + ":" +
            mediaSizeBytes.coerceAtLeast(0L) + ":" + outputSize.width + "x" + outputSize.height
    }

    fun create(
        context: Context,
        item: MediaItem,
        targetWidthPx: Int,
        targetHeightPx: Int
    ): ImageRequest {
        val size = sizeFor(targetWidthPx, targetHeightPx)
        val uri = Uri.parse(item.uri)
        val key = cacheKey(uri, item.isVideo, size.width, size.height, item.sizeBytes)
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(size.width, size.height)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .memoryCacheKey(key)
            .memoryCachePolicy(CachePolicy.ENABLED)

        if (item.isVideo) {
            // Generated video frames are stored as encoded files by VideoPosterFetcher.
            request.diskCachePolicy(CachePolicy.DISABLED)
        } else {
            request.diskCacheKey(key).diskCachePolicy(CachePolicy.ENABLED)
        }
        return request.build()
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val result = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            result.append(((byte.toInt() and 0xff) + 0x100).toString(16).substring(1))
        }
        return result.toString()
    }
}
