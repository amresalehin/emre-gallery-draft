package com.amresalehin.emregallery.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.StatFs
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Production face detection and recognition engine using InsightFace buffalo_l models:
 * - SCRFD-10GF for high-recall face detection and 5-point landmark localization.
 * - W600K_R50 ArcFace for 512-dimensional normalized embeddings.
 */
class BuffaloLFaceEngine internal constructor(
    internal val context: Context,
    internal val detectorSession: OrtSession,
    internal val recognitionSession: OrtSession,
    internal val detectionThreshold: Float
) : FaceEngine {

    override val engineId: String get() = ENGINE_ID
    override val modelVersion: String get() = MODEL_VERSION
    override val embeddingDimension: Int get() = EMBEDDING_DIMENSION
    override val formatVersion: Int get() = FORMAT_VERSION

    internal data class FaceDetection(val bounds: RectF, val landmarks: FloatArray, val score: Float)

    private data class DecodedImage(val bitmap: Bitmap, val origW: Int, val origH: Int)
    @Volatile private var cachedDecoded: Pair<Uri, DecodedImage>? = null

    private fun getOrDecode(uri: Uri): DecodedImage? {
        synchronized(this) {
            val cached = cachedDecoded
            if (cached != null && cached.first == uri && !cached.second.bitmap.isRecycled) {
                return cached.second
            }
        }
        val img = decodeOrientedImage(uri) ?: return null
        synchronized(this) {
            cachedDecoded?.second?.bitmap?.let { if (!it.isRecycled) it.recycle() }
            cachedDecoded = uri to img
        }
        return img
    }

    private fun releaseDecoded(uri: Uri) {
        synchronized(this) {
            val cached = cachedDecoded
            if (cached != null && cached.first == uri) {
                if (!cached.second.bitmap.isRecycled) cached.second.bitmap.recycle()
                cachedDecoded = null
            }
        }
    }

    override fun detectFaces(uri: Uri): List<RawFaceDetection> {
        val decoded = getOrDecode(uri)
            ?: error("Could not decode image at URI: $uri")
        val detections = detectScrfdFaces(decoded.bitmap)
        val scaleX = decoded.origW.toFloat() / decoded.bitmap.width.toFloat().coerceAtLeast(1f)
        val scaleY = decoded.origH.toFloat() / decoded.bitmap.height.toFloat().coerceAtLeast(1f)

        val filtered = detections.filter {
            val w = it.bounds.width() * scaleX
            val h = it.bounds.height() * scaleY
            w >= MIN_FACE_SIZE && h >= MIN_FACE_SIZE
        }

        return filtered.mapIndexed { index, face ->
            val origBounds = RectF(
                face.bounds.left * scaleX,
                face.bounds.top * scaleY,
                face.bounds.right * scaleX,
                face.bounds.bottom * scaleY
            )
            val origLandmarks = FloatArray(face.landmarks.size)
            for (k in 0 until face.landmarks.size / 2) {
                origLandmarks[k * 2] = face.landmarks[k * 2] * scaleX
                origLandmarks[k * 2 + 1] = face.landmarks[k * 2 + 1] * scaleY
            }
            RawFaceDetection(index, origBounds, origLandmarks, face.score)
        }
    }

    override fun embedFaces(uri: Uri, detections: List<RawFaceDetection>): List<DetectedFaceEmbedding> {
        if (detections.isEmpty()) {
            releaseDecoded(uri)
            return emptyList()
        }
        val decoded = getOrDecode(uri)
            ?: error("Could not decode image at URI: $uri")
        return try {
            val bitmap = decoded.bitmap
            val scaleDownX = bitmap.width.toFloat() / decoded.origW.toFloat().coerceAtLeast(1f)
            val scaleDownY = bitmap.height.toFloat() / decoded.origH.toFloat().coerceAtLeast(1f)

            detections.mapIndexedNotNull { _, raw ->
                if (raw.bounds.width() < MIN_FACE_SIZE || raw.bounds.height() < MIN_FACE_SIZE) {
                    return@mapIndexedNotNull null
                }
                val localBounds = RectF(
                    raw.bounds.left * scaleDownX,
                    raw.bounds.top * scaleDownY,
                    raw.bounds.right * scaleDownX,
                    raw.bounds.bottom * scaleDownY
                )
                val rawLm = raw.landmarks
                val localLandmarks = if (rawLm != null && rawLm.size >= 10) {
                    FloatArray(rawLm.size) { i ->
                        if (i % 2 == 0) rawLm[i] * scaleDownX else rawLm[i] * scaleDownY
                    }
                } else {
                    floatArrayOf(
                        localBounds.left + localBounds.width() * 0.32f, localBounds.top + localBounds.height() * 0.38f,
                        localBounds.left + localBounds.width() * 0.68f, localBounds.top + localBounds.height() * 0.38f,
                        localBounds.centerX(), localBounds.top + localBounds.height() * 0.56f,
                        localBounds.left + localBounds.width() * 0.38f, localBounds.top + localBounds.height() * 0.73f,
                        localBounds.left + localBounds.width() * 0.62f, localBounds.top + localBounds.height() * 0.73f
                    )
                }

                val aligned = EnteFaceAligner.align(bitmap, localLandmarks, localBounds)
                try {
                    val embedding = inferEmbedding(aligned)
                    val laplacian = FaceQualityMetrics.calculateLaplacianVariance(aligned)
                    val metrics = FaceQualityMetrics.compute(
                        boundsWidth = raw.bounds.width(),
                        boundsHeight = raw.bounds.height(),
                        imageWidth = decoded.origW,
                        imageHeight = decoded.origH,
                        confidence = raw.score,
                        landmarks = raw.landmarks,
                        laplacianVariance = laplacian
                    )
                    DetectedFaceEmbedding(raw.faceIndex, RectF(raw.bounds), embedding, raw.score, metrics.overallQuality, metrics)
                } finally {
                    aligned.recycle()
                }
            }
        } finally {
            releaseDecoded(uri)
        }
    }

    override fun embed(uri: Uri): List<DetectedFaceEmbedding> {
        val detections = detectFaces(uri)
        if (detections.isEmpty()) {
            releaseDecoded(uri)
            return emptyList()
        }
        return embedFaces(uri, detections)
    }

    override fun embedBitmap(bitmap: Bitmap): List<DetectedFaceEmbedding> {
        val detections = detectScrfdFaces(bitmap)
        val valid = detections.filter { it.bounds.width() >= MIN_FACE_SIZE && it.bounds.height() >= MIN_FACE_SIZE }
        return valid.mapIndexed { index, face ->
            val aligned = EnteFaceAligner.align(bitmap, face.landmarks, face.bounds)
            try {
                val embedding = inferEmbedding(aligned)
                val laplacian = FaceQualityMetrics.calculateLaplacianVariance(aligned)
                val metrics = FaceQualityMetrics.compute(
                    boundsWidth = face.bounds.width(),
                    boundsHeight = face.bounds.height(),
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    confidence = face.score,
                    landmarks = face.landmarks,
                    laplacianVariance = laplacian
                )
                DetectedFaceEmbedding(index, RectF(face.bounds), embedding, face.score, metrics.overallQuality, metrics)
            } finally {
                aligned.recycle()
            }
        }
    }

    private fun decodeOrientedImage(uri: Uri, maxDimension: Int = 1600): DecodedImage? {
        val result = FaceImageDecoder.decodeOrientedImage(context, uri, maxDimension) ?: return null
        return DecodedImage(result.bitmap, result.originalWidth, result.originalHeight)
    }


    override fun close() {
        synchronized(this) {
            cachedDecoded?.second?.bitmap?.let { if (!it.isRecycled) it.recycle() }
            cachedDecoded = null
        }
        runCatching { detectorSession.close() }
        runCatching { recognitionSession.close() }
    }


    companion object {
        const val ENGINE_ID = "buffalo_l"
        const val MODEL_VERSION = "w600k_r50"
        const val EMBEDDING_DIMENSION = 512
        const val FORMAT_VERSION = 1

        internal const val DETECT_SIZE = 640
        internal const val RECOGNITION_SIZE = 112
        internal const val MIN_FACE_SIZE = 28
        internal const val NMS_THRESHOLD = 0.4f
        internal const val MAX_FACES = 100
        internal const val MIN_FREE_SPACE_BYTES = 60L * 1024 * 1024
        internal val STRIDES = intArrayOf(8, 16, 32)
        internal const val MODEL_URL =
            "https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_l.zip"
        internal val REQUIRED_MODELS = listOf(
            "det_10g.onnx", "w600k_r50.onnx"
        )

        fun isModelInstalled(context: Context): Boolean =
            BuffaloLModelInstaller.isModelInstalled(context)

        @Synchronized
        fun installBundledModel(context: Context) =
            BuffaloLModelInstaller.installBundledModel(context)

        suspend fun downloadModel(
            context: Context,
            force: Boolean = false,
            onProgress: (Long, Long) -> Unit = { _, _ -> },
            onStage: (String) -> Unit = {},
            awaitIfResumed: suspend () -> Unit = {},
            onDetailedProgress: (DetailedDownloadProgress) -> Unit = {}
        ) = BuffaloLModelInstaller.downloadModel(
            context, force, onProgress, onStage, awaitIfResumed, onDetailedProgress
        )

        fun open(
            context: Context,
            detectionThreshold: Float = FaceRecognitionSettings.DEFAULT_DETECTION_CONFIDENCE
        ): BuffaloLFaceEngine = BuffaloLModelInstaller.open(context, detectionThreshold)
    }
}
