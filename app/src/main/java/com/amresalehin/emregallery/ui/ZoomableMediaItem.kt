package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaItem
import kotlin.math.roundToInt

/**
 * Individual media page supporting pinch-to-zoom, pan while zoomed,
 * double-tap to zoom in/reset, and vertical upward/downward gestures when unzoomed.
 */
@Composable
internal fun ZoomableMediaItem(
    item: MediaItem,
    isCurrentPage: Boolean,
    isVideo: Boolean,
    videoState: ViewerVideoState?,
    touchSlop: Float,
    minFlingVelocity: Float,
    onToggleControls: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDownDelta: (Float) -> Unit,
    onSwipeDownRelease: (Float) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    isStoryMode: Boolean = false,
    onStoryTapLeft: () -> Unit = {},
    onStoryTapRight: () -> Unit = {},
    onStoryHoldingChanged: (Boolean) -> Unit = {},
    transitionProgress: Float = 1f,
    targetBounds: Rect? = null,
    dismissOffsetY: Float = 0f,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scaleState = remember(item.uri) { mutableFloatStateOf(1f) }
    val offsetXState = remember(item.uri) { mutableFloatStateOf(0f) }
    val offsetYState = remember(item.uri) { mutableFloatStateOf(0f) }

    var intrinsicWidth by remember(item.uri) { mutableIntStateOf(item.width) }
    var intrinsicHeight by remember(item.uri) { mutableIntStateOf(item.height) }

    // Keep video posters on the same cached MediaStore thumbnail used by the grid.
    val context = LocalContext.current
    val imageRequest = remember(item.uri, isVideo, context) {
        ThumbnailUtils.buildViewerRequest(context, item.uri, isVideo)
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scaleState.floatValue = 1f
            offsetXState.floatValue = 0f
            offsetYState.floatValue = 0f
            onZoomChanged(false)
        }
    }

    LaunchedEffect(scaleState.floatValue) {
        onZoomChanged(scaleState.floatValue > 1.05f)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .viewerMediaGestures(
                    itemUri = item.uri,
                    isVideo = isVideo,
                    isStoryMode = isStoryMode,
                    containerWidth = containerWidth,
                    containerHeight = containerHeight,
                    touchSlop = touchSlop,
                    minFlingVelocity = minFlingVelocity,
                    scaleState = scaleState,
                    offsetXState = offsetXState,
                    offsetYState = offsetYState,
                    onToggleControls = onToggleControls,
                    onLongPress = onLongPress,
                    onSwipeUp = onSwipeUp,
                    onSwipeDownDelta = onSwipeDownDelta,
                    onSwipeDownRelease = onSwipeDownRelease,
                    onStoryTapLeft = onStoryTapLeft,
                    onStoryTapRight = onStoryTapRight,
                    onStoryHoldingChanged = onStoryHoldingChanged
                ),
            contentAlignment = Alignment.Center
        ) {
            val fitted = computeFittedMediaDimensions(
                intrinsicWidth = intrinsicWidth,
                intrinsicHeight = intrinsicHeight,
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                targetBounds = targetBounds
            )
            val fittedWidthDp = if (fitted.widthPx > 0f) with(density) { fitted.widthPx.toDp() } else 0.dp
            val fittedHeightDp = if (fitted.heightPx > 0f) with(density) { fitted.heightPx.toDp() } else 0.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleState.floatValue
                        scaleY = scaleState.floatValue
                        translationX = offsetXState.floatValue
                        translationY = offsetYState.floatValue
                    },
                contentAlignment = Alignment.Center
            ) {
                val fittedModifier = if (fitted.widthPx > 0f && fitted.heightPx > 0f) {
                    Modifier.size(fittedWidthDp, fittedHeightDp)
                } else {
                    Modifier.fillMaxSize()
                }

                Box(
                    modifier = fittedModifier.viewerSharedElementTransition(
                        transitionProgress = transitionProgress,
                        isCurrentPage = isCurrentPage,
                        targetBounds = targetBounds,
                        fittedWidthPx = fitted.widthPx,
                        fittedHeightPx = fitted.heightPx,
                        containerWidthPx = containerWidth,
                        containerHeightPx = containerHeight,
                        dismissOffsetY = dismissOffsetY
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.name,
                        onSuccess = { state ->
                            val d = state.result.drawable
                            val w = if (d.intrinsicWidth > 0) d.intrinsicWidth.toFloat() else state.painter.intrinsicSize.width
                            val h = if (d.intrinsicHeight > 0) d.intrinsicHeight.toFloat() else state.painter.intrinsicSize.height
                            if (w > 0f && h > 0f && !w.isNaN() && !h.isNaN()) {
                                intrinsicWidth = w.roundToInt()
                                intrinsicHeight = h.roundToInt()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    if (isVideo && videoState != null) {
                        VideoPlayerSurface(videoState = videoState)
                    }
                }
            }
        }
    }
}
