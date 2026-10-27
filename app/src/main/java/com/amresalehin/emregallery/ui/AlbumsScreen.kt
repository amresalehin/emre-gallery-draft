package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun AlbumsScreen(
    media: List<MediaItem>,
    favourites: Set<String>,
    hidden: Set<String>,
    inBin: Set<String>,
    archive: Set<String>,
    customAlbums: List<CustomAlbum>,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onFilter: (String) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onCreateAlbumClick: (parentPreset: String) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onRenameAlbum: (String, String) -> Unit
) {
    val context = LocalContext.current
    val normalMedia = remember(media, hidden, inBin, archive) {
        media.filter { it.uri !in hidden && it.uri !in inBin && it.uri !in archive }
    }
    val folders = remember(normalMedia) {
        normalMedia.groupBy { it.relativePath.substringBeforeLast('/', "Internal storage").ifBlank { "Internal storage" } }
            .entries.sortedByDescending { it.value.size }
    }

    val favouritesCount = favourites.size
    val binCount = inBin.size
    val hiddenCount = hidden.size
    val archiveCount = archive.size

    var albumToRename by remember { mutableStateOf<CustomAlbum?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (albumToRename != null) {
        AlertDialog(
            onDismissRequest = { albumToRename = null },
            title = { Text("Rename album") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New album name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenameAlbum(albumToRename!!.id, renameText.trim())
                        }
                        albumToRename = null
                    },
                    enabled = renameText.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { albumToRename = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topContentPadding,
            bottom = bottomContentPadding
        )
    ) {
        // 1. People & pets Card (empty faces, smaller floating arrow in corner)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 2.dp,
                shadowElevation = 5.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("albums_people_card")
            ) {
                val detectedPeople = remember(media, context) {
                    runCatching {
                        val store = com.amresalehin.emregallery.face.FaceClusterStore(context.applicationContext)
                        store.personSummaries()
                    }.getOrDefault(emptyList())
                }
                val avatarSlots = remember(detectedPeople) {
                    val peopleUris = detectedPeople.mapNotNull { it.sampleUri }
                    (peopleUris + List((14 - peopleUris.size).coerceAtLeast(0)) { null }).take(14)
                }
                Box(modifier = Modifier.padding(14.dp)) {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gridItems(avatarSlots) { uri ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .size(58.dp)
                                    .clickable { onFilter("People") }
                            ) {
                                if (uri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(Uri.parse(uri))
                                            .crossfade(false)
                                            .build(),
                                        contentDescription = "Person",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    // Circular arrow button in top right
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.TopEnd)
                            .clickable { onFilter("People") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Open People",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Album (2/3rd, squaricle) and Device Folder (1/3rd, portrait) in same row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Column: Albums collection preview with nested mini album cards
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    shadowElevation = 5.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .clickable { onFilter("Albums") }
                        .testTag("albums_section_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoAlbum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Albums",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                customAlbums.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (customAlbums.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Create your first album", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                userScrollEnabled = false,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                gridItems(customAlbums.take(4), key = { it.id }) { album ->
                                    val cover = normalMedia.firstOrNull { it.uri in album.mediaUris }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        if (cover != null) {
                                            MediaThumbnail(
                                                item = cover,
                                                contentDescription = "Preview of " + album.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Column: Device folders with two compact preview tiles
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    shadowElevation = 5.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onFilter("Folders") }
                        .testTag("folders_section_card")
                ) {
                    val folderPreview = folders.take(2)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Folders",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (folderPreview.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                folderPreview.forEach { folder ->
                                    val cover = folder.value.firstOrNull()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        if (cover != null) {
                                            MediaThumbnail(
                                                item = cover,
                                                contentDescription = "Preview of " + folder.key.substringAfterLast('/'),
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            "${folders.size} ${if (folders.size == 1) "folder" else "folders"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. Maps Card (strictly black-grey-white)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 2.dp,
                shadowElevation = 5.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFilter("Map") }
                    .testTag("albums_map_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            Configuration.getInstance().userAgentValue = ctx.packageName
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(false)
                                setBuiltInZoomControls(false)
                                controller.setZoom(4.5)
                                controller.setCenter(GeoPoint(22.5, 79.0))
                                isClickable = false
                                isFocusable = false
                            }
                        },
                        update = { preview ->
                            preview.setOnTouchListener { _, _ -> false }
                            preview.invalidate()
                        }
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable { onFilter("Map") }
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.08f),
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
                                    )
                                )
                            )
                    )

                    // Content overlay
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = "Map and Places",
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Map",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 4. The tiles - Collections
        item {
            // Top primary pills: Favourites & Bin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CollectionPill(
                    title = "Favourites",
                    count = "$favouritesCount items",
                    icon = Icons.Default.Favorite,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onFilter("Favourites") },
                    modifier = Modifier.weight(1f)
                )
                CollectionPill(
                    title = "Bin",
                    count = "$binCount items",
                    icon = Icons.Default.Delete,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onFilter("Bin") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // 2-column tiles: Locked folder & Archive
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CollectionTile(
                    title = "Locked folder",
                    count = if (hiddenCount > 0) "$hiddenCount items" else "Private",
                    icon = Icons.Default.Lock,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onFilter("Hidden") },
                    modifier = Modifier.weight(1f)
                )
                CollectionTile(
                    title = "Archive",
                    count = "$archiveCount items",
                    icon = Icons.Default.Archive,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onFilter("Archive") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun FolderCard(name: String, items: List<MediaItem>, onClick: () -> Unit, onMore: () -> Unit) {
    Column(Modifier.width(142.dp)) {
        Box(Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
            items.firstOrNull()?.let {
                MediaThumbnail(
                    item = it,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            IconButton(onClick = onMore, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(32.dp).background(Color.Black.copy(alpha = .35f), CircleShape)) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
        }
        Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
        Text("${items.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CreateCollectionCard(onClick: () -> Unit) {
    Column(Modifier.width(142.dp)) {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(126.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Create album", Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurface) } }
        Text("Create album", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
        Text("Add new collection", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
