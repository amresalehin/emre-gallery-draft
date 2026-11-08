package com.amresalehin.emregallery.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
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
import com.amresalehin.emregallery.data.CloudAiProvider
import com.amresalehin.emregallery.data.FlorenceModelState
import com.amresalehin.emregallery.data.FlorenceStatus
import com.amresalehin.emregallery.data.PreferencesRepository
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
fun AiTaggingCard(
    totalPhotos: Int,
    taggingState: AiTaggingState,
    prefs: PreferencesRepository,
    countsReady: Boolean,
    cardBackground: Color,
    cardBorder: BorderStroke,
    numberFormat: NumberFormat,
    onStartTagging: (rescanAll: Boolean) -> Unit,
    onStopTagging: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    onShowSnackbar: ((String) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) {
        if (initiallyExpanded) {
            isExpanded = true
        }
    }

    val context = LocalContext.current
    val activeProvider by prefs.activeCloudProvider.collectAsState(initial = null)
    val florenceState by prefs.florenceModelState.collectAsState(initial = FlorenceModelState())

    val isRunning = taggingState.isRunning && !taggingState.isPaused
    val isPaused = taggingState.isRunning && taggingState.isPaused

    val scanProgress = if (totalPhotos > 0) (taggingState.processed.toFloat() / totalPhotos).coerceIn(0f, 1f) else 0f
    val scanPercent = (scanProgress * 100f).roundToInt()
    val animatedProgress by animateFloatAsState(targetValue = scanProgress, label = "ai_tagging_progress")
    val isCompleted = totalPhotos > 0 && taggingState.processed >= totalPhotos && !taggingState.isRunning

    val activeEngineLabel = remember(activeProvider, florenceState.status) {
        when {
            activeProvider != null -> activeProvider?.name ?: "Cloud AI"
            florenceState.status == FlorenceStatus.READY -> "Florence-2 Base"
            else -> "Smart Vision"
        }
    }

    var showActionMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("ai_tagging_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: AI Tagging Icon, Title, Dynamic Corner Box, Expand Toggle
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
                        .clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular Halo with AiTaggingIcon matching Face card
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AiTaggingIcon(tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "AI Image Tagging",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = activeEngineLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // Stop action button when actively scanning
                if (taggingState.isRunning) {
                    IconButton(
                        onClick = onStopTagging,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("tagging_stop_button")
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop AI Tagging",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

                // Dynamic Corner Box matching Face card design
                Box {
                    Surface(
                        onClick = {
                            when {
                                isRunning -> onStopTagging()
                                isCompleted -> showActionMenu = true
                                else -> onStartTagging(false)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isRunning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            isPaused -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            isCompleted -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isRunning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                isPaused -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .defaultMinSize(minWidth = 84.dp)
                            .testTag("tagging_corner_box_action")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            when {
                                isRunning -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "$scanPercent%",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                isCompleted -> {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Done",
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
                                        text = if (taggingState.processed > 0) "Start ($scanPercent%)" else "Start",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Dropdown menu for additional actions (Re-tag All, Tag Untagged)
                    DropdownMenu(
                        expanded = showActionMenu,
                        onDismissRequest = { showActionMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Scan Untagged Photos") },
                            onClick = {
                                showActionMenu = false
                                onStartTagging(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Re-tag Entire Library") },
                            onClick = {
                                showActionMenu = false
                                onStartTagging(true)
                            }
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("tagging_expand_toggle")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse AI Tagging" else "Expand AI Tagging",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Minimalist Divider with integrated Progress Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                if (isExpanded) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isRunning || scanProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Explanatory note
                    Text(
                        text = "Automatically detect themes, objects, palette and scenery in photos. Tags become instantly searchable across your gallery and filterable by #tags.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    // Active Pipeline Status Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Active Engine",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        activeProvider != null -> "Using cloud multimodal vision (${activeProvider?.name})"
                                        florenceState.status == FlorenceStatus.READY -> "Using on-device Florence-2 vision model"
                                        else -> "Using on-device smart vision heuristics (instant & offline)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = activeEngineLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Scan Progress / Current Item Live Feedback
                    if (isRunning) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Scanning photos...",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${taggingState.processed} / $totalPhotos photos",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (taggingState.currentItemName.isNotBlank()) {
                                    Text(
                                        text = taggingState.currentItemName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (taggingState.currentItemStatus.isNotBlank()) {
                                    Text(
                                        text = "Tags: ${taggingState.currentItemStatus}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Action Buttons Row: "Tag Untagged" | "Re-tag All"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onStartTagging(false)
                                onShowSnackbar?.invoke("Started AI tagging for untagged photos")
                            },
                            enabled = !isRunning,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp)
                                .testTag("tagging_start_untagged_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tag Untagged")
                        }

                        OutlinedButton(
                            onClick = {
                                onStartTagging(true)
                                onShowSnackbar?.invoke("Re-tagging all photos in background")
                            },
                            enabled = !isRunning,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp)
                                .testTag("tagging_rescan_all_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Re-tag All")
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun AiTaggingIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFF181C22)) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.6.dp.toPx()
            val cornerLength = 4.5.dp.toPx()
            val w = size.width
            val h = size.height

            // Top-left bracket
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(cornerLength, 0f), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, cornerLength), strokeWidth)

            // Top-right bracket
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w - cornerLength, 0f), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w, cornerLength), strokeWidth)

            // Bottom-left bracket
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(cornerLength, h), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(0f, h - cornerLength), strokeWidth)

            // Bottom-right bracket
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, h), androidx.compose.ui.geometry.Offset(w - cornerLength, h), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, h), androidx.compose.ui.geometry.Offset(w, h - cornerLength), strokeWidth)
        }
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint
        )
    }
}
