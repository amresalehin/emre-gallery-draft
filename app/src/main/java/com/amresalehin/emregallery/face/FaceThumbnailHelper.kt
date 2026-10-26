package com.amresalehin.emregallery.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface

object FaceThumbnailHelper {
    private val memoryCache = LruCache<String, Bitmap>(100)

    fun getFaceCrop(context: Context, uri: Uri, faceIndex: Int): Bitmap? {
        val cacheKey = "${uri}_$faceIndex"
        memoryCache.get(cacheKey)?.let { return it }

        return runCatching {
            val store = FaceClusterStore(context.applicationContext)
            val bounds = store.getFaceBounds(uri.toString(), faceIndex)

            val original = decodeOrientedBitmap(context, uri) ?: return null
            try {
                val crop = if (bounds != null && bounds.width() > 10f && bounds.height() > 10f) {
                    // Expand bounds slightly for better framing
                    val marginX = bounds.width() * 0.15f
                    val marginY = bounds.height() * 0.15f
                    val left = (bounds.left - marginX).coerceIn(0f, original.width.toFloat())
                    val top = (bounds.top - marginY).coerceIn(0f, original.height.toFloat())
                    val right = (bounds.right + marginX).coerceIn(0f, original.width.toFloat())
                    val bottom = (bounds.bottom + marginY).coerceIn(0f, original.height.toFloat())

                    val cropWidth = (right - left).toInt().coerceIn(1, original.width)
                    val cropHeight = (bottom - top).toInt().coerceIn(1, original.height)
                    val cropX = left.toInt().coerceIn(0, (original.width - cropWidth).coerceAtLeast(0))
                    val cropY = top.toInt().coerceIn(0, (original.height - cropHeight).coerceAtLeast(0))

                    Bitmap.createBitmap(original, cropX, cropY, cropWidth, cropHeight)
                } else {
                    // Fallback to square center crop
                    val size = minOf(original.width, original.height)
                    val cropX = ((original.width - size) / 2).coerceAtLeast(0)
                    val cropY = ((original.height - size) / 2).coerceAtLeast(0)
                    Bitmap.createBitmap(original, cropX, cropY, size, size)
                }

                val scaled = Bitmap.createScaledBitmap(crop, 128, 128, true)
                if (scaled !== crop) crop.recycle()
                memoryCache.put(cacheKey, scaled)
                scaled
            } finally {
                original.recycle()
            }
        }.getOrNull()
    }

    private fun decodeOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        return if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }
    }
}
