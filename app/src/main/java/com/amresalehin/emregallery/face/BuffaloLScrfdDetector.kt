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

/** SCRFD-10GF: 640px input, BGR channel order with (pixel - 127.5) / 128 normalization. */
internal fun BuffaloLFaceEngine.detectScrfdFaces(bitmap: Bitmap): List<FaceDetection> {
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
        if (resized !== bitmap) resized.recycle()
        padded.recycle()
    }
}

internal data class Output(val name: String, val shape: LongArray, val values: FloatArray) {
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

internal data class StrideOutputs(
    val scoreOutput: Output,
    val boxOutput: Output,
    val keypointOutput: Output?,
    val numAnchors: Int
)

internal fun BuffaloLFaceEngine.resolveStrideOutputs(stride: Int, outputs: List<Output>): StrideOutputs {
    val grid = DETECT_SIZE / stride
    val gridCells = grid * grid
    val anchorCount2A = gridCells * 2
    val anchorCount1A = gridCells * 1
    val strideIndex = STRIDES.indexOf(stride)

    // 1. Explicit semantic name matching
    val scoreByName = outputs.firstOrNull {
        it.name.contains("score_$stride", ignoreCase = true) ||
        it.name.contains("scores_$stride", ignoreCase = true)
    }
    val boxByName = outputs.firstOrNull {
        it.name.contains("bbox_$stride", ignoreCase = true) ||
        it.name.contains("box_$stride", ignoreCase = true)
    }
    val kpsByName = outputs.firstOrNull {
        it.name.contains("kps_$stride", ignoreCase = true) ||
        it.name.contains("landmark_$stride", ignoreCase = true)
    }
    if (scoreByName != null && boxByName != null) {
        val numAnchors = if (scoreByName.values.size == anchorCount2A) 2 else 1
        return StrideOutputs(scoreByName, boxByName, kpsByName, numAnchors)
    }

    // 2. InsightFace SCRFD standard Feature Map Context (FMC) layout:
    // Stride 8: score index 0, bbox index fmc, kps index 2*fmc
    // Stride 16: score index 1, bbox index 1+fmc, kps index 1+2*fmc
    // Stride 32: score index 2, bbox index 2+fmc, kps index 2+2*fmc
    if (outputs.size >= 9 && strideIndex in 0..2) {
        val fmc = if (outputs.size >= 15) 5 else 3
        val scoreCandidate = outputs.getOrNull(strideIndex)
        val boxCandidate = outputs.getOrNull(strideIndex + fmc)
        val kpsCandidate = outputs.getOrNull(strideIndex + fmc * 2)
        if (scoreCandidate != null && boxCandidate != null) {
            val scoreLen = scoreCandidate.values.size
            val boxLen = boxCandidate.values.size
            if ((scoreLen == anchorCount2A || scoreLen == anchorCount1A) && boxLen == scoreLen * 4) {
                val numAnchors = if (scoreLen == anchorCount2A) 2 else 1
                return StrideOutputs(scoreCandidate, boxCandidate, kpsCandidate, numAnchors)
            }
        }
    }

    // 3. Sequential grouping by stride: [score_8, box_8, kps_8, score_16, box_16, kps_16, ...]
    if (outputs.size >= 9 && strideIndex in 0..2) {
        val scoreCandidate = outputs.getOrNull(strideIndex * 3)
        val boxCandidate = outputs.getOrNull(strideIndex * 3 + 1)
        val kpsCandidate = outputs.getOrNull(strideIndex * 3 + 2)
        if (scoreCandidate != null && boxCandidate != null) {
            val scoreLen = scoreCandidate.values.size
            val boxLen = boxCandidate.values.size
            if ((scoreLen == anchorCount2A || scoreLen == anchorCount1A) && boxLen == scoreLen * 4) {
                val numAnchors = if (scoreLen == anchorCount2A) 2 else 1
                return StrideOutputs(scoreCandidate, boxCandidate, kpsCandidate, numAnchors)
            }
        }
    }

    // 4. Exact element count matching:
    // Try 2 anchors per cell first (standard SCRFD-10G)
    var scoreOut = outputs.firstOrNull { it.values.size == anchorCount2A && isScoreTensor(it) }
        ?: outputs.firstOrNull { it.values.size == anchorCount2A }
    var boxOut = outputs.firstOrNull { it.values.size == anchorCount2A * 4 }
    var kpsOut = outputs.firstOrNull { it.values.size == anchorCount2A * 10 }
    var numAnchors = 2

    // If not found, try 1 anchor per cell
    if (scoreOut == null || boxOut == null) {
        scoreOut = outputs.firstOrNull { it.values.size == anchorCount1A && isScoreTensor(it) }
            ?: outputs.firstOrNull { it.values.size == anchorCount1A }
        boxOut = outputs.firstOrNull { it.values.size == anchorCount1A * 4 }
        kpsOut = outputs.firstOrNull { it.values.size == anchorCount1A * 10 }
        numAnchors = 1
    }

    check(scoreOut != null && boxOut != null) {
        val summary = outputs.joinToString(prefix = "[", postfix = "]") {
            "${it.name}: shape=${it.shape.contentToString()}, len=${it.values.size}"
        }
        "Unsupported SCRFD output layout for stride $stride (expected anchor counts: 2A=$anchorCount2A, 1A=$anchorCount1A). Available outputs (${outputs.size}): $summary"
    }

    return StrideOutputs(scoreOut, boxOut, kpsOut, numAnchors)
}

internal fun BuffaloLFaceEngine.isScoreTensor(output: Output): Boolean {
    val last = output.shape.lastOrNull() ?: -1L
    return last == 1L || output.shape.size == 2
}

internal fun BuffaloLFaceEngine.decodeScrfd(
    outputs: List<Output>,
    originalWidth: Int,
    originalHeight: Int,
    scale: Float
): List<FaceDetection> {
    val candidates = mutableListOf<FaceDetection>()
    for (stride in STRIDES) {
        val resolved = resolveStrideOutputs(stride, outputs)
        val scoreOutput = resolved.scoreOutput
        val boxOutput = resolved.boxOutput
        val keypointOutput = resolved.keypointOutput
        val numAnchors = resolved.numAnchors

        val scores = scoreOutput.values
        val boxes = boxOutput.values
        val keypoints = keypointOutput?.values
        val count = min(scores.size, boxes.size / 4)
        val grid = DETECT_SIZE / stride
        for (i in 0 until count) {
            val score = scores[i]
            if (!score.isFinite() || score < detectionThreshold) continue
            val cell = i / numAnchors
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


internal fun BuffaloLFaceEngine.nonMaximumSuppression(input: List<FaceDetection>): List<FaceDetection> {
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

internal fun BuffaloLFaceEngine.iou(a: RectF, b: RectF): Float {
    val intersection = RectF(a)
    if (!intersection.intersect(b)) return 0f
    val area = intersection.width() * intersection.height()
    val union = a.width() * a.height() + b.width() * b.height() - area
    return if (union <= 0f) 0f else area / union
}

