package com.amresalehin.emregallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional fast thumbnail fetcher for small thumbnails only (<= 480px).
 * NEVER intercepts full-resolution or viewer requests.
 */
class MediaStoreThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val scheme = uri.scheme
        val authority = uri.authority
        if (scheme != ContentResolver.SCHEME_CONTENT || authority != MediaStore.AUTHORITY) {
            return@withContext null
        }

        val widthDim = options.size.width
        val heightDim = options.size.height
        if (widthDim !is Dimension.Pixels || heightDim !is Dimension.Pixels) {
            return@withContext null
        }

        val widthPx = widthDim.px
        val heightPx = heightDim.px
        if (widthPx > 480 || heightPx > 480) {
            return@withContext null
        }

        val bitmap: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val clampedWidth = widthPx.coerceIn(120, 480)
                val clampedHeight = heightPx.coerceIn(120, 480)
                context.contentResolver.loadThumbnail(uri, Size(clampedWidth, clampedHeight), null)
            } else {
                val id = try { ContentUris.parseId(uri) } catch (_: Exception) { -1L }
                if (id != -1L) {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver,
                        id,
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null
                    ) ?: MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                } else null
            }
        } catch (_: Throwable) {
            null
        }

        if (bitmap != null) {
            DrawableResult(
                drawable = BitmapDrawable(context.resources, bitmap),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } else {
            null
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme == ContentResolver.SCHEME_CONTENT && data.authority == MediaStore.AUTHORITY) {
                val widthDim = options.size.width
                val heightDim = options.size.height
                if (widthDim is Dimension.Pixels && heightDim is Dimension.Pixels) {
                    if (widthDim.px <= 480 && heightDim.px <= 480) {
                        return MediaStoreThumbnailFetcher(context, data, options)
                    }
                }
            }
            return null
        }
    }
}
