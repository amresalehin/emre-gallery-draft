package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.DownloadActivityItem

/**
 * Row displaying a failed download item with retry countdown/error details, retry action, and delete action.
 */
@Composable
internal fun FailedDownloadRow(
    item: DownloadActivityItem,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    currentTimeMillis: Long = System.currentTimeMillis()
) {
    ActivityRowContainer(
        thumbnailModel = item.thumbnailUrl ?: item.mediaUrl,
        item = item,
        modifier = modifier,
        subtitleContent = {
            if (item.isRetrying && item.nextRetryTimestampMillis != null) {
                val retryLabel = formatRetryCountdown(
                    nextRetryTimestampMillis = item.nextRetryTimestampMillis,
                    currentTimeMillis = currentTimeMillis,
                    minSeconds = 1L
                )
                Text(
                    text = retryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ActivityRetryAmber,
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
        },
        trailingContent = {
            IconButton(
                onClick = onRetry,
                enabled = !item.isRetrying
            ) {
                if (item.isRetrying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ActivityRetryAmber
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

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}
