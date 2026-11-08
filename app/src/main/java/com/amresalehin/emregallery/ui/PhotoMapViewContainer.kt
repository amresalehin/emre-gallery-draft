package com.amresalehin.emregallery.ui
import android.content.Context
import android.view.View
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.amresalehin.emregallery.R
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun PhotoMapViewContainer(
    locatedMedia: List<Pair<MediaItem, GeoPoint>>,
    mapLayer: String,
    loading: Boolean,
    scanProgress: Int,
    scanTotal: Int,
    selectedMedia: Pair<MediaItem, GeoPoint>? = null,
    selectedPlaceName: String? = null,
    onOpenSelected: () -> Unit = {},
    onDismissSelected: () -> Unit = {},
    onMapReady: (MapView) -> Unit,
    onBoundsChanged: (BoundingBox?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestOnMapReady = rememberUpdatedState(onMapReady)
    val latestOnBoundsChanged = rememberUpdatedState(onBoundsChanged)
    val heatmapPoints = remember(locatedMedia) { locatedMedia.map { it.second } }
    val heatmapOverlay = remember { PhotoHeatmapOverlay() }
    val selectedMarker = remember { mutableStateOf<Marker?>(null) }
    val mapInstance = remember { mutableStateOf<MapView?>(null) }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("photo_map_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))) {
            AndroidView(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    val savedCamera = loadSavedPhotoMapCamera(ctx)
                    MapView(ctx).apply {
                        var cameraInitialized = false
                        val cameraSaveRunnable = Runnable {
                            if (cameraInitialized) persistPhotoMapCamera(ctx, this@apply)
                        }
                        fun scheduleCameraPersistence() {
                            removeCallbacks(cameraSaveRunnable)
                            postDelayed(cameraSaveRunnable, 350L)
                        }

                        clipToOutline = true
                        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                        setTileSource(if (mapLayer == "Satellite") PhotoMapTiles.satelliteTileSource() else TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        setVerticalMapRepetitionEnabled(false)
                        setHorizontalMapRepetitionEnabled(false)
                        setScrollableAreaLimitDouble(BoundingBox(85.0511, 180.0, -85.0511, -180.0))
                        minZoomLevel = MIN_MAP_ZOOM
                        maxZoomLevel = MAX_MAP_ZOOM
                        controller.setZoom(savedCamera?.zoom ?: DEFAULT_WORLD_ZOOM)
                        controller.setCenter(savedCamera?.let { GeoPoint(it.latitude, it.longitude) } ?: WORLD_VIEW_CENTER)

                        // Initialize the camera once after measurement. Indexing never reframes it.
                        addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                            val newW = right - left
                            val newH = bottom - top
                            if (newW > 0 && newH > 0 && !cameraInitialized) {
                                minZoomLevel = MIN_MAP_ZOOM
                                maxZoomLevel = MAX_MAP_ZOOM
                                if (savedCamera != null) {
                                    controller.setCenter(GeoPoint(savedCamera.latitude, savedCamera.longitude))
                                    controller.setZoom(savedCamera.zoom.coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM))
                                } else {
                                    controller.setCenter(WORLD_VIEW_CENTER)
                                    controller.setZoom(DEFAULT_WORLD_ZOOM)
                                }
                                cameraInitialized = true
                                mapInstance.value = this
                                latestOnMapReady.value(this)
                                latestOnBoundsChanged.value(calculateMapBounds(this, ctx))
                                invalidate()
                            }
                        }

                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                latestOnBoundsChanged.value(calculateMapBounds(this@apply, ctx))
                                if (cameraInitialized) scheduleCameraPersistence()
                                return true
                            }
                            override fun onZoom(event: ZoomEvent?): Boolean {
                                latestOnBoundsChanged.value(calculateMapBounds(this@apply, ctx))
                                if (cameraInitialized) scheduleCameraPersistence()
                                return true
                            }
                        })

                        // Flush any pending camera save when the view leaves the screen,
                        // so the last pan/zoom is kept and no late save runs on a detached view.
                        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) = Unit
                            override fun onViewDetachedFromWindow(v: View) {
                                removeCallbacks(cameraSaveRunnable)
                                if (cameraInitialized) persistPhotoMapCamera(ctx, this@apply)
                            }
                        })
                    }
                },
                update = { map ->
                    mapInstance.value = map
                    // Heatmap stays visible at all times.
                    heatmapOverlay.updatePoints(heatmapPoints)
                    if (!map.overlays.contains(heatmapOverlay)) {
                        map.overlays.add(heatmapOverlay)
                    }

                    val selected = selectedMedia
                    if (selected == null) {
                        selectedMarker.value?.let { map.overlays.remove(it) }
                        selectedMarker.value = null
                    } else {
                        val marker = selectedMarker.value ?: Marker(map).apply {
                            icon = ContextCompat.getDrawable(map.context, R.drawable.ic_map_selection_pin)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }.also { selectedMarker.value = it }
                        marker.position = selected.second
                        marker.title = selected.first.name
                        if (!map.overlays.contains(marker)) map.overlays.add(marker)
                    }

                    // Viewport bounds are emitted by map gestures and initial camera setup.
                    // Do not post another bounds update from every AndroidView recomposition.
                    map.invalidate()
                }
            )

            // Simple, intuitive Zoom In (+) / Zoom Out (−) floating controls
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(2.dp)
                ) {
                    IconButton(
                        onClick = { mapInstance.value?.controller?.zoomIn() },
                        modifier = Modifier.size(38.dp).testTag("map_zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom in",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.width(22.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    IconButton(
                        onClick = { mapInstance.value?.controller?.zoomOut() },
                        modifier = Modifier.size(38.dp).testTag("map_zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom out",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 10.dp)
            ) {
                Text("© OpenStreetMap", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }

            if (selectedMedia != null) {
                // key() resets the bar's drag state whenever a different photo is selected.
                key(selectedMedia.first.uri) {
                    SelectedPlaceBar(
                        placeName = selectedPlaceName,
                        onOpen = onOpenSelected,
                        onDismiss = onDismissSelected,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            if (loading && scanTotal > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Text("Indexing coordinates ($scanProgress/$scanTotal)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

