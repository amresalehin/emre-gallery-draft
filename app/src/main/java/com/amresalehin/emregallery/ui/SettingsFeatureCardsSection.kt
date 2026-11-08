package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


internal fun androidx.compose.foundation.lazy.LazyListScope.settingsFeatureCardsItems(
    query: String,
    matchesFace: Boolean,
    matchesAi: Boolean,
    matchesTheme: Boolean,
    matchesOcr: Boolean,
    matchedDestinations: List<Destination>,
    indexingState: IndexingStatusUiState,
    indexingViewModel: IndexingStatusViewModel,
    availableFolders: List<FaceFolderInfo>,
    activeTargetEngineId: String,
    onActiveTargetEngineIdChange: (String) -> Unit,
    onShowModelRequiredDialog: () -> Unit,
    onNavigate: (Destination) -> Unit,
    onRunPipelineComparison: () -> Unit
) {
            // 3. FACE RECOGNITION CARD (Moved out of indexing status page and placed below theme card)
            if (matchesFace) {
                item(key = "face_recognition_card") {
                    FaceRecognitionCard(
                        totalPhotos = uiState.totalPhotos,
                        faceFindingState = uiState.faceFinding,
                        engineState = uiState.engine,
                        modelDownloadState = uiState.modelDownload,
                        clusteringState = uiState.clustering,
                        enteFaceFindingState = uiState.enteFaceFinding,
                        enteModelDownloadState = uiState.enteModelDownload,
                        enteClusteringState = uiState.enteClustering,
                        countsReady = uiState.countsReady,
                        cardBackground = cardBackground,
                        cardBorder = cardBorder,
                        numberFormat = numberFormat,
                        onConfidenceChange = indexingViewModel::updateDetectionConfidence,
                        onMinGroupFacesChange = indexingViewModel::updateMinGroupFaces,
                        onClusterDistanceChange = indexingViewModel::updateClusterDistance,
                        onToggleFaceFinding = { eng ->
                            activeTargetEngineId = eng
                            indexingViewModel.toggleFaceFinding(
                                onNeedsModelDownload = { onShowModelRequiredDialog() },
                                engineId = eng
                            )
                        },
                        onStopFaceFinding = { eng -> indexingViewModel.stopFaceFinding(eng) },
                        onRescanRequested = { eng ->
                            activeTargetEngineId = eng
                            showFaceScanOptions = true
                        },
                        onDownloadModel = { force, eng ->
                            activeTargetEngineId = eng
                            indexingViewModel.startModelDownload(force, eng)
                        },
                        onCancelDownload = { eng -> indexingViewModel.cancelModelDownload(eng) },
                        onClusterFaces = { threshold, eng -> indexingViewModel.runFaceClustering(threshold, eng) },
                        onResetClusters = { eng -> indexingViewModel.resetFaceClusters(eng) },
                        onRetryFailedFaces = { eng -> indexingViewModel.retryFailedFaces(eng) },
                        availableFolders = availableFolders,
                        onToggleFolder = { folderPath ->
                            val isAll = uiState.faceFinding.isAllFolders
                            val currentSelected = uiState.faceFinding.selectedFolders
                            if (isAll) {
                                indexingViewModel.setFaceIndexingFolders(allFolders = false, folders = setOf(folderPath))
                            } else {
                                val newSelected = if (folderPath in currentSelected) {
                                    currentSelected - folderPath
                                } else {
                                    currentSelected + folderPath
                                }
                                if (newSelected.isEmpty() || newSelected.size >= availableFolders.size) {
                                    indexingViewModel.setFaceIndexingFolders(allFolders = true, folders = emptySet())
                                } else {
                                    indexingViewModel.setFaceIndexingFolders(allFolders = false, folders = newSelected)
                                }
                            }
                        },
                        onSelectAllFolders = {
                            indexingViewModel.setFaceIndexingFolders(allFolders = true, folders = emptySet())
                        },
                        onComparePipelines = {
                            scope.launch(Dispatchers.IO) {
                                val commonUri = faceStore.findCommonIndexedMediaUri(BuffaloLFaceEngine.ENGINE_ID, com.amresalehin.emregallery.face.EnteFaceConstants.ENGINE_ID)
                                    ?: media.firstOrNull { !it.isVideo }?.uri
                                if (commonUri != null) {
                                    val result = faceStore.compareEnginesOnMedia(mediaUri = commonUri, context = context)
                                    withContext(Dispatchers.Main) {
                                        comparisonResult = result
                                        showComparisonDialog = true
                                    }
                                }
                            }
                        },
                        initiallyExpanded = query.isNotBlank() && matchesFace
                    )
                }
            }

            // 4. AI CONFIGURATION CARD
            if (matchesAi) {
                item(key = "ai_configuration_card") {
                    AiConfigurationCard(
                        prefs = actualPrefs,
                        onShowSnackbar = onShowSnackbar,
                        initiallyExpanded = query.isNotBlank() && matchesAi
                    )
                }
            }

            // 5. OTHER SETTINGS TILES ("About")
            items(otherEntries, key = { it.first }) { (label, icon) ->
                AestheticSettingsTile(
                    label = label,
                    icon = icon,
                    isDark = isDark,
                    onClick = {
                        onSettings(label)
                    }
                )
            }

            // Empty search state
            if (!matchesTheme && !matchesOcr && !matchesFace && !matchesAi && otherEntries.isEmpty() && query.isNotBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "No settings found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "No settings match \"$query\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
}

@Composable
internal fun AestheticSettingsTile(
    label: String,
    icon: ImageVector,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val cardBackground = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        Color(0x75F5F7FA)
    }

    val cardBorder = BorderStroke(
        1.dp,
        if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        else Color.White.copy(alpha = 0.65f)
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = cardBackground,
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_tile_${label.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                val subtitle = when (label) {
                    "About" -> "Emre Gallery · 0.1.0"
                    else -> ""
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
