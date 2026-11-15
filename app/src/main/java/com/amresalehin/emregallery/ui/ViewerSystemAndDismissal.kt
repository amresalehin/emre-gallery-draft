package com.amresalehin.emregallery.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal val ViewerReturnSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 650f
)

/**
 * Hides system bars during fullscreen video playback and restores them on exit or disposal.
 */
@Composable
internal fun ViewerSystemBarsImmersion(isFullscreen: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(isFullscreen, activity) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

/**
 * Registers priority-ordered Android back and predictive-back handlers for the viewer:
 * 1. Exit video fullscreen mode
 * 2. Scrubbed predictive-back shared-element return transition
 * 3. Standard animated return close
 */
@Composable
internal fun ViewerBackHandlers(
    itemUri: String,
    videoState: ViewerVideoState,
    showDetails: Boolean,
    isClosing: Boolean,
    transitionProgress: Animatable<Float, AnimationVector1D>,
    onClosingStart: () -> Unit,
    onInitiateClose: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = videoState.isFullscreen) {
        videoState.isFullscreen = false
    }

    PredictiveBackHandler(enabled = !videoState.isFullscreen && !showDetails && !isClosing) { progressFlow: Flow<BackEventCompat> ->
        try {
            ThumbnailBoundsTracker.activeViewerUri = itemUri
            progressFlow.collect { backEvent ->
                transitionProgress.snapTo((1f - backEvent.progress).coerceIn(0f, 1f))
            }
            onClosingStart()
            transitionProgress.animateTo(
                targetValue = 0f,
                animationSpec = ViewerReturnSpringSpec
            )
            ThumbnailBoundsTracker.activeViewerUri = null
            onBack()
        } catch (e: CancellationException) {
            transitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = ViewerReturnSpringSpec
            )
        }
    }

    BackHandler(enabled = !videoState.isFullscreen && !showDetails && !isClosing) {
        onInitiateClose()
    }
}

internal fun handleViewerSwipeDownDelta(
    deltaDown: Float,
    screenHeightPx: Float,
    dismissOffsetY: Animatable<Float, AnimationVector1D>,
    transitionProgress: Animatable<Float, AnimationVector1D>,
    scope: CoroutineScope
) {
    scope.launch {
        val next = (dismissOffsetY.value + deltaDown).coerceAtLeast(0f)
        dismissOffsetY.snapTo(next)
        val p = 1f - (next / (screenHeightPx * 0.45f)).coerceIn(0f, 0.40f)
        transitionProgress.snapTo(p)
    }
}

internal fun handleViewerSwipeDownRelease(
    velocityDown: Float,
    screenHeightPx: Float,
    minFlingVelocity: Float,
    itemUri: String,
    dismissOffsetY: Animatable<Float, AnimationVector1D>,
    transitionProgress: Animatable<Float, AnimationVector1D>,
    scope: CoroutineScope,
    onClosingStart: () -> Unit,
    onBack: () -> Unit
) {
    val dismissDistanceThreshold = screenHeightPx * 0.12f
    if (dismissOffsetY.value > dismissDistanceThreshold || velocityDown > minFlingVelocity * 1.2f) {
        scope.launch {
            onClosingStart()
            ThumbnailBoundsTracker.activeViewerUri = itemUri
            val progressVelocity = if (screenHeightPx > 0f) {
                -(velocityDown / (screenHeightPx * 0.45f)).coerceIn(0f, 15f)
            } else {
                0f
            }
            transitionProgress.animateTo(
                targetValue = 0f,
                initialVelocity = progressVelocity,
                animationSpec = ViewerReturnSpringSpec
            )
            ThumbnailBoundsTracker.activeViewerUri = null
            onBack()
        }
    } else {
        scope.launch {
            launch {
                dismissOffsetY.animateTo(
                    targetValue = 0f,
                    initialVelocity = velocityDown,
                    animationSpec = ViewerReturnSpringSpec
                )
            }
            transitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = ViewerReturnSpringSpec
            )
        }
    }
}

internal fun shareViewerMediaItem(context: Context, item: MediaItem) {
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = item.mimeType ?: if (item.isVideo) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Share media"
            )
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
