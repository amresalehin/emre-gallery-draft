package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.face.DetectedMediaFace
import com.amresalehin.emregallery.face.FaceProcessingCoordinator
import androidx.compose.ui.platform.testTag


@Composable
fun ViewerFaceContainer(
    faceUiState: ViewerFaceUiState,
    onScanThisPhoto: () -> Unit,
    onCancelScan: () -> Unit,
    onRescanPhoto: () -> Unit,
    onRescanLowThreshold: () -> Unit,
    onInstallModel: () -> Unit,
    onRetryScan: () -> Unit,
    onViewErrorDetails: () -> Unit,
    onEditTagOrPerson: () -> Unit,
    onFaceClick: ((DetectedMediaFace) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isProcessingActive by FaceProcessingCoordinator.isProcessingActive.collectAsStateWithLifecycle()
    val isBusy = isProcessingActive || FaceProcessingCoordinator.isBusy(context)

    when (faceUiState.state) {
        ViewerFaceState.NOT_INDEXED -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Face indexing has not been run for this item.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onScanThisPhoto,
                        enabled = !isBusy,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("scan_this_photo_button")
                    ) {
                        Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan This Photo")
                    }
                }
            }
        }
        ViewerFaceState.IN_PROGRESS -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = faceUiState.progressPhase ?: "Scanning photo...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    OutlinedButton(
                        onClick = onCancelScan,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("cancel_scan_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel Scan")
                    }
                }
            }
        }
        ViewerFaceState.WAITING_FOR_GROUPING -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = faceUiState.progressPhase ?: "Faces found · waiting to be grouped",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        ViewerFaceState.GROUPING_INCOMPLETE -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = modifier.fillMaxWidth()
            ) {
                Text(
                    text = faceUiState.progressPhase ?: "Faces found, but no person group is available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        ViewerFaceState.COMPLETED_FACES_FOUND -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    faceUiState.faces.forEach { face ->
                        OverviewFacePortrait(
                            face = face,
                            enabled = !isBusy,
                            onClick = if (!isBusy) { { onFaceClick?.invoke(face) } } else null
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onRescanPhoto,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("rescan_photo_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Re-scan Photo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onEditTagOrPerson,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("edit_tag_add_person_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Tag / Add Person", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        else -> {
            ViewerFaceEmptyAndErrorStates(
                faceUiState = faceUiState,
                isBusy = isBusy,
                onRescanPhoto = onRescanPhoto,
                onRescanLowThreshold = onRescanLowThreshold,
                onInstallModel = onInstallModel,
                onRetryScan = onRetryScan,
                onViewErrorDetails = onViewErrorDetails,
                modifier = modifier
            )
        }
    }
}
