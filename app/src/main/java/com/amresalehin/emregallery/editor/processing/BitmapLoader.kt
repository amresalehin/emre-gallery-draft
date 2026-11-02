package com.amresalehin.emregallery.editor.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max

/**
 * Handles downsampled and memory-safe bitmap decoding.
 */
object BitmapLoader {

    suspend fun loadSampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            var stream: InputStream? = context.contentResolver.openInputStream(uri) ?: return@runCatching null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            stream?.close()

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return@runCatching null

            var inSampleSize = 1
            val maxSrc = max(srcWidth, srcHeight)
            while ((maxSrc / inSampleSize) > maxDimension * 1.25) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }

            stream = context.contentResolver.openInputStream(uri) ?: return@runCatching null
            val decoded = BitmapFactory.decodeStream(stream, null, decodeOptions)
            stream?.close()
            decoded
        }.getOrNull()
    }
}
