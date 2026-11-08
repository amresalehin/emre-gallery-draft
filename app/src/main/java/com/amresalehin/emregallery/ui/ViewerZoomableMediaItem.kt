package com.amresalehin.emregallery.ui
import androidx.compose.animation.*
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    targetBounds: androidx.compose.ui.geometry.Rect? = null,
    dismissOffsetY: Float = 0f,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var scale by remember(item.uri) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.uri) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.uri) { mutableFloatStateOf(0f) }
    var intrinsicWidth by remember(item.uri) { mutableIntStateOf(item.width) }
    var intrinsicHeight by remember(item.uri) { mutableIntStateOf(item.height) }
    val context = LocalContext.current
    val imageRequest = remember(item.uri, isVideo, context) {
        ThumbnailUtils.buildViewerRequest(context, item.uri, isVideo)
    }
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            onZoomChanged(false)
        }
    }
    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.05f)
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
                .pointerInput(item.uri, isVideo) {
                    val width = containerWidth
                    val height = containerHeight
                    var lastTapTime = 0L
                    var lastTapPos = Offset.Zero
                    coroutineScope {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)
                            var isPinching = false
                            var isDragging = false
                            var isVerticalSwipe = false
                            var isHorizontalBrowse = false
                            var swipeDirection = 0 // 1 = down (dismiss), -1 = up (drawer)
                            var totalMovementX = 0f
                            var totalMovementY = 0f
                            var longPressTriggered = false
                            val longPressJob = launch {
                                delay(if (isStoryMode) 180L else 500L)
                                if (!isDragging && !isPinching && scale <= 1.02f) {
                                    if (isStoryMode) {
                                        onStoryHoldingChanged(true)
                                    } else {
                                        longPressTriggered = true
                                        onLongPress()
                                    }
                                }
                            }
                            while (true) {
                                val event = awaitPointerEvent()
                                val pointerCount = event.changes.size
                                if (pointerCount >= 2 && !isVideo) {
                                    isPinching = true
                                    longPressJob.cancel()
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid()
                                    val oldScale = scale
                                    val newScale = (oldScale * zoomChange).coerceIn(1f, 4.5f)
                                    if (oldScale > 0f) {
                                        val scaleRatio = newScale / oldScale
                                        val focalX = centroid.x - width / 2f
                                        val focalY = centroid.y - height / 2f
                                        val newOffsetX = (offsetX - focalX) * scaleRatio + focalX + panChange.x
                                        val newOffsetY = (offsetY - focalY) * scaleRatio + focalY + panChange.y
                                        val maxX = (width * (newScale - 1f)) / 2f
                                        val maxY = (height * (newScale - 1f)) / 2f
                                        scale = newScale
                                        offsetX = newOffsetX.coerceIn(-maxX, maxX)
                                        offsetY = newOffsetY.coerceIn(-maxY, maxY)
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (pointerCount == 1) {
                                    val change = event.changes.first()
                                    if (!change.pressed) break
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val posChange = change.positionChange()
                                    totalMovementX += abs(posChange.x)
                                    totalMovementY += abs(posChange.y)
                                    if (totalMovementX > touchSlop || totalMovementY > touchSlop) {
                                        longPressJob.cancel()
                                        if (isStoryMode) onStoryHoldingChanged(false)
                                    }
                                    if (scale > 1.02f && !isVideo) {
                                        val maxX = (width * (scale - 1f)) / 2f
                                        val maxY = (height * (scale - 1f)) / 2f
                                        offsetX = (offsetX + posChange.x).coerceIn(-maxX, maxX)
                                        offsetY = (offsetY + posChange.y).coerceIn(-maxY, maxY)
                                        change.consume()
                                    } else {
                                        if (!isVerticalSwipe && !isHorizontalBrowse) {
                                            val dist = kotlin.math.hypot(totalMovementX, totalMovementY)
                                            if (dist > touchSlop) {
                                                if (totalMovementX >= totalMovementY) {
                                                    isHorizontalBrowse = true
                                                } else {
                                                    isVerticalSwipe = true
                                                    swipeDirection = if (posChange.y < 0) -1 else 1
                                                }
                                            }
                                        }
                                        if (isVerticalSwipe) {
                                            change.consume()
                                            onSwipeDownDelta(posChange.y)
                                        }
                                    }
                                } else {
                                    break
                                }
                            }
                            longPressJob.cancel()
                            if (isStoryMode) onStoryHoldingChanged(false)
                            val velocityY = velocityTracker.calculateVelocity().y
                            if (isVerticalSwipe) {
                                if (swipeDirection < 0) {
                                    if (velocityY < -minFlingVelocity || totalMovementY > touchSlop * 2f) {
                                        onSwipeUp()
                                    }
                                } else {
                                    onSwipeDownRelease(velocityY)
                                }
                            } else if (!isPinching && !longPressTriggered && totalMovementX < touchSlop && totalMovementY < touchSlop) {
                                if (isStoryMode) {
                                    if (down.position.x < width * 0.30f) {
                                        onStoryTapLeft()
                                    } else {
                                        onStoryTapRight()
                                    }
                                } else {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTime < 280L && (down.position - lastTapPos).getDistance() < touchSlop * 3f && !isVideo) {
                                        lastTapTime = 0L
                                        if (scale > 1.2f) {
                                            launch {
                                                val startScale = scale
                                                val startX = offsetX
                                                val startY = offsetY
                                                animate(0f, 1f, animationSpec = tween(200)) { fraction, _ ->
                                                    scale = startScale + (1f - startScale) * fraction
                                                    offsetX = startX * (1f - fraction)
                                                    offsetY = startY * (1f - fraction)
                                                }
                                            }
                                        } else {
                                            launch {
                                                val targetScale = 2.5f
                                                val focalX = down.position.x - width / 2f
                                                val focalY = down.position.y - height / 2f
                                                val maxX = (width * (targetScale - 1f)) / 2f
                                                val maxY = (height * (targetScale - 1f)) / 2f
                                                val targetOffsetX = (-focalX * (targetScale - 1f)).coerceIn(-maxX, maxX)
                                                val targetOffsetY = (-focalY * (targetScale - 1f)).coerceIn(-maxY, maxY)
                                                val startScale = scale
                                                val startX = offsetX
                                                val startY = offsetY
                                                animate(0f, 1f, animationSpec = tween(220)) { fraction, _ ->
                                                    scale = startScale + (targetScale - startScale) * fraction
                                                    offsetX = startX + (targetOffsetX - startX) * fraction
                                                    offsetY = startY + (targetOffsetY - startY) * fraction
                                                }
                                            }
                                        }
                                    } else {
                                        lastTapTime = now
                                        lastTapPos = down.position
                                        launch {
                                            delay(280L)
                                            if (lastTapTime == now) {
                                                onToggleControls()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            ZoomableFittedMediaContent(
                item = item,
                imageRequest = imageRequest,
                isCurrentPage = isCurrentPage,
                isVideo = isVideo,
                videoState = videoState,
                intrinsicWidth = intrinsicWidth,
                intrinsicHeight = intrinsicHeight,
                onIntrinsicSizeChange = { w, h ->
                    intrinsicWidth = w
                    intrinsicHeight = h
                },
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                transitionProgress = transitionProgress,
                targetBounds = targetBounds,
                dismissOffsetY = dismissOffsetY
            )
        }
    }
}
