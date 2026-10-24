package com.amresalehin.emregallery.ui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * State holder for video playback in the media viewer.
 */
@Stable
class ViewerVideoState(
    val player: ExoPlayer?,
    val isVideo: Boolean
) {
    var isFirstFrameRendered by mutableStateOf(false)
    var isPlaying by mutableStateOf(true)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var isMuted by mutableStateOf(false)

    fun togglePlayPause() {
        if (player == null) return
        if (isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(position: Long) {
        positionMs = position
        player?.seekTo(position)
    }

    fun toggleMute() {
        val newMuted = !isMuted
        isMuted = newMuted
        player?.volume = if (newMuted) 0f else 1f
    }
}

/**
 * Manages the ExoPlayer lifecycle and produces a [ViewerVideoState].
 */
@Composable
fun rememberViewerVideoState(
    uri: String,
    isVideo: Boolean,
    showControls: Boolean
): ViewerVideoState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(uri, isVideo) {
        if (isVideo) {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 1000,
                    /* maxBufferMs = */ 3000,
                    /* bufferForPlaybackMs = */ 100,
                    /* bufferForPlaybackAfterRebufferMs = */ 250
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build().apply {
                    setMediaItem(ExoMediaItem.fromUri(Uri.parse(uri)))
                    repeatMode = Player.REPEAT_MODE_ONE
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    prepare()
                    playWhenReady = true
                }
        } else {
            null
        }
    }

    val state = remember(uri, isVideo) {
        ViewerVideoState(player = player, isVideo = isVideo)
    }

    // Attach ExoPlayer listener
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

    // Pause player when activity/lifecycle moves to background
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

    // Poll current playback position while controls are visible
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

/**
 * AndroidView host for ExoPlayer rendering within the HorizontalPager.
 */
@Composable
fun VideoPlayerSurface(
    videoState: ViewerVideoState,
    modifier: Modifier = Modifier
) {
    if (!videoState.isVideo || videoState.player == null) return

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
            .alpha(if (videoState.isFirstFrameRendered) 1f else 0f),
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

/**
 * Floating video playback controller bar (Play/Pause, scrub slider, timestamps, mute).
 */
@Composable
fun VideoPlayerControlsBar(
    videoState: ViewerVideoState,
    modifier: Modifier = Modifier
) {
    if (!videoState.isVideo || videoState.player == null) return

    val position = videoState.positionMs
    val duration = videoState.durationMs
    val isPlaying = videoState.isPlaying
    val isMuted = videoState.isMuted

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(bottom = 76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = { videoState.togglePlayPause() },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause video" else "Play video"
            )
        }
        Text(
            text = formatTime(position),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
        Slider(
            value = position.toFloat().coerceAtMost(duration.coerceAtLeast(1L).toFloat()),
            onValueChange = { videoState.positionMs = it.toLong() },
            onValueChangeFinished = { videoState.seekTo(videoState.positionMs) },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatTime(duration),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
        IconButton(
            onClick = { videoState.toggleMute() },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (isMuted) "Unmute video" else "Mute video"
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
