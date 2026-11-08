package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.*
import java.util.*


internal fun SquareGrid(
    media: List<MediaItem>,
    columnsCount: Int,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cols = columnsCount.coerceAtLeast(1)
    val rows = remember(media, cols) {
        media.chunked(cols)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowItems.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpen(item) }
                            .testTag("media_item_${item.id}")
                    ) {
                        AsyncImage(
                            model = remember(item.uri, context) {
                                ThumbnailUtils.buildThumbnailRequest(context, item.uri)
                            },
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (item.isVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
                val remainder = cols - rowItems.size
                repeat(remainder) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun DynamicMasonryGrid(
    media: List<MediaItem>,
    columnsCount: Int,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cols = columnsCount.coerceAtLeast(1)
    val columns = remember(media, cols) {
        List(cols) { colIndex ->
            media.filterIndexed { index, _ -> index % cols == colIndex }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        columns.forEach { colItems ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                colItems.forEach { item ->
                    val aspect = when (Math.abs(item.id.hashCode()) % 3) {
                        0 -> 0.85f
                        1 -> 1.0f
                        else -> 1.25f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpen(item) }
                            .testTag("media_item_${item.id}")
                    ) {
                        AsyncImage(
                            model = remember(item.uri, context) {
                                ThumbnailUtils.buildThumbnailRequest(context, item.uri)
                            },
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (item.isVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact list row for list view mode in search results.
 */
@Composable
internal fun CompactMediaListRow(
    item: MediaItem,
    onOpen: () -> Unit,
    isDark: Boolean = false
) {
    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)
    val cardSurface = searchCardSurfaceColor(isDark)
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .testTag("media_row_${item.id}"),
        shape = RoundedCornerShape(10.dp),
        color = cardSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = remember(item.uri, context) {
                    ThumbnailUtils.buildThumbnailRequest(context, item.uri)
                },
                contentDescription = item.name,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.relativePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.isVideo) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    modifier = Modifier.size(20.dp),
                    tint = secondaryText
                )
            }
        }
    }
}

/**
 * Custom Date Range Picker Dialog.
 */
