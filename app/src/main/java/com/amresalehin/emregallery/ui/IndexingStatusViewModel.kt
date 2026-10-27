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

    private var currentPhotos: List<MediaItem> = emptyList()
    private var currentPhotoUris: List<String> = emptyList()
    private var currentOcrLanguages: Set<String> = emptySet()
    private var currentOcrModel: String = "fast"

    var rescanAllRequested: Boolean = false
        private set

    private var ocrStartTimeMillis: Long = 0L
    private var ocrStartProcessed: Int = 0
    private var faceStartTimeMillis: Long = 0L
    private var faceStartProcessed: Int = 0
    private var enteFaceStartTimeMillis: Long = 0L
    private var enteFaceStartProcessed: Int = 0
    private var locationStartTimeMillis: Long = 0L
    private var locationStartProcessed: Int = 0

    init {
        updateEngineState()
        startServiceMonitoring()
    }

    fun setMedia(media: List<MediaItem>, ocrLanguages: Set<String>, ocrModel: String = "fast") {
        val photos = media.filterNot { it.isVideo }
        val uris = photos.map { it.uri }
        currentPhotos = photos
        currentPhotoUris = uris
        currentOcrLanguages = ocrLanguages
        currentOcrModel = ocrModel

        _uiState.update { it.copy(totalPhotos = photos.size, ocr = it.ocr.copy(model = ocrModel)) }
        refreshCounts()
    }

    fun setOcrModel(modelId: String) {
        currentOcrModel = modelId
        _uiState.update { it.copy(ocr = it.ocr.copy(model = modelId)) }
    }

    fun updateEngineState() {
        val (buffaloName, buffaloFailure) = FaceIndexingRepository.queryEngineInfo(context, BuffaloLFaceEngineFactory)
        val (_, enteFailure) = FaceIndexingRepository.queryEngineInfo(context, EnteFaceEngineFactory)
        val buffaloInstalled = BuffaloLFaceEngine.isModelInstalled(context)
        val enteInstalled = EnteFaceModelManager.isInstalled(context)

        _uiState.update { state ->
            state.copy(
                engine = EngineState(
                    isBuffaloLInstalled = buffaloInstalled,
                    isEnteInstalled = enteInstalled,
                    activeEngineName = buffaloName,
                    activeEngineId = FaceEngineRegistry.activeId,
                    primaryModelFailure = buffaloFailure,
                    enteModelFailure = enteFailure
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

            val result = withContext(Dispatchers.IO) {
                val oCount = photos.count { item ->
                    TesseractOcr.hasCachedResult(context, Uri.parse(item.uri))
                }
                val oFiles = TesseractOcr.cachedResultCount(context)

                // Buffalo_L counts
                val bCount = faceStore.indexedMediaCount(photoUris, BuffaloLFaceEngine.ENGINE_ID)
                val bFaces = faceStore.indexedFaceCount(BuffaloLFaceEngine.ENGINE_ID)
                val bFailures = faceStore.failedMediaCount(photoUris, BuffaloLFaceEngine.ENGINE_ID)
                val bSummaries = faceStore.summaries(BuffaloLFaceEngine.ENGINE_ID)
                val bFailedList = faceStore.getFailedMedia(BuffaloLFaceEngine.ENGINE_ID).map { it.first to (it.second ?: "Detection failed") }

                // Ente-Style counts
                val eCount = faceStore.indexedMediaCount(photoUris, EnteFaceConstants.ENGINE_ID)
                val eFaces = faceStore.indexedFaceCount(EnteFaceConstants.ENGINE_ID)
                val eFailures = faceStore.failedMediaCount(photoUris, EnteFaceConstants.ENGINE_ID)
                val eSummaries = faceStore.summaries(EnteFaceConstants.ENGINE_ID)
                val eFailedList = faceStore.getFailedMedia(EnteFaceConstants.ENGINE_ID).map { it.first to (it.second ?: "Detection failed") }

                val locCache = com.amresalehin.emregallery.data.PhotoMapLocationCache(context.applicationContext)
                val locMap = locCache.loadAll()
                val lProcessed = photos.count { locMap.containsKey(it.uri) }
                val lLocated = locMap.values.count { it.point != null }

                FullCountsResult(
                    oCount, oFiles,
                    bCount, bFaces, bFailures, bSummaries, bFailedList,
                    eCount, eFaces, eFailures, eSummaries, eFailedList,
                    lProcessed, lLocated
                )
            }

            _uiState.update { state ->
                val totalPhotos = photos.size
                state.copy(
                    countsReady = true,
                    totalPhotos = totalPhotos,
                    ocr = state.ocr.copy(
                        processed = result.ocrProcessed.coerceIn(0, totalPhotos),
                        cachedCount = result.ocrCached
                    ),
                    faceFinding = state.faceFinding.copy(
                        processed = (result.buffaloProcessed + result.buffaloFailures).coerceIn(0, totalPhotos),
                        successfulCount = result.buffaloProcessed.coerceIn(0, totalPhotos),
                        totalDetected = result.buffaloDetected,
                        failedCount = result.buffaloFailures,
                        failedItems = result.buffaloFailedItems
                    ),
                    clustering = state.clustering.copy(
                        summaries = result.buffaloSummaries
                    ),
                    enteFaceFinding = state.enteFaceFinding.copy(
                        processed = (result.enteProcessed + result.enteFailures).coerceIn(0, totalPhotos),
                        successfulCount = result.enteProcessed.coerceIn(0, totalPhotos),
                        totalDetected = result.enteDetected,
                        failedCount = result.enteFailures,
                        failedItems = result.enteFailedItems
                    ),
                    enteClustering = state.enteClustering.copy(
                        summaries = result.enteSummaries
                    ),
                    location = state.location.copy(
                        processed = result.locProcessed.coerceIn(0, totalPhotos),
                        locatedCount = result.locLocated
                    )
                )
            }
        }
    }

    private data class FullCountsResult(
        val ocrProcessed: Int,
        val ocrCached: Int,
        val buffaloProcessed: Int,
        val buffaloDetected: Int,
        val buffaloFailures: Int,
        val buffaloSummaries: List<com.amresalehin.emregallery.face.FaceClusterSummary>,
        val buffaloFailedItems: List<Pair<String, String>>,
        val enteProcessed: Int,
        val enteDetected: Int,
        val enteFailures: Int,
        val enteSummaries: List<com.amresalehin.emregallery.face.FaceClusterSummary>,
        val enteFailedItems: List<Pair<String, String>>,
        val locProcessed: Int,
        val locLocated: Int
    )

    fun retryFailedFaces(engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
        IndexingForegroundService.retryFailedFaces(context, engineId)
    }

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
                val speed = prefs.getLong(IndexingForegroundService.KEY_SPEED, 0L)
                val eta = prefs.getLong(IndexingForegroundService.KEY_ETA, 0L)
                val error = prefs.getString(IndexingForegroundService.KEY_ERROR, null)
                val engineId = prefs.getString(IndexingForegroundService.KEY_ENGINE_ID, BuffaloLFaceEngine.ENGINE_ID) ?: BuffaloLFaceEngine.ENGINE_ID
                val currentItem = prefs.getString(IndexingForegroundService.KEY_CURRENT_ITEM_NAME, "") ?: ""
                val currentStatus = prefs.getString(IndexingForegroundService.KEY_CURRENT_ITEM_STATUS, "") ?: ""

                val detStateStr = prefs.getString(IndexingForegroundService.KEY_DETECTION_STATE, "PENDING") ?: "PENDING"
                val detProcessed = prefs.getInt(IndexingForegroundService.KEY_DETECTION_PROCESSED, 0)
                val detTotal = prefs.getInt(IndexingForegroundService.KEY_DETECTION_TOTAL, 0)

                val embStateStr = prefs.getString(IndexingForegroundService.KEY_EMBEDDING_STATE, "PENDING") ?: "PENDING"
                val embProcessed = prefs.getInt(IndexingForegroundService.KEY_EMBEDDING_PROCESSED, 0)
                val embTotal = prefs.getInt(IndexingForegroundService.KEY_EMBEDDING_TOTAL, 0)

                val cluStateStr = prefs.getString(IndexingForegroundService.KEY_CLUSTERING_STATE, "PENDING") ?: "PENDING"
                val cluProcessed = prefs.getInt(IndexingForegroundService.KEY_CLUSTERING_PROCESSED, 0)
                val cluTotal = prefs.getInt(IndexingForegroundService.KEY_CLUSTERING_TOTAL, 0)

                fun parseStage(stateStr: String, proc: Int, tot: Int): com.amresalehin.emregallery.face.StageProgress {
                    val s = runCatching { com.amresalehin.emregallery.face.StageState.valueOf(stateStr) }
                        .getOrDefault(com.amresalehin.emregallery.face.StageState.PENDING)
                    val pct = if (tot > 0) (proc * 100 / tot).coerceIn(0, 100) else if (s == com.amresalehin.emregallery.face.StageState.COMPLETED) 100 else 0
                    return com.amresalehin.emregallery.face.StageProgress(s, proc, tot, pct)
                }

                val detStage = parseStage(detStateStr, detProcessed, detTotal)
                val embStage = parseStage(embStateStr, embProcessed, embTotal)
                val cluStage = parseStage(cluStateStr, cluProcessed, cluTotal)

                _uiState.update { state ->
                    var newOcr = state.ocr
                    var newFace = state.faceFinding
                    var newEnteFace = state.enteFaceFinding
                    var newLocation = state.location
                    var newDownload = state.modelDownload
                    var newEnteDownload = state.enteModelDownload

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
                            if (engineId == EnteFaceConstants.ENGINE_ID) {
                                val ipm = if (running && !paused) {
                                    calculateRate(processed - enteFaceStartProcessed, enteFaceStartTimeMillis)
                                } else 0
                                val effectiveProc = if (total > 0) processed else newEnteFace.processed
                                val effectiveFailed = if (failed >= 0) failed else newEnteFace.failedCount
                                val effectiveSucc = (effectiveProc - effectiveFailed).coerceAtLeast(0)
                                newEnteFace = newEnteFace.copy(
                                    isRunning = running,
                                    isPaused = paused,
                                    processed = effectiveProc,
                                    successfulCount = effectiveSucc,
                                    totalDetected = if (faces >= 0) faces else newEnteFace.totalDetected,
                                    failedCount = effectiveFailed,
                                    itemsPerMinute = ipm,
                                    currentItemName = if (running) currentItem else "",
                                    currentItemStatus = if (running) currentStatus else "",
                                    activeEngineId = engineId,
                                    detectionStage = detStage,
                                    embeddingStage = embStage,
                                    clusteringStage = cluStage
                                )
                            } else {
                                val ipm = if (running && !paused) {
                                    calculateRate(processed - faceStartProcessed, faceStartTimeMillis)
                                } else 0
                                val effectiveProc = if (total > 0) processed else newFace.processed
                                val effectiveFailed = if (failed >= 0) failed else newFace.failedCount
                                val effectiveSucc = (effectiveProc - effectiveFailed).coerceAtLeast(0)
                                newFace = newFace.copy(
                                    isRunning = running,
                                    isPaused = paused,
                                    processed = effectiveProc,
                                    successfulCount = effectiveSucc,
                                    totalDetected = if (faces >= 0) faces else newFace.totalDetected,
                                    failedCount = effectiveFailed,
                                    itemsPerMinute = ipm,
                                    currentItemName = if (running) currentItem else "",
                                    currentItemStatus = if (running) currentStatus else "",
                                    activeEngineId = engineId,
                                    detectionStage = detStage,
                                    embeddingStage = embStage,
                                    clusteringStage = cluStage
                                )
                            }
                        }
                        "model" -> {
                            if (engineId == EnteFaceConstants.ENGINE_ID) {
                                newEnteDownload = newEnteDownload.copy(
                                    isDownloading = running,
                                    isPaused = paused,
                                    stage = stage,
                                    bytesReceived = bytes,
                                    totalBytes = bytesTotal,
                                    speedBytesPerSec = speed,
                                    etaSeconds = eta,
                                    engineId = engineId,
                                    error = error
                                )
                            } else {
                                newDownload = newDownload.copy(
                                    isDownloading = running,
                                    isPaused = paused,
                                    stage = stage,
                                    bytesReceived = bytes,
                                    totalBytes = bytesTotal,
                                    speedBytesPerSec = speed,
                                    etaSeconds = eta,
                                    engineId = engineId,
                                    error = error
                                )
                            }
                        }
                        "location" -> {
                            val ipm = if (running && !paused) {
                                calculateRate(processed - locationStartProcessed, locationStartTimeMillis)
                            } else 0
                            newLocation = newLocation.copy(
                                isRunning = running,
                                isPaused = paused,
                                processed = if (total > 0) processed else newLocation.processed,
                                locatedCount = if (faces >= 0) faces else newLocation.locatedCount,
                                itemsPerMinute = ipm
                            )
                        }
                    }

                    if (!running) {
                        newOcr = newOcr.copy(isRunning = false, isPaused = false, itemsPerMinute = 0)
                        newFace = newFace.copy(isRunning = false, isPaused = false, itemsPerMinute = 0, currentItemName = "")
                        newEnteFace = newEnteFace.copy(isRunning = false, isPaused = false, itemsPerMinute = 0, currentItemName = "")
                        newLocation = newLocation.copy(isRunning = false, isPaused = false, itemsPerMinute = 0)
                        newDownload = newDownload.copy(isDownloading = false, isPaused = false)
                        newEnteDownload = newEnteDownload.copy(isDownloading = false, isPaused = false)
                    }

                    state.copy(
                        ocr = newOcr,
                        faceFinding = newFace,
                        enteFaceFinding = newEnteFace,
                        location = newLocation,
                        modelDownload = newDownload,
                        enteModelDownload = newEnteDownload
                    )
                }

                if (wasRunning && !running) {
                    refreshCounts()
                }
                wasRunning = running
                delay(1000)
            }
        }
    }

    private fun calculateRate(items: Int, startTimeMillis: Long): Int {
        if (items <= 0 || startTimeMillis <= 0L) return 0
        val elapsedSec = (System.currentTimeMillis() - startTimeMillis) / 1000f
        if (elapsedSec < 3f) return 0
        return ((items / elapsedSec) * 60f).roundToInt()
    }

    // --- Face Finding Actions (Scoped by Engine) ---

    fun toggleFaceFinding(
        onNeedsModelDownload: (rescanAll: Boolean) -> Unit,
        engineId: String = BuffaloLFaceEngine.ENGINE_ID
    ) {
        val state = _uiState.value
        if (!state.countsReady || currentPhotos.isEmpty()) return

        val targetState = if (engineId == EnteFaceConstants.ENGINE_ID) state.enteFaceFinding else state.faceFinding
        val isInstalled = if (engineId == EnteFaceConstants.ENGINE_ID) state.engine.isEnteInstalled else state.engine.isBuffaloLInstalled

        if (targetState.isRunning) {
            val paused = !targetState.isPaused
            if (engineId == EnteFaceConstants.ENGINE_ID) {
                _uiState.update { it.copy(enteFaceFinding = it.enteFaceFinding.copy(isPaused = paused)) }
            } else {
                _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isPaused = paused)) }
            }
            if (paused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
            return
        }

        // Single heavy task constraint: do not run face finding if another heavy job is active
        if (state.ocr.isRunning || state.modelDownload.isDownloading || state.enteModelDownload.isDownloading ||
            state.faceFinding.isRunning || state.enteFaceFinding.isRunning
        ) return

        if (targetState.processed >= currentPhotos.size) return

        if (!isInstalled) {
            rescanAllRequested = false
            onNeedsModelDownload(false)
            return
        }

        if (engineId == EnteFaceConstants.ENGINE_ID) {
            enteFaceStartTimeMillis = System.currentTimeMillis()
            enteFaceStartProcessed = state.enteFaceFinding.processed
            _uiState.update { it.copy(enteFaceFinding = it.enteFaceFinding.copy(isRunning = true, isPaused = false)) }
        } else {
            faceStartTimeMillis = System.currentTimeMillis()
            faceStartProcessed = state.faceFinding.processed
            _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isRunning = true, isPaused = false)) }
        }
        IndexingForegroundService.startFace(context, rescanAll = false, engineId = engineId)
    }

    fun stopFaceFinding(engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
        IndexingForegroundService.cancel(context)
        if (engineId == EnteFaceConstants.ENGINE_ID) {
            _uiState.update { it.copy(enteFaceFinding = it.enteFaceFinding.copy(isRunning = false, isPaused = false, itemsPerMinute = 0)) }
        } else {
            _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isRunning = false, isPaused = false, itemsPerMinute = 0)) }
        }
        refreshCounts()
    }

    fun clearAndRescanFaces(
        onNeedsModelDownload: () -> Unit,
        engineId: String = BuffaloLFaceEngine.ENGINE_ID
    ) {
        val state = _uiState.value
        if (state.ocr.isRunning || state.faceFinding.isRunning || state.enteFaceFinding.isRunning ||
            state.modelDownload.isDownloading || state.enteModelDownload.isDownloading
        ) return

        val isInstalled = if (engineId == EnteFaceConstants.ENGINE_ID) state.engine.isEnteInstalled else state.engine.isBuffaloLInstalled
        if (!isInstalled) {
            rescanAllRequested = true
            onNeedsModelDownload()
            return
        }

        if (engineId == EnteFaceConstants.ENGINE_ID) {
            enteFaceStartTimeMillis = System.currentTimeMillis()
            enteFaceStartProcessed = 0
            _uiState.update { it.copy(enteFaceFinding = it.enteFaceFinding.copy(isRunning = true, isPaused = false)) }
        } else {
            faceStartTimeMillis = System.currentTimeMillis()
            faceStartProcessed = 0
            _uiState.update { it.copy(faceFinding = it.faceFinding.copy(isRunning = true, isPaused = false)) }
        }
        IndexingForegroundService.startFace(context, rescanAll = true, engineId = engineId)
    }

    fun startModelDownload(
        force: Boolean = false,
        engineId: String = BuffaloLFaceEngine.ENGINE_ID
    ) {
        val state = _uiState.value
        if (state.modelDownload.isDownloading || state.enteModelDownload.isDownloading ||
            state.ocr.isRunning || state.faceFinding.isRunning || state.enteFaceFinding.isRunning
        ) return

        if (engineId == EnteFaceConstants.ENGINE_ID) {
            _uiState.update {
                it.copy(
                    enteModelDownload = ModelDownloadState(
                        isDownloading = true,
                        isPaused = false,
                        stage = "downloading",
                        bytesReceived = 0L,
                        totalBytes = 0L,
                        error = null
                    )
                )
            }
        } else {
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
        }
        IndexingForegroundService.downloadModel(context, force, engineId = engineId)
    }

    fun cancelModelDownload(engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
        IndexingForegroundService.cancel(context)
        if (engineId == EnteFaceConstants.ENGINE_ID) {
            _uiState.update {
                it.copy(enteModelDownload = it.enteModelDownload.copy(isDownloading = false, isPaused = false))
            }
        } else {
            _uiState.update {
                it.copy(modelDownload = it.modelDownload.copy(isDownloading = false, isPaused = false))
            }
        }
    }

    fun runFaceClustering(
        threshold: Float,
        engineId: String = BuffaloLFaceEngine.ENGINE_ID
    ) {
        val state = _uiState.value
        val clusteringState = if (engineId == EnteFaceConstants.ENGINE_ID) state.enteClustering else state.clustering
        if (clusteringState.isClusteringRunning) return

        if (engineId == EnteFaceConstants.ENGINE_ID) {
            _uiState.update {
                it.copy(
                    enteClustering = it.enteClustering.copy(
                        isClusteringRunning = true,
                        statusMessage = "Executing Ente linear incremental clustering..."
                    )
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    clustering = it.clustering.copy(
                        isClusteringRunning = true,
                        statusMessage = "Clustering facial embeddings..."
                    )
                )
            }
        }

        viewModelScope.launch {
            try {
                val resultCount = withContext(Dispatchers.IO) {
                    if (engineId == EnteFaceConstants.ENGINE_ID) {
                        EnteFaceClusteringStrategy.reclusterChronologically(faceStore, threshold)
                    } else {
                        faceStore.reclusterAll(threshold)
                    }
                }
                val (summaries, detectedCount) = withContext(Dispatchers.IO) {
                    Pair(faceStore.summaries(engineId), faceStore.indexedFaceCount(engineId))
                }

                _uiState.update {
                    if (engineId == EnteFaceConstants.ENGINE_ID) {
                        it.copy(
                            enteClustering = it.enteClustering.copy(
                                summaries = summaries,
                                isClusteringRunning = false,
                                statusMessage = "Successfully grouped into $resultCount people profiles."
                            ),
                            enteFaceFinding = it.enteFaceFinding.copy(totalDetected = detectedCount)
                        )
                    } else {
                        it.copy(
                            clustering = it.clustering.copy(
                                summaries = summaries,
                                isClusteringRunning = false,
                                statusMessage = "Successfully grouped into $resultCount people profiles."
                            ),
                            faceFinding = it.faceFinding.copy(totalDetected = detectedCount)
                        )
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Clustering failed"
                _uiState.update {
                    if (engineId == EnteFaceConstants.ENGINE_ID) {
                        it.copy(
                            enteClustering = it.enteClustering.copy(
                                isClusteringRunning = false,
                                statusMessage = "Clustering error: $errorMsg"
                            )
                        )
                    } else {
                        it.copy(
                            clustering = it.clustering.copy(
                                isClusteringRunning = false,
                                statusMessage = "Clustering error: $errorMsg"
                            )
                        )
                    }
                }
            } finally {
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

    fun resetFaceClusters(engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
        val state = _uiState.value
        val clusteringState = if (engineId == EnteFaceConstants.ENGINE_ID) state.enteClustering else state.clustering
        if (clusteringState.isClusteringRunning) return

        viewModelScope.launch {
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

    // --- Tuning Settings ---

    fun updateDetectionConfidence(confidence: Float, engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
        val rounded = (confidence * 100).roundToInt() / 100f
        FaceRecognitionSettings.setDetectionConfidence(context, engineId, rounded)
        _uiState.update {
            if (engineId == EnteFaceConstants.ENGINE_ID) {
                it.copy(enteFaceFinding = it.enteFaceFinding.copy(detectionConfidence = rounded))
            } else {
                it.copy(faceFinding = it.faceFinding.copy(detectionConfidence = rounded))
            }
        }
    }

    fun updateMinGroupFaces(minFaces: Int, engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
        val coerced = minFaces.coerceIn(1, 30)
        FaceRecognitionSettings.setMinGroupFaces(context, engineId, coerced)
        _uiState.update {
            if (engineId == EnteFaceConstants.ENGINE_ID) {
                it.copy(enteClustering = it.enteClustering.copy(minGroupFaces = coerced))
            } else {
                it.copy(clustering = it.clustering.copy(minGroupFaces = coerced))
            }
        }
    }

    fun updateClusterDistance(distance: Float, engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
        val rounded = (distance * 100).roundToInt() / 100f
        FaceRecognitionSettings.setRecognitionDistance(context, engineId, rounded)
        _uiState.update {
            if (engineId == EnteFaceConstants.ENGINE_ID) {
                it.copy(enteClustering = it.enteClustering.copy(clusterDistance = rounded))
            } else {
                it.copy(clustering = it.clustering.copy(clusterDistance = rounded))
            }
        }
    }

    // --- Location Actions ---

    fun toggleLocationIndexing() {
        val state = _uiState.value
        if (!state.countsReady || currentPhotos.isEmpty()) return
        if (state.location.isRunning) {
            val paused = !state.location.isPaused
            _uiState.update { it.copy(location = it.location.copy(isPaused = paused)) }
            if (paused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
            return
        }
        if (state.ocr.isRunning || state.faceFinding.isRunning || state.enteFaceFinding.isRunning ||
            state.modelDownload.isDownloading || state.enteModelDownload.isDownloading
        ) return
        if (state.location.processed >= currentPhotos.size) return

        locationStartTimeMillis = System.currentTimeMillis()
        locationStartProcessed = state.location.processed
        _uiState.update { it.copy(location = it.location.copy(isRunning = true, isPaused = false)) }
        IndexingForegroundService.startLocation(context, rescanAll = false)
    }

    fun stopLocationIndexing() {
        IndexingForegroundService.cancel(context)
        _uiState.update { it.copy(location = it.location.copy(isRunning = false, isPaused = false)) }
    }

    fun clearAndReindexLocation() {
        locationStartTimeMillis = System.currentTimeMillis()
        locationStartProcessed = 0
        _uiState.update { it.copy(location = it.location.copy(isRunning = true, isPaused = false, processed = 0, locatedCount = 0)) }
        IndexingForegroundService.startLocation(context, rescanAll = true)
    }

    // --- OCR Actions ---

    fun toggleOcr() {
        val state = _uiState.value
        if (!state.countsReady || currentPhotos.isEmpty()) return
        if (state.ocr.isRunning) {
            val paused = !state.ocr.isPaused
            _uiState.update { it.copy(ocr = it.ocr.copy(isPaused = paused)) }
            if (paused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
            return
        }
        if (state.location.isRunning || state.faceFinding.isRunning || state.enteFaceFinding.isRunning ||
            state.modelDownload.isDownloading || state.enteModelDownload.isDownloading
        ) return
        if (state.ocr.processed >= currentPhotos.size) return

        ocrStartTimeMillis = System.currentTimeMillis()
        ocrStartProcessed = state.ocr.processed
        _uiState.update { it.copy(ocr = it.ocr.copy(isRunning = true, isPaused = false)) }
        IndexingForegroundService.startOcr(context, currentOcrLanguages.toList(), rescanAll = false, modelId = currentOcrModel)
    }

    fun stopOcr() {
        IndexingForegroundService.cancel(context)
        _uiState.update { it.copy(ocr = it.ocr.copy(isRunning = false, isPaused = false)) }
    }

    fun clearAndReindexOcr() {
        ocrStartTimeMillis = System.currentTimeMillis()
        ocrStartProcessed = 0
        _uiState.update { it.copy(ocr = it.ocr.copy(isRunning = true, isPaused = false, processed = 0)) }
        IndexingForegroundService.startOcr(context, currentOcrLanguages.toList(), rescanAll = true, modelId = currentOcrModel)
    }
}
