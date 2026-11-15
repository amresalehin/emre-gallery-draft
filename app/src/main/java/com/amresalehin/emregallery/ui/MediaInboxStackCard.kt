package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaInboxStack

/**
 * Image Stack Card displaying layered offset depth cards behind a hero cover thumbnail,
 * source favicon, collection title, and item count.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageStackCard(
    stack: MediaInboxStack,
    accentIndex: Int = 0,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val layerColors = remember(accentIndex, isDark) {
        StackCardPresentation.layerColors(accentIndex, isDark)
    }
    val dateStr = remember(stack.timestampMillis) {
        StackCardPresentation.formatDate(stack.timestampMillis)
    }
    val coverImageModel = remember(stack.items) {
        StackCardPresentation.resolveCoverModel(stack)
    }

    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 8.dp, bottom = 8.dp)
            .testTag("inbox_image_stack_${stack.id}")
    ) {
        // Layer 2 (Backmost stacked card - offset 8dp bottom-right)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 8.dp, y = 8.dp)
                .background(layerColors[1], shape = cardShape)
                .border(1.dp, layerColors[1].copy(alpha = 0.85f), shape = cardShape)
        )

        // Layer 1 (Middle stacked card - offset 4dp bottom-right)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(layerColors[0], shape = cardShape)
                .border(1.dp, layerColors[0].copy(alpha = 0.9f), shape = cardShape)
        )

        // Front Main Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape = cardShape)
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = if (isDark) 1.dp else 3.dp,
            border = BorderStroke(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.45f)
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                StackCardHeroCover(
                    stackId = stack.id,
                    title = stack.title,
                    coverImageModel = coverImageModel,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode
                )
                StackCardMetadataFooter(
                    stack = stack,
                    dateStr = dateStr
                )
            }
        }
    }
}
