package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amresalehin.emregallery.data.MediaItem
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

/**
 * Screen displaying gallery photos on an interactive OpenStreetMap with GPS heatmap density overlays
 * and location-based photo browsing.
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

    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var mapFramed by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val canRead = granted || (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        viewModel.updatePermission(canRead)
    }

    LaunchedEffect(Unit) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        viewModel.updatePermission(hasPermission)
    }

    LaunchedEffect(media, uiState.canReadOriginalLocation) {
        viewModel.loadAndScanMedia(media)
    }

    LaunchedEffect(uiState.locatedMedia, focusItem) {
        val target = focusItem ?: return@LaunchedEffect
        val point = uiState.locatedMedia.firstOrNull { it.first.uri == target.uri }?.second ?: return@LaunchedEffect
        mapRef?.let { map ->
            map.controller.animateTo(point)
            map.controller.setZoom(15.0)
            map.invalidate()
        }
    }

    // Auto-frame initial bounding box when photos are located
    LaunchedEffect(uiState.locatedMedia) {
        val map = mapRef
        if (uiState.locatedMedia.isNotEmpty() && !mapFramed && map != null && map.width > 0 && map.height > 0) {
            val points = uiState.locatedMedia.map { it.second }
            val north = points.maxOf { it.latitude }
            val south = points.minOf { it.latitude }
            val east = points.maxOf { it.longitude }
            val west = points.minOf { it.longitude }
            val bounds = BoundingBox(north, east, south, west)
            runCatching {
                if (points.size == 1 || (north - south) < 0.01 && (east - west) < 0.01) {
                    map.controller.setCenter(points.first())
                    map.controller.setZoom(5.0)
                } else {
                    map.zoomToBoundingBox(bounds, true, 48)
                    if (map.zoomLevelDouble > 14.0) map.controller.setZoom(14.0)
                }
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        PhotoMapTopBar(
            locatedCount = uiState.locatedMedia.size,
            loading = uiState.loading,
            scanProgress = uiState.scanProgress,
            scanTotal = uiState.scanTotal,
            mapLayer = uiState.mapLayer,
            mapAppearance = uiState.mapAppearance,
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
                            if (uiState.mapAppearance == "Night") PhotoMapTiles.nightTileSource() else TileSourceFactory.MAPNIK
                        }
                    )
                    map.invalidate()
                }
            },
            onToggleMapAppearance = {
                val nextAppearance = viewModel.toggleMapAppearance()
                mapRef?.let { map ->
                    map.setTileSource(
                        if (nextAppearance == "Night") PhotoMapTiles.nightTileSource() else TileSourceFactory.MAPNIK
                    )
                    map.invalidate()
                }
            },
            onFocusPhotos = {
                if (!uiState.loading) {
                    mapRef?.let { map ->
                        uiState.locatedMedia.firstOrNull()?.second?.let { point ->
                            map.controller.animateTo(point)
                            map.controller.setZoom(12.0)
                        }
                    }
                }
            }
        )

        PhotoMapViewContainer(
            locatedMedia = uiState.locatedMedia,
            mapLayer = uiState.mapLayer,
            mapAppearance = uiState.mapAppearance,
            loading = uiState.loading,
            scanProgress = uiState.scanProgress,
            scanTotal = uiState.scanTotal,
            onMapReady = { map ->
                mapRef = map
            },
            onBoundsChanged = { bounds ->
                viewModel.updateVisibleMapBounds(bounds)
            },
            modifier = Modifier.weight(1f)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !uiState.canReadOriginalLocation) {
            PhotoMapPermissionBanner(
                onGrantPermission = {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                }
            )
        }

        PhotoMapDetailsCard(
            viewportCount = uiState.viewportMedia.size,
            locatedTotal = uiState.locatedMedia.size,
            loading = uiState.loading,
            dayGroups = uiState.dayGroups,
            onPhotoClick = { photo, point ->
                mapRef?.let { map ->
                    map.controller.animateTo(point)
                    map.controller.setZoom(14.0)
                }
                onOpen(photo)
            },
            modifier = Modifier.weight(1f)
        )
    }
}
