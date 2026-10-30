package com.amresalehin.emregallery.ui

import android.content.Context
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amresalehin.emregallery.data.MediaItem
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

const val WORLD_VIEW_LAT_NORTH = 85.0511
const val WORLD_VIEW_LAT_SOUTH = -85.0511
const val WORLD_VIEW_LON_WEST = -180.0
const val WORLD_VIEW_LON_EAST = 180.0

val WORLD_VIEW_CENTER = GeoPoint(0.0, 0.0)
val DEFAULT_MAP_CENTER = GeoPoint(12.5, 90.0)
val WORLD_VIEW_BOUNDS = BoundingBox(
    WORLD_VIEW_LAT_NORTH,
    WORLD_VIEW_LON_EAST,
    WORLD_VIEW_LAT_SOUTH,
    WORLD_VIEW_LON_WEST
)

private const val PHOTO_MAP_CAMERA_PREFERENCES = "photo_map_camera"
private const val CAMERA_LATITUDE_KEY = "latitude"
private const val CAMERA_LONGITUDE_KEY = "longitude"
private const val CAMERA_ZOOM_KEY = "zoom"

/** A camera snapshot saved independently of the photo-indexing lifecycle. */
internal data class SavedPhotoMapCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double
)

internal fun loadSavedPhotoMapCamera(context: Context): SavedPhotoMapCamera? {
    val preferences = context.getSharedPreferences(
        PHOTO_MAP_CAMERA_PREFERENCES,
        Context.MODE_PRIVATE
    )
    val latitude = preferences.getString(CAMERA_LATITUDE_KEY, null)?.toDoubleOrNull()
        ?: return null
    val longitude = preferences.getString(CAMERA_LONGITUDE_KEY, null)?.toDoubleOrNull()
        ?: return null
    val zoom = preferences.getString(CAMERA_ZOOM_KEY, null)?.toDoubleOrNull()
        ?: return null

    if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
    if (!longitude.isFinite() || longitude !in -180.0..180.0) return null
    if (!zoom.isFinite() || zoom <= 0.0 || zoom > 30.0) return null

    // Previous versions saved the initial world camera as if it were a user-selected
    // position. Discard that legacy snapshot so the map can frame real photo locations.
    if (latitude == 0.0 && longitude == 0.0 && zoom <= 4.0) {
        preferences.edit()
            .remove(CAMERA_LATITUDE_KEY)
            .remove(CAMERA_LONGITUDE_KEY)
            .remove(CAMERA_ZOOM_KEY)
            .apply()
        return null
    }

    return SavedPhotoMapCamera(latitude, longitude, zoom)
}

