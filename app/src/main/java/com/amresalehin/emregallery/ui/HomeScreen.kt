package com.amresalehin.emregallery.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import coil.Coil
import com.amresalehin.emregallery.data.ThumbnailRequestFactory
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import java.util.LinkedHashMap
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private fun mediaTileKey(item: MediaItem): String = "media_${item.uri}"

private fun mediaTileAspect(item: MediaItem, gridStyle: String): Float {
    if (gridStyle == "square") return 1f
    return if (item.width > 0 && item.height > 0) {
        (item.width.toFloat() / item.height.toFloat()).coerceIn(0.55f, 1.8f)
    } else {
        when (item.id.hashCode().mod(4)) {
            0 -> 0.72f
            1 -> 0.9f
            2 -> 1.0f
            else -> 1.35f
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    media: List<MediaItem>,
    loading: Boolean,
    albumTitle: String? = null,
    sortOrder: String = "date_desc",
    groupBy: String = "day",
    gridColumns: Int = 3,
    gridStyle: String = "masonry",
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onClearAlbumFilter: () -> Unit = {},
    onAddPhotosToAlbum: (() -> Unit)? = null,
    onPermissionGranted: () -> Unit = {},
    onMoveToBin: (Set<String>) -> Unit = {},
    onMoveToLocked: (Set<String>) -> Unit = {},
    onShareItems: (Set<String>) -> Unit = {},
    onAddToAlbum: ((Set<String>) -> Unit)? = null,
    selectedUris: Set<String> = emptySet(),
    onSelectionChange: (Set<String>) -> Unit = {},
    onOpen: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val homeScope = rememberCoroutineScope()
    var pinchGridColumns by remember(gridColumns) { mutableIntStateOf(gridColumns.coerceIn(1, 6)) }
    var pinchZoomRemainder by remember { mutableFloatStateOf(1f) }
    var gridViewportWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val effectiveColumns = pinchGridColumns.coerceIn(1, 6)
    val horizontalContentPaddingPx = with(density) { 12.dp.roundToPx() * 2 }
    val horizontalSpacingPx = with(density) { 4.dp.roundToPx() }
    val prefetchTileWidthPx = if (gridViewportWidthPx > 0) {
        ((gridViewportWidthPx - horizontalContentPaddingPx -
            horizontalSpacingPx * (effectiveColumns - 1)) / effectiveColumns).coerceAtLeast(1)
    } else 0
    val selectionMode = selectedUris.isNotEmpty()

    BackHandler(enabled = selectionMode) {
        onSelectionChange(emptySet())
    }
    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val hasPermission = hasMediaPermission(context)
        granted = hasPermission
        if (hasPermission) onPermissionGranted()
    }
    LaunchedEffect(Unit) { granted = hasMediaPermission(context) }
    if (!granted) {
        val permissionsToRequest = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            Build.VERSION.SDK_INT >= 29 -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        PermissionState { launcher.launch(permissionsToRequest) }
        return
    }

    val sortedMedia = remember(media, sortOrder) {
        when (sortOrder) {
            "date_asc", "date_taken_asc" -> media.sortedWith(compareBy({ if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L }, { it.id }))
            "date_added_asc" -> media.sortedWith(compareBy({ it.dateAddedSeconds * 1000L }, { it.id }))
            "date_added_desc" -> media.sortedWith(compareByDescending<MediaItem> { it.dateAddedSeconds * 1000L }.thenBy { it.id })
            "name_asc" -> media.sortedWith(compareBy({ it.name.lowercase(Locale.getDefault()) }, { it.id }))
            "name_desc" -> media.sortedWith(compareByDescending<MediaItem> { it.name.lowercase(Locale.getDefault()) }.thenBy { it.id })
            "size_asc" -> media.sortedWith(compareBy<MediaItem> { it.sizeBytes }.thenBy { it.id })
            "size_desc" -> media.sortedWith(compareByDescending<MediaItem> { it.sizeBytes }.thenBy { it.id })
            else -> media.sortedWith(compareByDescending<MediaItem> { if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L }.thenBy { it.id })
        }
    }

    val groups = remember(sortedMedia, groupBy) {
        when (groupBy) {
            "day" -> groupMediaByDay(sortedMedia)
            "month" -> groupMediaByMonth(sortedMedia)
            "year" -> groupMediaByYear(sortedMedia)
            "folder" -> groupMediaByFolder(sortedMedia)
            "initial" -> groupMediaByInitial(sortedMedia)
            "none" -> listOf(DayGroup(dayKey = 0L, dayLabel = "", items = sortedMedia))
            else -> listOf(DayGroup(dayKey = 0L, dayLabel = "", items = sortedMedia))
        }
    }


    // Stable URI-based keys tie each LazyGrid slot, outline and thumbnail to one media item.
    // Prefetch the exact resolution variant that the corresponding visible tile will request.
    val gridMedia = remember(groups) { groups.flatMap { it.items } }
    val gridPositionByKey = remember(groups) {
        buildMap<String, Int> {
            var position = 0
            groups.forEach { group ->
                group.items.forEach { mediaItem ->
                    put(mediaTileKey(mediaItem), position++)
                }
            }
        }
    }
    val thumbnailImageLoader = remember(context) { Coil.imageLoader(context) }

    LaunchedEffect(
        gridState, gridMedia, gridPositionByKey, thumbnailImageLoader,
        prefetchTileWidthPx, effectiveColumns, gridStyle
    ) {
        val recentlyPrefetched = LinkedHashSet<String>()
        val recentFailures = LinkedHashMap<String, Long>()
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .mapNotNull { itemInfo -> gridPositionByKey[itemInfo.key.toString()] }
                .maxOrNull()
        }.distinctUntilChanged().collect { lastVisibleIndex ->
            if (lastVisibleIndex == null || lastVisibleIndex < 0 ||
                gridMedia.isEmpty() || prefetchTileWidthPx <= 0
            ) return@collect

            // Keep only a small lead so decoded previews do not compete heavily with visible tiles.
            val firstIndex = lastVisibleIndex + 1
            val endExclusive = minOf(firstIndex + 6, gridMedia.size)
            var attemptedPrefetches = 0
            for (index in firstIndex until endExclusive) {
                if (attemptedPrefetches >= 2) break
                val item = gridMedia[index]
                val aspect = mediaTileAspect(item, gridStyle)
                val targetHeightPx = (prefetchTileWidthPx / aspect).roundToInt().coerceAtLeast(1)
                val targetSize = ThumbnailRequestFactory.sizeFor(prefetchTileWidthPx, targetHeightPx)
                val prefetchKey = ThumbnailRequestFactory.cacheKey(item, targetSize.width, targetSize.height)
                if (prefetchKey in recentlyPrefetched) continue
                val failedAt = recentFailures[prefetchKey]
                if (failedAt != null && System.currentTimeMillis() - failedAt < 15_000L) continue

                attemptedPrefetches += 1
                // One ordered request stream. snapshotFlow conflates position updates while an
                // item is being prepared, so processing resumes from the latest visible position.
                val result = thumbnailImageLoader.execute(
                    ThumbnailRequestFactory.create(
                        context = context,
                        item = item,
                        targetWidthPx = prefetchTileWidthPx,
                        targetHeightPx = targetHeightPx
                    )
                )
                if (result is coil.request.SuccessResult) {
                    recentlyPrefetched.add(prefetchKey)
                    recentFailures.remove(prefetchKey)
                    if (recentlyPrefetched.size > 96) recentlyPrefetched.remove(recentlyPrefetched.first())
                } else {
                    recentFailures[prefetchKey] = System.currentTimeMillis()
                    if (recentFailures.size > 96) recentFailures.remove(recentFailures.keys.first())
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(pinchGridColumns.coerceIn(1, 6)),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    if (gridViewportWidthPx != size.width) gridViewportWidthPx = size.width
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            // Keep the one-finger scroll path to a pointer-count check only.
                            // Zoom calculations and event consumption are reserved for multi-touch.
                            if (event.changes.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f) {
                                    pinchZoomRemainder *= zoomChange
                                    if (pinchZoomRemainder < 0.82f) {
                                        pinchGridColumns = (pinchGridColumns + 1).coerceAtMost(6)
                                        pinchZoomRemainder = 1f
                                    } else if (pinchZoomRemainder > 1.22f) {
                                        pinchGridColumns = (pinchGridColumns - 1).coerceAtLeast(1)
                                        pinchZoomRemainder = 1f
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = topContentPadding,
                bottom = bottomContentPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalItemSpacing = 14.dp
        ) {
            if (albumTitle != null) {
                val isCollection = albumTitle in listOf("Locked folder", "Bin", "Archive", "Favourites", "Videos", "Recently added")
                val collectionIcon = when (albumTitle) {
                    "Favourites" -> Icons.Default.Favorite
                    "Videos" -> Icons.Default.PlayCircle
                    "Recently added" -> Icons.Default.Schedule
                    "Locked folder" -> Icons.Default.Lock
                    "Bin" -> Icons.Default.Delete
                    "Archive" -> Icons.Default.Archive
                    else -> Icons.Default.Folder
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    val returnProgress = LocalViewerCoordinatedProgress.current
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .destinationTopBarEntrance(returnProgress),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(collectionIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (isCollection) "Collection" else "Album", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                Text(albumTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (onAddPhotosToAlbum != null) {
                                FilledTonalButton(
                                    onClick = onAddPhotosToAlbum,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add photos", style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            IconButton(onClick = onClearAlbumFilter, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, "Clear filter", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }

            if (loading && sortedMedia.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    HomeScreenSkeleton(
                        gridColumns = pinchGridColumns,
                        gridStyle = gridStyle,
                        showMemories = albumTitle == null
                    )
                }
            } else if (sortedMedia.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (albumTitle != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(12.dp))
                                Text("No photos in this album", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(6.dp))
                                Text("Add photos from your gallery to populate this album.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                if (onAddPhotosToAlbum != null) {
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = onAddPhotosToAlbum) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add photos")
                                    }
                                }
                            }
                        } else {
                            Text("No photos or videos found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                if (loading) {
                    item(span = StaggeredGridItemSpan.FullLine) { LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape)) }
                }
                if (albumTitle == null) {
                    item(span = StaggeredGridItemSpan.FullLine) { MemoriesStrip(sortedMedia, onOpen) }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            thickness = 1.dp
                        )
                    }
                }

                groups.forEach { group ->
                    if (group.dayLabel.isNotBlank()) {
                        item(key = "header_${group.dayKey}", span = StaggeredGridItemSpan.FullLine, contentType = "header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = group.dayLabel,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "${group.items.size} ${if (group.items.size == 1) "item" else "items"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    staggeredItems(
                        items = group.items,
                        key = { mediaItem -> mediaTileKey(mediaItem) },
                        contentType = { "media_thumbnail" }
                    ) { mediaItem ->
                        val tileAspect = mediaTileAspect(mediaItem, gridStyle)
                        MosaicMediaItem(
                            item = mediaItem,
                            modifier = Modifier.fillMaxWidth().aspectRatio(tileAspect),
                            selected = mediaItem.uri in selectedUris,
                            onLongPress = { onSelectionChange(selectedUris + mediaItem.uri) },
                            onToggleSelection = { onSelectionChange(if (mediaItem.uri in selectedUris) selectedUris - mediaItem.uri else selectedUris + mediaItem.uri) },
                            onOpen = { opened -> if (selectionMode) { onSelectionChange(if (opened.uri in selectedUris) selectedUris - opened.uri else selectedUris + opened.uri) } else onOpen(opened) }
                        )
                    }
                }
            }
        }

        HomeFastScrollbar(
            gridState = gridState,
            topContentPadding = topContentPadding,
            bottomContentPadding = bottomContentPadding,
            thumbColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


