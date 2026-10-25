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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
 * Dimensions, positioning, top rounded corners, and gestures are preserved.
 * Switching tabs changes only the content area without resizing or dismissing the drawer.
 */
@Composable
fun BoxScope.ViewerDetailsDrawer(
    item: MediaItem,
    items: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    customTags: Set<String>,
    showDetails: Boolean,
    detailsRevealTriggered: Boolean,
    detailsRevealPx: Float,
    detailsSheetHeightPx: Float,
    onHeightMeasured: (Float) -> Unit,
    onRevealPxChanged: (Float) -> Unit,
    onToggleDetails: () -> Unit,
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
    val viewConfiguration = ViewConfiguration.get(context)
    val coroutineScope = rememberCoroutineScope()
    val detailsState = rememberViewerDetailsState(item, defaultOcrLanguages, defaultOcrModel)

    var selectedTab by remember { mutableStateOf(DetailDrawerTab.OVERVIEW) }
    val faceStore = remember(context) { FaceClusterStore(context.applicationContext) }
    var faceReloadKey by remember { mutableIntStateOf(0) }

    val detectedFaces = remember(item.uri, faceReloadKey) {
        faceStore.facesForMedia(item.uri)
    }

    // Dialog state for Actions tab
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var renamingFace by remember { mutableStateOf<DetectedMediaFace?>(null) }

    // Backdrop scrim
    AnimatedVisibility(
        visible = showDetails,
        enter = fadeIn(animationSpec = tween(280)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(showDetails) {
                    var totalDragY = 0f
                    val touchSlop = viewConfiguration.scaledTouchSlop.toFloat()
                    val dismissThreshold = touchSlop * 3.5f
                    detectDragGestures(
                        onDragStart = { totalDragY = 0f },
                        onDragEnd = {
                            if (totalDragY > dismissThreshold) {
                                onToggleDetails()
                            }
                        },
                        onDragCancel = { totalDragY = 0f }
                    ) { change, dragAmount ->
                        totalDragY += dragAmount.y
                        if (kotlin.math.abs(totalDragY) > touchSlop) {
                            change.consume()
                        }
                    }
                }
                .clickable { onToggleDetails() }
        )
    }

    val sheetDragOffsetY = remember { Animatable(0f) }
    LaunchedEffect(showDetails) {
        sheetDragOffsetY.snapTo(0f)
        if (showDetails && !detailsRevealTriggered) {
            onRevealPxChanged(detailsSheetHeightPx)
        } else if (!showDetails) {
            onRevealPxChanged(0f)
        }
    }

    AnimatedVisibility(
        visible = showDetails,
        enter = fadeIn(animationSpec = tween(120)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(280, easing = FastOutLinearInEasing)
        ) + fadeOut(animationSpec = tween(180)),
        modifier = modifier.align(Alignment.BottomCenter)
    ) {
        Surface(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.50f)
                .onSizeChanged {
                    val h = it.height.toFloat()
                    onHeightMeasured(h)
                    if (showDetails && !detailsRevealTriggered) {
                        onRevealPxChanged(h)
                    }
                }
                .graphicsLayer {
                    translationY = (detailsSheetHeightPx - detailsRevealPx).coerceAtLeast(0f) +
                        sheetDragOffsetY.value.coerceAtLeast(0f)
                },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                // Top drag handle with drag down dismiss
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                        .pointerInput(showDetails) {
                            val velocityTracker = VelocityTracker()
                            val minFling = viewConfiguration.scaledMinimumFlingVelocity.toFloat()
                            val dismissDist = 80.dp.toPx()
                            detectDragGestures(
                                onDragStart = {
                                    velocityTracker.resetTracking()
                                    coroutineScope.launch { sheetDragOffsetY.snapTo(0f) }
                                },
                                onDragEnd = {
                                    val velocityY = velocityTracker.calculateVelocity().y
                                    coroutineScope.launch {
                                        when {
                                            (sheetDragOffsetY.value > dismissDist || velocityY > minFling) -> {
                                                sheetDragOffsetY.animateTo(
                                                    targetValue = detailsSheetHeightPx.coerceAtLeast(600f),
                                                    initialVelocity = velocityY,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                                onToggleDetails()
                                                sheetDragOffsetY.snapTo(0f)
                                            }
                                            else -> {
                                                sheetDragOffsetY.animateTo(
                                                    targetValue = 0f,
                                                    initialVelocity = velocityY,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                            }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        sheetDragOffsetY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                coroutineScope.launch {
                                    val next = (sheetDragOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                                    sheetDragOffsetY.snapTo(next)
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

                // Pinned Header & Tabs at top of drawer
                DetailDrawerHeader(
                    item = item,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )

                // Scrollable content area (Overview and Actions share exact same container dimensions)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        DetailDrawerTab.OVERVIEW -> {
                            OverviewTabContent(
                                item = item,
                                customTags = customTags,
                                detectedFaces = detectedFaces,
                                metadata = detailsState.metadata,
                                onOpenMap = onOpenMap
                            )
                        }
                        DetailDrawerTab.ACTIONS -> {
                            ActionsTabContent(
                                item = item,
                                customTags = customTags,
                                detectedFaces = detectedFaces,
                                metadata = detailsState.metadata,
                                detailsState = detailsState,
                                onAddTo = onAddTo,
                                onAddLocationClick = { showAddLocationDialog = true },
                                onAddTagClick = { showAddTagDialog = true },
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
