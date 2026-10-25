package com.amresalehin.emregallery.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.MediaItem
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

/**
 * Top bar for the Photo Map screen with back navigation, map layer selection, day/night theme toggle,
 * and photo location focus action.
 */
@Composable
fun PhotoMapTopBar(
    locatedCount: Int,
    loading: Boolean,
    scanProgress: Int,
    scanTotal: Int,
    mapLayer: String,
    mapAppearance: String,
    showMapLayerMenu: Boolean,
    onBack: () -> Unit,
    onOpenLayerMenu: () -> Unit,
    onDismissLayerMenu: () -> Unit,
    onSelectLayer: (String) -> Unit,
    onToggleMapAppearance: () -> Unit,
    onFocusPhotos: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Photo Map",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (locatedCount == 0) "No geotagged photos" else "$locatedCount photos located",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            Surface(
                onClick = onOpenLayerMenu,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = "Map layers",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        mapLayer,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(
                expanded = showMapLayerMenu,
                onDismissRequest = onDismissLayerMenu
            ) {
                DropdownMenuItem(
                    text = { Text("Standard map") },
                    leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
                    trailingIcon = { if (mapLayer == "Standard") Icon(Icons.Default.Check, contentDescription = "Selected") },
                    onClick = { onSelectLayer("Standard") }
                )
                DropdownMenuItem(
                    text = { Text("Satellite") },
                    leadingIcon = { Icon(Icons.Default.SatelliteAlt, contentDescription = null) },
                    trailingIcon = { if (mapLayer == "Satellite") Icon(Icons.Default.Check, contentDescription = "Selected") },
                    onClick = { onSelectLayer("Satellite") }
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        Surface(
            onClick = onToggleMapAppearance,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (mapAppearance == "Day") Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = "Toggle day/night map theme",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        Surface(
            onClick = onFocusPhotos,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(
                        progress = {
                            if (scanTotal > 0) (scanProgress.toFloat() / scanTotal).coerceIn(0f, 1f) else 0f
                        },
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    )
                } else {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Focus photos",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Container holding the interactive osmdroid MapView, heatmap overlays, zoom controls, and status chips.
 */
@Composable
fun PhotoMapViewContainer(
    locatedMedia: List<Pair<MediaItem, GeoPoint>>,
    mapLayer: String,
    mapAppearance: String,
    loading: Boolean,
    scanProgress: Int,
    scanTotal: Int,
    onMapReady: (MapView) -> Unit,
    onBoundsChanged: (BoundingBox?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("photo_map_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
        ) {
            var activeMapView: MapView? = null

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp)),
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        clipToOutline = true
                        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                        setTileSource(
                            when {
                                mapLayer == "Satellite" -> PhotoMapTiles.satelliteTileSource()
                                mapAppearance == "Night" -> PhotoMapTiles.nightTileSource()
                                else -> TileSourceFactory.MAPNIK
                            }
                        )
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        controller.setZoom(3.0)
                        controller.setCenter(GeoPoint(20.5937, 78.9629))
                        activeMapView = this
                        onMapReady(this)
                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                onBoundsChanged(calculateMapBounds(this@apply, ctx))
                                return true
                            }
                            override fun onZoom(event: ZoomEvent?): Boolean {
                                onBoundsChanged(calculateMapBounds(this@apply, ctx))
                                return true
                            }
                        })
                    }
                },
                update = { map ->
                    activeMapView = map
                    onMapReady(map)
                    map.overlays.removeAll { it is PhotoHeatmapOverlay }
                    if (locatedMedia.isNotEmpty()) {
                        map.overlays.add(PhotoHeatmapOverlay(locatedMedia.map { it.second }))
                    }
                    map.post {
                        onBoundsChanged(calculateMapBounds(map, context))
                    }
                    map.invalidate()
                }
            )

            // Zoom In & Zoom Out floating controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = { activeMapView?.controller?.zoomIn() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Surface(
                    onClick = { activeMapView?.controller?.zoomOut() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // OpenStreetMap Attribution badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 10.dp)
            ) {
                Text(
                    "© OpenStreetMap",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Indexing progress indicator banner
            if (loading && scanTotal > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Text(
                            "Indexing coordinates ($scanProgress/$scanTotal)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calculates current visible bounding box from MapView projection.
 */
fun calculateMapBounds(map: MapView, context: Context): BoundingBox? {
    if (map.width <= 0 || map.height <= 0) return null
    val density = context.resources.displayMetrics.density
    val topPx = (8f * density).toInt().coerceIn(0, map.height - 1)
    val bottomPx = (map.height - 8f * density).toInt().coerceIn(topPx + 1, map.height)
    val northWest = map.projection.fromPixels(0, topPx)
    val southEast = map.projection.fromPixels(map.width, bottomPx)
    return BoundingBox(
        northWest.latitude,
        southEast.longitude,
        southEast.latitude,
        northWest.longitude
    )
}

/**
 * Permission prompt banner for ACCESS_MEDIA_LOCATION on Android 10 (API 29) and higher.
 */
@Composable
fun PhotoMapPermissionBanner(
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Location access needed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "Grant permission to read embedded GPS metadata.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            Button(
                onClick = onGrantPermission,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Grant", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Bottom card displaying photos within the visible viewport, grouped by day.
 */
@Composable
fun PhotoMapDetailsCard(
    viewportCount: Int,
    locatedTotal: Int,
    loading: Boolean,
    dayGroups: List<Pair<String, List<Pair<MediaItem, GeoPoint>>>>,
    onPhotoClick: (MediaItem, GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
            .testTag("photo_map_details_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "%,d photos in this area".format(Locale.getDefault(), viewportCount),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (!loading && locatedTotal == 0) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No photos with GPS data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Only images containing embedded location coordinates appear on this map.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (!loading && viewportCount == 0) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No photos in this area",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Zoom out or move the map above to see photos from other locations.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    dayGroups.forEach { (day, entries) ->
                        item(key = "heading_$day") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    day,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${entries.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        item(key = "photos_$day") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(entries, key = { it.first.uri }) { (photo, point) ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(Uri.parse(photo.uri))
                                            .crossfade(false)
                                            .build(),
                                        contentDescription = photo.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 110.dp, height = 110.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                onPhotoClick(photo, point)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
