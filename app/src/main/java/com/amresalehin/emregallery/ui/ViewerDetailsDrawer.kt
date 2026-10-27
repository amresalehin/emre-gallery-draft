package com.amresalehin.emregallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Geocoder
import android.net.Uri
import com.amresalehin.emregallery.IndexingForegroundService
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.PhotoMapLocationCache
import com.amresalehin.emregallery.face.*
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.util.GeoPoint
import java.util.Locale

@Stable
class ViewerDetailsState(
    val context: Context,
    val item: MediaItem,
    defaultOcrLanguages: Set<String>,
    initialOcrModel: String = "fast"
) {
    var metadata by mutableStateOf<Map<String, String>>(emptyMap())
    var ocrText by mutableStateOf("")
    var ocrError by mutableStateOf<String?>(null)
    var ocrBusy by mutableStateOf(false)
    var ocrModel by mutableStateOf(initialOcrModel)
    val ocrLanguages = mutableStateListOf<String>().apply {
        addAll(if (defaultOcrLanguages.isNotEmpty()) defaultOcrLanguages else setOf("eng"))
    }

    suspend fun loadData() {
        metadata = withContext(Dispatchers.IO) { runCatching { readMediaMetadata(context, item) }.getOrDefault(emptyMap()) }
        ocrText = withContext(Dispatchers.IO) { TesseractOcr.readCachedResult(context, Uri.parse(item.uri)).orEmpty() }
        ocrError = null
    }

    fun toggleOcrLanguage(code: String) {
        if (code in ocrLanguages) { if (ocrLanguages.size > 1) ocrLanguages.remove(code) } else ocrLanguages.add(code)
    }

    suspend fun runOcr() {
        if (item.isVideo || ocrBusy || ocrLanguages.isEmpty()) return
        ocrBusy = true; ocrError = null; ocrText = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val mediaUri = Uri.parse(item.uri)
                val model = com.amresalehin.emregallery.ocr.TesseractModel.fromId(ocrModel)
                val recognized = TesseractOcr.recognize(context, mediaUri, ocrLanguages.toList(), model)
                runCatching { TesseractOcr.saveCachedResult(context, mediaUri, recognized) }
                recognized
            }
        }
        result.onSuccess { ocrText = it.ifBlank { "No text detected in this image." } }
            .onFailure { ocrError = it.message ?: "OCR failed. Please try again." }
        ocrBusy = false
    }

    fun copyOcrText() {
        if (ocrText.isNotBlank() && ocrText != "No text detected in this image.") {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.setPrimaryClip(ClipData.newPlainText("Recognized text", ocrText))
        }
    }
}

@Composable
fun rememberViewerDetailsState(item: MediaItem, defaultOcrLanguages: Set<String>, defaultOcrModel: String = "fast"): ViewerDetailsState {
    val context = LocalContext.current
    val state = remember(item.uri, defaultOcrLanguages, defaultOcrModel) { ViewerDetailsState(context, item, defaultOcrLanguages, defaultOcrModel) }
    LaunchedEffect(item.uri, defaultOcrLanguages, defaultOcrModel) { state.loadData() }
    return state
}

