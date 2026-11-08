package com.amresalehin.emregallery.ui
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amresalehin.emregallery.IndexingForegroundService
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.BuffaloLFaceEngineFactory
import com.amresalehin.emregallery.face.EnteFaceClusteringStrategy
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.EnteFaceEngineFactory
import com.amresalehin.emregallery.face.EnteFaceModelManager
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceEngineRegistry
import com.amresalehin.emregallery.face.FaceIndexingRepository
import com.amresalehin.emregallery.face.FaceStoreEvents
import com.amresalehin.emregallery.face.StageProgress
import com.amresalehin.emregallery.face.StageState
import com.amresalehin.emregallery.face.FaceProcessingCoordinator
import com.amresalehin.emregallery.face.FaceProcessingOperation
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal fun IndexingStatusViewModel.runFaceClusteringImpl(
    threshold: Float,
    engineId: String = BuffaloLFaceEngine.ENGINE_ID,
    force: Boolean = true
) {
    val state = _uiState.value
    val clusteringState = if (engineId == EnteFaceConstants.ENGINE_ID) state.enteClustering else state.clustering
    val findingState = if (engineId == EnteFaceConstants.ENGINE_ID) state.enteFaceFinding else state.faceFinding
    if (clusteringState.isClusteringRunning || findingState.isRunning || findingState.isPaused) return

    // The foreground service owns the shared face DB. Reject a stale UI action even if
    // its StateFlow hasn't yet reflected a newly started indexing job.
    val prefs = context.getSharedPreferences(IndexingForegroundService.STATE_PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(IndexingForegroundService.KEY_RUNNING, false) &&
        prefs.getString(IndexingForegroundService.KEY_TASK, "") == "face"
    ) return

    val modelVersion = if (engineId == EnteFaceConstants.ENGINE_ID) {
        EnteFaceConstants.MODEL_VERSION
    } else {
        BuffaloLFaceEngine.MODEL_VERSION
    }
    val strategyVersion = if (engineId == EnteFaceConstants.ENGINE_ID) {
        EnteFaceClusteringStrategy.strategyVersion
    } else {
        MANUAL_CLUSTERING_STRATEGY_VERSION
    }

    _uiState.update {
        if (engineId == EnteFaceConstants.ENGINE_ID) {
            it.copy(
                enteClustering = it.enteClustering.copy(
                    isClusteringRunning = true,
                    statusMessage = "Grouping facial embeddings…"
                ),
                enteFaceFinding = it.enteFaceFinding.copy(
                    clusteringStage = StageProgress(StageState.RUNNING, 0, it.enteFaceFinding.totalDetected, 0),
                    groupingStatus = "Grouping people…"
                )
            )
        } else {
            it.copy(
                clustering = it.clustering.copy(
                    isClusteringRunning = true,
                    statusMessage = "Grouping facial embeddings…"
                ),
                faceFinding = it.faceFinding.copy(
                    clusteringStage = StageProgress(StageState.RUNNING, 0, it.faceFinding.totalDetected, 0),
                    groupingStatus = "Grouping people…"
                )
            )
        }
    }

    viewModelScope.launch {
        val clusterHandle = FaceProcessingCoordinator.beginOperation(
            operationId = "manual_clustering_$engineId",
            type = FaceProcessingOperation.GROUPING,
            engineId = engineId,
            description = "Grouping facial embeddings"
        )
        try {
            data class ClusteringResult(
                val grouped: Boolean,
                val peopleCount: Int,
                val faceCount: Int,
                val unassignedCount: Int,
                val summaries: List<com.amresalehin.emregallery.face.FaceClusterSummary>
            )

            val result = withContext(Dispatchers.IO) {
                if (!force && !faceStore.isGroupingRequired(engineId, modelVersion, strategyVersion)) {
                    return@withContext ClusteringResult(
                        grouped = false,
                        peopleCount = faceStore.personCount(engineId = engineId),
                        faceCount = faceStore.indexedFaceCount(engineId),
                        unassignedCount = faceStore.unassignedFaceCount(engineId, modelVersion),
                        summaries = faceStore.summaries(engineId)
                    )
                }

                faceStore.markGroupingPending(engineId, modelVersion, strategyVersion)
                val peopleCount = if (engineId == EnteFaceConstants.ENGINE_ID) {
                    EnteFaceClusteringStrategy.reclusterChronologically(faceStore, threshold)
                } else {
                    faceStore.reclusterAllSafely(threshold, engineId, modelVersion)
                }
                faceStore.ensurePersonCovers(engineId)
                val finalCount = faceStore.faceEmbeddingCount(engineId, modelVersion)
                faceStore.markGroupingCompleted(engineId, modelVersion, strategyVersion, peopleCount, finalCount)
                FaceStoreEvents.notifyAllUpdated()
                ClusteringResult(
                    grouped = true,
                    peopleCount = peopleCount,
                    faceCount = faceStore.indexedFaceCount(engineId),
                    unassignedCount = faceStore.unassignedFaceCount(engineId, modelVersion),
                    summaries = faceStore.summaries(engineId)
                )
            }

            val statusMessage = if (result.grouped) {
                "Successfully grouped into ${result.peopleCount} people profiles."
            } else {
                "People are already grouped."
            }
            val groupingStatus = "Grouping complete · ${result.peopleCount} people · ${result.unassignedCount} unassigned"
            _uiState.update {
                if (engineId == EnteFaceConstants.ENGINE_ID) {
                    it.copy(
                        enteClustering = it.enteClustering.copy(
                            summaries = result.summaries,
                            isClusteringRunning = false,
                            statusMessage = statusMessage
                        ),
                        enteFaceFinding = it.enteFaceFinding.copy(
                            totalDetected = result.faceCount,
                            clusteringStage = StageProgress(StageState.COMPLETED, result.faceCount, result.faceCount, 100),
                            groupingStatus = groupingStatus
                        )
                    )
                } else {
                    it.copy(
                        clustering = it.clustering.copy(
                            summaries = result.summaries,
                            isClusteringRunning = false,
                            statusMessage = statusMessage
                        ),
                        faceFinding = it.faceFinding.copy(
                            totalDetected = result.faceCount,
                            clusteringStage = StageProgress(StageState.COMPLETED, result.faceCount, result.faceCount, 100),
                            groupingStatus = groupingStatus
                        )
                    )
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Clustering failed"
            runCatching {
                withContext(Dispatchers.IO) {
                    faceStore.markGroupingFailed(engineId, modelVersion, strategyVersion, errorMsg)
                }
            }
            FaceStoreEvents.notifyAllUpdated()
            _uiState.update {
                if (engineId == EnteFaceConstants.ENGINE_ID) {
                    it.copy(
                        enteClustering = it.enteClustering.copy(
                            isClusteringRunning = false,
                            statusMessage = "Clustering error: $errorMsg"
                        ),
                        enteFaceFinding = it.enteFaceFinding.copy(
                            clusteringStage = StageProgress(
                                StageState.FAILED,
                                it.enteFaceFinding.clusteringStage.processed,
                                it.enteFaceFinding.clusteringStage.total,
                                it.enteFaceFinding.clusteringStage.percentage
                            ),
                            groupingStatus = "Grouping failed: $errorMsg"
                        )
                    )
                } else {
                    it.copy(
                        clustering = it.clustering.copy(
                            isClusteringRunning = false,
                            statusMessage = "Clustering error: $errorMsg"
                        ),
                        faceFinding = it.faceFinding.copy(
                            clusteringStage = StageProgress(
                                StageState.FAILED,
                                it.faceFinding.clusteringStage.processed,
                                it.faceFinding.clusteringStage.total,
                                it.faceFinding.clusteringStage.percentage
                            ),
                            groupingStatus = "Grouping failed: $errorMsg"
                        )
                    )
                }
            }
        } finally {
            clusterHandle.close()
            _uiState.update {
                if (engineId == EnteFaceConstants.ENGINE_ID) {
                    it.copy(enteClustering = it.enteClustering.copy(isClusteringRunning = false))
                } else {
                    it.copy(clustering = it.clustering.copy(isClusteringRunning = false))
                }
            }
        }
    }
}

internal fun IndexingStatusViewModel.resetFaceClustersImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
    val state = _uiState.value
    val clusteringState = if (engineId == EnteFaceConstants.ENGINE_ID) state.enteClustering else state.clustering
    if (clusteringState.isClusteringRunning) return

    viewModelScope.launch {
        val resetHandle = FaceProcessingCoordinator.beginOperation(
            operationId = "manual_reset_clusters_$engineId",
            type = FaceProcessingOperation.FACE_DB_MUTATION,
            engineId = engineId,
            description = "Resetting face clusters"
        )
        try {
            withContext(Dispatchers.IO) {
                faceStore.resetClustersOnly(engineId)
            }
            _uiState.update {
                if (engineId == EnteFaceConstants.ENGINE_ID) {
                    it.copy(
                        enteClustering = it.enteClustering.copy(
                            summaries = emptyList(),
                            isClusteringRunning = false,
                            statusMessage = "Ente clusters reset. Stored facial embeddings preserved."
                        )
                    )
                } else {
                    it.copy(
                        clustering = it.clustering.copy(
                            summaries = emptyList(),
                            isClusteringRunning = false,
                            statusMessage = "Clusters reset. Stored facial embeddings preserved."
                        )
                    )
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Failed to reset clusters"
            _uiState.update {
                if (engineId == EnteFaceConstants.ENGINE_ID) {
                    it.copy(
                        enteClustering = it.enteClustering.copy(
                            isClusteringRunning = false,
                            statusMessage = "Reset error: $errorMsg"
                        )
                    )
                } else {
                    it.copy(
                        clustering = it.clustering.copy(
                            isClusteringRunning = false,
                            statusMessage = "Reset error: $errorMsg"
                        )
                    )
                }
            }
        } finally {
            resetHandle.close()
            _uiState.update {
                if (engineId == EnteFaceConstants.ENGINE_ID) {
                    it.copy(enteClustering = it.enteClustering.copy(isClusteringRunning = false))
                } else {
                    it.copy(clustering = it.clustering.copy(isClusteringRunning = false))
                }
            }
        }
    }
}

