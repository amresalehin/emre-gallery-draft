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
    private const val CROP_CACHE_BYTES = 16 * 1024 * 1024
    private const val BASE_CACHE_BYTES = 32 * 1024 * 1024
    private val memoryCache = object : LruCache<String, Bitmap>(CROP_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private data class CachedBaseImage(val bitmap: Bitmap, val originalWidth: Int, val originalHeight: Int)
    private val baseImageCache = object : LruCache<String, CachedBaseImage>(BASE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: CachedBaseImage): Int = value.bitmap.byteCount
    }

    @Synchronized
    fun clearCaches() { memoryCache.evictAll(); baseImageCache.evictAll() }

    fun getFaceCrop(context: Context, uri: Uri, faceIndex: Int, engineId: String = BuffaloLFaceEngine.ENGINE_ID, providedBounds: RectF? = null): Bitmap? {
        return runCatching {
            val bounds = providedBounds ?: FaceClusterStore(context.applicationContext).getFaceBounds(uri.toString(), faceIndex, engineId)
            if (bounds == null || bounds.width() <= 10f || bounds.height() <= 10f) return null
            val cacheKey = listOf(engineId, uri.toString(), faceIndex.toString(), bounds.left.toBits(), bounds.top.toBits(), bounds.right.toBits(), bounds.bottom.toBits()).joinToString(":")
            synchronized(memoryCache) { memoryCache.get(cacheKey) }?.let { return it }
            val cachedBase = getOrDecodeBaseImage(context, uri) ?: return null
            val baseBitmap = cachedBase.bitmap
            val scaleX = baseBitmap.width.toFloat() / cachedBase.originalWidth.toFloat().coerceAtLeast(1f)
            val scaleY = baseBitmap.height.toFloat() / cachedBase.originalHeight.toFloat().coerceAtLeast(1f)
            val scaledBounds = RectF(bounds.left * scaleX, bounds.top * scaleY, bounds.right * scaleX, bounds.bottom * scaleY)
            val left = (scaledBounds.left - scaledBounds.width() * 0.15f).coerceIn(0f, baseBitmap.width.toFloat())
            val top = (scaledBounds.top - scaledBounds.height() * 0.15f).coerceIn(0f, baseBitmap.height.toFloat())
            val right = (scaledBounds.right + scaledBounds.width() * 0.15f).coerceIn(0f, baseBitmap.width.toFloat())
            val bottom = (scaledBounds.bottom + scaledBounds.height() * 0.15f).coerceIn(0f, baseBitmap.height.toFloat())
            val cropWidth = (right - left).toInt().coerceIn(1, baseBitmap.width)
            val cropHeight = (bottom - top).toInt().coerceIn(1, baseBitmap.height)
            val cropX = left.toInt().coerceIn(0, (baseBitmap.width - cropWidth).coerceAtLeast(0))
            val cropY = top.toInt().coerceIn(0, (baseBitmap.height - cropHeight).coerceAtLeast(0))
            val crop = Bitmap.createBitmap(baseBitmap, cropX, cropY, cropWidth, cropHeight)
            val scaled = Bitmap.createScaledBitmap(crop, 128, 128, true)
            if (scaled !== crop) crop.recycle()
            synchronized(memoryCache) { memoryCache.put(cacheKey, scaled) }
            scaled
        }.getOrNull()
    }

    private fun getOrDecodeBaseImage(context: Context, uri: Uri): CachedBaseImage? {
        val uriKey = uri.toString()
        synchronized(baseImageCache) { baseImageCache.get(uriKey)?.let { return it } }
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) } ?: return null
        val rawW = boundsOptions.outWidth
        val rawH = boundsOptions.outHeight
        if (rawW <= 0 || rawH <= 0) return null
        val isTransposed = orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270 || orientation == ExifInterface.ORIENTATION_TRANSPOSE || orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val originalW = if (isTransposed) rawH else rawW
        val originalH = if (isTransposed) rawW else rawH
        var inSampleSize = 1
        while ((rawW / inSampleSize) > 1280 || (rawH / inSampleSize) > 1280) inSampleSize *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { this.inSampleSize = inSampleSize; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: return null
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }
        val oriented = if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
        } else decoded
        return CachedBaseImage(oriented, originalW, originalH).also { synchronized(baseImageCache) { baseImageCache.put(uriKey, it) } }
    }
}
