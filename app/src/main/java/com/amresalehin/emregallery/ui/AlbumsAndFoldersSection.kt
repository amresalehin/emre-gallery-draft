package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem


internal fun androidx.compose.foundation.lazy.LazyListScope.albumsAndFoldersSectionItems(
    customAlbums: List<CustomAlbum>,
    normalMedia: List<MediaItem>,
    folders: List<Map.Entry<String, List<MediaItem>>>,
    onFilter: (String) -> Unit,
    onCreateAlbumClick: (String) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onRequestRenameAlbum: (CustomAlbum) -> Unit
) {
        // 2. Albums Section (full-width section with header, count, 'See all >' pill, and carousel)
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("albums_section_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row: album icon, "Albums", count, and "See all >" pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Albums",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = customAlbums.size.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // "See all >" Pill
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .clickable { onFilter("Albums") }
                                .testTag("albums_see_all_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "See all",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "See all albums",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Content Carousel or Empty State
                    if (customAlbums.isEmpty()) {
                        AlbumsEmptyState(onCreateClick = { onCreateAlbumClick("") })
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(customAlbums, key = { it.id }) { album ->
                                val palette = AlbumPalettes[customAlbums.indexOf(album) % AlbumPalettes.size]
                                AlbumCardItem(
                                    album = album,
                                    palette = palette,
                                    normalMedia = normalMedia,
                                    onClick = { onFilter(album.name) },
                                    onRename = {
                                        onRequestRenameAlbum(album)
                                    },
                                    onDelete = { onDeleteAlbum(album.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Folders Section (full-width section with header, count, 'See all >' pill, and carousel)
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("folders_section_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row: folder icon, "Folders", count, and "See all >" pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Folders",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = folders.size.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // "See all >" Pill
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .clickable { onFilter("Folders") }
                                .testTag("folders_see_all_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "See all",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "See all folders",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Content Carousel or Empty State
                    if (folders.isEmpty()) {
                        FoldersEmptyState()
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(folders, key = { it.key }) { folder ->
                                val palette = FolderPalettes[folders.indexOf(folder) % FolderPalettes.size]
                                FolderCardItem(
                                    folderKey = folder.key,
                                    items = folder.value,
                                    palette = palette,
                                    onClick = { onFilter(folder.key) }
                                )
                            }
                        }
                    }
                }
            }
        }

}
