package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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


fun SearchAdvancedFilterSheet(
    filterState: SearchFilterState,
    onApplyFilter: (SearchFilterState) -> Unit,
    onDismiss: () -> Unit,
    onRequestCustomDatePicker: () -> Unit,
    isDark: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftFilter by remember { mutableStateOf(filterState) }

    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)
    val bg = searchBgColor(isDark)
    val cardSurface = searchCardSurfaceColor(isDark)
    val barSurface = searchBarSurfaceColor(isDark)
    val selectedSurface = searchSelectedSurfaceColor(isDark)
    val dividerCol = searchDividerColor(isDark)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cardSurface,
        scrimColor = Color.Black.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Search Filters",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
                TextButton(
                    onClick = {
                        draftFilter = SearchFilterState()
                    },
                    modifier = Modifier.testTag("filter_sheet_reset_button")
                ) {
                    Text("Reset all", color = secondaryText)
                }
            }

            HorizontalDivider(color = dividerCol)

            // Media Type Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Media Type",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MediaTypeFilter.entries.forEach { type ->
                        val selected = draftFilter.mediaType == type
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { draftFilter = draftFilter.copy(mediaType = type) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) selectedSurface else barSurface,
                            border = BorderStroke(1.dp, if (selected) textColor.copy(alpha = 0.4f) else Color.Transparent)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = type.label,
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

            // GPS Location Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "GPS Location",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GpsFilter.entries.forEach { gpsOpt ->
                        val selected = draftFilter.gpsFilter == gpsOpt
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { draftFilter = draftFilter.copy(gpsFilter = gpsOpt) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) selectedSurface else barSurface,
                            border = BorderStroke(1.dp, if (selected) textColor.copy(alpha = 0.4f) else Color.Transparent)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = gpsOpt.label,
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

            // OCR Text Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "OCR Text",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OcrTextFilter.entries.forEach { ocrOpt ->
                        val selected = draftFilter.ocrTextFilter == ocrOpt
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { draftFilter = draftFilter.copy(ocrTextFilter = ocrOpt) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) selectedSurface else barSurface,
                            border = BorderStroke(1.dp, if (selected) textColor.copy(alpha = 0.4f) else Color.Transparent)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ocrOpt.label,
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

            SearchFilterSheetBottomSection(
                draftFilter = draftFilter,
                onDraftFilterChange = { draftFilter = it },
                onApplyFilter = onApplyFilter,
                onDismiss = onDismiss,
                onRequestCustomDatePicker = onRequestCustomDatePicker,
                textColor = textColor,
                secondaryText = secondaryText,
                barSurface = barSurface,
                selectedSurface = selectedSurface,
                dividerCol = dividerCol,
                isDark = isDark
            )
        }
    }
}
