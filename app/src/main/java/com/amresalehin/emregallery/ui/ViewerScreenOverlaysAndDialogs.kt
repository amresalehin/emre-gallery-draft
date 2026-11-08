package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoxScope.ViewerScreenOverlaysSection(
    item: MediaItem,
    items: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    customTags: Set<String>,
    favourite: Boolean,
    isBin: Boolean,
    isHidden: Boolean,
    isArchived: Boolean,
    isStoryMode: Boolean,
    isStoryHolding: Boolean,
    showEndOfStory: Boolean,
    onShowEndOfStoryChange: (Boolean) -> Unit,
    onResetStoryToStart: () -> Unit,
    currentStoryTitle: String,
    currentStoryItems: List<MediaItem>,
    allStoryItems: List<MediaItem>,
    storyProgress: Float,
    showControls: Boolean,
    showDetails: Boolean,
    isClosing: Boolean,
    transitionProgressValue: Float,
    pagerState: androidx.compose.foundation.pager.PagerState,
    videoState: ViewerVideoState,
    initiateClose: () -> Unit,
    shareMedia: () -> Unit,
    onFavourite: () -> Unit,
    onShowMore: () -> Unit,
    onToggleBin: () -> Unit,
    onRequestConfirmMoveToBin: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleArchive: () -> Unit,
    onAddTo: () -> Unit,
    onDeletePermanently: (() -> Unit)?,
    onRequestConfirmPermanentDelete: () -> Unit,
    onToggleDetails: () -> Unit,
    onOpenMap: () -> Unit,
    onAddTag: (String) -> Unit,
    defaultOcrLanguages: Set<String>,
    defaultOcrModel: String,
    defaultMlKitLanguages: Set<String>,
    onOpenPerson: ((String, String) -> Unit)?
) {
            // Edge gradient shading behind top and bottom bars (hidden in fullscreen or when sheet rises)
            if (!videoState.isFullscreen) {
                ViewerEdgeShading(visible = ((showControls && !isStoryMode) || (isStoryMode && !isStoryHolding && !showEndOfStory)) && !isClosing)
            }

            // Top App Bar in standard or story view
            if (!videoState.isFullscreen) {
                if (isStoryMode) {
                    val showStoryBar = !isStoryHolding && !isClosing && !showEndOfStory
                    AnimatedVisibility(
                        visible = showStoryBar,
                        enter = fadeIn(animationSpec = tween(150)),
                        exit = fadeOut(animationSpec = tween(150)),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        ViewerStoryTopBar(
                            storyTitle = currentStoryTitle,
                            currentIndex = pagerState.currentPage,
                            itemCount = currentStoryItems.size,
                            currentProgress = storyProgress,
                            isFavourite = favourite,
                            onBack = { initiateClose() },
                            onShare = shareMedia,
                            onToggleFavourite = onFavourite,
                            onMore = { onShowMore() },
                            modifier = Modifier.graphicsLayer {
                                val p = transitionProgressValue
                                translationY = -(80.dp.toPx()) * (1f - p)
                                alpha = if (isClosing) ((p - 0.35f) / 0.65f).coerceIn(0f, 1f) else 1f
                            }
                        )
                    }
                } else if (showControls) {
                    ViewerTopAppBar(
                        isFavourite = favourite,
                        onBack = { initiateClose() },
                        onShare = shareMedia,
                        onToggleFavourite = onFavourite,
                        onMore = { onShowMore() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer {
                                val p = transitionProgressValue
                                translationY = -(80.dp.toPx()) * (1f - p)
                                alpha = if (isClosing) ((p - 0.35f) / 0.65f).coerceIn(0f, 1f) else 1f
                            }
                    )
                }
            }

            // Fullscreen quick exit back button
            if (videoState.isFullscreen && showControls) {
                IconButton(
                    onClick = { videoState.isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Exit fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Coordinated bottom controls cluster: Video Player Controls Bar stacked cleanly above ViewerBottomActionBar.
            if (showControls) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .graphicsLayer {
                            val p = transitionProgressValue
                            translationY = (80.dp.toPx()) * (1f - p)
                            alpha = if (isClosing) ((p - 0.35f) / 0.65f).coerceIn(0f, 1f) else 1f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (item.isVideo) {
                        VideoPlayerControlsBar(
                            videoState = videoState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (videoState.isFullscreen) 20.dp else 4.dp)
                        )
                    }

                    if (!videoState.isFullscreen && !isStoryMode) {
                        ViewerBottomActionBar(
                            isBin = isBin,
                            isHidden = isHidden,
                            isArchived = isArchived,
                            onToggleBin = {
                                if (isBin) {
                                    onToggleBin()
                                } else {
                                    onRequestConfirmMoveToBin()
                                }
                            },
                            onToggleHide = onToggleHide,
                            onToggleArchive = onToggleArchive,
                            onMove = onAddTo,
                            onDeletePermanently = if (isBin && onDeletePermanently != null) { { onRequestConfirmPermanentDelete() } } else null
                        )
                    }
                }
            }

            // Native Media Details Drawer (Modal Bottom Sheet)
            if (showDetails && !videoState.isFullscreen && !isClosing) {
                ViewerDetailsDrawer(
                    item = item,
                    items = items,
                    customAlbums = customAlbums,
                    customTags = customTags,
                    onClose = {
                        if (showDetails) onToggleDetails()
                    },
                    onShare = shareMedia,
                    onAddTo = onAddTo,
                    onToggleBin = {
                        if (isBin) {
                            onToggleBin()
                        } else {
                            onRequestConfirmMoveToBin()
                        }
                    },
                    onShowMore = { onShowMore() },
                    onOpenMap = onOpenMap,
                    onAddTag = onAddTag,
                    isBin = isBin,
                    defaultOcrLanguages = defaultOcrLanguages,
                    defaultOcrModel = defaultOcrModel,
                    defaultMlKitLanguages = defaultMlKitLanguages,
                    onOpenPerson = onOpenPerson
                )
            }

            // End-of-memory constellation screen
            if (isStoryMode && showEndOfStory && !isClosing) {
                EndOfStoryOverlay(
                    storyItems = allStoryItems,
                    storyTitle = currentStoryTitle,
                    itemCount = allStoryItems.size,
                    onReplay = {
                        onResetStoryToStart()
                    },
                    onClose = { initiateClose() }
                )
            }
}

