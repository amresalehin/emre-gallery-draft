package com.amresalehin.emregallery.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.face.FaceEngineRegistry
import com.amresalehin.emregallery.face.FaceRecognitionSettings


@Composable
internal fun GalleryAppSessionState.RenderViewerAndChromeOverlays(
    boxScope: BoxScope,
    returnDestination: Destination
) = with(boxScope) {
        // Top-layer Media Viewer with continuous thumbnail-to-fullscreen & return shared animation
        val activeViewerMedia = selectedMedia
        if (destination == Destination.VIEWER && activeViewerMedia != null) {
            val currentItem = activeViewerMedia
            val isItemBinned = currentItem.isTrashed || currentItem.uri in inBin
            val itemsForViewer = remember(viewerMediaList, currentItem) {
                if (viewerMediaList.any { it.uri == currentItem.uri }) viewerMediaList else listOf(currentItem)
            }
            ViewerScreen(
                item = currentItem,
                items = itemsForViewer,
                storyTitle = viewerStoryTitle,
                storyGroups = viewerStories,
                initialStoryIndex = currentStoryIndex,
                defaultOcrLanguages = ocrLanguagesPref,
                defaultOcrModel = ocrModelPref,
                defaultMlKitLanguages = mlKitLanguagesPref,
                favourite = currentItem.uri in favourites,
                isHidden = currentItem.uri in hidden,
                isBin = isItemBinned,
                isArchived = currentItem.uri in archive,
                showControls = showViewerControls,
                showDetails = showDetails,
                customAlbums = customAlbums,
                customTags = customTagsMap[currentItem.uri] ?: emptySet(),
                customPeople = customPeopleMap[currentItem.uri] ?: emptySet(),
                onAddTag = { tag -> scope.launch { prefs.addTagForMedia(currentItem.uri, tag) } },
                onAddPerson = { person -> scope.launch { prefs.addPersonForMedia(currentItem.uri, person) } },
                onToggleControls = { showViewerControls = !showViewerControls },
                onToggleDetails = { showDetails = !showDetails; showViewerControls = true },
                onBack = { performBackNavigation() },
                onFavourite = { scope.launch { prefs.toggleFavourite(currentItem.uri) } },
                onToggleHide = {
                    val isAlreadyLocked = currentItem.relativePath == "Locked" || currentItem.uri in lockedVaultItems.map { it.uri }
                    if (!isAlreadyLocked && lockedFolderPinHash.isNullOrBlank()) {
                        showLockedFolderSetupPrompt = true
                    } else {
                        scope.launch {
                            if (!isAlreadyLocked) {
                                val lockedList = withContext(Dispatchers.IO) { lockedVaultManager.lockItems(listOf(currentItem)) }
                                lockedVaultItems = withContext(Dispatchers.IO) { lockedVaultManager.getVaultItems() }
                                prefs.setHidden(currentItem.uri, true)
                                refreshMedia()
                                val res = snackbarHostState.showSnackbar(
                                    message = "Moved to Locked folder",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    withContext(Dispatchers.IO) { lockedVaultManager.unlockItems(lockedList) }
                                    lockedVaultItems = withContext(Dispatchers.IO) { lockedVaultManager.getVaultItems() }
                                    prefs.setHidden(currentItem.uri, false)
                                    refreshMedia()
                                }
                            } else {
                                withContext(Dispatchers.IO) { lockedVaultManager.unlockItems(listOf(currentItem)) }
                                lockedVaultItems = withContext(Dispatchers.IO) { lockedVaultManager.getVaultItems() }
                                prefs.setHidden(currentItem.uri, false)
                                refreshMedia()
                                snackbarHostState.showSnackbar("Restored to gallery", duration = SnackbarDuration.Short)
                            }
                        }
                        performBackNavigation()
                    }
                },
                onToggleBin = {
                    if (isItemBinned) {
                        restoreFromDeviceTrash(listOf(currentItem))
                    } else {
                        moveToDeviceTrash(listOf(currentItem))
                    }
                    performBackNavigation()
                },
                onDeletePermanently = if (isItemBinned) {
                    {
                        permanentlyDelete(listOf(currentItem))
                        performBackNavigation()
                    }
                } else null,
                onToggleArchive = {
                    val willArchive = currentItem.uri !in archive
                    scope.launch {
                        prefs.setArchived(currentItem.uri, willArchive)
                        val msg = if (willArchive) "Archived" else "Unarchived"
                        val res = snackbarHostState.showSnackbar(
                            message = msg,
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (res == SnackbarResult.ActionPerformed) {
                            prefs.setArchived(currentItem.uri, !willArchive)
                        }
                    }
                    performBackNavigation()
                },
                onAddTo = { mediaForAddToAlbum = currentItem; showAddToAlbumSheet = true },
                onOpenNeighbor = { selectedMedia = it; showDetails = false },
                onOpenMap = {
                    showDetails = false
                    navigateTo(Destination.MAP, rememberPrevious = true, newMapFocusItem = currentItem)
                },
                onOpenPerson = { personId, engineId ->
                    showDetails = false
                    if (engineId.isNotBlank()) {
                        FaceRecognitionSettings.setActiveEngine(context, engineId)
                        FaceEngineRegistry.setActiveEngine(engineId)
                    }
                    activePersonId = personId
                    navigateTo(Destination.PEOPLE)
                },
                transitionProgress = viewerTransitionProgress
            )
        }

        // Wider top and bottom edge blur across app screens, smoothly fading with return progress (disabled on browser)
        val showEdgeBlurs = (destination in listOf(Destination.HOME, Destination.ALBUMS)) && destination != Destination.VIEWER && destination != Destination.BROWSER
        if (showEdgeBlurs) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.045f)
                    .graphicsLayer {
                        val cp = if (destination == Destination.VIEWER) viewerTransitionProgress.value else 0f
                        alpha = (1f - cp).coerceIn(0f, 1f)
                    }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
                    .blur(6.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.045f)
                    .graphicsLayer {
                        val cp = if (destination == Destination.VIEWER) viewerTransitionProgress.value else 0f
                        alpha = (1f - cp).coerceIn(0f, 1f)
                    }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .blur(6.dp)
            )
        }

        // Floating search bar across Home and Albums - coordinated entrance downward from above screen edge
        val showSearchBar = returnDestination in listOf(Destination.HOME, Destination.ALBUMS) && destination != Destination.VIEWER && destination != Destination.BROWSER
        if (showSearchBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .graphicsLayer {
                            val cp = if (destination == Destination.VIEWER) viewerTransitionProgress.value else 0f
                            translationY = -(size.height + 60.dp.toPx()) * cp
                            alpha = (1f - cp * 1.15f).coerceIn(0f, 1f)
                        }
                ) {
                    FloatingSearchPill(
                        label = when (returnDestination) {
                            Destination.ALBUMS -> "Search folders, albums…"
                            else -> "Search photos, people, places…"
                        },
                        showMoreOptions = returnDestination == Destination.HOME,
                        onMenuClick = { showSidePanel = true },
                        onOptionsClick = { showHomeViewOptionsSheet = true },
                        onClick = {
                            navigateTo(Destination.SEARCH)
                            searchStage = SearchStage.DISCOVERY
                            searchQuery = ""
                        }
                    )
                }
            }
        }
}
