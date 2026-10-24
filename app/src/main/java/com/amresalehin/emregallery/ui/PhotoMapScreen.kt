package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.*
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
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Persistent SQLite cache for photo GPS metadata, including photos with no GPS coordinates.
private class PhotoMapLocationCache(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "photo_map_locations.db", null, 2) {

    data class CachedLocation(val point: GeoPoint?)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE photo_locations (uri TEXT PRIMARY KEY NOT NULL, latitude REAL, longitude REAL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS photo_locations")
        onCreate(db)
    }

    @Synchronized
    fun loadAll(): Map<String, CachedLocation> {
        val result = mutableMapOf<String, CachedLocation>()
        readableDatabase.query(
            "photo_locations", arrayOf("uri", "latitude", "longitude"),
            null, null, null, null, null
        ).use { cursor ->
            val uriColumn = cursor.getColumnIndexOrThrow("uri")
            val latitudeColumn = cursor.getColumnIndexOrThrow("latitude")
            val longitudeColumn = cursor.getColumnIndexOrThrow("longitude")
            while (cursor.moveToNext()) {
                val uri = cursor.getString(uriColumn)
                val point = if (cursor.isNull(latitudeColumn) || cursor.isNull(longitudeColumn)) {
                    null
                } else {
                    GeoPoint(cursor.getDouble(latitudeColumn), cursor.getDouble(longitudeColumn))
                }
                result[uri] = CachedLocation(point)
            }
        }
        return result
    }

    @Synchronized
    fun save(uri: String, point: GeoPoint?) {
        val values = ContentValues().apply {
            put("uri", uri)
            if (point == null) {
                putNull("latitude")
                putNull("longitude")
            } else {
                put("latitude", point.latitude)
                put("longitude", point.longitude)
            }
        }
        writableDatabase.insertWithOnConflict(
            "photo_locations", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }
}

/**
 * Draws a Google Maps-inspired density heatmap from photo GPS points.
 */
private class PhotoHeatmapOverlay(
    private val points: List<GeoPoint>
) : org.osmdroid.views.overlay.Overlay() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private data class ScreenPoint(val x: Float, val y: Float)

