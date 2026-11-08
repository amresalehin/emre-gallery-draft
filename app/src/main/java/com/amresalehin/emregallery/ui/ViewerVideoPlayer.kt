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

/**
 * State holder for video playback in the media viewer.
 * Respects session-wide playback intent so manually paused videos don't autoplay when swiping.
 */
@Stable
class ViewerVideoState(
    val player: ExoPlayer?,
    val isVideo: Boolean,
    initialPlayIntent: Boolean,
    private val onPlayIntentChange: (Boolean) -> Unit
) {
    var isFirstFrameRendered by mutableStateOf(false)
    var isPlaying by mutableStateOf(if (isVideo && player != null) initialPlayIntent else false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var isMuted by mutableStateOf(false)
    var isFullscreen by mutableStateOf(false)

    fun togglePlayPause() {
        val targetPlay = !isPlaying
        if (targetPlay) {
            player?.play()
        } else {
            player?.pause()
        }
        isPlaying = targetPlay
        onPlayIntentChange(targetPlay)
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

    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
    }
}

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
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 1000,
                    /* maxBufferMs = */ 3000,
                    /* bufferForPlaybackMs = */ 100,
                    /* bufferForPlaybackAfterRebufferMs = */ 250
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val httpDataSourceFactory = OkHttpDataSource.Factory(OkHttpDownloader.client)
                .setUserAgent(OkHttpDownloader.DEFAULT_USER_AGENT)
            val upstreamDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(upstreamDataSourceFactory)

            val parsedUri = Uri.parse(uri)
            val isHls = uri.contains(".m3u8", ignoreCase = true) || uri.contains("mpegurl", ignoreCase = true)
            val isDash = uri.contains(".mpd", ignoreCase = true) || uri.contains("dash", ignoreCase = true)

            val mediaItem = ExoMediaItem.Builder()
                .setUri(parsedUri)
                .apply {
                    if (isHls) {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    } else if (isDash) {
                        setMimeType(MimeTypes.APPLICATION_MPD)
                    }
                }
                .build()

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build().apply {
                    setMediaItem(mediaItem)
                    repeatMode = if (isHls || isDash) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    prepare()
                    // Respect user's playback intent for this session:
                    playWhenReady = sessionPlayIntent
                }
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

    // Attach ExoPlayer listener to current active player
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