internal fun persistPhotoMapCamera(context: Context, map: MapView) {
    runCatching {
        if (map.width <= 0 || map.height <= 0) return

        val center = map.mapCenter
        val zoom = map.zoomLevelDouble
        if (!center.latitude.isFinite() || !center.longitude.isFinite() ||
            !zoom.isFinite() || zoom <= 0.0
        ) return

        context.getSharedPreferences(PHOTO_MAP_CAMERA_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(CAMERA_LATITUDE_KEY, center.latitude.toString())
            .putString(CAMERA_LONGITUDE_KEY, center.longitude.toString())
            .putString(CAMERA_ZOOM_KEY, zoom.toString())
            .apply()
    }
}

/**
 * Calculates the zoom level required for a single Mercator world frame (256 * 2^z)
 * to cleanly cover the container without vertical or horizontal repeating.
 * Uses the nearest bounding constraint (whichever dimension—width or height—fits first without repeating).
 */
fun calculateWorldFitZoom(map: MapView): Double {
    if (map.width <= 0 || map.height <= 0) return 1.0
    val maxDim = maxOf(map.width, map.height).toDouble()
    return (kotlin.math.ln(maxDim / 256.0) / kotlin.math.ln(2.0)).coerceAtLeast(1.0)
}

/**
 * Fits the entire world view automatically, framing the viewport cleanly to fill
 * the container using the nearest bounding constraint without repeating.
 */
fun showWorldView(map: MapView, animated: Boolean = false) {
    if (map.width <= 0 || map.height <= 0) {
        map.post { showWorldView(map, animated) }
        return
    }
    val targetZoom = calculateWorldFitZoom(map)
    map.minZoomLevel = targetZoom
    if (animated) {
        map.controller.animateTo(WORLD_VIEW_CENTER)
        map.controller.setZoom(targetZoom)
    } else {
        map.controller.setCenter(WORLD_VIEW_CENTER)
        map.controller.setZoom(targetZoom)
    }
    map.invalidate()
}

/**
 * Shows a neutral Eastern Hemisphere starting view when the library has no usable GPS
 * coordinates. The map should not imply that the media belongs at the null-island point.
 */
fun showDefaultMapView(map: MapView, animated: Boolean = false) {
    if (map.width <= 0 || map.height <= 0) {
        map.post { showDefaultMapView(map, animated) }
        return
    }
    val targetZoom = calculateWorldFitZoom(map)
    map.minZoomLevel = targetZoom
    if (animated) {
        map.controller.animateTo(DEFAULT_MAP_CENTER)
        map.controller.setZoom(targetZoom)
    } else {
        map.controller.setCenter(DEFAULT_MAP_CENTER)
        map.controller.setZoom(targetZoom)
    }
    map.invalidate()
}

fun focusSinglePhoto(map: MapView, point: GeoPoint, animated: Boolean = true) {
    if (map.width <= 0 || map.height <= 0) {
        map.post { focusSinglePhoto(map, point, animated) }
        return
    }
    if (animated) map.controller.animateTo(point) else map.controller.setCenter(point)
    map.controller.setZoom(15.0)
    map.invalidate()
}

/**
 * Computes the geographic bounding box of a list of coordinates with safety margins,
 * clamped strictly to valid Web Mercator world bounds.
 */
fun computeMediaBoundingBox(points: List<GeoPoint>): BoundingBox? {
    if (points.isEmpty()) return null
    if (points.size == 1) {
        val pt = points.first()
        return BoundingBox(
            (pt.latitude + 0.005).coerceAtMost(WORLD_VIEW_LAT_NORTH),
            (pt.longitude + 0.005).coerceAtMost(WORLD_VIEW_LON_EAST),
            (pt.latitude - 0.005).coerceAtLeast(WORLD_VIEW_LAT_SOUTH),
            (pt.longitude - 0.005).coerceAtLeast(WORLD_VIEW_LON_WEST)
        )
    }
    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }
    val latSpan = north - south
    val lonSpan = kotlin.math.abs(east - west)

    val latMargin = (latSpan * 0.02).coerceAtLeast(0.0001)
    val lonMargin = (lonSpan * 0.02).coerceAtLeast(0.0001)
    return BoundingBox(
        (north + latMargin).coerceAtMost(WORLD_VIEW_LAT_NORTH),
        (east + lonMargin).coerceAtMost(WORLD_VIEW_LON_EAST),
        (south - latMargin).coerceAtLeast(WORLD_VIEW_LAT_SOUTH),
        (west - lonMargin).coerceAtLeast(WORLD_VIEW_LON_WEST)
    )
}

/**
 * Fits the given bounding box on the map with standard edge padding.
 */
