package com.amresalehin.emregallery.face

import android.content.Context
import android.net.Uri
import com.amresalehin.emregallery.data.MediaItem
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

enum class StageState { PENDING, RUNNING, COMPLETED, PAUSED, FAILED }

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
    val currentItemStatus: String = "",
    val groupingStatus: String = "",
    val currentItemFaces: Int? = null,
    val engineId: String = BuffaloLFaceEngine.ENGINE_ID,
    val engineName: String = "",
    val detectionStage: StageProgress = StageProgress(),
    val embeddingStage: StageProgress = StageProgress(),
    val clusteringStage: StageProgress = StageProgress(),
    val failedItems: List<Pair<String, String>> = emptyList()
)

/** Clean, decoupled media face indexing repository. */
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
            } else factory.displayName to null
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
        val candidates = if (targetUrisOnly != null) photos.filter { it.uri in targetUrisOnly } else photos
        val pending = candidates.filter { it.uri in forceRescanUris || !store.isMediaIndexed(it.uri, engineId) }
        var processed = 0
        var facesFound = 0
        var failed = 0
        val failedList = mutableListOf<Pair<String, String>>()
        var engine: FaceEngine? = null
        var groupingStatus: String? = null
        var lastCompletedItemName = ""
        var lastCompletedItemStatus = ""
        var lastCompletedItemFaces: Int? = null

        val totalPending = pending.size
        var detectionStage = StageProgress(
            if (totalPending > 0) StageState.RUNNING else StageState.COMPLETED,
            total = totalPending
        )
        var embeddingStage = StageProgress(
            if (totalPending > 0) StageState.PENDING else StageState.COMPLETED,
            total = totalPending
        )
        var clusteringStage = StageProgress(
            if (totalPending > 0) StageState.PENDING else StageState.COMPLETED,
            total = totalPending
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
                    engineFactory.create(context, detectionThreshold).also {
                        activeEngineDescription = engineFactory.displayName
                        lastEngineFailures.remove(engineId)
                    }
                } catch (e: Exception) {
                    val msg = "${engineFactory.displayName} initialization failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}"
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
                val itemName = item.name.ifBlank { it.uri.substringAfterLast('/') }
                val currentPct = if (totalPending > 0) index * 100 / totalPending else 0
                detectionStage = StageProgress(StageState.RUNNING, index, totalPending, currentPct)
                embeddingStage = StageProgress(StageState.PENDING, index, totalPending, currentPct)
                var lastItemStatus = "Scanning for faces…"
                var currentItemFacesCount: Int? = null

                fun progress() = FaceIndexingProgress(
                    processed, totalPending, facesFound, failed, itemName, lastItemStatus,
                    groupingStatus.orEmpty(), currentItemFacesCount, engineId, engineFactory.displayName,
                    detectionStage, embeddingStage, clusteringStage, failedList.toList()
                )
                onProgress(progress())

                try {
                    val activeEngine = engine ?: error("Face recognition engine is not ready.")
                    val rawDetections = activeEngine.detectFaces(Uri.parse(item.uri))
                    currentItemFacesCount = rawDetections.size
                    detectionStage = StageProgress(
                        if (index + 1 >= totalPending) StageState.COMPLETED else StageState.RUNNING,
                        index + 1, totalPending, percent(index + 1, totalPending)
                    )

                    val detections = if (rawDetections.isEmpty()) {
                        emptyList()
                    } else {
                        embeddingStage = StageProgress(StageState.RUNNING, index, totalPending, currentPct)
                        lastItemStatus = "${rawDetections.size} face${if (rawDetections.size > 1) "s" else ""} found, generating embeddings…"
                        onProgress(progress())
                        activeEngine.embedFaces(Uri.parse(item.uri), rawDetections)
                    }
                    currentCoroutineContext().ensureActive()

                    // One transaction commits observations, metrics, pruning, centroids, and checkpoint.
                    store.replaceMediaFacesAtomically(
                        mediaUri = item.uri,
                        observations = detections.map { face ->
                            FaceObservationReplacement(
                                faceIndex = face.faceIndex,
                                embedding = face.embedding,
                                bounds = face.bounds,
                                confidence = face.score,
                                quality = face.quality,
                                qualityMetrics = face.qualityMetrics
                            )
                        },
                        provenance = provenance,
                        maxDistance = recognitionDistance,
                        deferClustering = engineFactory.clusteringStrategy != null
                    )
                    facesFound += detections.size
                    lastItemStatus = if (detections.isEmpty()) "0 faces found" else
                        "Indexed ${detections.size} face${if (detections.size > 1) "s" else ""}"
                    embeddingStage = StageProgress(
                        if (index + 1 >= totalPending) StageState.COMPLETED else StageState.RUNNING,
                        index + 1, totalPending, percent(index + 1, totalPending)
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (err: Exception) {
                    val errorDetail = err.localizedMessage ?: err.message ?: "Detection/embedding failed"
                    // The previous committed generation remains intact; only its checkpoint is marked failed.
                    store.markMediaFailed(item.uri, engineId, errorDetail)
                    failedList.add(item.uri to errorDetail)
                    failed++
                    lastItemStatus = "Failed: $errorDetail"
                } finally {
                    processed++
                    lastCompletedItemName = itemName
                    lastCompletedItemStatus = lastItemStatus
                    lastCompletedItemFaces = currentItemFacesCount
                    val completedPct = percent(processed, totalPending)
                    detectionStage = StageProgress(
                        if (processed >= totalPending) StageState.COMPLETED else StageState.RUNNING,
                        processed, totalPending, completedPct
                    )
                    embeddingStage = StageProgress(
                        if (processed >= totalPending) StageState.COMPLETED else StageState.RUNNING,
                        processed, totalPending, completedPct
                    )
                    onProgress(progress())
                }
            }

            val strategy = engineFactory.clusteringStrategy
            if (strategy != null) {
                val revision = groupingRevision(strategy.strategyVersion, recognitionDistance)
                val faceTotal = store.faceEmbeddingCount(engineId, engineFactory.modelVersion)
                if (store.isGroupingRequired(engineId, engineFactory.modelVersion, revision)) {
                    store.markGroupingPending(engineId, engineFactory.modelVersion, revision, faceTotal)
                    clusteringStage = StageProgress(StageState.RUNNING, 0, faceTotal, 0)
                    groupingStatus = "Grouping people…"
                    onProgress(FaceIndexingProgress(
                        processed, totalPending, facesFound, failed, lastCompletedItemName,
                        lastCompletedItemStatus, groupingStatus.orEmpty(), lastCompletedItemFaces,
                        engineId, engineFactory.displayName, detectionStage, embeddingStage,
                        clusteringStage, failedList.toList()
                    ))
                    try {
                        val groupedPeople = strategy.clusterFaces(
                            store = store, mediaUris = pending.map { it.uri }, engineId = engineId,
                            modelVersion = engineFactory.modelVersion,
                            embeddingDimension = engineFactory.embeddingDimension,
                            maxDistance = recognitionDistance,
                            onProgress = { done, total ->
                                if (Thread.currentThread().isInterrupted) throw CancellationException("Grouping cancelled.")
                                val safeTotal = total.coerceAtLeast(0)
                                val safeDone = done.coerceIn(0, safeTotal)
                                clusteringStage = StageProgress(
                                    StageState.RUNNING, safeDone, safeTotal,
                                    if (safeTotal > 0) (safeDone * 100 / safeTotal).coerceIn(0, 99) else 100
                                )
                                groupingStatus = if (safeTotal > 0) "Grouping people… $safeDone/$safeTotal faces" else "Grouping people…"
                                runBlocking {
                                    onProgress(FaceIndexingProgress(
                                        processed, totalPending, facesFound, failed, lastCompletedItemName,
                                        lastCompletedItemStatus, groupingStatus.orEmpty(), lastCompletedItemFaces,
                                        engineId, engineFactory.displayName, detectionStage, embeddingStage,
                                        clusteringStage, failedList.toList()
                                    ))
                                }
                            }
                        )
                        store.ensurePersonCovers(engineId)
                        val finalCount = store.faceEmbeddingCount(engineId, engineFactory.modelVersion)
                        store.markGroupingCompleted(engineId, engineFactory.modelVersion, revision, groupedPeople, finalCount)
                        clusteringStage = StageProgress(StageState.COMPLETED, faceTotal, faceTotal, 100)
                        val unassigned = store.unassignedFaceCount(engineId, engineFactory.modelVersion)
                        groupingStatus = "Grouping complete · $groupedPeople people · $unassigned unassigned"
                        FaceStoreEvents.notifyAllUpdated()
                    } catch (cancelled: CancellationException) {
                        runCatching {
                            store.markGroupingPending(engineId, engineFactory.modelVersion, revision,
                                store.faceEmbeddingCount(engineId, engineFactory.modelVersion))
                        }
                        FaceStoreEvents.notifyAllUpdated()
                        throw cancelled
                    } catch (err: Exception) {
                        val detail = err.localizedMessage ?: err.message ?: "Unknown grouping error"
                        runCatching {
                            store.markGroupingFailed(engineId, engineFactory.modelVersion, revision, detail,
                                store.faceEmbeddingCount(engineId, engineFactory.modelVersion))
                        }
                        FaceStoreEvents.notifyAllUpdated()
                        clusteringStage = StageProgress(StageState.FAILED, clusteringStage.processed, faceTotal, clusteringStage.percentage)
                        groupingStatus = "Grouping failed: $detail"
                    }
                    onProgress(FaceIndexingProgress(
                        processed, totalPending, facesFound, failed, lastCompletedItemName,
                        lastCompletedItemStatus, groupingStatus.orEmpty(), lastCompletedItemFaces,
                        engineId, engineFactory.displayName, detectionStage, embeddingStage,
                        clusteringStage, failedList.toList()
                    ))
                } else clusteringStage = StageProgress(StageState.COMPLETED, faceTotal, faceTotal, 100)
            } else if (totalPending > 0) {
                clusteringStage = StageProgress(StageState.COMPLETED, totalPending, totalPending, 100)
            }
        } finally {
            runCatching { engine?.close() }
        }

        FaceIndexingProgress(
            processed, totalPending, facesFound, failed, lastCompletedItemName,
            lastCompletedItemStatus, groupingStatus.orEmpty(), lastCompletedItemFaces,
            engineId, engineFactory.displayName, detectionStage, embeddingStage,
            clusteringStage, failedList.toList()
        ).also { onProgress(it) }
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

private fun percent(processed: Int, total: Int): Int = if (total > 0) processed * 100 / total else 100
