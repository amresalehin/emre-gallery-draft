package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.face.EngineComparisonResult
import com.amresalehin.emregallery.face.FaceRecognitionSettings


@Composable
internal fun BoxScope.SettingsFloatingSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMenuClick: () -> Unit
) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMenuClick, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Open navigation menu",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search settings",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    "Search settings…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
}

@Composable
internal fun SettingsActionDialogs(
    showFaceScanOptions: Boolean,
    onDismissFaceScanOptions: () -> Unit,
    showModelRequiredDialog: Boolean,
    onShowModelRequiredChange: (Boolean) -> Unit,
    activeTargetEngineId: String,
    indexingViewModel: IndexingStatusViewModel,
    showComparisonDialog: Boolean,
    onDismissComparisonDialog: () -> Unit,
    comparisonResult: com.amresalehin.emregallery.face.EngineComparisonResult?
) {
    // Dialogs
    if (showFaceScanOptions) {
        FaceScanOptionsDialog(
            onDismiss = { onDismissFaceScanOptions() },
            onRescanAll = {
                onDismissFaceScanOptions()
                indexingViewModel.clearAndRescanFaces(
                    onNeedsModelDownload = { onShowModelRequiredChange(true) },
                    engineId = activeTargetEngineId
                )
            },
            onRemainingOnly = {
                onDismissFaceScanOptions()
                indexingViewModel.toggleFaceFinding(
                    onNeedsModelDownload = { onShowModelRequiredChange(true) },
                    engineId = activeTargetEngineId
                )
            }
        )
    }

    if (showModelRequiredDialog) {
        ModelDownloadRequiredDialog(
            onDismiss = { onShowModelRequiredChange(false) },
            onDownloadModel = {
                onShowModelRequiredChange(false)
                indexingViewModel.startModelDownload(engineId = activeTargetEngineId)
            }
        )
    }

    val currentComparisonResult = comparisonResult
    if (showComparisonDialog && currentComparisonResult != null) {
        val comp = currentComparisonResult
        AlertDialog(
            onDismissRequest = { onDismissComparisonDialog() },
            title = {
                Text(
                    "Pipeline Comparison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Sample: ${comp.mediaUri.substringAfterLast('/')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Buffalo_L (InsightFace SCRFD-10GF)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Detected faces: ${comp.facesA.size}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Ente (YOLO5Face-small)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Detected faces: ${comp.facesB.size}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        "Spatial Overlap (IoU >= ${FaceRecognitionSettings.COMPARISON_IOU_THRESHOLD}): ${comp.matchingFaceCount} face(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onDismissComparisonDialog() }) {
                    Text("Done")
                }
            }
        )
    }
}
