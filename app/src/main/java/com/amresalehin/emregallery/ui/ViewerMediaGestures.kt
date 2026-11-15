package com.amresalehin.emregallery.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Attaches coordinated viewer touch gestures:
 * pinch-to-zoom, pan clamping when zoomed, double-tap zoom toggle,
 * vertical swipe-up/swipe-down disambiguation, long-press, and story tap/hold zones.
 */
@Composable
internal fun Modifier.viewerMediaGestures(
    itemUri: String,
    isVideo: Boolean,
    isStoryMode: Boolean,
    containerWidth: Float,
    containerHeight: Float,
    touchSlop: Float,
    minFlingVelocity: Float,
    scaleState: MutableFloatState,
    offsetXState: MutableFloatState,
    offsetYState: MutableFloatState,
    onToggleControls: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDownDelta: (Float) -> Unit,
    onSwipeDownRelease: (Float) -> Unit,
    onStoryTapLeft: () -> Unit,
    onStoryTapRight: () -> Unit,
    onStoryHoldingChanged: (Boolean) -> Unit
): Modifier {
    val currentWidth by rememberUpdatedState(containerWidth)
    val currentHeight by rememberUpdatedState(containerHeight)
    val currentIsStoryMode by rememberUpdatedState(isStoryMode)
    val currentOnToggleControls by rememberUpdatedState(onToggleControls)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDownDelta by rememberUpdatedState(onSwipeDownDelta)
    val currentOnSwipeDownRelease by rememberUpdatedState(onSwipeDownRelease)
    val currentOnStoryTapLeft by rememberUpdatedState(onStoryTapLeft)
    val currentOnStoryTapRight by rememberUpdatedState(onStoryTapRight)
    val currentOnStoryHoldingChanged by rememberUpdatedState(onStoryHoldingChanged)

    return pointerInput(itemUri, isVideo) {
        var lastTapTime = 0L
        var lastTapPos = Offset.Zero

        coroutineScope {
            awaitEachGesture {
                val width = currentWidth
                val height = currentHeight
                val storyMode = currentIsStoryMode

                val down = awaitFirstDown(requireUnconsumed = false)
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                var isPinching = false
                var isVerticalSwipe = false
                var isHorizontalBrowse = false
                var swipeDirection = 0 // 1 = down (dismiss), -1 = up (drawer)
                var totalMovementX = 0f
                var totalMovementY = 0f
                var longPressTriggered = false

                val longPressJob = launch {
                    delay(if (storyMode) 180L else 500L)
                    if (!isPinching && scaleState.floatValue <= 1.02f) {
                        if (storyMode) {
                            currentOnStoryHoldingChanged(true)
                        } else {
                            longPressTriggered = true
                            currentOnLongPress()
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

                        val oldScale = scaleState.floatValue
                        val newScale = (oldScale * zoomChange).coerceIn(1f, 4.5f)

                        if (oldScale > 0f) {
                            val scaleRatio = newScale / oldScale
                            val focalX = centroid.x - width / 2f
                            val focalY = centroid.y - height / 2f
                            val newOffsetX = (offsetXState.floatValue - focalX) * scaleRatio + focalX + panChange.x
                            val newOffsetY = (offsetYState.floatValue - focalY) * scaleRatio + focalY + panChange.y

                            val maxX = (width * (newScale - 1f)) / 2f
                            val maxY = (height * (newScale - 1f)) / 2f

                            scaleState.floatValue = newScale
                            offsetXState.floatValue = newOffsetX.coerceIn(-maxX, maxX)
                            offsetYState.floatValue = newOffsetY.coerceIn(-maxY, maxY)
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
                            if (storyMode) currentOnStoryHoldingChanged(false)
                        }

                        if (scaleState.floatValue > 1.02f && !isVideo) {
                            val maxX = (width * (scaleState.floatValue - 1f)) / 2f
                            val maxY = (height * (scaleState.floatValue - 1f)) / 2f

                            offsetXState.floatValue = (offsetXState.floatValue + posChange.x).coerceIn(-maxX, maxX)
                            offsetYState.floatValue = (offsetYState.floatValue + posChange.y).coerceIn(-maxY, maxY)
                            change.consume()
                        } else {
                            if (!isVerticalSwipe && !isHorizontalBrowse) {
                                val dist = hypot(totalMovementX, totalMovementY)
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
                                currentOnSwipeDownDelta(posChange.y)
                            }
                        }
                    } else {
                        break
                    }
                }

                longPressJob.cancel()
                if (storyMode) currentOnStoryHoldingChanged(false)

                val velocityY = velocityTracker.calculateVelocity().y
                if (isVerticalSwipe) {
                    if (swipeDirection < 0) {
                        if (velocityY < -minFlingVelocity || totalMovementY > touchSlop * 2f) {
                            currentOnSwipeUp()
                        }
                    } else {
                        currentOnSwipeDownRelease(velocityY)
                    }
                } else if (!isPinching && !longPressTriggered && totalMovementX < touchSlop && totalMovementY < touchSlop) {
                    if (storyMode) {
                        if (down.position.x < width * 0.30f) {
                            currentOnStoryTapLeft()
                        } else {
                            currentOnStoryTapRight()
                        }
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 280L && (down.position - lastTapPos).getDistance() < touchSlop * 3f && !isVideo) {
                            lastTapTime = 0L
                            if (scaleState.floatValue > 1.2f) {
                                launch {
                                    val startScale = scaleState.floatValue
                                    val startX = offsetXState.floatValue
                                    val startY = offsetYState.floatValue
                                    animate(0f, 1f, animationSpec = tween(200)) { fraction, _ ->
                                        scaleState.floatValue = startScale + (1f - startScale) * fraction
                                        offsetXState.floatValue = startX * (1f - fraction)
                                        offsetYState.floatValue = startY * (1f - fraction)
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

                                    val startScale = scaleState.floatValue
                                    val startX = offsetXState.floatValue
                                    val startY = offsetYState.floatValue
                                    animate(0f, 1f, animationSpec = tween(220)) { fraction, _ ->
                                        scaleState.floatValue = startScale + (targetScale - startScale) * fraction
                                        offsetXState.floatValue = startX + (targetOffsetX - startX) * fraction
                                        offsetYState.floatValue = startY + (targetOffsetY - startY) * fraction
                                    }
                                }
                            }
                        } else {
                            lastTapTime = now
                            lastTapPos = down.position
                            launch {
                                delay(280L)
                                if (lastTapTime == now) {
                                    currentOnToggleControls()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
