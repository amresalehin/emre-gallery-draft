package com.amresalehin.emregallery.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface

object FaceThumbnailHelper {
    private val memoryCache = LruCache<String, Bitmap>(60)

    fun getFaceCrop(context: Context, uri: Uri, faceIndex: Int): Bitmap? {
        val cacheKey = "${uri}_$faceIndex"
        memoryCache.get(cacheKey)?.let { return it }

        return runCatching {
            val engine = AndroidNativeFaceEngine(context)
            val detections = engine.embed(uri)
            val match = detections.firstOrNull { it.faceIndex == faceIndex } ?: detections.getOrNull(faceIndex)
            val bounds = match?.bounds ?: return null

            val original = decodeOrientedBitmap(context, uri) ?: return null
            try {
                val cropWidth = bounds.width().toInt().coerceIn(1, original.width)
                val cropHeight = bounds.height().toInt().coerceIn(1, original.height)
                val cropX = bounds.left.toInt().coerceIn(0, (original.width - cropWidth).coerceAtLeast(0))
                val cropY = bounds.top.toInt().coerceIn(0, (original.height - cropHeight).coerceAtLeast(0))

                val faceCrop = Bitmap.createBitmap(original, cropX, cropY, cropWidth, cropHeight)
                val scaled = Bitmap.createScaledBitmap(faceCrop, 128, 128, true)
                if (scaled !== faceCrop) faceCrop.recycle()
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