    override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)
        if (shadow || points.isEmpty() || mapView.width <= 0 || mapView.height <= 0) return

        val projection = mapView.projection
        val projected = android.graphics.Point()
        val radius = 38f
        val neighbourRadius = 42f
        val neighbourRadiusSquared = neighbourRadius * neighbourRadius
        val margin = radius.toInt()
        val screenPoints = points.mapNotNull { point ->
            projection.toPixels(point, projected)
            val x = projected.x.toFloat()
            val y = projected.y.toFloat()
            if (x < -margin || x > mapView.width + margin ||
                y < -margin || y > mapView.height + margin
            ) null else ScreenPoint(x, y)
        }
        if (screenPoints.isEmpty()) return

        val cellSize = neighbourRadius.toInt()
        fun cellKey(x: Int, y: Int): Long =
            (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)

        val buckets = HashMap<Long, MutableList<Int>>()
        screenPoints.forEachIndexed { index, point ->
            val cellX = kotlin.math.floor(point.x / cellSize).toInt()
            val cellY = kotlin.math.floor(point.y / cellSize).toInt()
            buckets.getOrPut(cellKey(cellX, cellY)) { mutableListOf() }.add(index)
        }

        val densities = IntArray(screenPoints.size)
        screenPoints.forEachIndexed { index, point ->
            val cellX = kotlin.math.floor(point.x / cellSize).toInt()
            val cellY = kotlin.math.floor(point.y / cellSize).toInt()
            var count = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    buckets[cellKey(cellX + dx, cellY + dy)]?.forEach { otherIndex ->
                        val other = screenPoints[otherIndex]
                        val deltaX = point.x - other.x
                        val deltaY = point.y - other.y
                        if (deltaX * deltaX + deltaY * deltaY <= neighbourRadiusSquared) count++
                    }
                }
            }
            densities[index] = count
        }

        fun densityColor(count: Int): Int {
            val stops = intArrayOf(
                android.graphics.Color.rgb(0, 105, 255),
                android.graphics.Color.rgb(0, 220, 255),
                android.graphics.Color.rgb(0, 220, 110),
                android.graphics.Color.rgb(255, 230, 0),
                android.graphics.Color.rgb(255, 135, 0),
                android.graphics.Color.rgb(245, 35, 25)
            )
            val thresholds = intArrayOf(1, 2, 4, 7, 12, 20)
            if (count <= thresholds.first()) return stops.first()
            if (count >= thresholds.last()) return stops.last()
            val upper = thresholds.indexOfFirst { count <= it }
            val lower = upper - 1
            val fraction = (count - thresholds[lower]).toFloat() /
                (thresholds[upper] - thresholds[lower]).toFloat()
            val a = stops[lower]
            val b = stops[upper]
            fun channel(color: Int, shift: Int) = (color shr shift) and 0xff
            val red = (channel(a, 16) + (channel(b, 16) - channel(a, 16)) * fraction).toInt()
            val green = (channel(a, 8) + (channel(b, 8) - channel(a, 8)) * fraction).toInt()
            val blue = (channel(a, 0) + (channel(b, 0) - channel(a, 0)) * fraction).toInt()
            return android.graphics.Color.rgb(red, green, blue)
        }

        screenPoints.forEachIndexed { index, point ->
            val color = densityColor(densities[index])
            paint.shader = android.graphics.RadialGradient(
                point.x,
                point.y,
                radius,
                intArrayOf(
                    android.graphics.Color.argb(150, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color)),
                    android.graphics.Color.argb(70, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color)),
                    android.graphics.Color.argb(0, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
                ),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(point.x, point.y, radius, paint)
        }
        paint.shader = null
    }
}

private fun satelliteTileSource(): OnlineTileSourceBase = object : OnlineTileSourceBase(
    "EsriWorldImagery",
    1,
    19,
    256,
    ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Esri World Imagery"
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + imageFilenameEnding()
}

private fun nightTileSource(): XYTileSource = XYTileSource(
    "CartoDBDarkMatter",
    0,
    20,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    )
)

