package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem

@Composable
fun CustomAlbumCard(
    album: CustomAlbum,
    coverItem: MediaItem?,
    onClick: () -> Unit,
    onCreateSubAlbum: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.width(142.dp)) {
        Box(
            Modifier.fillMaxWidth().height(126.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .testTag("album_card_${album.id}")
        ) {
            if (coverItem != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(coverItem.uri))
                        .size(300, 300)
                        .crossfade(false)
                        .memoryCacheKey("${coverItem.uri}_album_thumb")
                        .build(),
                    contentDescription = album.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        if (album.parentPath.isNotBlank()) Icons.Default.FolderSpecial else Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(46.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = .45f), CircleShape).testTag("album_menu_button_${album.id}")
                ) {
                    Icon(Icons.Default.MoreVert, "More actions", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Add sub-album") },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                        onClick = { menuExpanded = false; onCreateSubAlbum() },
                        modifier = Modifier.testTag("album_add_sub_${album.id}")
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onRename() },
                        modifier = Modifier.testTag("album_rename_${album.id}")
                    )
                    DropdownMenuItem(
                        text = { Text("Delete album", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() },
                        modifier = Modifier.testTag("album_delete_${album.id}")
                    )
                }
            }

            if (album.parentPath.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        album.parentPath,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text(album.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
        Text("${album.mediaUris.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAlbumDialog(
    presetParent: String,
    existingAlbums: List<CustomAlbum>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, parentPath: String) -> Unit
) {
    var albumName by remember { mutableStateOf("") }
    var parentPath by remember { mutableStateOf(presetParent) }
    var showParentDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (parentPath.isBlank()) "Create new album" else "Create nested sub-album", color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = albumName,
                    onValueChange = { albumName = it },
                    label = { Text("Album name") },
                    placeholder = { Text("e.g. Summer Vacation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("album_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Column {
                    Text("Nest inside parent album (optional):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = parentPath,
                        onValueChange = { parentPath = it },
                        placeholder = { Text("None (top level)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("album_parent_input"),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (existingAlbums.isNotEmpty()) {
                                IconButton(onClick = { showParentDropdown = !showParentDropdown }) {
                                    Icon(Icons.Default.ArrowDropDown, "Select existing album")
                                }
                            }
                        }
                    )
                    if (existingAlbums.isNotEmpty()) {
                        DropdownMenu(
                            expanded = showParentDropdown,
                            onDismissRequest = { showParentDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (Root level)") },
                                onClick = { parentPath = ""; showParentDropdown = false }
                            )
                            existingAlbums.map { it.fullPath }.distinct().forEach { full ->
                                DropdownMenuItem(
                                    text = { Text(full) },
                                    onClick = { parentPath = full; showParentDropdown = false }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (albumName.isNotBlank()) {
                        onConfirm(albumName.trim(), parentPath.trim())
                    }
                },
                enabled = albumName.isNotBlank(),
                modifier = Modifier.testTag("album_create_confirm_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToAlbumBottomSheet(
    item: MediaItem,
    customAlbums: List<CustomAlbum>,
    onDismiss: () -> Unit,
    onToggleAlbum: (CustomAlbum) -> Unit,
    onCreateNewAlbum: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add to album", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Select albums to include this photo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(
                    onClick = { onDismiss(); onCreateNewAlbum() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_to_album_new_button")
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New album")
                }
            }

            if (customAlbums.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No custom albums yet", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onDismiss(); onCreateNewAlbum() }) {
                            Text("Create your first album")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(customAlbums) { album ->
                        val isInAlbum = item.uri in album.mediaUris
                        Surface(
                            onClick = { onToggleAlbum(album) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isInAlbum) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (isInAlbum) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().testTag("album_toggle_${album.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (album.parentPath.isNotBlank()) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isInAlbum) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(album.name, fontWeight = FontWeight.SemiBold, color = if (isInAlbum) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    if (album.parentPath.isNotBlank()) {
                                        Text("In: ${album.parentPath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${album.mediaUris.size} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isInAlbum) {
                                    Icon(Icons.Default.CheckCircle, "In album", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(Icons.Default.AddCircleOutline, "Add to album", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddPhotosToAlbumDialog(
    album: CustomAlbum,
    allMedia: List<MediaItem>,
    onDismiss: () -> Unit,
    onAddSelected: (List<String>) -> Unit
) {
    val selectedUris = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Add photos to '${album.name}'", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${selectedUris.size} selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            if (allMedia.isEmpty()) {
                Text("No photos available in library.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allMedia) { item ->
                        val isAlreadyIn = item.uri in album.mediaUris
                        val isSelected = item.uri in selectedUris
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (!isAlreadyIn) {
                                        if (isSelected) selectedUris.remove(item.uri)
                                        else selectedUris.add(item.uri)
                                    }
                                }
                        ) {
                            AsyncImage(Uri.parse(item.uri), item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (isSelected || isAlreadyIn) {
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(Color.Black.copy(alpha = if (isAlreadyIn) 0.5f else 0.35f))
                                )
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = if (isAlreadyIn) Color.LightGray else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddSelected(selectedUris.toList()) },
                enabled = selectedUris.isNotEmpty(),
                modifier = Modifier.testTag("confirm_add_photos_button")
            ) {
                Text("Add (${selectedUris.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CollectionPill(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconBg: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 3.5.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("collection_pill_${title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CollectionTile(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconBg: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 3.5.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("collection_tile_${title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
