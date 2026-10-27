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

enum class StageState {
    PENDING,
    RUNNING,
    COMPLETED,
    PAUSED,
    FAILED
}

data class StageProgress(
    val state: StageState = StageState.PENDING,
    val processed: Int = 0,
    val total: Int = 0,
    val percentage: Int = 0
)

data class FaceIndexingProgress(
    val processed: Int = 0,
    val total: Int = 0,
    val facesFound: Int = 0,
    val failed: Int = 0,
    val currentItemName: String = "",
    val engineId: String = BuffaloLFaceEngine.ENGINE_ID,
    val engineName: String = "",
    val detectionStage: StageProgress = StageProgress(),
    val embeddingStage: StageProgress = StageProgress(),
    val clusteringStage: StageProgress = StageProgress(),
    val failedItems: List<Pair<String, String>> = emptyList()
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
        forceRescanUris: Set<String> = emptySet(),
        targetUrisOnly: Set<String>? = null
    ): FaceIndexingProgress = withContext(Dispatchers.IO) {
        val photos = media.filterNot { it.isVideo }
        val engineId = engineFactory.engineId
        val candidates = if (targetUrisOnly != null) {
            photos.filter { it.uri in targetUrisOnly }
        } else photos

        val pending = candidates.filter { it.uri in forceRescanUris || !store.isMediaIndexed(it.uri, engineId) }
        var processed = 0
        var facesFound = 0
        var failed = 0
        val failedList = mutableListOf<Pair<String, String>>()
        var engine: FaceEngine? = null

        val totalPending = pending.size
        var detectionStage = StageProgress(
            state = if (totalPending > 0) StageState.RUNNING else StageState.COMPLETED,
            processed = 0,
            total = totalPending,
            percentage = 0
        )
        var embeddingStage = StageProgress(
            state = if (totalPending > 0) StageState.PENDING else StageState.COMPLETED,
            processed = 0,
            total = totalPending,
            percentage = 0
        )
        var clusteringStage = StageProgress(
            state = if (totalPending > 0) StageState.PENDING else StageState.COMPLETED,
            processed = 0,
            total = totalPending,
            percentage = 0
        )

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

            for ((index, item) in pending.withIndex()) {
                awaitIfResumed()
                currentCoroutineContext().ensureActive()

                val itemName = item.name.ifBlank { item.uri.substringAfterLast('/') }
                val currentPct = if (totalPending > 0) (index * 100 / totalPending) else 0

                detectionStage = StageProgress(StageState.RUNNING, index, totalPending, currentPct)
                embeddingStage = StageProgress(StageState.RUNNING, index, totalPending, currentPct)

                onProgress(
                    FaceIndexingProgress(
                        processed = processed,
                        total = totalPending,
                        facesFound = facesFound,
                        failed = failed,
                        currentItemName = itemName,
                        engineId = engineId,
                        engineName = engineFactory.displayName,
                        detectionStage = detectionStage,
                        embeddingStage = embeddingStage,
                        clusteringStage = clusteringStage,
                        failedItems = failedList.toList()
                    )
                )

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
                } catch (err: Exception) {
                    val errorDetail = err.localizedMessage ?: "Detection/embedding failed"
                    store.markMediaFailed(item.uri, engineId, errorDetail)
                    failedList.add(item.uri to errorDetail)
                    failed++
                } finally {
                    processed++
                    val completedPct = if (totalPending > 0) (processed * 100 / totalPending) else 100
                    detectionStage = StageProgress(
                        state = if (processed >= totalPending) StageState.COMPLETED else StageState.RUNNING,
                        processed = processed,
                        total = totalPending,
                        percentage = completedPct
                    )
                    embeddingStage = StageProgress(
                        state = if (processed >= totalPending) StageState.COMPLETED else StageState.RUNNING,
                        processed = processed,
                        total = totalPending,
                        percentage = completedPct
                    )

                    onProgress(
                        FaceIndexingProgress(
                            processed = processed,
                            total = totalPending,
                            facesFound = facesFound,
                            failed = failed,
                            currentItemName = itemName,
                            engineId = engineId,
                            engineName = engineFactory.displayName,
                            detectionStage = detectionStage,
                            embeddingStage = embeddingStage,
                            clusteringStage = clusteringStage,
                            failedItems = failedList.toList()
                        )
                    )
                }
            }

            // Dedicated clustering strategy execution
            if (pending.isNotEmpty() && engineFactory.clusteringStrategy != null) {
                clusteringStage = StageProgress(StageState.RUNNING, 0, totalPending, 0)
                onProgress(
                    FaceIndexingProgress(
                        processed = processed,
                        total = totalPending,
                        facesFound = facesFound,
                        failed = failed,
                        currentItemName = "Clustering facial embeddings…",
                        engineId = engineId,
                        engineName = engineFactory.displayName,
                        detectionStage = detectionStage,
                        embeddingStage = embeddingStage,
                        clusteringStage = clusteringStage,
                        failedItems = failedList.toList()
                    )
                )

                engineFactory.clusteringStrategy?.clusterFaces(
                    store = store,
                    mediaUris = pending.map { it.uri },
                    engineId = engineFactory.engineId,
                    modelVersion = engineFactory.modelVersion,
                    embeddingDimension = engineFactory.embeddingDimension,
                    maxDistance = recognitionDistance
                )

                clusteringStage = StageProgress(StageState.COMPLETED, totalPending, totalPending, 100)
            } else if (totalPending > 0) {
                clusteringStage = StageProgress(StageState.COMPLETED, totalPending, totalPending, 100)
            }
        } finally {
            engine?.close()
        }

        val finalProgress = FaceIndexingProgress(
            processed = processed,
            total = totalPending,
            facesFound = facesFound,
            failed = failed,
            currentItemName = "",
            engineId = engineId,
            engineName = engineFactory.displayName,
            detectionStage = detectionStage,
            embeddingStage = embeddingStage,
            clusteringStage = clusteringStage,
            failedItems = failedList.toList()
        )
        onProgress(finalProgress)
        finalProgress
    }

    suspend fun downloadModel(
        force: Boolean = false,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        onStage: (String) -> Unit = {},
        awaitIfResumed: suspend () -> Unit = {},
        onDetailedProgress: (DetailedDownloadProgress) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val engineId = engineFactory.engineId
        try {
            engineFactory.downloadModel(context, force, onProgress, onStage, awaitIfResumed, onDetailedProgress)
            lastEngineFailures.remove(engineId)
            activeEngineDescription = engineFactory.displayName
        } catch (e: Exception) {
            lastEngineFailures[engineId] = e.localizedMessage ?: "Download failed"
            throw e
        }
    }
}
