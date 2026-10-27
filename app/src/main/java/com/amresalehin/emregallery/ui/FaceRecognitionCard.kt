package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import java.text.NumberFormat
import java.util.Locale
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedEngineId by remember {
        mutableStateOf(FaceRecognitionSettings.getActiveEngine(context))
    }
    val isEnte = selectedEngineId == EnteFaceConstants.ENGINE_ID

    val currentFindingState = if (isEnte) enteFaceFindingState else faceFindingState
    val currentDownloadState = if (isEnte) enteModelDownloadState else modelDownloadState
    val currentClusteringState = if (isEnte) enteClusteringState else clusteringState
    val isModelInstalled = if (isEnte) engineState.isEnteInstalled else engineState.isBuffaloLInstalled

    val faceProgress = if (totalPhotos > 0) (currentFindingState.processed.toFloat() / totalPhotos).coerceIn(0f, 1f) else 0f
    val facePercent = (faceProgress * 100f).roundToInt()

    val downloadPercent = if (currentDownloadState.totalBytes > 0) {
        ((currentDownloadState.bytesReceived.toFloat() / currentDownloadState.totalBytes) * 100f).roundToInt().coerceIn(0, 100)
    } else if (currentDownloadState.bytesReceived > 0) {
        val estimatedTotal = if (isEnte) 25L * 1024 * 1024 else 288L * 1024 * 1024
        ((currentDownloadState.bytesReceived.toFloat() / estimatedTotal) * 100f).roundToInt().coerceIn(0, 99)
    } else 0

    var modelMenuExpanded by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    // Dangerous reset confirmation dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    "Reset Clusters?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will delete all person profiles, custom names, and manual groupings for ${if (isEnte) "Ente" else "Buffalo_L"}. Stored facial embeddings are preserved so you can re-cluster at any time.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetClusters(selectedEngineId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Icon, Face Recognition Title, Start/Pause Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Face Recognition",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (isEnte) "Ente (YOLO5Face + MobileFaceNet)" else "Buffalo_L (InsightFace ArcFace)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Action Start / Pause / Resume button
                Button(
                    onClick = { onToggleFaceFinding(selectedEngineId) },
                    enabled = countsReady && totalPhotos > 0 && !currentDownloadState.isDownloading &&
                            (currentFindingState.isRunning || currentFindingState.processed < totalPhotos),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        when {
                            currentFindingState.isRunning && !currentFindingState.isPaused -> Icons.Default.Pause
                            currentFindingState.processed >= totalPhotos && totalPhotos > 0 -> Icons.Default.Check
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            currentFindingState.isRunning && !currentFindingState.isPaused -> "Pause"
                            currentFindingState.isRunning && currentFindingState.isPaused -> "Resume"
                            currentFindingState.processed >= totalPhotos && totalPhotos > 0 -> "Complete"
                            else -> "Start"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (currentFindingState.isRunning) {
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = { onStopFaceFinding(selectedEngineId) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop face scanning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Engine Selection Switcher Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isEnte,
                    onClick = {
                        selectedEngineId = BuffaloLFaceEngine.ENGINE_ID
                        FaceRecognitionSettings.setActiveEngine(context, BuffaloLFaceEngine.ENGINE_ID)
                    },
                    label = { Text("Buffalo_L (Default)") },
                    leadingIcon = if (!isEnte) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = isEnte,
                    onClick = {
                        selectedEngineId = EnteFaceConstants.ENGINE_ID
                        FaceRecognitionSettings.setActiveEngine(context, EnteFaceConstants.ENGINE_ID)
                    },
                    label = { Text("Ente (YOLO5Face)") },
                    leadingIcon = if (isEnte) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            // Progress stats & linear progress bar
            val successfulCount = (currentFindingState.processed - currentFindingState.failedCount).coerceAtLeast(0)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${numberFormat.format(successfulCount)} / ${numberFormat.format(totalPhotos)} indexed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (currentFindingState.failedCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "(${numberFormat.format(currentFindingState.failedCount)} failed)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Text(
                        "$facePercent%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { faceProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Model failure / download error banner with retry option
            val modelFailure = if (isEnte) engineState.enteModelFailure else engineState.primaryModelFailure
            val downloadError = currentDownloadState.error
            val displayError = downloadError ?: (if (!isModelInstalled) modelFailure else null)

            if (displayError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = displayError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (!currentDownloadState.isDownloading) {
                            TextButton(
                                onClick = { onDownloadModel(true, selectedEngineId) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            // Slider 1: Detection confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.TrackChanges,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Detection confidence",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = currentFindingState.detectionConfidence,
                        onValueChange = { onConfidenceChange(it, selectedEngineId) },
                        valueRange = 0.30f..0.90f,
                        enabled = !currentFindingState.isRunning,
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    String.format(Locale.US, "%.2f", currentFindingState.detectionConfidence),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(36.dp)
                )
            }

            // Slider 2: Min faces per person
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Min faces per person",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = currentClusteringState.minGroupFaces.toFloat(),
                        onValueChange = { onMinGroupFacesChange(it.roundToInt().coerceIn(1, 30), selectedEngineId) },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${currentClusteringState.minGroupFaces}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(36.dp)
                )
            }

            // Slider 3: Clustering distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isEnte) "Clustering distance (Quality-adaptive)" else "Clustering distance",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = currentClusteringState.clusterDistance,
                        onValueChange = { onClusterDistanceChange(it, selectedEngineId) },
                        valueRange = 0.25f..0.75f,
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    String.format(Locale.US, "%.2f", currentClusteringState.clusterDistance),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(36.dp)
                )
            }

            // Row 4: Model Selector, Download State & Cancel Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "Model files",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (currentDownloadState.isDownloading) {
                    FilledTonalButton(
                        onClick = { onCancelDownload(selectedEngineId) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(8.dp))
                }

                Box {
                    Surface(
                        onClick = { modelMenuExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when {
                                    currentDownloadState.isDownloading -> "Downloading $downloadPercent%"
                                    isModelInstalled -> if (isEnte) "Ente Installed (~25 MB)" else "Buffalo_L Installed"
                                    else -> if (isEnte) "Download Ente (~25 MB)" else "Download Buffalo_L (288 MB)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select model",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false },
                        modifier = Modifier.widthIn(min = 240.dp, max = 300.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        if (isEnte) "Ente YOLO5Face + MobileFaceNet" else "Buffalo_L (InsightFace)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (isModelInstalled) "Installed on device"
                                        else if (isEnte) "Download models (~25 MB)"
                                        else "Download model (288 MB)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingIcon = {
                                if (currentDownloadState.isDownloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else if (isModelInstalled) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            },
                            onClick = {
                                modelMenuExpanded = false
                                if (!isModelInstalled && !currentDownloadState.isDownloading) {
                                    onDownloadModel(true, selectedEngineId)
                                }
                            }
                        )
                        if (currentDownloadState.isDownloading) {
                            DropdownMenuItem(
                                text = { Text("Cancel Download", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    modelMenuExpanded = false
                                    onCancelDownload(selectedEngineId)
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Actions: Clustering & Rescan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onClusterFaces(currentClusteringState.clusterDistance, selectedEngineId) },
                    enabled = !currentClusteringState.isClusteringRunning && currentFindingState.totalDetected > 0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (currentClusteringState.isClusteringRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Clustering…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (currentClusteringState.summaries.isEmpty()) "Cluster Faces"
                            else "Cluster (${currentClusteringState.summaries.size})"
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onRescanRequested(selectedEngineId) },
                    enabled = countsReady && totalPhotos > 0 && !currentFindingState.isRunning,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Re-scan")
                }

                if (currentClusteringState.summaries.isNotEmpty()) {
                    IconButton(
                        onClick = { showResetConfirmDialog = true },
                        enabled = !currentClusteringState.isClusteringRunning,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Reset clusters",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Pipeline Comparison Trigger Button
            if (onComparePipelines != null) {
                OutlinedButton(
                    onClick = onComparePipelines,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Compare Buffalo_L vs Ente Pipeline")
                }
            }
        }
    }
}
