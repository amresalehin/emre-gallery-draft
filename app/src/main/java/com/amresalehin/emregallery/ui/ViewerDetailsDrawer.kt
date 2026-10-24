package com.amresalehin.emregallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewConfiguration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State holder for the media details drawer (metadata and OCR).
 */
@Stable
class ViewerDetailsState(
    val context: Context,
    val item: MediaItem,
    defaultOcrLanguages: Set<String>
) {
    var metadata by mutableStateOf<Map<String, String>>(emptyMap())
    var ocrText by mutableStateOf("")
    var ocrError by mutableStateOf<String?>(null)
    var ocrBusy by mutableStateOf(false)
    val ocrLanguages = mutableStateListOf<String>().apply {
        val initial = if (defaultOcrLanguages.isNotEmpty()) defaultOcrLanguages else setOf("eng")
        addAll(initial)
    }

    suspend fun loadData() {
        metadata = withContext(Dispatchers.IO) {
            runCatching { readMediaMetadata(context, item) }.getOrDefault(emptyMap())
        }
        ocrText = withContext(Dispatchers.IO) {
            TesseractOcr.readCachedResult(context, Uri.parse(item.uri)).orEmpty()
        }
        ocrError = null
    }

    fun toggleOcrLanguage(code: String) {
        if (code in ocrLanguages) {
            if (ocrLanguages.size > 1) ocrLanguages.remove(code)
        } else {
            ocrLanguages.add(code)
        }
    }

    suspend fun runOcr() {
        if (item.isVideo || ocrBusy || ocrLanguages.isEmpty()) return
        ocrBusy = true
        ocrError = null
        ocrText = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val mediaUri = Uri.parse(item.uri)
                val recognized = TesseractOcr.recognize(context, mediaUri, ocrLanguages.toList())
                runCatching { TesseractOcr.saveCachedResult(context, mediaUri, recognized) }
                recognized
            }
        }
        result.onSuccess { text ->
            ocrText = text.ifBlank { "No text detected in this image." }
        }.onFailure { error ->
            ocrError = error.message ?: "OCR failed. Please try again."
        }
        ocrBusy = false
    }

    fun copyOcrText() {
        if (ocrText.isNotBlank() && ocrText != "No text detected in this image.") {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Recognized text", ocrText))
        }
    }
}

@Composable
fun rememberViewerDetailsState(
    item: MediaItem,
    defaultOcrLanguages: Set<String>
): ViewerDetailsState {
    val context = LocalContext.current
    val state = remember(item.uri, defaultOcrLanguages) {
        ViewerDetailsState(context, item, defaultOcrLanguages)
    }

    LaunchedEffect(item.uri, defaultOcrLanguages) {
        state.loadData()
    }

    return state
}

/**
 * Slide-up details drawer containing EXIF/XMP info, location, people, albums, OCR, and tags.
 */
