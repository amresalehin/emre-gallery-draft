package com.amresalehin.emregallery.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline-after-first-use multilingual OCR.
 * Traineddata files are downloaded from the official tesseract-ocr tessdata_fast repository
 * and cached in app-private storage. No image is uploaded to a server.
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
            val trainedData = File(tessdataDir, "$code.traineddata")
            if (!trainedData.exists() || trainedData.length() < 1024) {
                downloadTrainedData(code, trainedData)
            }
        }

        val bitmap = decodeBitmap(context, uri)
        val engine = TessBaseAPI()
        try {
            if (!engine.init(baseDir.absolutePath, languageCodes.distinct().joinToString("+"))) {
                error("Tesseract could not initialize the selected language data.")
            }
            engine.setImage(bitmap)
            return engine.getUTF8Text().orEmpty().trim()
        } finally {
            engine.end()
            bitmap.recycle()
        }
    }

    private fun downloadTrainedData(code: String, destination: File) {
        val url = URL("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$code.traineddata")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
        }
        val temporary = File(destination.parentFile, "$code.traineddata.download")
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("Could not download $code language data (HTTP ${connection.responseCode}).")
            }
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (temporary.length() < 1024) error("Downloaded $code language data is incomplete.")
            if (!temporary.renameTo(destination)) {
                destination.delete()
                if (!temporary.renameTo(destination)) error("Could not save $code language data.")
            }
        } finally {
            temporary.delete()
            connection.disconnect()
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
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not decode this image.")
    }
}
