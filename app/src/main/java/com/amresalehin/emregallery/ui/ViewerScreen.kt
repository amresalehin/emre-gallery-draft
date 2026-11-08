package com.amresalehin.emregallery.ui
import android.view.ViewConfiguration
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import com.amresalehin.emregallery.data.StoryGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.coroutineScope
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
    storyTitle: String? = null,
    storyGroups: List<StoryGroup> = emptyList(),
    initialStoryIndex: Int = 0,
    defaultOcrLanguages: Set<String> = setOf("eng"),
    defaultOcrModel: String = "fast",
    defaultMlKitLanguages: Set<String> = setOf("latin"),
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
    onOpenPerson: ((personId: String, engineId: String) -> Unit)? = null,
    transitionProgress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfig = remember(context) { ViewConfiguration.get(context) }
    val minFlingVelocity = remember(viewConfig) { viewConfig.scaledMinimumFlingVelocity.toFloat() }
    var sessionPlayIntent by remember { mutableStateOf(true) }
    val videoState = rememberViewerVideoState(
        uri = item.uri,
        isVideo = item.isVideo,
        showControls = showControls,
        sessionPlayIntent = sessionPlayIntent,
        onPlayIntentChange = { sessionPlayIntent = it }
    )
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
                stiffness = 500f
            )
        )
    }
    var showMore by remember { mutableStateOf(false) }
    var confirmPermanentDelete by remember { mutableStateOf(false) }
    var confirmMoveToBin by remember { mutableStateOf(false) }
    var isCurrentZoomed by remember { mutableStateOf(false) }
    val dismissOffsetY = remember { Animatable(0f) }
    LaunchedEffect(item.uri) {
        dismissOffsetY.snapTo(0f)
    }
    val isStoryMode = storyTitle != null || storyGroups.isNotEmpty()
    var activeStoryIndex by remember(storyGroups, initialStoryIndex) {
        mutableIntStateOf(initialStoryIndex.coerceIn(0, (storyGroups.size - 1).coerceAtLeast(0)))
    }
    val currentStory = remember(storyGroups, activeStoryIndex, storyTitle, items) {
        if (storyGroups.isNotEmpty()) {
            storyGroups.getOrElse(activeStoryIndex.coerceIn(0, storyGroups.size - 1)) {
                StoryGroup(storyTitle ?: "", items)
            }
        } else {
            StoryGroup(storyTitle ?: "", items)
        }
    }
    val currentStoryTitle = currentStory.title
    val currentStoryItems = currentStory.items.ifEmpty { items }
    val allStoryItems = remember(storyGroups, items) {
        if (storyGroups.isNotEmpty()) storyGroups.flatMap { it.items }.ifEmpty { items } else items
    }
    val initialIndex = remember(items, currentStoryItems, isStoryMode, item.uri) {
        val list = if (isStoryMode) currentStoryItems else items
        val idx = list.indexOfFirst { it.uri == item.uri }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { if (isStoryMode) currentStoryItems.size else items.size }
    )
    var storyProgress by remember { mutableFloatStateOf(0f) }
    var isStoryHolding by remember { mutableStateOf(false) }
    var showEndOfStory by remember { mutableStateOf(false) }
    val storyCallbacks = rememberViewerStoryAndPagerEffects(
        item, items, isStoryMode, storyGroups, activeStoryIndex, { activeStoryIndex = it },
        currentStoryItems, storyProgress, { storyProgress = it }, isStoryHolding,
        showEndOfStory, { showEndOfStory = it }, isClosing, showDetails, showMore,
        confirmMoveToBin, confirmPermanentDelete, isCurrentZoomed, pagerState, videoState, onOpenNeighbor
    )
    val stepNext = storyCallbacks.stepNext
    val stepPrevious = storyCallbacks.stepPrevious
    val shareMedia: () -> Unit = {
        val current = (if (isStoryMode) currentStoryItems else items).getOrNull(pagerState.currentPage) ?: item
        shareMediaItem(context, current)
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val ReturnSpringSpec = remember {
            spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 650f
            )
        }
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
        BackHandler(enabled = videoState.isFullscreen) {
            videoState.isFullscreen = false
        }
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
                transitionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = ReturnSpringSpec
                )
            }
        }
        BackHandler(enabled = !videoState.isFullscreen && !showDetails && !isClosing) {
            initiateClose()
        }
        val targetBounds = ThumbnailBoundsTracker.getBounds(item.uri)
        val bgThemeColor = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (videoState.isFullscreen) {
                        drawRect(Color.Black)
                    } else {
                        val p = transitionProgress.value
                        val currentDismissY = dismissOffsetY.value
                        val dismissFraction = (abs(currentDismissY) / 540f).coerceIn(0f, 1f)
                        val backdropAlpha = (1f - dismissFraction * 0.70f).coerceIn(0.30f, 1f)
                        val scrimAlpha = (p * backdropAlpha).coerceIn(0f, 1f)
                        drawRect(bgThemeColor.copy(alpha = scrimAlpha))
                    }
                }
        ) {
            ViewerHorizontalMediaPager(
                item, items, isStoryMode, currentStoryItems, pagerState, videoState,
                touchSlop, minFlingVelocity, showDetails, showControls,
                onToggleControls, onToggleDetails, { showMore = true }, { isCurrentZoomed = it },
                stepPrevious, stepNext, { isStoryHolding = it }, transitionProgress,
                targetBounds, dismissOffsetY, screenHeightPx, initiateClose
            )
            ViewerScreenOverlaysSection(
                item, items, customAlbums, customTags, favourite, isBin, isHidden, isArchived,
                isStoryMode, isStoryHolding, showEndOfStory, { showEndOfStory = it },
                { showEndOfStory = false; activeStoryIndex = 0; storyProgress = 0f; coroutineScope.launch { pagerState.scrollToPage(0) } },
                currentStoryTitle, currentStoryItems, allStoryItems, storyProgress, showControls, showDetails,
                isClosing, transitionProgress.value, pagerState, videoState, initiateClose, shareMedia,
                onFavourite, { showMore = true }, onToggleBin, { confirmMoveToBin = true }, onToggleHide,
                onToggleArchive, onAddTo, onDeletePermanently, { confirmPermanentDelete = true },
                onToggleDetails, onOpenMap, onAddTag, defaultOcrLanguages, defaultOcrModel, defaultMlKitLanguages, onOpenPerson
            )
        }
    }
    ViewerScreenSheetsAndConfirmDialogs(
        item, isBin, isHidden, isArchived, showMore, { showMore = it }, showDetails, onToggleDetails,
        confirmMoveToBin, { confirmMoveToBin = it }, confirmPermanentDelete, { confirmPermanentDelete = it },
        onToggleBin, onToggleHide, onToggleArchive, onAddTo, onDeletePermanently
    )
}