fun fitBounds(
    map: MapView,
    bounds: BoundingBox,
    animated: Boolean = true,
    paddingDp: Int = 48
) {
    if (map.width <= 0 || map.height <= 0) {
        map.post { fitBounds(map, bounds, animated, paddingDp) }
        return
    }
    val density = map.context.resources.displayMetrics.density
    val standardPaddingPx = (paddingDp * density).toInt()
    val maxPaddingX = (map.width * 0.2f).toInt()
    val maxPaddingY = (map.height * 0.2f).toInt()
    val paddingPx = minOf(standardPaddingPx, maxPaddingX, maxPaddingY).coerceAtLeast(0)

    runCatching {
        map.zoomToBoundingBox(bounds, animated, paddingPx)
        if (map.zoomLevelDouble > 15.0) {
            map.controller.setZoom(15.0)
        }
        val minZoom = calculateWorldFitZoom(map)
        if (map.zoomLevelDouble < minZoom) {
            map.controller.setZoom(minZoom)
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

    val hasSavedCamera = remember(context) { loadSavedPhotoMapCamera(context) != null }
    val initialCameraFramed = remember { mutableStateOf(hasSavedCamera) }
    val userHasMovedMap = remember { mutableStateOf(false) }
    val hasMeaningfulCameraPosition = remember { mutableStateOf(hasSavedCamera) }

    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var selectedMapMedia by remember { mutableStateOf<Pair<MediaItem, GeoPoint>?>(null) }
    var lastFocusedUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(media) {
        viewModel.loadAndScanMedia(media)
    }

    // A requested photo is an explicit navigation action, so it may move the camera.
    LaunchedEffect(focusItem?.uri, mapRef) {
        val requestedItem = focusItem ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        if (lastFocusedUri == requestedItem.uri) return@LaunchedEffect

        val point = viewModel.getLocationForMedia(context, requestedItem)
        if (point != null) {
            selectedMapMedia = requestedItem to point
            focusSinglePhoto(map, point, animated = true)
            initialCameraFramed.value = true
            hasMeaningfulCameraPosition.value = true
            lastFocusedUri = requestedItem.uri
        } else {
            lastFocusedUri = requestedItem.uri
            snackbarHostState.showSnackbar(
                message = "Cannot focus on \"${requestedItem.name}\": no accessible GPS coordinates found.",
                duration = SnackbarDuration.Short
            )
        }
    }

    // On a fresh install or after clearing a legacy (0, 0) camera snapshot, frame the
    // available GPS-tagged media once. Do not reframe as indexing adds more coordinates.
    LaunchedEffect(mapRef, uiState.locatedMedia, uiState.loading, uiState.scanProgress, focusItem?.uri) {
        val map = mapRef ?: return@LaunchedEffect
        if (
            hasSavedCamera ||
            initialCameraFramed.value ||
            userHasMovedMap.value ||
            focusItem != null
        ) return@LaunchedEffect

        // Fit cached coordinates before a scan begins; otherwise wait until the scan completes.
        if (uiState.locatedMedia.isEmpty() && uiState.loading) return@LaunchedEffect
        if (uiState.locatedMedia.isNotEmpty() && uiState.loading && uiState.scanProgress > 0) {
            return@LaunchedEffect
        }

        val points = uiState.locatedMedia.map { it.second }
        when {
            points.size == 1 -> {
                focusSinglePhoto(map, points.first(), animated = false)
                hasMeaningfulCameraPosition.value = true
            }
            points.size > 1 -> {
                computeMediaBoundingBox(points)?.let { fitBounds(map, it, animated = false) }
                    ?: showDefaultMapView(map, animated = false)
                hasMeaningfulCameraPosition.value = true
            }
            else -> showDefaultMapView(map, animated = false)
        }
        initialCameraFramed.value = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Persist the latest camera before the activity is backgrounded. A map can remain
    // composed while its Activity pauses, so disposal alone is not a reliable save point.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                mapRef?.let { map ->
                    if (hasMeaningfulCameraPosition.value) {
                        persistPhotoMapCamera(context, map)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Persist only a real restored, auto-framed, or user-selected camera; never persist
    // the temporary starting camera as a false (0, 0) location.
    DisposableEffect(Unit) {
        onDispose {
            mapRef?.let { map ->
                if (hasMeaningfulCameraPosition.value) {
                    persistPhotoMapCamera(context, map)
                }
                map.onPause()
                map.onDetach()
            }
            mapRef = null
        }
    }

    val onFitAllPhotos: () -> Unit = {
        selectedMapMedia = null
        initialCameraFramed.value = true
        val map = mapRef
        if (map != null) {
            val geotaggedPoints = uiState.locatedMedia.map { it.second }
            if (geotaggedPoints.isNotEmpty()) {
                hasMeaningfulCameraPosition.value = true
                val allMediaBounds = computeMediaBoundingBox(geotaggedPoints)
                if (allMediaBounds != null) {
                    if (geotaggedPoints.size == 1) {
                        focusSinglePhoto(map, geotaggedPoints.first(), animated = true)
                    } else {
                        fitBounds(map, allMediaBounds, animated = true)
                    }
                } else {
                    showDefaultMapView(map, animated = true)
                }
            } else {
                showDefaultMapView(map, animated = true)
            }
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
                onFitAllPhotos = onFitAllPhotos
            )

            PhotoMapViewContainer(
                locatedMedia = uiState.locatedMedia,
                mapLayer = uiState.mapLayer,
                loading = uiState.loading,
                scanProgress = uiState.scanProgress,
                scanTotal = uiState.scanTotal,
                selectedMedia = selectedMapMedia,
                onOpenSelected = { selectedMapMedia?.first?.let(onOpen) },
                onMapReady = { map -> mapRef = map },
                onUserMapGesture = {
                    userHasMovedMap.value = true
                    initialCameraFramed.value = true
                    hasMeaningfulCameraPosition.value = true
                },
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
                    selectedMapMedia = photo to point
                    initialCameraFramed.value = true
                    hasMeaningfulCameraPosition.value = true
                    // Selecting a thumbnail is a location lookup, not a zoom command.
                    // Pan to its coordinates while preserving the user's current zoom.
                    mapRef?.controller?.animateTo(point)
                },
                onFitAllPhotos = onFitAllPhotos,
                modifier = Modifier.weight(1f)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}
