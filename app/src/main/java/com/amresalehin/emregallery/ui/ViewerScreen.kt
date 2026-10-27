package com.amresalehin.emregallery.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.ViewConfiguration
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Fullscreen media viewer screen supporting:
 * - Shared-element return animation back to original source thumbnail
 * - Playback continuity between videos (respects user Play/Pause intent)
 * - Safe layout of video playback controls without overlapping bottom action buttons
 * - True fullscreen immersive video playback with status/navigation bar hiding and pure black backdrop
 * - Fluid pinch/double-tap zoom/pan
 * - Unified 1:1 gesture-coordinated details drawer
 */
@Composable
fun ViewerScreen(
    item: MediaItem,
    items: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    defaultOcrLanguages: Set<String> = setOf("eng"),
    defaultOcrModel: String = "fast",
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
    onDeletePermanently: (() -> Unit)? = null,
    onAddTo: () -> Unit,
    onOpenNeighbor: (MediaItem) -> Unit,
    onOpenMap: () -> Unit,
    transitionProgress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
    onOpenPerson: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfig = remember(context) { ViewConfiguration.get(context) }
    val minFlingVelocity = remember(viewConfig) { viewConfig.scaledMinimumFlingVelocity.toFloat() }

    // Session-wide playback intent: true by default, updated on manual user Play/Pause.
    // Switching videos retains user's paused or playing intent.
    var sessionPlayIntent by remember { mutableStateOf(true) }

    // Video playback management with intent continuity
    val videoState = rememberViewerVideoState(
        uri = item.uri,
        isVideo = item.isVideo,
        showControls = showControls,
        sessionPlayIntent = sessionPlayIntent,
        onPlayIntentChange = { sessionPlayIntent = it }
    )

    // True fullscreen window immersion management
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(videoState.isFullscreen, activity) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (videoState.isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(item.uri) {
        ThumbnailBoundsTracker.activeViewerUri = item.uri
        transitionProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 420f
            )
        )
    }

    // "More" bottom sheet state
    var showMore by remember { mutableStateOf(false) }
    var confirmPermanentDelete by remember { mutableStateOf(false) }
    var confirmMoveToBin by remember { mutableStateOf(false) }

    // Zoom state of current active image
    var isCurrentZoomed by remember { mutableStateOf(false) }

    // Downward drag-to-dismiss offset
    val dismissOffsetY = remember { Animatable(0f) }
    LaunchedEffect(item.uri) {
        dismissOffsetY.snapTo(0f)
    }

    // Horizontal pager for media browsing
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
            ThumbnailBoundsTracker.activeViewerUri = current.uri
        }
    }

    // Scroll to target page if parent changes selected media
    LaunchedEffect(item.uri) {
        val targetIndex = items.indexOfFirst { it.uri == item.uri }
        if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    val shareMedia: () -> Unit = {
        shareMediaItem(context, item)
    }

    val launchEdit: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_EDIT)
                    .setDataAndType(Uri.parse(item.uri), item.mimeType ?: "image/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        val ReturnSpringSpec = remember {
            spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 380f
            )
        }

        // Shared return animation close initiator
        val initiateClose: () -> Unit = {
            if (!isClosing) {
                isClosing = true
                coroutineScope.launch {
                    if (showDetails) {
                        onToggleDetails()
                    }
                    ThumbnailBoundsTracker.activeViewerUri = item.uri
                    transitionProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = ReturnSpringSpec
                    )
                    ThumbnailBoundsTracker.activeViewerUri = null
                    onBack()
                }
            }
        }

        // --- Android Back Handling Priority ---
        // Priority 1: Video fullscreen mode exits first
        BackHandler(enabled = videoState.isFullscreen) {
            videoState.isFullscreen = false
        }

        // Priority 2: Only when details sheet is NOT showing does Predictive Back exit the viewer
        PredictiveBackHandler(enabled = !videoState.isFullscreen && !showDetails && !isClosing) { progressFlow: Flow<BackEventCompat> ->
            try {
                ThumbnailBoundsTracker.activeViewerUri = item.uri
                progressFlow.collect { backEvent ->
                    transitionProgress.snapTo((1f - backEvent.progress).coerceIn(0f, 1f))
                }
                isClosing = true
                transitionProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = ReturnSpringSpec
                )
                ThumbnailBoundsTracker.activeViewerUri = null
                onBack()
            } catch (e: CancellationException) {
                // Return transition was cancelled or interrupted
                transitionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = ReturnSpringSpec
                )
            }
        }

        // Standard Back when details sheet is NOT showing
        BackHandler(enabled = !videoState.isFullscreen && !showDetails && !isClosing) {
            initiateClose()
        }

        // Calculate geometry for shared-element transition toward original source thumbnail
        val progress = transitionProgress.value
        val targetBounds = remember(item.uri) { ThumbnailBoundsTracker.getBounds(item.uri) }

        val destTransX: Float
        val destTransY: Float
        val destScaleX: Float
        val destScaleY: Float
        if (targetBounds != null) {
            val thumbW = targetBounds.width
            val thumbH = targetBounds.height
            val thumbCenterX = targetBounds.left + thumbW / 2f
            val thumbCenterY = targetBounds.top + thumbH / 2f
            val screenCenterX = screenWidthPx / 2f
            val screenCenterY = screenHeightPx / 2f
            destTransX = thumbCenterX - screenCenterX
            destTransY = thumbCenterY - screenCenterY
            destScaleX = (thumbW / screenWidthPx).coerceIn(0.01f, 1f)
            destScaleY = (thumbH / screenHeightPx).coerceIn(0.01f, 1f)
        } else {
            destTransX = 0f
            destTransY = 0f
            destScaleX = 0.85f
            destScaleY = 0.85f
        }

        val currentScaleX = destScaleX + (1f - destScaleX) * progress
        val currentScaleY = destScaleY + (1f - destScaleY) * progress
        val currentTransX = destTransX * (1f - progress)
        val currentTransY = destTransY * (1f - progress) + dismissOffsetY.value * progress
        val cornerRadius = ((1f - progress) * 10f).dp
        // Do NOT fade out the image before reaching progress == 0 when targetBounds is present.
        // It stays fully opaque so it lands directly on top of the thumbnail with zero blank dip/flicker.
        val viewerAlpha = if (targetBounds == null && isClosing) progress.coerceIn(0f, 1f) else 1f

        // Background scrim calculation
        val currentDismissY = dismissOffsetY.value
        val dismissFraction = (abs(currentDismissY) / 540f).coerceIn(0f, 1f)
        val backdropAlpha = (1f - dismissFraction * 0.70f).coerceIn(0.30f, 1f)
        val scrimAlpha = if (videoState.isFullscreen) 1f else (progress * backdropAlpha).coerceIn(0f, 1f)
        val controlsAlpha = if (isClosing) ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f) else 1f
        val outgoingTopBarOffset = with(density) { -(80.dp.toPx()) * (1f - progress) }
        val outgoingBottomBarOffset = with(density) { (80.dp.toPx()) * (1f - progress) }

        val backgroundColor = if (videoState.isFullscreen) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.background.copy(alpha = scrimAlpha)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            // Horizontal Media Pager with coordinated shared-element geometry
            HorizontalPager(
                state = pagerState,
                flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
                userScrollEnabled = !isCurrentZoomed && !videoState.isFullscreen && !showDetails,
                pageSpacing = 16.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = currentTransX
                        translationY = currentTransY
                        scaleX = currentScaleX
                        scaleY = currentScaleY
                        alpha = viewerAlpha
                        clip = progress < 0.99f || dismissFraction > 0.02f
                        shape = RoundedCornerShape(cornerRadius)
                    },
                key = { pageIndex -> items.getOrNull(pageIndex)?.uri ?: pageIndex.toString() }
            ) { pageIndex ->
                val currentItem = items.getOrNull(pageIndex) ?: item
                val isCurrent = pageIndex == pagerState.currentPage

                ZoomableMediaItem(
                    item = currentItem,
                    isCurrentPage = isCurrent,
                    isVideo = currentItem.isVideo,
                    videoState = if (currentItem.isVideo && currentItem.uri == item.uri) videoState else null,
                    touchSlop = viewConfig.scaledTouchSlop.toFloat(),
                    minFlingVelocity = minFlingVelocity,
                    onToggleControls = {
                        if (!showDetails) onToggleControls()
                    },
                    onLongPress = {
                        if (!showDetails && !videoState.isFullscreen) showMore = true
                    },
                    onSwipeUp = {
                        if (!showDetails && !videoState.isFullscreen) {
                            onToggleDetails()
                        }
                    },
                    onSwipeDownDelta = { deltaDown ->
                        // Downward gesture on media is strictly for media closing when details are closed
                        if (!isCurrentZoomed && !showDetails && !videoState.isFullscreen) {
                            coroutineScope.launch {
                                val next = (dismissOffsetY.value + deltaDown).coerceAtLeast(0f)
                                dismissOffsetY.snapTo(next)
                                val p = (1f - (next / (screenHeightPx * 0.45f)).coerceIn(0f, 0.40f))
                                transitionProgress.snapTo(p)
                            }
                        }
                    },
                    onSwipeDownRelease = { velocityDown ->
                        // Media closer cannot activate while details are open
                        if (!isCurrentZoomed && !showDetails && !videoState.isFullscreen) {
                            val dismissDistanceThreshold = screenHeightPx * 0.15f
                            if (dismissOffsetY.value > dismissDistanceThreshold || velocityDown > minFlingVelocity * 1.3f) {
                                coroutineScope.launch {
                                    isClosing = true
                                    ThumbnailBoundsTracker.activeViewerUri = item.uri
                                    launch {
                                        dismissOffsetY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = ReturnSpringSpec
                                        )
                                    }
                                    transitionProgress.animateTo(
                                        targetValue = 0f,
                                        animationSpec = ReturnSpringSpec
                                    )
                                    ThumbnailBoundsTracker.activeViewerUri = null
                                    onBack()
                                }
                            } else {
                                coroutineScope.launch {
                                    launch {
                                        dismissOffsetY.animateTo(
                                            targetValue = 0f,
                                            initialVelocity = velocityDown,
                                            animationSpec = ReturnSpringSpec
                                        )
                                    }
                                    transitionProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = ReturnSpringSpec
                                    )
                                }
                            }
                        }
                    },
                    onZoomChanged = { zoomed ->
                        if (isCurrent) isCurrentZoomed = zoomed
                    },
                    transitionProgress = progress,
                    hasTargetBounds = targetBounds != null
                )
            }

            // Edge gradient shading behind top and bottom bars (hidden in fullscreen or when sheet rises)
            if (!videoState.isFullscreen) {
                ViewerEdgeShading(visible = showControls && !isClosing)
            }

            // Top App Bar in standard view
            if (showControls && !videoState.isFullscreen) {
                ViewerTopAppBar(
                    isFavourite = favourite,
                    onBack = { initiateClose() },
                    onShare = shareMedia,
                    onToggleFavourite = onFavourite,
                    onMore = { showMore = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            translationY = outgoingTopBarOffset
                            alpha = controlsAlpha
                        }
                )
            }

            // Fullscreen quick exit back button
            if (videoState.isFullscreen && showControls) {
                IconButton(
                    onClick = { videoState.isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Exit fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Coordinated bottom controls cluster: Video Player Controls Bar stacked cleanly above ViewerBottomActionBar.
            if (showControls) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .graphicsLayer {
                            translationY = outgoingBottomBarOffset
                            alpha = controlsAlpha
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (item.isVideo) {
                        VideoPlayerControlsBar(
                            videoState = videoState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (videoState.isFullscreen) 20.dp else 4.dp)
                        )
                    }

                    if (!videoState.isFullscreen) {
                        ViewerBottomActionBar(
                            isBin = isBin,
                            onEdit = launchEdit,
                            onToggleBin = {
                                if (isBin) {
                                    onToggleBin()
                                } else {
                                    confirmMoveToBin = true
                                }
                            },
                            onDeletePermanently = if (isBin && onDeletePermanently != null) { { confirmPermanentDelete = true } } else null
                        )
                    }
                }
            }

            // Native Media Details Drawer (Modal Bottom Sheet)
            if (showDetails && !videoState.isFullscreen && !isClosing) {
                ViewerDetailsDrawer(
                    item = item,
                    items = items,
                    customAlbums = customAlbums,
                    customTags = customTags,
                    onClose = {
                        if (showDetails) onToggleDetails()
                    },
                    onShare = shareMedia,
                    onAddTo = onAddTo,
                    onToggleBin = {
                        if (isBin) {
                            onToggleBin()
                        } else {
                            confirmMoveToBin = true
                        }
                    },
                    onShowMore = { showMore = true },
                    onOpenMap = onOpenMap,
                    onAddTag = onAddTag,
                    isBin = isBin,
                    defaultOcrLanguages = defaultOcrLanguages,
                    defaultOcrModel = defaultOcrModel,
                    onOpenPerson = { personId ->
                        if (showDetails) onToggleDetails()
                        onOpenPerson(personId)
                    }
                )
            }
        }
    }

    // "More" options modal bottom sheet
    if (showMore) {
        ViewerMoreBottomSheet(
            isBin = isBin,
            isHidden = isHidden,
            isArchived = isArchived,
            onToggleBin = {
                if (isBin) {
                    onToggleBin()
                } else {
                    confirmMoveToBin = true
                }
            },
            onToggleHide = onToggleHide,
            onToggleArchive = onToggleArchive,
            onDeletePermanently = if (isBin && onDeletePermanently != null) { { confirmPermanentDelete = true } } else null,
            onShowDetails = { if (!showDetails) onToggleDetails() },
            onDismiss = { showMore = false }
        )
    }

    // Confirmation dialog for moving to Bin
    if (confirmMoveToBin) {
        AlertDialog(
            onDismissRequest = { confirmMoveToBin = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
            title = { Text("Move to Bin?") },
            text = { Text("Move \"${item.name}\" to the device Bin? You can restore it later from the Bin.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmMoveToBin = false
                        onToggleBin()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text("Move to Bin")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMoveToBin = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Confirmation dialog for permanent delete
    if (confirmPermanentDelete) {
        AlertDialog(
            onDismissRequest = { confirmPermanentDelete = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
            title = { Text("Delete permanently?") },
            text = { Text("Permanently delete \"${item.name}\" from this device? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmPermanentDelete = false
                        onDeletePermanently?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPermanentDelete = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

/**
 * Individual media page supporting pinch-to-zoom, pan while zoomed,
 * double-tap to zoom in/reset, and vertical upward/downward gestures when unzoomed.
 */
@Composable
private fun ZoomableMediaItem(
    item: MediaItem,
    isCurrentPage: Boolean,
    isVideo: Boolean,
    videoState: ViewerVideoState?,
    touchSlop: Float,
    minFlingVelocity: Float,
    onToggleControls: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDownDelta: (Float) -> Unit,
    onSwipeDownRelease: (Float) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    transitionProgress: Float = 1f,
    hasTargetBounds: Boolean = false,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var scale by remember(item.uri) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.uri) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.uri) { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Keep video posters on the same cached MediaStore thumbnail used by the grid.
    // Decoding an independent video frame here can make the poster change before ExoPlayer's first frame.
    val context = LocalContext.current
    val imageRequest = remember(item.uri, isVideo, context) {
        val request = ImageRequest.Builder(context)
            .data(Uri.parse(item.uri))
            .diskCachePolicy(CachePolicy.ENABLED)

        if (isVideo) {
            request
                .size(480, 480)
                .precision(Precision.INEXACT)
                .crossfade(false)
                .diskCacheKey("${item.uri}_thumb")
                .memoryCacheKey("${item.uri}_thumb")
        } else {
            request
                .size(Size.ORIGINAL)
                .precision(Precision.EXACT)
                .placeholderMemoryCacheKey("${item.uri}_thumb")
                .diskCacheKey("${item.uri}:viewer-original")
                .memoryCacheKey("${item.uri}:viewer-original")
                .crossfade(100)
        }
        request.build()
    }

    // Reset zoom when navigating away from this page
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            onZoomChanged(false)
        }
    }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.05f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(item.uri, isVideo) {
                containerSize = size
                val width = size.width.toFloat()
                val height = size.height.toFloat()

                var lastTapTime = 0L
                var lastTapPos = Offset.Zero

                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        var isPinching = false
                        var isDragging = false
                        var isVerticalSwipe = false
                        var isHorizontalBrowse = false
                        var swipeDirection = 0 // 1 = down (dismiss), -1 = up (drawer)
                        var totalMovementX = 0f
                        var totalMovementY = 0f
                        var longPressTriggered = false

                        val longPressJob = launch {
                            delay(500L)
                            if (!isDragging && !isPinching && scale <= 1.02f) {
                                longPressTriggered = true
                                onLongPress()
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val pointerCount = event.changes.size

                            if (pointerCount >= 2 && !isVideo) {
                                isPinching = true
                                longPressJob.cancel()

                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid()

                                val oldScale = scale
                                val newScale = (oldScale * zoomChange).coerceIn(1f, 4.5f)

                                if (oldScale > 0f) {
                                    val scaleRatio = newScale / oldScale
                                    val focalX = centroid.x - width / 2f
                                    val focalY = centroid.y - height / 2f
                                    val newOffsetX = (offsetX - focalX) * scaleRatio + focalX + panChange.x
                                    val newOffsetY = (offsetY - focalY) * scaleRatio + focalY + panChange.y

                                    val maxX = (width * (newScale - 1f)) / 2f
                                    val maxY = (height * (newScale - 1f)) / 2f

                                    scale = newScale
                                    offsetX = newOffsetX.coerceIn(-maxX, maxX)
                                    offsetY = newOffsetY.coerceIn(-maxY, maxY)
                                }

                                event.changes.forEach { it.consume() }
                            } else if (pointerCount == 1) {
                                val change = event.changes.first()
                                if (!change.pressed) break

                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val posChange = change.positionChange()
                                totalMovementX += abs(posChange.x)
                                totalMovementY += abs(posChange.y)

                                if (totalMovementX > touchSlop || totalMovementY > touchSlop) {
                                    longPressJob.cancel()
                                }

                                if (scale > 1.02f && !isVideo) {
                                    // Zero-Tolerance Zoom Lock:
                                    // If underlying media is zoomed in beyond 1.0x scale, disable all sheet gestures.
                                    // All drag touches must strictly belong to media panning.
                                    val maxX = (width * (scale - 1f)) / 2f
                                    val maxY = (height * (scale - 1f)) / 2f

                                    offsetX = (offsetX + posChange.x).coerceIn(-maxX, maxX)
                                    offsetY = (offsetY + posChange.y).coerceIn(-maxY, maxY)
                                    change.consume()
                                } else {
                                    // Directional Slop Disambiguation:
                                    // If |dx| > |dy|, yield control to horizontal pager browsing.
                                    // Lock into vertical sheet manipulation only when |dy| > |dx|.
                                    if (!isVerticalSwipe && !isHorizontalBrowse) {
                                        val dist = kotlin.math.hypot(totalMovementX, totalMovementY)
                                        if (dist > touchSlop) {
                                            if (totalMovementX >= totalMovementY) {
                                                isHorizontalBrowse = true
                                            } else {
                                                isVerticalSwipe = true
                                                swipeDirection = if (posChange.y < 0) -1 else 1
                                            }
                                        }
                                    }

                                    if (isVerticalSwipe) {
                                        change.consume()
                                        if (posChange.y > 0) {
                                            onSwipeDownDelta(posChange.y)
                                        }
                                    }
                                }
                            } else {
                                break
                            }
                        }

                        longPressJob.cancel()

                        val velocityY = velocityTracker.calculateVelocity().y
                        if (isVerticalSwipe) {
                            if (swipeDirection < 0) {
                                if (velocityY < -minFlingVelocity || totalMovementY > touchSlop * 2f) {
                                    onSwipeUp()
                                }
                            } else {
                                onSwipeDownRelease(velocityY)
                            }
                        } else if (!isPinching && !longPressTriggered && totalMovementX < touchSlop && totalMovementY < touchSlop) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 280L && (down.position - lastTapPos).getDistance() < touchSlop * 3f && !isVideo) {
                                lastTapTime = 0L
                                if (scale > 1.2f) {
                                    launch {
                                        val startScale = scale
                                        val startX = offsetX
                                        val startY = offsetY
                                        animate(0f, 1f, animationSpec = tween(200)) { fraction, _ ->
                                            scale = startScale + (1f - startScale) * fraction
                                            offsetX = startX * (1f - fraction)
                                            offsetY = startY * (1f - fraction)
                                        }
                                    }
                                } else {
                                    launch {
                                        val targetScale = 2.5f
                                        val focalX = down.position.x - width / 2f
                                        val focalY = down.position.y - height / 2f
                                        val maxX = (width * (targetScale - 1f)) / 2f
                                        val maxY = (height * (targetScale - 1f)) / 2f
                                        val targetOffsetX = (-focalX * (targetScale - 1f)).coerceIn(-maxX, maxX)
                                        val targetOffsetY = (-focalY * (targetScale - 1f)).coerceIn(-maxY, maxY)

                                        val startScale = scale
                                        val startX = offsetX
                                        val startY = offsetY
                                        animate(0f, 1f, animationSpec = tween(220)) { fraction, _ ->
                                            scale = startScale + (targetScale - startScale) * fraction
                                            offsetX = startX + (targetOffsetX - startX) * fraction
                                            offsetY = startY + (targetOffsetY - startY) * fraction
                                        }
                                    }
                                }
                            } else {
                                lastTapTime = now
                                lastTapPos = down.position
                                launch {
                                    delay(280L)
                                    if (lastTapTime == now) {
                                        onToggleControls()
                                    }
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentAlignment = Alignment.Center
        ) {
            // Keep this poster in place throughout video preparation and the first-frame handoff.
            AsyncImage(
                model = imageRequest,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (hasTargetBounds) {
                    transitionContentScale(transitionProgress)
                } else {
                    ContentScale.Fit
                }
            )

            // Video Surface when current item is a video
            if (isVideo && videoState != null) {
                VideoPlayerSurface(videoState = videoState)
            }
        }
    }
}

/**
 * Continuously interpolates image scale factors from cover/crop at the thumbnail end
 * of the shared-element transition to contain/fit at the fullscreen end.
 */
internal fun transitionContentScale(progress: Float): ContentScale {
    val fraction = progress.coerceIn(0f, 1f)
    return object : ContentScale {
        override fun computeScaleFactor(
            srcSize: androidx.compose.ui.geometry.Size,
            dstSize: androidx.compose.ui.geometry.Size
        ): ScaleFactor {
            val crop = ContentScale.Crop.computeScaleFactor(srcSize, dstSize)
            val fit = ContentScale.Fit.computeScaleFactor(srcSize, dstSize)
            return ScaleFactor(
                scaleX = crop.scaleX + (fit.scaleX - crop.scaleX) * fraction,
                scaleY = crop.scaleY + (fit.scaleY - crop.scaleY) * fraction
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun shareMediaItem(context: Context, item: MediaItem) {
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
