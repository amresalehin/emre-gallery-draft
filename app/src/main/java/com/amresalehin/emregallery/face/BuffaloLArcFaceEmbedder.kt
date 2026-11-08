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

/** ArcFace W600K_R50: 112px input, BGR channel order with (pixel - 127.5) / 127.5 normalization. */
internal fun BuffaloLFaceEngine.inferEmbedding(face: Bitmap): FloatArray {
    val input = imageTensorData(face, BuffaloLFaceEngine.RECOGNITION_SIZE, 127.5f, 127.5f)
    val tensor = OnnxTensor.createTensor(
        OrtEnvironment.getEnvironment(), FloatBuffer.wrap(input),
        longArrayOf(1, 3, BuffaloLFaceEngine.RECOGNITION_SIZE.toLong(), BuffaloLFaceEngine.RECOGNITION_SIZE.toLong())
    )
    try {
        recognitionSession.run(mapOf(recognitionSession.inputNames.first() to tensor)).use { result ->
            val embedding = flatten(result.get(recognitionSession.outputNames.first()).get().value)
            check(embedding.size == BuffaloLFaceEngine.EMBEDDING_DIMENSION) {
                "Unexpected Buffalo_L embedding size: ${embedding.size} (expected $BuffaloLFaceEngine.EMBEDDING_DIMENSION)."
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

/** Prepares CHW RGB float array tensor from bitmap pixels. */
internal fun BuffaloLFaceEngine.imageTensorData(bitmap: Bitmap, size: Int, mean: Float, std: Float): FloatArray {
    val scaled = if (bitmap.width == size && bitmap.height == size) bitmap
        else Bitmap.createScaledBitmap(bitmap, size, size, true)
    try {
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val plane = size * size
        val input = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            input[i] = (Color.red(pixel) - mean) / std
            input[plane + i] = (Color.green(pixel) - mean) / std
            input[2 * plane + i] = (Color.blue(pixel) - mean) / std
        }
        return input
    } finally {
        if (scaled !== bitmap) scaled.recycle()
    }
}

internal fun BuffaloLFaceEngine.flatten(value: Any): FloatArray {
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


