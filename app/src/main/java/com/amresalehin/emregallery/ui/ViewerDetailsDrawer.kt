package com.amresalehin.emregallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.view.ViewConfiguration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.hypot
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.PhotoMapLocationCache
import com.amresalehin.emregallery.face.DetectedMediaFace
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.Locale

/**
 * State holder for the media details drawer (metadata and OCR).
 */
@Stable
class ViewerDetailsState(
    val context: Context,
    val item: MediaItem,
    defaultOcrLanguages: Set<String>,
    initialOcrModel: String = "fast"
) {
    var metadata by mutableStateOf<Map<String, String>>(emptyMap())
    var ocrText by mutableStateOf("")
    var ocrError by mutableStateOf<String?>(null)
    var ocrBusy by mutableStateOf(false)
    var ocrModel by mutableStateOf(initialOcrModel)
    val ocrLanguages = mutableStateListOf<String>().apply {
        val initial = if (defaultOcrLanguages.isNotEmpty()) defaultOcrLanguages else setOf("eng")
        addAll(initial)
    }

    suspend fun loadData() {
        metadata = withContext(Dispatchers.IO) {
            runCatching { readMediaMetadata(context, item) }.getOrDefault(emptyMap())
        }
        ocrText = withContext(Dispatchers.IO) {
            TesseractOcr.readCachedResult(context, Uri.parse(item.uri)).orEmpty()
        }
        ocrError = null
    }

    fun toggleOcrLanguage(code: String) {
        if (code in ocrLanguages) {
            if (ocrLanguages.size > 1) ocrLanguages.remove(code)
        } else {
            ocrLanguages.add(code)
        }
    }

    suspend fun runOcr() {
        if (item.isVideo || ocrBusy || ocrLanguages.isEmpty()) return
        ocrBusy = true
        ocrError = null
        ocrText = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val mediaUri = Uri.parse(item.uri)
                val model = com.amresalehin.emregallery.ocr.TesseractModel.fromId(ocrModel)
                val recognized = TesseractOcr.recognize(context, mediaUri, ocrLanguages.toList(), model)
                runCatching { TesseractOcr.saveCachedResult(context, mediaUri, recognized) }
                recognized
            }
        }
        result.onSuccess { text ->
            ocrText = text.ifBlank { "No text detected in this image." }
        }.onFailure { error ->
            ocrError = error.message ?: "OCR failed. Please try again."
        }
        ocrBusy = false
    }

    fun copyOcrText() {
        if (ocrText.isNotBlank() && ocrText != "No text detected in this image.") {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Recognized text", ocrText))
        }
    }
}

@Composable
fun rememberViewerDetailsState(
    item: MediaItem,
    defaultOcrLanguages: Set<String>,
    defaultOcrModel: String = "fast"
): ViewerDetailsState {
    val context = LocalContext.current
    val state = remember(item.uri, defaultOcrLanguages, defaultOcrModel) {
        ViewerDetailsState(context, item, defaultOcrLanguages, defaultOcrModel)
    }

    LaunchedEffect(item.uri, defaultOcrLanguages, defaultOcrModel) {
        state.loadData()
    }

    return state
}

/**
 * Slide-up details drawer containing Overview and Actions tabs.
 * Coordinated directly with finger drag gestures, 6 detents (Closed, Compact ~35%, Medium ~50%, Initial ~65%, Large ~80%, Expanded ~95%),
 * nested scrolling on content, directional gesture locking, and progressive scrim.
 */
