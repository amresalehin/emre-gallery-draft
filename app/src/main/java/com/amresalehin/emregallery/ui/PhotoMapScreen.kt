package com.amresalehin.emregallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amresalehin.emregallery.data.MediaItem
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

private const val WORLD_VIEW_ZOOM = 1.0
private val WORLD_VIEW_CENTER = GeoPoint(0.0, 0.0)

fun showWorldView(map: MapView, animated: Boolean = false) {
    if (map.width <= 0 || map.height <= 0) {
        map.post { showWorldView(map, animated) }
        return
    }
    if (animated) {
        map.controller.animateTo(WORLD_VIEW_CENTER)
        map.controller.setZoom(WORLD_VIEW_ZOOM)
    } else {
        map.controller.setCenter(WORLD_VIEW_CENTER)
        map.controller.setZoom(WORLD_VIEW_ZOOM)
    }
    map.invalidate()
}

fun focusSinglePhoto(map: MapView, point: GeoPoint, animated: Boolean = true) {
    if (animated) map.controller.animateTo(point) else map.controller.setCenter(point)
    map.controller.setZoom(15.0)
    map.invalidate()
}

fun fitPointsToMap(
    map: MapView,
    points: List<GeoPoint>,
    animated: Boolean = true,
    paddingPx: Int = 64
) {
    if (points.isEmpty()) {
        showWorldView(map, animated)
        return
    }
    if (points.size == 1) {
        focusSinglePhoto(map, points.first(), animated)
        return
    }

    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }
    val latSpan = north - south
    val lonSpan = kotlin.math.abs(east - west)

    if (latSpan < 0.005 && lonSpan < 0.005) {
        val center = GeoPoint((north + south) / 2.0, (east + west) / 2.0)
        if (animated) map.controller.animateTo(center) else map.controller.setCenter(center)
        map.controller.setZoom(15.0)
        map.invalidate()
        return
    }
    if (latSpan < 0.05 && lonSpan < 0.05) {
        val center = GeoPoint((north + south) / 2.0, (east + west) / 2.0)
        if (animated) map.controller.animateTo(center) else map.controller.setCenter(center)
        map.controller.setZoom(13.5)
        map.invalidate()
        return
    }

    val marginLat = (latSpan * 0.10).coerceAtLeast(0.01)
    val marginLon = (lonSpan * 0.10).coerceAtLeast(0.01)
    val bounds = BoundingBox(
        (north + marginLat).coerceAtMost(85.0),
        (east + marginLon).coerceAtMost(180.0),
        (south - marginLat).coerceAtLeast(-85.0),
        (west - marginLon).coerceAtLeast(-180.0)
    )
    runCatching {
        if (map.width > 0 && map.height > 0) {
            map.zoomToBoundingBox(bounds, animated, paddingPx)
            if (map.zoomLevelDouble > 15.0) map.controller.setZoom(15.0)
        } else {
            map.post {
                map.zoomToBoundingBox(bounds, animated, paddingPx)
                if (map.zoomLevelDouble > 15.0) map.controller.setZoom(15.0)
            }
        }
        map.invalidate()
    }
}

