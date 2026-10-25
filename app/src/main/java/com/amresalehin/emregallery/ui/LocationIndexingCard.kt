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
import kotlin.math.roundToInt

@Composable
fun LocationIndexingCard(
    totalPhotos: Int,
    locationState: LocationIndexingState,
    countsReady: Boolean,
    cardBackground: Color,
    cardBorder: BorderStroke,
    numberFormat: NumberFormat,
    onToggleLocation: () -> Unit,
    onStopLocation: () -> Unit,
    onReindexLocation: () -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalPhotos > 0) (locationState.processed.toFloat() / totalPhotos).coerceIn(0f, 1f) else 0f
    val percent = (progress * 100f).roundToInt()

    var showRescanConfirm by remember { mutableStateOf(false) }

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
            // Header: Icon, Location Title, Start/Pause Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Map & Location Indexing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (locationState.isRunning) {
                            if (locationState.isPaused) "Indexing paused" else "Scanning GPS metadata…"
                        } else if (locationState.processed >= totalPhotos && totalPhotos > 0) {
                            "All photos scanned"
                        } else {
                            "EXIF GPS & geotag catalog"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (locationState.isRunning) {
                        IconButton(onClick = onStopLocation) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop location indexing")
                        }
                    }
                    FilledTonalIconButton(
                        onClick = onToggleLocation,
                        enabled = countsReady && totalPhotos > 0
                    ) {
                        Icon(
                            if (locationState.isRunning) {
                                if (locationState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause
                            } else Icons.Default.PlayArrow,
                            contentDescription = if (locationState.isRunning) "Pause indexing" else "Start location indexing"
                        )
                    }
                }
            }

            // Progress bar and percentage
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${numberFormat.format(locationState.processed)} of ${numberFormat.format(totalPhotos)} photos checked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            // Metrics row: Located count, Remaining, Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IndexingMetric(
                    label = "Geotagged",
                    value = numberFormat.format(locationState.locatedCount),
                    modifier = Modifier.weight(1f)
                )
                IndexingMetric(
                    label = "Remaining",
                    value = numberFormat.format((totalPhotos - locationState.processed).coerceAtLeast(0)),
                    modifier = Modifier.weight(1f)
                )
                IndexingMetric(
                    label = "Scan Rate",
                    value = if (locationState.isRunning && !locationState.isPaused && locationState.itemsPerMinute > 0) {
                        "${locationState.itemsPerMinute}/min"
                    } else "Idle",
                    modifier = Modifier.weight(1f)
                )
            }

            // Bottom action row: Reindex All & Open Map button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showRescanConfirm = true },
                    enabled = countsReady && !locationState.isRunning
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Rescan all")
                }

                Button(
                    onClick = onOpenMap,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("View Map", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showRescanConfirm) {
        AlertDialog(
            onDismissRequest = { showRescanConfirm = false },
            title = { Text("Rescan All Locations?") },
            text = { Text("This will clear the cached photo locations and rescan every photo using unredacted EXIF GPS metadata.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRescanConfirm = false
                        onReindexLocation()
                    }
                ) {
                    Text("Rescan All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRescanConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
