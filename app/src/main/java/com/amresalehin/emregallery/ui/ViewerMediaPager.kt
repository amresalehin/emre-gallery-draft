package com.amresalehin.emregallery.ui
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ViewerHorizontalMediaPager(
    item: MediaItem,
    items: List<MediaItem>,
    isStoryMode: Boolean,
    currentStoryItems: List<MediaItem>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    videoState: ViewerVideoState,
    touchSlop: Float,
    minFlingVelocity: Float,
    showDetails: Boolean,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onToggleDetails: () -> Unit,
    onShowMore: () -> Unit,
    onIsCurrentZoomedChange: (Boolean) -> Unit,
    stepPrevious: () -> Unit,
    stepNext: () -> Unit,
    onIsStoryHoldingChange: (Boolean) -> Unit,
    transitionProgress: androidx.compose.animation.core.Animatable<Float, *>,
    targetBounds: androidx.compose.ui.geometry.Rect?,
    dismissOffsetY: androidx.compose.animation.core.Animatable<Float, *>,
    screenHeightPx: Float,
    initiateClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
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
                    if (isStoryMode) "${activeStoryIndex}_$uri" else uri
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
                    onToggleControls = {
                        if (!showDetails) onToggleControls()
                    },
                    onLongPress = {
                        if (!showDetails && !videoState.isFullscreen) onShowMore()
                    },
                    onSwipeUp = {
                        if (!showDetails && !videoState.isFullscreen) {
                            onToggleDetails()
                        }
                    },
                    onSwipeDownDelta = { deltaDown ->
                        if (!isCurrentZoomed && !showDetails && !videoState.isFullscreen && !isClosing) {
                            coroutineScope.launch {
                                val next = (dismissOffsetY.value + deltaDown).coerceAtLeast(0f)
                                dismissOffsetY.snapTo(next)
                                val p = (1f - (next / (screenHeightPx * 0.45f)).coerceIn(0f, 0.40f))
                                transitionProgress.snapTo(p)
                            }
                        }
                    },
                    onSwipeDownRelease = { velocityDown ->
                        if (!isCurrentZoomed && !showDetails && !videoState.isFullscreen && !isClosing) {
                            val dismissDistanceThreshold = screenHeightPx * 0.12f
                            if (dismissOffsetY.value > dismissDistanceThreshold || velocityDown > minFlingVelocity * 1.2f) {
                                coroutineScope.launch {
                                    isClosing = true
                                    ThumbnailBoundsTracker.activeViewerUri = item.uri
                                    val progressVelocity = if (screenHeightPx > 0f) {
                                        -(velocityDown / (screenHeightPx * 0.45f)).coerceIn(0f, 15f)
                                    } else 0f
                                    transitionProgress.animateTo(
                                        targetValue = 0f,
                                        initialVelocity = progressVelocity,
                                        animationSpec = ReturnSpringSpec
                                    )
                                    ThumbnailBoundsTracker.activeViewerUri = null
                                    onBack()
                                }
                            } else {
                                coroutineScope.launch {
                                    launch {
                                        dismissOffsetY.animateTo(
                                            targetValue = 0f,
                                            initialVelocity = velocityDown,
                                            animationSpec = ReturnSpringSpec
                                        )
                                    }
                                    transitionProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = ReturnSpringSpec
                                    )
                                }
                            }
                        }
                    },
                    onZoomChanged = { zoomed ->
                        if (isCurrent) onIsCurrentZoomedChange(zoomed)
                    },
                    isStoryMode = isStoryMode,
                    onStoryTapLeft = stepPrevious,
                    onStoryTapRight = stepNext,
                    onStoryHoldingChanged = { isStoryHolding = it },
                    transitionProgress = transitionProgress.value,
                    targetBounds = currentBounds,
                    dismissOffsetY = dismissOffsetY.value
                )
            }
}
