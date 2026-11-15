@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amresalehin.emregallery.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

/**
 * AndroidView host for ExoPlayer rendering within the HorizontalPager.
 */
@Composable
fun VideoPlayerSurface(
    videoState: ViewerVideoState,
    modifier: Modifier = Modifier
) {
    if (!videoState.isVideo || videoState.player == null) return

    // Keep the cached poster underneath and fade the first rendered video frame in over it.
    // SurfaceView alpha is supported on API 26+, which is this app's minimum Android version.
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (videoState.isFirstFrameRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "video_frame_handoff"
    )

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = videoState.player
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isClickable = false
                isFocusable = false
            }
        },
        modifier = modifier
            .fillMaxSize()
            .alpha(surfaceAlpha),
        update = { pv ->
            if (pv.player != videoState.player) {
                pv.player = videoState.player
            }
            pv.useController = false
            pv.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            pv.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    )
}
