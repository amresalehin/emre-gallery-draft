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

val DEFAULT_MAP_CENTER = GeoPoint(12.5, 90.0)

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

/** Process-local state: a cold app start intentionally begins with no restored camera. */
internal object PhotoMapSessionCamera {
    @Volatile
    var snapshot: SavedPhotoMapCamera? = null

    fun capture(map: MapView) {
        if (map.width <= 0 || map.height <= 0) return
        val center = map.mapCenter
        val zoom = map.zoomLevelDouble
        if (!center.latitude.isFinite() || !center.longitude.isFinite() ||
            !zoom.isFinite() || zoom < 0.0
        ) return
        snapshot = SavedPhotoMapCamera(center.latitude, center.longitude, zoom)
    }
}

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
 * Calculates the lowest zoom at which the complete Mercator world fits inside the
 * map viewport. The smaller viewport dimension is the limiting constraint; using
 * the larger dimension would crop the world on the other axis.
 */
fun calculateWorldFitZoom(map: MapView): Double {
    if (map.width <= 0 || map.height <= 0) return 0.0
    val limitingDimension = minOf(map.width, map.height).toDouble()
    return (kotlin.math.ln(limitingDimension / 256.0) / kotlin.math.ln(2.0))
        .coerceAtLeast(0.0)
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
    viewModel: PhotoMapViewModel = viewModel(),
    useAlbumMapSessionCamera: Boolean = false
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

    val sessionCamera = if (useAlbumMapSessionCamera) PhotoMapSessionCamera.snapshot else null

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
            lastFocusedUri = requestedItem.uri
        } else {
            lastFocusedUri = requestedItem.uri
            snackbarHostState.showSnackbar(
                message = "Cannot focus on \"${requestedItem.name}\": no accessible GPS coordinates found.",
                duration = SnackbarDuration.Short
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Preserve the camera in memory only for maps entered through the Albums map card.
    // This survives navigation within the current app process, but not a cold app start.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                mapRef?.let { map ->
                    if (useAlbumMapSessionCamera) PhotoMapSessionCamera.capture(map)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Capture the last camera when leaving the map, but only for the Albums map-card route.
    DisposableEffect(Unit) {
        onDispose {
            mapRef?.let { map ->
                if (useAlbumMapSessionCamera) PhotoMapSessionCamera.capture(map)
                map.onPause()
                map.onDetach()
            }
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
                useAlbumMapSessionCamera = useAlbumMapSessionCamera,
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
                    // Selecting a thumbnail is a location lookup, not a zoom command.
                    // Pan to its coordinates while preserving the user's current zoom.
                    mapRef?.controller?.animateTo(point)
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
