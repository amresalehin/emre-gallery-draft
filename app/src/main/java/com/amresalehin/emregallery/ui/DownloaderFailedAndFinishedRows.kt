package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.DownloadActivityItem

@Composable
internal fun FailedDownloadRow(
    item: DownloadActivityItem,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    currentTimeMillis: Long = System.currentTimeMillis()
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Thumbnail with dynamic format tag (NO false 4K)
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 62.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = item.thumbnailUrl ?: item.mediaUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            val formatBadge = getDisplayFormatBadge(item)
            if (formatBadge.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = formatBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Title and status/error message
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

            if (item.isRetrying && item.nextRetryTimestampMillis != null) {
                val remainingSec = ((item.nextRetryTimestampMillis - currentTimeMillis + 999L) / 1000L).coerceAtLeast(1L)
                Text(
                    text = "Retrying in ${remainingSec}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFB74D),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                val errorMsg = item.errorMessage ?: "Network error"
                Text(
                    text = "Failed • $errorMsg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Retry Button
        IconButton(
            onClick = onRetry,
            enabled = !item.isRetrying,
            modifier = Modifier.size(36.dp)
        ) {
            if (item.isRetrying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFFFB74D)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry download",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Delete Button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Row displaying a finished past download item matching image_ae11436d.png with green checkmark
 */
@Composable

