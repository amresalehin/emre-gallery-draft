package com.amresalehin.emregallery.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.util.zip.ZipInputStream
import kotlin.math.max

data class DetectedFaceEmbedding(
    val faceIndex: Int,
    val bounds: RectF,
    val embedding: FloatArray
)

/**
 * Offline face embedding with InsightFace buffalo_l's w600k_r50 ONNX recognizer.
 * ML Kit is used only for face boxes/landmarks; no image or embedding is sent to a server.
 *
 * The official buffalo_l pretrained pack is distributed under non-commercial research terms.
 * Download is deliberately an explicit user action and must not be triggered automatically.
 */
class BuffaloLFaceEngine private constructor(
    private val context: Context,
    private val session: OrtSession,
    private val detector: FaceDetector,
    private val packSessions: List<OrtSession>
) : AutoCloseable {

    fun embed(uri: Uri): List<DetectedFaceEmbedding> {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("Could not open image for face recognition.")
        try {
            val faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
                .sortedBy { it.boundingBox.left }
            return faces.mapIndexedNotNull { index, face ->
                if (face.boundingBox.width() < MIN_FACE_SIZE || face.boundingBox.height() < MIN_FACE_SIZE) {
                    return@mapIndexedNotNull null
                }
                val aligned = alignFace(bitmap, face)
                try {
                    DetectedFaceEmbedding(index, RectF(face.boundingBox), infer(aligned))
                } finally {
                    aligned.recycle()
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun infer(face: Bitmap): FloatArray {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        face.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        // InsightFace's ArcFace recognizer expects BGR channels normalized to [-1, 1].
        val input = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val planeSize = INPUT_SIZE * INPUT_SIZE
        for (i in pixels.indices) {
            val pixel = pixels[i]
            input[i] = (Color.blue(pixel) - 127.5f) / 127.5f
            input[planeSize + i] = (Color.green(pixel) - 127.5f) / 127.5f
            input[2 * planeSize + i] = (Color.red(pixel) - 127.5f) / 127.5f
        }
        val inputName = session.inputNames.first()
        OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(),
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        ).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val batch = result[0].value as Array<*>
                return (batch[0] as FloatArray).copyOf()
            }
        }
    }

    private fun alignFace(source: Bitmap, face: Face): Bitmap {
        val output = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val matrix = Matrix()
        if (leftEye != null && rightEye != null && nose != null) {
            val sourcePoints = floatArrayOf(
                leftEye.x, leftEye.y,
                rightEye.x, rightEye.y,
                nose.x, nose.y
            )
            // Standard ArcFace 112x112 reference landmarks.
            val targetPoints = floatArrayOf(38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f)
            if (!matrix.setPolyToPoly(sourcePoints, 0, targetPoints, 0, 3)) {
                fallbackCropMatrix(matrix, face, source.width, source.height)
            }
        } else {
            fallbackCropMatrix(matrix, face, source.width, source.height)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return output
    }

    private fun fallbackCropMatrix(matrix: Matrix, face: Face, width: Int, height: Int) {
        val box = face.boundingBox
        val side = max(box.width(), box.height()).toFloat() * 1.35f
        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        val left = (cx - side / 2f).coerceAtLeast(0f)
        val top = (cy - side / 2f).coerceAtLeast(0f)
        val right = (left + side).coerceAtMost(width.toFloat())
        val bottom = (top + side).coerceAtMost(height.toFloat())
        val cropWidth = (right - left).coerceAtLeast(1f)
        val cropHeight = (bottom - top).coerceAtLeast(1f)
        matrix.setScale(INPUT_SIZE / cropWidth, INPUT_SIZE / cropHeight)
        matrix.postTranslate(-left * INPUT_SIZE / cropWidth, -top * INPUT_SIZE / cropHeight)
    }

    override fun close() {
        runCatching { detector.close() }
        runCatching { session.close() }
        packSessions.forEach { runCatching { it.close() } }
    }

    companion object {
        private const val INPUT_SIZE = 112
        private const val MIN_FACE_SIZE = 28
        private const val MODEL_URL =
            "https://github.com/deepinsight/insightface/releases/download/model-zoo/buffalo_l.zip"
        private val REQUIRED_MODELS = listOf(
            "det_10g.onnx", "w600k_r50.onnx", "2d106det.onnx", "1k3d68.onnx", "genderage.onnx"
        )

        fun isModelInstalled(context: Context): Boolean {
            val dir = File(context.filesDir, "face-models/buffalo_l")
            return REQUIRED_MODELS.all { name ->
                dir.resolve(name).let { it.isFile && it.length() > 1024 * 1024 }
            }
        }

        /**
         * Downloads the official model pack on demand, extracts only the recognizer needed by
         * this Android pipeline, and keeps it in app-private storage. Call only after explicit
         * user consent; the source archive is large (roughly 326 MB).
         */
        fun downloadModel(context: Context, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
            val modelDir = File(context.filesDir, "face-models/buffalo_l")
            if (!modelDir.exists() && !modelDir.mkdirs()) error("Cannot create face model directory.")
            if (isModelInstalled(context)) return

            val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.instanceFollowRedirects = true
            val staging = File(modelDir, "installing").apply { deleteRecursively(); mkdirs() }
            try {
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    error("Could not download buffalo_l model pack (HTTP " + connection.responseCode + ").")
                }
                val total = connection.contentLengthLong.coerceAtLeast(-1L)
                var received = 0L
                ZipInputStream(BufferedInputStream(connection.inputStream)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name.substringAfterLast('/')
                            if (name.endsWith(".onnx") || name.equals("MODEL.LICENSE", true) || name.equals("LICENSE", true)) {
                                val outputFile = staging.resolve(name)
                                outputFile.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        val count = zip.read(buffer)
                                        if (count < 0) break
                                        output.write(buffer, 0, count)
                                        received += count
                                        onProgress(received, total)
                                    }
                                }
                            } else {
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    received += count
                                    if (received % (4L * 1024 * 1024) < buffer.size) onProgress(received, total)
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                val missing = REQUIRED_MODELS.filter { name ->
                    staging.resolve(name).let { !it.isFile || it.length() < 1024 * 1024 }
                }
                if (missing.isNotEmpty()) error("Incomplete buffalo_l archive; missing or invalid: " + missing.joinToString())
                REQUIRED_MODELS.forEach { name ->
                    val source = staging.resolve(name)
                    val target = modelDir.resolve(name)
                    if (target.exists()) target.delete()
                    if (!source.renameTo(target)) source.copyTo(target, overwrite = true)
                }
                staging.listFiles()?.filter { it.isFile && it.name.contains("LICENSE", true) }?.forEach {
                    it.copyTo(modelDir.resolve(it.name), overwrite = true)
                }
            } finally {
                connection.disconnect()
                staging.deleteRecursively()
            }
            check(isModelInstalled(context)) { "buffalo_l model pack installation failed validation." }
        }

        fun open(context: Context): BuffaloLFaceEngine {
            check(isModelInstalled(context)) {
                "The complete buffalo_l model pack is not installed. Download it from People settings first."
            }
            val modelDir = File(context.filesDir, "face-models/buffalo_l")
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }
            val env = OrtEnvironment.getEnvironment()
            val auxiliarySessions = mutableListOf<OrtSession>()
            val recognition = env.createSession(modelDir.resolve("w600k_r50.onnx").absolutePath, options)
            try {
                listOf("det_10g.onnx", "2d106det.onnx", "1k3d68.onnx", "genderage.onnx").forEach { name ->
                    auxiliarySessions += env.createSession(modelDir.resolve(name).absolutePath, options)
                }
                val detectorOptions = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .enableTracking()
                    .build()
                return BuffaloLFaceEngine(
                    context.applicationContext,
                    recognition,
                    FaceDetection.getClient(detectorOptions),
                    auxiliarySessions
                )
            } catch (error: Throwable) {
                recognition.close()
                auxiliarySessions.forEach { runCatching { it.close() } }
                throw error
            }
        }

    }
}
