package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeViewOptionsBottomSheet(
    sortOrder: String,
    groupBy: String,
    gridColumns: Int,
    gridStyle: String,
    onSortOrderChange: (String) -> Unit,
    onGroupByChange: (String) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onGridStyleChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempGridStyle by remember(gridStyle) { mutableStateOf(gridStyle) }
    var tempGridColumns by remember(gridColumns) { mutableIntStateOf(gridColumns.coerceIn(1, 6)) }

    val initialField = when {
        sortOrder.startsWith("date_added") -> "date_added"
        sortOrder.startsWith("name") -> "name"
        sortOrder.startsWith("size") -> "size"
        else -> "date_taken"
    }
    val initialDirection = if (sortOrder.endsWith("_asc")) "asc" else "desc"

    var tempSortField by remember(sortOrder) { mutableStateOf(initialField) }
    var tempSortDirection by remember(sortOrder) { mutableStateOf(initialDirection) }
    var tempGroupBy by remember(groupBy) { mutableStateOf(groupBy.ifBlank { "none" }) }

    val applySelections = {
        onGridStyleChange(tempGridStyle)
        onGridColumnsChange(tempGridColumns)
        val mappedSortOrder = when (tempSortField) {
            "date_taken" -> if (tempSortDirection == "asc") "date_taken_asc" else "date_taken_desc"
            "date_added" -> if (tempSortDirection == "asc") "date_added_asc" else "date_added_desc"
            "name" -> if (tempSortDirection == "asc") "name_asc" else "name_desc"
            "size" -> if (tempSortDirection == "asc") "size_asc" else "size_desc"
            else -> "date_taken_desc"
        }
        onSortOrderChange(mappedSortOrder)
        onGroupByChange(tempGroupBy)
        onDismiss()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = applySelections,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. View section
            Text(
                text = "View",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Grid Button
                val isGridSelected = tempGridStyle == "square"
                Surface(
                    onClick = { tempGridStyle = "square" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isGridSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = if (isGridSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .testTag("view_option_grid")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Grid",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        if (isGridSelected) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Masonry Button
                val isMasonrySelected = tempGridStyle == "masonry"
                Surface(
                    onClick = { tempGridStyle = "masonry" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isMasonrySelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = if (isMasonrySelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .testTag("view_option_masonry")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Dashboard,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Masonry",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        if (isMasonrySelected) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 2. Columns section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Columns",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(16.dp))
                Icon(
                    Icons.Default.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = tempGridColumns.toFloat(),
                    onValueChange = { tempGridColumns = it.roundToInt() },
                    valueRange = 1f..6f,
                    steps = 4,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("view_columns_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        activeTickColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Icon(
                    Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$tempGridColumns",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }


            HomeViewSortGroupResetSections(
                tempSortField = tempSortField,
                tempSortDirection = tempSortDirection,
                tempGroupBy = tempGroupBy,
                onSortChange = { f, d ->
                    tempSortField = f
                    tempSortDirection = d
                },
                onGroupByChange = { tempGroupBy = it },
                onResetDefaults = {
                    tempGridStyle = "square"
                    tempGridColumns = 3
                    tempSortField = "date_taken"
                    tempSortDirection = "desc"
                    tempGroupBy = "none"
                }
            )
        }
    }
}
