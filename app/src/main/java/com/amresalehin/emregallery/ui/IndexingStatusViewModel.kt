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

class IndexingStatusViewModel(application: Application) : AndroidViewModel(application) {

    internal companion object {
        const val MANUAL_CLUSTERING_STRATEGY_VERSION = 1
    }

    internal val context: Context get() = getApplication()
    internal val faceStore = FaceClusterStore(context.applicationContext)

    internal val _uiState = MutableStateFlow(
        IndexingStatusUiState(
            faceFinding = FaceFindingState(
                detectionConfidence = FaceRecognitionSettings.detectionConfidence(context, BuffaloLFaceEngine.ENGINE_ID)
            ),
            clustering = FaceClusteringState(
                minGroupFaces = FaceRecognitionSettings.minGroupFaces(context, BuffaloLFaceEngine.ENGINE_ID),
                clusterDistance = FaceRecognitionSettings.recognitionDistance(context, BuffaloLFaceEngine.ENGINE_ID)
            ),
            enteFaceFinding = FaceFindingState(
                detectionConfidence = FaceRecognitionSettings.detectionConfidence(context, EnteFaceConstants.ENGINE_ID)
            ),
            enteClustering = FaceClusteringState(
                minGroupFaces = FaceRecognitionSettings.minGroupFaces(context, EnteFaceConstants.ENGINE_ID),
                clusterDistance = FaceRecognitionSettings.recognitionDistance(context, EnteFaceConstants.ENGINE_ID)
            )
        )
    )
    val uiState: StateFlow<IndexingStatusUiState> = _uiState.asStateFlow()

    internal var currentPhotos: List<MediaItem> = emptyList()
    internal var currentPhotoUris: List<String> = emptyList()
    internal var currentOcrLanguages: Set<String> = emptySet()
    internal var currentMlKitLanguages: Set<String> = setOf("latin")
    internal var currentOcrModel: String = "fast"
    internal var currentOcrEngine: String = "tesseract"

    var rescanAllRequested: Boolean = false
        private set

    internal var ocrStartTimeMillis: Long = 0L
    internal var ocrStartProcessed: Int = 0
    internal var faceStartTimeMillis: Long = 0L
    internal var faceStartProcessed: Int = 0
    internal var enteFaceStartTimeMillis: Long = 0L
    internal var enteFaceStartProcessed: Int = 0
    internal var locationStartTimeMillis: Long = 0L
    internal var locationStartProcessed: Int = 0

    init {
        updateEngineState()
        startServiceMonitoring()
    }

    fun setMedia(
        media: List<MediaItem>,
        ocrLanguages: Set<String>,
        ocrModel: String = "fast",
        ocrEngine: String = "tesseract",
        mlKitLanguages: Set<String> = setOf("latin")
    ) {
        val photos = media.filterNot { it.isVideo }
        val uris = photos.map { it.uri }
        currentPhotos = photos
        currentPhotoUris = uris
        currentOcrLanguages = ocrLanguages
        currentMlKitLanguages = mlKitLanguages
        currentOcrModel = ocrModel
        currentOcrEngine = ocrEngine

        _uiState.update { it.copy(totalPhotos = photos.size, ocr = it.ocr.copy(model = ocrModel, engine = ocrEngine, mlKitLanguages = mlKitLanguages)) }
        refreshCounts()
    }

    fun setOcrModel(modelId: String) {
        currentOcrModel = modelId
        _uiState.update { it.copy(ocr = it.ocr.copy(model = modelId)) }
    }

    fun setOcrEngine(engineId: String) {
        currentOcrEngine = engineId
        _uiState.update { it.copy(ocr = it.ocr.copy(engine = engineId)) }
    }

    fun setMlKitLanguages(languages: Set<String>) {
        currentMlKitLanguages = languages
        _uiState.update { it.copy(ocr = it.ocr.copy(mlKitLanguages = languages)) }
    }


    fun updateEngineState() = updateEngineStateImpl()
    fun refreshCounts() = refreshCountsImpl()
    fun retryFailedFaces(engineId: String = BuffaloLFaceEngine.ENGINE_ID) = retryFailedFacesImpl(engineId)

    fun toggleFaceFinding(
        rescanAll: Boolean = false,
        engineId: String = BuffaloLFaceEngine.ENGINE_ID,
        folders: List<String>? = null
    ) = toggleFaceFindingImpl(rescanAll, engineId, folders)

    fun setFaceIndexingFolders(allFolders: Boolean, folders: Set<String>) =
        setFaceIndexingFoldersImpl(allFolders, folders)

    fun stopFaceFinding(engineId: String = BuffaloLFaceEngine.ENGINE_ID) = stopFaceFindingImpl(engineId)

    fun clearAndRescanFaces(
        engineId: String = BuffaloLFaceEngine.ENGINE_ID,
        folders: List<String>? = null
    ) = clearAndRescanFacesImpl(engineId, folders)

    fun startModelDownload(
        force: Boolean = false,
        engineId: String = BuffaloLFaceEngine.ENGINE_ID
    ) = startModelDownloadImpl(force, engineId)

    fun cancelModelDownload(engineId: String = BuffaloLFaceEngine.ENGINE_ID) = cancelModelDownloadImpl(engineId)

    fun runFaceClustering(
        engineId: String = BuffaloLFaceEngine.ENGINE_ID,
        maxDistance: Float? = null
    ) = runFaceClusteringImpl(engineId, maxDistance)

    fun resetFaceClusters(engineId: String = BuffaloLFaceEngine.ENGINE_ID) = resetFaceClustersImpl(engineId)

    fun updateDetectionConfidence(confidence: Float, engineId: String = BuffaloLFaceEngine.ENGINE_ID) =
        updateDetectionConfidenceImpl(confidence, engineId)

    fun updateMinGroupFaces(minFaces: Int, engineId: String = BuffaloLFaceEngine.ENGINE_ID) =
        updateMinGroupFacesImpl(minFaces, engineId)

    fun updateClusterDistance(distance: Float, engineId: String = BuffaloLFaceEngine.ENGINE_ID) =
        updateClusterDistanceImpl(distance, engineId)

    fun toggleLocationIndexing() = toggleLocationIndexingImpl()
    fun stopLocationIndexing() = stopLocationIndexingImpl()
    fun clearAndReindexLocation() = clearAndReindexLocationImpl()
    fun toggleOcr() = toggleOcrImpl()
    fun stopOcr() = stopOcrImpl()
    fun clearAndReindexOcr() = clearAndReindexOcrImpl()
}
