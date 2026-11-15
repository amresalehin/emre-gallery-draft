package com.amresalehin.emregallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.ScrapedMediaItem

/**
 * Clean, prominent media card for scraped downloader results.
 * Displays format label top-left, selection checkbox top-right,
 * filename below thumbnail, dimensions bottom-left, and category bottom-right.
 */
@Composable
fun DownloaderMediaCard(
    item: ScrapedMediaItem,
    isSelected: Boolean,
    dynamicDimensions: Map<String, Pair<Int, Int>>,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false
) {
    val meta = resolveScrapedCardMeta(item, dynamicDimensions)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF1F3F5),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("scraped_media_card_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DownloaderMediaCardThumbnail(
                previewUrl = item.thumbnailUrl ?: item.url,
                contentDescription = item.title,
                formatText = meta.formatText,
                isTicked = isSelected || isDownloaded,
                isDownloaded = isDownloaded
            )

            DownloaderMediaCardFooter(
                titleText = meta.titleText,
                dimText = meta.dimText,
                categoryText = meta.categoryText
            )
        }
    }
}

@Composable
private fun DownloaderMediaCardThumbnail(
    previewUrl: String,
    contentDescription: String,
    formatText: String,
    isTicked: Boolean,
    isDownloaded: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xFFE9ECEF))
    ) {
        AsyncImage(
            model = previewUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 1.dp,
            modifier = Modifier
                .padding(start = 6.dp, top = 6.dp)
                .align(Alignment.TopStart)
        ) {
            Text(
                text = formatText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = Color(0xFF212529),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .padding(end = 6.dp, top = 6.dp)
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isTicked) Color(0xFF212529) else Color.Black.copy(alpha = 0.25f))
                .border(
                    width = 1.5.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isTicked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (isDownloaded) "Downloaded" else "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloaderMediaCardFooter(
    titleText: String,
    dimText: String,
    categoryText: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = titleText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = Color(0xFF212529),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dimText,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color(0xFF6C757D),
                maxLines = 1
            )
            Text(
                text = categoryText,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color(0xFF6C757D),
                maxLines = 1
            )
        }
    }
}