private data class FaceStateSnapshot(
    val allFaces: List<DetectedMediaFace>,
    val visibleFaces: List<DetectedMediaFace>,
    val info: FaceClusterStore.MediaIndexedInfo?,
    val groupingRequired: Boolean,
    val groupingError: String?
)

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val detailsState = rememberViewerDetailsState(item, defaultOcrLanguages, defaultOcrModel)
    var selectedTab by remember { mutableStateOf(DetailDrawerTab.OVERVIEW) }
    val faceStore = remember(context) { FaceClusterStore(context.applicationContext) }
    var faceReloadKey by remember { mutableIntStateOf(0) }
    val activeEngineFlow = remember(context) { FaceRecognitionSettings.activeEngineFlow(context) }
    val activeEngineId by activeEngineFlow.collectAsState(initial = FaceRecognitionSettings.getActiveEngine(context))
    val factory = remember(activeEngineId) {
        FaceEngineRegistry.getFactory(activeEngineId) ?: error("Unknown face engine: $activeEngineId")
    }
    var detectedFaces by remember(item.uri, activeEngineId) { mutableStateOf<List<DetectedMediaFace>>(emptyList()) }
    var allDetectedFaces by remember(item.uri, activeEngineId) { mutableStateOf<List<DetectedMediaFace>>(emptyList()) }
    var mediaIndexInfo by remember(item.uri, activeEngineId) { mutableStateOf<FaceClusterStore.MediaIndexedInfo?>(null) }
    var groupingRequired by remember(item.uri, activeEngineId) { mutableStateOf(false) }
    var groupingError by remember(item.uri, activeEngineId) { mutableStateOf<String?>(null) }
    var isLoadingIndexInfo by remember(item.uri, activeEngineId) { mutableStateOf(true) }
    var singleScanJob by remember(item.uri) { mutableStateOf<Job?>(null) }
    var scanPhase by remember(item.uri) { mutableStateOf<String?>(null) }
    var lastScanError by remember(item.uri) { mutableStateOf<String?>(null) }
    var showErrorDetailsDialog by remember { mutableStateOf(false) }
    var showFaceChooserDialog by remember { mutableStateOf(false) }
    var isInstallingModel by remember { mutableStateOf(false) }
    var modelInstallProgress by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.uri, faceReloadKey, activeEngineId, factory) {
        suspend fun refreshFaceState() {
            val snapshot = withContext(Dispatchers.IO) {
                val allFaces = faceStore.facesForMedia(item.uri, activeEngineId)
                val info = faceStore.getMediaIndexedInfo(item.uri, activeEngineId)
                val strategy = factory.clusteringStrategy
                val revision = strategy?.let { groupingRevision(it.strategyVersion, FaceRecognitionSettings.recognitionDistance(context, activeEngineId)) }
                val needsGrouping = if (strategy != null && revision != null) faceStore.isGroupingRequired(activeEngineId, factory.modelVersion, revision) else false
                val error = if (strategy != null && revision != null) faceStore.groupingFailure(activeEngineId, factory.modelVersion, revision) else null
                val visibleFaces = if (needsGrouping || error != null) emptyList() else allFaces.filter { !it.personId.isNullOrBlank() && it.clusterId.isNotBlank() }
                FaceStateSnapshot(allFaces, visibleFaces, info, needsGrouping, error)
            }
            allDetectedFaces = snapshot.allFaces; detectedFaces = snapshot.visibleFaces; mediaIndexInfo = snapshot.info
            groupingRequired = snapshot.groupingRequired; groupingError = snapshot.groupingError; isLoadingIndexInfo = false
        }
        kotlinx.coroutines.coroutineScope {
            launch { FaceStoreEvents.mediaUpdates.collect { updatedUri -> if (updatedUri.isBlank() || updatedUri == item.uri) refreshFaceState() } }
            launch { FaceStoreEvents.peopleUpdates.collect { refreshFaceState() } }
            refreshFaceState()
        }
    }

    var isModelInstalled by remember(activeEngineId) { mutableStateOf(true) }
    LaunchedEffect(activeEngineId, faceReloadKey, factory) {
        isModelInstalled = withContext(Dispatchers.IO) { factory.isInstalled(context) }
    }

    val startScan: (Boolean) -> Unit = { lowThreshold ->
        if (singleScanJob?.isActive != true && !item.isVideo && isModelInstalled) {
            lastScanError = null; scanPhase = "Initializing..."
            singleScanJob = coroutineScope.launch {
                try {
                    val retryGroupingOnly = groupingError != null || groupingRequired
                    val threshold = if (lowThreshold) FaceRecognitionSettings.LOW_DETECTION_CONFIDENCE_THRESHOLD else FaceRecognitionSettings.detectionConfidence(context, activeEngineId)
                    FaceIndexingRepository(context.applicationContext, factory).indexNewMedia(
                        media = listOf(item), store = faceStore,
                        forceRescanUris = if (retryGroupingOnly) emptySet() else setOf(item.uri),
                        targetUrisOnly = setOf(item.uri), detectionThreshold = threshold,
                        recognitionDistance = FaceRecognitionSettings.recognitionDistance(context, activeEngineId),
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
                } catch (e: CancellationException) { scanPhase = null }
                catch (e: Exception) {
                    val msg = e.localizedMessage ?: e.message ?: "Failed to scan photo"
                    lastScanError = msg
                    withContext(Dispatchers.IO) { faceStore.markMediaFailed(item.uri, activeEngineId, msg) }
                    faceReloadKey++
                } finally { singleScanJob = null; scanPhase = null }
            }
        }
    }
    val cancelScan: () -> Unit = { singleScanJob?.cancel(); singleScanJob = null; scanPhase = null }
    val installModel: () -> Unit = {
        if (!isInstallingModel) {
            lastScanError = null
            isInstallingModel = true
            modelInstallProgress = "Starting download..."
            // Use the same foreground-service job and progress state as Indexing Status.
            IndexingForegroundService.downloadModel(context, force = true, engineId = activeEngineId)
        }
    }

    LaunchedEffect(activeEngineId, isInstallingModel) {
        if (!isInstallingModel) return@LaunchedEffect
        val prefs = context.getSharedPreferences(IndexingForegroundService.STATE_PREFS, Context.MODE_PRIVATE)
        var observedDownload = false
        var waitingPolls = 0
        while (isActive) {
            val task = prefs.getString(IndexingForegroundService.KEY_TASK, "").orEmpty()
            val taskEngine = prefs.getString(IndexingForegroundService.KEY_ENGINE_ID, BuffaloLFaceEngine.ENGINE_ID).orEmpty()
            val running = prefs.getBoolean(IndexingForegroundService.KEY_RUNNING, false)
            if (task == "model" && taskEngine == activeEngineId && running) {
                observedDownload = true
                waitingPolls = 0
                val stage = prefs.getString(IndexingForegroundService.KEY_STAGE, "downloading").orEmpty()
                val percent = prefs.getInt(IndexingForegroundService.KEY_PROCESSED, 0).coerceIn(0, 100)
                modelInstallProgress = if (stage == "downloading") {
                    "Downloading model ($percent%)"
                } else {
                    stage.replaceFirstChar { it.uppercase() } + if (percent > 0) " ($percent%)" else "..."
                }
            } else if (observedDownload) {
                isInstallingModel = false
                modelInstallProgress = null
                val error = prefs.getString(IndexingForegroundService.KEY_ERROR, null)
                isModelInstalled = withContext(Dispatchers.IO) { factory.isInstalled(context) }
                if (error != null) lastScanError = "Failed to download model: $error"
                else if (isModelInstalled) lastScanError = null
                faceReloadKey++
                return@LaunchedEffect
            } else {
                waitingPolls++
                // Another foreground indexing task may have prevented this request from starting.
                if (waitingPolls >= 20) {
                    isInstallingModel = false
                    modelInstallProgress = null
                    isModelInstalled = withContext(Dispatchers.IO) { factory.isInstalled(context) }
                    lastScanError = if (prefs.getBoolean(IndexingForegroundService.KEY_RUNNING, false)) {
                        "Another indexing task is active. Finish or cancel it before downloading a model."
                    } else {
                        "The model download did not start. Please retry."
                    }
                    faceReloadKey++
                    return@LaunchedEffect
                }
            }
            delay(250)
        }
    }

    val isScanning = singleScanJob?.isActive == true
    val faceUiState = remember(isModelInstalled, isInstallingModel, modelInstallProgress, isScanning, scanPhase, detectedFaces, mediaIndexInfo, lastScanError, isLoadingIndexInfo, groupingRequired, groupingError, allDetectedFaces) {
        val detectedCount = maxOf(mediaIndexInfo?.faceCount ?: 0, allDetectedFaces.size)
        when {
            isScanning -> ViewerFaceUiState(state = ViewerFaceState.IN_PROGRESS, progressPhase = scanPhase ?: "Scanning photo...", engineName = factory.displayName, engineId = activeEngineId)
            isInstallingModel -> ViewerFaceUiState(state = ViewerFaceState.IN_PROGRESS, progressPhase = modelInstallProgress ?: "Installing model...", engineName = factory.displayName, engineId = activeEngineId)
            lastScanError != null && (!isModelInstalled || (detectedFaces.isEmpty() && groupingError == null)) ->
                ViewerFaceUiState(ViewerFaceState.FAILED, errorMessage = lastScanError, engineName = factory.displayName, engineId = activeEngineId)
            !isModelInstalled -> ViewerFaceUiState(ViewerFaceState.MODEL_MISSING, engineName = factory.displayName, engineId = activeEngineId)
            isLoadingIndexInfo -> ViewerFaceUiState(ViewerFaceState.NOT_INDEXED, engineName = factory.displayName, engineId = activeEngineId)
            mediaIndexInfo?.status == "failed" -> ViewerFaceUiState(ViewerFaceState.FAILED, errorMessage = mediaIndexInfo?.errorMessage ?: "Face scan failed for this photo", engineName = factory.displayName, engineId = activeEngineId)
            groupingError != null -> ViewerFaceUiState(ViewerFaceState.FAILED, errorMessage = "Grouping failed: $groupingError", engineName = factory.displayName, engineId = activeEngineId, groupingFailure = true)
            groupingRequired && detectedCount > 0 -> ViewerFaceUiState(state = ViewerFaceState.WAITING_FOR_GROUPING, progressPhase = "$detectedCount faces found · waiting to be grouped", engineName = factory.displayName, engineId = activeEngineId)
            detectedFaces.isNotEmpty() -> ViewerFaceUiState(ViewerFaceState.COMPLETED_FACES_FOUND, faces = detectedFaces, engineName = factory.displayName, engineId = activeEngineId)
            detectedCount > 0 -> ViewerFaceUiState(state = ViewerFaceState.GROUPING_INCOMPLETE, progressPhase = "$detectedCount faces found · waiting for a person group", engineName = factory.displayName, engineId = activeEngineId)
            mediaIndexInfo?.status == "done" -> ViewerFaceUiState(ViewerFaceState.COMPLETED_NO_FACES, engineName = factory.displayName, engineId = activeEngineId)
            else -> ViewerFaceUiState(ViewerFaceState.NOT_INDEXED, engineName = factory.displayName, engineId = activeEngineId)
        }
    }

    val overviewListState = rememberLazyListState()
    val actionsListState = rememberLazyListState()
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var renamingFace by remember { mutableStateOf<DetectedMediaFace?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
            DetailDrawerHeader(item = item, selectedTab = selectedTab, onSelectTab = { selectedTab = it })
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (selectedTab) {
                    DetailDrawerTab.OVERVIEW -> OverviewTabContent(
                        item = item, customAlbums = customAlbums, customTags = customTags, detectedFaces = detectedFaces,
                        metadata = detailsState.metadata, onOpenMap = onOpenMap, onAddToAlbum = onAddTo,
                        onAddTag = { showAddTagDialog = true }, onEditLocation = { showAddLocationDialog = true },
                        listState = overviewListState, faceUiState = faceUiState, onScanThisPhoto = { startScan(false) },
                        onCancelScan = cancelScan, onRescanPhoto = { startScan(false) }, onRescanLowThreshold = { startScan(true) },
                        onInstallModel = installModel, onRetryScan = { startScan(false) }, onViewErrorDetails = { showErrorDetailsDialog = true },
                        onEditTagOrPerson = {
                            if (detectedFaces.size == 1) renamingFace = detectedFaces.first()
                            else if (detectedFaces.size > 1) showFaceChooserDialog = true else showAddTagDialog = true
                        }, onFaceClick = { if (!it.personId.isNullOrBlank()) renamingFace = it })
                    DetailDrawerTab.ACTIONS -> ActionsTabContent(item = item, customTags = customTags,
                        detectedFaces = detectedFaces, metadata = detailsState.metadata, detailsState = detailsState,
                        onRenameFaceClick = { renamingFace = it }, onRescanPhoto = { startScan(false) }, listState = actionsListState)
                }
            }
        }
    }

    if (showAddLocationDialog) {
        var locationInput by remember { mutableStateOf("") }
        var isGeocoding by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { showAddLocationDialog = false }, title = { Text("Add location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a place name, city, or coordinates:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = locationInput, onValueChange = { locationInput = it }, label = { Text("Location name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { showAddLocationDialog = false; onOpenMap() }, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Select on map")
                    }
                }
            }, confirmButton = {
                Button(onClick = {
                    val input = locationInput.trim()
                    if (input.isNotEmpty()) {
                        isGeocoding = true
                        coroutineScope.launch {
                            val geocoder = runCatching { @Suppress("DEPRECATION") Geocoder(context, Locale.getDefault()).getFromLocationName(input, 1) }.getOrNull()?.firstOrNull()
                            val point = if (geocoder != null) GeoPoint(geocoder.latitude, geocoder.longitude) else {
                                val parts = input.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                                if (parts.size >= 2) GeoPoint(parts[0], parts[1]) else null
                            }
                            withContext(Dispatchers.IO) { PhotoMapLocationCache(context).save(item.uri, point) }
                            detailsState.metadata = detailsState.metadata.toMutableMap().apply {
                                this["location"] = input; if (point != null) { this["latitude"] = point.latitude.toString(); this["longitude"] = point.longitude.toString() }
                            }
                            isGeocoding = false; showAddLocationDialog = false
                        }
                    }
                }, enabled = !isGeocoding && locationInput.isNotBlank()) {
                    if (isGeocoding) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Save")
                }
            }, dismissButton = { TextButton(onClick = { showAddLocationDialog = false }) { Text("Cancel") } })
    }

    if (showAddTagDialog) {
        var tagInput by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAddTagDialog = false }, title = { Text("Add tag") },
            text = { OutlinedTextField(value = tagInput, onValueChange = { tagInput = it }, label = { Text("Tag name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { val trimmed = tagInput.trim(); if (trimmed.isNotEmpty()) { onAddTag(trimmed); showAddTagDialog = false } }, enabled = tagInput.isNotBlank()) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showAddTagDialog = false }) { Text("Cancel") } })
    }

    if (renamingFace != null) {
        val face = renamingFace!!
        val personId = face.personId.orEmpty()
        var nameInput by remember(face) { mutableStateOf(if (face.displayName.isBlank() || face.displayName.equals("Unknown", true)) "" else face.displayName) }
        AlertDialog(onDismissRequest = { renamingFace = null },
            title = { Text(if (face.displayName.isBlank() || face.displayName.equals("Unknown", true)) "Name this person" else "Person details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Person name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        coroutineScope.launch(Dispatchers.IO) { faceStore.removeFaceFromPerson(face.mediaUri, face.faceIndex, engineId = face.engineId); faceReloadKey++; renamingFace = null }
                    }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Remove face from this person")
                    }
                }
            }, confirmButton = {
                Button(onClick = {
                    val cleanName = nameInput.trim()
                    if (cleanName.isNotEmpty()) coroutineScope.launch(Dispatchers.IO) {
                        if (personId.isNotBlank()) faceStore.renamePerson(personId, cleanName); faceReloadKey++; renamingFace = null
                    }
                }, enabled = nameInput.isNotBlank() && personId.isNotBlank()) { Text("Save") }
            }, dismissButton = { TextButton(onClick = { renamingFace = null }) { Text("Cancel") } })
    }

    if (showErrorDetailsDialog) FaceErrorDetailsDialog(errorMessage = lastScanError ?: mediaIndexInfo?.errorMessage ?: "Unknown error", engineName = factory.displayName, onDismiss = { showErrorDetailsDialog = false })

    if (showFaceChooserDialog && detectedFaces.isNotEmpty()) {
        AlertDialog(onDismissRequest = { showFaceChooserDialog = false }, title = { Text("Select person to name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detectedFaces.forEach { face ->
                        val isNamed = face.displayName.isNotBlank() && !face.displayName.equals("Unknown", ignoreCase = true)
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().clickable { showFaceChooserDialog = false; renamingFace = face }) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.size(40.dp)) {
                                    FaceCropThumbnail(mediaUri = face.portraitMediaUri ?: face.mediaUri, faceIndex = face.portraitFaceIndex ?: face.faceIndex,
                                        engineId = face.engineId, contentDescription = if (isNamed) face.displayName else "Person", modifier = Modifier.fillMaxSize().clip(CircleShape))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column { Text(if (isNamed) face.displayName else "Name this person", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showFaceChooserDialog = false }) { Text("Cancel") } })
    }
}
