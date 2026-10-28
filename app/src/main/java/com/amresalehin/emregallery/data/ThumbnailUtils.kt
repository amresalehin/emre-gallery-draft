package com.amresalehin.emregallery.data

import android.content.Context
import android.net.Uri
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision

/**
 * Standard thumbnail size in pixels. 360x360 matches 3-column 1080p phone displays pixel-perfectly
 * while consuming minimal RAM (under 260KB per image with RGB_565/Hardware bitmap).
 */
const val STANDARD_THUMB_SIZE_PX = 360

/**
 * Canonical cache key for thumbnails across all screens in the gallery.
 * Ensures 100% memory and disk cache sharing between Home grid, Albums, Search,
 * Favourites, Bin, Archive, and the Viewer placeholder.
 */
fun thumbnailCacheKey(uri: String): String = "${uri}_thumb"

/**
 * Builds an optimized, low-RAM thumbnail request using the standardized cache key and dimensions.
 */
fun buildThumbnailRequest(context: Context, uri: String): ImageRequest {
    return ImageRequest.Builder(context)
        .data(Uri.parse(uri))
        .size(STANDARD_THUMB_SIZE_PX, STANDARD_THUMB_SIZE_PX)
        .precision(Precision.INEXACT)
        .crossfade(false)
        .diskCacheKey(thumbnailCacheKey(uri))
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCacheKey(thumbnailCacheKey(uri))
        .memoryCachePolicy(CachePolicy.ENABLED)
        .allowRgb565(true)
        .allowHardware(true)
        .build()
}
