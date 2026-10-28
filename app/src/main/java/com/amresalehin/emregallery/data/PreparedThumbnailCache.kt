package com.amresalehin.emregallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.StatFs
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk-backed thumbnail preparation for the gallery.
 *
 * The UI continues to draw each cell immediately. This store keeps decoded thumbnails out of
 * long-lived RAM: requests first reuse a prepared WebP file, while a background job gradually
 * prepares every MediaStore item after permission is granted.
 */
object PreparedThumbnailCache {
    private const val DIRECTORY_NAME = "prepared_thumbnails"
    private const val FREE_SPACE_RESERVE_BYTES = 768L * 1024L * 1024L
    private const val WEBP_QUALITY = 80
    private const val PREWARM_WIDTH = 420
    private const val PREWARM_HEIGHT = 420

    private val uriLocks = ConcurrentHashMap<String, Mutex>()

    data class ThumbnailResult(
        val bitmap: Bitmap,
        val fromPreparedDiskCache: Boolean
    )

    fun hasPrepared(context: Context, uri: String, widthPx: Int = PREWARM_WIDTH, heightPx: Int = PREWARM_HEIGHT): Boolean {
        val file = cacheFile(context.applicationContext, uri, widthPx, heightPx)
        if (!file.isFile || file.length() <= 0L) return false

        // Validate the WebP header without allocating a decoded bitmap.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }
        if (bounds.outWidth > 0 && bounds.outHeight > 0) return true

        runCatching { file.delete() }
        return false
    }

    fun hasStorageHeadroom(context: Context): Boolean = runCatching {
        StatFs(context.applicationContext.cacheDir.absolutePath).availableBytes > FREE_SPACE_RESERVE_BYTES
    }.getOrDefault(false)

