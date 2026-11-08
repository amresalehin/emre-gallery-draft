package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.*
import java.util.*


@Composable
internal fun ColumnScope.SearchFilterSheetBottomSection(
    draftFilter: SearchFilterState,
    onDraftFilterChange: (SearchFilterState) -> Unit,
    onApplyFilter: (SearchFilterState) -> Unit,
    onDismiss: () -> Unit,
    onRequestCustomDatePicker: () -> Unit,
    textColor: Color,
    secondaryText: Color,
    barSurface: Color,
    selectedSurface: Color,
    dividerCol: Color,
    isDark: Boolean
) {
            // Face Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Faces",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FaceFilter.entries.forEach { faceOpt ->
                        val selected = draftFilter.faceFilter == faceOpt
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onDraftFilterChange(draftFilter.copy(faceFilter = faceOpt)) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) selectedSurface else barSurface,
                            border = BorderStroke(1.dp, if (selected) textColor.copy(alpha = 0.4f) else Color.Transparent)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = faceOpt.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }

            // Time Range Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Date / Time Range",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TimeRangeFilter.AllTime to "All time",
                        TimeRangeFilter.Last30Days to "30 days",
                        TimeRangeFilter.Last90Days to "90 days"
                    ).forEach { (range, label) ->
                        val selected = when (range) {
                            is TimeRangeFilter.AllTime -> draftFilter.timeRange is TimeRangeFilter.AllTime
                            is TimeRangeFilter.Last30Days -> draftFilter.timeRange is TimeRangeFilter.Last30Days
                            is TimeRangeFilter.Last90Days -> draftFilter.timeRange is TimeRangeFilter.Last90Days
                            else -> false
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onDraftFilterChange(draftFilter.copy(timeRange = range)) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) selectedSurface else barSurface,
                            border = BorderStroke(1.dp, if (selected) textColor.copy(alpha = 0.4f) else Color.Transparent)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = textColor
                                )
                            }
                        }
                    }
                }

                // Custom date option
                val isCustomDate = draftFilter.timeRange is TimeRangeFilter.Custom
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onRequestCustomDatePicker() },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCustomDate) selectedSurface else barSurface,
                    border = BorderStroke(1.dp, if (isCustomDate) textColor.copy(alpha = 0.4f) else Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp), tint = secondaryText)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isCustomDate) "Custom date selected" else "Choose custom date range…",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isCustomDate) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = textColor
                        )
                    }
                }
            }

            // Favorites Only Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Favorites only",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = textColor
                    )
                    Text(
                        text = "Show only starred/favorite photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText
                    )
                }
                Switch(
                    checked = draftFilter.favoritesOnly,
                    onCheckedChange = { onDraftFilterChange(draftFilter.copy(favoritesOnly = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = textColor,
                        checkedTrackColor = selectedSurface,
                        uncheckedThumbColor = secondaryText,
                        uncheckedTrackColor = barSurface
                    )
                )
            }

            // Apply Button
            Button(
                onClick = {
                    onApplyFilter(draftFilter)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("filter_sheet_apply_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = textColor,
                    contentColor = if (isDark) SearchColors.DarkBg else Color.White
                )
            ) {
                Text(
                    text = "Apply Filters",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
}
