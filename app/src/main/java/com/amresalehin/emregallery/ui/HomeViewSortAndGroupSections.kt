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


@Composable
internal fun ColumnScope.HomeViewSortGroupResetSections(
    tempSortField: String,
    tempSortDirection: String,
    tempGroupBy: String,
    onSortChange: (String, String) -> Unit,
    onGroupByChange: (String) -> Unit,
    onResetDefaults: () -> Unit
) {
            // 3. Sort by section
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val sortFields = listOf(
                Triple("date_taken", "Date taken", Icons.Default.CalendarMonth),
                Triple("date_added", "Date added", Icons.Default.EventAvailable),
                Triple("name", "File name", Icons.Default.SortByAlpha),
                Triple("size", "File size", Icons.Default.Description)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortFields.forEach { (field, label, icon) ->
                    val isSelected = tempSortField == field
                    Surface(
                        onClick = {
                            if (!isSelected) {
                                onSortChange(field, tempSortDirection)
                            } else {
                                onSortChange(field, if (tempSortDirection == "asc") "desc" else "asc")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(82.dp)
                            .testTag("sort_card_$field")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 7.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.10f) else Color.Transparent)
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                onSortChange(field, "asc")
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = "Ascending",
                                            modifier = Modifier.size(13.dp),
                                            tint = if (isSelected && tempSortDirection == "asc") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                onSortChange(field, "desc")
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowDownward,
                                            contentDescription = "Descending",
                                            modifier = Modifier.size(13.dp),
                                            tint = if (isSelected && tempSortDirection == "desc") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 4. Group by section
            Text(
                text = "Group by",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val groupOptions = listOf(
                Triple("none", "None", Icons.Default.Block),
                Triple("day", "Day", Icons.Default.CalendarToday),
                Triple("month", "Month", Icons.Default.CalendarMonth),
                Triple("year", "Year", Icons.Default.DateRange),
                Triple("folder", "Device folder", Icons.Default.Folder),
                Triple("initial", "Initial", Icons.Default.TextFields)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupOptions.forEach { (key, label, icon) ->
                    val isSelected = tempGroupBy == key
                    Surface(
                        onClick = { onGroupByChange(key) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        },
                        modifier = Modifier
                            .width(68.dp)
                            .height(72.dp)
                            .testTag("group_card_$key")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 5. Reset to default
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset to default
                Surface(
                    onClick = {
                        onResetDefaults()
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("reset_view_options_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Reset to default",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }


            }

}
