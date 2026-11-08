@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.amresalehin.emregallery.data.OkHttpDownloader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


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

/**
 * Floating video playback controller bar overlay (Play/Pause, timestamps, seek slider, mute, fullscreen).
 * Features spacious touch targets, adaptive seek slider compression, distinct adjacent buttons,
 * and clean translucent styling that never overlaps surrounding action bars.
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
    val isFullscreen = videoState.isFullscreen

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Play / Pause button with generous touch target
            IconButton(
                onClick = { videoState.togglePlayPause() },
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause video" else "Play video",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Current time with tabular figures
            Text(
                text = formatTime(position),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            // Flexible Seek Slider that shrinks before compressing timestamps or buttons
            Slider(
                value = position.toFloat().coerceAtMost(duration.coerceAtLeast(1L).toFloat()),
                onValueChange = { videoState.positionMs = it.toLong() },
                onValueChangeFinished = { videoState.seekTo(videoState.positionMs) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                ),
                modifier = Modifier
                    .weight(1f, fill = true)
                    .padding(horizontal = 2.dp)
            )

            // Duration with tabular figures
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            // Adjacent Mute and Fullscreen buttons with distinct touch targets
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Mute toggle
                IconButton(
                    onClick = { videoState.toggleMute() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute video" else "Mute video",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Fullscreen toggle
                IconButton(
                    onClick = { videoState.toggleFullscreen() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
