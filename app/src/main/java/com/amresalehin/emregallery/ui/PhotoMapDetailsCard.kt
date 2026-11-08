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

import com.amresalehin.emregallery.ui.DISMISS_WIDTH_FRACTION
import com.amresalehin.emregallery.ui.DISMISS_HEIGHT_FRACTION
import com.amresalehin.emregallery.ui.DISMISS_FLING_VELOCITY

fun PhotoMapDetailsCard(
    viewportCount: Int,
    locatedTotal: Int,
    loading: Boolean,
    scanProgress: Int = 0,
    scanTotal: Int = 0,
    dayGroups: List<Pair<String, List<Pair<MediaItem, GeoPoint>>>>,
    onPhotoClick: (MediaItem, GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp).testTag("photo_map_details_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        loading && locatedTotal == 0 -> "Scanning media for locations…"
                        !loading && locatedTotal == 0 -> "No geotagged media"
                        else -> "%,d items in this area".format(Locale.getDefault(), viewportCount)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (locatedTotal > 0 && viewportCount != locatedTotal) {
                    Text("of $locatedTotal located", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (loading && locatedTotal == 0) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Scanning media locations…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (scanTotal > 0) "Reading GPS metadata ($scanProgress of $scanTotal scanned)" else "Scanning your gallery for GPS metadata…",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (!loading && locatedTotal == 0) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.LocationOff, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No media with GPS data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("None of the images or videos in this gallery contain accessible GPS coordinates.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else if (viewportCount == 0) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No media in this area", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Pan or zoom out to see media.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    dayGroups.forEach { (day, entries) ->
                        item(key = "heading_$day", span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(day, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                Text("${entries.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(entries, key = { it.first.uri }) { (photo, point) ->
                            Box(
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPhotoClick(photo, point) }
                            ) {
                                AsyncImage(
                                    model = remember(photo.uri, context) {
                                        ThumbnailUtils.buildThumbnailRequest(context, photo.uri)
                                    },
                                    contentDescription = photo.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (photo.isVideo) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.58f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Video",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
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
}
