package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.DownloadActivityItem

/**
 * Prominent Activity Card container on the download inbox main page:
 * - Delegates header/badge rendering to [ActivityPreviewCardHeader]
 * - Delegates thumbnail strip rendering to [ActivityPreviewThumbnailStrip]
 */
@Composable
fun ActivityPreviewCard(
    activities: List<DownloadActivityItem>,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val containerShape = RoundedCornerShape(26.dp)

    val downloading = remember(activities) { activities.filter { it.isDownloading || it.isPaused || it.isRetrying } }
    val failed = remember(activities) { activities.filter { it.isFailed } }
    val finished = remember(activities) { activities.filter { it.isFinished } }

    Surface(
        onClick = onOpenActivity,
        shape = containerShape,
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.65f else 0.75f)
        ),
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = containerShape,
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.12f),
                spotColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.18f)
            )
            .testTag("downloader_activity_preview_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ActivityPreviewCardHeader(
                failedCount = failed.size,
                downloadingCount = downloading.size,
                isDark = isDark
            )

            ActivityPreviewThumbnailStrip(
                hasActivities = activities.isNotEmpty(),
                downloading = downloading,
                failed = failed,
                finished = finished
            )
        }
    }
}
