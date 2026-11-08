package com.amresalehin.emregallery.ui
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.data.LockedVaultManager
import com.amresalehin.emregallery.data.MediaRepository
import com.amresalehin.emregallery.data.PreferencesRepository
import com.amresalehin.emregallery.face.FaceClusterStore
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesRepository(context.applicationContext) }
    val mediaRepository = remember { MediaRepository(context.contentResolver) }
    val lockedVaultManager = remember { LockedVaultManager(context.applicationContext) }
    val appState = remember { GalleryAppSessionState(context, prefs, mediaRepository, lockedVaultManager) }
    appState.scope = scope
    appState.homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    appState.favourites = prefs.favourites.collectAsStateWithLifecycle(initialValue = emptySet()).value
    appState.hidden = prefs.hidden.collectAsStateWithLifecycle(initialValue = emptySet()).value
    appState.inBin = prefs.bin.collectAsStateWithLifecycle(initialValue = emptySet()).value
    appState.archive = prefs.archive.collectAsStateWithLifecycle(initialValue = emptySet()).value
    appState.recentSearches = prefs.recentSearches.collectAsStateWithLifecycle(initialValue = emptyList()).value
    appState.themePref = prefs.theme.collectAsStateWithLifecycle(initialValue = remember { prefs.getThemeSync() }).value
    appState.customAlbums = prefs.customAlbums.collectAsStateWithLifecycle(initialValue = emptyList()).value
    appState.sortOrderPref = prefs.sortOrder.collectAsStateWithLifecycle(initialValue = "date_desc").value
    appState.groupByPref = prefs.groupBy.collectAsStateWithLifecycle(initialValue = "day").value
    appState.gridColumnsPref = prefs.gridColumns.collectAsStateWithLifecycle(initialValue = 3).value
    appState.gridStylePref = prefs.gridStyle.collectAsStateWithLifecycle(initialValue = "masonry").value
    appState.ocrLanguagesPref = prefs.ocrLanguages.collectAsStateWithLifecycle(initialValue = setOf("eng")).value
    appState.mlKitLanguagesPref = prefs.mlKitLanguages.collectAsStateWithLifecycle(initialValue = setOf("latin")).value
    appState.ocrModelPref = prefs.ocrModel.collectAsStateWithLifecycle(initialValue = "fast").value
    appState.ocrEnginePref = prefs.ocrEngine.collectAsStateWithLifecycle(initialValue = "tesseract").value
    appState.customTagsMap = prefs.customTagsMap.collectAsStateWithLifecycle(initialValue = emptyMap()).value
    appState.customPeopleMap = prefs.customPeopleMap.collectAsStateWithLifecycle(initialValue = emptyMap()).value
    appState.lockedFolderPinHash = prefs.lockedFolderPinHash.collectAsStateWithLifecycle(initialValue = null).value
    val isWelcomeCompletedInit = remember { prefs.isWelcomeCompletedSync() }
    val hasCompletedWelcomeFlow by prefs.hasCompletedWelcome.collectAsStateWithLifecycle(initialValue = isWelcomeCompletedInit)
    appState.hasCompletedWelcome = hasCompletedWelcomeFlow || isWelcomeCompletedInit
    with(appState) {
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            lockedVaultItems = runCatching { lockedVaultManager.getVaultItems() }.getOrDefault(emptyList())
        }
        if (hasMediaPermission(context)) refreshMedia()
    }
    LaunchedEffect(sortOrderPref, groupByPref, hidden, inBin, archive) {
        if (rawMedia.isNotEmpty()) {
            val processed = withContext(Dispatchers.Default) {
                val eligible = rawMedia.filter { it.uri !in hidden && !it.isTrashed && it.uri !in inBin && it.uri !in archive }
                processGalleryMedia(eligible, sortOrderPref, groupByPref, galleryData.isComplete)
            }
            galleryData = processed
        }
    }
    LaunchedEffect(media, hidden, inBin) {
        if (media.isNotEmpty()) {
            delay(3000)
            withContext(Dispatchers.IO) {
                runCatching {
                    val locationCache = com.amresalehin.emregallery.data.PhotoMapLocationCache(context.applicationContext)
                    val safeMedia = media.filter { it.uri !in hidden && !it.isTrashed && it.uri !in inBin }
                    locationCache.scanMediaBatch(context, safeMedia)
                }
            }
        }
    }
    permanentDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val deletedUris = pendingPermanentDeleteUris
        pendingPermanentDeleteUris = emptyList()
        if (result.resultCode == android.app.Activity.RESULT_OK && deletedUris.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    FaceClusterStore(context).deleteMediaCompletely(deletedUris)
                }
                prefs.removePermanently(deletedUris)
                refreshMedia()
                snackbarHostState.showSnackbar(
                    message = "${deletedUris.size} ${if (deletedUris.size == 1) "item" else "items"} permanently deleted",
                    duration = SnackbarDuration.Short
                )
            }
        } else if (result.resultCode != android.app.Activity.RESULT_OK) {
            scope.launch {
                snackbarHostState.showSnackbar("Deletion cancelled")
            }
        }
    }
    restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val uris = pendingRestoreUris
        pendingRestoreUris = emptyList()
        if (result.resultCode == android.app.Activity.RESULT_OK && uris.isNotEmpty()) {
            scope.launch {
                prefs.setInBinBatch(uris.toSet(), false)
                refreshMedia()
                snackbarHostState.showSnackbar("Restored to gallery", duration = SnackbarDuration.Short)
            }
        }
    }
    trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val uris = pendingTrashUris
        pendingTrashUris = emptyList()
        if (result.resultCode == android.app.Activity.RESULT_OK && uris.isNotEmpty()) {
            scope.launch {
                prefs.setInBinBatch(uris.toSet(), true)
                prefs.setHiddenBatch(uris.toSet(), false)
                refreshMedia()
                val res = snackbarHostState.showSnackbar(
                    message = if (uris.size == 1) "Moved to Bin" else "${uris.size} items moved to Bin",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) {
                    restoreFromDeviceTrash(media.filter { it.uri in uris })
                }
            }
        }
    }
    val canGoBack = homeSelectedUris.isNotEmpty() || backStack.isNotEmpty() || destination != Destination.HOME || albumFilter != null || showSidePanel || (destination == Destination.VIEWER && showDetails)
    PredictiveBackHandler(enabled = canGoBack) { progress: Flow<BackEventCompat> ->
        try {
            isPredictiveBackActive = true
            progress.collect { backEvent ->
                predictiveBackProgress = backEvent.progress
                predictiveBackEdge = backEvent.swipeEdge
            }
            performBackNavigation()
        } catch (e: CancellationException) {
            predictiveBackProgress = 0f
            throw e
        } finally {
            isPredictiveBackActive = false
            predictiveBackProgress = 0f
        }
    }
        UpdateAlbumAndVisibleMediaState()
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        topContentPadding = statusBarTop + 68.dp
        bottomContentPadding = navBarBottom + 88.dp
    LaunchedEffect(Unit) {
        prefs.clearHomeScrollPosition()
    }
    val hasMediaPerm = hasMediaPermission(context)
    val hasNotifPerm = hasNotificationPermission(context)
    val hasAllFilesPerm = hasAllFilesAccess()
    val allRequiredPermissions = hasMediaPerm && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasNotifPerm) && hasAllFilesPerm
    if (!hasCompletedWelcome || !allRequiredPermissions) {
        WelcomeScreen(
            initialStep = if (hasCompletedWelcome) 2 else 0,
            onComplete = {
                scope.launch {
                    prefs.setCompletedWelcome(true)
                    refreshMedia()
                }
            }
        )
        return
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isPredictiveBackActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
            }
            val baseDestination = if (destination == Destination.VIEWER) previousDestination else destination
            val returnDestination = if (destination == Destination.VIEWER) previousDestination else destination
            CompositionLocalProvider(LocalViewerCoordinatedProgress provides { if (destination == Destination.VIEWER) viewerTransitionProgress.value else 0f }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isPredictiveBackActive && predictiveBackProgress > 0f) {
                                val p = predictiveBackProgress
                                val scale = 1f - (p * 0.12f)
                                scaleX = scale
                                scaleY = scale
                                val maxShift = 48.dp.toPx()
                                translationX = if (predictiveBackEdge == BackEventCompat.EDGE_LEFT) {
                                    p * maxShift
                                } else {
                                    -p * maxShift
                                }
                                shape = RoundedCornerShape((p * 28).dp)
                                clip = true
                                shadowElevation = (p * 20).dp.toPx()
                            }
                        }
                        .pointerInput(destination, showSidePanel, albumFilter) {
                            if (!showSidePanel && destination in listOf(Destination.HOME, Destination.ALBUMS, Destination.SETTINGS) && albumFilter == null) {
                                val edgeWidth = 32.dp.toPx()
                                val slop = viewConfiguration.touchSlop * 1.5f
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (down.position.x <= edgeWidth) {
                                        var totalDragX = 0f
                                        var totalDragY = 0f
                                        do {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change != null) {
                                                totalDragX += change.position.x - change.previousPosition.x
                                                totalDragY += change.position.y - change.previousPosition.y
                                                if (totalDragX > slop && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.5f) {
                                                    showSidePanel = true
                                                    change.consume()
                                                    break
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                            }
                        }
                ) {
                    if (!RenderPrimaryDestination(baseDestination)) {
                        RenderSecondaryDestination(baseDestination)
                    }
                }
            }
            RenderViewerAndChromeOverlays(this, returnDestination)
            RenderBottomNavAndDrawer(this, returnDestination)
        }
    }
    RenderGlobalDialogsAndSheets()
    }
}
