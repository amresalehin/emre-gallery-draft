package com.amresalehin.emregallery.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer

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
