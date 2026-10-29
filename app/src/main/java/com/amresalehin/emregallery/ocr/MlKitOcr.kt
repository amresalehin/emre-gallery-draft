package com.amresalehin.emregallery.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.amresalehin.emregallery.face.FaceImageDecoder
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * On-device Optical Character Recognition powered by Google ML Kit Text Recognition.
 * Fully offline, runs on-device neural text recognition for Latin scripts (English, Spanish,
 * French, German, Portuguese, Italian, Turkish, Polish, Dutch, etc.).
 */
object MlKitOcr {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Recognizes text from a media item at [uri].
     * Tries direct URI stream via ML Kit InputImage, and falls back to oriented bitmap decoder
     * if direct stream access fails.
     */
    fun recognize(context: Context, uri: Uri): String {
        var tempBitmap: Bitmap? = null
        try {
            val inputImage = try {
                InputImage.fromFilePath(context, uri)
            } catch (_: Exception) {
                val decoded = FaceImageDecoder.decodeOrientedImage(context, uri, maxDimension = 2200)
                    ?: error("Could not decode image at URI: $uri")
                tempBitmap = decoded.bitmap
                InputImage.fromBitmap(decoded.bitmap, 0)
            }

            val task = recognizer.process(inputImage)
            val result = Tasks.await(task, 45, TimeUnit.SECONDS)
            return result.text.trim()
        } finally {
            tempBitmap?.recycle()
        }
    }

    /**
     * Recognizes text from an in-memory [bitmap].
     */
    fun recognize(bitmap: Bitmap, rotationDegrees: Int = 0): String {
        val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
        val task = recognizer.process(inputImage)
        val result = Tasks.await(task, 45, TimeUnit.SECONDS)
        return result.text.trim()
    }
}
