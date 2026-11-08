package com.amresalehin.emregallery.face

import android.content.Context
import android.net.Uri
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap


/**
 * Clean, decoupled media face indexing repository.
 * Operates with any FaceEngine implementation supplied via constructor injection.
 */
class FaceIndexingRepository(
    internal val context: Context,
    internal val engineFactory: FaceEngineFactory = BuffaloLFaceEngineFactory
) {

    companion object {
        @Volatile
        var activeEngineDescription: String = "Detecting..."
            private set

        private val lastEngineFailures = ConcurrentHashMap<String, String>()

        fun queryEngineInfo(context: Context, factory: FaceEngineFactory = BuffaloLFaceEngineFactory): Pair<String, String?> {
            val failure = lastEngineFailures[factory.engineId]
            return factory.displayName to failure
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
        // Enforce background scheduling priority so inference threads run on efficiency cores
        // and never starve the UI/render threads.
        runCatching {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        }
        val photos = media.filterNot { it.isVideo }
        val engineId = engineFactory.engineId
        val cooldownMillis = FaceRecognitionSettings.cpuThrottleCooldownMillis(context)
        val operationHandle = FaceProcessingCoordinator.beginOperation(
            operationId = "repo_index_${System.nanoTime()}",
            type = if (forceRescanUris.isNotEmpty()) FaceProcessingOperation.RESCANNING else FaceProcessingOperation.SCANNING,
            engineId = engineId,
            description = "Face indexing (${engineFactory.displayName})"
        )
        val candidates = if (targetUrisOnly != null) {
            photos.filter { it.uri in targetUrisOnly }
        } else photos

        val pending = candidates.filter { it.uri in forceRescanUris || !store.isMediaIndexed(it.uri, engineId) }
        var processed = 0
        var facesFound = 0
        var photosWithFaces = 0
        var failed = 0
        val failedList = mutableListOf<Pair<String, String>>()
        var engine: FaceEngine? = null
        var groupingStatus: String? = null
        var lastCompletedItemName = ""
        var lastCompletedItemStatus = ""
        var lastCompletedItemFaces: Int? = null

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

            val actualDimension = engine?.embeddingDimension ?: engineFactory.embeddingDimension
            val provenance = EmbeddingProvenance(
                engineId = engineFactory.engineId,
                modelVersion = engineFactory.modelVersion,
                dimension = actualDimension
            )

            val loopSummary = processPendingPhotosLoop(
                pending = pending,
                store = store,
                engine = engine,
                engineId = engineId,
                provenance = provenance,
                recognitionDistance = recognitionDistance,
                cooldownMillis = cooldownMillis,
                initialDetectionStage = detectionStage,
                initialEmbeddingStage = embeddingStage,
                clusteringStage = clusteringStage,
                awaitIfResumed = awaitIfResumed,
                onProgress = onProgress
            )
            processed = loopSummary.processed
            facesFound = loopSummary.facesFound
            photosWithFaces = loopSummary.photosWithFaces
            failed = loopSummary.failed
            lastCompletedItemName = loopSummary.lastCompletedItemName
            lastCompletedItemStatus = loopSummary.lastCompletedItemStatus
            lastCompletedItemFaces = loopSummary.lastCompletedItemFaces
            detectionStage = loopSummary.detectionStage
            embeddingStage = loopSummary.embeddingStage
            failedList.addAll(loopSummary.failedList)
            val groupingOutcome = runPostIndexingGroupingPass(
                store = store,
                pendingUris = pending.map { it.uri },
                engineId = engineId,
                actualDimension = actualDimension,
                recognitionDistance = recognitionDistance,
                processed = processed,
                totalPending = totalPending,
                facesFound = facesFound,
                photosWithFaces = photosWithFaces,
                failed = failed,
                lastCompletedItemName = lastCompletedItemName,
                lastCompletedItemStatus = lastCompletedItemStatus,
                lastCompletedItemFaces = lastCompletedItemFaces,
                detectionStage = detectionStage,
                embeddingStage = embeddingStage,
                initialClusteringStage = clusteringStage,
                failedList = failedList.toList(),
                onProgress = onProgress
            )
            clusteringStage = groupingOutcome.clusteringStage
            groupingStatus = groupingOutcome.groupingStatus
        } finally {
            engine?.close()
            FaceStoreEvents.notifyAllUpdated()
            operationHandle.close()
        }

        val finalProgress = FaceIndexingProgress(
            processed = processed,
            total = totalPending,
            facesFound = facesFound,
            photosWithFaces = photosWithFaces,
            failed = failed,
            currentItemName = lastCompletedItemName,
            currentItemStatus = lastCompletedItemStatus,
            groupingStatus = groupingStatus.orEmpty(),
            currentItemFaces = lastCompletedItemFaces,
            engineId = engineId,
            engineName = engineFactory.displayName,
            detectionStage = detectionStage,
            embeddingStage = embeddingStage,
            clusteringStage = clusteringStage,
            failedItems = failedList.toList()
        )
        onProgress(finalProgress)
        FaceStoreEvents.notifyAllUpdated()
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
        FaceProcessingCoordinator.withProcessing(
            operationId = "repo_download_${System.nanoTime()}",
            type = FaceProcessingOperation.MODEL_DOWNLOAD,
            engineId = engineId,
            description = "Downloading ${engineFactory.displayName}"
        ) {
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
}
