package com.amresalehin.emregallery.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min

data class EnteDetection(
    val bounds: RectF,
    val landmarks: FloatArray,
    val score: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnteDetection) return false
        if (bounds != other.bounds) return false
        if (!landmarks.contentEquals(other.landmarks)) return false
        if (score != other.score) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bounds.hashCode()
        result = 31 * result + landmarks.contentHashCode()
        result = 31 * result + score.hashCode()
        return result
    }
}

/** YOLO5Face detector with bounded top-score candidate selection. */
class EnteFaceDetector(
    private val session: OrtSession,
    private val detectionThreshold: Float = EnteFaceConstants.DEFAULT_DETECTION_CONFIDENCE,
    private val nmsThreshold: Float = EnteFaceConstants.DEFAULT_NMS_IOU_THRESHOLD
) {
    companion object {
        const val INPUT_SIZE = EnteFaceConstants.DETECT_SIZE
        private const val MAX_CANDIDATES = 500
        private const val MAX_OUTPUT_FACES = 100

        fun decodeTensorOutput(
            flatValues: FloatArray,
            numAnchors: Int,
            stridePerAnchor: Int = 16,
            originalWidth: Int,
            originalHeight: Int,
            scale: Float,
            padX: Float,
            padY: Float,
            confidenceThreshold: Float,
            nmsIoUThreshold: Float
        ): List<EnteDetection> {
            require(stridePerAnchor > 4) { "YOLO5Face anchors require at least 5 values." }
            require(scale > 0f && scale.isFinite()) { "Invalid detector input scale: $scale" }
            val maxRows = min(numAnchors, flatValues.size / stridePerAnchor)
            val candidates = PriorityQueue<EnteDetection>(
                compareBy<EnteDetection> { it.score }
                    .thenBy { it.bounds.left }.thenBy { it.bounds.top }
                    .thenBy { it.bounds.right }.thenBy { it.bounds.bottom }
            )
            for (i in 0 until maxRows) {
                val offset = i * stridePerAnchor
                val objConf = flatValues[offset + 4]
                val classConf = if (stridePerAnchor >= 16) flatValues[offset + 15] else if (stridePerAnchor > 5) flatValues[offset + 5] else 1.0f
                val score = objConf * classConf
                if (!score.isFinite() || score < confidenceThreshold) continue
                val cx = flatValues[offset]
                val cy = flatValues[offset + 1]
                val w = flatValues[offset + 2]
                val h = flatValues[offset + 3]
                if (w <= 0f || h <= 0f || !cx.isFinite() || !cy.isFinite() || !w.isFinite() || !h.isFinite()) continue
                val left = ((cx - w / 2f - padX) / scale).coerceIn(0f, originalWidth.toFloat())
                val top = ((cy - h / 2f - padY) / scale).coerceIn(0f, originalHeight.toFloat())
                val right = ((cx + w / 2f - padX) / scale).coerceIn(0f, originalWidth.toFloat())
                val bottom = ((cy + h / 2f - padY) / scale).coerceIn(0f, originalHeight.toFloat())
                if (right - left < 2f || bottom - top < 2f) continue
                val bounds = RectF(left, top, right, bottom)
                val landmarks = FloatArray(10)
                if (stridePerAnchor >= 16) {
                    for (k in 0 until 5) {
                        landmarks[k * 2] = ((flatValues[offset + 5 + k * 2] - padX) / scale).coerceIn(0f, originalWidth.toFloat())
                        landmarks[k * 2 + 1] = ((flatValues[offset + 5 + k * 2 + 1] - padY) / scale).coerceIn(0f, originalHeight.toFloat())
                    }
                } else {
                    landmarks[0] = bounds.left + bounds.width() * 0.32f; landmarks[1] = bounds.top + bounds.height() * 0.38f
                    landmarks[2] = bounds.left + bounds.width() * 0.68f; landmarks[3] = bounds.top + bounds.height() * 0.38f
                    landmarks[4] = bounds.centerX(); landmarks[5] = bounds.top + bounds.height() * 0.56f
                    landmarks[6] = bounds.left + bounds.width() * 0.38f; landmarks[7] = bounds.top + bounds.height() * 0.73f
                    landmarks[8] = bounds.left + bounds.width() * 0.62f; landmarks[9] = bounds.top + bounds.height() * 0.73f
                }
                val detection = EnteDetection(bounds, landmarks, score)
                if (candidates.size < MAX_CANDIDATES) candidates.offer(detection)
                else if (candidates.peek() != null && detection.score > candidates.peek().score) {
                    candidates.poll(); candidates.offer(detection)
                }
            }
            return applyNms(candidates.toList(), nmsIoUThreshold)
        }

        fun applyNms(detections: List<EnteDetection>, iouThreshold: Float): List<EnteDetection> {
            if (detections.isEmpty()) return emptyList()
            val sorted = detections.sortedWith(
                compareByDescending<EnteDetection> { it.score }
                    .thenBy { it.bounds.left }.thenBy { it.bounds.top }
                    .thenBy { it.bounds.right }.thenBy { it.bounds.bottom }
            )
            val accepted = ArrayList<EnteDetection>(MAX_OUTPUT_FACES)
            for (candidate in sorted) {
                if (accepted.any { calculateIoU(candidate.bounds, it.bounds) >= iouThreshold }) continue
                accepted.add(candidate)
                if (accepted.size >= MAX_OUTPUT_FACES) break
            }
            return accepted
        }

        fun calculateIoU(a: RectF, b: RectF): Float {
            val intersectionWidth = max(0f, min(a.right, b.right) - max(a.left, b.left))
            val intersectionHeight = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
            val intersectionArea = intersectionWidth * intersectionHeight
            val unionArea = a.width() * a.height() + b.width() * b.height() - intersectionArea
            return if (unionArea > 0f) intersectionArea / unionArea else 0f
        }
    }

    fun detect(bitmap: Bitmap): List<EnteDetection> {
        val origW = bitmap.width
        val origH = bitmap.height
        val scale = min(INPUT_SIZE.toFloat() / origW, INPUT_SIZE.toFloat() / origH)
        val newW = (origW * scale).toInt().coerceAtLeast(1)
        val newH = (origH * scale).toInt().coerceAtLeast(1)
        val padX = (INPUT_SIZE - newW) / 2f
        val padY = (INPUT_SIZE - newH) / 2f
        val letterboxed = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(letterboxed)
        canvas.drawColor(Color.BLACK)
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        try { canvas.drawBitmap(scaled, padX, padY, Paint(Paint.FILTER_BITMAP_FLAG)) }
        finally { if (scaled !== bitmap) scaled.recycle() }
        try {
            val tensor = OnnxTensor.createTensor(
                OrtEnvironment.getEnvironment(), FloatBuffer.wrap(preprocessToRgbNormalized(letterboxed)),
                longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            )
            try {
                session.run(mapOf(session.inputNames.first() to tensor)).use { results ->
                    val outputName = session.outputNames.first()
                    val flatFloats = flattenFloatArray(results.get(outputName).get().value)
                    val shape = (session.outputInfo[outputName]?.info as? TensorInfo)?.shape ?: longArrayOf()
                    val stride = shape.lastOrNull()?.takeIf { it > 0L }?.toInt() ?: 16
                    check(stride >= 6) { "Unexpected YOLOv5Face output shape ${shape.contentToString()}." }
                    check(flatFloats.size % stride == 0) { "Output size ${flatFloats.size} is not divisible by stride $stride." }
                    val numAnchors = flatFloats.size / stride
                    val metadataAnchors = if (shape.size >= 2 && shape[shape.size - 2] > 0L) shape[shape.size - 2].toInt() else -1
                    check(metadataAnchors < 0 || metadataAnchors == numAnchors) { "Tensor metadata and anchor count disagree." }
                    return decodeTensorOutput(flatFloats, numAnchors, stride, origW, origH, scale, padX, padY, detectionThreshold, nmsThreshold)
                }
            } finally { tensor.close() }
        } finally { letterboxed.recycle() }
    }

    private fun preprocessToRgbNormalized(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val floatData = FloatArray(3 * w * h)
        val planeSize = w * h
        for (i in pixels.indices) {
            val p = pixels[i]
            floatData[i] = ((p shr 16) and 0xFF) / 255f
            floatData[planeSize + i] = ((p shr 8) and 0xFF) / 255f
            floatData[planeSize * 2 + i] = (p and 0xFF) / 255f
        }
        return floatData
    }

    private fun flattenFloatArray(value: Any): FloatArray {
        if (value is FloatArray) return value
        fun count(item: Any?): Int = when (item) {
            null -> 0
            is FloatArray -> item.size
            is Float -> 1
            is Array<*> -> item.sumOf(::count)
            else -> 0
        }
        val result = FloatArray(count(value))
        var offset = 0
        fun append(item: Any?) {
            when (item) {
                null -> Unit
                is FloatArray -> { item.copyInto(result, offset); offset += item.size }
                is Float -> result[offset++] = item
                is Array<*> -> item.forEach(::append)
            }
        }
        append(value)
        return result
    }
}
