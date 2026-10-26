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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    countsReady: Boolean,
    cardBackground: Color,
    cardBorder: BorderStroke,
    numberFormat: NumberFormat,
    onConfidenceChange: (Float) -> Unit,
    onMinGroupFacesChange: (Int) -> Unit,
    onClusterDistanceChange: (Float) -> Unit,
    onToggleFaceFinding: () -> Unit,
    onStopFaceFinding: () -> Unit,
    onRescanRequested: () -> Unit,
    onDownloadModel: (force: Boolean) -> Unit,
    onCancelDownload: () -> Unit,
    onClusterFaces: (Float) -> Unit,
    onResetClusters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val faceProgress = if (totalPhotos > 0) (faceFindingState.processed.toFloat() / totalPhotos).coerceIn(0f, 1f) else 0f
    val facePercent = (faceProgress * 100f).roundToInt()

    val downloadPercent = if (modelDownloadState.totalBytes > 0) {
        ((modelDownloadState.bytesReceived.toFloat() / modelDownloadState.totalBytes) * 100f).roundToInt().coerceIn(0, 100)
    } else if (modelDownloadState.bytesReceived > 0) {
        ((modelDownloadState.bytesReceived.toFloat() / (288L * 1024 * 1024)) * 100f).roundToInt().coerceIn(0, 99)
    } else 0

    var modelMenuExpanded by remember { mutableStateOf(false) }

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
                Text(
                    "Face Recognition",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Action Start / Pause / Resume button
                Button(
                    onClick = onToggleFaceFinding,
                    enabled = countsReady && totalPhotos > 0 && !modelDownloadState.isDownloading && (faceFindingState.isRunning || faceFindingState.processed < totalPhotos),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        when {
                            faceFindingState.isRunning && !faceFindingState.isPaused -> Icons.Default.Pause
                            faceFindingState.processed >= totalPhotos && totalPhotos > 0 -> Icons.Default.Check
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            faceFindingState.isRunning && !faceFindingState.isPaused -> "Pause"
                            faceFindingState.isRunning && faceFindingState.isPaused -> "Resume"
                            faceFindingState.processed >= totalPhotos && totalPhotos > 0 -> "Complete"
                            else -> "Start"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (faceFindingState.isRunning) {
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = onStopFaceFinding,
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

            // Progress stats & linear progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${numberFormat.format(faceFindingState.processed)} / ${numberFormat.format(totalPhotos)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        value = faceFindingState.detectionConfidence,
                        onValueChange = onConfidenceChange,
                        valueRange = 0.30f..0.90f,
                        enabled = !faceFindingState.isRunning,
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    String.format(Locale.US, "%.2f", faceFindingState.detectionConfidence),
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
                        value = clusteringState.minGroupFaces.toFloat(),
                        onValueChange = { onMinGroupFacesChange(it.roundToInt().coerceIn(1, 30)) },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${clusteringState.minGroupFaces}",
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
                        "Clustering distance",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = clusteringState.clusterDistance,
                        onValueChange = onClusterDistanceChange,
                        valueRange = 0.25f..0.75f,
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    String.format(Locale.US, "%.2f", clusteringState.clusterDistance),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(36.dp)
                )
            }

            // Row 4: Model Selector
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
                    "Model",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

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
                                    modelDownloadState.isDownloading -> "Downloading $downloadPercent%"
                                    engineState.isBuffaloLInstalled -> "Buffalo_L"
                                    else -> "Download Buffalo_L"
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
                        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Buffalo_L (InsightFace)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (engineState.isBuffaloLInstalled) "Installed on device (512-d ArcFace)" else "Download model (288 MB)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingIcon = {
                                if (modelDownloadState.isDownloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else if (engineState.isBuffaloLInstalled) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            },
                            onClick = {
                                modelMenuExpanded = false
                                if (!engineState.isBuffaloLInstalled && !modelDownloadState.isDownloading) {
                                    onDownloadModel(true)
                                }
                            }
                        )
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
                    onClick = { onClusterFaces(clusteringState.clusterDistance) },
                    enabled = !clusteringState.isClusteringRunning && faceFindingState.totalDetected > 0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (clusteringState.isClusteringRunning) {
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
                            if (clusteringState.summaries.isEmpty()) "Cluster Faces"
                            else "Cluster (${clusteringState.summaries.size})"
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRescanRequested,
                    enabled = countsReady && totalPhotos > 0 && !faceFindingState.isRunning,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Re-scan")
                }

                if (clusteringState.summaries.isNotEmpty()) {
                    IconButton(
                        onClick = onResetClusters,
                        enabled = !clusteringState.isClusteringRunning,
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
        }
    }
}
