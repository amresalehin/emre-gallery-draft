package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
internal fun SearchMetadataAndDynamicPills(
    filterState: SearchFilterState,
    onFilterChange: (SearchFilterState) -> Unit,
    dynamicChips: List<DynamicEntityChip>,
    onToggleDynamicChip: (DynamicEntityChip) -> Unit,
    textColor: Color,
    secondaryText: Color,
    cardSurface: Color,
    selectedSurface: Color,
    dividerCol: Color
) {
        // 5. GPS Pill (Icon only)
        val isGpsSelected = filterState.gpsFilter == GpsFilter.HAS_GPS
        SearchFilterPill(
            label = "GPS",
            isSelected = isGpsSelected,
            onToggle = {
                onFilterChange(
                    filterState.copy(
                        gpsFilter = if (isGpsSelected) GpsFilter.ALL else GpsFilter.HAS_GPS
                    )
                )
            },
            icon = Icons.Default.LocationOn,
            contentDescription = "GPS",
            showLabel = false,
            testTag = "filter_pill_gps",
            textColor = textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            selectedSurface = selectedSurface,
            dividerCol = dividerCol
        )

        // 6. NO GPS Pill (Icon only)
        val isNoGpsSelected = filterState.gpsFilter == GpsFilter.NO_GPS
        SearchFilterPill(
            label = "No GPS",
            isSelected = isNoGpsSelected,
            onToggle = {
                onFilterChange(
                    filterState.copy(
                        gpsFilter = if (isNoGpsSelected) GpsFilter.ALL else GpsFilter.NO_GPS
                    )
                )
            },
            icon = Icons.Default.LocationOff,
            contentDescription = "No GPS",
            showLabel = false,
            testTag = "filter_pill_no_gps",
            textColor = textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            selectedSurface = selectedSurface,
            dividerCol = dividerCol
        )

        // 7. WITH TEXT Pill (Icon only)
        val isTextSelected = filterState.ocrTextFilter == OcrTextFilter.HAS_TEXT
        SearchFilterPill(
            label = "With Text",
            isSelected = isTextSelected,
            onToggle = {
                onFilterChange(
                    filterState.copy(
                        ocrTextFilter = if (isTextSelected) OcrTextFilter.ALL else OcrTextFilter.HAS_TEXT
                    )
                )
            },
            icon = Icons.Default.TextFields,
            contentDescription = "With text",
            showLabel = false,
            testTag = "filter_pill_text",
            textColor = textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            selectedSurface = selectedSurface,
            dividerCol = dividerCol
        )

        // 8. WITHOUT TEXT Pill (Icon only)
        val isWithoutTextSelected = filterState.ocrTextFilter == OcrTextFilter.NO_TEXT
        SearchFilterPill(
            label = "Without Text",
            isSelected = isWithoutTextSelected,
            onToggle = {
                onFilterChange(
                    filterState.copy(
                        ocrTextFilter = if (isWithoutTextSelected) OcrTextFilter.ALL else OcrTextFilter.NO_TEXT
                    )
                )
            },
            icon = Icons.Default.FontDownloadOff,
            contentDescription = "Without text",
            showLabel = false,
            testTag = "filter_pill_without_text",
            textColor = textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            selectedSurface = selectedSurface,
            dividerCol = dividerCol
        )

        // 9. FACE Pill (Icon only)
        val isFaceSelected = filterState.faceFilter == FaceFilter.HAS_FACE
        SearchFilterPill(
            label = "Face",
            isSelected = isFaceSelected,
            onToggle = {
                onFilterChange(
                    filterState.copy(
                        faceFilter = if (isFaceSelected) FaceFilter.ALL else FaceFilter.HAS_FACE
                    )
                )
            },
            icon = Icons.Default.Face,
            contentDescription = "Face",
            showLabel = false,
            testTag = "filter_pill_face",
            textColor = textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            selectedSurface = selectedSurface,
            dividerCol = dividerCol
        )

        // 10. NO FACE Pill (Icon only)
        val isNoFaceSelected = filterState.faceFilter == FaceFilter.NO_FACE
        SearchFilterPill(
            label = "No Face",
            isSelected = isNoFaceSelected,
            onToggle = {
                onFilterChange(
                    filterState.copy(
                        faceFilter = if (isNoFaceSelected) FaceFilter.ALL else FaceFilter.NO_FACE
                    )
                )
            },
            icon = Icons.Default.PersonOff,
            contentDescription = "No face",
            showLabel = false,
            testTag = "filter_pill_no_face",
            textColor = textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            selectedSurface = selectedSurface,
            dividerCol = dividerCol
        )

        // 11. Dynamic Entity Chips (Strictly derived from matching results)
        dynamicChips.forEach { chip ->
            val isChipSelected = when (chip.kind) {
                ChipKind.ALBUM -> filterState.selectedAlbumId == chip.value
                ChipKind.PERSON -> filterState.selectedPersonId == chip.value
                ChipKind.PLACE -> filterState.selectedPlace == chip.value
                ChipKind.TAG -> filterState.selectedTag == chip.value
            }

            Surface(
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .clickable { onToggleDynamicChip(chip) }
                    .testTag("dynamic_chip_${chip.id}"),
                shape = CircleShape,
                color = if (isChipSelected) selectedSurface else cardSurface,
                border = BorderStroke(1.dp, if (isChipSelected) textColor.copy(alpha = 0.5f) else dividerCol)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val icon = when (chip.kind) {
                        ChipKind.ALBUM -> Icons.Default.PhotoAlbum
                        ChipKind.PERSON -> Icons.Default.Person
                        ChipKind.PLACE -> Icons.Default.Place
                        ChipKind.TAG -> Icons.Default.Tag
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (isChipSelected) textColor else secondaryText
                    )
                    Text(
                        text = chip.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isChipSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = textColor
                    )
                    Text(
                        text = chip.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryText
                    )
                }
            }
        }
}

