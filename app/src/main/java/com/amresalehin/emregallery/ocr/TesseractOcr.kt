package com.amresalehin.emregallery.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.InputStream

/**
 * Fully on-device OCR using Tesseract (tess-two).
 * Traineddata files are bundled into APK assets and extracted to internal storage on demand.
 */
object TesseractOcr {
    val supportedLanguages = linkedMapOf(
        "English" to "eng",
        "Hindi" to "hin",
        "Bengali" to "ben",
        "Arabic" to "ara",
        "Urdu" to "urd",
        "French" to "fra",
        "German" to "deu",
        "Spanish" to "spa",
        "Portuguese" to "por",
        "Russian" to "rus",
        "Chinese (Simplified)" to "chi_sim",
        "Japanese" to "jpn"
    )

    fun recognize(context: Context, uri: Uri, languageCodes: List<String>): String {
        require(languageCodes.isNotEmpty()) { "Choose at least one OCR language." }
        val baseDir = File(context.filesDir, "tesseract")
        val tessdataDir = File(baseDir, "tessdata")
        if (!tessdataDir.exists() && !tessdataDir.mkdirs()) {
            error("Could not create OCR language-data directory.")
        }

        val validCodes = languageCodes.distinct().filter { it in supportedLanguages.values }
        require(validCodes.isNotEmpty()) { "No supported OCR languages selected." }

        validCodes.forEach { code ->
            val destination = File(tessdataDir, "$code.traineddata")
            if (!destination.exists() || destination.length() < 1024) {
                copyBundledTrainedData(context, code, destination)
            }
        }

        val bitmap = decodeBitmap(context, uri)
        val englishData = File(tessdataDir, "eng.traineddata")
        if (!englishData.exists() || englishData.length() < 1024) {
            copyBundledTrainedData(context, "eng", englishData)
        }

        val combinedLangs = validCodes.joinToString("+")
        val candidates = (listOf(combinedLangs) + validCodes + listOf("eng")).distinct()
        var activeEngine: TessBaseAPI? = null
        try {
            // Use a fresh native engine for every attempt. A failed init can leave
            // the native instance unsuitable for another init call.
            for (candidate in candidates) {
                if (candidate.split("+").any { code ->
                        val file = File(tessdataDir, "$code.traineddata")
                        !file.isFile || file.length() < 1024
                    }) {
                    continue
                }
                val candidateEngine = TessBaseAPI()
                val initialized = runCatching {
                    candidateEngine.init(baseDir.absolutePath, candidate)
                }.getOrDefault(false)
                if (initialized) {
                    activeEngine = candidateEngine
                    break
                }
                candidateEngine.end()
            }

            val engine = activeEngine ?: error(
                "Tesseract could not load OCR language data ($combinedLangs). " +
                    "The bundled traineddata may be missing, incomplete, or incompatible. " +
                    "Reinstall the latest app build and try selecting one language at a time."
            )
            engine.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            engine.setImage(bitmap)
            return engine.getUTF8Text().orEmpty().trim()
        } finally {
            activeEngine?.end()
            bitmap.recycle()
        }
    }

    private fun copyBundledTrainedData(context: Context, code: String, destination: File) {
        val parent = destination.parentFile ?: error("Invalid destination parent directory")
        if (!parent.exists() && !parent.mkdirs()) {
            error("Could not create directory for language: $code")
        }
        val temporary = File(parent, "$code.traineddata.tmp_${System.currentTimeMillis()}")
        try {
            context.assets.open("tessdata/$code.traineddata").use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (temporary.length() < 1024) error("Bundled $code language data is missing or incomplete.")
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
                "Bundled OCR data for '$code' is unavailable: ${e.message}",
                e
            )
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = openStream(context, uri)
            ?: error("Cannot open this media item. Check that Emre Gallery has permission to access it.")
        boundsStream.use { stream ->
            // Bounds-only decoding intentionally returns null; dimensions are set on bounds.
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("This file is not a readable image. Try another image format.")
        }

        var sample = 1
        while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = openStream(context, uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("Could not decode this image. Check the file permissions and format.")

        // Read EXIF orientation to ensure upright text recognition
        val orientation = runCatching {
            openStream(context, uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            // EXIF 5 (transpose) and 7 (transverse) combine rotation and reflection.
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        val orientedBitmap = if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }

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

    private fun openStream(context: Context, uri: Uri): InputStream? {
        return runCatching {
            if (uri.scheme == null || uri.scheme == "file") {
                val path = uri.path ?: uri.toString()
                val file = File(path)
                if (file.exists()) file.inputStream() else context.contentResolver.openInputStream(uri)
            } else {
                context.contentResolver.openInputStream(uri)
            }
        }.getOrNull()
    }
}
