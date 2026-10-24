package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FaceClusteringCard(
    faceDetected: Int,
    clusteringState: FaceClusteringState,
    cardBackground: Color,
    cardBorder: BorderStroke,
    numberFormat: NumberFormat,
    onMinGroupFacesChange: (Int) -> Unit,
    onClusterDistanceChange: (Float) -> Unit,
    onClusterFaces: (Float) -> Unit,
    onResetClusters: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Face Clustering",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Status Badge
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            when {
                                clusteringState.isClusteringRunning -> "Clustering"
                                faceDetected == 0 -> "No Faces"
                                else -> "${clusteringState.summaries.size} Groups"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = when {
                            clusteringState.isClusteringRunning -> MaterialTheme.colorScheme.primaryContainer
                            clusteringState.summaries.isNotEmpty() -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        disabledLabelColor = when {
                            clusteringState.isClusteringRunning -> MaterialTheme.colorScheme.onPrimaryContainer
                            clusteringState.summaries.isNotEmpty() -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ),
                    border = null,
                    modifier = Modifier.height(28.dp)
                )
            }

            // Minimum group size
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Minimum Faces per Person", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Text("${clusteringState.minGroupFaces} faces", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = clusteringState.minGroupFaces.toFloat(),
                    onValueChange = { onMinGroupFacesChange(it.roundToInt().coerceIn(1, 30)) },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Groups below this size stay indexed but are hidden from People & pets.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Recognition Distance Line Drag Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Maximum Recognition Distance",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val thresholdLabel = when {
                        clusteringState.clusterDistance <= 0.45f -> "Strict"
                        clusteringState.clusterDistance <= 0.60f -> "Balanced"
                        else -> "Loose"
                    }
                    Text(
                        String.format(Locale.US, "%.2f (%s)", clusteringState.clusterDistance, thresholdLabel),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = clusteringState.clusterDistance,
                    onValueChange = onClusterDistanceChange,
                    valueRange = 0.25f..0.75f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Strict (0.25)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Loose (0.75)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Metrics Grid
            val largestGroup = remember(clusteringState.summaries) {
                clusteringState.summaries.maxOfOrNull { it.faceCount } ?: 0
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IndexingMetric(
                    label = "People Groups",
                    value = numberFormat.format(clusteringState.summaries.size),
                    modifier = Modifier.weight(1f)
                )
                IndexingMetric(
                    label = "Clustered Faces",
                    value = numberFormat.format(faceDetected),
                    modifier = Modifier.weight(1f)
                )
                IndexingMetric(
                    label = "Largest Group",
                    value = "$largestGroup faces",
                    modifier = Modifier.weight(1f)
                )
            }

            // Feedback status text
            if (clusteringState.statusMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            clusteringState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (faceDetected == 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Tip: Run Face Finding on your photos first so face embeddings are available for clustering.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onClusterFaces(clusteringState.clusterDistance) },
                    enabled = !clusteringState.isClusteringRunning && faceDetected > 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
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
                        Text(if (clusteringState.summaries.isEmpty()) "Cluster Faces" else "Re-cluster Faces")
                    }
                }
                if (clusteringState.summaries.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onResetClusters,
                        enabled = !clusteringState.isClusteringRunning,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }
        }
    }
}
