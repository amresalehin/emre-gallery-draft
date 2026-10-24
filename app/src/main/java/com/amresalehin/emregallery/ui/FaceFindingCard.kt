package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun FaceFindingCard(
    totalPhotos: Int,
    faceFindingState: FaceFindingState,
    engineState: EngineState,
    modelDownloadState: ModelDownloadState,
    countsReady: Boolean,
    cardBackground: Color,
    cardBorder: BorderStroke,
    numberFormat: NumberFormat,
    onConfidenceChange: (Float) -> Unit,
    onToggleFaceFinding: () -> Unit,
    onStopFaceFinding: () -> Unit,
    onRescanRequested: () -> Unit,
    onDownloadModel: (force: Boolean) -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val faceProgress = if (totalPhotos > 0) (faceFindingState.processed.toFloat() / totalPhotos).coerceIn(0f, 1f) else 0f
    val facePercent = (faceProgress * 100f).roundToInt()

    val downloadPercent = if (modelDownloadState.totalBytes > 0) {
        ((modelDownloadState.bytesReceived.toFloat() / modelDownloadState.totalBytes) * 100f).roundToInt().coerceIn(0, 100)
    } else if (modelDownloadState.bytesReceived > 0) {
        ((modelDownloadState.bytesReceived.toFloat() / (288L * 1024 * 1024)) * 100f).roundToInt().coerceIn(0, 99)
    } else 0

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Face Finding",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Status Badge
                val faceChipLabel = when {
                    modelDownloadState.isDownloading -> when (modelDownloadState.stage) {
                        "extracting" -> "Extracting"
                        "installing" -> "Installing"
                        else -> "Downloading $downloadPercent%"
                    }
                    faceFindingState.isRunning && !faceFindingState.isPaused -> "Detecting"
                    faceFindingState.isRunning && faceFindingState.isPaused -> "Paused"
                    faceFindingState.processed >= totalPhotos && totalPhotos > 0 -> "Complete"
                    modelDownloadState.error != null -> "Download Failed"
                    !engineState.isBuffaloLInstalled -> "Native Fallback"
                    engineState.primaryModelFailure != null -> "Primary Failed"
                    else -> "Buffalo_L Ready"
                }

                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            faceChipLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = when {
                            modelDownloadState.isDownloading || (faceFindingState.isRunning && !faceFindingState.isPaused) -> MaterialTheme.colorScheme.primaryContainer
                            faceFindingState.isRunning && faceFindingState.isPaused -> MaterialTheme.colorScheme.secondaryContainer
                            faceFindingState.processed >= totalPhotos && totalPhotos > 0 -> MaterialTheme.colorScheme.tertiaryContainer
                            modelDownloadState.error != null || (engineState.primaryModelFailure != null && engineState.isBuffaloLInstalled) -> MaterialTheme.colorScheme.errorContainer
                            !engineState.isBuffaloLInstalled -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        disabledLabelColor = when {
                            modelDownloadState.isDownloading || (faceFindingState.isRunning && !faceFindingState.isPaused) -> MaterialTheme.colorScheme.onPrimaryContainer
                            faceFindingState.isRunning && faceFindingState.isPaused -> MaterialTheme.colorScheme.onSecondaryContainer
                            faceFindingState.processed >= totalPhotos && totalPhotos > 0 -> MaterialTheme.colorScheme.onTertiaryContainer
                            modelDownloadState.error != null || (engineState.primaryModelFailure != null && engineState.isBuffaloLInstalled) -> MaterialTheme.colorScheme.onErrorContainer
                            !engineState.isBuffaloLInstalled -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    ),
                    border = null,
                    modifier = Modifier.height(28.dp)
                )
            }

            // Detection confidence control
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Face Detection Confidence", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Text(
                        String.format(Locale.US, "%.2f", faceFindingState.detectionConfidence),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = faceFindingState.detectionConfidence,
                    onValueChange = onConfidenceChange,
                    valueRange = 0.30f..0.90f,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !faceFindingState.isRunning
                )
                Text(
                    "Higher values reject weaker detections. Changes apply to new scans; use Re-scan to reprocess indexed photos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Active Engine Status & Model Download
            val engineBannerShape = RoundedCornerShape(14.dp)
            if (modelDownloadState.isDownloading) {
                Surface(
                    shape = engineBannerShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                when (modelDownloadState.stage) {
                                    "extracting" -> "Extracting Buffalo_L Model…"
                                    "installing" -> "Installing Buffalo_L Model…"
                                    else -> "Downloading Buffalo_L Model ($downloadPercent%)"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = onCancelDownload,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        val isTransferStage = modelDownloadState.stage == "downloading"
                        val progressFloat = if (modelDownloadState.totalBytes > 0) {
                            (modelDownloadState.bytesReceived.toFloat() / modelDownloadState.totalBytes).coerceIn(0f, 0.99f)
                        } else 0f
                        if (isTransferStage && modelDownloadState.totalBytes > 0) {
                            LinearProgressIndicator(
                                progress = { progressFloat },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isTransferStage) Text(
                            text = "${formatBytes(modelDownloadState.bytesReceived)} of ${if (modelDownloadState.totalBytes > 0) formatBytes(modelDownloadState.totalBytes) else "unknown size"} downloaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (!engineState.isBuffaloLInstalled) {
                Surface(
                    shape = engineBannerShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Active Engine: Android Native FaceDetector (Fallback)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Primary Buffalo_L model (288 MB) is not downloaded. Using built-in 128-d face detection. Download Buffalo_L for deep face recognition.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (modelDownloadState.error != null) {
                            Text(
                                "Download failed: ${modelDownloadState.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = { onDownloadModel(true) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download Buffalo_L (288 MB)")
                        }
                    }
                }
            } else if (engineState.primaryModelFailure != null) {
                Surface(
                    shape = engineBannerShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Primary Engine Failed: Buffalo_L",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "Reason: ${engineState.primaryModelFailure}\nActive engine: Android Native FaceDetector (Fallback)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onDownloadModel(true) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Re-download Model")
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = engineBannerShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Active Engine: Buffalo_L (InsightFace ONNX)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "High-accuracy SCRFD detector + 512-dim ArcFace embeddings active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Progress
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${numberFormat.format(faceFindingState.processed)} of ${numberFormat.format(totalPhotos)} photos scanned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$facePercent%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                LinearProgressIndicator(
                    progress = { faceProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IndexingMetric(
                    label = "Detected Faces",
                    value = numberFormat.format(faceFindingState.totalDetected),
                    modifier = Modifier.weight(1f)
                )
                IndexingMetric(
                    label = "Detection Speed",
                    value = "${faceFindingState.itemsPerMinute}/min",
                    modifier = Modifier.weight(1f)
                )
                IndexingMetric(
                    label = "Unreadable",
                    value = numberFormat.format(faceFindingState.failedCount),
                    modifier = Modifier.weight(1f)
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onToggleFaceFinding,
                    enabled = countsReady && totalPhotos > 0 && !modelDownloadState.isDownloading && (faceFindingState.isRunning || faceFindingState.processed < totalPhotos),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (faceFindingState.isRunning && !faceFindingState.isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            faceFindingState.isRunning && !faceFindingState.isPaused -> "Pause"
                            faceFindingState.isRunning && faceFindingState.isPaused -> "Resume"
                            faceFindingState.processed >= totalPhotos && totalPhotos > 0 -> "Finished"
                            else -> "Find Faces"
                        }
                    )
                }
                if (faceFindingState.isRunning) {
                    OutlinedButton(
                        onClick = onStopFaceFinding,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
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
            }
        }
    }
}
