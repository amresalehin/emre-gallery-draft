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
class BuffaloLFaceEngine private constructor(
    private val context: Context,
    private val detectorSession: OrtSession,
    private val recognitionSession: OrtSession,
    private val detectionThreshold: Float
) : FaceEngine {

    override val engineId: String get() = ENGINE_ID
    override val modelVersion: String get() = MODEL_VERSION
    override val embeddingDimension: Int get() = EMBEDDING_DIMENSION
    override val formatVersion: Int get() = FORMAT_VERSION

    private data class FaceDetection(val bounds: RectF, val landmarks: FloatArray, val score: Float)

    override fun embed(uri: Uri): List<DetectedFaceEmbedding> {
        val bitmap = decodeOrientedBitmap(uri)
            ?: error("Could not decode image at URI: $uri")
        return try {
            embedBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    override fun embedBitmap(bitmap: Bitmap): List<DetectedFaceEmbedding> {
        val detections = detect(bitmap)
        return detections.mapIndexedNotNull { index, face ->
            if (face.bounds.width() < MIN_FACE_SIZE || face.bounds.height() < MIN_FACE_SIZE) {
                return@mapIndexedNotNull null
            }
            val aligned = alignFace(bitmap, face.landmarks)
            try {
                val embedding = inferEmbedding(aligned)
                val faceArea = (face.bounds.width() * face.bounds.height()).coerceAtLeast(1f)
                val imageArea = (bitmap.width * bitmap.height).coerceAtLeast(1).toFloat()
                val sizeRatio = (faceArea / imageArea).coerceIn(0.001f, 1.0f)
                val aspect = face.bounds.width() / face.bounds.height().coerceAtLeast(1f)
                val aspectScore = (1f - kotlin.math.abs(aspect - 0.85f)).coerceIn(0.2f, 1.0f)
                val quality = (face.score * 0.5f + kotlin.math.min(sizeRatio * 10f, 0.3f) + aspectScore * 0.2f).coerceIn(0f, 1f)
                DetectedFaceEmbedding(index, RectF(face.bounds), embedding, face.score, quality)
            } finally {
                aligned.recycle()
            }
        }
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

    /** SCRFD-10GF: 640px input, BGR channel order with (pixel - 127.5) / 128 normalization. */
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

    private data class Output(val name: String, val shape: LongArray, val values: FloatArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Output) return false
            if (name != other.name) return false
            if (!shape.contentEquals(other.shape)) return false
            if (!values.contentEquals(other.values)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + shape.contentHashCode()
            result = 31 * result + values.contentHashCode()
            return result
        }
    }

    private fun decodeScrfd(
        outputs: List<Output>,
        originalWidth: Int,
        originalHeight: Int,
        scale: Float
    ): List<FaceDetection> {
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
                "Unsupported SCRFD output layout for stride $stride."
            }
            val scores = scoreOutput.values
            val boxes = boxOutput.values
            val keypoints = keypointOutput?.values
            val count = min(anchorCount, min(scores.size, boxes.size / 4))
            val grid = DETECT_SIZE / stride
            for (i in 0 until count) {
                val score = scores[i]
                if (!score.isFinite() || score < detectionThreshold) continue
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

    /** ArcFace W600K_R50: 112px input, BGR channel order with (pixel - 127.5) / 127.5 normalization. */
    private fun inferEmbedding(face: Bitmap): FloatArray {
        val input = imageTensorData(face, RECOGNITION_SIZE, 127.5f, 127.5f)
        val tensor = OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(), FloatBuffer.wrap(input),
            longArrayOf(1, 3, RECOGNITION_SIZE.toLong(), RECOGNITION_SIZE.toLong())
        )
        try {
            recognitionSession.run(mapOf(recognitionSession.inputNames.first() to tensor)).use { result ->
                val embedding = flatten(result.get(recognitionSession.outputNames.first()).get().value)
                check(embedding.size == EMBEDDING_DIMENSION) {
                    "Unexpected Buffalo_L embedding size: ${embedding.size} (expected $EMBEDDING_DIMENSION)."
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

    /** Prepares CHW BGR float array tensor from bitmap pixels. */
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
        const val ENGINE_ID = "buffalo_l"
        const val MODEL_VERSION = "w600k_r50"
        const val EMBEDDING_DIMENSION = 512
        const val FORMAT_VERSION = 1

        private const val DETECT_SIZE = 640
        private const val RECOGNITION_SIZE = 112
        private const val MIN_FACE_SIZE = 28
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

        private fun openConnectionWithRedirects(initialUrl: String, existingBytes: Long = 0L): HttpURLConnection {
            var url = initialUrl
            var redirects = 0
            while (redirects < 6) {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 30_000
                conn.readTimeout = 120_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) EmreGallery/1.0")
                if (existingBytes > 0L) {
                    conn.setRequestProperty("Range", "bytes=$existingBytes-")
                }
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

        @Synchronized
        fun installBundledModel(context: Context) {
            if (isModelInstalled(context)) return
            val modelDir = File(context.filesDir, "face-models/buffalo_l")
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                error("Cannot create face model directory.")
            }
            val availableBytes = StatFs(modelDir.absolutePath).availableBytes
            check(availableBytes >= MIN_FREE_SPACE_BYTES) {
                "At least 60 MB of free storage is required to install Buffalo_L model."
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
                        "Bundled Buffalo_L model file is incomplete: $name"
                    }
                    if (target.exists()) target.delete()
                    if (!temporary.renameTo(target)) temporary.copyTo(target, overwrite = true)
                } finally {
                    temporary.delete()
                }
            }
        }

        suspend fun downloadModel(
            context: Context,
            force: Boolean = false,
            onProgress: (Long, Long) -> Unit = { _, _ -> },
            onStage: (String) -> Unit = {},
            awaitIfResumed: suspend () -> Unit = {},
            onDetailedProgress: (DetailedDownloadProgress) -> Unit = {}
        ) {
            val modelDir = File(context.filesDir, "face-models/buffalo_l")
            if (!modelDir.exists() && !modelDir.mkdirs()) error("Cannot create face model directory.")
            if (!force && isModelInstalled(context)) {
                onStage("ready")
                onDetailedProgress(DetailedDownloadProgress(288L * 1024 * 1024, 288L * 1024 * 1024, 0L, 0L, "ready"))
                return
            }
            val availableBytes = StatFs(modelDir.absolutePath).availableBytes
            check(availableBytes >= MIN_FREE_SPACE_BYTES) {
                "At least 60 MB of free storage is required to install Buffalo_L."
            }

            val archive = File(modelDir, "buffalo_l.zip.part")
            val staging = File(modelDir, "installing").apply { deleteRecursively(); mkdirs() }
            val existingBytes = if (archive.exists()) archive.length() else 0L

            onStage("connecting")
            val estimatedTotal = 288L * 1024 * 1024
            onDetailedProgress(DetailedDownloadProgress(existingBytes, estimatedTotal, 0L, 0L, "connecting"))

            val connection = openConnectionWithRedirects(MODEL_URL, existingBytes)
            var responseTotal = estimatedTotal
            try {
                val code = connection.responseCode
                val isPartial = code == 206
                val isOk = code in 200..299

                if (!isOk && !isPartial) {
                    if (code != 416) {
                        error("Could not download Buffalo_L model pack (HTTP $code).")
                    }
                }

                val appendMode = isPartial && existingBytes > 0L
                val serverContentLength = connection.contentLengthLong.coerceAtLeast(-1L)
                val total = if (appendMode) {
                    existingBytes + (if (serverContentLength > 0) serverContentLength else 0L)
                } else if (serverContentLength > 0) {
                    serverContentLength
                } else {
                    estimatedTotal
                }
                responseTotal = total
                onStage("downloading")

                val downloadStartTime = System.currentTimeMillis()
                var received = if (appendMode) existingBytes else 0L
                var sessionDownloaded = 0L

                if (code != 416) {
                    BufferedInputStream(connection.inputStream).use { input ->
                        java.io.FileOutputStream(archive, appendMode).buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                awaitIfResumed()
                                if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled.")
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                received += count
                                sessionDownloaded += count

                                val elapsedSec = ((System.currentTimeMillis() - downloadStartTime) / 1000.0).coerceAtLeast(0.1)
                                val speed = (sessionDownloaded / elapsedSec).toLong()
                                val remainingBytes = (total - received).coerceAtLeast(0L)
                                val etaSec = if (speed > 0) remainingBytes / speed else 0L

                                onProgress(received, total)
                                onDetailedProgress(
                                    DetailedDownloadProgress(
                                        bytesReceived = received,
                                        totalBytes = total,
                                        speedBytesPerSec = speed,
                                        etaSeconds = etaSec,
                                        stage = "downloading"
                                    )
                                )
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            try {
                onStage("verifying")
                onDetailedProgress(
                    DetailedDownloadProgress(responseTotal, responseTotal, 0L, 0L, "verifying")
                )
                if (!archive.exists() || archive.length() < 10L * 1024 * 1024) {
                    archive.delete()
                    error("Downloaded archive is invalid or incomplete.")
                }

                onStage("extracting")
                onDetailedProgress(
                    DetailedDownloadProgress(responseTotal, responseTotal, 0L, 0L, "extracting")
                )
                ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        awaitIfResumed()
                        if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled.")
                        if (!entry.isDirectory) {
                            val name = entry.name.substringAfterLast('/')
                            if (name.endsWith(".onnx") || name.contains("LICENSE", true)) {
                                val destination = staging.resolve(name)
                                destination.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        awaitIfResumed()
                                        if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled.")
                                        val count = zip.read(buffer)
                                        if (count < 0) break
                                        output.write(buffer, 0, count)
                                    }
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
                if (missing.isNotEmpty()) error("Incomplete Buffalo_L archive; missing: " + missing.joinToString())

                onStage("installing")
                onDetailedProgress(
                    DetailedDownloadProgress(responseTotal, responseTotal, 0L, 0L, "installing")
                )
                REQUIRED_MODELS.forEach { name ->
                    awaitIfResumed()
                    if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled.")
                    val source = staging.resolve(name)
                    val target = modelDir.resolve(name)
                    if (target.exists()) target.delete()
                    if (!source.renameTo(target)) source.copyTo(target, overwrite = true)
                }
                staging.listFiles()?.filter { it.isFile && it.name.contains("LICENSE", true) }?.forEach {
                    it.copyTo(modelDir.resolve(it.name), overwrite = true)
                }
                check(isModelInstalled(context)) { "Buffalo_L model installation failed validation." }
                if (responseTotal > 0L) onProgress(responseTotal, responseTotal)
                onStage("ready")
                onDetailedProgress(
                    DetailedDownloadProgress(responseTotal, responseTotal, 0L, 0L, "ready")
                )
            } finally {
                archive.delete()
                staging.deleteRecursively()
            }
        }

        fun open(
            context: Context,
            detectionThreshold: Float = FaceRecognitionSettings.DEFAULT_DETECTION_CONFIDENCE
        ): BuffaloLFaceEngine {
            if (!isModelInstalled(context)) {
                runCatching { installBundledModel(context) }
            }
            check(isModelInstalled(context)) {
                "Buffalo_L model is not installed. Please download it first."
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
                return BuffaloLFaceEngine(
                    context.applicationContext,
                    detector,
                    recognizer,
                    detectionThreshold.coerceIn(0.30f, 0.90f)
                )
            } catch (error: Throwable) {
                detector.close()
                throw error
            }
        }
    }
}

/**
 * Factory for Buffalo_L engine registered with FaceEngineRegistry.
 */
object BuffaloLFaceEngineFactory : FaceEngineFactory {
    override val engineId: String = BuffaloLFaceEngine.ENGINE_ID
    override val displayName: String = "Buffalo_L (InsightFace ONNX 512-d)"
    override val modelVersion: String = BuffaloLFaceEngine.MODEL_VERSION
    override val embeddingDimension: Int = BuffaloLFaceEngine.EMBEDDING_DIMENSION
    override val isExperimental: Boolean = false

    override fun isInstalled(context: Context): Boolean = BuffaloLFaceEngine.isModelInstalled(context)

    override fun create(context: Context, detectionThreshold: Float): FaceEngine =
        BuffaloLFaceEngine.open(context, detectionThreshold)

    override suspend fun downloadModel(
        context: Context,
        force: Boolean,
        onProgress: (Long, Long) -> Unit,
        onStage: (String) -> Unit,
        awaitIfResumed: suspend () -> Unit,
        onDetailedProgress: (DetailedDownloadProgress) -> Unit
    ) = BuffaloLFaceEngine.downloadModel(context, force, onProgress, onStage, awaitIfResumed, onDetailedProgress)

    init {
        FaceEngineRegistry.register(this)
    }
}
