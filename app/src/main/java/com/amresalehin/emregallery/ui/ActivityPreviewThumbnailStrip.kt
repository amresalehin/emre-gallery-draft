package com.amresalehin.emregallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.DownloadActivityItem

/**
 * Horizontal preview thumbnail strip displaying active/failed items, divider, and finished items,
 * or a compact empty placeholder when no download activity exists.
 */
@Composable
internal fun ActivityPreviewThumbnailStrip(
    hasActivities: Boolean,
    downloading: List<DownloadActivityItem>,
    failed: List<DownloadActivityItem>,
    finished: List<DownloadActivityItem>
) {
    if (!hasActivities) {
        ActivityPreviewEmptyPlaceholder()
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val activeOrFailedList = (downloading.take(2) + failed.take(2)).take(2)
        val finishedList = finished.take(3)

        for (item in activeOrFailedList) {
            ActivityThumbnailBox(item = item)
        }

        if (activeOrFailedList.isNotEmpty() && finishedList.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(54.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }

        for (item in finishedList) {
            ActivityThumbnailBox(item = item)
        }
    }
}

@Composable
private fun ActivityPreviewEmptyPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active downloads • Activity appears here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
