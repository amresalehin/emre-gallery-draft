package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.*
import java.util.*


fun SearchResultsHeader(
    queryTitle: String,
    totalCount: Int,
    photosCount: Int,
    videosCount: Int,
    sortMode: SearchSortMode,
    onSortChange: (SearchSortMode) -> Unit,
    viewMode: SearchViewMode,
    onViewModeChange: (SearchViewMode) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showViewMenu by remember { mutableStateOf(false) }

    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)
    val cardSurface = searchCardSurfaceColor(isDark)
    val barSurface = searchBarSurfaceColor(isDark)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Query Title & Real Result Count Summary
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = queryTitle.ifBlank { "All items" },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val summaryParts = mutableListOf<String>()
            if (photosCount > 0) summaryParts.add("$photosCount photos")
            if (videosCount > 0) summaryParts.add("$videosCount videos")
            val summaryText = if (summaryParts.isNotEmpty()) {
                "$totalCount items · " + summaryParts.joinToString(", ")
            } else {
                "$totalCount items"
            }

            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right Controls: Sort & View
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Sort Control
            Box {
                Surface(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showSortMenu = true }
                        .testTag("sort_menu_button"),
                    shape = RoundedCornerShape(8.dp),
                    color = barSurface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Sort",
                            modifier = Modifier.size(16.dp),
                            tint = textColor
                        )
                        Text(
                            text = sortMode.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = textColor
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = secondaryText
                        )
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(cardSurface)
                ) {
                    SearchSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode.label,
                                    color = textColor,
                                    fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSortChange(mode)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            // View Control
            Box {
                Surface(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showViewMenu = true }
                        .testTag("view_mode_button"),
                    shape = RoundedCornerShape(8.dp),
                    color = barSurface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val viewIcon = when (viewMode) {
                            SearchViewMode.MASONRY -> Icons.Default.Dashboard
                            SearchViewMode.GRID -> Icons.Default.GridView
                            SearchViewMode.LARGE_GRID -> Icons.Default.Window
                            SearchViewMode.LIST -> Icons.Default.ViewAgenda
                        }
                        Icon(
                            imageVector = viewIcon,
                            contentDescription = "View",
                            modifier = Modifier.size(16.dp),
                            tint = textColor
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = secondaryText
                        )
                    }
                }

                DropdownMenu(
                    expanded = showViewMenu,
                    onDismissRequest = { showViewMenu = false },
                    modifier = Modifier.background(cardSurface)
                ) {
                    SearchViewMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode.label,
                                    color = textColor,
                                    fontWeight = if (viewMode == mode) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onViewModeChange(mode)
                                showViewMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Live Suggestion Row (State B):
 * Height ~56-72 dp.
 * Shows thumbnail / entity icon, primary label, secondary context, and trailing northwest arrow affordance.
 */
@Composable
