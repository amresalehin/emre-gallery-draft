package com.amresalehin.emregallery.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.media.FaceDetector
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.sqrt

/**
 * On-device Android platform face detector and feature embedder.
 * Runs instantly without requiring external network downloads or large asset models.
 */
class AndroidNativeFaceEngine(private val context: Context) : FaceEngine {

    companion object {
        private const val MAX_FACES = 32
        private const val DETECT_WIDTH = 640
        private const val EMBEDDING_SIZE = 128
    }

    override fun embed(uri: Uri): List<DetectedFaceEmbedding> {
        val original = decodeOrientedBitmap(uri) ?: return emptyList()
        try {
            // Android FaceDetector requires even width and RGB_565 configuration
            val scale = if (original.width > DETECT_WIDTH) DETECT_WIDTH.toFloat() / original.width else 1f
            var targetWidth = (original.width * scale).toInt()
            if (targetWidth % 2 != 0) targetWidth -= 1
            var targetHeight = (original.height * scale).toInt()
            if (targetHeight <= 0) targetHeight = 2

            val scaled = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
            val rgb565 = scaled.copy(Bitmap.Config.RGB_565, false)
            if (scaled !== original) scaled.recycle()

            val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
            val detector = FaceDetector(rgb565.width, rgb565.height, MAX_FACES)
            val faceCount = detector.findFaces(rgb565, faces)
            rgb565.recycle()

            if (faceCount <= 0) return emptyList()

            val results = mutableListOf<DetectedFaceEmbedding>()
            val midPoint = PointF()
            val invScale = 1f / scale

            for (i in 0 until faceCount) {
                val face = faces[i] ?: continue
                if (face.confidence() < 0.40f) continue
                face.getMidPoint(midPoint)
                val eyeDistance = face.eyesDistance()
                if (eyeDistance < 8f) continue

                // Construct face bounds around eyes and face structure
                val left = ((midPoint.x - eyeDistance * 1.4f) * invScale).coerceIn(0f, original.width.toFloat())
                val top = ((midPoint.y - eyeDistance * 1.4f) * invScale).coerceIn(0f, original.height.toFloat())
                val right = ((midPoint.x + eyeDistance * 1.4f) * invScale).coerceIn(0f, original.width.toFloat())
                val bottom = ((midPoint.y + eyeDistance * 1.8f) * invScale).coerceIn(0f, original.height.toFloat())
                val bounds = RectF(left, top, right, bottom)
                if (bounds.width() < 20f || bounds.height() < 20f) continue

                val cropWidth = bounds.width().toInt().coerceIn(1, original.width)
                val cropHeight = bounds.height().toInt().coerceIn(1, original.height)
                val cropX = bounds.left.toInt().coerceIn(0, original.width - cropWidth)
                val cropY = bounds.top.toInt().coerceIn(0, original.height - cropHeight)

                val faceCrop = Bitmap.createBitmap(original, cropX, cropY, cropWidth, cropHeight)
                val embedding = extractFaceFeatureVector(faceCrop)
                faceCrop.recycle()

                results.add(DetectedFaceEmbedding(faceIndex = i, bounds = bounds, embedding = embedding))
            }
            return results
        } finally {
            original.recycle()
        }
    }

    private fun extractFaceFeatureVector(crop: Bitmap): FloatArray {
        // Standardize face to canonical 64x64 grid
        val standard = Bitmap.createScaledBitmap(crop, 64, 64, true)
        val vector = FloatArray(EMBEDDING_SIZE)
        try {
            // Extract multi-scale spatial luminance and color distribution
            val blockSize = 8
            var idx = 0
            for (by in 0 until 8) {
                for (bx in 0 until 8) {
                    var sumL = 0f
                    var sumR = 0f
                    var sumB = 0f
                    for (y in 0 until blockSize) {
                        for (x in 0 until blockSize) {
                            val pixel = standard.getPixel(bx * blockSize + x, by * blockSize + y)
                            val r = Color.red(pixel) / 255f
                            val g = Color.green(pixel) / 255f
                            val b = Color.blue(pixel) / 255f
                            sumL += (0.299f * r + 0.587f * g + 0.114f * b)
                            sumR += r
                            sumB += b
                        }
                    }
                    val area = (blockSize * blockSize).toFloat()
                    if (idx < 64) {
                        vector[idx] = sumL / area
                    }
                    if (idx + 64 < EMBEDDING_SIZE) {
                        vector[idx + 64] = ((sumR - sumB) / area)
                    }
                    idx++
                }
            }

            // L2 normalize vector so cosine similarity works reliably in FaceClusterStore
            var sumSq = 0.0
            for (v in vector) sumSq += v * v
            val norm = sqrt(sumSq).toFloat().coerceAtLeast(1e-9f)
            for (i in vector.indices) {
                vector[i] /= norm
            }
            return vector
        } finally {
            if (standard !== crop) standard.recycle()
        }
    }

    private fun decodeOrientedBitmap(uri: Uri): Bitmap? {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return decoded
        }
        return runCatching {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }.getOrElse { decoded }
    }

    override fun close() {}
}
