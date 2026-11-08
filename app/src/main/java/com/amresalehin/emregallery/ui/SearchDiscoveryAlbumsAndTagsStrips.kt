package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.*
import java.util.*

internal fun androidx.compose.foundation.lazy.LazyListScope.discoveryAlbumsAndTagsItems(
    discoveryData: DiscoveryData?,
    onSelectEntity: (DiscoveryEntityItem) -> Unit,
    textColor: Color,
    secondaryText: Color,
    cardSurface: Color,
    dividerCol: Color,
    context: android.content.Context
) {
        // 5. Real Albums (Horizontal strip of existing albums)
        if (discoveryData != null && discoveryData.albums.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Albums",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = textColor
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(discoveryData.albums) { album ->
                            Surface(
                                modifier = Modifier
                                    .width(118.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectEntity(album) }
                                    .testTag("discovery_album_${album.id}"),
                                shape = RoundedCornerShape(12.dp),
                                color = cardSurface,
                                border = BorderStroke(1.dp, dividerCol)
                            ) {
                                Column {
                                    if (album.thumbnailUri != null) {
                                        AsyncImage(
                                            model = remember(album.thumbnailUri, context) {
                                                ThumbnailUtils.buildThumbnailRequest(context, album.thumbnailUri)
                                            },
                                            contentDescription = album.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(76.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(76.dp)
                                                .background(dividerCol.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PhotoAlbum, null, tint = secondaryText)
                                        }
                                    }
                                    Column(Modifier.padding(8.dp)) {
                                        Text(
                                            text = album.title,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!album.subtitle.isNullOrBlank()) {
                                            Text(
                                                text = album.subtitle,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = secondaryText,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Real Tags (If user has created tags)
        if (discoveryData != null && discoveryData.tags.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = textColor
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(discoveryData.tags) { tag ->
                            Surface(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(CircleShape)
                                    .clickable { onSelectEntity(tag) }
                                    .testTag("discovery_tag_${tag.id}"),
                                shape = CircleShape,
                                color = cardSurface,
                                border = BorderStroke(1.dp, dividerCol)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Tag, null, modifier = Modifier.size(15.dp), tint = secondaryText)
                                    Text(text = tag.title, style = MaterialTheme.typography.labelMedium, color = textColor)
                                    if (!tag.subtitle.isNullOrBlank()) {
                                        Text(text = tag.subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = secondaryText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}
