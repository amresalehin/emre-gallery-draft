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

/**
 * Focuses camera on a single photo location at standard detail zoom (15.0).
 */
fun focusSinglePhoto(map: MapView, point: GeoPoint, animated: Boolean = true) {
    if (animated) {
        map.controller.animateTo(point)
    } else {
        map.controller.setCenter(point)
    }
    map.controller.setZoom(15.0)
    map.invalidate()
}

/**
 * Centralized camera-fitting implementation for framing any group of locations.
 * Defines consistent, predictable behavior for one photo, tightly clustered photos,
 * and widely distributed locations.
 */
fun fitPointsToMap(
    map: MapView,
    points: List<GeoPoint>,
    animated: Boolean = true,
    paddingPx: Int = 64
) {
    if (points.isEmpty()) return

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

    // Tightly clustered photos (span < ~500m)
    if (latSpan < 0.005 && lonSpan < 0.005) {
        val center = GeoPoint((north + south) / 2.0, (east + west) / 2.0)
        if (animated) {
            map.controller.animateTo(center)
        } else {
            map.controller.setCenter(center)
        }
        map.controller.setZoom(15.0)
        map.invalidate()
        return
    }

    // Moderately clustered photos (span < ~5km)
    if (latSpan < 0.05 && lonSpan < 0.05) {
        val center = GeoPoint((north + south) / 2.0, (east + west) / 2.0)
        if (animated) {
            map.controller.animateTo(center)
        } else {
            map.controller.setCenter(center)
        }
        map.controller.setZoom(13.5)
        map.invalidate()
        return
    }

    // Widely distributed locations: calculate bounding box with 10% margin
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
            if (map.zoomLevelDouble > 15.0) {
                map.controller.setZoom(15.0)
            }
        } else {
            map.post {
                map.zoomToBoundingBox(bounds, animated, paddingPx)
                if (map.zoomLevelDouble > 15.0) {
                    map.controller.setZoom(15.0)
                }
            }
        }
        map.invalidate()
    }
}

/**
 * Screen displaying gallery photos on an interactive OpenStreetMap.
 */
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
        if (isGranted) {
            viewModel.rescanMedia(media)
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && !hasMediaLocationPermission(context)) {
            locationPermLauncher.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var mapFramed by remember { mutableStateOf(false) }
    var reportedMissingGpsForUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(media) {
        viewModel.loadAndScanMedia(media)
    }

    // Camera handling for focusItem (viewer-originated or user selection)
    LaunchedEffect(uiState.locatedMedia, focusItem, uiState.loading) {
        val target = focusItem ?: return@LaunchedEffect
        val match = uiState.locatedMedia.firstOrNull { it.first.uri == target.uri }
        if (match != null) {
            mapRef?.let { map ->
                focusSinglePhoto(map, match.second, animated = true)
            }
        } else if (!uiState.loading && reportedMissingGpsForUri != target.uri) {
            reportedMissingGpsForUri = target.uri
            snackbarHostState.showSnackbar(
                message = "Cannot focus on \"${target.name}\": no accessible GPS coordinates found.",
                duration = SnackbarDuration.Short
            )
            // Fit all available photos instead of doing nothing
            mapRef?.let { map ->
                if (uiState.locatedMedia.isNotEmpty()) {
                    fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = true)
                }
            }
        }
    }

    // Auto-frame all located photos on initial load if no specific focusItem is provided
    LaunchedEffect(uiState.locatedMedia) {
        val map = mapRef
        if (uiState.locatedMedia.isNotEmpty() && !mapFramed && map != null && map.width > 0 && map.height > 0) {
            if (focusItem == null) {
                fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = false)
            }
            mapFramed = true
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
        Modifier
            .fillMaxSize()
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
                            if (layer == "Satellite") {
                                PhotoMapTiles.satelliteTileSource()
                            } else {
                                TileSourceFactory.MAPNIK
                            }
                        )
                        map.invalidate()
                    }
                },
                onFitAllPhotos = {
                    mapRef?.let { map ->
                        if (uiState.locatedMedia.isNotEmpty()) {
                            fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = true)
                        }
                    }
                },
                onRescan = {
                    viewModel.rescanMedia(media)
                }
            )

            PhotoMapViewContainer(
                locatedMedia = uiState.locatedMedia,
                mapLayer = uiState.mapLayer,
                loading = uiState.loading,
                scanProgress = uiState.scanProgress,
                scanTotal = uiState.scanTotal,
                onMapReady = { map ->
                    mapRef = map
                    if (uiState.locatedMedia.isNotEmpty() && !mapFramed) {
                        if (focusItem == null) {
                            fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = false)
                        }
                        mapFramed = true
                    }
                },
                onBoundsChanged = { bounds ->
                    viewModel.updateVisibleMapBounds(bounds)
                },
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
                    mapRef?.let { map ->
                        focusSinglePhoto(map, point, animated = true)
                    }
                    onOpen(photo)
                },
                onFitAllPhotos = {
                    mapRef?.let { map ->
                        if (uiState.locatedMedia.isNotEmpty()) {
                            fitPointsToMap(map, uiState.locatedMedia.map { it.second }, animated = true)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}
