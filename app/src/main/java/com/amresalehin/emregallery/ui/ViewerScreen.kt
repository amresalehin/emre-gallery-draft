package com.amresalehin.emregallery.ui

import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.StoryGroup
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Fullscreen media viewer screen supporting:
 * - Shared-element return animation back to original source thumbnail
 * - Playback continuity between videos (respects user Play/Pause intent)
 * - Safe layout of video playback controls without overlapping bottom action buttons
 * - True fullscreen immersive video playback with status/navigation bar hiding and pure black backdrop
 * - Fluid pinch/double-tap zoom/pan
 * - Unified 1:1 gesture-coordinated details drawer
 */
@Composable
fun ViewerScreen(
    item: MediaItem,
    items: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    storyTitle: String? = null,
    storyGroups: List<StoryGroup> = emptyList(),
    initialStoryIndex: Int = 0,
    defaultOcrLanguages: Set<String> = setOf("eng"),
    defaultOcrModel: String = "fast",
    defaultMlKitLanguages: Set<String> = setOf("latin"),
    customTags: Set<String>,
    customPeople: Set<String>,
    onAddTag: (String) -> Unit,
    onAddPerson: (String) -> Unit,
    favourite: Boolean,
    isHidden: Boolean,
    isBin: Boolean,
    isArchived: Boolean,
    showControls: Boolean,
    showDetails: Boolean,
    onToggleControls: () -> Unit,
    onToggleDetails: () -> Unit,
    onBack: () -> Unit,
    onFavourite: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleBin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDeletePermanently: (() -> Unit)? = null,
    onAddTo: () -> Unit,
    onOpenNeighbor: (MediaItem) -> Unit,
    onOpenMap: () -> Unit,
    onOpenPerson: ((personId: String, engineId: String) -> Unit)? = null,
    transitionProgress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewConfig = remember(context) { ViewConfiguration.get(context) }
    val minFlingVelocity = remember(viewConfig) { viewConfig.scaledMinimumFlingVelocity.toFloat() }

    var sessionPlayIntent by remember { mutableStateOf(true) }
    val videoState = rememberViewerVideoState(
        uri = item.uri,
        isVideo = item.isVideo,
        showControls = showControls,
        sessionPlayIntent = sessionPlayIntent,
        onPlayIntentChange = { sessionPlayIntent = it }
    )

    ViewerSystemBarsImmersion(isFullscreen = videoState.isFullscreen)

    var isClosing by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var confirmPermanentDelete by remember { mutableStateOf(false) }
    var confirmMoveToBin by remember { mutableStateOf(false) }
    var isCurrentZoomed by remember { mutableStateOf(false) }

    val dismissOffsetY = remember { Animatable(0f) }
    LaunchedEffect(item.uri) {
        dismissOffsetY.snapTo(0f)
        ThumbnailBoundsTracker.activeViewerUri = item.uri
        transitionProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 500f
            )
        )
    }

    val storyState = rememberViewerStoryState(
        storyTitle = storyTitle,
        storyGroups = storyGroups,
        initialStoryIndex = initialStoryIndex,
        items = items
    )
    val isStoryMode = storyState.isStoryMode
    val currentStoryItems = storyState.currentStoryItems

    val initialIndex = remember(items, currentStoryItems, isStoryMode, item.uri) {
        val list = if (isStoryMode) currentStoryItems else items
        list.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { if (isStoryMode) currentStoryItems.size else items.size }
    )

    LaunchedEffect(pagerState, currentStoryItems) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            storyState.storyProgress = 0f
            val activeList = if (isStoryMode) currentStoryItems else items
            val current = activeList.getOrNull(settledPage)
            if (current != null && current.uri != item.uri) {
                onOpenNeighbor(current)
                ThumbnailBoundsTracker.activeViewerUri = current.uri
            }
        }
    }

    ViewerStoryEffects(
        storyState = storyState,
        pagerState = pagerState,
        item = item,
        videoState = videoState,
        isClosing = isClosing,
        isExternalModalPaused = showDetails || showMore || confirmMoveToBin || confirmPermanentDelete || isCurrentZoomed,
        onOpenNeighbor = onOpenNeighbor,
        onStepNext = { storyState.stepNext(pagerState, isClosing, coroutineScope) }
    )

    LaunchedEffect(item.uri) {
        if (!isStoryMode && !pagerState.isScrollInProgress) {
            val targetIndex = items.indexOfFirst { it.uri == item.uri }
            if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
                pagerState.scrollToPage(targetIndex)
            }
        }
    }

    val shareMedia: () -> Unit = { shareViewerMediaItem(context, item) }
    val initiateClose: () -> Unit = {
        if (!isClosing) {
            isClosing = true
            coroutineScope.launch {
                if (showDetails) onToggleDetails()
                ThumbnailBoundsTracker.activeViewerUri = item.uri
                transitionProgress.animateTo(targetValue = 0f, animationSpec = ViewerReturnSpringSpec)
                ThumbnailBoundsTracker.activeViewerUri = null
                onBack()
            }
        }
    }

    ViewerBackHandlers(
        itemUri = item.uri,
        videoState = videoState,
        showDetails = showDetails,
        isClosing = isClosing,
        transitionProgress = transitionProgress,
        onClosingStart = { isClosing = true },
        onInitiateClose = initiateClose,
        onBack = onBack
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        val targetBounds = ThumbnailBoundsTracker.getBounds(item.uri)
        val bgThemeColor = MaterialTheme.colorScheme.background

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (videoState.isFullscreen) {
                        drawRect(Color.Black)
                    } else {
                        val p = transitionProgress.value
                        val dismissFraction = (abs(dismissOffsetY.value) / 540f).coerceIn(0f, 1f)
                        val backdropAlpha = (1f - dismissFraction * 0.70f).coerceIn(0.30f, 1f)
                        drawRect(bgThemeColor.copy(alpha = (p * backdropAlpha).coerceIn(0f, 1f)))
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
                userScrollEnabled = !isCurrentZoomed && !videoState.isFullscreen && !showDetails && !isClosing && transitionProgress.value >= 0.99f,
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxSize(),
                key = { pageIndex ->
                    val activeList = if (isStoryMode) currentStoryItems else items
                    val uri = activeList.getOrNull(pageIndex)?.uri ?: pageIndex.toString()
                    if (isStoryMode) "${storyState.activeStoryIndex}_$uri" else uri
                }
            ) { pageIndex ->
                val activeList = if (isStoryMode) currentStoryItems else items
                val currentItem = activeList.getOrNull(pageIndex) ?: item
                val isCurrent = pageIndex == pagerState.currentPage
                val currentBounds = if (isCurrent) (targetBounds ?: ThumbnailBoundsTracker.getBounds(currentItem.uri)) else null

                ZoomableMediaItem(
                    item = currentItem,
                    isCurrentPage = isCurrent,
                    isVideo = currentItem.isVideo,
                    videoState = if (currentItem.isVideo && currentItem.uri == item.uri) videoState else null,
                    touchSlop = viewConfig.scaledTouchSlop.toFloat(),
                    minFlingVelocity = minFlingVelocity,
                    onToggleControls = { if (!showDetails) onToggleControls() },
                    onLongPress = { if (!showDetails && !videoState.isFullscreen) showMore = true },
                    onSwipeUp = { if (!showDetails && !videoState.isFullscreen) onToggleDetails() },
                    onSwipeDownDelta = { deltaDown ->
                        if (!isCurrentZoomed && !showDetails && !videoState.isFullscreen && !isClosing) {
                            handleViewerSwipeDownDelta(deltaDown, screenHeightPx, dismissOffsetY, transitionProgress, coroutineScope)
                        }
                    },
                    onSwipeDownRelease = { velocityDown ->
                        if (!isCurrentZoomed && !showDetails && !videoState.isFullscreen && !isClosing) {
                            handleViewerSwipeDownRelease(
                                velocityDown = velocityDown,
                                screenHeightPx = screenHeightPx,
                                minFlingVelocity = minFlingVelocity,
                                itemUri = item.uri,
                                dismissOffsetY = dismissOffsetY,
                                transitionProgress = transitionProgress,
                                scope = coroutineScope,
                                onClosingStart = { isClosing = true },
                                onBack = onBack
                            )
                        }
                    },
                    onZoomChanged = { zoomed -> if (isCurrent) isCurrentZoomed = zoomed },
                    isStoryMode = isStoryMode,
                    onStoryTapLeft = { storyState.stepPrevious(pagerState, isClosing, coroutineScope) },
                    onStoryTapRight = { storyState.stepNext(pagerState, isClosing, coroutineScope) },
                    onStoryHoldingChanged = { storyState.isStoryHolding = it },
                    transitionProgress = transitionProgress.value,
                    targetBounds = currentBounds,
                    dismissOffsetY = dismissOffsetY.value
                )
            }

            ViewerChromeOverlays(
                item = item,
                items = items,
                customAlbums = customAlbums,
                customTags = customTags,
                defaultOcrLanguages = defaultOcrLanguages,
                defaultOcrModel = defaultOcrModel,
                defaultMlKitLanguages = defaultMlKitLanguages,
                favourite = favourite,
                isHidden = isHidden,
                isBin = isBin,
                isArchived = isArchived,
                showControls = showControls,
                showDetails = showDetails,
                isClosing = isClosing,
                transitionProgressValue = transitionProgress.value,
                videoState = videoState,
                storyState = storyState,
                currentPageIndex = pagerState.currentPage,
                onInitiateClose = initiateClose,
                onShare = shareMedia,
                onFavourite = onFavourite,
                onShowMore = { showMore = true },
                onToggleDetails = onToggleDetails,
                onToggleBinOrConfirm = { if (isBin) onToggleBin() else confirmMoveToBin = true },
                onToggleHide = onToggleHide,
                onToggleArchive = onToggleArchive,
                onAddTo = onAddTo,
                onRequestPermanentDelete = if (isBin && onDeletePermanently != null) { { confirmPermanentDelete = true } } else null,
                onOpenMap = onOpenMap,
                onAddTag = onAddTag,
                onOpenPerson = onOpenPerson,
                onReplayStory = { storyState.replay(pagerState, coroutineScope) }
            )
        }
    }

    ViewerDialogsAndSheets(
        itemName = item.name,
        isBin = isBin,
        isHidden = isHidden,
        isArchived = isArchived,
        showDetails = showDetails,
        showMore = showMore,
        confirmMoveToBin = confirmMoveToBin,
        confirmPermanentDelete = confirmPermanentDelete,
        hasPermanentDeleteAction = onDeletePermanently != null,
        onDismissMore = { showMore = false },
        onToggleBin = onToggleBin,
        onRequestMoveToBin = { confirmMoveToBin = true },
        onDismissMoveToBin = { confirmMoveToBin = false },
        onToggleHide = onToggleHide,
        onToggleArchive = onToggleArchive,
        onAddTo = onAddTo,
        onRequestPermanentDelete = { confirmPermanentDelete = true },
        onDismissPermanentDelete = { confirmPermanentDelete = false },
        onConfirmPermanentDelete = { onDeletePermanently?.invoke() },
        onToggleDetails = onToggleDetails
    )
}
