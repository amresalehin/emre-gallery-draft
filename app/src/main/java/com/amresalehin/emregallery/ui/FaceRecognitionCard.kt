package com.amresalehin.emregallery.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.face.StageState
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
fun FaceRecognitionCard(
    totalPhotos: Int,
    faceFindingState: FaceFindingState,
    engineState: EngineState,
    modelDownloadState: ModelDownloadState,
    clusteringState: FaceClusteringState,
    enteFaceFindingState: FaceFindingState = FaceFindingState(),
    enteModelDownloadState: ModelDownloadState = ModelDownloadState(),
    enteClusteringState: FaceClusteringState = FaceClusteringState(),
    countsReady: Boolean,
    cardBackground: Color,
    cardBorder: BorderStroke,
    numberFormat: NumberFormat,
    onConfidenceChange: (Float, String) -> Unit,
    onMinGroupFacesChange: (Int, String) -> Unit,
    onClusterDistanceChange: (Float, String) -> Unit,
    onToggleFaceFinding: (String) -> Unit,
    onStopFaceFinding: (String) -> Unit,
    onRescanRequested: (String) -> Unit,
    onDownloadModel: (force: Boolean, engineId: String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onClusterFaces: (Float, String) -> Unit,
    onResetClusters: (String) -> Unit,
    onComparePipelines: (() -> Unit)? = null,
    onRetryFailedFaces: ((String) -> Unit)? = null,
    availableFolders: List<FaceFolderInfo> = emptyList(),
    onToggleFolder: (String) -> Unit = {},
    onSelectAllFolders: () -> Unit = {},
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) {
        if (initiallyExpanded) {
            isExpanded = true
        }
    }
    val context = LocalContext.current
    val activeEngineInSettings = FaceRecognitionSettings.getActiveEngine(context)
    val isFaceRecognitionEnabled = FaceRecognitionSettings.preferences(context)
        .getBoolean("face_recognition_enabled", true) && activeEngineInSettings != "disabled"

    var selectedEngineId by remember {
        mutableStateOf(
            when {
                activeEngineInSettings == EnteFaceConstants.ENGINE_ID -> EnteFaceConstants.ENGINE_ID
                activeEngineInSettings == BuffaloLFaceEngine.ENGINE_ID -> BuffaloLFaceEngine.ENGINE_ID
                engineState.isBuffaloLInstalled -> BuffaloLFaceEngine.ENGINE_ID
                engineState.isEnteInstalled -> EnteFaceConstants.ENGINE_ID
                else -> BuffaloLFaceEngine.ENGINE_ID
            }
        )
    }

    val selectedOption = when {
        !isFaceRecognitionEnabled -> "disable"
        selectedEngineId == EnteFaceConstants.ENGINE_ID -> "yolo"
        else -> "buffalo"
    }

    val isEnte = selectedEngineId == EnteFaceConstants.ENGINE_ID
    val currentFindingState = if (isEnte) enteFaceFindingState else faceFindingState
    val currentDownloadState = if (isEnte) enteModelDownloadState else modelDownloadState
    val currentClusteringState = if (isEnte) enteClusteringState else clusteringState

    val isModelInstalled = when (selectedEngineId) {
        EnteFaceConstants.ENGINE_ID -> engineState.isEnteInstalled
        BuffaloLFaceEngine.ENGINE_ID -> engineState.isBuffaloLInstalled
        else -> false
    }

    val isDownloading = currentDownloadState.isDownloading
    val isClusteringActive = currentClusteringState.isClusteringRunning || currentFindingState.clusteringStage.state == StageState.RUNNING
    val isScanningRunning = currentFindingState.isRunning && !currentFindingState.isPaused && !isClusteringActive
    val isScanningPaused = currentFindingState.isRunning && currentFindingState.isPaused

    val downloadPercent = if (currentDownloadState.totalBytes > 0) {
        ((currentDownloadState.bytesReceived.toFloat() / currentDownloadState.totalBytes) * 100f).roundToInt().coerceIn(0, 100)
    } else if (currentDownloadState.bytesReceived > 0) {
        val estimatedTotal = if (isEnte) 25L * 1024 * 1024 else 288L * 1024 * 1024
        ((currentDownloadState.bytesReceived.toFloat() / estimatedTotal) * 100f).roundToInt().coerceIn(0, 99)
    } else 0

    val effectiveTotalPhotos = if (currentFindingState.scopedTotalPhotos > 0 || !currentFindingState.isAllFolders) {
        currentFindingState.scopedTotalPhotos
    } else totalPhotos

    val scanProgress = if (effectiveTotalPhotos > 0) (currentFindingState.processed.toFloat() / effectiveTotalPhotos).coerceIn(0f, 1f) else 0f
    val scanPercent = (scanProgress * 100f).roundToInt()
    val animatedScanProgress by animateFloatAsState(targetValue = scanProgress, label = "face_scan_progress")

    val isCompleted = isModelInstalled && !currentFindingState.isRunning && !isClusteringActive && effectiveTotalPhotos > 0 && currentFindingState.processed >= effectiveTotalPhotos

    var showActionMenu by remember { mutableStateOf(false) }

    fun selectOption(option: String) {
        when (option) {
            "disable" -> {
                if (currentFindingState.isRunning) {
                    onStopFaceFinding(selectedEngineId)
                }
                if (currentDownloadState.isDownloading) {
                    onCancelDownload(selectedEngineId)
                }
                FaceRecognitionSettings.preferences(context).edit()
                    .putBoolean("face_recognition_enabled", false)
                    .putString(FaceRecognitionSettings.KEY_ACTIVE_ENGINE, "disabled")
                    .apply()
            }
            "yolo" -> {
                if (currentDownloadState.isDownloading && selectedEngineId != EnteFaceConstants.ENGINE_ID) {
                    onCancelDownload(selectedEngineId)
                }
                selectedEngineId = EnteFaceConstants.ENGINE_ID
                FaceRecognitionSettings.preferences(context).edit()
                    .putBoolean("face_recognition_enabled", true)
                    .apply()
                FaceRecognitionSettings.setActiveEngine(context, EnteFaceConstants.ENGINE_ID)
            }
            "buffalo" -> {
                if (currentDownloadState.isDownloading && selectedEngineId != BuffaloLFaceEngine.ENGINE_ID) {
                    onCancelDownload(selectedEngineId)
                }
                selectedEngineId = BuffaloLFaceEngine.ENGINE_ID
                FaceRecognitionSettings.preferences(context).edit()
                    .putBoolean("face_recognition_enabled", true)
                    .apply()
                FaceRecognitionSettings.setActiveEngine(context, BuffaloLFaceEngine.ENGINE_ID)
            }
        }
    }

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("face_recognition_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FaceRecognitionHeaderRow(
                isExpanded = isExpanded,
                onToggleExpand = { isExpanded = !isExpanded },
                selectedOption = selectedOption,
                selectedEngineId = selectedEngineId,
                isDownloading = isDownloading,
                downloadPercent = downloadPercent,
                isModelInstalled = isModelInstalled,
                isCompleted = isCompleted,
                isClusteringActive = isClusteringActive,
                isScanningRunning = isScanningRunning,
                isScanningPaused = isScanningPaused,
                scanPercent = scanPercent,
                currentFindingState = currentFindingState,
                showActionMenu = showActionMenu,
                onShowActionMenuChange = { showActionMenu = it },
                onStopFaceFinding = onStopFaceFinding,
                onCancelDownload = onCancelDownload,
                onDownloadModel = onDownloadModel,
                onToggleFaceFinding = onToggleFaceFinding,
                onRescanRequested = onRescanRequested,
                onClusterFaces = onClusterFaces,
                onResetClusters = onResetClusters,
                onComparePipelines = onComparePipelines
            )
            // Minimalist Divider with integrated Progress Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                if (isExpanded) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { (downloadPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = Color.Transparent
                    )
                } else if (scanProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { animatedScanProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }

            if (isExpanded) {
                FaceRecognitionExpandedContent(
                    selectedOption = selectedOption,
                    onSelectOption = { selectOption(it) },
                    currentFindingState = currentFindingState,
                    availableFolders = availableFolders,
                    onSelectAllFolders = onSelectAllFolders,
                    onToggleFolder = onToggleFolder
                )
            }
        }
    }
}
