package com.amresalehin.emregallery.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.StoryGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages Memories story progression, hold-to-pause state, multi-story group transitions,
 * and end-of-story constellation state for [ViewerScreen].
 */
@Stable
internal class ViewerStoryState(
    val isStoryMode: Boolean,
    private val storyGroups: List<StoryGroup>,
    private val storyTitle: String?,
    private val fallbackItems: List<MediaItem>,
    initialStoryIndex: Int
) {
    var activeStoryIndex by mutableIntStateOf(
        initialStoryIndex.coerceIn(0, (storyGroups.size - 1).coerceAtLeast(0))
    )
    var storyProgress by mutableFloatStateOf(0f)
    var isStoryHolding by mutableStateOf(false)
    var showEndOfStory by mutableStateOf(false)

    val currentStory: StoryGroup
        get() = if (storyGroups.isNotEmpty()) {
            storyGroups.getOrElse(activeStoryIndex.coerceIn(0, storyGroups.size - 1)) {
                StoryGroup(storyTitle ?: "", fallbackItems)
            }
        } else {
            StoryGroup(storyTitle ?: "", fallbackItems)
        }

    val currentStoryTitle: String
        get() = currentStory.title

    val currentStoryItems: List<MediaItem>
        get() = currentStory.items.ifEmpty { fallbackItems }

    val allStoryItems: List<MediaItem>
        get() = if (storyGroups.isNotEmpty()) {
            storyGroups.flatMap { it.items }.ifEmpty { fallbackItems }
        } else {
            fallbackItems
        }

    fun stepNext(
        pagerState: PagerState,
        isClosing: Boolean,
        scope: CoroutineScope
    ) {
        if (showEndOfStory || isClosing || pagerState.isScrollInProgress) return
        val activeList = if (isStoryMode) currentStoryItems else fallbackItems
        if (pagerState.currentPage < activeList.size - 1) {
            val targetPage = pagerState.currentPage + 1
            storyProgress = 0f
            scope.launch {
                pagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                )
            }
        } else if (isStoryMode && activeStoryIndex < storyGroups.size - 1) {
            activeStoryIndex++
        } else {
            showEndOfStory = true
        }
    }

    fun stepPrevious(
        pagerState: PagerState,
        isClosing: Boolean,
        scope: CoroutineScope
    ) {
        if (showEndOfStory || isClosing || pagerState.isScrollInProgress) return
        if (pagerState.currentPage > 0) {
            val targetPage = pagerState.currentPage - 1
            storyProgress = 0f
            scope.launch {
                pagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                )
            }
        } else if (isStoryMode && activeStoryIndex > 0) {
            val prevStoryIndex = activeStoryIndex - 1
            val prevStoryItems = storyGroups[prevStoryIndex].items
            activeStoryIndex = prevStoryIndex
            storyProgress = 0f
            scope.launch {
                pagerState.scrollToPage((prevStoryItems.size - 1).coerceAtLeast(0))
            }
        }
    }

    fun replay(pagerState: PagerState, scope: CoroutineScope) {
        showEndOfStory = false
        activeStoryIndex = 0
        storyProgress = 0f
        scope.launch {
            pagerState.scrollToPage(0)
        }
    }
}

@Composable
internal fun rememberViewerStoryState(
    storyTitle: String?,
    storyGroups: List<StoryGroup>,
    initialStoryIndex: Int,
    items: List<MediaItem>
): ViewerStoryState {
    val isStoryMode = storyTitle != null || storyGroups.isNotEmpty()
    return remember(storyTitle, storyGroups, initialStoryIndex, items) {
        ViewerStoryState(
            isStoryMode = isStoryMode,
            storyGroups = storyGroups,
            storyTitle = storyTitle,
            fallbackItems = items,
            initialStoryIndex = initialStoryIndex
        )
    }
}

@Composable
internal fun ViewerStoryEffects(
    storyState: ViewerStoryState,
    pagerState: PagerState,
    item: MediaItem,
    videoState: ViewerVideoState,
    isClosing: Boolean,
    isExternalModalPaused: Boolean,
    onOpenNeighbor: (MediaItem) -> Unit,
    onStepNext: () -> Unit
) {
    LaunchedEffect(storyState.activeStoryIndex) {
        if (storyState.isStoryMode) {
            storyState.storyProgress = 0f
            if (pagerState.currentPage != 0) {
                pagerState.scrollToPage(0)
            }
            val first = storyState.currentStoryItems.firstOrNull()
            if (first != null && first.uri != item.uri) {
                onOpenNeighbor(first)
                ThumbnailBoundsTracker.activeViewerUri = first.uri
            }
        }
    }

    if (storyState.isStoryMode) {
        val currentActiveItem = storyState.currentStoryItems.getOrNull(pagerState.currentPage) ?: item
        val isCurrentItemVideo = currentActiveItem.isVideo
        val isPaused = storyState.isStoryHolding ||
            isExternalModalPaused ||
            isClosing ||
            storyState.showEndOfStory ||
            (isCurrentItemVideo && !videoState.isPlaying)

        LaunchedEffect(
            pagerState.settledPage,
            storyState.activeStoryIndex,
            isPaused,
            isCurrentItemVideo,
            videoState.durationMs,
            videoState.positionMs,
            videoState.isPlaying
        ) {
            if (storyState.showEndOfStory || isClosing || pagerState.isScrollInProgress) return@LaunchedEffect

            if (isCurrentItemVideo) {
                if (videoState.durationMs > 0L) {
                    storyState.storyProgress = (videoState.positionMs.toFloat() / videoState.durationMs.toFloat()).coerceIn(0f, 1f)
                    if (videoState.positionMs >= videoState.durationMs - 150L && videoState.durationMs > 500L) {
                        onStepNext()
                    }
                }
            } else if (!isPaused) {
                val totalDurationMs = 4500f
                val startProgress = storyState.storyProgress
                val startTime = System.currentTimeMillis()
                while (!isPaused && storyState.storyProgress < 1f) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val newProgress = (startProgress + elapsed / totalDurationMs).coerceIn(0f, 1f)
                    storyState.storyProgress = newProgress
                    if (newProgress >= 1f) {
                        onStepNext()
                        break
                    }
                    delay(16L)
                }
            }
        }
    }
}
