package com.amresalehin.emregallery.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.InputStream


enum class TesseractModel(
    val id: String,
    val title: String,
    val shortName: String,
    val tag: String,
    val description: String,
    val speedSummary: String,
    val accuracySummary: String,
    val memoryFootprint: String,
    val assetFolder: String,
    val storageDirName: String,
    val repoUrl: String
) {
    FAST(
        id = "fast",
        title = "tessdata_fast",
        shortName = "Fast (Quantized)",
        tag = "Recommended for Mobile",
        description = "Integer-quantized 8-bit LSTM neural network models. Optimized for high speed, low RAM footprint, and battery-friendly photo indexing.",
        speedSummary = "Up to 4x faster",
        accuracySummary = "High accuracy",
        memoryFootprint = "~4 MB per language",
        assetFolder = "tessdata_fast",
        storageDirName = "tesseract_fast",
        repoUrl = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main"
    ),
    BEST(
        id = "best",
        title = "tessdata_best",
        shortName = "Best (Full Precision)",
        tag = "Maximum Quality",
        description = "Full-precision floating-point LSTM neural network models directly from Tesseract training. Delivers maximum character recognition accuracy on complex documents, low-contrast text, and signs.",
        speedSummary = "Standard speed",
        accuracySummary = "Maximum accuracy",
        memoryFootprint = "~15 MB per language",
        assetFolder = "tessdata_best",
        storageDirName = "tesseract_best",
        repoUrl = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/main"
    );

    companion object {
        fun fromId(id: String?): TesseractModel =
            entries.find { it.id.equals(id, ignoreCase = true) || it.title.equals(id, ignoreCase = true) } ?: FAST
    }
}

/**

internal fun TesseractOcr.copyBundledTrainedData(
    context: Context,
    code: String,
    model: TesseractModel,
    destination: File
) {
    val parent = destination.parentFile ?: error("Invalid destination parent directory")
    if (!parent.exists() && !parent.mkdirs()) {
        error("Could not create directory for language: $code")
    }
    val temporary = File(parent, "$code.traineddata.tmp_${System.currentTimeMillis()}")
    try {
        var loaded = false
        // 1. Try model-specific asset folder (e.g. assets/tessdata_fast/$code.traineddata or assets/tessdata_best/$code.traineddata)
        try {
            context.assets.open("${model.assetFolder}/$code.traineddata").use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            loaded = temporary.length() >= 1024
        } catch (_: Exception) {
            loaded = false
        }

        // 2. Fallback to generic assets/tessdata/$code.traineddata if available
        if (!loaded) {
            try {
                context.assets.open("tessdata/$code.traineddata").use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                loaded = temporary.length() >= 1024
            } catch (_: Exception) {
                loaded = false
            }
        }

        // 3. Download on-demand from the official GitHub repository for this specific model
        if (!loaded) {
            val urlString = "${model.repoUrl}/$code.traineddata"
            val connection = java.net.URL(urlString).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.instanceFollowRedirects = true
            try {
                connection.connect()
                if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                } else {
                    error("HTTP ${connection.responseCode} while fetching $urlString")
                }
            } finally {
                connection.disconnect()
            }
        }

        if (temporary.length() < 1024) error("Language data for '$code' (${model.title}) is incomplete.")
        if (destination.exists()) {
            destination.delete()
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    } catch (e: Exception) {
        temporary.delete()
        throw IllegalStateException(
            "OCR data for '$code' (${model.title}) could not be installed: ${e.message}",
            e
        )
    }
}

internal fun TesseractOcr.decodeBitmap(context: Context, uri: Uri): Bitmap {
    val decoded = com.amresalehin.emregallery.face.FaceImageDecoder.decodeOrientedImage(context, uri, maxDimension = 2200)
        ?: error("Could not decode image at URI: $uri. Check the file permissions and format.")
    val orientedBitmap = decoded.bitmap

    // Tesseract requires a software ARGB_8888 bitmap
    return if (orientedBitmap.config != Bitmap.Config.ARGB_8888) {
        val softwareBitmap = orientedBitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: run {
                orientedBitmap.recycle()
                error("Could not prepare this image for OCR. Try a different image.")
            }
        orientedBitmap.recycle()
        softwareBitmap
    } else {
        orientedBitmap
    }
}

