package com.amresalehin.emregallery.face

import android.content.Context
import android.net.Uri
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FaceIndexingProgress(
    val processed: Int,
    val total: Int,
    val facesFound: Int,
    val failed: Int
)

/**
 * Incremental indexer: completed media are skipped on later runs, so adding new photos does not
 * require recomputing embeddings for the entire library.
 */
class FaceIndexingRepository(private val context: Context) {

    suspend fun indexNewMedia(
        media: List<MediaItem>,
        store: FaceClusterStore,
        onProgress: suspend (FaceIndexingProgress) -> Unit = {},
        awaitIfResumed: suspend () -> Unit = {}
    ): FaceIndexingProgress = withContext(Dispatchers.IO) {
        val photos = media.filterNot { it.isVideo }
        val pending = photos.filterNot { store.isMediaIndexed(it.uri) }
        var processed = 0
        var facesFound = 0
        var failed = 0
        var engine: BuffaloLFaceEngine? = null
        try {
            if (pending.isNotEmpty()) engine = BuffaloLFaceEngine.open(context)
            for (item in pending) {
                // Pause and cancellation take effect between images; an in-flight native
                // inference is allowed to complete before the next checkpoint.
                awaitIfResumed()
                currentCoroutineContext().ensureActive()
                try {
                    val detections = engine!!.embed(Uri.parse(item.uri))
                    detections.forEach { face ->
                        store.addFace(item.uri, face.faceIndex, face.embedding)
                    }
                    store.markMediaIndexed(item.uri, detections.size)
                    facesFound += detections.size
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    store.markMediaFailed(item.uri)
                    failed++
                } finally {
                    processed++
                    onProgress(FaceIndexingProgress(processed, pending.size, facesFound, failed))
                }
            }
        } finally {
            engine?.close()
        }
        FaceIndexingProgress(processed, pending.size, facesFound, failed)
    }

    suspend fun downloadModel(
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        BuffaloLFaceEngine.downloadModel(context, onProgress)
    }
}
