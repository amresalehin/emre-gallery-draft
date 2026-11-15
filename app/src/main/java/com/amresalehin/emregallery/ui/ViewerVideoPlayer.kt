@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amresalehin.emregallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Manages the ExoPlayer lifecycle and produces a [ViewerVideoState].
 * Applies [sessionPlayIntent] when creating and preparing new media items.
 */
@Composable
fun rememberViewerVideoState(
    uri: String,
    isVideo: Boolean,
    showControls: Boolean,
    sessionPlayIntent: Boolean = true,
    onPlayIntentChange: (Boolean) -> Unit = {}
): ViewerVideoState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(uri, isVideo) {
        if (isVideo) {
            createViewerExoPlayer(
                context = context,
                uri = uri,
                sessionPlayIntent = sessionPlayIntent
            )
        } else {
            null
        }
    }

    val state = remember(uri, isVideo) {
        ViewerVideoState(
            player = player,
            isVideo = isVideo,
            initialPlayIntent = sessionPlayIntent,
            onPlayIntentChange = onPlayIntentChange
        )
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                state.isFirstFrameRendered = true
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                state.isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    state.durationMs = player?.duration?.takeIf { it > 0L } ?: 0L
                }
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.stop()
            player?.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(player, uri, showControls) {
        if (!showControls) return@LaunchedEffect
        while (player != null && isActive) {
            state.positionMs = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            if (d > 0L && d != state.durationMs) {
                state.durationMs = d
            }
            delay(250)
        }
    }

    return state
}
