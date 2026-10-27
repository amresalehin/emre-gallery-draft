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

/** MediaStore thumbnail path for still images only. Videos use VideoPosterFetcher. */
class MediaStoreThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val width = options.size.width as? Dimension.Pixels ?: return@withContext null
        val height = options.size.height as? Dimension.Pixels ?: return@withContext null
        if (width.px > 600 || height.px > 600) return@withContext null

        val bitmap: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    uri, Size(width.px.coerceIn(120, 512), height.px.coerceIn(120, 512)), null
                )
            } else {
                val id = runCatching { ContentUris.parseId(uri) }.getOrDefault(-1L)
                if (id >= 0L) MediaStore.Images.Thumbnails.getThumbnail(
                    context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null
                ) else null
            }
        } catch (_: Throwable) { null }

        if (bitmap == null) null else DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            // Coil 2 exposes MEMORY, DISK and NETWORK only. Use NETWORK here to let Coil's
            // disk-cache interceptor persist this MediaStore-generated bitmap by diskCacheKey;
            // the underlying fetch itself is still local and runs on Dispatchers.IO.
            dataSource = DataSource.NETWORK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "content") return null
            if (data.authority != "media" && data.authority != MediaStore.AUTHORITY) return null
            val hasImagePath = data.pathSegments.any { it.equals("images", ignoreCase = true) }
            if (!hasImagePath) {
                val mimeType = runCatching { context.contentResolver.getType(data) }.getOrNull()
                if (mimeType?.startsWith("image/") != true) return null
            }
            val width = options.size.width as? Dimension.Pixels ?: return null
            val height = options.size.height as? Dimension.Pixels ?: return null
            if (width.px <= 600 && height.px <= 600) {
                return MediaStoreThumbnailFetcher(context.applicationContext, data, options)
            }
            return null
        }
    }
}