@Composable
fun BoxScope.ViewerDetailsDrawer(
    item: MediaItem,
    items: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    customTags: Set<String>,
    showDetails: Boolean,
    detailsRevealTriggered: Boolean,
    detailsRevealPx: Float,
    detailsSheetHeightPx: Float,
    onHeightMeasured: (Float) -> Unit,
    onRevealPxChanged: (Float) -> Unit,
    onToggleDetails: () -> Unit,
    onShare: () -> Unit,
    onAddTo: () -> Unit,
    onToggleBin: () -> Unit,
    onShowMore: () -> Unit,
    onOpenMap: () -> Unit,
    onAddTag: (String) -> Unit,
    isBin: Boolean,
    defaultOcrLanguages: Set<String> = setOf("eng"),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewConfiguration = ViewConfiguration.get(context)
    val coroutineScope = rememberCoroutineScope()
    val detailsState = rememberViewerDetailsState(item, defaultOcrLanguages)

    // Smooth backdrop scrim
    AnimatedVisibility(
        visible = showDetails,
        enter = fadeIn(animationSpec = tween(280)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(showDetails) {
                    var totalDragY = 0f
                    val touchSlop = viewConfiguration.scaledTouchSlop.toFloat()
                    val dismissThreshold = touchSlop * 3.5f
                    detectDragGestures(
                        onDragStart = { totalDragY = 0f },
                        onDragEnd = {
                            if (totalDragY > dismissThreshold) {
                                onToggleDetails()
                            }
                        },
                        onDragCancel = { totalDragY = 0f }
                    ) { change, dragAmount ->
                        totalDragY += dragAmount.y
                        if (kotlin.math.abs(totalDragY) > touchSlop) {
                            change.consume()
                        }
                    }
                }
                .clickable { onToggleDetails() }
        )
    }

    val sheetDragOffsetY = remember { Animatable(0f) }
    LaunchedEffect(showDetails) {
        sheetDragOffsetY.snapTo(0f)
        if (showDetails && !detailsRevealTriggered) {
            onRevealPxChanged(detailsSheetHeightPx)
        } else if (!showDetails) {
            onRevealPxChanged(0f)
        }
    }

    AnimatedVisibility(
        visible = showDetails,
        enter = fadeIn(animationSpec = tween(120)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(280, easing = FastOutLinearInEasing)
        ) + fadeOut(animationSpec = tween(180)),
        modifier = modifier.align(Alignment.BottomCenter)
    ) {
        Surface(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.50f)
                .onSizeChanged {
                    val h = it.height.toFloat()
                    onHeightMeasured(h)
                    if (showDetails && !detailsRevealTriggered) {
                        onRevealPxChanged(h)
                    }
                }
                .graphicsLayer {
                    translationY = (detailsSheetHeightPx - detailsRevealPx).coerceAtLeast(0f) +
                        sheetDragOffsetY.value.coerceAtLeast(0f)
                },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                // Top drag handle with drag down dismiss
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                        .pointerInput(showDetails) {
                            val velocityTracker = VelocityTracker()
                            val minFling = viewConfiguration.scaledMinimumFlingVelocity.toFloat()
                            val dismissDist = 80.dp.toPx()
                            detectDragGestures(
                                onDragStart = {
                                    velocityTracker.resetTracking()
                                    coroutineScope.launch { sheetDragOffsetY.snapTo(0f) }
                                },
                                onDragEnd = {
                                    val velocityY = velocityTracker.calculateVelocity().y
                                    coroutineScope.launch {
                                        when {
                                            (sheetDragOffsetY.value > dismissDist || velocityY > minFling) -> {
                                                sheetDragOffsetY.animateTo(
                                                    targetValue = detailsSheetHeightPx.coerceAtLeast(600f),
                                                    initialVelocity = velocityY,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                                onToggleDetails()
                                                sheetDragOffsetY.snapTo(0f)
                                            }
                                            else -> {
                                                sheetDragOffsetY.animateTo(
                                                    targetValue = 0f,
                                                    initialVelocity = velocityY,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                            }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        sheetDragOffsetY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                coroutineScope.launch {
                                    val next = (sheetDragOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                                    sheetDragOffsetY.snapTo(next)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(42.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header: Filename, Date & Time, and Edit button pill
                    item {
                        DetailHeaderSection(
                            item = item,
                            onEdit = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_EDIT)
                                            .setDataAndType(Uri.parse(item.uri), item.mimeType ?: "image/*")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                }
                            }
                        )
                    }

                    // Quick Actions row
                    item {
                        DetailQuickActionsRow(
                            isBin = isBin,
                            onShare = onShare,
                            onAddTo = onAddTo,
                            onToggleBin = onToggleBin,
                            onMore = onShowMore
                        )
                    }

                    // Card 1: Location & Mini Map Preview
                    item {
                        val locationText = detailsState.metadata["location"]?.takeIf { it.isNotBlank() }
                            ?: "Location not available"
                        DetailLocationCard(
                            locationText = locationText,
                            onOpenMap = onOpenMap
                        )
                    }

                    // Card 2: Visual description
                    item {
                        DetailAiSummaryCard()
                    }

                    // Card 3: OCR text recognition
                    item {
                        DetailOcrCard(
                            item = item,
                            ocrText = detailsState.ocrText,
                            ocrError = detailsState.ocrError,
                            ocrBusy = detailsState.ocrBusy,
                            ocrLanguages = detailsState.ocrLanguages,
                            onToggleLanguage = { detailsState.toggleOcrLanguage(it) },
                            onRunOcr = {
                                coroutineScope.launch { detailsState.runOcr() }
                            },
                            onCopyText = { detailsState.copyOcrText() }
                        )
                    }

                    // Card 4: Tags
                    item {
                        DetailTagsCard(
                            tags = customTags,
                            onAddTag = { onAddTag("Tag") }
                        )
                    }

                    // Card 5: People detected
                    item {
                        DetailPeopleCard(mediaUri = item.uri)
                    }

                    // Card 6: Albums containing this item
                    item {
                        DetailAlbumsCard(
                            item = item,
                            customAlbums = customAlbums,
                            items = items,
                            onAddTo = onAddTo
                        )
                    }

                    // Card 7: EXIF & XMP Metadata
                    item {
                        DetailMetadataCard(
                            metadata = detailsState.metadata,
                            item = item
                        )
                    }

                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