    /**
     * Returns a prepared thumbnail when present. On a miss, creates the Android MediaStore
     * thumbnail on IO and atomically writes a compact WebP copy, unless the free-space reserve
     * would be violated. Per-URI/size locks prevent the UI request and background prewarmer from
     * generating the same thumbnail concurrently.
     */
    suspend fun loadOrCreate(
        context: Context,
        uri: Uri,
        widthPx: Int,
        heightPx: Int
    ): ThumbnailResult? {
        val appContext = context.applicationContext
        val key = cacheKey(uri.toString(), widthPx, heightPx)
        val file = File(cacheDirectory(appContext), "$key.webp")

        readPrepared(file)?.let { return ThumbnailResult(it, fromPreparedDiskCache = true) }

        val mutex = uriLocks.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            readPrepared(file)?.let { return@withLock ThumbnailResult(it, fromPreparedDiskCache = true) }

            val bitmap = loadMediaStoreThumbnail(appContext, uri, widthPx, heightPx)
                ?: return@withLock null
            writePreparedIfSafe(appContext, file, bitmap)
            ThumbnailResult(bitmap, fromPreparedDiskCache = false)
        }
    }

    /**
     * Prepares one item and immediately releases its bitmap. This intentionally does not warm
     * every decoded image into Coil's memory cache, which would make large libraries consume
     * unbounded RAM and cause memory pressure/GC during scrolling.
     */
    suspend fun prepareIfMissing(context: Context, uri: String): Boolean {
        if (hasPrepared(context, uri)) return true
        if (!hasStorageHeadroom(context)) return false

        val result = loadOrCreate(context, Uri.parse(uri), PREWARM_WIDTH, PREWARM_HEIGHT)
        result?.bitmap?.let { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        return hasPrepared(context, uri)
    }

    private fun cacheDirectory(context: Context): File =
        File(context.cacheDir, DIRECTORY_NAME)

    private fun cacheFile(context: Context, uri: String, widthPx: Int, heightPx: Int): File {
        val normalizedWidth = widthPx.coerceIn(96, 512)
        val normalizedHeight = heightPx.coerceIn(96, 512)
        val key = cacheKey(uri, normalizedWidth, normalizedHeight)
        return File(cacheDirectory(context), "$key.webp")
    }

    private fun cacheKey(uri: String, widthPx: Int, heightPx: Int): String {
        val normalizedWidth = widthPx.coerceIn(96, 512)
        val normalizedHeight = heightPx.coerceIn(96, 512)
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$uri|$normalizedWidth|$normalizedHeight".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun readPrepared(file: File): Bitmap? {
        if (!file.isFile || file.length() <= 0L) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (bitmap == null) {
            runCatching { file.delete() }
        } else {
            runCatching { file.setLastModified(System.currentTimeMillis()) }
        }
        return bitmap
    }

    private fun writePreparedIfSafe(context: Context, file: File, bitmap: Bitmap) {
        if (!hasStorageHeadroom(context)) return
        val parent = file.parentFile ?: return
        if (!parent.exists() && !parent.mkdirs()) return

        val temporary = File(parent, file.nameWithoutExtension + ".tmp")
        runCatching {
            temporary.delete()
            FileOutputStream(temporary).use { stream ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                if (!bitmap.compress(format, WEBP_QUALITY, stream)) {
                    throw IllegalStateException("Could not encode prepared thumbnail")
                }
            }

            if (!temporary.renameTo(file)) {
                // A prior file can exist after an interrupted write or concurrent process restart.
                file.delete()
                if (!temporary.renameTo(file)) {
                    temporary.delete()
                    return@runCatching
                }
            }
            file.setLastModified(System.currentTimeMillis())
        }
        if (temporary.exists()) runCatching { temporary.delete() }
    }

    private suspend fun loadMediaStoreThumbnail(
        context: Context,
        uri: Uri,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val targetWidth = widthPx.coerceIn(96, 512)
        val targetHeight = heightPx.coerceIn(96, 512)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val signal = CancellationSignal()
                val job = currentCoroutineContext()[Job]
                val cancellationHandle = job?.invokeOnCompletion { cause ->
                    if (cause is CancellationException) signal.cancel()
                }
                try {
                    context.contentResolver.loadThumbnail(uri, Size(targetWidth, targetHeight), signal)
                } finally {
                    cancellationHandle?.dispose()
                }
            } else {
                val id = runCatching { android.content.ContentUris.parseId(uri) }.getOrDefault(-1L)
                if (id < 0L) {
                    null
                } else {
                    val isVideo = uri.path?.contains("/video/") == true
                    if (isVideo) {
                        MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
                        )
                    } else {
                        MediaStore.Images.Thumbnails.getThumbnail(
                            context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cancelled: OperationCanceledException) {
            throw CancellationException("Thumbnail fetch cancelled", cancelled)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Process-lifetime, single-worker prewarmer. It starts after gallery permission is granted,
 * skips files already prepared, and can safely resume from the first missing thumbnail next launch.
 */
object ThumbnailPrewarmManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var worker: Job? = null
    private var activeUris: List<String>? = null
    private var completedUris: List<String>? = null

    fun start(context: Context, media: List<MediaItem>) {
        val appContext = context.applicationContext
        val uris = media.map { it.uri }

        synchronized(lock) {
            if (uris.isEmpty()) {
                worker?.cancel()
                worker = null
                activeUris = emptyList()
                completedUris = emptyList()
                return
            }
            if (activeUris == uris && worker?.isActive == true) return
            if (completedUris == uris) return

            worker?.cancel()
            activeUris = uris
            completedUris = null
            worker = scope.launch {
                var completedAll = true
                try {
                    for ((index, item) in media.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        if (!PreparedThumbnailCache.hasPrepared(appContext, item.uri)) {
                            if (!PreparedThumbnailCache.hasStorageHeadroom(appContext)) {
                                completedAll = false
                                break
                            }
                            val prepared = try {
                                PreparedThumbnailCache.prepareIfMissing(appContext, item.uri)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                false
                            }
                            if (!prepared) completedAll = false
                        }
                        // Regularly yield so visible requests and other IO work can run promptly.
                        if (index % 8 == 0) yield()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } finally {
                    synchronized(lock) {
                        if (activeUris === uris) {
                            worker = null
                            if (completedAll) completedUris = uris
                        }
                    }
                }
            }
        }
    }
}