@Composable
fun PhotoMapScreen(
    media: List<MediaItem>,
    focusItem: MediaItem? = null,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val locationCache = remember(context.applicationContext) { PhotoMapLocationCache(context.applicationContext) }
    var canReadOriginalLocation by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        canReadOriginalLocation = granted || (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
    }
    var locatedMedia by remember { mutableStateOf<List<Pair<MediaItem, GeoPoint>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var scanProgress by remember { mutableIntStateOf(0) }
    var scanTotal by remember { mutableIntStateOf(0) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var mapLayer by remember { mutableStateOf("Standard") }
    var mapAppearance by remember { mutableStateOf("Day") }
    var showMapLayerMenu by remember { mutableStateOf(false) }
    var sheetExpanded by remember { mutableStateOf(false) }
    var visibleBounds by remember { mutableStateOf<org.osmdroid.util.BoundingBox?>(null) }
    var mapFramed by remember { mutableStateOf(false) }
    val sheetExpandedState = rememberUpdatedState(sheetExpanded)
    val dayFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }

    fun updateVisibleMapBounds(map: MapView, expanded: Boolean = sheetExpanded) {
        if (map.width <= 0 || map.height <= 0) return
        val density = context.resources.displayMetrics.density
        val topPx = (8f * density).toInt().coerceIn(0, map.height - 1)
        val bottomPx = (map.height - 8f * density).toInt().coerceIn(topPx + 1, map.height)
        val northWest = map.projection.fromPixels(0, topPx)
        val southEast = map.projection.fromPixels(map.width, bottomPx)
        visibleBounds = org.osmdroid.util.BoundingBox(
            northWest.latitude,
            southEast.longitude,
            southEast.latitude,
            northWest.longitude
        )
    }

    LaunchedEffect(media, canReadOriginalLocation) {
        loading = false
        scanProgress = 0
        scanTotal = 0
        val photos = media.filterNot { it.isVideo }
        val cached = withContext(Dispatchers.IO) { locationCache.loadAll() }
        locatedMedia = photos.mapNotNull { item ->
            cached[item.uri]?.point?.let { item to it }
        }
        val uncachedPhotos = photos.filterNot { cached.containsKey(it.uri) }
        scanTotal = uncachedPhotos.size
        if (uncachedPhotos.isEmpty()) {
            loading = false
            return@LaunchedEffect
        }

        loading = true
        val result = locatedMedia.toMutableList()
        var scanned = 0
        uncachedPhotos.forEach { item ->
            val point = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(
                        Uri.parse(item.uri)
                    )?.use { stream ->
                        ExifInterface(stream).latLong?.let { c ->
                            if (c[0].isFinite() && c[1].isFinite() && c[0] in -90.0..90.0 && c[1] in -180.0..180.0) GeoPoint(c[0], c[1]) else null
                        }
                    }
                }.getOrNull()
            }
            withContext(Dispatchers.IO) { locationCache.save(item.uri, point) }
            if (point != null) result.add(item to point)
            scanned++
            if (scanned % 12 == 0 || scanned == uncachedPhotos.size) {
                locatedMedia = result.toList()
                scanProgress = scanned
                kotlinx.coroutines.yield()
            }
        }
        locatedMedia = result.toList()
        scanProgress = uncachedPhotos.size
        loading = false
    }

    LaunchedEffect(locatedMedia, focusItem) {
        val target = focusItem ?: return@LaunchedEffect
        val point = locatedMedia.firstOrNull { it.first.uri == target.uri }?.second ?: return@LaunchedEffect
        mapRef?.let { map ->
            map.controller.animateTo(point)
            map.controller.setZoom(15.0)
            map.invalidate()
        }
    }

    val viewportMedia = remember(locatedMedia, visibleBounds) {
        val bounds = visibleBounds
        if (bounds == null) locatedMedia else locatedMedia.filter { (_, point) ->
            point.latitude <= bounds.latNorth && point.latitude >= bounds.latSouth &&
                if (bounds.lonWest <= bounds.lonEast) {
                    point.longitude >= bounds.lonWest && point.longitude <= bounds.lonEast
                } else {
                    point.longitude >= bounds.lonWest || point.longitude <= bounds.lonEast
                }
        }
    }

    val dayGroups = remember(viewportMedia) {
        viewportMedia.groupBy { (item, _) ->
            val millis = if (item.dateTakenMillis > 0) item.dateTakenMillis else item.dateAddedSeconds * 1000
            dayFormat.format(Date(millis))
        }.entries.sortedByDescending { entry ->
            entry.value.maxOfOrNull { (item, _) -> if (item.dateTakenMillis > 0) item.dateTakenMillis else item.dateAddedSeconds * 1000 } ?: 0L
        }
    }

    DisposableEffect(Unit) { onDispose { mapRef?.onPause(); mapRef?.onDetach(); mapRef = null } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
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
                    if (locatedMedia.isEmpty()) "No geotagged photos" else "${locatedMedia.size} photos located",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                Surface(
                    onClick = { showMapLayerMenu = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = "Map layers", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Text(mapLayer, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DropdownMenu(
                    expanded = showMapLayerMenu,
                    onDismissRequest = { showMapLayerMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Standard map") },
                        leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
                        trailingIcon = { if (mapLayer == "Standard") Icon(Icons.Default.Check, contentDescription = "Selected") },
                        onClick = {
                            mapLayer = "Standard"
                            mapRef?.setTileSource(if (mapAppearance == "Night") nightTileSource() else TileSourceFactory.MAPNIK)
                            mapRef?.invalidate()
                            showMapLayerMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Satellite") },
                        leadingIcon = { Icon(Icons.Default.SatelliteAlt, contentDescription = null) },
                        trailingIcon = { if (mapLayer == "Satellite") Icon(Icons.Default.Check, contentDescription = "Selected") },
                        onClick = {
                            mapLayer = "Satellite"
                            mapRef?.setTileSource(satelliteTileSource())
                            mapRef?.invalidate()
                            showMapLayerMenu = false
                        }
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Surface(
                onClick = {
                    mapAppearance = if (mapAppearance == "Day") "Night" else "Day"
                    mapRef?.let { map ->
                        if (mapLayer == "Satellite") mapLayer = "Standard"
                        map.setTileSource(if (mapAppearance == "Night") nightTileSource() else TileSourceFactory.MAPNIK)
                        map.invalidate()
                    }
                },
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
                onClick = {
                    if (!loading) {
                        mapRef?.let { map ->
                            locatedMedia.firstOrNull()?.second?.let { point ->
                                map.controller.animateTo(point)
                                map.controller.setZoom(12.0)
                            }
                        }
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(
                            progress = { if (scanTotal > 0) (scanProgress.toFloat() / scanTotal).coerceIn(0f, 1f) else 0f },
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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                            setTileSource(when { mapLayer == "Satellite" -> satelliteTileSource(); mapAppearance == "Night" -> nightTileSource(); else -> TileSourceFactory.MAPNIK })
                            setMultiTouchControls(true)
                            setBuiltInZoomControls(false)
                            controller.setZoom(3.0)
                            controller.setCenter(GeoPoint(20.5937, 78.9629))
                            mapRef = this
                            addMapListener(object : org.osmdroid.events.MapListener {
                                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                                    updateVisibleMapBounds(this@apply, sheetExpandedState.value)
                                    return true
                                }
                                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                    updateVisibleMapBounds(this@apply, sheetExpandedState.value)
                                    return true
                                }
                            })
                        }
                    },
                    update = { map ->
                        mapRef = map
                        if (locatedMedia.isNotEmpty() && !mapFramed && map.width > 0 && map.height > 0) {
                            val points = locatedMedia.map { it.second }
                            val north = points.maxOf { it.latitude }
                            val south = points.minOf { it.latitude }
                            val east = points.maxOf { it.longitude }
                            val west = points.minOf { it.longitude }
                            val bounds = org.osmdroid.util.BoundingBox(north, east, south, west)
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
                        map.overlays.removeAll { it is PhotoHeatmapOverlay }
                        if (locatedMedia.isNotEmpty()) map.overlays.add(PhotoHeatmapOverlay(locatedMedia.map { it.second }))
                        map.post { updateVisibleMapBounds(map, sheetExpanded) }
                        map.invalidate()
                    }
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { mapRef?.controller?.zoomIn() },
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
                        onClick = { mapRef?.controller?.zoomOut() },
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
                            Text("Indexing coordinates ($scanProgress/$scanTotal)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !canReadOriginalLocation) {
            Surface(
                modifier = Modifier
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
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Column(Modifier.weight(1f)) {
                        Text("Location access needed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Grant permission to read embedded GPS metadata.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                    }
                    Button(
                        onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Grant", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                        "%,d photos in this area".format(Locale.getDefault(), viewportMedia.size),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                if (!loading && locatedMedia.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.LocationOff, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No photos with GPS data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Only images containing embedded location coordinates appear on this map.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (!loading && viewportMedia.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Map, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No photos in this area", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Zoom out or move the map above to see photos from other locations.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                                            model = ImageRequest.Builder(context).data(Uri.parse(photo.uri)).crossfade(false).build(),
                                            contentDescription = photo.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 110.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    mapRef?.controller?.animateTo(point)
                                                    mapRef?.controller?.setZoom(14.0)
                                                    onOpen(photo)
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
}
