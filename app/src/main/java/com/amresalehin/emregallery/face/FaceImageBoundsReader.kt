package com.amresalehin.emregallery.face

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri

/** Image dimensions read from a media URI without allocating the full bitmap. */
internal data class FaceImageBounds(val width: Int, val height: Int)

internal object FaceImageBoundsReader {
    fun read(context: Context, uri: Uri): FaceImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = context.contentResolver.openInputStream(uri) ?: return null

        // BitmapFactory.decodeStream intentionally returns null in bounds-only mode.
        // The dimensions are delivered through Options, so do not use its return value
        // to decide whether the stream was opened or the image had valid bounds.
        stream.use { BitmapFactory.decodeStream(it, null, options) }

        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return FaceImageBounds(options.outWidth, options.outHeight)
    }
}
