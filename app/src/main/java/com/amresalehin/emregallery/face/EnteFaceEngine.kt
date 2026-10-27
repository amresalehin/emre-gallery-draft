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

    override fun detectFaces(uri: Uri): List<RawFaceDetection> {
        val bitmap = decodeOrientedBitmap(uri)
            ?: error("Could not decode image at URI: $uri")
        return try {
            val detections = detector.detect(bitmap)
            detections.mapIndexedNotNull { index, det ->
                if (det.bounds.width() < EnteFaceConstants.MIN_FACE_SIZE ||
                    det.bounds.height() < EnteFaceConstants.MIN_FACE_SIZE
                ) {
                    null
                } else {
                    RawFaceDetection(index, RectF(det.bounds), det.landmarks, det.score)
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    override fun embedFaces(uri: Uri, detections: List<RawFaceDetection>): List<DetectedFaceEmbedding> {
        if (detections.isEmpty()) return emptyList()
        val bitmap = decodeOrientedBitmap(uri)
            ?: error("Could not decode image at URI: $uri")
        return try {
            val results = ArrayList<DetectedFaceEmbedding>()
            for (det in detections) {
                if (det.bounds.width() < EnteFaceConstants.MIN_FACE_SIZE ||
                    det.bounds.height() < EnteFaceConstants.MIN_FACE_SIZE
                ) {
                    continue
                }
                val landmarks = det.landmarks ?: floatArrayOf(
                    det.bounds.left + det.bounds.width() * 0.32f, det.bounds.top + det.bounds.height() * 0.38f,
                    det.bounds.left + det.bounds.width() * 0.68f, det.bounds.top + det.bounds.height() * 0.38f,
                    det.bounds.centerX(), det.bounds.top + det.bounds.height() * 0.56f,
                    det.bounds.left + det.bounds.width() * 0.38f, det.bounds.top + det.bounds.height() * 0.73f,
                    det.bounds.left + det.bounds.width() * 0.62f, det.bounds.top + det.bounds.height() * 0.73f
                )
                val alignedFace = EnteFaceAligner.align(bitmap, landmarks, det.bounds)
                try {
                    val embedding = embedder.infer(alignedFace)
                    val metrics = FaceQualityMetrics.compute(
                        boundsWidth = det.bounds.width(),
                        boundsHeight = det.bounds.height(),
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        confidence = det.score,
                        landmarks = landmarks
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
            bitmap.recycle()
        }
    }

    override fun embed(uri: Uri): List<DetectedFaceEmbedding> {
        val detections = detectFaces(uri)
        if (detections.isEmpty()) return emptyList()
        return embedFaces(uri, detections)
    }

    override fun embedBitmap(bitmap: Bitmap): List<DetectedFaceEmbedding> {
        val detections = detector.detect(bitmap)
        val results = ArrayList<DetectedFaceEmbedding>()

        for ((index, det) in detections.withIndex()) {
            if (det.bounds.width() < EnteFaceConstants.MIN_FACE_SIZE ||
                det.bounds.height() < EnteFaceConstants.MIN_FACE_SIZE
            ) {
                continue
            }

            val alignedFace = EnteFaceAligner.align(bitmap, det.landmarks, det.bounds)
            try {
                val embedding = embedder.infer(alignedFace)
                val metrics = FaceQualityMetrics.compute(
                    boundsWidth = det.bounds.width(),
                    boundsHeight = det.bounds.height(),
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    confidence = det.score,
                    landmarks = det.landmarks
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

    private fun decodeOrientedBitmap(uri: Uri): Bitmap? {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
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
            else -> return decoded
        }
        return runCatching {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }.getOrElse { decoded }
    }

    override fun close() {
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
