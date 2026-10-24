package com.amresalehin.emregallery.ui

import android.content.Intent
import android.net.Uri
import android.view.ViewConfiguration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceClusterSummary
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    item: MediaItem,
    items: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    defaultOcrLanguages: Set<String> = setOf("eng"),
    customTags: Set<String>,
    customPeople: Set<String>,
    onAddTag: (String) -> Unit,
    onAddPerson: (String) -> Unit,
    favourite: Boolean,
    isHidden: Boolean,
    isBin: Boolean,
    isArchived: Boolean,
    showControls: Boolean,
    showDetails: Boolean,
    onToggleControls: () -> Unit,
    onToggleDetails: () -> Unit,
    onBack: () -> Unit,
    onFavourite: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleBin: () -> Unit,
    onToggleArchive: () -> Unit,
    onAddTo: () -> Unit,
    onOpenNeighbor: (MediaItem) -> Unit,
    onOpenMap: () -> Unit
) {
    val context = LocalContext.current
    val videoPlayer = remember(item.uri, item.isVideo) {
        if (item.isVideo) {
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 1000,
                    /* maxBufferMs = */ 3000,
                    /* bufferForPlaybackMs = */ 100,
                    /* bufferForPlaybackAfterRebufferMs = */ 250
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            androidx.media3.exoplayer.ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build().apply {
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(item.uri)))
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                    videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    prepare()
                    playWhenReady = true
                }
        } else null
    }
    var isVideoFirstFrameRendered by remember(item.uri) { mutableStateOf(false) }
    var isPlaying by remember(item.uri) { mutableStateOf(true) }
    var videoPosition by remember(item.uri) { mutableLongStateOf(0L) }
    var videoDuration by remember(item.uri) { mutableLongStateOf(0L) }
    var videoMuted by remember(item.uri) { mutableStateOf(false) }
    DisposableEffect(videoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() {
                isVideoFirstFrameRendered = true
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    videoDuration = videoPlayer?.duration?.takeIf { it > 0L } ?: 0L
                }
            }
        }
        videoPlayer?.addListener(listener)
        onDispose {
            videoPlayer?.removeListener(listener)
            videoPlayer?.stop()
            videoPlayer?.release()
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, videoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                videoPlayer?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    var metadata by remember(item.uri) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(videoPlayer, item.uri, showControls) {
        if (!showControls) return@LaunchedEffect
        while (videoPlayer != null && isActive) {
            videoPosition = videoPlayer.currentPosition.coerceAtLeast(0L)
            val d = videoPlayer.duration
            if (d > 0L && d != videoDuration) {
                videoDuration = d
            }
            delay(250)
        }
    }
    var ocrText by remember(item.uri) { mutableStateOf("") }
    var ocrError by remember(item.uri) { mutableStateOf<String?>(null) }
    var ocrBusy by remember(item.uri) { mutableStateOf(false) }
    val ocrLanguages = remember(item.uri, defaultOcrLanguages) {
        val initial = if (defaultOcrLanguages.isNotEmpty()) defaultOcrLanguages else setOf("eng")
        mutableStateListOf(*initial.toTypedArray())
    }
    val ocrScope = rememberCoroutineScope()
    LaunchedEffect(item.uri, defaultOcrLanguages) {
        metadata = withContext(Dispatchers.IO) {
            runCatching { readMediaMetadata(context, item) }.getOrDefault(emptyMap())
        }
        ocrText = withContext(Dispatchers.IO) {
            TesseractOcr.readCachedResult(context, Uri.parse(item.uri)).orEmpty()
        }
        ocrError = null
    }
    fun meta(key: String): String = metadata[key]?.takeIf { it.isNotBlank() } ?: "Not available"
    var showMore by remember { mutableStateOf(false) }
    // The media details drawer has a fixed half-screen height; no expanded state.
    val dragOffsetY = remember { Animatable(0f) }
    LaunchedEffect(item.uri) {
        dragOffsetY.snapTo(0f)
    }
    val share: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = item.mimeType ?: if (item.isVideo) "video/*" else "image/*"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Share media"
                )
            )
        }
    }

    val currentOffsetY = dragOffsetY.value
    val dismissFraction = (kotlin.math.abs(currentOffsetY) / 540f).coerceIn(0f, 1f)
    val backdropAlpha = (1f - dismissFraction * 0.70f).coerceIn(0.30f, 1f)

    val initialIndex = remember(items, item.uri) {
        val idx = items.indexOfFirst { it.uri == item.uri }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { items.size }
    )

    // Synchronize pager page settlement with parent selected media
    LaunchedEffect(pagerState.currentPage) {
        val current = items.getOrNull(pagerState.currentPage)
        if (current != null && current.uri != item.uri) {
            onOpenNeighbor(current)
        }
    }

    // Scroll to new target page if parent changes selected media
    LaunchedEffect(item.uri) {
        val targetIndex = items.indexOfFirst { it.uri == item.uri }
        if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    val viewConfig = ViewConfiguration.get(context)
    val minFlingVelocity = viewConfig.scaledMinimumFlingVelocity.toFloat()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var detailsRevealTriggered by remember(item.uri) { mutableStateOf(false) }
    var detailsRevealPx by remember(item.uri) { mutableFloatStateOf(0f) }
    var detailsSheetHeightPx by remember(item.uri) { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            // Keep the viewer canvas consistent with the user's selected app theme and animate backdrop alpha on drag
            .background(MaterialTheme.colorScheme.background.copy(alpha = backdropAlpha))
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !showDetails,
                state = rememberDraggableState { delta ->
                    ocrScope.launch {
                        val nextOffset = dragOffsetY.value + delta
                        dragOffsetY.snapTo(nextOffset)
                        // Start revealing the details drawer as soon as an upward swipe is
                        // deliberate, rather than waiting for the finger to lift.
                        val revealThreshold = viewConfig.scaledTouchSlop * 1.25f
                        if (nextOffset < -revealThreshold && !detailsRevealTriggered) {
                            detailsRevealTriggered = true
                            detailsRevealPx = (-nextOffset).coerceIn(0f, detailsSheetHeightPx)
                            onToggleDetails()
                        } else if (detailsRevealTriggered) {
                            // Drive the drawer from the same finger delta as the media.
                            detailsRevealPx = (-nextOffset).coerceIn(0f, detailsSheetHeightPx)
                        }
                    }
                },
                onDragStopped = { velocity ->
                    val dismissDistanceThreshold = with(density) { 140.dp.toPx() }
                    val detailsDistanceThreshold = with(density) { 64.dp.toPx() }
                    if (dragOffsetY.value > dismissDistanceThreshold || velocity > minFlingVelocity * 1.4f) {
                        ocrScope.launch {
                            dragOffsetY.animateTo(
                                targetValue = 1200f,
                                initialVelocity = velocity,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            onBack()
                        }
                    } else if (detailsRevealTriggered || dragOffsetY.value < -detailsDistanceThreshold || velocity < -minFlingVelocity * 1.4f) {
                        ocrScope.launch {
                            dragOffsetY.animateTo(
                                targetValue = 0f,
                                initialVelocity = velocity,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            if (!detailsRevealTriggered) {
                                onToggleDetails()
                            } else {
                                val startReveal = detailsRevealPx
                                val endReveal = detailsSheetHeightPx
                                animate(
                                    initialValue = startReveal,
                                    targetValue = endReveal,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) { value, _ -> detailsRevealPx = value }
                            }
                            detailsRevealTriggered = false
                        }
                    } else {
                        ocrScope.launch {
                            dragOffsetY.animateTo(
                                targetValue = 0f,
                                initialVelocity = velocity,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            detailsRevealTriggered = false
                        }
                    }
                }
            )
    ) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = currentOffsetY
                    val scale = (1f - dismissFraction * 0.28f).coerceIn(0.72f, 1f)
                    scaleX = scale
                    scaleY = scale
                    clip = dismissFraction > 0.02f
                    shape = RoundedCornerShape((dismissFraction * 28f).dp)
                },
            key = { pageIndex -> items.getOrNull(pageIndex)?.uri ?: pageIndex.toString() }
        ) { pageIndex ->
            val currentItem = items.getOrNull(pageIndex) ?: item
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentItem.uri) {
                        detectTapGestures(
                            onTap = { onToggleControls() },
                            onLongPress = { showMore = true }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Always render the cached thumbnail as baseline so there is never a black screen or blank flash
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(currentItem.uri))
                        .size(Size.ORIGINAL)
                        .precision(Precision.EXACT)
                        .placeholderMemoryCacheKey("${currentItem.uri}_thumb")
                        .diskCacheKey("${currentItem.uri}:viewer-original")
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCacheKey("${currentItem.uri}:viewer-original")
                        .crossfade(100)
                        .build(),
                    contentDescription = currentItem.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                if (currentItem.isVideo && currentItem.uri == item.uri) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { vc ->
                            androidx.media3.ui.PlayerView(vc).apply {
                                player = videoPlayer
                                useController = false
                                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_NEVER)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                isClickable = false
                                isFocusable = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (isVideoFirstFrameRendered) 1f else 0f),
                        update = { pv ->
                            if (pv.player != videoPlayer) {
                                pv.player = videoPlayer
                            }
                            pv.useController = false
                            pv.setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_NEVER)
                            pv.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    )
                }
            }
        }

        // Wider, stronger edge shading extends down to the floating actions
        // when controls appear, while keeping the centre of the photo unobscured.
        AnimatedVisibility(
            visible = showControls && !showDetails,
            enter = fadeIn(animationSpec = tween(260)),
            exit = fadeOut(animationSpec = tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val edgeShadeHeight = maxHeight * 0.22f
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(edgeShadeHeight)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.62f),
                                        Color.Black.copy(alpha = 0.38f),
                                        Color.Black.copy(alpha = 0.16f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(edgeShadeHeight)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.16f),
                                        Color.Black.copy(alpha = 0.42f),
                                        Color.Black.copy(alpha = 0.68f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        if (showControls && !showDetails && item.isVideo && videoPlayer != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
                    onClick = {
                        if (isPlaying) videoPlayer.pause() else videoPlayer.play()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause video" else "Play video"
                    )
                }
                Text(
                    text = "${videoPosition / 60000}:${((videoPosition / 1000) % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                Slider(
                    value = videoPosition.toFloat().coerceAtMost(videoDuration.coerceAtLeast(1L).toFloat()),
                    onValueChange = { videoPosition = it.toLong() },
                    onValueChangeFinished = { videoPlayer.seekTo(videoPosition) },
                    valueRange = 0f..videoDuration.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${videoDuration / 60000}:${((videoDuration / 1000) % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                IconButton(
                    onClick = {
                        videoMuted = !videoMuted
                        videoPlayer.volume = if (videoMuted) 0f else 1f
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (videoMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (videoMuted) "Unmute video" else "Mute video"
                    )
                }
            }
        }

        if (showControls && !showDetails) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewerButton(Icons.Default.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                ViewerButton(Icons.Default.Share, "Share", share)
                ViewerButton(if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favourite", onFavourite)
                ViewerButton(Icons.Default.MoreVert, "More") { showMore = true }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ViewerAction(Icons.Default.Tune, "Edit") {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_EDIT)
                                .setDataAndType(Uri.parse(item.uri), item.mimeType ?: "image/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        )
                    }
                }
                ViewerAction(
                    if (isBin) Icons.Default.Restore else Icons.Default.Delete,
                    if (isBin) "Restore" else "Delete",
                    onToggleBin
                )
            }

        }

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
                    .background(Color.Black.copy(alpha = .45f))
                    // Tapping outside dismisses the sheet; vertical swipes on the
                    // exposed viewer area expand or dismiss it without touching the panel.
                    .pointerInput(showDetails) {
                        var totalDragY = 0f
                        val dismissThreshold = viewConfiguration.touchSlop * 3.5f
                        detectDragGestures(
                            onDragStart = { totalDragY = 0f },
                            onDragEnd = {
                                when {
                                    totalDragY > dismissThreshold -> onToggleDetails()
                                }
                            },
                            onDragCancel = { totalDragY = 0f }
                        ) { change, dragAmount ->
                            totalDragY += dragAmount.y
                            if (kotlin.math.abs(totalDragY) > viewConfiguration.touchSlop) {
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
                detailsRevealPx = detailsSheetHeightPx
            } else if (!showDetails) {
                detailsRevealPx = 0f
            }
        }

        // Fade the panel in, while its vertical position follows the same upward
        // drag offset as the media. This avoids a second, competing slide animation.
        AnimatedVisibility(
            visible = showDetails,
            enter = fadeIn(animationSpec = tween(120)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(280, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.50f)
                    .onSizeChanged {
                        detailsSheetHeightPx = it.height.toFloat()
                        if (showDetails && !detailsRevealTriggered) {
                            detailsRevealPx = it.height.toFloat()
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
                                val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
                                val dismissDist = 80.dp.toPx()
                                detectDragGestures(
                                    onDragStart = {
                                        velocityTracker.resetTracking()
                                        ocrScope.launch { sheetDragOffsetY.snapTo(0f) }
                                    },
                                    onDragEnd = {
                                        val velocityY = velocityTracker.calculateVelocity().y
                                        ocrScope.launch {
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
                                        ocrScope.launch {
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
                                    // Keep the drawer attached to the finger continuously.
                                    ocrScope.launch {
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
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f))
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header: Filename, Date & Time, and Edit button pill
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = formatMediaDateTime(item.dateTakenMillis, item.dateAddedSeconds),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_EDIT)
                                                    .setDataAndType(Uri.parse(item.uri), item.mimeType ?: "image/*")
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            )
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Edit",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Top Quick Actions row: Share, Add to, Move to, Delete, More
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DetailActionPill(
                                    icon = Icons.Default.Share,
                                    label = "Share",
                                    modifier = Modifier.weight(1f),
                                    onClick = share
                                )
                                DetailActionPill(
                                    icon = Icons.Default.Add,
                                    label = "Add to",
                                    modifier = Modifier.weight(1f),
                                    onClick = onAddTo
                                )
                                DetailActionPill(
                                    icon = Icons.Default.Folder,
                                    label = "Move to",
                                    modifier = Modifier.weight(1f),
                                    onClick = onAddTo
                                )
                                DetailActionPill(
                                    icon = if (isBin) Icons.Default.Restore else Icons.Default.Delete,
                                    label = if (isBin) "Restore" else "Delete",
                                    modifier = Modifier.weight(1f),
                                    onClick = onToggleBin
                                )
                                DetailActionPill(
                                    icon = Icons.Default.MoreHoriz,
                                    label = "More",
                                    modifier = Modifier.weight(1f),
                                    onClick = { showMore = true }
                                )
                            }
                        }

                        // Card 1: Location & Mini Map Thumbnail Preview
                        item {
                            val locationText = metadata["location"]?.takeIf { it.isNotBlank() }
                                ?: "Location not available"
                            DetailCard(modifier = Modifier.clickable(onClick = onOpenMap)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = "Location",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = locationText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    // Mini Map Graphic
                                    Box(
                                        modifier = Modifier
                                            .width(84.dp)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFCBE3CE))
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val riverColor = Color(0xFF90CAF9)
                                            val roadColor = Color.White.copy(alpha = 0.75f)
                                            drawLine(
                                                color = riverColor,
                                                start = Offset(x = size.width * 0.25f, y = size.height),
                                                end = Offset(x = size.width * 0.85f, y = 0f),
                                                strokeWidth = 14f,
                                                cap = StrokeCap.Round
                                            )
                                            drawLine(
                                                color = roadColor,
                                                start = Offset(x = 0f, y = size.height * 0.45f),
                                                end = Offset(x = size.width, y = size.height * 0.55f),
                                                strokeWidth = 6f
                                            )
                                        }
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier
                                                .size(20.dp)
                                                .align(Alignment.Center)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Card 2: Visual description / Sparkle AI summary
                        item {
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = "Visual description",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Snow-covered mountains at sunset with a lake reflecting the peaks, a small village, and dramatic clouds in the sky.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Card 3: Compact OCR result card.
                        item {
                            DetailCard {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.DocumentScanner,
                                            contentDescription = "Recognized text",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                if (!item.isVideo) {
                                                    ocrBusy = true
                                                    ocrError = null
                                                    ocrText = ""
                                                    ocrScope.launch {
                                                        val result = withContext(Dispatchers.IO) {
                                                            runCatching {
                                                                val mediaUri = Uri.parse(item.uri)
                                                                val recognized = TesseractOcr.recognize(
                                                                    context,
                                                                    mediaUri,
                                                                    ocrLanguages.toList()
                                                                )
                                                                runCatching {
                                                                    TesseractOcr.saveCachedResult(context, mediaUri, recognized)
                                                                }
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
                                                }
                                            },
                                            enabled = !item.isVideo && !ocrBusy && ocrLanguages.isNotEmpty()
                                        ) {
                                            if (ocrBusy) {
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(
                                                    Icons.Default.DocumentScanner,
                                                    contentDescription = if (ocrText.isBlank()) "Recognize text" else "Recognize again"
                                                )
                                            }
                                        }
                                        if (ocrText.isNotBlank() && ocrText != "No text detected in this image.") {
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Recognized text", ocrText))
                                                }
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy recognized text")
                                            }
                                        }
                                    }

                                    if (!item.isVideo) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TesseractOcr.supportedLanguages.forEach { (languageName, code) ->
                                                FilterChip(
                                                    selected = code in ocrLanguages,
                                                    onClick = {
                                                        if (code in ocrLanguages) {
                                                            if (ocrLanguages.size > 1) ocrLanguages.remove(code)
                                                        } else {
                                                            ocrLanguages.add(code)
                                                        }
                                                    },
                                                    label = { Text(languageName) },
                                                    enabled = !ocrBusy
                                                )
                                            }
                                        }
                                    }

                                    if (ocrError != null) {
                                        Text(
                                            ocrError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (item.isVideo) {
                                        Text(
                                            "Text recognition is unavailable for videos.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else if (ocrText.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 220.dp)
                                                .verticalScroll(rememberScrollState())
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                ocrText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Card 4: Tags / Labels
                        item {
                            val tags = customTags.toList().sorted()
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocalOffer,
                                        contentDescription = "Tags",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        tags.forEach { tag ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = tag,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            modifier = Modifier.clickable { }
                                        ) {
                                            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Add tag",
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Card 5: People detected in this specific photo.
                        item {
                            val faceStore = remember(context) { FaceClusterStore(context.applicationContext) }
                            val detectedPeople = remember(item.uri) {
                                faceStore.summaries().filter { cluster ->
                                    faceStore.facesForCluster(cluster.id).any { face -> face.mediaUri == item.uri }
                                }
                            }
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PersonOutline,
                                        contentDescription = "People detected in this photo",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "People",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (detectedPeople.isEmpty()) {
                                            Text(
                                                text = "No recognized people in this photo. Scan photos in People & pets to identify faces.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                detectedPeople.forEach { person ->
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.widthIn(max = 76.dp)
                                                    ) {
                                                        val sampleUri = person.sampleUri
                                                        if (sampleUri != null) {
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(context)
                                                                    .data(Uri.parse(sampleUri))
                                                                    .size(120, 120)
                                                                    .crossfade(false)
                                                                    .build(),
                                                                contentDescription = person.displayName,
                                                                modifier = Modifier.size(46.dp).clip(CircleShape),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Surface(
                                                                shape = CircleShape,
                                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                                modifier = Modifier.size(46.dp)
                                                            ) {
                                                                Box(contentAlignment = Alignment.Center) {
                                                                    Icon(Icons.Default.Face, contentDescription = null)
                                                                }
                                                            }
                                                        }
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = person.displayName,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Card 6: Actual custom albums containing this media item
                        item {
                            val containingAlbums = remember(customAlbums, item.uri) {
                                customAlbums.filter { item.uri in it.mediaUris }
                            }
                            DetailCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = "Albums",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Albums",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (containingAlbums.isEmpty()) {
                                            Text(
                                                text = "Not in any custom album",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            containingAlbums.forEach { album ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                        modifier = Modifier.size(width = 48.dp, height = 36.dp)
                                                    ) {
                                                        val cover = items.firstOrNull { it.uri in album.mediaUris }
                                                        if (cover != null) {
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(context)
                                                                    .data(Uri.parse(cover.uri))
                                                                    .size(144, 108)
                                                                    .crossfade(false)
                                                                    .build(),
                                                                contentDescription = album.fullPath,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Default.PhotoAlbum, contentDescription = null)
                                                            }
                                                        }
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = album.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (album.parentPath.isNotBlank()) {
                                                            Text(
                                                                text = album.parentPath,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = onAddTo) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit")
                                    }
                                }
                            }
                        }

                        // Card 8: Combined EXIF and XMP metadata, showing only available values
                        item {
                            val metadataRows = remember(metadata, item.uri, item.dateTakenMillis, item.dateAddedSeconds, item.width, item.height, item.sizeBytes) {
                                val rows = listOf(
                                    "Dimensions" to (if (item.width > 0 && item.height > 0) "${item.width} × ${item.height} px" else null),
                                    "File size" to item.sizeBytes.takeIf { it > 0 }?.let { formatBytes(it) },
                                    "Device manufacturer" to metadata["make"],
                                    "Device model" to metadata["model"],
                                    "Aperture" to metadata["aperture"],
                                    "Shutter speed" to metadata["shutter"],
                                    "ISO" to metadata["iso"],
                                    "Focal length" to metadata["focal"],
                                    "Flash" to metadata["flash"],
                                    "Color space" to metadata["color"],
                                    "Location" to metadata["location"],
                                    "Latitude" to metadata["latitude"],
                                    "Longitude" to metadata["longitude"],
                                    "Title" to (metadata["title"]?.takeIf { it.isNotBlank() }
                                        ?: item.name.substringBeforeLast('.').takeIf { it.isNotBlank() }),
                                    "Description" to metadata["description"],
                                    "Creator" to metadata["creator"],
                                    "Copyright" to metadata["copyright"],
                                    "Subject" to metadata["subject"],
                                    "Keywords" to metadata["keywords"],
                                    "Rating" to metadata["rating"],
                                    "Label" to metadata["label"],
                                    "Capture date" to (metadata["capture_date"]?.takeIf { it.isNotBlank() }
                                        ?: item.dateTakenMillis.takeIf { it > 0 }?.let { formatIsoDate(it, item.dateAddedSeconds) })
                                )
                                rows.mapNotNull { (label, value) ->
                                    value?.trim()?.takeIf { it.isNotEmpty() && it != "Not available" }
                                        ?.let { label to it }
                                }
                            }
                            if (metadataRows.isNotEmpty()) {
                                DetailCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            Icons.Default.Article,
                                            contentDescription = "Image metadata",
                                            modifier = Modifier.size(22.dp).padding(top = 2.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "Metadata",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 260.dp)
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                metadataRows.forEach { (label, value) ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Text(
                                                            label,
                                                            modifier = Modifier.weight(0.9f),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            value,
                                                            modifier = Modifier.weight(1.1f),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showMore) ModalBottomSheet(onDismissRequest = { showMore = false }) {
        if (isBin) {
            ListItem(
                headlineContent = { Text("Restore to gallery") },
                leadingContent = { Icon(Icons.Default.Restore, null) },
                modifier = Modifier.clickable { showMore = false; onToggleBin() }
            )
        } else {
            ListItem(
                headlineContent = { Text(if (isHidden) "Unhide (return to gallery)" else "Move to Locked folder") },
                leadingContent = { Icon(if (isHidden) Icons.Default.Visibility else Icons.Default.Lock, null) },
                modifier = Modifier.clickable { showMore = false; onToggleHide() }
            )
            ListItem(
                headlineContent = { Text(if (isArchived) "Unarchive" else "Archive") },
                leadingContent = { Icon(Icons.Default.Archive, null) },
                modifier = Modifier.clickable { showMore = false; onToggleArchive() }
            )
            ListItem(
                headlineContent = { Text("Move to Bin") },
                leadingContent = { Icon(Icons.Default.Delete, null) },
                modifier = Modifier.clickable { showMore = false; onToggleBin() }
            )
        }
        ListItem(
            headlineContent = { Text("Media information") },
            leadingContent = { Icon(Icons.Default.Info, null) },
            modifier = Modifier.clickable { showMore = false; if (!showDetails) onToggleDetails() }
        )
        ListItem(
            headlineContent = { Text("Cancel") },
            leadingContent = { Icon(Icons.Default.Close, null) },
            modifier = Modifier.clickable { showMore = false }
        )
        Spacer(Modifier.height(24.dp))
    }
}

