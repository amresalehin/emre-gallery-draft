package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.ffmpeg.FfmpegKitEngine
import com.amresalehin.emregallery.ffmpeg.FfprobeMediaInfo
import kotlinx.coroutines.launch


@Composable
fun FfmpegVideoToolsSection(
    item: MediaItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isProbing by remember { mutableStateOf(false) }
    var mediaInfo by remember { mutableStateOf<FfprobeMediaInfo?>(null) }
    var probeAttempted by remember { mutableStateOf(false) }

    var isExtractingAudio by remember { mutableStateOf(false) }
    var audioSuccessMsg by remember { mutableStateOf<String?>(null) }

    var isCompressing by remember { mutableStateOf(false) }
    var compressSuccessMsg by remember { mutableStateOf<String?>(null) }

    var isExtractingFrame by remember { mutableStateOf(false) }
    var frameSuccessMsg by remember { mutableStateOf<String?>(null) }

    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Auto-probe media info once on mount
    LaunchedEffect(item.uri) {
        if (!probeAttempted) {
            probeAttempted = true
            isProbing = true
            coroutineScope.launch {
                val probed = FfmpegKitEngine.probeMedia(item.uri, context)
                mediaInfo = probed
                isProbing = false
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MovieFilter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "FFmpeg Video Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "FFmpegKit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // FFprobe Media Stream Inspector Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stream Details (FFprobe)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isProbing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                if (mediaInfo != null) {
                    val info = mediaInfo!!
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Container Format", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(info.format.take(24), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        info.videoCodec?.let { codec ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Video Codec", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(codec + (info.resolution?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        info.audioCodec?.let { codec ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Audio Codec", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val channelsStr = if (info.audioChannels != null) " · ${info.audioChannels}ch" else ""
                                val rateStr = if (info.sampleRateHz != null) " · ${info.sampleRateHz}Hz" else ""
                                Text("$codec$channelsStr$rateStr", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (info.bitrateKbps > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bitrate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${info.bitrateKbps} kbps", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else if (!isProbing) {
                    Text(
                        text = "Native FFmpeg probe ready. Tap actions below to process.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        FfmpegVideoActionButtonsSection(
            item = item,
            isExtractingAudio = isExtractingAudio,
            onIsExtractingAudioChange = { isExtractingAudio = it },
            audioSuccessMsg = audioSuccessMsg,
            onAudioSuccessMsgChange = { audioSuccessMsg = it },
            isCompressing = isCompressing,
            onIsCompressingChange = { isCompressing = it },
            compressSuccessMsg = compressSuccessMsg,
            onCompressSuccessMsgChange = { compressSuccessMsg = it },
            isExtractingFrame = isExtractingFrame,
            onIsExtractingFrameChange = { isExtractingFrame = it },
            frameSuccessMsg = frameSuccessMsg,
            onFrameSuccessMsgChange = { frameSuccessMsg = it },
            errorMsg = errorMsg,
            onErrorMsgChange = { errorMsg = it }
        )
    }
}
