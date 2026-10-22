package com.amresalehin.emregallery.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * Fully on-device OCR.
 * The Tesseract native engine is packaged by tess-two, and traineddata files are bundled
 * into the APK at build time. OCR does not need a network connection at first use.
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

        languageCodes.distinct().forEach { code ->
            require(code in supportedLanguages.values) { "Unsupported OCR language: $code" }
            val destination = File(tessdataDir, "$code.traineddata")
            if (!destination.exists() || destination.length() < 1024) {
                copyBundledTrainedData(context, code, destination)
            }
        }

        val bitmap = decodeBitmap(context, uri)
        val engine = TessBaseAPI()
        try {
            if (!engine.init(baseDir.absolutePath, languageCodes.distinct().joinToString("+"))) {
                error("Tesseract could not initialize the bundled language data.")
            }
            engine.setImage(bitmap)
            return engine.getUTF8Text().orEmpty().trim()
        } finally {
            engine.end()
            bitmap.recycle()
        }
    }

    private fun copyBundledTrainedData(context: Context, code: String, destination: File) {
        val temporary = File(destination.parentFile, "$code.traineddata.tmp")
        try {
            context.assets.open("tessdata/$code.traineddata").use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (temporary.length() < 1024) error("Bundled $code language data is missing or incomplete.")
            if (!temporary.renameTo(destination)) {
                destination.delete()
                if (!temporary.renameTo(destination)) error("Could not install bundled $code language data.")
            }
        } catch (e: Exception) {
            temporary.delete()
            throw IllegalStateException(
                "Bundled OCR data for $code is unavailable. Rebuild the app so the OCR data is included.",
                e
            )
        } finally {
            temporary.delete()
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Cannot open this media item.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("This file is not a readable image.")
        var sample = 1
        while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not decode this image.")
    }
}
