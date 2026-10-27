package com.amresalehin.emregallery.face

import android.content.Context
import android.net.Uri
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

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

        private val lastEngineFailures = ConcurrentHashMap<String, String>()

        fun queryEngineInfo(context: Context, factory: FaceEngineFactory = BuffaloLFaceEngineFactory): Pair<String, String?> {
            val installed = factory.isInstalled(context)
            val failure = lastEngineFailures[factory.engineId]
            return if (!installed) {
                factory.displayName to "${factory.displayName} model pack not installed. Please download the model to index faces."
            } else if (failure != null) {
                factory.displayName to failure
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
        recognitionDistance: Float = FaceRecognitionSettings.DEFAULT_RECOGNITION_DISTANCE,
        forceRescanUris: Set<String> = emptySet()
    ): FaceIndexingProgress = withContext(Dispatchers.IO) {
        val photos = media.filterNot { it.isVideo }
        val engineId = engineFactory.engineId
        val pending = photos.filter { it.uri in forceRescanUris || !store.isMediaIndexed(it.uri, engineId) }
        var processed = 0
        var facesFound = 0
        var failed = 0
        var engine: FaceEngine? = null

        try {
            if (pending.isNotEmpty()) {
                if (!engineFactory.isInstalled(context)) {
                    val msg = "${engineFactory.displayName} is not installed. Please download the model."
                    lastEngineFailures[engineId] = msg
                    activeEngineDescription = "${engineFactory.displayName} (Not downloaded)"
                    error(msg)
                }

                engine = try {
                    val created = engineFactory.create(context, detectionThreshold)
                    activeEngineDescription = engineFactory.displayName
                    lastEngineFailures.remove(engineId)
                    created
                } catch (e: Exception) {
                    val msg = "${engineFactory.displayName} initialization failed: " + (e.localizedMessage ?: e.message ?: "Unknown error")
                    lastEngineFailures[engineId] = msg
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
                        if (engineFactory.clusteringStrategy != null) {
                            store.addRawFaceObservation(
                                mediaUri = item.uri,
                                faceIndex = face.faceIndex,
                                rawEmbedding = face.embedding,
                                bounds = face.bounds,
                                confidence = face.score,
                                quality = face.quality,
                                provenance = provenance,
                                qualityMetrics = face.qualityMetrics
                            )
                        } else {
                            store.addFace(
                                mediaUri = item.uri,
                                faceIndex = face.faceIndex,
                                rawEmbedding = face.embedding,
                                maxDistance = recognitionDistance,
                                bounds = face.bounds,
                                confidence = face.score,
                                quality = face.quality,
                                provenance = provenance,
                                qualityMetrics = face.qualityMetrics
                            )
                        }
                    }
                    store.markMediaIndexed(item.uri, detections.size, engineId)
                    store.pruneStaleFacesForMedia(item.uri, engineId, detections.size)
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

            // If engine uses a dedicated clustering strategy (e.g. Ente linear incremental), execute it now
            if (pending.isNotEmpty() && engineFactory.clusteringStrategy != null) {
                engineFactory.clusteringStrategy?.clusterFaces(
                    store = store,
                    mediaUris = pending.map { it.uri },
                    engineId = engineFactory.engineId,
                    modelVersion = engineFactory.modelVersion,
                    embeddingDimension = engineFactory.embeddingDimension,
                    maxDistance = recognitionDistance
                )
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
        val engineId = engineFactory.engineId
        try {
            engineFactory.downloadModel(context, force, onProgress, onStage, awaitIfResumed)
            lastEngineFailures.remove(engineId)
            activeEngineDescription = engineFactory.displayName
        } catch (e: Exception) {
            lastEngineFailures[engineId] = e.localizedMessage ?: "Download failed"
            throw e
        }
    }
}
