package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import coil.Coil
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

private enum class EditorMode { Crop, Adjust, Filters, Effects }
private enum class FilterCategory(val title: String) {
    All("All"),
    Portrait("Portrait"),
    Film("Film"),
    Vintage("Vintage"),
    Monochrome("B&W"),
    Colour("Colour")
}
private enum class EditorFilter(val title: String, val category: FilterCategory) {
    Original("Original", FilterCategory.All),
    Portrait("Portrait", FilterCategory.Portrait),
    Soft("Soft", FilterCategory.Portrait),
    Golden("Golden", FilterCategory.Portrait),
    Film("Film", FilterCategory.Film),
    Cinema("Cinema", FilterCategory.Film),
    TealOrange("Teal / Orange", FilterCategory.Film),
    Vintage("Vintage", FilterCategory.Vintage),
    Fade("Fade", FilterCategory.Vintage),
    Sepia("Sepia", FilterCategory.Vintage),
    Mono("Mono", FilterCategory.Monochrome),
    Noir("Noir", FilterCategory.Monochrome),
    Silver("Silver", FilterCategory.Monochrome),
    Warm("Warm", FilterCategory.Colour),
    Cool("Cool", FilterCategory.Colour),
    Vivid("Vivid", FilterCategory.Colour),
    Matte("Matte", FilterCategory.Colour),
    Rose("Rose", FilterCategory.Colour),
    Emerald("Emerald", FilterCategory.Colour)
}
private enum class CropEdge { Left, Top, Right, Bottom }
private enum class CropPreset(val title: String, val ratio: Float?) {
    Free("Free", null),
    Square("1:1", 1f),
    FourThree("4:3", 4f / 3f),
    ThreeTwo("3:2", 3f / 2f),
    SixteenNine("16:9", 16f / 9f),
    NineSixteen("9:16", 9f / 16f)
}

private data class EditorRecipe(
    val rotation: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val filter: EditorFilter = EditorFilter.Original,
    val filterStrength: Float = 1f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val fade: Float = 0f
)

