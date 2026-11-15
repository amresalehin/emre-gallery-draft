@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amresalehin.emregallery.ui

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.amresalehin.emregallery.data.OkHttpDownloader

/**
 * Configures and prepares an [ExoPlayer] instance for local or streamed viewer media,
 * supporting HLS/DASH adaptive manifests and shared OkHttp session headers/cookies.
 */
internal fun createViewerExoPlayer(
    context: Context,
    uri: String,
    sessionPlayIntent: Boolean
): ExoPlayer {
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

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .build()
        .apply {
            setMediaItem(mediaItem)
            repeatMode = if (isHls || isDash) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            prepare()
            // Respect user's playback intent for this session:
            playWhenReady = sessionPlayIntent
        }
}
