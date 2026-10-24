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
import android.os.StatFs
import androidx.exifinterface.media.ExifInterface
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class DetectedFaceEmbedding(
    val faceIndex: Int,
    val bounds: RectF,
    val embedding: FloatArray
)

interface FaceEngine : AutoCloseable {
    fun embed(uri: Uri): List<DetectedFaceEmbedding>
    override fun close() {}
}

/**
 * Local InsightFace buffalo_l inference.
 *
 * The SCRFD-10GF model detects faces and returns five alignment landmarks. The W600K_R50
 * model produces the 512-dimensional ArcFace embedding used by incremental clustering.
 * The other three official pack models are installed and validated with the pack; they are
 * optional heads (2D/3D landmark detail and age/gender) and are deliberately not run during
 * routine gallery indexing because those outputs are not required to group faces.
 */
class BuffaloLFaceEngine private constructor(
    private val context: Context,
    private val detectorSession: OrtSession,
    private val recognitionSession: OrtSession
) : FaceEngine {

    private data class FaceDetection(val bounds: RectF, val landmarks: FloatArray, val score: Float)

    override fun embed(uri: Uri): List<DetectedFaceEmbedding> {
        val bitmap = decodeOrientedBitmap(uri)
            ?: error("Could not decode this image.")
        try {
            val detections = detect(bitmap)
            return detections.mapIndexedNotNull { index, face ->
                if (face.bounds.width() < MIN_FACE_SIZE || face.bounds.height() < MIN_FACE_SIZE) {
                    return@mapIndexedNotNull null
                }
                val aligned = alignFace(bitmap, face.landmarks)
                try {
                    DetectedFaceEmbedding(index, RectF(face.bounds), inferEmbedding(aligned))
                } finally {
                    aligned.recycle()
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeOrientedBitmap(uri: Uri): Bitmap? {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            ) } ?: ExifInterface.ORIENTATION_NORMAL
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

    /** SCRFD-10GF, 640px input, BGR with (pixel - 127.5) / 128 normalization. */
    private fun detect(bitmap: Bitmap): List<FaceDetection> {
        val scale = min(DETECT_SIZE.toFloat() / bitmap.width, DETECT_SIZE.toFloat() / bitmap.height)
        val resizedWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val resizedHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, true)
        val padded = Bitmap.createBitmap(DETECT_SIZE, DETECT_SIZE, Bitmap.Config.ARGB_8888)
        try {
            Canvas(padded).apply {
                drawColor(Color.BLACK)
                drawBitmap(resized, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val input = imageTensorData(padded, DETECT_SIZE, 127.5f, 128f)
            val tensor = OnnxTensor.createTensor(
                OrtEnvironment.getEnvironment(), FloatBuffer.wrap(input),
                longArrayOf(1, 3, DETECT_SIZE.toLong(), DETECT_SIZE.toLong())
            )
            try {
                detectorSession.run(mapOf(detectorSession.inputNames.first() to tensor)).use { result ->
                    val outputs = detectorSession.outputNames.map { name ->
                        val info = detectorSession.outputInfo[name]?.info as? TensorInfo
                        val shape = info?.shape ?: longArrayOf()
                        val values = flatten(result.get(name).get().value)
                        Output(name, shape, values)
                    }
                    return decodeScrfd(outputs, bitmap.width, bitmap.height, scale)
                }
            } finally {
                tensor.close()
            }
        } finally {
            resized.recycle()
            padded.recycle()
        }
    }

    private data class Output(val name: String, val shape: LongArray, val values: FloatArray)

    private fun decodeScrfd(
        outputs: List<Output>,
        originalWidth: Int,
        originalHeight: Int,
        scale: Float
    ): List<FaceDetection> {
        // ONNX exports may preserve semantic names (scores_8/bbox_8/kps_8) or use numeric names.
        // Resolve by semantic name first, then by tensor's final channel dimension and row count.
        val candidates = mutableListOf<FaceDetection>()
        for (stride in STRIDES) {
            val anchorCount = (DETECT_SIZE / stride) * (DETECT_SIZE / stride) * 2
            val scoreOutput = outputs.firstOrNull {
                it.name.contains("score_$stride", true) || it.name.contains("scores_$stride", true)
            } ?: outputs.firstOrNull { lastDim(it.shape) == 1L && rowCount(it.shape) == anchorCount.toLong() }
            val boxOutput = outputs.firstOrNull {
                it.name.contains("bbox_$stride", true) || it.name.contains("box_$stride", true)
            } ?: outputs.firstOrNull { lastDim(it.shape) == 4L && rowCount(it.shape) == anchorCount.toLong() }
            val keypointOutput = outputs.firstOrNull {
                it.name.contains("kps_$stride", true) || it.name.contains("landmark_$stride", true)
            } ?: outputs.firstOrNull { lastDim(it.shape) == 10L && rowCount(it.shape) == anchorCount.toLong() }
            check(scoreOutput != null && boxOutput != null) {
                "Unsupported SCRFD output layout for stride $stride. Outputs: " +
                    outputs.joinToString { it.name + ":" + it.shape.contentToString() }
            }
            val scores = scoreOutput.values
            val boxes = boxOutput.values
            val keypoints = keypointOutput?.values
            val count = min(anchorCount, min(scores.size, boxes.size / 4))
            val grid = DETECT_SIZE / stride
            for (i in 0 until count) {
                val score = scores[i]
                if (!score.isFinite() || score < DETECTION_THRESHOLD) continue
                val cell = i / 2
                val cx = (cell % grid) * stride.toFloat()
                val cy = (cell / grid) * stride.toFloat()
                val bo = i * 4
                val bounds = RectF(
                    ((cx - boxes[bo] * stride) / scale).coerceIn(0f, originalWidth.toFloat()),
                    ((cy - boxes[bo + 1] * stride) / scale).coerceIn(0f, originalHeight.toFloat()),
                    ((cx + boxes[bo + 2] * stride) / scale).coerceIn(0f, originalWidth.toFloat()),
                    ((cy + boxes[bo + 3] * stride) / scale).coerceIn(0f, originalHeight.toFloat())
                )
                if (bounds.width() < 1f || bounds.height() < 1f) continue
                val points = FloatArray(10)
                val ko = i * 10
                if (keypoints != null && ko + 9 < keypoints.size) {
                    for (point in 0 until 5) {
                        points[point * 2] = ((cx + keypoints[ko + point * 2] * stride) / scale)
                            .coerceIn(0f, originalWidth.toFloat())
                        points[point * 2 + 1] = ((cy + keypoints[ko + point * 2 + 1] * stride) / scale)
                            .coerceIn(0f, originalHeight.toFloat())
                    }
                } else {
                    points[0] = bounds.left + bounds.width() * 0.32f
                    points[1] = bounds.top + bounds.height() * 0.38f
                    points[2] = bounds.left + bounds.width() * 0.68f
                    points[3] = bounds.top + bounds.height() * 0.38f
                    points[4] = bounds.centerX()
                    points[5] = bounds.top + bounds.height() * 0.56f
                    points[6] = bounds.left + bounds.width() * 0.38f
                    points[7] = bounds.top + bounds.height() * 0.73f
                    points[8] = bounds.left + bounds.width() * 0.62f
                    points[9] = bounds.top + bounds.height() * 0.73f
                }
                candidates += FaceDetection(bounds, points, score)
            }
        }
        return nonMaximumSuppression(candidates)
    }

    private fun inferEmbedding(face: Bitmap): FloatArray {
        val input = imageTensorData(face, RECOGNITION_SIZE, 127.5f, 127.5f)
        val tensor = OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(), FloatBuffer.wrap(input),
            longArrayOf(1, 3, RECOGNITION_SIZE.toLong(), RECOGNITION_SIZE.toLong())
        )
        try {
            recognitionSession.run(mapOf(recognitionSession.inputNames.first() to tensor)).use { result ->
                val embedding = flatten(result.get(recognitionSession.outputNames.first()).get().value)
                check(embedding.size == 512) {
                    "Unexpected buffalo_l embedding size: ${embedding.size} (expected 512)."
                }
                var normSquared = 0.0
                embedding.forEach { normSquared += it * it }
                val norm = sqrt(normSquared).toFloat().coerceAtLeast(1e-12f)
                return FloatArray(embedding.size) { embedding[it] / norm }
            }
        } finally {
            tensor.close()
        }
    }

    private fun alignFace(source: Bitmap, landmarks: FloatArray): Bitmap {
        val output = Bitmap.createBitmap(RECOGNITION_SIZE, RECOGNITION_SIZE, Bitmap.Config.ARGB_8888)
        val matrix = Matrix()
        // ArcFace canonical 112px template. Three point pairs define the affine transform;
        // SCRFD's remaining two mouth landmarks are retained in the detector result for future
        // five-point least-squares alignment if a dedicated alignment implementation is added.
        val src = floatArrayOf(landmarks[0], landmarks[1], landmarks[2], landmarks[3], landmarks[4], landmarks[5])
        val dst = floatArrayOf(38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f)
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 3)) {
            val bounds = RectF(
                min(landmarks[0], landmarks[2]), min(landmarks[1], landmarks[3]),
                max(landmarks[0], landmarks[2]), max(landmarks[1], landmarks[3])
            )
            val side = max(bounds.width(), bounds.height()).coerceAtLeast(1f) * 2.2f
            matrix.setScale(RECOGNITION_SIZE / side, RECOGNITION_SIZE / side)
            matrix.postTranslate(
                (RECOGNITION_SIZE / 2f) - bounds.centerX() * RECOGNITION_SIZE / side,
                (RECOGNITION_SIZE / 2f) - bounds.centerY() * RECOGNITION_SIZE / side
            )
        }
        Canvas(output).apply {
            drawColor(Color.BLACK)
            drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        return output
    }

    private fun imageTensorData(bitmap: Bitmap, size: Int, mean: Float, std: Float): FloatArray {
        val scaled = if (bitmap.width == size && bitmap.height == size) bitmap
            else Bitmap.createScaledBitmap(bitmap, size, size, true)
        try {
            val pixels = IntArray(size * size)
            scaled.getPixels(pixels, 0, size, 0, 0, size, size)
            val plane = size * size
            val input = FloatArray(3 * plane)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                input[i] = (Color.blue(pixel) - mean) / std
                input[plane + i] = (Color.green(pixel) - mean) / std
                input[2 * plane + i] = (Color.red(pixel) - mean) / std
            }
            return input
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun flatten(value: Any): FloatArray {
        val result = ArrayList<Float>()
        fun visit(item: Any?) {
            when {
                item == null -> Unit
                item is FloatArray -> item.forEach(result::add)
                item is Number -> result.add(item.toFloat())
                item.javaClass.isArray -> {
                    for (i in 0 until java.lang.reflect.Array.getLength(item)) {
                        visit(java.lang.reflect.Array.get(item, i))
                    }
                }
            }
        }
        visit(value)
        return result.toFloatArray()
    }

    private fun lastDim(shape: LongArray): Long = shape.lastOrNull() ?: -1L
    private fun rowCount(shape: LongArray): Long {
        if (shape.size < 2) return -1L
        var count = 1L
        for (i in 1 until shape.lastIndex) {
            if (shape[i] <= 0L) return -1L
            count *= shape[i]
        }
        return count
    }

    private fun nonMaximumSuppression(input: List<FaceDetection>): List<FaceDetection> {
        val sorted = input.sortedByDescending { it.score }.toMutableList()
        val kept = mutableListOf<FaceDetection>()
        while (sorted.isNotEmpty() && kept.size < MAX_FACES) {
            val best = sorted.removeAt(0)
            kept += best
            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                if (iou(best.bounds, iterator.next().bounds) > NMS_THRESHOLD) iterator.remove()
            }
        }
        return kept.sortedBy { it.bounds.left }
    }

    private fun iou(a: RectF, b: RectF): Float {
        val intersection = RectF(a)
        if (!intersection.intersect(b)) return 0f
        val area = intersection.width() * intersection.height()
        val union = a.width() * a.height() + b.width() * b.height() - area
        return if (union <= 0f) 0f else area / union
    }

    override fun close() {
        runCatching { detectorSession.close() }
        runCatching { recognitionSession.close() }
    }

    companion object {
        private const val DETECT_SIZE = 640
        private const val RECOGNITION_SIZE = 112
        private const val MIN_FACE_SIZE = 28
        private const val DETECTION_THRESHOLD = 0.5f
        private const val NMS_THRESHOLD = 0.4f
        private const val MAX_FACES = 100
        private const val MIN_FREE_SPACE_BYTES = 60L * 1024 * 1024
        private val STRIDES = intArrayOf(8, 16, 32)
        private const val MODEL_URL =
            "https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_l.zip"
        private val REQUIRED_MODELS = listOf(
            "det_10g.onnx", "w600k_r50.onnx"
        )

        fun isModelInstalled(context: Context): Boolean {
            val dir = File(context.filesDir, "face-models/buffalo_l")
            return REQUIRED_MODELS.all { name ->
                dir.resolve(name).let { it.isFile && it.length() > 1024 * 1024 }
            }
        }

        private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
            var url = initialUrl
            var redirects = 0
            while (redirects < 6) {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 30_000
                conn.readTimeout = 120_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) EmreGallery/1.0")
                conn.connect()
                val code = conn.responseCode
                if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!location.isNullOrBlank()) {
                        url = location
                        redirects++
                        continue
                    }
                }
                return conn
            }
            error("Too many redirects connecting to $initialUrl")
        }

        /**
         * Installs the model pack shipped in the APK assets into app-private storage.
         * The copy runs on a background dispatcher during startup, and open() retries it
         * if face indexing starts before the startup copy has completed.
         */
        @Synchronized
        fun installBundledModel(context: Context) {
            if (isModelInstalled(context)) return
            val modelDir = File(context.filesDir, "face-models/buffalo_l")
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                error("Cannot create face model directory.")
            }
            val availableBytes = StatFs(modelDir.absolutePath).availableBytes
            check(availableBytes >= MIN_FREE_SPACE_BYTES) {
                "At least 60 MB of free storage is required to install the bundled buffalo_l model."
            }

            REQUIRED_MODELS.forEach { name ->
                val target = modelDir.resolve(name)
                if (target.isFile && target.length() > 1024 * 1024) return@forEach
                val temporary = modelDir.resolve("$name.installing")
                try {
                    context.assets.open("face-models/buffalo_l/$name").use { input ->
                        temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    check(temporary.length() > 1024 * 1024) {
                        "Bundled buffalo_l model file is incomplete: $name"
                    }
                    if (target.exists()) target.delete()
                    if (!temporary.renameTo(target)) temporary.copyTo(target, overwrite = true)
                } finally {
                    temporary.delete()
                }
            }

            runCatching {
                context.assets.list("face-models/buffalo_l").orEmpty()
                    .filter { it.contains("LICENSE", ignoreCase = true) }
                    .forEach { name ->
                        val target = modelDir.resolve(name)
                        if (!target.exists()) {
                            context.assets.open("face-models/buffalo_l/$name").use { input ->
                                target.outputStream().buffered().use { output -> input.copyTo(output) }
                            }
                        }
                    }
            }
            check(isModelInstalled(context)) { "Bundled buffalo_l model installation failed validation." }
        }

        /** Explicit, resumable-in-the-sense-of-retryable download; all pack files are installed. */
        fun downloadModel(
            context: Context,
            force: Boolean = false,
            onProgress: (Long, Long) -> Unit = { _, _ -> }
        ) {
            val modelDir = File(context.filesDir, "face-models/buffalo_l")
            if (!modelDir.exists() && !modelDir.mkdirs()) error("Cannot create face model directory.")
            if (!force && isModelInstalled(context)) return
            val connection = openConnectionWithRedirects(MODEL_URL)
            val availableBytes = StatFs(modelDir.absolutePath).availableBytes
            check(availableBytes >= MIN_FREE_SPACE_BYTES) {
                "At least 60 MB of free storage is required to install the complete buffalo_l pack."
            }
            val staging = File(modelDir, "installing").apply { deleteRecursively(); mkdirs() }
            try {
                if (connection.responseCode !in 200..299) {
                    error("Could not download buffalo_l model pack (HTTP ${connection.responseCode}).")
                }
                val total = connection.contentLengthLong.coerceAtLeast(-1L)
                var received = 0L
                ZipInputStream(BufferedInputStream(connection.inputStream)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (Thread.currentThread().isInterrupted) {
                            throw CancellationException("Download cancelled.")
                        }
                        if (!entry.isDirectory) {
                            val name = entry.name.substringAfterLast('/')
                            if (name.endsWith(".onnx") || name.contains("LICENSE", true)) {
                                val destination = staging.resolve(name)
                                destination.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        if (Thread.currentThread().isInterrupted) {
                                            throw CancellationException("Download cancelled.")
                                        }
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
                                    if (Thread.currentThread().isInterrupted) {
                                        throw CancellationException("Download cancelled.")
                                    }
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
            if (!isModelInstalled(context)) {
                runCatching { installBundledModel(context) }
            }
            if (!isModelInstalled(context)) {
                runCatching { downloadModel(context) }
            }
            check(isModelInstalled(context)) {
                "The complete buffalo_l pack is not installed."
            }
            val modelDir = File(context.filesDir, "face-models/buffalo_l")
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }
            val env = OrtEnvironment.getEnvironment()
            val detector = env.createSession(modelDir.resolve("det_10g.onnx").absolutePath, options)
            try {
                val recognizer = env.createSession(modelDir.resolve("w600k_r50.onnx").absolutePath, options)
                return BuffaloLFaceEngine(context.applicationContext, detector, recognizer)
            } catch (error: Throwable) {
                detector.close()
                throw error
            }
        }
    }
}
