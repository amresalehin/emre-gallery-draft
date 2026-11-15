package com.amresalehin.emregallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.DownloadActivityItem

@Composable
internal fun ActivityThumbnailBox(
    item: DownloadActivityItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 68.dp, height = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val previewModel = item.localUri ?: item.thumbnailUrl ?: item.mediaUrl
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(previewModel)
                .crossfade(true)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        val formatBadge = getDisplayFormatBadge(item)
        if (formatBadge.isNotBlank()) {
            ActivityFormatBadge(
                text = formatBadge,
                backgroundAlpha = 0.75f,
                modifier = Modifier
                    .padding(top = 4.dp, start = 4.dp)
                    .align(Alignment.TopStart)
            )
        }

        when {
            item.isDownloading -> DownloadingThumbnailOverlay(item = item)
            item.isRetrying -> ThumbnailCornerBadge(
                icon = Icons.Default.Refresh,
                contentDescription = "Retrying",
                containerColor = Color(0xFFFFB74D),
                iconTint = Color.Black
            )
            item.isFailed -> ThumbnailCornerBadge(
                icon = Icons.Default.PriorityHigh,
                contentDescription = "Failed",
                containerColor = Color.White,
                iconTint = Color.Black
            )
            item.isFinished -> ThumbnailCornerBadge(
                icon = Icons.Default.Check,
                contentDescription = "Finished",
                containerColor = Color(0xFF2E7D32),
                iconTint = Color.White
            )
        }
    }
}

@Composable
private fun BoxScope.DownloadingThumbnailOverlay(item: DownloadActivityItem) {
    if (item.isVideo) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.Center)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .align(Alignment.BottomCenter)
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = item.progress.coerceIn(0.08f, 1f))
                .background(Color.White)
        )
    }
}

@Composable
private fun BoxScope.ThumbnailCornerBadge(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    iconTint: Color
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 2.dp,
        modifier = Modifier
            .padding(bottom = 4.dp, end = 4.dp)
            .size(18.dp)
            .align(Alignment.BottomEnd)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
