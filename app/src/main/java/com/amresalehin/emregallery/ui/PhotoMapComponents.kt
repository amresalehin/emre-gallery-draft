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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 * Top bar for the Photo Map screen with back navigation, map layer selection, and fit all photos action.
 * Unnecessary heading and description have been removed for a clean, minimal look.
 */
@Composable
fun PhotoMapTopBar(
    mapLayer: String,
    showMapLayerMenu: Boolean,
    onBack: () -> Unit,
    onOpenLayerMenu: () -> Unit,
    onDismissLayerMenu: () -> Unit,
    onSelectLayer: (String) -> Unit,
    onFitAllPhotos: () -> Unit,
    onRescan: (() -> Unit)? = null,
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
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Layer selection dropdown
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

        Spacer(Modifier.width(8.dp))

        // Fit all photos button
        Surface(
            onClick = onFitAllPhotos,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.FilterCenterFocus,
                    contentDescription = "Fit all photos",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (onRescan != null) {
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = onRescan,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Rescan locations",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Container holding the interactive osmdroid MapView, heatmap overlays, and attribution.
 * Plus/minus zoom buttons removed; native pinch-to-zoom and map gestures preserved.
 */
@Composable
fun PhotoMapViewContainer(
    locatedMedia: List<Pair<MediaItem, GeoPoint>>,
    mapLayer: String,
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
                            if (mapLayer == "Satellite") {
                                PhotoMapTiles.satelliteTileSource()
                            } else {
                                TileSourceFactory.MAPNIK
                            }
                        )
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        controller.setZoom(3.0)
                        controller.setCenter(GeoPoint(20.5937, 78.9629))
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
 * Bottom card displaying photos within the visible viewport, grouped by day.
 * Distinguishes clearly between indexing in progress, completed indexing with no GPS coordinates,
 * and photos available.
 */
@Composable
fun PhotoMapDetailsCard(
    viewportCount: Int,
    locatedTotal: Int,
    loading: Boolean,
    scanProgress: Int = 0,
    scanTotal: Int = 0,
    dayGroups: List<Pair<String, List<Pair<MediaItem, GeoPoint>>>>,
    onPhotoClick: (MediaItem, GeoPoint) -> Unit,
    onFitAllPhotos: () -> Unit = {},
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
                    when {
                        loading && locatedTotal == 0 -> "Scanning photos for locations…"
                        !loading && locatedTotal == 0 -> "No geotagged photos"
                        else -> "%,d photos in this area".format(Locale.getDefault(), viewportCount)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (locatedTotal > 0 && viewportCount != locatedTotal) {
                    Text(
                        "of $locatedTotal located",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (loading && locatedTotal == 0) {
                // State 1: Indexing in progress with no coordinates found yet
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Scanning photo locations…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (scanTotal > 0) "Reading GPS metadata ($scanProgress of $scanTotal scanned)"
                        else "Scanning your gallery for GPS metadata…",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (!loading && locatedTotal == 0) {
                // State 2: Completed indexing with no accessible GPS coordinates
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "None of the photos in this gallery contain embedded GPS coordinates.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (viewportCount == 0) {
                // State 3: Photos exist, but none inside the active viewport
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Explore,
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pan or zoom out to see photos, or tap below to frame all $locatedTotal photos.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onFitAllPhotos,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.FilterCenterFocus, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fit all photos", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                // State 4: Photos found in viewport, rendered with chronological day groups
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