@Composable
fun BoxScope.ViewerDetailsDrawer(
    item: MediaItem,
    items: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    customTags: Set<String>,
    drawerVisibleHeight: Animatable<Float, AnimationVector1D>,
    initialHeightPx: Float,
    expandedHeightPx: Float,
    onSettle: suspend (startHeight: Float, currentHeight: Float, velocity: Float) -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onAddTo: () -> Unit,
    onToggleBin: () -> Unit,
    onShowMore: () -> Unit,
    onOpenMap: () -> Unit,
    onAddTag: (String) -> Unit,
    isBin: Boolean,
    defaultOcrLanguages: Set<String> = setOf("eng"),
    defaultOcrModel: String = "fast",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val detailsState = rememberViewerDetailsState(item, defaultOcrLanguages, defaultOcrModel)
    val viewConfig = ViewConfiguration.get(context)
    val touchSlop = viewConfig.scaledTouchSlop.toFloat()

    var selectedTab by remember { mutableStateOf(DetailDrawerTab.OVERVIEW) }
    val faceStore = remember(context) { FaceClusterStore(context.applicationContext) }
    var faceReloadKey by remember { mutableIntStateOf(0) }

    val detectedFaces = remember(item.uri, faceReloadKey) {
        faceStore.facesForMedia(item.uri)
    }

    // Dialog state for Overview / Actions tabs
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var renamingFace by remember { mutableStateOf<DetectedMediaFace?>(null) }

    val currentVisibleH = drawerVisibleHeight.value
    val sheetHeightDp = with(LocalDensity.current) { expandedHeightPx.toDp() }

    // Synchronized progressive backdrop scrim
    if (currentVisibleH > 0.5f) {
        val scrimAlpha = (currentVisibleH / expandedHeightPx).coerceIn(0f, 1f) * 0.45f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable {
                    coroutineScope.launch {
                        drawerVisibleHeight.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                        onClose()
                    }
                }
        )
    }

    // Track scroll gesture start height for nested scroll deliberate dismissal calculation
    var nestedScrollStartHeight by remember { mutableFloatStateOf(0f) }
    var isNestedScrollActive by remember { mutableStateOf(false) }

    // Nested scroll connection for the inner scrollable content
    val nestedScrollConnection = remember(expandedHeightPx, initialHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = -available.y // delta > 0 is upward drag, delta < 0 is downward drag
                if (!isNestedScrollActive && (delta != 0f)) {
                    nestedScrollStartHeight = drawerVisibleHeight.value
                    isNestedScrollActive = true
                }
                // When dragging up and sheet is not yet expanded: expand sheet first
                if (delta > 0 && drawerVisibleHeight.value < expandedHeightPx - 1f) {
                    val newH = (drawerVisibleHeight.value + delta).coerceAtMost(expandedHeightPx)
                    val consumed = newH - drawerVisibleHeight.value
                    coroutineScope.launch { drawerVisibleHeight.snapTo(newH) }
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = -available.y // delta < 0 is downward drag when content is already at top
                if (!isNestedScrollActive && (delta != 0f)) {
                    nestedScrollStartHeight = drawerVisibleHeight.value
                    isNestedScrollActive = true
                }
                if (delta < 0 && drawerVisibleHeight.value > 0f) {
                    val newH = (drawerVisibleHeight.value + delta).coerceAtLeast(0f)
                    val consumedH = drawerVisibleHeight.value - newH
                    coroutineScope.launch { drawerVisibleHeight.snapTo(newH) }
                    return Offset(0f, -consumedH)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val velocity = -available.y
                val startH = if (nestedScrollStartHeight > 0f) nestedScrollStartHeight else drawerVisibleHeight.value
                isNestedScrollActive = false
                nestedScrollStartHeight = 0f
                if (velocity > 0 && drawerVisibleHeight.value < expandedHeightPx - 1f) {
                    onSettle(startH, drawerVisibleHeight.value, velocity)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val velocity = -available.y
                val startH = if (nestedScrollStartHeight > 0f) nestedScrollStartHeight else drawerVisibleHeight.value
                isNestedScrollActive = false
                nestedScrollStartHeight = 0f
                if (drawerVisibleHeight.value < expandedHeightPx) {
                    onSettle(startH, drawerVisibleHeight.value, velocity)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    if (currentVisibleH > 0f) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(sheetHeightDp)
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = (expandedHeightPx - currentVisibleH).coerceAtLeast(0f)
                },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                // Top drag handle with direction lock and deliberate dismissal tracking
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                        .pointerInput(expandedHeightPx, touchSlop) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)
                                var isVerticalDrag = false
                                var isHorizontal = false
                                var totalDx = 0f
                                var totalDy = 0f
                                var gestureStartH = drawerVisibleHeight.value

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (isVerticalDrag) {
                                            val velocityY = velocityTracker.calculateVelocity().y
                                            coroutineScope.launch {
                                                onSettle(gestureStartH, drawerVisibleHeight.value, -velocityY)
                                            }
                                        }
                                        break
                                    }

                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val posChange = change.positionChange()
                                    totalDx += posChange.x
                                    totalDy += posChange.y

                                    if (!isVerticalDrag && !isHorizontal) {
                                        val dist = hypot(totalDx, totalDy)
                                        if (dist > touchSlop) {
                                            if (abs(totalDx) > abs(totalDy)) {
                                                isHorizontal = true
                                            } else {
                                                isVerticalDrag = true
                                                gestureStartH = drawerVisibleHeight.value
                                                change.consume()
                                                val nextH = (drawerVisibleHeight.value - posChange.y).coerceIn(0f, expandedHeightPx)
                                                coroutineScope.launch { drawerVisibleHeight.snapTo(nextH) }
                                            }
                                        }
                                    } else if (isVerticalDrag) {
                                        change.consume()
                                        val nextH = (drawerVisibleHeight.value - posChange.y).coerceIn(0f, expandedHeightPx)
                                        coroutineScope.launch { drawerVisibleHeight.snapTo(nextH) }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(42.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

                // Pinned Header & Tabs at top of drawer with directional gesture locking
                DetailDrawerHeader(
                    item = item,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    modifier = Modifier.pointerInput(expandedHeightPx, touchSlop) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)
                            var isVerticalDrag = false
                            var isHorizontal = false
                            var totalDx = 0f
                            var totalDy = 0f
                            var gestureStartH = drawerVisibleHeight.value

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (isVerticalDrag) {
                                        val velocityY = velocityTracker.calculateVelocity().y
                                        coroutineScope.launch {
                                            onSettle(gestureStartH, drawerVisibleHeight.value, -velocityY)
                                        }
                                    }
                                    break
                                }

                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val posChange = change.positionChange()
                                totalDx += posChange.x
                                totalDy += posChange.y

                                if (!isVerticalDrag && !isHorizontal) {
                                    val dist = hypot(totalDx, totalDy)
                                    if (dist > touchSlop) {
                                        if (abs(totalDx) > abs(totalDy)) {
                                            isHorizontal = true
                                        } else {
                                            isVerticalDrag = true
                                            gestureStartH = drawerVisibleHeight.value
                                            change.consume()
                                            val nextH = (drawerVisibleHeight.value - posChange.y).coerceIn(0f, expandedHeightPx)
                                            coroutineScope.launch { drawerVisibleHeight.snapTo(nextH) }
                                        }
                                    }
                                } else if (isVerticalDrag) {
                                    change.consume()
                                    val nextH = (drawerVisibleHeight.value - posChange.y).coerceIn(0f, expandedHeightPx)
                                    coroutineScope.launch { drawerVisibleHeight.snapTo(nextH) }
                                }
                            }
                        }
                    }
                )

                // Scrollable content area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection)
                ) {
                    when (selectedTab) {
                        DetailDrawerTab.OVERVIEW -> {
                            OverviewTabContent(
                                item = item,
                                customAlbums = customAlbums,
                                customTags = customTags,
                                detectedFaces = detectedFaces,
                                metadata = detailsState.metadata,
                                onOpenMap = onOpenMap,
                                onAddToAlbum = onAddTo,
                                onAddTag = { showAddTagDialog = true },
                                onEditLocation = { showAddLocationDialog = true }
                            )
                        }
                        DetailDrawerTab.ACTIONS -> {
                            ActionsTabContent(
                                item = item,
                                customTags = customTags,
                                detectedFaces = detectedFaces,
                                metadata = detailsState.metadata,
                                detailsState = detailsState,
                                onRenameFaceClick = { renamingFace = it }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Location Dialog
    if (showAddLocationDialog) {
        var locationInput by remember { mutableStateOf("") }
        var isGeocoding by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddLocationDialog = false },
            title = { Text("Add location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a place name, city, or coordinates:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = locationInput,
                        onValueChange = { locationInput = it },
                        label = { Text("Location name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = {
                            showAddLocationDialog = false
                            onOpenMap()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Select on map")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = locationInput.trim()
                        if (input.isNotEmpty()) {
                            isGeocoding = true
                            coroutineScope.launch {
                                val geocoder = runCatching {
                                    @Suppress("DEPRECATION")
                                    Geocoder(context, Locale.getDefault()).getFromLocationName(input, 1)
                                }.getOrNull()?.firstOrNull()

                                val point = if (geocoder != null) {
                                    GeoPoint(geocoder.latitude, geocoder.longitude)
                                } else {
                                    val parts = input.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                                    if (parts.size >= 2) GeoPoint(parts[0], parts[1]) else null
                                }

                                withContext(Dispatchers.IO) {
                                    PhotoMapLocationCache(context).save(item.uri, point)
                                }

                                val updated = detailsState.metadata.toMutableMap()
                                updated["location"] = input
                                if (point != null) {
                                    updated["latitude"] = point.latitude.toString()
                                    updated["longitude"] = point.longitude.toString()
                                }
                                detailsState.metadata = updated
                                isGeocoding = false
                                showAddLocationDialog = false
                            }
                        }
                    },
                    enabled = !isGeocoding && locationInput.isNotBlank()
                ) {
                    if (isGeocoding) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLocationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Tag Dialog
    if (showAddTagDialog) {
        var tagInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add tag") },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tagInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddTag(trimmed)
                            showAddTagDialog = false
                        }
                    },
                    enabled = tagInput.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Person Dialog
    if (renamingFace != null) {
        val face = renamingFace!!
        var nameInput by remember(face) {
            val initial = if (face.displayName.equals("Unknown", ignoreCase = true)) "" else face.displayName
            mutableStateOf(initial)
        }
        AlertDialog(
            onDismissRequest = { renamingFace = null },
            title = {
                Text(if (face.displayName.isBlank() || face.displayName.equals("Unknown", ignoreCase = true)) "Name this person" else "Rename person")
            },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Person name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = nameInput.trim()
                        if (cleanName.isNotEmpty()) {
                            faceStore.renameCluster(face.clusterId, cleanName)
                            faceReloadKey++
                            renamingFace = null
                        }
                    },
                    enabled = nameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingFace = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
