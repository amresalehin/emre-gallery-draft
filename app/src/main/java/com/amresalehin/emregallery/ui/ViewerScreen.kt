package com.amresalehin.emregallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Fullscreen media viewer screen supporting image zoom/pan, ExoPlayer video playback,
 * interactive spring drag-to-dismiss gestures, controls overlays, and a slide-up details drawer.
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
    onAddTo: () -> Unit,
    onOpenNeighbor: (MediaItem) -> Unit,
    onOpenMap: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfig = remember(context) { ViewConfiguration.get(context) }
    val minFlingVelocity = remember(viewConfig) { viewConfig.scaledMinimumFlingVelocity.toFloat() }

    // Video playback management
    val videoState = rememberViewerVideoState(
        uri = item.uri,
        isVideo = item.isVideo,
        showControls = showControls
    )

    // "More" bottom sheet state
    var showMore by remember { mutableStateOf(false) }

    // Interactive vertical drag-to-dismiss & drag-to-reveal gestures
    val dragOffsetY = remember { Animatable(0f) }
    LaunchedEffect(item.uri) {
        dragOffsetY.snapTo(0f)
    }

    var detailsRevealTriggered by remember(item.uri) { mutableStateOf(false) }
    var detailsRevealPx by remember(item.uri) { mutableFloatStateOf(0f) }
    var detailsSheetHeightPx by remember(item.uri) { mutableFloatStateOf(0f) }

    val currentOffsetY = dragOffsetY.value
    val dismissFraction = (abs(currentOffsetY) / 540f).coerceIn(0f, 1f)
    val backdropAlpha = (1f - dismissFraction * 0.70f).coerceIn(0.30f, 1f)

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
        }
    }

    // Scroll to new target page if parent changes selected media
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = backdropAlpha))
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !showDetails,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        val nextOffset = dragOffsetY.value + delta
                        dragOffsetY.snapTo(nextOffset)
                        val revealThreshold = viewConfig.scaledTouchSlop * 1.25f
                        if (nextOffset < -revealThreshold && !detailsRevealTriggered) {
                            detailsRevealTriggered = true
                            detailsRevealPx = (-nextOffset).coerceIn(0f, detailsSheetHeightPx)
                            onToggleDetails()
                        } else if (detailsRevealTriggered) {
                            detailsRevealPx = (-nextOffset).coerceIn(0f, detailsSheetHeightPx)
                        }
                    }
                },
                onDragStopped = { velocity ->
                    val dismissDistanceThreshold = with(density) { 140.dp.toPx() }
                    val detailsDistanceThreshold = with(density) { 64.dp.toPx() }

                    if (dragOffsetY.value > dismissDistanceThreshold || velocity > minFlingVelocity * 1.4f) {
                        coroutineScope.launch {
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
                        coroutineScope.launch {
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
                        coroutineScope.launch {
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
        // Horizontal Media Pager
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
                // Media Image preview
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

                // Video Surface when current item is a video
                if (currentItem.isVideo && currentItem.uri == item.uri) {
                    VideoPlayerSurface(videoState = videoState)
                }
            }
        }

        // Edge gradient shading behind top and bottom bars
        ViewerEdgeShading(visible = showControls && !showDetails)

        // Video playback controls bar
        if (showControls && !showDetails && item.isVideo) {
            VideoPlayerControlsBar(
                videoState = videoState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Top App Bar & Bottom Action Bar
        if (showControls && !showDetails) {
            ViewerTopAppBar(
                isFavourite = favourite,
                onBack = onBack,
                onShare = shareMedia,
                onToggleFavourite = onFavourite,
                onMore = { showMore = true }
            )

            ViewerBottomActionBar(
                isBin = isBin,
                onEdit = launchEdit,
                onToggleBin = onToggleBin
            )
        }

        // Slide-up details drawer
        ViewerDetailsDrawer(
            item = item,
            items = items,
            customAlbums = customAlbums,
            customTags = customTags,
            showDetails = showDetails,
            detailsRevealTriggered = detailsRevealTriggered,
            detailsRevealPx = detailsRevealPx,
            detailsSheetHeightPx = detailsSheetHeightPx,
            onHeightMeasured = { detailsSheetHeightPx = it },
            onRevealPxChanged = { detailsRevealPx = it },
            onToggleDetails = onToggleDetails,
            onShare = shareMedia,
            onAddTo = onAddTo,
            onToggleBin = onToggleBin,
            onShowMore = { showMore = true },
            onOpenMap = onOpenMap,
            onAddTag = onAddTag,
            isBin = isBin,
            defaultOcrLanguages = defaultOcrLanguages,
            defaultOcrModel = defaultOcrModel
        )
    }

    // "More" options modal bottom sheet
    if (showMore) {
        ViewerMoreBottomSheet(
            isBin = isBin,
            isHidden = isHidden,
            isArchived = isArchived,
            onToggleBin = onToggleBin,
            onToggleHide = onToggleHide,
            onToggleArchive = onToggleArchive,
            onShowDetails = { if (!showDetails) onToggleDetails() },
            onDismiss = { showMore = false }
        )
    }
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
