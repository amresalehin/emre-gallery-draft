package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag


@Composable
internal fun ViewerFaceEmptyAndErrorStates(
    faceUiState: ViewerFaceUiState,
    isBusy: Boolean,
    onRescanPhoto: () -> Unit,
    onRescanLowThreshold: () -> Unit,
    onInstallModel: () -> Unit,
    onRetryScan: () -> Unit,
    onViewErrorDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (faceUiState.state) {
        ViewerFaceState.COMPLETED_NO_FACES -> {
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
                            Icons.Outlined.Face,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No faces detected in this media item.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onRescanLowThreshold,
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("rescan_low_threshold_button")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Re-scan with Low Threshold", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onRescanPhoto,
                            enabled = !isBusy,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("rescan_photo_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Force Re-scan")
                        }
                    }
                }
            }
        }
        ViewerFaceState.MODEL_MISSING -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
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
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Face model files missing for ${faceUiState.engineName.ifBlank { "active engine" }}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Button(
                        onClick = onInstallModel,
                        enabled = !isBusy,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("install_model_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Install Model")
                    }
                }
            }
        }
        ViewerFaceState.FAILED -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
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
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = faceUiState.errorMessage ?: "Face indexing failed for this photo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onRetryScan,
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("retry_scan_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (faceUiState.groupingFailure) "Retry Grouping" else "Retry Scan")
                        }
                        OutlinedButton(
                            onClick = onViewErrorDetails,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("view_error_details_button")
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("View Error Details")
                        }
                    }
                }
            }
        }
        else -> {}
    }
}

@Composable
fun FaceErrorDetailsDialog(
    errorMessage: String,
    engineName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text("Error Details")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Engine: $engineName",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Dismiss")
            }
        }
    )
}

/**
 * Overview Tab content:
 * 1. Albums (membership chips + add control in header, minimal empty state if none)
 * 2. Tags (clean monochrome chips + add control in header, minimal empty state if none)
 * 3. People (individual large portraits, horizontal scroll, hidden if empty)
 * 4. Location (monochrome preview box + place name, preview opens map, edit control in header/card opens edit dialog, placeholder if none)
 * 5. Metadata (compact heading with downward chevron, no duplicate fields, thin dividers)
 */
