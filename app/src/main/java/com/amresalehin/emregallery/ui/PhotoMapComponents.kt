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

/** Placeholder shown in the selected-place bar until the place name resolves. */
internal const val PLACE_NAME_PLACEHOLDER = "Even Light isn\u2019t Instant"

/** Fraction of the bar's width/height that must be dragged to dismiss it. */
internal const val DISMISS_WIDTH_FRACTION = 0.35f
internal const val DISMISS_HEIGHT_FRACTION = 0.6f

/** Fling speed (px/s) that dismisses the bar regardless of distance. */
internal const val DISMISS_FLING_VELOCITY = 1200f

@Composable
fun PhotoMapTopBar(
    mapLayer: String,
    showMapLayerMenu: Boolean,
    onBack: () -> Unit,
    onOpenLayerMenu: () -> Unit,
    onDismissLayerMenu: () -> Unit,
    onSelectLayer: (String) -> Unit,
    onFitAllPhotos: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.weight(1f))
        Box {
            Surface(
                onClick = onOpenLayerMenu,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (mapLayer == "Satellite") Icons.Default.SatelliteAlt else Icons.Default.Layers,
                        contentDescription = "Map layer: $mapLayer",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            DropdownMenu(expanded = showMapLayerMenu, onDismissRequest = onDismissLayerMenu) {
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
        Surface(
            onClick = onFitAllPhotos,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FilterCenterFocus, contentDescription = "Fit all media", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Full-width bar showing the selected photo's place.
 *
 * - Tap anywhere on the bar to open the media.
 * - Swipe left, right, or down to dismiss. Dragging upward is blocked.
 */
@Composable
internal fun SelectedPlaceBar(
    placeName: String?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val offset = remember { mutableStateOf(Offset.Zero) }
    val size = remember { mutableStateOf(IntSize.Zero) }
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val tracker = remember { VelocityTracker() }

    // Animates the bar either off-screen (dismiss) or back to rest, based on distance and fling speed.
    val settle: () -> Unit = {
        val velocity = tracker.calculateVelocity()
        val o = offset.value
        val w = size.value.width.toFloat().coerceAtLeast(1f)
        val h = size.value.height.toFloat().coerceAtLeast(1f)
        val target: Offset? = when {
            o.x > w * DISMISS_WIDTH_FRACTION || (velocity.x > DISMISS_FLING_VELOCITY && o.x > 0f) ->
                Offset(w * 1.5f, o.y)
            o.x < -w * DISMISS_WIDTH_FRACTION || (velocity.x < -DISMISS_FLING_VELOCITY && o.x < 0f) ->
                Offset(-w * 1.5f, o.y)
            o.y > h * DISMISS_HEIGHT_FRACTION || (velocity.y > DISMISS_FLING_VELOCITY && o.y > 0f) ->
                Offset(o.x, h * 3f)
            else -> null
        }
        settleJob.value?.cancel()
        settleJob.value = scope.launch {
            val from = offset.value
            val to = target ?: Offset.Zero
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = if (target != null) tween(durationMillis = 180) else spring(stiffness = 400f)
            ) { t, _ ->
                offset.value = Offset(
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t
                )
            }
            if (target != null) currentOnDismiss.value()
        }
    }

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("map_redirect_button")
                .onSizeChanged { size.value = it }
                // pointerInput stays outside graphicsLayer so drag coordinates don't shift while the bar moves.
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            settleJob.value?.cancel()
                            tracker.resetTracking()
                        },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val o = offset.value
                            offset.value = Offset(
                                o.x + dragAmount.x,
                                (o.y + dragAmount.y).coerceAtLeast(0f)
                            )
                        }
                    )
                }
                .graphicsLayer {
                    val o = offset.value
                    val w = size.value.width.toFloat().coerceAtLeast(1f)
                    val h = size.value.height.toFloat().coerceAtLeast(1f)
                    translationX = o.x
                    translationY = o.y
                    val progress = max(abs(o.x) / w, o.y / h).coerceIn(0f, 1f)
                    alpha = 1f - 0.7f * progress
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = placeName ?: PLACE_NAME_PLACEHOLDER,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontStyle = if (placeName == null) FontStyle.Italic else FontStyle.Normal,
                    color = if (placeName == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open selected media",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Container for the interactive map and its heatmap overlay.
 *
 * [onDismissSelected] is called after the user swipes the selected-place bar away.
 * The caller must clear its selection (set [selectedMedia] to null) so the pin is removed too.
 */
@Composable
