package com.amresalehin.emregallery.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Explicit video-poster path. Primary output is the earliest decodable frame at time zero;
 * system-generated MediaStore thumbnails are only a marked fallback.
 */
class VideoPosterFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val width = options.size.width as? Dimension.Pixels ?: return@withContext null
        val height = options.size.height as? Dimension.Pixels ?: return@withContext null
        val target = ThumbnailRequestFactory.sizeFor(width.px, height.px)
        val mediaSizeBytes = ThumbnailRequestFactory.rememberedMediaSize(uri)
            ?: queryMediaSize(context, uri).also { ThumbnailRequestFactory.rememberMediaSize(uri, it) }
        val key = ThumbnailRequestFactory.cacheKey(uri, true, target.width, target.height, mediaSizeBytes)
        val cache = VideoPosterCache(context)
        var file = VideoPosterWork.load(key) { cache.getOrCreate(uri, target, key) }
            ?: return@withContext null

        var bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            bitmap?.recycle()
            cache.deleteFile(file)
            file = VideoPosterWork.load(key + ":repair") { cache.getOrCreate(uri, target, key) }
                ?: return@withContext null
            bitmap = BitmapFactory.decodeFile(file.absolutePath)
        }

        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            bitmap?.recycle()
            cache.deleteFile(file)
            null
        } else {
            DrawableResult(
                drawable = BitmapDrawable(context.resources, bitmap),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "content") return null
            val segments = data.pathSegments
            // MediaStore image URIs are common during fast scrolling; reject them from the
            // video fetcher without an extra ContentResolver MIME query.
            if (segments.any { it.equals("images", ignoreCase = true) }) return null
            val hasVideoPath = segments.any { it.equals("video", ignoreCase = true) }
            if (!hasVideoPath) {
                val mimeType = runCatching { context.contentResolver.getType(data) }.getOrNull()
                if (mimeType?.startsWith("video/") != true) return null
            }
            if (options.size.width !is Dimension.Pixels || options.size.height !is Dimension.Pixels) return null
            return VideoPosterFetcher(context.applicationContext, data, options)
        }
    }
}

private object VideoPosterWork {
    private val semaphore = Semaphore(2)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<File?>>()

    suspend fun load(key: String, producer: () -> File?): File? {
        val task = inFlight.computeIfAbsent(key) {
            scope.async { semaphore.withPermit { producer() } }
        }
        return try {
            task.await()
        } finally {
            if (task.isCompleted) inFlight.remove(key, task)
        }
    }
}

