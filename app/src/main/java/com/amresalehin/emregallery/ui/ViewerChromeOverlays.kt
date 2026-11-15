package com.amresalehin.emregallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem

/**
 * Coordinates the viewer's foreground chrome overlays: edge gradient shading,
 * standard/story top bars, fullscreen exit button, video controls + bottom action bar,
 * details drawer, and end-of-story overlay.
 */
@Composable
internal fun BoxScope.ViewerChromeOverlays(
    item: MediaItem,
    items: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    customTags: Set<String>,
    defaultOcrLanguages: Set<String>,
    defaultOcrModel: String,
    defaultMlKitLanguages: Set<String>,
    favourite: Boolean,
    isHidden: Boolean,
    isBin: Boolean,
    isArchived: Boolean,
    showControls: Boolean,
    showDetails: Boolean,
    isClosing: Boolean,
    transitionProgressValue: Float,
    videoState: ViewerVideoState,
    storyState: ViewerStoryState,
    currentPageIndex: Int,
    onInitiateClose: () -> Unit,
    onShare: () -> Unit,
    onFavourite: () -> Unit,
    onShowMore: () -> Unit,
    onToggleDetails: () -> Unit,
    onToggleBinOrConfirm: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleArchive: () -> Unit,
    onAddTo: () -> Unit,
    onRequestPermanentDelete: (() -> Unit)?,
    onOpenMap: () -> Unit,
    onAddTag: (String) -> Unit,
    onOpenPerson: ((personId: String, engineId: String) -> Unit)?,
    onReplayStory: () -> Unit
) {
    val isStoryMode = storyState.isStoryMode

    if (!videoState.isFullscreen) {
        ViewerEdgeShading(
            visible = ((showControls && !isStoryMode) ||
                (isStoryMode && !storyState.isStoryHolding && !storyState.showEndOfStory)) && !isClosing
        )
    }

    if (!videoState.isFullscreen) {
        if (isStoryMode) {
            val showStoryBar = !storyState.isStoryHolding && !isClosing && !storyState.showEndOfStory
            AnimatedVisibility(
                visible = showStoryBar,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ViewerStoryTopBar(
                    storyTitle = storyState.currentStoryTitle,
                    currentIndex = currentPageIndex,
                    itemCount = storyState.currentStoryItems.size,
                    currentProgress = storyState.storyProgress,
                    isFavourite = favourite,
                    onBack = onInitiateClose,
                    onShare = onShare,
                    onToggleFavourite = onFavourite,
                    onMore = onShowMore,
                    modifier = Modifier.graphicsLayer {
                        translationY = -(80.dp.toPx()) * (1f - transitionProgressValue)
                        alpha = if (isClosing) ((transitionProgressValue - 0.35f) / 0.65f).coerceIn(0f, 1f) else 1f
                    }
                )
            }
        } else if (showControls) {
            ViewerTopAppBar(
                isFavourite = favourite,
                onBack = onInitiateClose,
                onShare = onShare,
                onToggleFavourite = onFavourite,
                onMore = onShowMore,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = -(80.dp.toPx()) * (1f - transitionProgressValue)
                        alpha = if (isClosing) ((transitionProgressValue - 0.35f) / 0.65f).coerceIn(0f, 1f) else 1f
                    }
            )
        }
    }

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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Exit fullscreen",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (showControls) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .graphicsLayer {
                    translationY = (80.dp.toPx()) * (1f - transitionProgressValue)
                    alpha = if (isClosing) ((transitionProgressValue - 0.35f) / 0.65f).coerceIn(0f, 1f) else 1f
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
                    onToggleBin = onToggleBinOrConfirm,
                    onToggleHide = onToggleHide,
                    onToggleArchive = onToggleArchive,
                    onMove = onAddTo,
                    onDeletePermanently = onRequestPermanentDelete
                )
            }
        }
    }

    if (showDetails && !videoState.isFullscreen && !isClosing) {
        ViewerDetailsDrawer(
            item = item,
            items = items,
            customAlbums = customAlbums,
            customTags = customTags,
            onClose = {
                if (showDetails) onToggleDetails()
            },
            onShare = onShare,
            onAddTo = onAddTo,
            onToggleBin = onToggleBinOrConfirm,
            onShowMore = onShowMore,
            onOpenMap = onOpenMap,
            onAddTag = onAddTag,
            isBin = isBin,
            defaultOcrLanguages = defaultOcrLanguages,
            defaultOcrModel = defaultOcrModel,
            defaultMlKitLanguages = defaultMlKitLanguages,
            onOpenPerson = onOpenPerson
        )
    }

    if (isStoryMode && storyState.showEndOfStory && !isClosing) {
        EndOfStoryOverlay(
            storyItems = storyState.allStoryItems,
            storyTitle = storyState.currentStoryTitle,
            itemCount = storyState.allStoryItems.size,
            onReplay = onReplayStory,
            onClose = onInitiateClose
        )
    }
}
