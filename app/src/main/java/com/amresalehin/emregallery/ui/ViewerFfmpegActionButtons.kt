package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.ffmpeg.FfmpegKitEngine
import kotlinx.coroutines.launch


@Composable
internal fun ColumnScope.FfmpegVideoActionButtonsSection(
    item: MediaItem,
    isExtractingAudio: Boolean,
    onIsExtractingAudioChange: (Boolean) -> Unit,
    audioSuccessMsg: String?,
    onAudioSuccessMsgChange: (String?) -> Unit,
    isCompressing: Boolean,
    onIsCompressingChange: (Boolean) -> Unit,
    compressSuccessMsg: String?,
    onCompressSuccessMsgChange: (String?) -> Unit,
    isExtractingFrame: Boolean,
    onIsExtractingFrameChange: (Boolean) -> Unit,
    frameSuccessMsg: String?,
    onFrameSuccessMsgChange: (String?) -> Unit,
    errorMsg: String?,
    onErrorMsgChange: (String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
        // Action Buttons Row
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Extract Audio
            OutlinedButton(
                onClick = {
                    if (isExtractingAudio || isCompressing || isExtractingFrame) return@OutlinedButton
                    onIsExtractingAudioChange(true)
                    onAudioSuccessMsgChange(null)
                    onErrorMsgChange(null)
                    coroutineScope.launch {
                        val file = FfmpegKitEngine.resolveUriToFile(context, item.uri, item.name)
                        if (file != null && file.exists()) {
                            val res = FfmpegKitEngine.extractAudio(file, outputFormat = "mp3", context = context)
                            if (res.isSuccess && res.outputFile != null) {
                                audioSuccessMsg = "Saved: ${res.outputFile.name}"
                            } else {
                                errorMsg = "Audio extraction failed (Code ${res.returnCode})"
                            }
                        } else {
                            errorMsg = "Unable to read local video file"
                        }
                        onIsExtractingAudioChange(false)
                    }
                },
                enabled = !isExtractingAudio && !isCompressing && !isExtractingFrame,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isExtractingAudio) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Extracting MP3 Audio...")
                } else {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Extract Audio Track (MP3)")
                }
            }

            // 2. Compress Video
            OutlinedButton(
                onClick = {
                    if (isExtractingAudio || isCompressing || isExtractingFrame) return@OutlinedButton
                    onIsCompressingChange(true)
                    onCompressSuccessMsgChange(null)
                    onErrorMsgChange(null)
                    coroutineScope.launch {
                        val file = FfmpegKitEngine.resolveUriToFile(context, item.uri, item.name)
                        if (file != null && file.exists()) {
                            val res = FfmpegKitEngine.compressVideo(file, targetHeight = 720, crf = 26, context = context)
                            if (res.isSuccess && res.outputFile != null) {
                                compressSuccessMsg = "Saved: ${res.outputFile.name} (${res.outputFile.length() / 1024} KB)"
                            } else {
                                errorMsg = "Video compression failed (Code ${res.returnCode})"
                            }
                        } else {
                            errorMsg = "Unable to read local video file"
                        }
                        onIsCompressingChange(false)
                    }
                },
                enabled = !isExtractingAudio && !isCompressing && !isExtractingFrame,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isCompressing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Compressing (720p H.264)...")
                } else {
                    Icon(Icons.Default.VideoSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Compress Video (720p H.264)")
                }
            }

            // 3. Save Poster Frame
            OutlinedButton(
                onClick = {
                    if (isExtractingAudio || isCompressing || isExtractingFrame) return@OutlinedButton
                    onIsExtractingFrameChange(true)
                    onFrameSuccessMsgChange(null)
                    onErrorMsgChange(null)
                    coroutineScope.launch {
                        val file = FfmpegKitEngine.resolveUriToFile(context, item.uri, item.name)
                        if (file != null && file.exists()) {
                            val thumb = FfmpegKitEngine.extractThumbnail(file, positionSec = 1.0, context = context)
                            if (thumb != null && thumb.exists()) {
                                frameSuccessMsg = "Saved poster: ${thumb.name}"
                            } else {
                                errorMsg = "Frame extraction failed"
                            }
                        } else {
                            errorMsg = "Unable to read local video file"
                        }
                        onIsExtractingFrameChange(false)
                    }
                },
                enabled = !isExtractingAudio && !isCompressing && !isExtractingFrame,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isExtractingFrame) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Extracting Poster Frame...")
                } else {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Video Poster Frame (JPEG)")
                }
            }
        }

        // Status messages
        audioSuccessMsg?.let { msg ->
            Text(
                text = "✓ $msg",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Medium
            )
        }
        compressSuccessMsg?.let { msg ->
            Text(
                text = "✓ $msg",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Medium
            )
        }
        frameSuccessMsg?.let { msg ->
            Text(
                text = "✓ $msg",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Medium
            )
        }
        errorMsg?.let { err ->
            Text(
                text = "✕ $err",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
}
