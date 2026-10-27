package com.amresalehin.emregallery.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface

object FaceThumbnailHelper {
    private val memoryCache = LruCache<String, Bitmap>(100)

    // Base downsampled image cache to avoid repeated expensive full-res decodes for group photos
    private data class CachedBaseImage(val bitmap: Bitmap, val originalWidth: Int, val originalHeight: Int)
    private val baseImageCache = LruCache<String, CachedBaseImage>(8)

    fun getFaceCrop(
        context: Context,
        uri: Uri,
        faceIndex: Int,
        engineId: String = BuffaloLFaceEngine.ENGINE_ID,
        providedBounds: RectF? = null
    ): Bitmap? {
        val cacheKey = "${engineId}_${uri}_$faceIndex"
        memoryCache.get(cacheKey)?.let { return it }

        return runCatching {
            val store = FaceClusterStore(context.applicationContext)
            val bounds = providedBounds ?: store.getFaceBounds(uri.toString(), faceIndex, engineId)

            val cachedBase = getOrDecodeBaseImage(context, uri) ?: return null
            val baseBitmap = cachedBase.bitmap
            val origW = cachedBase.originalWidth
            val origH = cachedBase.originalHeight

            val crop = if (bounds != null && bounds.width() > 10f && bounds.height() > 10f) {
                val scaleX = baseBitmap.width.toFloat() / origW.toFloat().coerceAtLeast(1f)
                val scaleY = baseBitmap.height.toFloat() / origH.toFloat().coerceAtLeast(1f)

                val scaledBounds = RectF(
                    bounds.left * scaleX,
                    bounds.top * scaleY,
                    bounds.right * scaleX,
                    bounds.bottom * scaleY
                )

                // Expand bounds slightly for better framing
                val marginX = scaledBounds.width() * 0.15f
                val marginY = scaledBounds.height() * 0.15f
                val left = (scaledBounds.left - marginX).coerceIn(0f, baseBitmap.width.toFloat())
                val top = (scaledBounds.top - marginY).coerceIn(0f, baseBitmap.height.toFloat())
                val right = (scaledBounds.right + marginX).coerceIn(0f, baseBitmap.width.toFloat())
                val bottom = (scaledBounds.bottom + marginY).coerceIn(0f, baseBitmap.height.toFloat())

                val cropWidth = (right - left).toInt().coerceIn(1, baseBitmap.width)
                val cropHeight = (bottom - top).toInt().coerceIn(1, baseBitmap.height)
                val cropX = left.toInt().coerceIn(0, (baseBitmap.width - cropWidth).coerceAtLeast(0))
                val cropY = top.toInt().coerceIn(0, (baseBitmap.height - cropHeight).coerceAtLeast(0))

                Bitmap.createBitmap(baseBitmap, cropX, cropY, cropWidth, cropHeight)
            } else {
                // Fallback to square center crop
                val size = minOf(baseBitmap.width, baseBitmap.height)
                val cropX = ((baseBitmap.width - size) / 2).coerceAtLeast(0)
                val cropY = ((baseBitmap.height - size) / 2).coerceAtLeast(0)
                Bitmap.createBitmap(baseBitmap, cropX, cropY, size, size)
            }

            val scaled = Bitmap.createScaledBitmap(crop, 128, 128, true)
            if (scaled !== crop) crop.recycle()
            memoryCache.put(cacheKey, scaled)
            scaled
        }.getOrNull()
    }

    private fun getOrDecodeBaseImage(context: Context, uri: Uri): CachedBaseImage? {
        val uriKey = uri.toString()
        synchronized(baseImageCache) {
            baseImageCache.get(uriKey)?.let { return it }
        }

        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        // 1. First pass: decode dimensions only to calculate downsampling
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOptions)
        } ?: return null

        val rawW = boundsOptions.outWidth
        val rawH = boundsOptions.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        // Account for EXIF rotation in original width/height
        val isTransposed = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270
        val originalW = if (isTransposed) rawH else rawW
        val originalH = if (isTransposed) rawW else rawH

        // Downsample to max dimension 1280 to save memory and avoid high allocation pressure
        var inSampleSize = 1
        while ((rawW / inSampleSize) > 1280 || (rawH / inSampleSize) > 1280) {
            inSampleSize *= 2
        }

        // 2. Second pass: decode downsampled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        val oriented = if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }

        val result = CachedBaseImage(oriented, originalW, originalH)
        synchronized(baseImageCache) {
            baseImageCache.put(uriKey, result)
        }
        return result
    }
}
