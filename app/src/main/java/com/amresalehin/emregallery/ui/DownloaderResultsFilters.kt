package com.amresalehin.emregallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DownloaderCategoryToolbar(
    categories: List<Triple<String, ImageVector, Int>>,
    selectedFilter: String?,
    onSelectFilter: (String?) -> Unit,
    sortOption: String,
    sortDropdownExpanded: Boolean,
    onSortDropdownExpandedChange: (Boolean) -> Unit,
    onSelectSortOption: (String) -> Unit,
    hasActiveDimensionFilter: Boolean,
    onToggleDimensionFilter: () -> Unit,
    isAllSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(categories) { (categoryName, icon, count) ->
                val isSelected = selectedFilter == categoryName
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFF212529) else Color(0xFFE9ECEF),
                    modifier = Modifier.clickable {
                        onSelectFilter(if (isSelected) null else categoryName)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = categoryName,
                            tint = if (isSelected) Color.White else Color(0xFF212529),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "$count",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (isSelected) Color.White else Color(0xFF212529)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(Color(0xFFDEE2E6))
        )

        Spacer(Modifier.width(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE9ECEF),
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { onSortDropdownExpandedChange(true) }
                        .testTag("downloader_sort_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Sort",
                            tint = if (sortOption != "Default") Color(0xFF212529) else Color(0xFF495057),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = sortDropdownExpanded,
                    onDismissRequest = { onSortDropdownExpandedChange(false) }
                ) {
                    listOf("Default", "Title (A-Z)", "Videos First", "Images First", "Streams First").forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt, fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onSelectSortOption(opt)
                                onSortDropdownExpandedChange(false)
                            }
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = if (hasActiveDimensionFilter) Color(0xFF212529) else Color(0xFFE9ECEF),
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onToggleDimensionFilter)
                    .testTag("downloader_filter_tune_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filter dimensions & size",
                        tint = if (hasActiveDimensionFilter) Color.White else Color(0xFF495057),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = Color(0xFFE9ECEF),
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onToggleSelectAll)
                    .testTag("downloader_select_all_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isAllSelected) Icons.Default.Check else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = "Select All",
                        tint = Color(0xFF495057),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DownloaderDimensionFilterDrawer(
    visible: Boolean,
    minWidthFilter: Float,
    minHeightFilter: Float,
    showSvg: Boolean,
    onMinWidthChange: (Float) -> Unit,
    onMinHeightChange: (Float) -> Unit,
    onShowSvgChange: (Boolean) -> Unit,
    onResetFilters: () -> Unit
) {
    AnimatedVisibility(visible = visible) {
        Surface(
            color = Color(0xFFF8F9FA),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dimension & Size Filter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212529)
                    )
                    TextButton(onClick = onResetFilters) {
                        Text("Reset", color = Color(0xFF212529))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Min Width: ${if (minWidthFilter <= 0f) "Any" else "${minWidthFilter.toInt()}px"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF495057)
                    )
                }
                Slider(
                    value = minWidthFilter,
                    onValueChange = onMinWidthChange,
                    valueRange = 0f..3840f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Min Height: ${if (minHeightFilter <= 0f) "Any" else "${minHeightFilter.toInt()}px"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF495057)
                    )
                }
                Slider(
                    value = minHeightFilter,
                    onValueChange = onMinHeightChange,
                    valueRange = 0f..2160f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Show SVG images", style = MaterialTheme.typography.bodySmall, color = Color(0xFF495057))
                    Switch(checked = showSvg, onCheckedChange = onShowSvgChange)
                }
            }
        }
    }
}
