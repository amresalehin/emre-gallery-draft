package com.amresalehin.emregallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.amresalehin.emregallery.face.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewerDetailsModalSheet(
    item: MediaItem,
    selectedTab: DetailDrawerTab,
    onSelectTab: (DetailDrawerTab) -> Unit,
    customAlbums: List<CustomAlbum>,
    customTags: Set<String>,
    detectedFaces: List<DetectedMediaFace>,
    detailsState: ViewerDetailsState,
    faceStore: FaceClusterStore,
    faceUiState: ViewerFaceUiState,
    selectedDrawerEngineId: String,
    onSelectedDrawerEngineIdChange: (String) -> Unit,
    overviewListState: androidx.compose.foundation.lazy.LazyListState,
    actionsListState: androidx.compose.foundation.lazy.LazyListState,
    sheetState: androidx.compose.material3.SheetState,
    isProcessingActive: Boolean,
    lastScanError: String?,
    mediaIndexInfo: FaceClusterStore.MediaIndexedInfo?,
    engineDisplayName: String,
    showErrorDetailsDialog: Boolean,
    onShowErrorDetailsChange: (Boolean) -> Unit,
    showFaceChooserDialog: Boolean,
    onShowFaceChooserChange: (Boolean) -> Unit,
    onStartScan: (Boolean) -> Unit,
    onCancelScan: () -> Unit,
    onInstallModel: () -> Unit,
    onShowAddLocation: () -> Unit,
    onShowAddTag: () -> Unit,
    onRenamingFaceChange: (DetectedMediaFace?) -> Unit,
    onOpenMap: () -> Unit,
    onAddTo: () -> Unit,
    onClose: () -> Unit,
    onOpenPerson: ((String, String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
                onSelectTab = { onSelectTab(it) }
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
                            onAddTag = { onShowAddTag() },
                            onEditLocation = { onShowAddLocation() },
                            listState = overviewListState,
                            faceUiState = faceUiState,
                            selectedEngineId = selectedDrawerEngineId,
                            onSelectEngine = { newEngine ->
                                if (!FaceProcessingCoordinator.isBusy(context)) {
                                    val switched = FaceRecognitionSettings.setActiveEngine(context, newEngine)
                                    if (switched) {
                                        onSelectedDrawerEngineIdChange(newEngine)
                                        FaceEngineRegistry.setActiveEngine(newEngine)
                                        FaceStoreEvents.notifyAllUpdated()
                                    }
                                }
                            },
                            onScanThisPhoto = { onStartScan(false) },
                            onCancelScan = onCancelScan,
                            onRescanPhoto = { onStartScan(false) },
                            onRescanLowThreshold = { onStartScan(true) },
                            onInstallModel = onInstallModel,
                            onRetryScan = { onStartScan(false) },
                            onViewErrorDetails = { onShowErrorDetailsChange(true) },
                            onEditTagOrPerson = {
                                if (!FaceProcessingCoordinator.isBusy(context)) {
                                    if (detectedFaces.size == 1) {
                                        val f = detectedFaces.first()
                                        coroutineScope.launch {
                                            if (FaceProcessingCoordinator.isBusy(context)) return@launch
                                            val resolvedPersonId = if (!f.personId.isNullOrBlank()) {
                                                f.personId
                                            } else {
                                                withContext(Dispatchers.IO) {
                                                    FaceProcessingCoordinator.runIfIdle {
                                                        faceStore.ensurePersonForFace(
                                                            mediaUri = f.mediaUri,
                                                            faceIndex = f.faceIndex,
                                                            engineId = f.engineId.ifBlank { selectedDrawerEngineId }
                                                        )
                                                    }
                                                }
                                            }
                                            if (!resolvedPersonId.isNullOrBlank() && !FaceProcessingCoordinator.isBusy(context)) {
                                                onClose()
                                                onOpenPerson?.invoke(resolvedPersonId, f.engineId.ifBlank { selectedDrawerEngineId })
                                            }
                                        }
                                    } else if (detectedFaces.size > 1) {
                                        if (!FaceProcessingCoordinator.isBusy(context)) {
                                            onShowFaceChooserChange(true)
                                        }
                                    } else {
                                        onShowAddTag()
                                    }
                                }
                            },
                            onFaceClick = { face ->
                                if (!FaceProcessingCoordinator.isBusy(context)) {
                                    coroutineScope.launch {
                                        if (FaceProcessingCoordinator.isBusy(context)) return@launch
                                        val resolvedPersonId = if (!face.personId.isNullOrBlank()) {
                                            face.personId
                                        } else {
                                            withContext(Dispatchers.IO) {
                                                FaceProcessingCoordinator.runIfIdle {
                                                    faceStore.ensurePersonForFace(
                                                        mediaUri = face.mediaUri,
                                                        faceIndex = face.faceIndex,
                                                        engineId = face.engineId.ifBlank { selectedDrawerEngineId }
                                                    )
                                                }
                                            }
                                        }
                                        if (!resolvedPersonId.isNullOrBlank() && !FaceProcessingCoordinator.isBusy(context)) {
                                            onClose()
                                            onOpenPerson?.invoke(resolvedPersonId, face.engineId.ifBlank { selectedDrawerEngineId })
                                        }
                                    }
                                }
                            }
                        )
                    }
                    DetailDrawerTab.ACTIONS -> {
                        ActionsTabContent(
                            item = item,
                            customTags = customTags,
                            detectedFaces = detectedFaces,
                            metadata = detailsState.metadata,
                            detailsState = detailsState,
                            onRenameFaceClick = { onRenamingFaceChange(it) },
                            onRescanPhoto = { onStartScan(false) },
                            listState = actionsListState
                        )
                    }
                }
            }
        }
    }

    if (showErrorDetailsDialog) {
        val err = lastScanError ?: mediaIndexInfo?.errorMessage ?: "Unknown error"
        FaceErrorDetailsDialog(
            errorMessage = err,
            engineName = engineDisplayName,
            onDismiss = { onShowErrorDetailsChange(false) }
        )
    }

    if (showFaceChooserDialog && detectedFaces.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { onShowFaceChooserChange(false) },
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
                                .clickable(enabled = !isProcessingActive) {
                                    if (FaceProcessingCoordinator.isBusy(context)) return@clickable
                                    onShowFaceChooserChange(false)
                                    coroutineScope.launch {
                                        if (FaceProcessingCoordinator.isBusy(context)) return@launch
                                        val resolvedPersonId = if (!face.personId.isNullOrBlank()) {
                                            face.personId
                                        } else {
                                            withContext(Dispatchers.IO) {
                                                FaceProcessingCoordinator.runIfIdle {
                                                    faceStore.ensurePersonForFace(
                                                        mediaUri = face.mediaUri,
                                                        faceIndex = face.faceIndex,
                                                        engineId = face.engineId.ifBlank { selectedDrawerEngineId }
                                                    )
                                                }
                                            }
                                        }
                                        if (!resolvedPersonId.isNullOrBlank() && !FaceProcessingCoordinator.isBusy(context)) {
                                            onClose()
                                            onOpenPerson?.invoke(resolvedPersonId, face.engineId.ifBlank { selectedDrawerEngineId })
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    FaceCropThumbnail(
                                        mediaUri = face.portraitMediaUri ?: face.mediaUri,
                                        faceIndex = face.portraitFaceIndex ?: face.faceIndex,
                                        engineId = face.engineId,
                                        contentDescription = if (isNamed) face.displayName else "Person",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isNamed) face.displayName else "Name this person",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onShowFaceChooserChange(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}