@Composable
fun ImageEditorScreen(
    source: MediaItem,
    onBack: () -> Unit,
    onSaved: (MediaItem) -> Unit,
    startInFilters: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceBitmap by remember(source.uri) { mutableStateOf<Bitmap?>(null) }
    var recipe by remember(source.uri) { mutableStateOf(EditorRecipe()) }
    var undoStack by remember(source.uri) { mutableStateOf(emptyList<EditorRecipe>()) }
    var redoStack by remember(source.uri) { mutableStateOf(emptyList<EditorRecipe>()) }
    fun updateRecipe(next: EditorRecipe) {
        if (next == recipe) return
        undoStack = (undoStack + recipe).takeLast(30)
        redoStack = emptyList()
        recipe = next
    }
    fun undoRecipe() {
        val previous = undoStack.lastOrNull() ?: return
        redoStack = (redoStack + recipe).takeLast(30)
        undoStack = undoStack.dropLast(1)
        recipe = previous
    }
    fun redoRecipe() {
        val next = redoStack.lastOrNull() ?: return
        undoStack = (undoStack + recipe).takeLast(30)
        redoStack = redoStack.dropLast(1)
        recipe = next
    }
    var mode by remember(source.uri, startInFilters) {
        mutableStateOf(if (startInFilters) EditorMode.Filters else EditorMode.Crop)
    }
    var selectedFilterCategory by remember(source.uri) { mutableStateOf(FilterCategory.All) }
    var loading by remember(source.uri) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCropHandles by remember { mutableStateOf(true) }
    var cropPreset by remember { mutableStateOf(CropPreset.Free) }

    LaunchedEffect(source.uri) {
        loading = true
        error = null
        val decoded = withContext(Dispatchers.IO) {
            runCatching { decodeEditorBitmap(context, source.uri, maxDimension = 2048) }.getOrNull()
        }
        sourceBitmap = decoded
        loading = false
        if (decoded == null) error = "Unable to decode this image"
    }

    BackHandler(enabled = !saving) { onBack() }

    val image = sourceBitmap
    fun saveEditedImage() {
        if (image == null || saving || loading) return
        val currentRecipe = recipe
        saving = true
        error = null
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val fullResolution = decodeEditorBitmap(context, source.uri, maxDimension = null)
                        ?: throw IOException("Unable to decode the original image for export")
                    try {
                        exportEditorResult(context, fullResolution, currentRecipe, source)
                    } finally {
                        if (!fullResolution.isRecycled) fullResolution.recycle()
                    }
                }
            }
            saving = false
            saved.onSuccess(onSaved).onFailure {
                error = it.message ?: "Could not save the edited image"
            }
        }
    }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveEditedImage()
        else error = "Storage permission is required to save into Pictures/Emre"
    }
    var preview by remember(image) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(image, recipe) {
        val renderSource = image ?: run {
            preview = null
            return@LaunchedEffect
        }
        val rendered = withContext(Dispatchers.Default) {
            runCatching { applyEditorRecipe(renderSource, recipe, maxPreviewDimension = 2048) }.getOrNull()
        }
        val previous = preview
        preview = rendered
        if (previous != null && previous !== image && previous !== rendered && !previous.isRecycled) {
            previous.recycle()
        }
    }
    androidx.compose.runtime.DisposableEffect(preview, image) {
        onDispose {
            val bitmap = preview
            if (bitmap != null && bitmap !== image && !bitmap.isRecycled) bitmap.recycle()
        }
    }
    androidx.compose.runtime.DisposableEffect(image) {
        onDispose {
            if (image != null && !image.isRecycled) image.recycle()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !saving) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Editor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(source.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = ::undoRecipe, enabled = !saving && undoStack.isNotEmpty()) {
                Icon(Icons.Default.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = ::redoRecipe, enabled = !saving && redoStack.isNotEmpty()) {
                Icon(Icons.Default.Redo, contentDescription = "Redo")
            }
            TextButton(
                onClick = {
                    updateRecipe(EditorRecipe())
                    cropPreset = CropPreset.Free
                    showCropHandles = true
                },
                enabled = !saving
            ) { Text("Reset") }
            IconButton(
                onClick = {
                    if (image == null) return@IconButton
                    if (
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) {
                        writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        saveEditedImage()
                    }
                },
                enabled = image != null && !loading && !saving
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Save, contentDescription = "Save copy")
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val currentPreview = preview
            when {
                loading -> CircularProgressIndicator()
                currentPreview != null -> {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Color(0xFF202020)),
                        contentAlignment = Alignment.Center
                    ) {
                        val imageAspect = currentPreview.width.toFloat() / currentPreview.height
                        val boundsAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
                        val displayWidth = if (imageAspect > boundsAspect) maxWidth else maxHeight * imageAspect
                        val displayHeight = if (imageAspect > boundsAspect) maxWidth / imageAspect else maxHeight
                        Box(Modifier.width(displayWidth).height(displayHeight)) {
                            androidx.compose.foundation.Image(
                                bitmap = currentPreview.asImageBitmap(),
                                contentDescription = "Edited image preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                            if (mode == EditorMode.Crop && showCropHandles) {
                                CropOverlay(
                                    recipe = recipe,
                                    onRecipeChange = ::updateRecipe,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                else -> Text(error ?: "Image unavailable", color = MaterialTheme.colorScheme.error)
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorMode.entries.forEach { target ->
                val selected = mode == target
                Surface(
                    modifier = Modifier.weight(1f).clip(CircleShape).clickable { mode = target },
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape
                ) {
                    Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (target) {
                                EditorMode.Crop -> Icons.Default.Crop
                                EditorMode.Adjust -> Icons.Default.Tune
                                EditorMode.Filters -> Icons.Default.AutoAwesome
                                EditorMode.Effects -> Icons.Default.FilterVintage
                            },
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            when (target) {
                                EditorMode.Crop -> "Crop"
                                EditorMode.Adjust -> "Adjust"
                                EditorMode.Filters -> "Filters"
                                EditorMode.Effects -> "Effects"
                            },
                            modifier = Modifier.padding(start = 7.dp),
                            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (mode == EditorMode.Crop) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
            ) {
                item { EditorAction("90°", Icons.Default.Rotate90DegreesCcw) { updateRecipe(recipe.copy(rotation = (recipe.rotation + 90) % 360)) } }
                item { EditorAction("Flip H", Icons.Default.Flip) { updateRecipe(recipe.copy(flipHorizontal = !recipe.flipHorizontal)) } }
                item { EditorAction("Flip V", Icons.Default.Flip) { updateRecipe(recipe.copy(flipVertical = !recipe.flipVertical)) } }
                item { EditorAction("Reset crop", Icons.Default.Refresh) { updateRecipe(recipe.copy(cropLeft=0f,cropTop=0f,cropRight=1f,cropBottom=1f)); cropPreset = CropPreset.Free } }
                item { EditorAction("Apply crop", Icons.Default.Check) { showCropHandles = false } }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(CropPreset.entries.size) { index ->
                    val preset = CropPreset.entries[index]
                    val selected = preset == cropPreset
                    Surface(
                        modifier = Modifier.clip(CircleShape).clickable {
                            cropPreset = preset
                            val sourceAspect = image?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f
                            val rotatedAspect = if (recipe.rotation % 180 == 0) sourceAspect else 1f / sourceAspect
                            updateRecipe(applyCropPreset(recipe, preset.ratio, rotatedAspect))
                            showCropHandles = true
                        },
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape
                    ) {
                        Text(
                            preset.title,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Text(
                "Drag the crop edges over the image. Export creates a new copy.",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (mode == EditorMode.Adjust) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider("Brightness", recipe.brightness, -0.5f..0.5f) { updateRecipe(recipe.copy(brightness = it)) }
                AdjustmentSlider("Contrast", recipe.contrast, 0.5f..1.8f) { updateRecipe(recipe.copy(contrast = it)) }
                AdjustmentSlider("Saturation", recipe.saturation, 0f..2f) { updateRecipe(recipe.copy(saturation = it)) }
            }
        } else if (mode == EditorMode.Filters) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(FilterCategory.entries.size) { index ->
                    val category = FilterCategory.entries[index]
                    val selected = category == selectedFilterCategory
                    Surface(
                        modifier = Modifier.clip(CircleShape).clickable { selectedFilterCategory = category },
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape
                    ) {
                        Text(
                            category.title,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            val availableFilters = EditorFilter.entries.filter {
                selectedFilterCategory == FilterCategory.All || it.category == selectedFilterCategory
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableFilters.size) { index ->
                    val filter = availableFilters[index]
                    val selected = recipe.filter == filter
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(13.dp)).clickable {
                            updateRecipe(recipe.copy(filter = filter))
                        },
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(13.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                filter.title,
                                color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                if (selected) "Selected" else filter.category.title,
                                color = if (selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                AdjustmentSlider("Strength", recipe.filterStrength, 0f..1f) {
                    updateRecipe(recipe.copy(filterStrength = it))
                }
                Text(
                    "19 non-destructive colour presets. Strength blends each filter with the original.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                AdjustmentSlider("Vignette", recipe.vignette, 0f..1f) {
                    updateRecipe(recipe.copy(vignette = it))
                }
                AdjustmentSlider("Grain", recipe.grain, 0f..1f) {
                    updateRecipe(recipe.copy(grain = it))
                }
                AdjustmentSlider("Fade", recipe.fade, 0f..1f) {
                    updateRecipe(recipe.copy(fade = it))
                }
                Text(
                    "Effects are applied after colour grading and remain editable until export.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtLeast(4.dp)))
    }
}

@Composable
private fun EditorAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(17.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(86.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(
            if (label == "Brightness") "${(value * 100).roundToInt()}" else "${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(43.dp)
        )
    }
}

@Composable
private fun CropOverlay(
    recipe: EditorRecipe,
    onRecipeChange: (EditorRecipe) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRecipe by rememberUpdatedState(recipe)
    val currentOnRecipeChange by rememberUpdatedState(onRecipeChange)
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            var activeEdge: CropEdge? = null
            detectDragGestures(
                onDragStart = { offset ->
                    val current = currentRecipe
                    val positions = listOf(
                        CropEdge.Left to kotlin.math.abs(offset.x - size.width * current.cropLeft),
                        CropEdge.Top to kotlin.math.abs(offset.y - size.height * current.cropTop),
                        CropEdge.Right to kotlin.math.abs(offset.x - size.width * current.cropRight),
                        CropEdge.Bottom to kotlin.math.abs(offset.y - size.height * current.cropBottom)
                    )
                    val nearest = positions.minByOrNull { it.second }
                    activeEdge = nearest?.takeIf { it.second <= 36.dp.toPx() }?.first
                },
                onDragEnd = { activeEdge = null },
                onDragCancel = { activeEdge = null },
                onDrag = { change, drag ->
                    val edge = activeEdge ?: return@detectDragGestures
                    change.consume()
                    val current = currentRecipe
                    val dx = drag.x / size.width
                    val dy = drag.y / size.height
                    val minimumCropSize = 0.08f
                    val next = when (edge) {
                        CropEdge.Left -> current.copy(
                            cropLeft = (current.cropLeft + dx).coerceIn(0f, current.cropRight - minimumCropSize)
                        )
                        CropEdge.Top -> current.copy(
                            cropTop = (current.cropTop + dy).coerceIn(0f, current.cropBottom - minimumCropSize)
                        )
                        CropEdge.Right -> current.copy(
                            cropRight = (current.cropRight + dx).coerceIn(current.cropLeft + minimumCropSize, 1f)
                        )
                        CropEdge.Bottom -> current.copy(
                            cropBottom = (current.cropBottom + dy).coerceIn(current.cropTop + minimumCropSize, 1f)
                        )
                    }
                    currentOnRecipeChange(next)
                }
            )
        }
    ) {
        val l = size.width * recipe.cropLeft
        val t = size.height * recipe.cropTop
        val r = size.width * recipe.cropRight
        val b = size.height * recipe.cropBottom
        val dim = Color.Black.copy(alpha = 0.48f)
        drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, t))
        drawRect(dim, topLeft = Offset(0f, t), size = Size(l, b - t))
        drawRect(dim, topLeft = Offset(r, t), size = Size(size.width - r, b - t))
        drawRect(dim, topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
        drawRect(Color.White, topLeft = Offset(l, t), size = Size(r - l, b - t), style = Stroke(width = 2.dp.toPx()))
        for (i in 1..2) {
            val x = l + (r - l) * i / 3f
            val y = t + (b - t) * i / 3f
            drawLine(Color.White.copy(alpha = 0.55f), Offset(x, t), Offset(x, b), strokeWidth = 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.55f), Offset(l, y), Offset(r, y), strokeWidth = 1.dp.toPx())
        }
        val handle = 10.dp.toPx()
        listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach {
            drawCircle(Color.White, handle, it)
        }
    }
}

private fun applyCropPreset(recipe: EditorRecipe, ratio: Float?, imageAspect: Float): EditorRecipe {
    if (ratio == null) {
        return recipe.copy(cropLeft = 0f, cropTop = 0f, cropRight = 1f, cropBottom = 1f)
    }
    val targetNormalizedRatio = ratio / imageAspect.coerceAtLeast(0.0001f)
    val currentWidth = recipe.cropRight - recipe.cropLeft
    val currentHeight = recipe.cropBottom - recipe.cropTop
    val currentRatio = currentWidth / currentHeight
    val width: Float
    val height: Float
    if (currentRatio > targetNormalizedRatio) {
        height = currentHeight
        width = height * targetNormalizedRatio
    } else {
        width = currentWidth
        height = width / targetNormalizedRatio
    }
    val centerX = (recipe.cropLeft + recipe.cropRight) / 2f
    val centerY = (recipe.cropTop + recipe.cropBottom) / 2f
    val left = (centerX - width / 2f).coerceIn(0f, 1f - width)
    val top = (centerY - height / 2f).coerceIn(0f, 1f - height)
    return recipe.copy(cropLeft = left, cropTop = top, cropRight = left + width, cropBottom = top + height)
}

private fun applyEditorRecipe(
    source: Bitmap,
    recipe: EditorRecipe,
    maxPreviewDimension: Int? = null
): Bitmap {
    val working = if (maxPreviewDimension != null && maxOf(source.width, source.height) > maxPreviewDimension) {
        val ratio = maxPreviewDimension.toFloat() / maxOf(source.width, source.height)
        Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).roundToInt().coerceAtLeast(1),
            (source.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
    } else source

    // Keep each geometric operation centered. In particular, mirroring around the
    // bitmap center prevents a negative scale from moving the image off-canvas.
    var transformed = working
    if (recipe.rotation % 360 != 0) {
        val rotationMatrix = Matrix().apply { setRotate(recipe.rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(
            transformed, 0, 0, transformed.width, transformed.height, rotationMatrix, true
        )
        if (rotated !== transformed && transformed !== source && !transformed.isRecycled) {
            transformed.recycle()
        }
        transformed = rotated
    }
    if (recipe.flipHorizontal || recipe.flipVertical) {
        val flipMatrix = Matrix().apply {
            setScale(
                if (recipe.flipHorizontal) -1f else 1f,
                if (recipe.flipVertical) -1f else 1f,
                transformed.width / 2f,
                transformed.height / 2f
            )
        }
        val flipped = Bitmap.createBitmap(
            transformed, 0, 0, transformed.width, transformed.height, flipMatrix, true
        )
        if (flipped !== transformed && transformed !== source && !transformed.isRecycled) {
            transformed.recycle()
        }
        transformed = flipped
    }

    val left = (transformed.width * recipe.cropLeft).roundToInt().coerceIn(0, transformed.width - 1)
    val top = (transformed.height * recipe.cropTop).roundToInt().coerceIn(0, transformed.height - 1)
    val right = (transformed.width * recipe.cropRight).roundToInt().coerceIn(left + 1, transformed.width)
    val bottom = (transformed.height * recipe.cropBottom).roundToInt().coerceIn(top + 1, transformed.height)
    val cropped = if (left == 0 && top == 0 && right == transformed.width && bottom == transformed.height) {
        transformed
    } else {
        Bitmap.createBitmap(transformed, left, top, right - left, bottom - top)
    }
    if (cropped !== transformed && transformed !== source && !transformed.isRecycled) {
        transformed.recycle()
    }

    val hasAdjustments = recipe.brightness != 0f || recipe.contrast != 1f || recipe.saturation != 1f
    val hasFilter = recipe.filter != EditorFilter.Original && recipe.filterStrength > 0f
    var adjusted = cropped
    if (hasAdjustments || hasFilter) {
        val brightnessOffset = recipe.brightness * 255f
        val contrast = recipe.contrast
        val saturation = recipe.saturation
        val lumR = 0.213f * (1f - saturation)
        val lumG = 0.715f * (1f - saturation)
        val lumB = 0.072f * (1f - saturation)
        val adjustmentMatrix = android.graphics.ColorMatrix(floatArrayOf(
            contrast * (lumR + saturation), contrast * lumG, contrast * lumB, 0f, brightnessOffset,
            contrast * lumR, contrast * (lumG + saturation), contrast * lumB, 0f, brightnessOffset,
            contrast * lumR, contrast * lumG, contrast * (lumB + saturation), 0f, brightnessOffset,
            0f, 0f, 0f, 1f, 0f
        ))
        val filterValues = blendFilterWithIdentity(
            editorFilterMatrix(recipe.filter),
            if (hasFilter) recipe.filterStrength else 0f
        )
        val filterMatrix = android.graphics.ColorMatrix(filterValues)
        val combinedMatrix = android.graphics.ColorMatrix().apply {
            setConcat(filterMatrix, adjustmentMatrix)
        }
        adjusted = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(combinedMatrix)
        }
        android.graphics.Canvas(adjusted).drawBitmap(cropped, 0f, 0f, paint)
        if (cropped !== source && !cropped.isRecycled) cropped.recycle()
    }

    val hasSpatialEffects = recipe.vignette > 0f || recipe.grain > 0f || recipe.fade > 0f
    if (!hasSpatialEffects) return adjusted
    val effected = applyEditorEffects(adjusted, recipe.vignette, recipe.grain, recipe.fade)
    if (effected !== adjusted && adjusted !== source && !adjusted.isRecycled) adjusted.recycle()
    return effected
}



private fun buildToneMatrix(
    saturation: Float,
    contrast: Float = 1f,
    redOffset: Float = 0f,
    greenOffset: Float = 0f,
    blueOffset: Float = 0f
): FloatArray {
    val lumR = 0.213f * (1f - saturation)
    val lumG = 0.715f * (1f - saturation)
    val lumB = 0.072f * (1f - saturation)
    return floatArrayOf(
        contrast * (lumR + saturation), contrast * lumG, contrast * lumB, 0f, redOffset,
        contrast * lumR, contrast * (lumG + saturation), contrast * lumB, 0f, greenOffset,
        contrast * lumR, contrast * lumG, contrast * (lumB + saturation), 0f, blueOffset,
        0f, 0f, 0f, 1f, 0f
    )
}

private fun editorFilterMatrix(filter: EditorFilter): FloatArray = when (filter) {
    EditorFilter.Original -> floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    EditorFilter.Portrait -> buildToneMatrix(0.98f, 0.97f, 9f, 5f, 1f)
    EditorFilter.Soft -> buildToneMatrix(0.88f, 0.9f, 13f, 11f, 8f)
    EditorFilter.Golden -> buildToneMatrix(1.12f, 1.05f, 20f, 9f, -15f)
    EditorFilter.Film -> buildToneMatrix(0.82f, 1.08f, 7f, 3f, -2f)
    EditorFilter.Cinema -> buildToneMatrix(0.92f, 1.22f, -3f, 2f, 8f)
    EditorFilter.TealOrange -> floatArrayOf(
        1.12f, 0.05f, -0.02f, 0f, 8f,
        0.02f, 0.98f, 0.02f, 0f, 1f,
        -0.03f, 0.12f, 1.08f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f
    )
    EditorFilter.Vintage -> buildToneMatrix(0.72f, 0.91f, 15f, 8f, -5f)
    EditorFilter.Fade -> buildToneMatrix(0.78f, 0.84f, 29f, 27f, 22f)
    EditorFilter.Sepia -> floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    EditorFilter.Mono -> buildToneMatrix(0f)
    EditorFilter.Noir -> buildToneMatrix(0f, 1.34f, -15f, -15f, -15f)
    EditorFilter.Silver -> buildToneMatrix(0.08f, 1.08f, 8f, 8f, 8f)
    EditorFilter.Warm -> buildToneMatrix(1.04f, 1.02f, 11f, 4f, -9f)
    EditorFilter.Cool -> buildToneMatrix(1.02f, 1.02f, -8f, 2f, 13f)
    EditorFilter.Vivid -> buildToneMatrix(1.42f, 1.08f)
    EditorFilter.Matte -> buildToneMatrix(0.84f, 0.87f, 18f, 18f, 18f)
    EditorFilter.Rose -> buildToneMatrix(1.08f, 1.01f, 14f, 0f, 7f)
    EditorFilter.Emerald -> buildToneMatrix(1.12f, 1.04f, -4f, 12f, 3f)
}

private fun blendFilterWithIdentity(matrix: FloatArray, amount: Float): FloatArray {
    val identity = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    val strength = amount.coerceIn(0f, 1f)
    return FloatArray(20) { index ->
        identity[index] + (matrix[index] - identity[index]) * strength
    }
}

private fun applyEditorEffects(
    source: Bitmap,
    vignette: Float,
    grain: Float,
    fade: Float
): Bitmap {
    if (vignette <= 0f && grain <= 0f && fade <= 0f) return source
    val width = source.width
    val height = source.height
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val row = IntArray(width)
    val centerX = (width - 1) / 2f
    val centerY = (height - 1) / 2f
    val maxRadiusSquared = (centerX * centerX + centerY * centerY).coerceAtLeast(1f)
    val vignetteAmount = vignette.coerceIn(0f, 1f) * 0.62f
    val grainAmount = grain.coerceIn(0f, 1f) * 26f
    val fadeAmount = fade.coerceIn(0f, 1f)

    for (y in 0 until height) {
        source.getPixels(row, 0, width, 0, y, width, 1)
        val dy = y - centerY
        for (x in 0 until width) {
            val pixel = row[x]
            val alpha = android.graphics.Color.alpha(pixel)
            var red = android.graphics.Color.red(pixel).toFloat()
            var green = android.graphics.Color.green(pixel).toFloat()
            var blue = android.graphics.Color.blue(pixel).toFloat()

            if (fadeAmount > 0f) {
                val lift = fadeAmount * 27f
                val compression = 1f - fadeAmount * 0.2f
                red = red * compression + lift
                green = green * compression + lift
                blue = blue * compression + lift
            }

            if (vignetteAmount > 0f) {
                val dx = x - centerX
                val radiusSquared = (dx * dx + dy * dy) / maxRadiusSquared
                val shade = (1f - vignetteAmount * radiusSquared).coerceIn(0.35f, 1f)
                red *= shade
                green *= shade
                blue *= shade
            }

            if (grainAmount > 0f) {
                var seed = (x * 0x1f123bb5) xor (y * 0x5f356495)
                seed = (seed xor (seed ushr 13)) * 1274126177
                val noise = (((seed ushr 24) and 0xff) / 255f - 0.5f) * grainAmount
                red += noise
                green += noise
                blue += noise
            }

            row[x] = android.graphics.Color.argb(
                alpha,
                red.toInt().coerceIn(0, 255),
                green.toInt().coerceIn(0, 255),
                blue.toInt().coerceIn(0, 255)
            )
        }
        output.setPixels(row, 0, width, 0, y, width, 1)
    }
    return output
}

private fun decodeEditorBitmap(
    context: Context,
    imageUri: String,
    maxDimension: Int?
): Bitmap? {
    val uri = Uri.parse(imageUri)
    val requestBuilder = ImageRequest.Builder(context)
        .data(uri)
        .allowHardware(false)
    if (maxDimension == null) {
        requestBuilder.size(CoilSize.ORIGINAL)
    } else {
        requestBuilder.size(maxDimension, maxDimension)
    }
    val coilDrawable = Coil.imageLoader(context).execute(requestBuilder.build()).drawable
    val coilBitmap = (coilDrawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
    if (coilBitmap != null) {
        return coilBitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: throw IOException("Could not create an editor-owned bitmap")
    }

    // Fallback path for image providers that Coil cannot decode into a BitmapDrawable.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    } ?: return null
    var sampleSize = 1
    if (maxDimension != null) {
        while (
            bounds.outWidth / sampleSize > maxDimension ||
            bounds.outHeight / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}

private fun copyExifMetadata(
    context: Context,
    sourceUri: Uri,
    destinationFile: File,
    width: Int,
    height: Int
) {
    val sourceExif = runCatching {
        context.contentResolver.openInputStream(sourceUri)?.use { ExifInterface(it) }
    }.getOrNull() ?: return

    runCatching {
        val destinationExif = ExifInterface(destinationFile.absolutePath)
        // Retain useful capture, camera and location metadata while avoiding the
        // source orientation flag because the edited pixels are already upright.
        val preservedTags = listOf(
            "DateTime",
            "DateTimeOriginal",
            "DateTimeDigitized",
            "SubSecTime",
            "SubSecTimeOriginal",
            "SubSecTimeDigitized",
            "Make",
            "Model",
            "Software",
            "ImageDescription",
            "UserComment",
            "Copyright",
            "ExposureTime",
            "FNumber",
            "ISOSpeedRatings",
            "FocalLength",
            "Flash",
            "WhiteBalance",
            "MeteringMode",
            "ExposureProgram",
            "ExposureBiasValue",
            "LensMake",
            "LensModel",
            "GPSLatitude",
            "GPSLatitudeRef",
            "GPSLongitude",
            "GPSLongitudeRef",
            "GPSAltitude",
            "GPSAltitudeRef",
            "GPSTimeStamp",
            "GPSDateStamp",
            "GPSProcessingMethod"
        )
        preservedTags.forEach { tag ->
            sourceExif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let {
                destinationExif.setAttribute(tag, it)
            }
        }
        destinationExif.setAttribute("Orientation", ExifInterface.ORIENTATION_NORMAL.toString())
        destinationExif.setAttribute("ImageWidth", width.toString())
        destinationExif.setAttribute("ImageLength", height.toString())
        destinationExif.setAttribute("PixelXDimension", width.toString())
        destinationExif.setAttribute("PixelYDimension", height.toString())
        destinationExif.saveAttributes()
    }
}

private suspend fun scanMediaFile(context: Context, file: File): Uri =
    suspendCancellableCoroutine { continuation ->
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg")
        ) { _, scannedUri ->
            if (!continuation.isActive) return@scanFile
            if (scannedUri != null) {
                continuation.resume(scannedUri)
            } else {
                continuation.resumeWithException(IOException("Media scanner could not index the exported image"))
            }
        }
    }

private suspend fun exportEditorResult(
    context: Context,
    source: Bitmap,
    recipe: EditorRecipe,
    media: MediaItem
): MediaItem {
    val output = applyEditorRecipe(source, recipe)
    val name = "EMRE_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
    var uri: Uri? = null
    var legacyFile: File? = null
    var tempFile: File? = null
    try {
        tempFile = File.createTempFile("emre-edit-", ".jpg", context.cacheDir)
        tempFile.outputStream().use { stream ->
            if (!output.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                throw IOException("Image encoding failed")
            }
            stream.flush()
        }
        copyExifMetadata(context, Uri.parse(media.uri), tempFile, output.width, output.height)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Emre")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            uri = context.contentResolver.insert(collection, values)
                ?: throw IOException("Could not create output image")
            context.contentResolver.openOutputStream(uri, "w")?.use { destination ->
                tempFile.inputStream().use { input -> input.copyTo(destination) }
            } ?: throw IOException("Could not open output stream")
            val updated = context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
            if (updated <= 0) throw IOException("Could not finalize the exported image")
        } else {
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val directory = File(pictures, "Emre")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Could not create Pictures/Emre")
            }
            legacyFile = File(directory, name)
            tempFile.copyTo(legacyFile, overwrite = false)
            uri = scanMediaFile(context, legacyFile)
        }

        val outputUri = uri ?: throw IOException("Image export did not produce a URI")
        val width = output.width
        val height = output.height
        val sizeBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.openFileDescriptor(outputUri, "r")?.use { it.statSize } ?: 0L
            }.getOrDefault(0L)
        } else {
            legacyFile?.length() ?: 0L
        }
        val now = System.currentTimeMillis()
        return MediaItem(
            id = if (outputUri.authority == MediaStore.AUTHORITY) {
                android.content.ContentUris.parseId(outputUri)
            } else {
                legacyFile?.lastModified() ?: now
            },
            uri = outputUri.toString(),
            name = name,
            mimeType = "image/jpeg",
            dateAddedSeconds = now / 1000L,
            dateTakenMillis = media.dateTakenMillis,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            relativePath = "Pictures/Emre/",
            isVideo = false
        )
    } catch (t: Throwable) {
        uri?.let { outputUri ->
            if (outputUri.scheme == "content") {
                runCatching { context.contentResolver.delete(outputUri, null, null) }
            }
        }
        legacyFile?.let { if (it.exists()) it.delete() }
        throw t
    } finally {
        tempFile?.let { if (it.exists()) it.delete() }
        if (output !== source && !output.isRecycled) output.recycle()
    }
}
