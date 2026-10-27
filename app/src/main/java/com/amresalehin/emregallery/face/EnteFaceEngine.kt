package com.amresalehin.emregallery.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

/**
 * Production-ready Ente-style Face Engine:
 * - Detector: YOLO5Face (yolov5n-face) with 5 facial keypoints.
 * - Aligner: 5-point similarity transform face alignment to 112x112 canonical template.
 * - Embedder: MobileFaceNet with dimension validation and L2 normalization.
 * - Quality: typed, versioned FaceQualityMetrics (blur, pose, size, confidence).
 */
class EnteFaceEngine private constructor(
    private val context: Context,
    private val detectorSession: OrtSession,
    private val embedderSession: OrtSession,
    private val detectionThreshold: Float = EnteFaceConstants.DEFAULT_DETECTION_CONFIDENCE
) : FaceEngine {

    override val engineId: String get() = EnteFaceConstants.ENGINE_ID
    override val modelVersion: String get() = EnteFaceConstants.MODEL_VERSION
    override val embeddingDimension: Int get() = embedder.actualDimension
    override val formatVersion: Int get() = EnteFaceConstants.FORMAT_VERSION

    private val detector = EnteFaceDetector(detectorSession, detectionThreshold)
    private val embedder = EnteFaceEmbedder(embedderSession, EnteFaceConstants.EMBEDDING_DIMENSION)

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
        val detections = detector.detect(decoded.bitmap)
        val scaleX = decoded.origW.toFloat() / decoded.bitmap.width.toFloat().coerceAtLeast(1f)
        val scaleY = decoded.origH.toFloat() / decoded.bitmap.height.toFloat().coerceAtLeast(1f)

        val filtered = detections.filter {
            val w = it.bounds.width() * scaleX
            val h = it.bounds.height() * scaleY
            w >= EnteFaceConstants.MIN_FACE_SIZE && h >= EnteFaceConstants.MIN_FACE_SIZE
        }

        return filtered.mapIndexed { index, det ->
            val origBounds = RectF(
                det.bounds.left * scaleX,
                det.bounds.top * scaleY,
                det.bounds.right * scaleX,
                det.bounds.bottom * scaleY
            )
            val origLandmarks = FloatArray(det.landmarks.size)
            for (k in 0 until det.landmarks.size / 2) {
                origLandmarks[k * 2] = det.landmarks[k * 2] * scaleX
                origLandmarks[k * 2 + 1] = det.landmarks[k * 2 + 1] * scaleY
            }
            RawFaceDetection(index, origBounds, origLandmarks, det.score)
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

            val results = ArrayList<DetectedFaceEmbedding>()
            for (det in detections) {
                if (det.bounds.width() < EnteFaceConstants.MIN_FACE_SIZE ||
                    det.bounds.height() < EnteFaceConstants.MIN_FACE_SIZE
                ) {
                    continue
                }
                val localBounds = RectF(
                    det.bounds.left * scaleDownX,
                    det.bounds.top * scaleDownY,
                    det.bounds.right * scaleDownX,
                    det.bounds.bottom * scaleDownY
                )
                val rawLm = det.landmarks
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

                val alignedFace = EnteFaceAligner.align(bitmap, localLandmarks, localBounds)
                try {
                    val embedding = embedder.infer(alignedFace)
                    val laplacian = FaceQualityMetrics.calculateLaplacianVariance(alignedFace)
                    val metrics = FaceQualityMetrics.compute(
                        boundsWidth = det.bounds.width(),
                        boundsHeight = det.bounds.height(),
                        imageWidth = decoded.origW,
                        imageHeight = decoded.origH,
                        confidence = det.score,
                        landmarks = det.landmarks,
                        laplacianVariance = laplacian
                    )

                    results.add(
                        DetectedFaceEmbedding(
                            faceIndex = det.faceIndex,
                            bounds = RectF(det.bounds),
                            embedding = embedding,
                            score = det.score,
                            quality = metrics.overallQuality,
                            qualityMetrics = metrics
                        )
                    )
                } finally {
                    alignedFace.recycle()
                }
            }
            results
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
        val detections = detector.detect(bitmap)
        val results = ArrayList<DetectedFaceEmbedding>()

        val valid = detections.filter {
            it.bounds.width() >= EnteFaceConstants.MIN_FACE_SIZE &&
            it.bounds.height() >= EnteFaceConstants.MIN_FACE_SIZE
        }

        for ((index, det) in valid.withIndex()) {
            val alignedFace = EnteFaceAligner.align(bitmap, det.landmarks, det.bounds)
            try {
                val embedding = embedder.infer(alignedFace)
                val laplacian = FaceQualityMetrics.calculateLaplacianVariance(alignedFace)
                val metrics = FaceQualityMetrics.compute(
                    boundsWidth = det.bounds.width(),
                    boundsHeight = det.bounds.height(),
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    confidence = det.score,
                    landmarks = det.landmarks,
                    laplacianVariance = laplacian
                )

                results.add(
                    DetectedFaceEmbedding(
                        faceIndex = index,
                        bounds = RectF(det.bounds),
                        embedding = embedding,
                        score = det.score,
                        quality = metrics.overallQuality,
                        qualityMetrics = metrics
                    )
                )
            } finally {
                alignedFace.recycle()
            }
        }
        return results
    }

    private fun decodeOrientedImage(uri: Uri, maxDimension: Int = 1600): DecodedImage? {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOptions)
        } ?: return null

        val rawW = boundsOptions.outWidth
        val rawH = boundsOptions.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        val isTransposed = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val origW = if (isTransposed) rawH else rawW
        val origH = if (isTransposed) rawW else rawH

        var inSampleSize = 1
        while ((rawW / inSampleSize) > maxDimension || (rawH / inSampleSize) > maxDimension) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
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
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }

        return DecodedImage(oriented, origW, origH)
    }

    override fun close() {
        synchronized(this) {
            cachedDecoded?.second?.bitmap?.let { if (!it.isRecycled) it.recycle() }
            cachedDecoded = null
        }
        runCatching { detectorSession.close() }
        runCatching { embedderSession.close() }
    }

    companion object {
        fun open(
            context: Context,
            detectionThreshold: Float = EnteFaceConstants.DEFAULT_DETECTION_CONFIDENCE
        ): EnteFaceEngine {
            if (!EnteFaceModelManager.isInstalled(context)) {
                EnteFaceModelManager.installFromAssetsIfPresent(context)
            }
            check(EnteFaceModelManager.isInstalled(context)) {
                "Ente face models are not installed. Please download or install them first."
            }

            val modelDir = EnteFaceModelManager.getModelDir(context)
            val detectorFile = File(modelDir, EnteFaceConstants.DETECTOR_FILENAME)
            val embedderFile = File(modelDir, EnteFaceConstants.EMBEDDER_FILENAME)

            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }

            val env = OrtEnvironment.getEnvironment()
            val detectorSession = env.createSession(detectorFile.absolutePath, sessionOptions)
            try {
                val embedderSession = env.createSession(embedderFile.absolutePath, sessionOptions)
                return EnteFaceEngine(
                    context.applicationContext,
                    detectorSession,
                    embedderSession,
                    detectionThreshold
                )
            } catch (t: Throwable) {
                detectorSession.close()
                throw t
            }
        }
    }
}
