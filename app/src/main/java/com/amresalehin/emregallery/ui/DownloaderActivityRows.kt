package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.DownloadActivityItem
import com.amresalehin.emregallery.data.DownloadActivityManager

/**
 * Row displaying a finished past download item with size, date, and completion checkmark.
 */
@Composable
internal fun FinishedDownloadRow(
    item: DownloadActivityItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ActivityRowContainer(
        thumbnailModel = item.localUri ?: item.thumbnailUrl ?: item.mediaUrl,
        item = item,
        modifier = modifier,
        onClick = onClick,
        subtitleContent = {
            val sizeStr = DownloadActivityManager.formatBytes(item.bytesRead)
            val dateStr = DownloadActivityManager.formatDate(item.timestampMillis)
            Text(
                text = "$sizeStr • $dateStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Finished",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(24.dp)
            )
        }
    )
}
