package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Native Android media detail drawer implemented with Material 3 ModalBottomSheet.
 * Features native gestures, partial expansion, flinging, nested scrolling, scrim, and dismissal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerDetailsDrawer(
    item: MediaItem,
    items: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    customTags: Set<String>,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onAddTo: () -> Unit,
    onToggleBin: () -> Unit,
    onShowMore: () -> Unit,
    onOpenMap: () -> Unit,
    onAddTag: (String) -> Unit,
    isBin: Boolean,
    defaultOcrLanguages: Set<String> = setOf("eng"),
    defaultOcrModel: String = "fast",
    defaultMlKitLanguages: Set<String> = setOf("latin"),
    onOpenPerson: ((personId: String, engineId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val detailsState = rememberViewerDetailsState(item, defaultOcrLanguages, defaultOcrModel, defaultMlKitLanguages)

    var selectedTab by remember { mutableStateOf(DetailDrawerTab.OVERVIEW) }
    val faceStore = remember(context) { FaceClusterStore(context.applicationContext) }
    var faceReloadKey by remember { mutableIntStateOf(0) }
    val activeEngineId = remember(context) { FaceRecognitionSettings.getActiveEngine(context) }
    var selectedDrawerEngineId by remember(activeEngineId) { mutableStateOf(activeEngineId) }
    val factory = remember(selectedDrawerEngineId) {
        FaceEngineRegistry.getFactory(selectedDrawerEngineId) ?: BuffaloLFaceEngineFactory
    }

    var detectedFaces by remember(item.uri, selectedDrawerEngineId) { mutableStateOf<List<DetectedMediaFace>>(emptyList()) }
    var allDetectedFaces by remember(item.uri, selectedDrawerEngineId) { mutableStateOf<List<DetectedMediaFace>>(emptyList()) }
    var mediaIndexInfo by remember(item.uri, selectedDrawerEngineId) { mutableStateOf<FaceClusterStore.MediaIndexedInfo?>(null) }
    var groupingRequired by remember(item.uri, selectedDrawerEngineId) { mutableStateOf(false) }
    var groupingError by remember(item.uri, selectedDrawerEngineId) { mutableStateOf<String?>(null) }
    var isLoadingIndexInfo by remember(item.uri, selectedDrawerEngineId) { mutableStateOf(true) }

    var singleScanJob by remember(item.uri) { mutableStateOf<Job?>(null) }
    var scanPhase by remember(item.uri) { mutableStateOf<String?>(null) }
    var lastScanError by remember(item.uri) { mutableStateOf<String?>(null) }
    var showErrorDetailsDialog by remember { mutableStateOf(false) }
    var showFaceChooserDialog by remember { mutableStateOf(false) }
    val isProcessingActive by FaceProcessingCoordinator.isProcessingActive.collectAsStateWithLifecycle()
    LaunchedEffect(isProcessingActive) {
        if (isProcessingActive) {
            showFaceChooserDialog = false
        }
    }
    var isInstallingModel by remember { mutableStateOf(false) }
    var modelInstallProgress by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.uri, faceReloadKey, selectedDrawerEngineId, factory) {
        suspend fun refreshFaceState() {
            val snapshot = withContext(Dispatchers.IO) {
                val allFaces = faceStore.facesForMedia(item.uri, selectedDrawerEngineId)
                val info = faceStore.getMediaIndexedInfo(item.uri, selectedDrawerEngineId)
                val strategy = factory.clusteringStrategy
                val needsGrouping = strategy?.let {
                    faceStore.isGroupingRequired(selectedDrawerEngineId, factory.modelVersion, it.strategyVersion)
                } ?: false
                val error = strategy?.let {
                    faceStore.groupingFailure(selectedDrawerEngineId, factory.modelVersion, it.strategyVersion)
                }
                val visibleFaces = allFaces
                FaceStateSnapshot(allFaces, visibleFaces, info, needsGrouping, error)
            }
            allDetectedFaces = snapshot.allFaces
            detectedFaces = snapshot.visibleFaces
            mediaIndexInfo = snapshot.info
            groupingRequired = snapshot.groupingRequired
            groupingError = snapshot.groupingError
            isLoadingIndexInfo = false
        }

        kotlinx.coroutines.coroutineScope {
            launch {
                FaceStoreEvents.mediaUpdates.collect { updatedUri ->
                    if (updatedUri.isBlank() || updatedUri == item.uri) refreshFaceState()
                }
            }
            launch {
                FaceStoreEvents.peopleUpdates.collect { refreshFaceState() }
            }
            refreshFaceState()
        }
    }

    val isModelInstalled = remember(selectedDrawerEngineId, faceReloadKey) {
        factory.isInstalled(context)
    }

    val startScan: (Boolean) -> Unit = { lowThreshold ->
        if (singleScanJob?.isActive == true || item.isVideo || FaceProcessingCoordinator.isBusy(context)) {
        } else if (!isModelInstalled) {
        } else {
            lastScanError = null
            scanPhase = "Initializing..."
            singleScanJob = coroutineScope.launch {
                val scanHandle = FaceProcessingCoordinator.beginOperation(
                    operationId = "viewer_single_scan_${item.uri}",
                    type = FaceProcessingOperation.SCANNING,
                    engineId = selectedDrawerEngineId,
                    description = "Scanning single photo"
                )
                try {
                    val retryGroupingOnly = groupingError != null || groupingRequired
                    val threshold = if (lowThreshold) FaceRecognitionSettings.LOW_DETECTION_CONFIDENCE_THRESHOLD else FaceRecognitionSettings.detectionConfidence(context, selectedDrawerEngineId)
                    val repo = FaceIndexingRepository(context.applicationContext, factory)
                    repo.indexNewMedia(
                        media = listOf(item),
                        store = faceStore,
                        forceRescanUris = if (retryGroupingOnly) emptySet() else setOf(item.uri),
                        targetUrisOnly = setOf(item.uri),
                        detectionThreshold = threshold,
                        onProgress = { progress ->
                            scanPhase = when {
                                progress.clusteringStage.state == StageState.RUNNING -> "Clustering..."
                                progress.currentItemStatus.isNotBlank() -> progress.currentItemStatus
                                progress.embeddingStage.state == StageState.RUNNING -> "Generating embeddings..."
                                progress.detectionStage.state == StageState.RUNNING -> "Detecting faces..."
                                else -> "Processing..."
                            }
                        }
                    )
                    faceReloadKey++
                } catch (e: CancellationException) {
                    scanPhase = null
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: e.message ?: "Failed to scan photo"
                    lastScanError = msg
                    withContext(Dispatchers.IO) {
                        faceStore.markMediaFailed(item.uri, selectedDrawerEngineId, msg)
                    }
                    faceReloadKey++
                } finally {
                    scanHandle.close()
                    singleScanJob = null
                    scanPhase = null
                }
            }
        }
    }

    val cancelScan: () -> Unit = {
        singleScanJob?.cancel()
        singleScanJob = null
        scanPhase = null
    }

    val installModel: () -> Unit = {
        if (!isInstallingModel && !FaceProcessingCoordinator.isBusy(context)) {
            isInstallingModel = true
            modelInstallProgress = "Starting download..."
            coroutineScope.launch {
                val installHandle = FaceProcessingCoordinator.beginOperation(
                    operationId = "viewer_model_install",
                    type = FaceProcessingOperation.MODEL_DOWNLOAD,
                    engineId = selectedDrawerEngineId,
                    description = "Installing face model"
                )
                try {
                    val repo = FaceIndexingRepository(context.applicationContext, factory)
                    repo.downloadModel(
                        force = true,
                        onDetailedProgress = { p ->
                            modelInstallProgress = "${p.stage.replaceFirstChar { it.uppercase() }} (${p.percentage}%)"
                        }
                    )
                    faceReloadKey++
                } catch (e: Exception) {
                    lastScanError = "Failed to download model: ${e.localizedMessage ?: e.message}"
                } finally {
                    installHandle.close()
                    isInstallingModel = false
                    modelInstallProgress = null
                }
            }
        }
    }

    val isScanning = singleScanJob?.isActive == true
    val faceUiState = remember(
        isModelInstalled, isInstallingModel, modelInstallProgress, isScanning, scanPhase,
        detectedFaces, mediaIndexInfo, lastScanError, isLoadingIndexInfo, groupingRequired, groupingError, allDetectedFaces
    ) {
        computeViewerFaceUiState(
            isModelInstalled, isInstallingModel, modelInstallProgress, isScanning, scanPhase,
            detectedFaces, mediaIndexInfo, lastScanError, isLoadingIndexInfo, groupingRequired,
            groupingError, allDetectedFaces, factory.displayName, selectedDrawerEngineId
        )
    }

    val overviewListState = rememberLazyListState()
    val actionsListState = rememberLazyListState()

    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var renamingFace by remember { mutableStateOf<DetectedMediaFace?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)


    ViewerDetailsModalSheet(
        item = item,
        selectedTab = selectedTab,
        onSelectTab = { selectedTab = it },
        customAlbums = customAlbums,
        customTags = customTags,
        detectedFaces = detectedFaces,
        detailsState = detailsState,
        faceStore = faceStore,
        faceUiState = faceUiState,
        selectedDrawerEngineId = selectedDrawerEngineId,
        onSelectedDrawerEngineIdChange = { selectedDrawerEngineId = it },
        overviewListState = overviewListState,
        actionsListState = actionsListState,
        sheetState = sheetState,
        isProcessingActive = isProcessingActive,
        lastScanError = lastScanError,
        mediaIndexInfo = mediaIndexInfo,
        engineDisplayName = factory.displayName,
        showErrorDetailsDialog = showErrorDetailsDialog,
        onShowErrorDetailsChange = { showErrorDetailsDialog = it },
        showFaceChooserDialog = showFaceChooserDialog,
        onShowFaceChooserChange = { showFaceChooserDialog = it },
        onStartScan = { startScan(it) },
        onCancelScan = cancelScan,
        onInstallModel = installModel,
        onShowAddLocation = { showAddLocationDialog = true },
        onShowAddTag = { showAddTagDialog = true },
        onRenamingFaceChange = { renamingFace = it },
        onOpenMap = onOpenMap,
        onAddTo = onAddTo,
        onClose = onClose,
        onOpenPerson = onOpenPerson,
        modifier = modifier
    )

    ViewerDetailsLocationTagPersonDialogs(
        item = item,
        detailsState = detailsState,
        faceStore = faceStore,
        showAddLocationDialog = showAddLocationDialog,
        onDismissAddLocation = { showAddLocationDialog = false },
        onOpenMap = onOpenMap,
        showAddTagDialog = showAddTagDialog,
        onDismissAddTag = { showAddTagDialog = false },
        onAddTag = onAddTag,
        renamingFace = renamingFace,
        onDismissRenamingFace = { renamingFace = null },
        onIncrementFaceReloadKey = { faceReloadKey++ }
    )
}
