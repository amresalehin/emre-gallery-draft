package com.amresalehin.emregallery.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.face.StageState
import java.text.NumberFormat
import kotlin.math.roundToInt


@Composable
internal fun FaceRecognitionCornerActionBox(
    selectedOption: String,
    selectedEngineId: String,
    isDownloading: Boolean,
    downloadPercent: Int,
    isModelInstalled: Boolean,
    isCompleted: Boolean,
    isClusteringActive: Boolean,
    isScanningRunning: Boolean,
    isScanningPaused: Boolean,
    scanPercent: Int,
    processedCount: Int,
    showActionMenu: Boolean,
    onShowActionMenuChange: (Boolean) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDownloadModel: (Boolean, String) -> Unit,
    onToggleFaceFinding: (String) -> Unit,
    onRescanRequested: (String) -> Unit,
    onClusterFaces: (Float, String) -> Unit,
    onResetClusters: (String) -> Unit,
    onComparePipelines: (() -> Unit)?
) {
    val context = LocalContext.current
                Box {
                    Surface(
                        onClick = {
                            when {
                                isDownloading -> onCancelDownload(selectedEngineId)
                                !isModelInstalled && selectedOption != "disable" -> onDownloadModel(true, selectedEngineId)
                                isCompleted -> onShowActionMenuChange(true)
                                isClusteringActive -> { /* clustering active */ }
                                isScanningRunning || isScanningPaused -> onToggleFaceFinding(selectedEngineId)
                                selectedOption != "disable" -> onToggleFaceFinding(selectedEngineId)
                            }
                        },
                        enabled = selectedOption != "disable",
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isDownloading -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            isScanningRunning || isClusteringActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            isScanningPaused -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            !isModelInstalled && selectedOption != "disable" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            isCompleted -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isDownloading -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                isScanningRunning || isClusteringActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                isScanningPaused -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                !isModelInstalled && selectedOption != "disable" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .defaultMinSize(minWidth = 84.dp)
                            .testTag("face_corner_box_action")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            when {
                                selectedOption == "disable" -> {
                                    Text(
                                        text = "Disabled",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    )
                                }
                                isDownloading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "$downloadPercent%",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                }
                                !isModelInstalled -> {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "Download",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                isClusteringActive -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Clustering…",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                isScanningRunning -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "$scanPercent% · Pause",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                isScanningPaused -> {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "$scanPercent% · Resume",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                }
                                isCompleted -> {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "100% · Done",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                                else -> {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = if (processedCount > 0) "Start ($scanPercent%)" else "Start",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Dropdown menu for additional actions (Rescan, Recluster, Compare)
                    DropdownMenu(
                        expanded = showActionMenu,
                        onDismissRequest = { onShowActionMenuChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rescan All Photos") },
                            onClick = {
                                onShowActionMenuChange(false)
                                onRescanRequested(selectedEngineId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Re-cluster Faces") },
                            onClick = {
                                onShowActionMenuChange(false)
                                onClusterFaces(FaceRecognitionSettings.recognitionDistance(context, selectedEngineId), selectedEngineId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reset Clusters") },
                            onClick = {
                                onShowActionMenuChange(false)
                                onResetClusters(selectedEngineId)
                            }
                        )
                        if (onComparePipelines != null) {
                            DropdownMenuItem(
                                text = { Text("Compare Pipelines") },
                                onClick = {
                                    onShowActionMenuChange(false)
                                    onComparePipelines()
                                }
                            )
                        }
                    }
                }
}
