package com.amresalehin.emregallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaInboxItem

/**
 * True Mosaic Thumbnail component for media stacks.
 * Supports:
 * - 0 items: Empty placeholder
 * - 1 item: Full image
 * - 2 items: 2 equal vertical columns (50% / 50%) side-by-side
 * - 3 items: 1 tall on left (58%), 2 stacked on right (42%)
 * - 4+ items: 2x2 quadrant grid with +N counter on the 4th quadrant
 */
@Composable
fun TrueMediaMosaicThumbnail(
    items: List<MediaInboxItem>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        when (items.size) {
            0 -> MosaicEmptyPlaceholder()
            1 -> MosaicTileImage(item = items[0], modifier = Modifier.fillMaxSize())
            2 -> MosaicTwoColumnLayout(items[0], items[1])
            3 -> MosaicThreeItemLayout(items[0], items[1], items[2])
            else -> MosaicQuadrantLayout(items)
        }
    }
}

@Composable
private fun MosaicEmptyPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun MosaicTileImage(
    item: MediaInboxItem,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = item.localUri,
        contentDescription = item.title,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun MosaicTwoColumnLayout(
    first: MediaInboxItem,
    second: MediaInboxItem
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        MosaicTileImage(
            item = first,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        MosaicTileImage(
            item = second,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun MosaicThreeItemLayout(
    left: MediaInboxItem,
    topRight: MediaInboxItem,
    bottomRight: MediaInboxItem
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        MosaicTileImage(
            item = left,
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
        )
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MosaicTileImage(
                item = topRight,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            MosaicTileImage(
                item = bottomRight,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MosaicQuadrantLayout(items: List<MediaInboxItem>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MosaicTileImage(
                item = items[0],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            MosaicTileImage(
                item = items[1],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MosaicTileImage(
                item = items[2],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                MosaicTileImage(item = items[3], modifier = Modifier.fillMaxSize())
                if (items.size > 4) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.58f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${items.size - 3}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
