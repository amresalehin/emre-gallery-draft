package com.amresalehin.emregallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Geocoder
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.osmdroid.util.GeoPoint
import java.util.Locale

/**
 * State holder for the media details drawer (metadata and OCR).
 */
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
        val initial = if (defaultOcrLanguages.isNotEmpty()) defaultOcrLanguages else setOf("eng")
        addAll(initial)
    }

    suspend fun loadData() {
        metadata = withContext(Dispatchers.IO) {
            runCatching { readMediaMetadata(context, item) }.getOrDefault(emptyMap())
        }
        ocrText = withContext(Dispatchers.IO) {
            TesseractOcr.readCachedResult(context, Uri.parse(item.uri)).orEmpty()
        }
        ocrError = null
    }

    fun toggleOcrLanguage(code: String) {
        if (code in ocrLanguages) {
            if (ocrLanguages.size > 1) ocrLanguages.remove(code)
        } else {
            ocrLanguages.add(code)
        }
    }

    suspend fun runOcr() {
        if (item.isVideo || ocrBusy || ocrLanguages.isEmpty()) return
        ocrBusy = true
        ocrError = null
        ocrText = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val mediaUri = Uri.parse(item.uri)
                val model = com.amresalehin.emregallery.ocr.TesseractModel.fromId(ocrModel)
                val recognized = TesseractOcr.recognize(context, mediaUri, ocrLanguages.toList(), model)
                runCatching { TesseractOcr.saveCachedResult(context, mediaUri, recognized) }
                recognized
            }
        }
        result.onSuccess { text ->
            ocrText = text.ifBlank { "No text detected in this image." }
        }.onFailure { error ->
            ocrError = error.message ?: "OCR failed. Please try again."
        }
        ocrBusy = false
    }

    fun copyOcrText() {
        if (ocrText.isNotBlank() && ocrText != "No text detected in this image.") {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Recognized text", ocrText))
        }
    }
}

