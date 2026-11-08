package com.amresalehin.emregallery.ui
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedFolderScreen(
    items: List<MediaItem>,
    availableGalleryMedia: List<MediaItem>,
    isUnlocked: Boolean,
    hasPinSet: Boolean,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onUnlockItem: (MediaItem) -> Unit,
    onUnlockBatch: (Set<String>) -> Unit,
    onDeleteBatchToBin: (Set<String>) -> Unit,
    onAddItemsToLocked: (Set<String>) -> Unit,
    onSetPin: (String) -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    onUnlockSuccess: () -> Unit,
    onLockAgain: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAddPhotosSheet by remember { mutableStateOf(false) }
    var confirmBatchMoveToBin by remember { mutableStateOf(false) }

    // Intercept back to exit
    BackHandler {
        onBack()
    }

    if (!isUnlocked) {
        LockedFolderAuthView(
            hasPinSet = hasPinSet,
            onBack = onBack,
            onSetPin = onSetPin,
            onVerifyPin = onVerifyPin,
            onUnlockSuccess = onUnlockSuccess
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .destinationTopBarEntrance(LocalViewerCoordinatedProgress.current)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("locked_folder_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Albums")
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Locked folder",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "${items.size} ${if (items.size == 1) "item" else "items"} · Hidden from main gallery",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = { showAddPhotosSheet = true },
                        modifier = Modifier.padding(end = 4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add photos", style = MaterialTheme.typography.labelMedium)
                    }

                    IconButton(
                        onClick = onLockAgain,
                        modifier = Modifier.testTag("locked_folder_lock_now_button")
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Lock folder now",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // Multi-selection bar if items are selected
            AnimatedVisibility(
                visible = selectedUris.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedUris = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                        Text(
                            "${selectedUris.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        FilledTonalButton(
                            onClick = {
                                val targets = selectedUris
                                selectedUris = emptySet()
                                onUnlockBatch(targets)
                            },
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Move out")
                        }
                        IconButton(
                            onClick = {
                                val targets = selectedUris
                                selectedUris = emptySet()
                                onDeleteBatchToBin(targets)
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Move to Bin", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(88.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Locked folder is empty",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Photos and videos in your Locked folder stay private and will not appear in your gallery, albums, or search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { showAddPhotosSheet = true },
                            modifier = Modifier.testTag("locked_folder_empty_add_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add photos to Locked folder")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gridItems(items, key = { it.uri }) { item ->
                        val isSelected = item.uri in selectedUris
                        LockedMediaCard(
                            item = item,
                            isSelected = isSelected,
                            selectionMode = selectedUris.isNotEmpty(),
                            onOpen = {
                                if (selectedUris.isNotEmpty()) {
                                    selectedUris = if (isSelected) selectedUris - item.uri else selectedUris + item.uri
                                } else {
                                    onOpen(item)
                                }
                            },
                            onLongPress = {
                                selectedUris = selectedUris + item.uri
                            },
                            onUnlock = { onUnlockItem(item) }
                        )
                    }
                }
            }
        }
    }



    // Modal Sheet for adding photos from gallery to locked folder
    if (showAddPhotosSheet) {
        AddPhotosToLockedSheet(
            availableMedia = availableGalleryMedia,
            onDismiss = { showAddPhotosSheet = false },
            onAddSelected = { selected ->
                showAddPhotosSheet = false
                onAddItemsToLocked(selected)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
