package com.amresalehin.emregallery.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amresalehin.emregallery.IndexingForegroundService
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceIndexingRepository
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

    private val context: Context get() = getApplication()
    private val faceStore = FaceClusterStore(context.applicationContext)

    private val _uiState = MutableStateFlow(
        IndexingStatusUiState(
            faceFinding = FaceFindingState(
                detectionConfidence = FaceRecognitionSettings.detectionConfidence(context)
            ),
            clustering = FaceClusteringState(
                minGroupFaces = FaceRecognitionSettings.minGroupFaces(context),
                clusterDistance = FaceRecognitionSettings.recognitionDistance(context)
            )
        )
    )
    val uiState: StateFlow<IndexingStatusUiState> = _uiState.asStateFlow()

    private var currentPhotos: List<MediaItem> = emptyList()
    private var currentPhotoUris: List<String> = emptyList()
    private var currentOcrLanguages: Set<String> = emptySet()

    var allowNativeFallback: Boolean = false
        private set
    var rescanAllRequested: Boolean = false
        private set

    private var ocrStartTimeMillis: Long = 0L
    private var ocrStartProcessed: Int = 0
    private var faceStartTimeMillis: Long = 0L
    private var faceStartProcessed: Int = 0

    init {
        updateEngineState()
        startServiceMonitoring()
    }

    fun setMedia(media: List<MediaItem>, ocrLanguages: Set<String>) {
        val photos = media.filterNot { it.isVideo }
        val uris = photos.map { it.uri }
        currentPhotos = photos
        currentPhotoUris = uris
        currentOcrLanguages = ocrLanguages

        _uiState.update { it.copy(totalPhotos = photos.size) }
        refreshCounts()
    }

    fun updateEngineState() {
        val (engine, failure) = FaceIndexingRepository.queryEngineInfo(context)
        val installed = BuffaloLFaceEngine.isModelInstalled(context)
        _uiState.update { state ->
            state.copy(
                engine = EngineState(
                    isBuffaloLInstalled = installed,
                    activeEngineName = engine,
                    primaryModelFailure = failure
                )
            )
        }
    }

    fun refreshCounts() {
        updateEngineState()
        viewModelScope.launch {
            _uiState.update { it.copy(countsReady = false) }
            val photos = currentPhotos
            val photoUris = currentPhotoUris

            val (ocrProcessed, ocrCached, faceProcessed, faceDetected, faceFailures, summaries) = withContext(Dispatchers.IO) {
                val oCount = photos.count { item ->
                    TesseractOcr.hasCachedResult(context, Uri.parse(item.uri))
                }
                val oFiles = TesseractOcr.cachedResultCount(context)
                val fCount = faceStore.indexedMediaCount(photoUris)
                val fFaces = faceStore.indexedFaceCount()
                val fFailures = faceStore.failedMediaCount()
                val cSummaries = faceStore.summaries()
                CountsResult(oCount, oFiles, fCount, fFaces, fFailures, cSummaries)
            }

            _uiState.update { state ->
                val totalPhotos = photos.size
                state.copy(
                    countsReady = true,
                    totalPhotos = totalPhotos,
                    ocr = state.ocr.copy(
                        processed = ocrProcessed.coerceIn(0, totalPhotos),
                        cachedCount = ocrCached
                    ),
                    faceFinding = state.faceFinding.copy(
                        processed = faceProcessed.coerceIn(0, totalPhotos),
                        totalDetected = faceDetected,
                        failedCount = faceFailures
                    ),
                    clustering = state.clustering.copy(
                        summaries = summaries
                    )
                )
            }
        }
    }

    private data class CountsResult(
        val ocrProcessed: Int,
        val ocrCached: Int,
        val faceProcessed: Int,
        val faceDetected: Int,
        val faceFailures: Int,
        val summaries: List<com.amresalehin.emregallery.face.FaceClusterSummary>
    )

    private fun startServiceMonitoring() {
        viewModelScope.launch {
            IndexingForegroundService.status(context)
            var wasRunning = false
            while (isActive) {
                val prefs = context.getSharedPreferences(
                    IndexingForegroundService.STATE_PREFS,
                    Context.MODE_PRIVATE
                )
                val task = prefs.getString(IndexingForegroundService.KEY_TASK, "") ?: ""
                val running = prefs.getBoolean(IndexingForegroundService.KEY_RUNNING, false)
                val paused = prefs.getBoolean(IndexingForegroundService.KEY_PAUSED, false)
                val processed = prefs.getInt(IndexingForegroundService.KEY_PROCESSED, 0)
                val total = prefs.getInt(IndexingForegroundService.KEY_TOTAL, 0)
                val faces = prefs.getInt(IndexingForegroundService.KEY_FACES, -1)
                val failed = prefs.getInt(IndexingForegroundService.KEY_FAILED, -1)
                val stage = prefs.getString(IndexingForegroundService.KEY_STAGE, "downloading") ?: "downloading"
                val bytes = prefs.getLong(IndexingForegroundService.KEY_BYTES, 0L)
                val bytesTotal = prefs.getLong(IndexingForegroundService.KEY_BYTES_TOTAL, 0L)
                val error = prefs.getString(IndexingForegroundService.KEY_ERROR, null)

                _uiState.update { state ->
                    var newOcr = state.ocr
                    var newFace = state.faceFinding
                    var newDownload = state.modelDownload

                    when (task) {
                        "ocr" -> {
                            val ipm = if (running && !paused) {
                                calculateRate(processed - ocrStartProcessed, ocrStartTimeMillis)
                            } else 0
                            newOcr = newOcr.copy(
                                isRunning = running,
                                isPaused = paused,
                                processed = if (total > 0) processed else newOcr.processed,
                                itemsPerMinute = ipm
                            )
                        }
                        "face" -> {
                            val ipm = if (running && !paused) {
                                calculateRate(processed - faceStartProcessed, faceStartTimeMillis)
                            } else 0
                            newFace = newFace.copy(
                                isRunning = running,
                                isPaused = paused,
                                processed = if (total > 0) processed else newFace.processed,
                                totalDetected = if (faces >= 0) faces else newFace.totalDetected,
                                failedCount = if (failed >= 0) failed else newFace.failedCount,
                                itemsPerMinute = ipm
                            )
                        }
                        "model" -> {
                            newDownload = newDownload.copy(
                                isDownloading = running,
                                isPaused = paused,
                                stage = stage,
                                bytesReceived = bytes,
                                totalBytes = bytesTotal,
                                error = error
                            )
                        }
                    }

                    if (task != "model") {
                        newDownload = newDownload.copy(error = error)
                    }

                    state.copy(
                        ocr = newOcr,
                        faceFinding = newFace,
                        modelDownload = newDownload
                    )
                }

                if (wasRunning && !running) {
                    if (task == "ocr" || task == "face") {
                        refreshCounts()
                    }
                    updateEngineState()
                }
                wasRunning = running
                delay(750)
            }
        }
    }

    private fun calculateRate(processed: Int, startedAtMillis: Long): Int {
        if (processed <= 0 || startedAtMillis <= 0L) return 0
        val elapsed = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(1_000L)
        return ((processed * 60_000.0) / elapsed).roundToInt().coerceAtLeast(1)
    }

    fun toggleOcr() {
        val state = _uiState.value
        if (!state.countsReady || currentPhotos.isEmpty()) return
        if (state.ocr.isRunning) {
            val paused = !state.ocr.isPaused
            _uiState.update { it.copy(ocr = it.ocr.copy(isPaused = paused)) }
            if (paused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
            return
        }
        if (state.faceFinding.isRunning || state.modelDownload.isDownloading) return
        if (state.ocr.processed >= currentPhotos.size) return

        ocrStartTimeMillis = System.currentTimeMillis()
        ocrStartProcessed = state.ocr.processed
        _uiState.update { it.copy(ocr = it.ocr.copy(isRunning = true, isPaused = false)) }
        IndexingForegroundService.startOcr(
            context,
            currentOcrLanguages.toList().ifEmpty { listOf("eng") }
        )
    }

    fun stopOcr() {
        IndexingForegroundService.cancel(context)
        _uiState.update { it.copy(ocr = it.ocr.copy(isRunning = false, isPaused = false, itemsPerMinute = 0)) }
        refreshCounts()
    }

    fun clearAndReindexOcr() {
        val state = _uiState.value
        if (state.ocr.isRunning || state.faceFinding.isRunning || state.modelDownload.isDownloading) return
        ocrStartTimeMillis = System.currentTimeMillis()
        ocrStartProcessed = 0
        _uiState.update { it.copy(ocr = it.ocr.copy(isRunning = true, isPaused = false)) }
        IndexingForegroundService.startOcr(
            context,
            currentOcrLanguages.toList().ifEmpty { listOf("eng") },
            rescanAll = true
        )
    }

    fun toggleFaceFinding(onShowFallbackChoice: (rescanAll: Boolean) -> Unit) {
        val state = _uiState.value
        if (!state.countsReady || currentPhotos.isEmpty()) return
        if (state.faceFinding.isRunning) {
            val paused = !state.faceFinding.isPaused
            _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isPaused = paused)) }
            if (paused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
            return
        }
        if (state.ocr.isRunning || state.modelDownload.isDownloading) return
        if (state.faceFinding.processed >= currentPhotos.size) return

        if (!state.engine.isBuffaloLInstalled && !allowNativeFallback) {
            rescanAllRequested = false
            onShowFallbackChoice(false)
            return
        }

        faceStartTimeMillis = System.currentTimeMillis()
        faceStartProcessed = state.faceFinding.processed
        _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isRunning = true, isPaused = false)) }
        IndexingForegroundService.startFace(context, allowNative = allowNativeFallback)
    }

    fun stopFaceFinding() {
        IndexingForegroundService.cancel(context)
        _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isRunning = false, isPaused = false, itemsPerMinute = 0)) }
        refreshCounts()
    }

    fun clearAndRescanFaces(onShowFallbackChoice: () -> Unit) {
        val state = _uiState.value
        if (state.ocr.isRunning || state.faceFinding.isRunning || state.modelDownload.isDownloading) return
        if (!state.engine.isBuffaloLInstalled && !allowNativeFallback) {
            rescanAllRequested = true
            onShowFallbackChoice()
            return
        }

        faceStartTimeMillis = System.currentTimeMillis()
        faceStartProcessed = 0
        _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isRunning = true, isPaused = false)) }
        IndexingForegroundService.startFace(context, rescanAll = true, allowNative = allowNativeFallback)
    }

    fun startModelDownload(force: Boolean = false) {
        val state = _uiState.value
        if (state.modelDownload.isDownloading || state.ocr.isRunning || state.faceFinding.isRunning) return
        _uiState.update {
            it.copy(
                modelDownload = ModelDownloadState(
                    isDownloading = true,
                    isPaused = false,
                    stage = "downloading",
                    bytesReceived = 0L,
                    totalBytes = 0L,
                    error = null
                )
            )
        }
        IndexingForegroundService.downloadModel(context, force)
    }

    fun cancelModelDownload() {
        IndexingForegroundService.cancel(context)
        _uiState.update {
            it.copy(modelDownload = it.modelDownload.copy(isDownloading = false, isPaused = false))
        }
    }

    fun setAllowNativeFallback(allow: Boolean) {
        allowNativeFallback = allow
    }

    fun confirmFallbackChoiceAndStart(rescanAll: Boolean) {
        allowNativeFallback = true
        rescanAllRequested = false
        val state = _uiState.value
        faceStartTimeMillis = System.currentTimeMillis()
        faceStartProcessed = if (rescanAll) 0 else state.faceFinding.processed
        _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isRunning = true, isPaused = false)) }
        IndexingForegroundService.startFace(context, rescanAll = rescanAll, allowNative = true)
    }

    fun updateDetectionConfidence(confidence: Float) {
        val rounded = (confidence * 100).roundToInt() / 100f
        _uiState.update { it.copy(faceFinding = it.faceFinding.copy(detectionConfidence = rounded)) }
        FaceRecognitionSettings.preferences(context).edit()
            .putFloat(FaceRecognitionSettings.KEY_DETECTION_CONFIDENCE, rounded)
            .apply()
    }

    fun updateMinGroupFaces(minFaces: Int) {
        val coerced = minFaces.coerceIn(1, 30)
        _uiState.update { it.copy(clustering = it.clustering.copy(minGroupFaces = coerced)) }
        FaceRecognitionSettings.preferences(context).edit()
            .putInt(FaceRecognitionSettings.KEY_MIN_GROUP_FACES, coerced)
            .apply()
    }

    fun updateClusterDistance(distance: Float) {
        val rounded = (distance * 100).roundToInt() / 100f
        _uiState.update { it.copy(clustering = it.clustering.copy(clusterDistance = rounded)) }
        FaceRecognitionSettings.preferences(context).edit()
            .putFloat(FaceRecognitionSettings.KEY_RECOGNITION_DISTANCE, rounded)
            .apply()
    }

    fun runFaceClustering(threshold: Float) {
        val state = _uiState.value
        if (state.clustering.isClusteringRunning) return

        _uiState.update {
            it.copy(
                clustering = it.clustering.copy(
                    isClusteringRunning = true,
                    statusMessage = "Clustering facial embeddings..."
                )
            )
        }

        viewModelScope.launch {
            val resultCount = withContext(Dispatchers.IO) {
                faceStore.reclusterAll(threshold)
            }
            val (summaries, detectedCount) = withContext(Dispatchers.IO) {
                Pair(faceStore.summaries(), faceStore.indexedFaceCount())
            }
            _uiState.update {
                it.copy(
                    clustering = it.clustering.copy(
                        summaries = summaries,
                        isClusteringRunning = false,
                        statusMessage = "Successfully grouped into $resultCount people profiles."
                    ),
                    faceFinding = it.faceFinding.copy(
                        totalDetected = detectedCount
                    )
                )
            }
        }
    }

    fun resetFaceClusters() {
        val state = _uiState.value
        if (state.clustering.isClusteringRunning) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                faceStore.resetClustersOnly()
            }
            _uiState.update {
                it.copy(
                    clustering = it.clustering.copy(
                        summaries = emptyList(),
                        statusMessage = "Clusters reset. Stored facial embeddings preserved."
                    )
                )
            }
        }
    }
}
