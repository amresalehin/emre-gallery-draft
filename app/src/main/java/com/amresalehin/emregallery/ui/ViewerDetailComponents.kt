package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.DetectedMediaFace


enum class DetailDrawerTab {
    OVERVIEW, ACTIONS
}

enum class ViewerFaceState {
    NOT_INDEXED,
    IN_PROGRESS,
    WAITING_FOR_GROUPING,
    GROUPING_INCOMPLETE,
    COMPLETED_FACES_FOUND,
    COMPLETED_NO_FACES,
    MODEL_MISSING,
    FAILED
}

data class ViewerFaceUiState(
    val state: ViewerFaceState = ViewerFaceState.NOT_INDEXED,
    val faces: List<DetectedMediaFace> = emptyList(),
    val progressPhase: String? = null,
    val errorMessage: String? = null,
    val engineName: String = "",
    val engineId: String = "",
    val groupingFailure: Boolean = false
)

@Composable
fun ViewerButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.94f), CircleShape)
    ) {
        Icon(icon, label, tint = scheme.onSurface)
    }
}

@Composable
fun ViewerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val actionColor = tint ?: scheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .defaultMinSize(minWidth = 54.dp, minHeight = 48.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = actionColor)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = actionColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 0.dp,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

/**
 * Top header displaying:
 * - Line 1: Filename
 * - Line 2: Capture date/time and file size (e.g., 25 Oct 2026 · 4:32 PM · 3.8 MB)
 * - Tabs: Overview and Actions in a subtle rounded pill container with underline on selected tab.
 * No Edit button in header.
 */
@Composable
fun DetailDrawerHeader(
    item: MediaItem,
    selectedTab: DetailDrawerTab,
    onSelectTab: (DetailDrawerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatMediaSubtitle(item),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(14.dp))

        // Subtle rounded container for Overview & Actions tabs
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                val isOverview = selectedTab == DetailDrawerTab.OVERVIEW
                val isActions = selectedTab == DetailDrawerTab.ACTIONS

                // Overview Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelectTab(DetailDrawerTab.OVERVIEW) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Overview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isOverview) FontWeight.Bold else FontWeight.Medium,
                            color = if (isOverview) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                        if (isOverview) {
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(84.dp)
                                    .height(2.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface,
                                        RoundedCornerShape(1.dp)
                                    )
                            )
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                // Actions Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelectTab(DetailDrawerTab.ACTIONS) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isActions) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActions) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                        if (isActions) {
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(84.dp)
                                    .height(2.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface,
                                        RoundedCornerShape(1.dp)
                                    )
                            )
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