@Composable
fun rememberViewerDetailsState(
    item: MediaItem,
    defaultOcrLanguages: Set<String>,
    defaultOcrModel: String = "fast"
): ViewerDetailsState {
    val context = LocalContext.current
    val state = remember(item.uri, defaultOcrLanguages, defaultOcrModel) {
        ViewerDetailsState(context, item, defaultOcrLanguages, defaultOcrModel)
    }

    LaunchedEffect(item.uri, defaultOcrLanguages, defaultOcrModel) {
        state.loadData()
    }

    return state
}

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val detailsState = rememberViewerDetailsState(item, defaultOcrLanguages, defaultOcrModel)

    var selectedTab by remember { mutableStateOf(DetailDrawerTab.OVERVIEW) }
    val faceStore = remember(context) { FaceClusterStore(context.applicationContext) }
    var faceReloadKey by remember { mutableIntStateOf(0) }
    val activeEngineId = remember(context) { FaceRecognitionSettings.getActiveEngine(context) }
    val factory = remember(activeEngineId) {
        FaceEngineRegistry.getFactory(activeEngineId) ?: BuffaloLFaceEngineFactory
    }

    var detectedFaces by remember(item.uri, activeEngineId) { mutableStateOf<List<DetectedMediaFace>>(emptyList()) }
    var mediaIndexInfo by remember(item.uri, activeEngineId) { mutableStateOf<FaceClusterStore.MediaIndexedInfo?>(null) }
    var isLoadingIndexInfo by remember(item.uri, activeEngineId) { mutableStateOf(true) }

    var singleScanJob by remember(item.uri) { mutableStateOf<Job?>(null) }
    var scanPhase by remember(item.uri) { mutableStateOf<String?>(null) }
    var lastScanError by remember(item.uri) { mutableStateOf<String?>(null) }
    var showErrorDetailsDialog by remember { mutableStateOf(false) }
    var showFaceChooserDialog by remember { mutableStateOf(false) }
    var isInstallingModel by remember { mutableStateOf(false) }
    var modelInstallProgress by remember { mutableStateOf<String?>(null) }

    // Strict Engine Scoping: Query exclusively for activeEngineId, and subscribe to live store updates!
    LaunchedEffect(item.uri, faceReloadKey, activeEngineId) {
        withContext(Dispatchers.IO) {
            val faces = faceStore.facesForMedia(item.uri, activeEngineId)
            val info = faceStore.getMediaIndexedInfo(item.uri, activeEngineId)
            withContext(Dispatchers.Main) {
                detectedFaces = faces
                mediaIndexInfo = info
                isLoadingIndexInfo = false
            }
        }
        FaceStoreEvents.mediaUpdates.collect { updatedUri ->
            if (updatedUri == item.uri) {
                withContext(Dispatchers.IO) {
                    val faces = faceStore.facesForMedia(item.uri, activeEngineId)
                    val info = faceStore.getMediaIndexedInfo(item.uri, activeEngineId)
                    withContext(Dispatchers.Main) {
                        detectedFaces = faces
                        mediaIndexInfo = info
                        isLoadingIndexInfo = false
                    }
                }
            }
        }
    }

    val isModelInstalled = remember(activeEngineId, faceReloadKey) {
        factory.isInstalled(context)
    }

    val startScan: (Boolean) -> Unit = { lowThreshold ->
        if (singleScanJob?.isActive == true || item.isVideo) {
            // mutex: active scan
        } else if (!isModelInstalled) {
            // missing dependencies
        } else {
            lastScanError = null
            scanPhase = "Initializing..."
            singleScanJob = coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        faceStore.clearMediaFaces(item.uri, activeEngineId)
                    }
                    val threshold = if (lowThreshold) 0.35f else FaceRecognitionSettings.detectionConfidence(context, activeEngineId)
                    val repo = FaceIndexingRepository(context.applicationContext, factory)
                    repo.indexNewMedia(
                        media = listOf(item),
                        store = faceStore,
                        forceRescanUris = setOf(item.uri),
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
                        faceStore.markMediaFailed(item.uri, activeEngineId, msg)
                    }
                    faceReloadKey++
                } finally {
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
        if (!isInstallingModel) {
            isInstallingModel = true
            modelInstallProgress = "Starting download..."
            coroutineScope.launch {
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
                    isInstallingModel = false
                    modelInstallProgress = null
                }
            }
        }
    }

    val isScanning = singleScanJob?.isActive == true
    val faceUiState = remember(
        isModelInstalled,
        isInstallingModel,
        modelInstallProgress,
        isScanning,
        scanPhase,
        detectedFaces,
        mediaIndexInfo,
        lastScanError,
        isLoadingIndexInfo
    ) {
        when {
            isScanning -> ViewerFaceUiState(
                state = ViewerFaceState.IN_PROGRESS,
                progressPhase = scanPhase ?: "Scanning photo...",
                engineName = factory.displayName,
                engineId = activeEngineId
            )
            isInstallingModel -> ViewerFaceUiState(
                state = ViewerFaceState.IN_PROGRESS,
                progressPhase = modelInstallProgress ?: "Installing model...",
                engineName = factory.displayName,
                engineId = activeEngineId
            )
            !isModelInstalled -> ViewerFaceUiState(
                state = ViewerFaceState.MODEL_MISSING,
                engineName = factory.displayName,
                engineId = activeEngineId
            )
            isLoadingIndexInfo -> ViewerFaceUiState(
                state = ViewerFaceState.NOT_INDEXED,
                engineName = factory.displayName,
                engineId = activeEngineId
            )
            lastScanError != null && detectedFaces.isEmpty() -> ViewerFaceUiState(
                state = ViewerFaceState.FAILED,
                errorMessage = lastScanError,
                engineName = factory.displayName,
                engineId = activeEngineId
            )
            detectedFaces.isNotEmpty() -> ViewerFaceUiState(
                state = ViewerFaceState.COMPLETED_FACES_FOUND,
                faces = detectedFaces,
                engineName = factory.displayName,
                engineId = activeEngineId
            )
            mediaIndexInfo?.status == "failed" -> ViewerFaceUiState(
                state = ViewerFaceState.FAILED,
                errorMessage = mediaIndexInfo?.errorMessage ?: "Face scan failed for this photo",
                engineName = factory.displayName,
                engineId = activeEngineId
            )
            mediaIndexInfo?.status == "indexed" -> ViewerFaceUiState(
                state = ViewerFaceState.COMPLETED_NO_FACES,
                engineName = factory.displayName,
                engineId = activeEngineId
            )
            else -> ViewerFaceUiState(
                state = ViewerFaceState.NOT_INDEXED,
                engineName = factory.displayName,
                engineId = activeEngineId
            )
        }
    }

    val overviewListState = rememberLazyListState()
    val actionsListState = rememberLazyListState()

    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var renamingFace by remember { mutableStateOf<DetectedMediaFace?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            DetailDrawerHeader(
                item = item,
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    DetailDrawerTab.OVERVIEW -> {
                        OverviewTabContent(
                            item = item,
                            customAlbums = customAlbums,
                            customTags = customTags,
                            detectedFaces = detectedFaces,
                            metadata = detailsState.metadata,
                            onOpenMap = onOpenMap,
                            onAddToAlbum = onAddTo,
                            onAddTag = { showAddTagDialog = true },
                            onEditLocation = { showAddLocationDialog = true },
                            listState = overviewListState,
                            faceUiState = faceUiState,
                            onScanThisPhoto = { startScan(false) },
                            onCancelScan = cancelScan,
                            onRescanPhoto = { startScan(false) },
                            onRescanLowThreshold = { startScan(true) },
                            onInstallModel = installModel,
                            onRetryScan = { startScan(false) },
                            onViewErrorDetails = { showErrorDetailsDialog = true },
                            onEditTagOrPerson = {
                                if (detectedFaces.size == 1) {
                                    renamingFace = detectedFaces.first()
                                } else if (detectedFaces.size > 1) {
                                    showFaceChooserDialog = true
                                } else {
                                    showAddTagDialog = true
                                }
                            },
                            onFaceClick = { face -> renamingFace = face }
                        )
                    }
                    DetailDrawerTab.ACTIONS -> {
                        ActionsTabContent(
                            item = item,
                            customTags = customTags,
                            detectedFaces = detectedFaces,
                            metadata = detailsState.metadata,
                            detailsState = detailsState,
                            onRenameFaceClick = { renamingFace = it },
                            onRescanPhoto = { startScan(false) },
                            listState = actionsListState
                        )
                    }
                }
            }
        }
    }

    // Add Location Dialog
    if (showAddLocationDialog) {
        var locationInput by remember { mutableStateOf("") }
        var isGeocoding by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddLocationDialog = false },
            title = { Text("Add location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a place name, city, or coordinates:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = locationInput,
                        onValueChange = { locationInput = it },
                        label = { Text("Location name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = {
                            showAddLocationDialog = false
                            onOpenMap()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Select on map")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = locationInput.trim()
                        if (input.isNotEmpty()) {
                            isGeocoding = true
                            coroutineScope.launch {
                                val geocoder = runCatching {
                                    @Suppress("DEPRECATION")
                                    Geocoder(context, Locale.getDefault()).getFromLocationName(input, 1)
                                }.getOrNull()?.firstOrNull()

                                val point = if (geocoder != null) {
                                    GeoPoint(geocoder.latitude, geocoder.longitude)
                                } else {
                                    val parts = input.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                                    if (parts.size >= 2) GeoPoint(parts[0], parts[1]) else null
                                }

                                withContext(Dispatchers.IO) {
                                    PhotoMapLocationCache(context).save(item.uri, point)
                                }

                                val updated = detailsState.metadata.toMutableMap()
                                updated["location"] = input
                                if (point != null) {
                                    updated["latitude"] = point.latitude.toString()
                                    updated["longitude"] = point.longitude.toString()
                                }
                                detailsState.metadata = updated
                                isGeocoding = false
                                showAddLocationDialog = false
                            }
                        }
                    },
                    enabled = !isGeocoding && locationInput.isNotBlank()
                ) {
                    if (isGeocoding) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLocationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Tag Dialog
    if (showAddTagDialog) {
        var tagInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add tag") },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tagInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddTag(trimmed)
                            showAddTagDialog = false
                        }
                    },
                    enabled = tagInput.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename / Manage Person Dialog
    if (renamingFace != null) {
        val face = renamingFace!!
        val personId = face.personId ?: face.clusterId
        var nameInput by remember(face) {
            val initial = if (face.displayName.isBlank() || face.displayName.equals("Unknown", ignoreCase = true)) "" else face.displayName
            mutableStateOf(initial)
        }
        AlertDialog(
            onDismissRequest = { renamingFace = null },
            title = {
                Text(if (face.displayName.isBlank() || face.displayName.equals("Unknown", ignoreCase = true)) "Name this person" else "Person details")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Person name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = {
                            faceStore.removeFaceFromPerson(face.mediaUri, face.faceIndex, engineId = face.engineId)
                            faceReloadKey++
                            renamingFace = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove face from this person")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = nameInput.trim()
                        if (cleanName.isNotEmpty()) {
                            faceStore.renamePerson(personId, cleanName)
                            faceReloadKey++
                            renamingFace = null
                        }
                    },
                    enabled = nameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingFace = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showErrorDetailsDialog) {
        val err = lastScanError ?: mediaIndexInfo?.errorMessage ?: "Unknown error"
        FaceErrorDetailsDialog(
            errorMessage = err,
            engineName = factory.displayName,
            onDismiss = { showErrorDetailsDialog = false }
        )
    }

    if (showFaceChooserDialog && detectedFaces.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showFaceChooserDialog = false },
            title = { Text("Select person to name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detectedFaces.forEach { face ->
                        val isNamed = face.displayName.isNotBlank() && !face.displayName.equals("Unknown", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFaceChooserDialog = false
                                    renamingFace = face
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isNamed) face.displayName else "Face #${face.faceIndex + 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val w = (face.boxRight - face.boxLeft).toInt().coerceAtLeast(0)
                                    val h = (face.boxBottom - face.boxTop).toInt().coerceAtLeast(0)
                                    if (w > 0 && h > 0) {
                                        Text(
                                            text = "Bounds: ${w}×${h} px",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFaceChooserDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
