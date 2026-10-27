package com.amresalehin.emregallery.data

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.request.SuccessResult
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Persistent thumbnail cache limits scale with the visible library size. The hard caps prevent
 * unbounded storage growth, while the per-item estimates usually keep one grid-sized rendition
 * for every image and video.
 */
object ThumbnailCacheBudget {
    private const val KIB = 1024L
    private const val MIB = 1024L * KIB
    private const val GIB = 1024L * MIB

    @Volatile var stillMaxBytes: Long = 2L * GIB
        private set
    @Volatile var videoMaxBytes: Long = 512L * MIB
        private set

    @Synchronized
    fun configure(media: List<MediaItem>) {
        val stillCount = media.count { !it.isVideo }.toLong()
        val videoCount = media.size.toLong() - stillCount
        stillMaxBytes = (stillCount * 48L * KIB).coerceIn(128L * MIB, 2L * GIB)
        videoMaxBytes = (videoCount * 80L * KIB).coerceIn(64L * MIB, 512L * MIB)
    }
}

/**
 * Warms the full default-visible gallery independently from LazyGrid composition or scrolling.
 * It runs sequentially to limit concurrent decode pressure, first preparing opening tiles, then processing
 * the rest of the library. Partial runs resume cheaply because each completed rendition is skipped.
 */
object ThumbnailWarmupManager {
    private const val WARMUP_VERSION = 1
    private const val TILE_HORIZONTAL_PADDING_DP = 24f
    private const val TILE_SPACING_DP = 4f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var activeRequestKey: String? = null
    private var activeJob: Job? = null

    fun start(
        context: Context,
        media: List<MediaItem>,
        gridColumns: Int,
        gridStyle: String,
        sortOrder: String
    ) {
        if (media.isEmpty()) return
        val appContext = context.applicationContext
        ThumbnailCacheBudget.configure(media)
        val columns = gridColumns.coerceIn(1, 6)
        // GalleryApp calls this on Dispatchers.IO because the signature scans the library list.
        val requestKey = quickRequestKey(media, columns, gridStyle, sortOrder)

        synchronized(lock) {
            if (activeRequestKey == requestKey && activeJob?.isActive == true) return
            activeJob?.cancel()
            activeRequestKey = requestKey
            activeJob = scope.launch {
                try {
                    prepareLibrary(appContext, media, columns, gridStyle, sortOrder)
                } finally {
                    synchronized(lock) {
                        if (activeRequestKey == requestKey) {
                            activeRequestKey = null
                            activeJob = null
                        }
                    }
                }
            }
        }
    }

    private suspend fun prepareLibrary(
        context: Context,
        media: List<MediaItem>,
        columns: Int,
        gridStyle: String,
        sortOrder: String
    ) {
        coroutineContext.ensureActive()
        val tileWidthPx = calculateTileWidthPx(context, columns)
        val ordered = sortForDisplay(media, sortOrder)
        val fingerprint = fingerprint(ordered, columns, gridStyle, sortOrder, tileWidthPx)
        // Keep the completion marker in noBackupFilesDir alongside the thumbnails. A restored
        // preferences backup must never claim that non-backed-up thumbnail files already exist.
        val marker = File(context.noBackupFilesDir, "thumbnail_cache/warmup_complete")
        if (marker.isFile && runCatching { marker.readText() }.getOrNull() == fingerprint) return

        val imageLoader = Coil.imageLoader(context)
        val priorityCount = minOf(ordered.size, (columns * 8).coerceIn(18, 48))
        val priorityItems = ordered.take(priorityCount)
        val priorityUris = priorityItems.mapTo(HashSet()) { it.uri }
        val backgroundItems = ordered.asSequence()
            .filterNot { it.uri in priorityUris }
            .sortedWith(compareBy<MediaItem> { timestampMillis(it) }.thenBy { it.id })
            .toList()
        val failedUris = HashSet<String>()

        // Opening tiles are warmed first, but the job continues after these succeed.
        for (item in priorityItems) {
            coroutineContext.ensureActive()
            if (!prepareOne(context, imageLoader, item, tileWidthPx, gridStyle, force = false)) {
                failedUris.add(item.uri)
            }
        }

        // This loop is independent from scroll position: eventually every listed image/video is
        // prepared even if the user never scrolls. Sequential work limits decoder/bitmap pressure.
        for (item in backgroundItems) {
            coroutineContext.ensureActive()
            if (!prepareOne(context, imageLoader, item, tileWidthPx, gridStyle, force = false)) {
                failedUris.add(item.uri)
            }
        }

        // Refresh the opening variants last so they are among the most-recent cache entries.
        for (item in priorityItems) {
            coroutineContext.ensureActive()
            if (prepareOne(context, imageLoader, item, tileWidthPx, gridStyle, force = true)) {
                failedUris.remove(item.uri)
            } else {
                failedUris.add(item.uri)
            }
        }

        if (failedUris.isEmpty()) {
            runCatching {
                marker.parentFile?.mkdirs()
                val temporary = File(marker.parentFile, marker.name + "." + System.nanoTime() + ".tmp")
                temporary.writeText(fingerprint)
                if (!temporary.renameTo(marker)) {
                    marker.writeText(fingerprint)
                    temporary.delete()
                }
            }
        }
    }

