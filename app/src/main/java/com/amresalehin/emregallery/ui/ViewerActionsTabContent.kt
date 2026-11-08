package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.DetectedMediaFace
import com.amresalehin.emregallery.face.FaceProcessingCoordinator


@Composable
fun ActionsTabContent(
    item: MediaItem,
    customTags: Set<String>,
    detectedFaces: List<DetectedMediaFace>,
    metadata: Map<String, String>,
    detailsState: ViewerDetailsState,
    onRenameFaceClick: (DetectedMediaFace) -> Unit,
    onAddTo: (() -> Unit)? = null,
    onAddLocationClick: (() -> Unit)? = null,
    onAddTagClick: (() -> Unit)? = null,
    onRescanPhoto: (() -> Unit)? = null,
    onAddPersonClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    val coroutineScope = rememberCoroutineScope()
    val hasPeople = detectedFaces.isNotEmpty()

    var selectedEngine by remember { mutableStateOf("Tesseract") }
    var engineExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var langExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val isProcessingActive by FaceProcessingCoordinator.isProcessingActive.collectAsStateWithLifecycle()
    val isBusy = isProcessingActive || FaceProcessingCoordinator.isBusy(context)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. People (detected faces, smaller portraits, muted look, pencil directly beneath)
        if (hasPeople) {
            item(key = "actions_people") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "People",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (onRescanPhoto != null) {
                            TextButton(
                                onClick = onRescanPhoto,
                                enabled = !isBusy,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Re-scan")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        detectedFaces.forEach { face ->
                            ActionFacePortrait(
                                face = face,
                                enabled = !isBusy,
                                onRename = { if (!FaceProcessingCoordinator.isBusy(context)) onRenameFaceClick(face) }
                            )
                        }
                    }
                }
            }
        }

        viewerActionsOcrItem(
            item = item,
            detailsState = detailsState,
            selectedEngine = selectedEngine,
            onSelectedEngineChange = { selectedEngine = it },
            engineExpanded = engineExpanded,
            onEngineExpandedChange = { engineExpanded = it },
            modeExpanded = modeExpanded,
            onModeExpandedChange = { modeExpanded = it },
            langExpanded = langExpanded,
            onLangExpandedChange = { langExpanded = it }
        )
    // 4. FFmpeg Video Tools (Videos only)
        if (item.isVideo) {
            item(key = "actions_video_ffmpeg") {
                FfmpegVideoToolsSection(
                    item = item,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Smaller portrait for Actions tab with muted visual treatment and a small pencil button directly beneath.
 */