private class VideoPosterCache(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.noBackupFilesDir, "thumbnail_cache/videos")
    private val legacyDirectory = File(appContext.cacheDir, "video_posters")

    fun getOrCreate(uri: Uri, target: ThumbnailSize, key: String): File? {
        val frameZero = fileFor(key, "frame0")
        val fallback = fileFor(key, "fallback")
        val migratedFrame = !isUsable(frameZero) && migrateLegacyFile(frameZero)
        val migratedFallback = !isUsable(fallback) && migrateLegacyFile(fallback)
        if (migratedFrame || migratedFallback) trimCache()
        if (isUsable(frameZero)) {
            frameZero.setLastModified(System.currentTimeMillis())
            return frameZero
        }
        if (isUsable(fallback)) {
            fallback.setLastModified(System.currentTimeMillis())
            return fallback
        }
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) return null
        return generate(uri, target, frameZero, fallback)
    }

    fun deleteFile(file: File) { runCatching { file.delete() } }

    private fun generate(uri: Uri, target: ThumbnailSize, frameZero: File, fallback: File): File? {
        val retriever = MediaMetadataRetriever()
        var firstBitmap: Bitmap? = null
        var fallbackBitmap: Bitmap? = null
        try {
            retriever.setDataSource(appContext, uri)
            firstBitmap = extractFrame(retriever, 0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, target)
            if (firstBitmap == null) {
                fallbackBitmap = extractFrame(retriever, 100_000L, MediaMetadataRetriever.OPTION_CLOSEST, target)
            }
        } catch (_: Throwable) {
            // Corrupt or unsupported media follows the explicit fallback route below.
        } finally {
            runCatching { retriever.release() }
        }

        if (firstBitmap != null) {
            val bitmap = firstBitmap!!
            firstBitmap = null
            val saved = writeAtomically(frameZero, bitmap)
            bitmap.recycle()
            if (saved) { trimCache(); return frameZero }
        }
        if (fallbackBitmap != null) {
            val bitmap = fallbackBitmap!!
            fallbackBitmap = null
            val saved = writeAtomically(fallback, bitmap)
            bitmap.recycle()
            if (saved) { trimCache(); return fallback }
        }
        firstBitmap?.recycle()
        fallbackBitmap?.recycle()

        val systemThumbnail = loadMediaStoreFallback(uri, target)
        if (systemThumbnail != null) {
            val saved = writeAtomically(fallback, systemThumbnail)
            systemThumbnail.recycle()
            if (saved) { trimCache(); return fallback }
        }
        return null
    }

    private fun extractFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        option: Int,
        target: ThumbnailSize
    ): Bitmap? = runCatching {
        val sourceWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val sourceHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

        var bitmap: Bitmap? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            bitmap = runCatching {
                retriever.getScaledFrameAtTime(timeUs, option, target.width, target.height)
            }.getOrNull()
        }
        if (bitmap == null) bitmap = retriever.getFrameAtTime(timeUs, option)

        bitmap?.let { decoded ->
            var oriented = normalizeRotation(decoded, rotation, sourceWidth, sourceHeight)
            val scaled = scaleWithin(oriented, target.width, target.height)
            if (scaled !== oriented) oriented.recycle()
            oriented = scaled
            oriented
        }
    }.getOrNull()

    private fun normalizeRotation(bitmap: Bitmap, rotationDegrees: Int, sourceWidth: Int, sourceHeight: Int): Bitmap {
        val rotation = ((rotationDegrees % 360) + 360) % 360
        if ((rotation != 90 && rotation != 270) || sourceWidth <= 0 || sourceHeight <= 0) return bitmap

        val expectedAspect = sourceHeight.toFloat() / sourceWidth.toFloat()
        val currentAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
        val rotatedAspect = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1).toFloat()
        if (abs(currentAspect - expectedAspect) <= abs(rotatedAspect - expectedAspect)) return bitmap

        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleWithin(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
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

    private fun loadMediaStoreFallback(uri: Uri, target: ThumbnailSize): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                appContext.contentResolver.loadThumbnail(uri, Size(target.width, target.height), null)
            }.getOrNull()?.let { return it }
        }
        if (uri.authority != MediaStore.AUTHORITY && uri.authority != "media") return null
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
        return runCatching {
            MediaStore.Video.Thumbnails.getThumbnail(
                appContext.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
            )
        }.getOrNull()
    }

    private fun writeAtomically(target: File, bitmap: Bitmap): Boolean {
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) return false
        val temporary = File(directory, target.name + "." + System.nanoTime() + ".tmp")
        return try {
            val compressed = FileOutputStream(temporary).use { output ->
                val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 84, output)
                output.flush()
                output.fd.sync()
                ok
            }
            if (!compressed) {
                temporary.delete()
                return false
            }
            // Source and destination are siblings, so rename replaces the finished output atomically.
            Os.rename(temporary.absolutePath, target.absolutePath)
            true
        } catch (_: Throwable) {
            temporary.delete()
            false
        }
    }

    private fun migrateLegacyFile(target: File): Boolean {
        if (isUsable(target)) return false
        val legacy = File(legacyDirectory, target.name)
        if (!isUsable(legacy)) return false
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) return false
        return try {
            Os.rename(legacy.absolutePath, target.absolutePath)
            true
        } catch (_: Throwable) {
            runCatching {
                legacy.copyTo(target, overwrite = true)
                legacy.delete()
                true
            }.getOrDefault(false)
        }
    }

    private fun isUsable(file: File): Boolean = file.isFile && file.length() > 0L
    private fun fileFor(key: String, kind: String): File = File(directory, sha256("$key:$kind") + ".jpg")

    private fun trimCache() {
        synchronized(EVICTION_LOCK) {
            val entries = directory.listFiles { file -> file.isFile && file.extension == "jpg" } ?: return
            var total = entries.sumOf { it.length() }
            for (file in entries.sortedBy { it.lastModified() }) {
                if (total <= ThumbnailCacheBudget.videoMaxBytes) break
                val bytes = file.length()
                if (file.delete()) total -= bytes
            }
        }
    }

    private fun sha256(value: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val result = StringBuilder(bytes.size * 2)
        bytes.forEach { byte -> result.append(((byte.toInt() and 0xff) + 0x100).toString(16).substring(1)) }
        return result.toString()
    }

    private companion object {
        val EVICTION_LOCK = Any()
    }
}

private fun queryMediaSize(context: Context, uri: Uri): Long = runCatching {
    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0).coerceAtLeast(0L) else 0L
    } ?: 0L
}.getOrDefault(0L)
