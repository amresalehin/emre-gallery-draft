package com.amresalehin.emregallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.DownloadActivityItem

internal val ActivityRetryAmber = Color(0xFFFFB74D)

internal fun formatRetryCountdown(
    nextRetryTimestampMillis: Long?,
    currentTimeMillis: Long,
    minSeconds: Long = 0L,
    nowLabel: String = "Retrying now..."
): String {
    val remainingSec = if (nextRetryTimestampMillis != null) {
        ((nextRetryTimestampMillis - currentTimeMillis + 999L) / 1000L).coerceAtLeast(minSeconds)
    } else {
        minSeconds
    }
    return if (remainingSec > 0L) "Retrying in ${remainingSec}s" else nowLabel
}

@Composable
internal fun ActivityRowContainer(
    thumbnailModel: String,
    item: DownloadActivityItem,
    modifier: Modifier = Modifier,
    showVideoPlayOverlay: Boolean = false,
    onClick: (() -> Unit)? = null,
    subtitleContent: @Composable ColumnScope.() -> Unit,
    trailingContent: @Composable RowScope.() -> Unit
) {
    val rowModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ActivityRowThumbnail(
            model = thumbnailModel,
            item = item,
            showVideoPlayOverlay = showVideoPlayOverlay
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitleContent()
        }

        trailingContent()
    }
}

@Composable
internal fun ActivityRowThumbnail(
    model: String,
    item: DownloadActivityItem,
    showVideoPlayOverlay: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 62.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        AsyncImage(
            model = model,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        val formatBadge = getDisplayFormatBadge(item)
        if (formatBadge.isNotBlank()) {
            ActivityFormatBadge(
                text = formatBadge,
                backgroundAlpha = 0.8f,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart)
            )
        }

        if (showVideoPlayOverlay) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.Center)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
