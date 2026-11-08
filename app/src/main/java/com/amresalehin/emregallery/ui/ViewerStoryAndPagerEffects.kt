package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.amresalehin.emregallery.data.StoryGroup
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


internal data class ViewerStoryStepCallbacks(
    val stepNext: () -> Unit,
    val stepPrevious: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberViewerStoryAndPagerEffects(
    item: MediaItem,
    items: List<MediaItem>,
    isStoryMode: Boolean,
    storyGroups: List<StoryGroup>,
    activeStoryIndex: Int,
    onActiveStoryIndexChange: (Int) -> Unit,
    currentStoryItems: List<MediaItem>,
    storyProgress: Float,
    onStoryProgressChange: (Float) -> Unit,
    isStoryHolding: Boolean,
    showEndOfStory: Boolean,
    onShowEndOfStoryChange: (Boolean) -> Unit,
    isClosing: Boolean,
    showDetails: Boolean,
    showMore: Boolean,
    confirmMoveToBin: Boolean,
    confirmPermanentDelete: Boolean,
    isCurrentZoomed: Boolean,
    pagerState: androidx.compose.foundation.pager.PagerState,
    videoState: ViewerVideoState,
    onOpenNeighbor: (MediaItem) -> Unit
): ViewerStoryStepCallbacks {
    val coroutineScope = rememberCoroutineScope()
    // When moving between story groups, reset pager position and notify neighbor
    LaunchedEffect(activeStoryIndex) {
        if (isStoryMode) {
            onStoryProgressChange(0f)
            if (pagerState.currentPage != 0) {
                pagerState.scrollToPage(0)
            }
            val first = currentStoryItems.firstOrNull()
            if (first != null && first.uri != item.uri) {
                onOpenNeighbor(first)
                ThumbnailBoundsTracker.activeViewerUri = first.uri
            }
        }
    }

    // Synchronize pager page settlement with parent selected media only when page has completely settled
    LaunchedEffect(pagerState, currentStoryItems) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            onStoryProgressChange(0f)
            val activeList = if (isStoryMode) currentStoryItems else items
            val current = activeList.getOrNull(settledPage)
            if (current != null && current.uri != item.uri) {
                onOpenNeighbor(current)
                ThumbnailBoundsTracker.activeViewerUri = current.uri
            }
        }
    }

    val stepNext: () -> Unit = {
        if (!showEndOfStory && !isClosing && !pagerState.isScrollInProgress) {
            val activeList = if (isStoryMode) currentStoryItems else items
            if (pagerState.currentPage < activeList.size - 1) {
                val targetPage = pagerState.currentPage + 1
                onStoryProgressChange(0f)
                coroutineScope.launch {
                    pagerState.animateScrollToPage(
                        page = targetPage,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                    )
                }
            } else {
                // Reached last photo of current story. Check if more stories exist in sequence
                if (isStoryMode && activeStoryIndex < storyGroups.size - 1) {
                    onActiveStoryIndexChange(activeStoryIndex + 1)
                } else {
                    // All memories have finished playing
                    onShowEndOfStoryChange(true)
                }
            }
        }
    }

    val stepPrevious: () -> Unit = {
        if (!showEndOfStory && !isClosing && !pagerState.isScrollInProgress) {
            if (pagerState.currentPage > 0) {
                val targetPage = pagerState.currentPage - 1
                onStoryProgressChange(0f)
                coroutineScope.launch {
                    pagerState.animateScrollToPage(
                        page = targetPage,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                    )
                }
            } else {
                // At first photo of current story: return to previous story's last photo if available
                if (isStoryMode && activeStoryIndex > 0) {
                    val prevStoryIndex = activeStoryIndex - 1
                    val prevStoryItems = storyGroups[prevStoryIndex].items
                    onActiveStoryIndexChange(prevStoryIndex)
                    onStoryProgressChange(0f)
                    coroutineScope.launch {
                        pagerState.scrollToPage((prevStoryItems.size - 1).coerceAtLeast(0))
                    }
                }
            }
        }
    }

    // Auto-advance story timer (~4.5s per photo, synchronized with video playback for videos)
    if (isStoryMode) {
        val currentMediaList = currentStoryItems
        val currentActiveItem = currentMediaList.getOrNull(pagerState.currentPage) ?: item
        val isCurrentItemVideo = currentActiveItem.isVideo
        val isPaused = isStoryHolding || showDetails || showMore || confirmMoveToBin || confirmPermanentDelete || isCurrentZoomed || isClosing || showEndOfStory || (isCurrentItemVideo && !videoState.isPlaying)

        LaunchedEffect(
            pagerState.settledPage,
            activeStoryIndex,
            isPaused,
            isCurrentItemVideo,
            videoState.durationMs,
            videoState.positionMs,
            videoState.isPlaying
        ) {
            if (showEndOfStory || isClosing || pagerState.isScrollInProgress) return@LaunchedEffect

            if (isCurrentItemVideo) {
                if (videoState.durationMs > 0L) {
                    onStoryProgressChange((videoState.positionMs.toFloat() / videoState.durationMs.toFloat()).coerceIn(0f, 1f))
                    if (videoState.positionMs >= videoState.durationMs - 150L && videoState.durationMs > 500L) {
                        stepNext()
                    }
                }
            } else {
                if (!isPaused) {
                    val totalDurationMs = 4500f
                    val startProgress = storyProgress
                    val startTime = System.currentTimeMillis()
                    while (!isPaused && storyProgress < 1f) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val newProgress = (startProgress + elapsed / totalDurationMs).coerceIn(0f, 1f)
                        onStoryProgressChange(newProgress)
                        if (newProgress >= 1f) {
                            stepNext()
                            break
                        }
                        delay(16L)
                    }
                }
            }
        }
    }

    // Scroll to target page if parent changes selected media (non-story mode only)
    LaunchedEffect(item.uri) {
        if (!isStoryMode && !pagerState.isScrollInProgress) {
            val targetIndex = items.indexOfFirst { it.uri == item.uri }
            if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
                pagerState.scrollToPage(targetIndex)
            }
        }
    }
    return ViewerStoryStepCallbacks(stepNext = stepNext, stepPrevious = stepPrevious)
}