@Composable
fun PhotoMapScreen(
    media: List<MediaItem>,
    focusItem: MediaItem? = null,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    viewModel: PhotoMapViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val locationPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.rescanMedia(media)
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            !hasMediaLocationPermission(context)
        ) {
            locationPermLauncher.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var mapFramed by remember { mutableStateOf(false) }
    var reportedMissingGpsForUri by remember { mutableStateOf<String?>(null) }
    var userHasMovedMap by remember { mutableStateOf(false) }

    LaunchedEffect(media) {
        viewModel.loadAndScanMedia(media)
    }

    LaunchedEffect(uiState.locatedMedia, focusItem, uiState.loading) {
        val requestedItem = focusItem ?: return@LaunchedEffect
        val match = uiState.locatedMedia.firstOrNull { it.first.uri == requestedItem.uri }
        if (match != null) {
            mapRef?.let { focusSinglePhoto(it, match.second, animated = true) }
        } else if (!uiState.loading && reportedMissingGpsForUri != requestedItem.uri) {
            reportedMissingGpsForUri = requestedItem.uri
            snackbarHostState.showSnackbar(
                message = "Cannot focus on \""+requestedItem.name+"\": no accessible GPS coordinates found.",
                duration = SnackbarDuration.Short
            )
            mapRef?.let { map ->
                if (uiState.locatedMedia.isNotEmpty()) {
                    fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = true)
                } else {
                    showWorldView(map, animated = true)
                }
            }
        }
    }

    // Show the world while indexing, then frame valid locations once. Empty scans
    // stay world-wide; repeated index emissions must not recenter the user's map.
    LaunchedEffect(uiState.locatedMedia, uiState.loading) {
        val map = mapRef
        if (!mapFramed && map != null && map.width > 0 && map.height > 0) {
            when {
                uiState.locatedMedia.isNotEmpty() && focusItem == null -> {
                    fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = false)
                    mapFramed = true
                }
                !uiState.loading && uiState.locatedMedia.isEmpty() -> {
                    if (!userHasMovedMap) showWorldView(map, animated = false)
                    mapFramed = true
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapRef?.onPause()
            mapRef?.onDetach()
            mapRef = null
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            PhotoMapTopBar(
                mapLayer = uiState.mapLayer,
                showMapLayerMenu = uiState.showMapLayerMenu,
                onBack = onBack,
                onOpenLayerMenu = { viewModel.setLayerMenuExpanded(true) },
                onDismissLayerMenu = { viewModel.setLayerMenuExpanded(false) },
                onSelectLayer = { layer ->
                    viewModel.setMapLayer(layer)
                    mapRef?.let { map ->
                        map.setTileSource(
                            if (layer == "Satellite") PhotoMapTiles.satelliteTileSource()
                            else TileSourceFactory.MAPNIK
                        )
                        map.invalidate()
                    }
                },
                onFitAllPhotos = {
                    mapRef?.let { map ->
                        if (uiState.locatedMedia.isNotEmpty()) {
                            fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = true)
                        } else {
                            showWorldView(map, animated = true)
                        }
                    }
                },
                onRescan = { viewModel.rescanMedia(media) }
            )

            PhotoMapViewContainer(
                locatedMedia = uiState.locatedMedia,
                mapLayer = uiState.mapLayer,
                loading = uiState.loading,
                scanProgress = uiState.scanProgress,
                scanTotal = uiState.scanTotal,
                onMapReady = { map ->
                    mapRef = map
                    if (!mapFramed && map.width > 0 && map.height > 0) {
                        when {
                            uiState.locatedMedia.isNotEmpty() && focusItem == null -> {
                                fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = false)
                                mapFramed = true
                            }
                            !uiState.loading && uiState.locatedMedia.isEmpty() -> {
                                showWorldView(map, animated = false)
                                mapFramed = true
                            }
                        }
                    }
                },
                onUserMapGesture = { userHasMovedMap = true },
                onBoundsChanged = { bounds -> viewModel.updateVisibleMapBounds(bounds) },
                modifier = Modifier.weight(1f)
            )

            PhotoMapDetailsCard(
                viewportCount = uiState.viewportMedia.size,
                locatedTotal = uiState.locatedMedia.size,
                loading = uiState.loading,
                scanProgress = uiState.scanProgress,
                scanTotal = uiState.scanTotal,
                dayGroups = uiState.dayGroups,
                onPhotoClick = { photo, point ->
                    mapRef?.let { focusSinglePhoto(it, point, animated = true) }
                    onOpen(photo)
                },
                onFitAllPhotos = {
                    mapRef?.let { map ->
                        if (uiState.locatedMedia.isNotEmpty()) {
                            fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = true)
                        } else {
                            showWorldView(map, animated = true)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}
