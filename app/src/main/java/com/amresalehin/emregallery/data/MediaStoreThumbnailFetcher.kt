package com.amresalehin.emregallery.data

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

class MediaStoreThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val widthDim = options.size.width
        val heightDim = options.size.height
        if (widthDim !is Dimension.Pixels || heightDim !is Dimension.Pixels) {
            return@withContext null
        }
        val widthPx = widthDim.px
        val heightPx = heightDim.px
        if (widthPx > 1024 || heightPx > 1024) {
            return@withContext null
        }

        val bitmap: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val clampedWidth = widthPx.coerceIn(96, 512)
                val clampedHeight = heightPx.coerceIn(96, 512)
                val signal = android.os.CancellationSignal()
                val job = coroutineContext[kotlinx.coroutines.Job]
                val handle = job?.invokeOnCompletion { signal.cancel() }
                try {
                    context.contentResolver.loadThumbnail(uri, Size(clampedWidth, clampedHeight), signal)
                } finally {
                    handle?.dispose()
                }
            } else {
                val id = try {
                    ContentUris.parseId(uri)
                } catch (e: Exception) {
                    -1L
                }
                if (id != -1L) {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null
                    ) ?: MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
                    )
                } else null
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException || t is android.os.OperationCanceledException) {
                throw kotlinx.coroutines.CancellationException("Thumbnail fetch cancelled", t)
            }
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
            if (data.scheme == "content" && (data.authority == "media" || data.authority == MediaStore.AUTHORITY)) {
                val widthDim = options.size.width
                val heightDim = options.size.height
                if (widthDim is Dimension.Pixels && heightDim is Dimension.Pixels) {
                    if (widthDim.px <= 1024 && heightDim.px <= 1024) {
                        return MediaStoreThumbnailFetcher(context, data, options)
                    }
                }
            }
            return null
        }
    }
}
