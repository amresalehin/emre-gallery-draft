package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
fun OcrIndexingCard(
    totalPhotos: Int,
    ocrState: OcrState,
    countsReady: Boolean,
    cardBackground: Color,
    cardBorder: BorderStroke,
    numberFormat: NumberFormat,
    onToggleOcr: () -> Unit,
    onStopOcr: () -> Unit,
    onReindexOcr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ocrProgress = if (totalPhotos > 0) (ocrState.processed.toFloat() / totalPhotos).coerceIn(0f, 1f) else 0f
    val ocrPercent = (ocrProgress * 100f).roundToInt()

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
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "OCR Text Indexing",
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
                                ocrState.isRunning && !ocrState.isPaused -> "Scanning"
                                ocrState.isRunning && ocrState.isPaused -> "Paused"
                                ocrState.processed >= totalPhotos && totalPhotos > 0 -> "Complete"
                                else -> "Idle"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = when {
                            ocrState.isRunning && !ocrState.isPaused -> MaterialTheme.colorScheme.primaryContainer
                            ocrState.isRunning && ocrState.isPaused -> MaterialTheme.colorScheme.secondaryContainer
                            ocrState.processed >= totalPhotos && totalPhotos > 0 -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        disabledLabelColor = when {
                            ocrState.isRunning && !ocrState.isPaused -> MaterialTheme.colorScheme.onPrimaryContainer
                            ocrState.isRunning && ocrState.isPaused -> MaterialTheme.colorScheme.onSecondaryContainer
                            ocrState.processed >= totalPhotos && totalPhotos > 0 -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ),
                    border = null,
                    modifier = Modifier.height(28.dp)
                )
            }

            // Progress
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${numberFormat.format(ocrState.processed)} of ${numberFormat.format(totalPhotos)} photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$ocrPercent%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { ocrProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IndexingMetric(
                    label = "Processing Rate",
                    value = "${ocrState.itemsPerMinute}/min",
                    modifier = Modifier.weight(1f)
                )
                IndexingMetric(
                    label = "Cached Results",
                    value = numberFormat.format(ocrState.cachedCount),
                    modifier = Modifier.weight(1f)
                )
                IndexingMetric(
                    label = "Pending",
                    value = numberFormat.format((totalPhotos - ocrState.processed).coerceAtLeast(0)),
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
                    onClick = onToggleOcr,
                    enabled = countsReady && totalPhotos > 0 && (ocrState.isRunning || ocrState.processed < totalPhotos),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (ocrState.isRunning && !ocrState.isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            ocrState.isRunning && !ocrState.isPaused -> "Pause"
                            ocrState.isRunning && ocrState.isPaused -> "Resume"
                            ocrState.processed >= totalPhotos && totalPhotos > 0 -> "Finished"
                            else -> "Start OCR"
                        }
                    )
                }
                if (ocrState.isRunning) {
                    OutlinedButton(
                        onClick = onStopOcr,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
                OutlinedButton(
                    onClick = onReindexOcr,
                    enabled = countsReady && totalPhotos > 0 && !ocrState.isRunning,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Re-index")
                }
            }
        }
    }
}
