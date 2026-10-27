package com.amresalehin.emregallery.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.system.Os
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

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

        val target = ThumbnailRequestFactory.sizeFor(width.px, height.px)
        val mediaSizeBytes = ThumbnailRequestFactory.rememberedMediaSize(uri)
            ?: queryMediaSize(context, uri).also { ThumbnailRequestFactory.rememberMediaSize(uri, it) }
        val key = ThumbnailRequestFactory.cacheKey(
            uri, false, target.width, target.height, mediaSizeBytes
        )
        val cache = StillThumbnailDiskCache(context)
        val bitmap = cache.loadOrCreate(key) {
            loadFromMediaStore(uri, target)
        } ?: return@withContext null

        DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            // Both MediaStore and this cache are local sources. Coil's DataSource enum does not
            // control persistence for DrawableResult; StillThumbnailDiskCache owns that behaviour.
            dataSource = DataSource.DISK
        )
    }

    private fun loadFromMediaStore(uri: Uri, target: ThumbnailSize): Bitmap? {
        val bitmap: Bitmap = (try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(target.width, target.height), null)
            } else {
                val id = runCatching { ContentUris.parseId(uri) }.getOrDefault(-1L)
                if (id >= 0L) {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null
                    )
                } else null
            }
        } catch (_: Throwable) {
            null
        }) ?: return null

        return scaleWithinBitmap(bitmap, target.width, target.height)
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

private class StillThumbnailDiskCache(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "media_thumbnails")

    fun loadOrCreate(key: String, loadFromMediaStore: () -> Bitmap?): Bitmap? {
        val file = fileFor(key)
        if (isUsable(file)) {
            val cached = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            if (cached != null && cached.width > 0 && cached.height > 0) {
                file.setLastModified(System.currentTimeMillis())
                return cached
            }
            file.delete()
        }

        val bitmap = loadFromMediaStore() ?: return null
        if (writeAtomically(file, bitmap)) trimCache()
        return bitmap
    }

    private fun writeAtomically(target: File, bitmap: Bitmap): Boolean {
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) return false
        val temporary = File(directory, target.name + "." + System.nanoTime() + ".tmp")
        var copyToWrite: Bitmap? = null
        val source: Bitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            copyToWrite = runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            copyToWrite ?: return false
        } else bitmap

        return try {
            val compressed = FileOutputStream(temporary).use { output ->
                val ok = source.compress(Bitmap.CompressFormat.WEBP, 86, output)
                output.flush()
                output.fd.sync()
                ok
            }
            if (!compressed) {
                temporary.delete()
                return false
            }
            Os.rename(temporary.absolutePath, target.absolutePath)
            true
        } catch (_: Throwable) {
            temporary.delete()
            false
        } finally {
            copyToWrite?.takeIf { it !== bitmap }?.recycle()
        }
    }

    private fun trimCache() {
        synchronized(EVICTION_LOCK) {
            val entries = directory.listFiles { file -> file.isFile && file.extension == "webp" } ?: return
            var total = entries.sumOf { it.length() }
            for (file in entries.sortedBy { it.lastModified() }) {
                if (total <= MAX_CACHE_BYTES) break
                val bytes = file.length()
                if (file.delete()) total -= bytes
            }
        }
    }

    private fun fileFor(key: String): File = File(directory, sha256(key) + ".webp")
    private fun isUsable(file: File): Boolean = file.isFile && file.length() > 0L

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                append(((byte.toInt() and 0xff) + 0x100).toString(16).substring(1))
            }
        }
    }

    private companion object {
        const val MAX_CACHE_BYTES = 128L * 1024L * 1024L
        val EVICTION_LOCK = Any()
    }
}

private fun scaleWithinBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    val scale = min(
        maxWidth.toFloat() / bitmap.width.coerceAtLeast(1).toFloat(),
        maxHeight.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
    ).coerceAtMost(1f)
    if (scale >= 0.999f) return bitmap
    val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
    if (scaled !== bitmap) bitmap.recycle()
    return scaled
}

private fun queryMediaSize(context: Context, uri: Uri): Long = runCatching {
    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0).coerceAtLeast(0L) else 0L
    } ?: 0L
}.getOrDefault(0L)
