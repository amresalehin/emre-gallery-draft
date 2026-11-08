package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.face.FaceStoreEvents
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.face.FaceProcessingCoordinator
import com.amresalehin.emregallery.face.PersonSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
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
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onFilter: (String) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onCreateAlbumClick: (parentPreset: String) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onRenameAlbum: (String, String) -> Unit,
    onOpenPerson: ((String) -> Unit)? = null,
    onOpenSavedLibrary: (() -> Unit)? = null,
    onOpenSavedStack: ((com.amresalehin.emregallery.data.MediaInboxStack) -> Unit)? = null,
    onOpenBrowser: (() -> Unit)? = null
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        com.amresalehin.emregallery.GalleryApplication.warmupChromium(context)
    }
    val isProcessingActive by FaceProcessingCoordinator.isProcessingActive.collectAsStateWithLifecycle()
    val isBusy = isProcessingActive || FaceProcessingCoordinator.isBusy(context)
    var selectedEngineId by remember { mutableStateOf(FaceRecognitionSettings.getActiveEngine(context)) }
    val faceStore = remember(context) { FaceClusterStore(context.applicationContext) }
    var detectedPeople by remember { mutableStateOf(emptyList<PersonSummary>()) }
    var engineMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedEngineId) {
        suspend fun refreshPeople() {
            detectedPeople = withContext(Dispatchers.IO) {
                faceStore.personSummaries(engineId = selectedEngineId)
            }
        }
        refreshPeople()
        kotlinx.coroutines.coroutineScope {
            launch { FaceStoreEvents.peopleUpdates.collect { refreshPeople() } }
            launch { FaceStoreEvents.mediaUpdates.collect { refreshPeople() } }
        }
    }
    val normalMedia = remember(media, hidden, inBin, archive) {
        media.filter { it.uri !in hidden && it.uri !in inBin && it.uri !in archive }
    }
    var folders by remember(normalMedia) {
        mutableStateOf(
            if (normalMedia.size <= 250) {
                normalMedia.groupBy { it.relativePath.substringBeforeLast('/', "Internal storage").ifBlank { "Internal storage" } }
                    .entries.sortedByDescending { it.value.size }
            } else {
                normalMedia.take(250).groupBy { it.relativePath.substringBeforeLast('/', "Internal storage").ifBlank { "Internal storage" } }
                    .entries.sortedByDescending { it.value.size }
            }
        )
    }
    LaunchedEffect(normalMedia) {
        if (normalMedia.size > 250) {
            val fullFolders = withContext(Dispatchers.Default) {
                normalMedia.groupBy { it.relativePath.substringBeforeLast('/', "Internal storage").ifBlank { "Internal storage" } }
                    .entries.sortedByDescending { it.value.size }
            }
            folders = fullFolders
        }
    }

    val favouritesCount = favourites.size
    val binCount = inBin.size
    val hiddenCount = hidden.size
    val archiveCount = archive.size

    val downloadActivities by com.amresalehin.emregallery.data.DownloadActivityManager.activities.collectAsState()
    val activityCountText = remember(downloadActivities) {
        val active = downloadActivities.count { it.status == com.amresalehin.emregallery.data.DownloadActivityStatus.DOWNLOADING || it.status == com.amresalehin.emregallery.data.DownloadActivityStatus.RETRYING }
        val failed = downloadActivities.count { it.status == com.amresalehin.emregallery.data.DownloadActivityStatus.FAILED }
        when {
            active > 0 -> "$active active"
            failed > 0 -> "$failed failed"
            downloadActivities.isNotEmpty() -> "${downloadActivities.size} items"
            else -> ""
        }
    }

    var albumToRename by remember { mutableStateOf<CustomAlbum?>(null) }
    var renameText by remember { mutableStateOf("") }

    val activeAlbumToRename = albumToRename
    if (activeAlbumToRename != null) {
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
                        val album = albumToRename ?: activeAlbumToRename
                        if (renameText.isNotBlank()) {
                            onRenameAlbum(album.id, renameText.trim())
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

    val hapticFeedback = LocalHapticFeedback.current
    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onRefresh?.invoke()
        },
        modifier = Modifier.fillMaxSize(),
        state = pullRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topContentPadding)
            )
        }
    ) {
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
        // 0. The tiles - Collections (Favourites, Bin, Locked folder, Inbox) moved to top
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

            // 2-column tiles: Locked folder & Activity
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
                    title = "Activity",
                    count = activityCountText,
                    icon = Icons.Default.MonitorHeart,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onFilter("Activity") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // 2-column tiles: Browser & Archive
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CollectionTile(
                    title = "Browser",
                    count = "",
                    icon = Icons.Default.TravelExplore,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onOpenBrowser?.invoke() ?: onFilter("Browser") },
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
        albumsPeopleCardItem(
            context = context,
            isBusy = isBusy,
            selectedEngineId = selectedEngineId,
            onSelectedEngineIdChange = { selectedEngineId = it },
            engineMenuExpanded = engineMenuExpanded,
            onEngineMenuExpandedChange = { engineMenuExpanded = it },
            detectedPeople = detectedPeople,
            onFilter = onFilter,
            onOpenPerson = onOpenPerson
        )

        albumsAndFoldersSectionItems(
            customAlbums = customAlbums,
            normalMedia = normalMedia,
            folders = folders,
            onFilter = onFilter,
            onCreateAlbumClick = onCreateAlbumClick,
            onDeleteAlbum = onDeleteAlbum,
            onRequestRenameAlbum = { album ->
                albumToRename = album
                renameText = album.name
            }
        )

        albumsMapCardItem(onFilter = onFilter)
    }
    }
}
