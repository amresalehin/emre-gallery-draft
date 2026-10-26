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
 * Clean, decoupled media face indexing repository.
 * Operates with any FaceEngine implementation supplied via constructor injection.
 */
class FaceIndexingRepository(
    private val context: Context,
    private val engineFactory: FaceEngineFactory = BuffaloLFaceEngineFactory
) {

    companion object {
        @Volatile
        var activeEngineDescription: String = "Detecting..."
            private set

        @Volatile
        var lastPrimaryFailure: String? = null

        fun queryEngineInfo(context: Context, factory: FaceEngineFactory = BuffaloLFaceEngineFactory): Pair<String, String?> {
            val installed = factory.isInstalled(context)
            return if (!installed) {
                factory.displayName to "${factory.displayName} model pack not installed. Please download the model to index faces."
            } else if (lastPrimaryFailure != null) {
                factory.displayName to lastPrimaryFailure
            } else {
                factory.displayName to null
            }
        }
    }

    suspend fun indexNewMedia(
        media: List<MediaItem>,
        store: FaceClusterStore,
        onProgress: suspend (FaceIndexingProgress) -> Unit = {},
        awaitIfResumed: suspend () -> Unit = {},
        detectionThreshold: Float = FaceRecognitionSettings.DEFAULT_DETECTION_CONFIDENCE,
        recognitionDistance: Float = FaceRecognitionSettings.DEFAULT_RECOGNITION_DISTANCE
    ): FaceIndexingProgress = withContext(Dispatchers.IO) {
        val photos = media.filterNot { it.isVideo }
        val engineId = engineFactory.engineId
        val pending = photos.filterNot { store.isMediaIndexed(it.uri, engineId) }
        var processed = 0
        var facesFound = 0
        var failed = 0
        var engine: FaceEngine? = null

        try {
            if (pending.isNotEmpty()) {
                if (!engineFactory.isInstalled(context)) {
                    val msg = "${engineFactory.displayName} is not installed. Please download the model."
                    lastPrimaryFailure = msg
                    activeEngineDescription = "${engineFactory.displayName} (Not downloaded)"
                    error(msg)
                }

                engine = try {
                    val created = engineFactory.create(context, detectionThreshold)
                    activeEngineDescription = engineFactory.displayName
                    lastPrimaryFailure = null
                    created
                } catch (e: Exception) {
                    val msg = "${engineFactory.displayName} initialization failed: " + (e.localizedMessage ?: e.message ?: "Unknown error")
                    lastPrimaryFailure = msg
                    activeEngineDescription = "${engineFactory.displayName} (Initialization failed)"
                    throw e
                }
            }

            val provenance = EmbeddingProvenance(
                engineId = engineFactory.engineId,
                modelVersion = engineFactory.modelVersion,
                dimension = engineFactory.embeddingDimension
            )

            for (item in pending) {
                awaitIfResumed()
                currentCoroutineContext().ensureActive()
                try {
                    val activeEngine = engine ?: error("Face recognition engine is not ready.")
                    val detections = activeEngine.embed(Uri.parse(item.uri))
                    detections.forEach { face ->
                        store.addFace(
                            mediaUri = item.uri,
                            faceIndex = face.faceIndex,
                            rawEmbedding = face.embedding,
                            maxDistance = recognitionDistance,
                            bounds = face.bounds,
                            confidence = face.score,
                            quality = face.quality,
                            provenance = provenance
                        )
                    }
                    store.markMediaIndexed(item.uri, detections.size, engineId)
                    facesFound += detections.size
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    store.markMediaFailed(item.uri, engineId)
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
        force: Boolean = false,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        onStage: (String) -> Unit = {},
        awaitIfResumed: suspend () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        engineFactory.downloadModel(context, force, onProgress, onStage, awaitIfResumed)
        lastPrimaryFailure = null
        activeEngineDescription = engineFactory.displayName
    }
}
