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
    private val priorityQueue = java.util.ArrayDeque<MediaItem>()
    private val queuedPriorityUris = HashSet<String>()

    /**
     * Moves the thumbnails most likely to be seen next to the front of the single IO worker.
     * Only a bounded window is retained; replacing it on direction changes drops stale predictions
     * without creating a second competing decoder or unbounded queue.
     */
    fun prioritize(items: List<MediaItem>) {
        if (items.isEmpty()) return
        synchronized(lock) {
            if (worker?.isActive != true || activeUris.isNullOrEmpty()) return
            priorityQueue.clear()
            queuedPriorityUris.clear()
            items.asSequence().distinctBy { it.uri }.take(120).forEach { item ->
                if (queuedPriorityUris.add(item.uri)) priorityQueue.addLast(item)
            }
        }
    }

    fun start(context: Context, media: List<MediaItem>) {
        val appContext = context.applicationContext
        val uris = media.map { it.uri }

        synchronized(lock) {
            if (uris.isEmpty()) {
                worker?.cancel()
                worker = null
                priorityQueue.clear()
                queuedPriorityUris.clear()
                activeUris = emptyList()
                completedUris = emptyList()
                return
            }
            if (activeUris == uris && worker?.isActive == true) return
            if (completedUris == uris) return

            worker?.cancel()
            priorityQueue.clear()
            queuedPriorityUris.clear()
            activeUris = uris
            completedUris = null
            worker = scope.launch {
                var completedAll = true
                var naturalIndex = 0
                var processedCount = 0
                try {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val item = synchronized(lock) {
                            when {
                                activeUris !== uris -> null
                                priorityQueue.isNotEmpty() -> {
                                    val prioritized = priorityQueue.removeFirst()
                                    queuedPriorityUris.remove(prioritized.uri)
                                    prioritized
                                }
                                naturalIndex < media.size -> media[naturalIndex++]
                                else -> null
                            }
                        } ?: break

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
                        processedCount++
                        // Regularly yield so visible requests and other IO work can run promptly.
                        if (processedCount % 8 == 0) yield()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } finally {
                    synchronized(lock) {
                        if (activeUris === uris) {
                            worker = null
                            priorityQueue.clear()
                            queuedPriorityUris.clear()
                            if (completedAll) completedUris = uris
                        }
                    }
                }
            }
        }
    }
}
