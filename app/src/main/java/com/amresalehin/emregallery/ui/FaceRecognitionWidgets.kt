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
internal fun FolderChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF161A20) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF161A20) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .height(38.dp)
            .defaultMinSize(minWidth = 72.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun FaceSegmentOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161A20),
            modifier = modifier.fillMaxHeight()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
internal fun FaceRecognitionIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFF181C22)) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.6.dp.toPx()
            val cornerLength = 4.5.dp.toPx()
            val w = size.width
            val h = size.height

            // Top-left
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(cornerLength, 0f), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, cornerLength), strokeWidth)

            // Top-right
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w - cornerLength, 0f), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w, cornerLength), strokeWidth)

            // Bottom-left
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(cornerLength, h), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(0f, h - cornerLength), strokeWidth)

            // Bottom-right
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, h), androidx.compose.ui.geometry.Offset(w - cornerLength, h), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, h), androidx.compose.ui.geometry.Offset(w, h - cornerLength), strokeWidth)
        }
        Icon(
            imageVector = Icons.Default.Face,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint
        )
    }
}


@Composable
internal fun FaceRecognitionHeaderRow(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
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
    currentFindingState: FaceFindingState,
    showActionMenu: Boolean,
    onShowActionMenuChange: (Boolean) -> Unit,
    onStopFaceFinding: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDownloadModel: (Boolean, String) -> Unit,
    onToggleFaceFinding: (String) -> Unit,
    onRescanRequested: (String) -> Unit,
    onClusterFaces: (Float, String) -> Unit,
    onResetClusters: (String) -> Unit,
    onComparePipelines: (() -> Unit)?
) {
            // Header Row: Face Icon, Title, Dynamic Corner Box, Expand Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clickable Left area (Icon + Title) to toggle expansion
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleExpand() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular Halo with FaceRecognitionIcon
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            FaceRecognitionIcon(tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Text(
                        text = "Face Recognition",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                // Stop action button when actively scanning
                if (currentFindingState.isRunning) {
                    IconButton(
                        onClick = { onStopFaceFinding(selectedEngineId) },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("face_stop_button")
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop Face Recognition",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

                // Dynamic Corner Box
                FaceRecognitionCornerActionBox(
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
                    processedCount = currentFindingState.processed,
                    showActionMenu = showActionMenu,
                    onShowActionMenuChange = { showActionMenu = it },
                    onCancelDownload = onCancelDownload,
                    onDownloadModel = onDownloadModel,
                    onToggleFaceFinding = onToggleFaceFinding,
                    onRescanRequested = onRescanRequested,
                    onClusterFaces = onClusterFaces,
                    onResetClusters = onResetClusters,
                    onComparePipelines = onComparePipelines
                )

                Spacer(Modifier.width(6.dp))

                IconButton(
                    onClick = { onToggleExpand() },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("face_expand_toggle")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse Face Recognition" else "Expand Face Recognition",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

}
