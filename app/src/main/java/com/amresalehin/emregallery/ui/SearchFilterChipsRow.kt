package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.*
import java.util.*

@Composable
fun SearchFilterChipsRow(
    filterState: SearchFilterState,
    onFilterChange: (SearchFilterState) -> Unit,
    dynamicChips: List<DynamicEntityChip>,
    onToggleDynamicChip: (DynamicEntityChip) -> Unit,
    onRequestCustomDatePicker: () -> Unit = {},
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val scrollState = rememberScrollState()

    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)
    val cardSurface = searchCardSurfaceColor(isDark)
    val selectedSurface = searchSelectedSurfaceColor(isDark)
    val dividerCol = searchDividerColor(isDark)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. VIDEOS Pill (Icon only)
        val isVideosSelected = filterState.mediaType == MediaTypeFilter.VIDEOS
        SearchFilterPill(
            label = "Videos",
            isSelected = isVideosSelected,
            onToggle = {
                onFilterChange(
                    filterState.copy(
                        mediaType = if (isVideosSelected) MediaTypeFilter.ALL else MediaTypeFilter.VIDEOS
                    )
                )
            },
            icon = Icons.Default.Videocam,
            contentDescription = "Videos",
            showLabel = false,
            testTag = "filter_pill_videos",
            textColor = textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            selectedSurface = selectedSurface,
            dividerCol = dividerCol
        )

        // 2. IMAGES Pill (Icon only)
        val isImagesSelected = filterState.mediaType == MediaTypeFilter.PHOTOS
        SearchFilterPill(
            label = "Images",
            isSelected = isImagesSelected,
            onToggle = {
                onFilterChange(
                    filterState.copy(
                        mediaType = if (isImagesSelected) MediaTypeFilter.ALL else MediaTypeFilter.PHOTOS
                    )
                )
            },
            icon = Icons.Default.Image,
            contentDescription = "Images",
            showLabel = false,
            testTag = "filter_pill_images",
            textColor = textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            selectedSurface = selectedSurface,
            dividerCol = dividerCol
        )

        // 3. GIFS Pill (Icon only)
        val isGifsSelected = filterState.mediaType == MediaTypeFilter.GIFS
        SearchFilterPill(
            label = "Gifs",
            isSelected = isGifsSelected,
            onToggle = {
                onFilterChange(
                    filterState.copy(
                        mediaType = if (isGifsSelected) MediaTypeFilter.ALL else MediaTypeFilter.GIFS
                    )
                )
            },
            icon = Icons.Default.Gif,
            contentDescription = "Gifs",
            showLabel = false,
            testTag = "filter_pill_gifs",
            textColor = textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            selectedSurface = selectedSurface,
            dividerCol = dividerCol
        )

        // 4. FAVOURITES Pill (Icon only)
        val isFavSelected = filterState.favoritesOnly
        SearchFilterPill(
            label = "Favourites",
            isSelected = isFavSelected,
            onToggle = {
                onFilterChange(
                    filterState.copy(favoritesOnly = !isFavSelected)
                )
            },
            icon = if (isFavSelected) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Favourites",
            showLabel = false,
            testTag = "filter_pill_favourites",
            textColor = if (isFavSelected) Color(0xFFE53935) else textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            selectedSurface = selectedSurface,
            dividerCol = dividerCol
        )

        SearchMetadataAndDynamicPills(
            filterState = filterState,
            onFilterChange = onFilterChange,
            dynamicChips = dynamicChips,
            onToggleDynamicChip = onToggleDynamicChip,
            textColor = textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            selectedSurface = selectedSurface,
            dividerCol = dividerCol
        )
    }
}

@Composable
fun SearchFilterPill(
    label: String = "",
    isSelected: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector? = null,
    contentDescription: String = label,
    showLabel: Boolean = true,
    testTag: String = "",
    textColor: Color,
    secondaryText: Color,
    cardSurface: Color,
    selectedSurface: Color,
    dividerCol: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(34.dp)
            .then(
                if (!showLabel) Modifier.width(38.dp) else Modifier
            )
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .testTag(testTag),
        shape = CircleShape,
        color = if (isSelected) selectedSurface else cardSurface,
        border = BorderStroke(
            width = if (isSelected) 1.2.dp else 1.dp,
            color = if (isSelected) textColor.copy(alpha = 0.55f) else dividerCol
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = if (showLabel) 12.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showLabel) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = contentDescription,
                            modifier = Modifier.size(15.dp),
                            tint = if (isSelected) textColor else secondaryText
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = textColor
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear $label filter",
                            modifier = Modifier.size(13.dp),
                            tint = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(18.dp),
                        tint = if (isSelected) textColor else secondaryText
                    )
                }
            }
        }
    }
}

/**
 * Compact Results Header (State C):
 * Left: Query title and result count / content type summary (e.g. "624 items · Photos, Videos").
 * Right: Sort control (default "Most relevant") and View control (Masonry, Grid, Large grid, List).
 */
@Composable
