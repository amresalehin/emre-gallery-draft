package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.alpha
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem


internal fun AlbumCardItem(
    album: CustomAlbum,
    palette: CardPalette,
    normalMedia: List<MediaItem>,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val albumMedia = remember(album, normalMedia) {
        normalMedia.filter { it.uri in album.mediaUris }.take(4)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = palette.bgColor,
        border = BorderStroke(1.5.dp, palette.textColor.copy(alpha = 0.22f)),
        modifier = modifier
            .width(160.dp)
            .height(220.dp)
            .clickable(onClick = onClick)
            .testTag("album_card_${album.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Album spine binding accent on left
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .padding(vertical = 14.dp)
                    .align(Alignment.CenterStart)
                    .background(
                        palette.textColor.copy(alpha = 0.16f),
                        RoundedCornerShape(2.dp)
                    )
            )

            // Count badge at top-right
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.badgeBg.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                Text(
                    text = album.mediaUris.size.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 10.dp, top = 26.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 2x2 collage
                Collage2x2(
                    items = albumMedia,
                    palette = palette,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                )

                // Bottom title row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Album options",
                                tint = palette.textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open album") },
                                onClick = {
                                    menuOpen = false
                                    onClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    menuOpen = false
                                    onRename()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Collage2x2(
    items: List<MediaItem>,
    palette: CardPalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CollageCell(
                item = items.getOrNull(0),
                palette = palette,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            CollageCell(
                item = items.getOrNull(1),
                palette = palette,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CollageCell(
                item = items.getOrNull(2),
                palette = palette,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            CollageCell(
                item = items.getOrNull(3),
                palette = palette,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
internal fun CollageCell(
    item: MediaItem?,
    palette: CardPalette,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.textColor.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (item != null) {
            val context = LocalContext.current
            AsyncImage(
                model = remember(item.uri, context) {
                    ThumbnailUtils.buildThumbnailRequest(context, item.uri, 200, 200)
                },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = palette.textColor.copy(alpha = 0.22f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