    private suspend fun prepareOne(
        context: Context,
        imageLoader: ImageLoader,
        item: MediaItem,
        tileWidthPx: Int,
        gridStyle: String,
        force: Boolean
    ): Boolean {
        val aspect = ThumbnailRequestFactory.tileAspect(item, gridStyle)
        val targetHeightPx = (tileWidthPx / aspect).roundToInt().coerceAtLeast(1)
        if (!force && ThumbnailRequestFactory.hasPersistentCache(
                context, item, tileWidthPx, targetHeightPx
            )
        ) return true

        return try {
            val result = imageLoader.execute(
                ThumbnailRequestFactory.create(
                    context = context,
                    item = item,
                    targetWidthPx = tileWidthPx,
                    targetHeightPx = targetHeightPx,
                    useMemoryCache = false
                )
            )
            result is SuccessResult && ThumbnailRequestFactory.hasPersistentCache(
                context, item, tileWidthPx, targetHeightPx
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
    }

    private fun calculateTileWidthPx(context: Context, columns: Int): Int {
        val metrics = context.resources.displayMetrics
        val paddingPx = (TILE_HORIZONTAL_PADDING_DP * metrics.density).roundToInt()
        val spacingPx = (TILE_SPACING_DP * metrics.density).roundToInt()
        return ((metrics.widthPixels - paddingPx - spacingPx * (columns - 1)) / columns)
            .coerceAtLeast(1)
    }

    private fun sortForDisplay(items: List<MediaItem>, sortOrder: String): List<MediaItem> =
        when (sortOrder) {
            "date_asc", "date_taken_asc" ->
                items.sortedWith(compareBy<MediaItem>({ timestampMillis(it) }, { it.id }))
            "date_added_asc" ->
                items.sortedWith(compareBy<MediaItem>({ it.dateAddedSeconds * 1000L }, { it.id }))
            "date_added_desc" ->
                items.sortedWith(compareByDescending<MediaItem> { it.dateAddedSeconds * 1000L }.thenBy { it.id })
            "name_asc" ->
                items.sortedWith(compareBy<MediaItem>({ it.name.lowercase(Locale.getDefault()) }, { it.id }))
            "name_desc" ->
                items.sortedWith(compareByDescending<MediaItem> { it.name.lowercase(Locale.getDefault()) }.thenBy { it.id })
            "size_asc" ->
                items.sortedWith(compareBy<MediaItem>({ it.sizeBytes }, { it.id }))
            "size_desc" ->
                items.sortedWith(compareByDescending<MediaItem> { it.sizeBytes }.thenBy { it.id })
            else ->
                items.sortedWith(compareByDescending<MediaItem> { timestampMillis(it) }.thenBy { it.id })
        }

    private fun timestampMillis(item: MediaItem): Long =
        if (item.dateTakenMillis > 0L) item.dateTakenMillis else item.dateAddedSeconds * 1000L

    private fun quickRequestKey(
        media: List<MediaItem>,
        columns: Int,
        gridStyle: String,
        sortOrder: String
    ): String {
        var listHash = 1
        var totalBytes = 0L
        for (item in media) {
            listHash = 31 * listHash + item.uri.hashCode()
            listHash = 31 * listHash + item.sizeBytes.hashCode()
            listHash = 31 * listHash + item.width
            listHash = 31 * listHash + item.height
            totalBytes += item.sizeBytes
        }
        return "${media.size}:$listHash:$totalBytes:$columns:$gridStyle:$sortOrder"
    }

    private fun fingerprint(
        ordered: List<MediaItem>,
        columns: Int,
        gridStyle: String,
        sortOrder: String,
        tileWidthPx: Int
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun append(value: String) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        append("warmup=$WARMUP_VERSION;cache=${ThumbnailRequestFactory.CACHE_POLICY_VERSION};columns=$columns;style=$gridStyle;sort=$sortOrder;width=$tileWidthPx")
        ordered.forEach { item ->
            append(item.uri)
            append(item.sizeBytes.toString())
            append(item.width.toString())
            append(item.height.toString())
            append(item.isVideo.toString())
            append(timestampMillis(item).toString())
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}
