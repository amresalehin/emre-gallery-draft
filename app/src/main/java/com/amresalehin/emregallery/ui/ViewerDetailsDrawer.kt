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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
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
import kotlin.math.abs
import kotlin.math.hypot

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
 * Elastic Google Photos-Style Overlay Detail Sheet with 3 Snap Anchors:
 * - Anchor 0 (Closed): Y = Screen Height (completely off-screen)
 * - Anchor 1 (Half / Peek): Y ≈ 45% of screen height (visible height ≈ 55%)
 * - Anchor 2 (Fully Expanded): Y ≈ 15% from top (visible height ≈ 85%, revealing dimmed media behind)
 *
 * Implements:
 * - Dynamic Piecewise Scrim (Closed -> Half: 0.0 -> 0.2; Half -> Full: 0.2 -> 0.6)
 * - Two-Phase Nested Scroll Resolution (Pre-scroll expands sheet, child scrolls when full, post-scroll collapses sheet)
 * - Header Interception bypassing nested scroll
 * - Top-Boundary Fling Dissipation preventing accidental sheet collapse
 * - State-locked list scrolling (scrollable body only scrolls when anchored at Y_full)
 */
@Composable
fun BoxScope.ViewerDetailsDrawer(
    item: MediaItem,
    items: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    customTags: Set<String>,
    drawerTopY: Animatable<Float, AnimationVector1D>,
    screenHeightPx: Float,
    density: Float,
    onSettle: suspend (startY: Float, currentY: Float, velocityY: Float) -> Unit,
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

    // List states for Overview and Actions tabs
    val overviewListState = rememberLazyListState()
    val actionsListState = rememberLazyListState()

    // Dialog state for Overview / Actions tabs
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var renamingFace by remember { mutableStateOf<DetectedMediaFace?>(null) }

    var isScanningPeople by remember { mutableStateOf(false) }
    val scanFacesForThisItem: () -> Unit = {
        if (!isScanningPeople && !item.isVideo) {
            isScanningPeople = true
            coroutineScope.launch {
                try {
                    val repo = com.amresalehin.emregallery.face.FaceIndexingRepository(context.applicationContext)
                    repo.indexNewMedia(
                        media = listOf(item),
                        store = faceStore,
                        allowNativeFallback = true
                    )
                    faceReloadKey++
                } catch (_: Exception) {
                } finally {
                    isScanningPeople = false
                }
            }
        }
    }

    val yClosed = ViewerDrawerPhysics.getYClosed(screenHeightPx)
    val yHalf = ViewerDrawerPhysics.getYHalf(screenHeightPx)
    val yFull = ViewerDrawerPhysics.getYFull(screenHeightPx)

    val currentTopY = drawerTopY.value
    val isClosed = currentTopY >= yClosed - 1f
    val isFullyExpanded = abs(currentTopY - yFull) < 5f

    val drawerSpring = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 400f
        )
    }

    // Reset list scroll offsets back to 0 on dismiss
    LaunchedEffect(isClosed) {
        if (isClosed) {
            overviewListState.scrollToItem(0)
            actionsListState.scrollToItem(0)
        }
    }

    // 1. Dynamic Backdrop Scrim Layer with Piecewise Opacity:
    // Closed to Half: scales from 0.0 to 0.2
    // Half to Fully Expanded: scales from 0.2 to 0.6
    // Tapping the visible scrim when sheet is at Half or Fully Expanded immediately animates sheet to Closed.
    if (!isClosed) {
        val scrimAlpha = ViewerDrawerPhysics.calculateScrimAlpha(currentTopY, screenHeightPx)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    coroutineScope.launch {
                        drawerTopY.animateTo(
                            targetValue = yClosed,
                            animationSpec = drawerSpring
                        )
                        onClose()
                    }
                }
        )
    }

    // 2. Two-Phase Nested Scroll Resolution for internal scrollable metadata body
    var nestedScrollStartY by remember { mutableFloatStateOf(0f) }
    var isNestedScrollActive by remember { mutableStateOf(false) }

    val nestedScrollConnection = remember(screenHeightPx, density, yFull, yClosed) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y // dy < 0 is upward drag, dy > 0 is downward drag
                if (!isNestedScrollActive && dy != 0f) {
                    nestedScrollStartY = drawerTopY.value
                    isNestedScrollActive = true
                }

                // Dragging Upward (dy < 0): Pre-Scroll
                // If the sheet is below Y_full, parent consumes 100% of dy to expand toward Y_full.
                // The internal list scroll position remains clamped at 0.
                // Once anchored at Y_full, parent consumes 0, and internal list consumes dy.
                if (dy < 0f && drawerTopY.value > yFull) {
                    val newY = (drawerTopY.value + dy).coerceAtLeast(yFull)
                    val consumedY = newY - drawerTopY.value
                    coroutineScope.launch { drawerTopY.snapTo(newY) }
                    return Offset(0f, consumedY)
                }

                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y // dy > 0 is downward drag when internal list is at scrollY == 0
                if (!isNestedScrollActive && dy != 0f) {
                    nestedScrollStartY = drawerTopY.value
                    isNestedScrollActive = true
                }

                // Dragging Downward (dy > 0): Child First
                // If internal list is scrolled down (scrollY > 0), list consumes dy to scroll back to top.
                // Post-Scroll: Once list reaches scrollY == 0, remaining downward delta moves the sheet
                // from Fully Expanded down toward Half or Closed.
                if (dy > 0f && drawerTopY.value < yClosed) {
                    val newY = (drawerTopY.value + dy).coerceAtMost(yClosed)
                    val consumedY = newY - drawerTopY.value
                    coroutineScope.launch { drawerTopY.snapTo(newY) }
                    return Offset(0f, consumedY)
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val startY = if (nestedScrollStartY > 0f) nestedScrollStartY else drawerTopY.value
                isNestedScrollActive = false
                nestedScrollStartY = 0f

                // If flinging upward while expanding toward Y_full:
                if (available.y < 0 && drawerTopY.value > yFull) {
                    onSettle(startY, drawerTopY.value, available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val startY = if (nestedScrollStartY > 0f) nestedScrollStartY else drawerTopY.value
                isNestedScrollActive = false
                nestedScrollStartY = 0f

                // Top-Boundary Fling Dissipation:
                // If user flings downward inside metadata body, dissipate velocity once list hits scrollY == 0.
                // Do NOT carry that fling momentum into an accidental sheet collapse during the same gesture.
                if (available.y > 0) {
                    return available // Dissipates downward fling momentum completely
                }

                if (drawerTopY.value > yFull && drawerTopY.value < yClosed) {
                    onSettle(startY, drawerTopY.value, available.y)
                    return available
                }

                return Velocity.Zero
            }
        }
    }

    // 3. Overlay Detail Sheet Surface (Foreground)
    if (currentTopY < yClosed + 5f) {
        val sheetHeightDp = with(LocalDensity.current) { (screenHeightPx - yFull).toDp() }

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(sheetHeightDp)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = currentTopY.coerceIn(yFull, yClosed)
                },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header Interception: Non-scrollable header (drag handle + filename + date + tabs/actions)
                // Any touch originating here bypasses nested scroll logic and drives sheet translation directly.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(screenHeightPx, density, touchSlop) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)
                                var isVerticalDrag = false
                                var isHorizontal = false
                                var totalDx = 0f
                                var totalDy = 0f
                                var headerDragStartY = drawerTopY.value

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (isVerticalDrag) {
                                            val velocityY = velocityTracker.calculateVelocity().y
                                            coroutineScope.launch {
                                                onSettle(headerDragStartY, drawerTopY.value, velocityY.coerceIn(-800f, 800f))
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
                                                headerDragStartY = drawerTopY.value
                                                change.consume()
                                                val nextY = (drawerTopY.value + posChange.y).coerceIn(yFull, yClosed)
                                                coroutineScope.launch { drawerTopY.snapTo(nextY) }
                                            }
                                        }
                                    } else if (isVerticalDrag) {
                                        change.consume()
                                        val nextY = (drawerTopY.value + posChange.y).coerceIn(yFull, yClosed)
                                        coroutineScope.launch { drawerTopY.snapTo(nextY) }
                                    }
                                }
                            }
                        }
                ) {
                    Column {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
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

                        // Pinned Header & Tabs at top of drawer
                        DetailDrawerHeader(
                            item = item,
                            selectedTab = selectedTab,
                            onSelectTab = { selectedTab = it }
                        )
                    }
                }

                // Scrollable Body (EXIF metadata, technical camera parameters, map/location preview, tags)
                // State-Locked Scrolling: When sheet is at Closed or Half, internal list scrolling is clamped at 0.
                // Internal list scrolling is unlocked ONLY when sheet is resting at Fully Expanded (Y = Y_full).
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
                                onEditLocation = { showAddLocationDialog = true },
                                listState = overviewListState,
                                onScanPeople = scanFacesForThisItem,
                                isScanningPeople = isScanningPeople
                            )
                        }
                        DetailDrawerTab.ACTIONS -> {
                            ActionsTabContent(
                                item = item,
                                customTags = customTags,
                                detectedFaces = detectedFaces,
                                metadata = detailsState.metadata,
                                detailsState = detailsState,
                                onRenameFaceClick = { renamingFace = it },
                                listState = actionsListState
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
