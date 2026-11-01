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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FilterVintage
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import coil.Coil
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.amresalehin.emregallery.data.MediaItem
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private enum class EditorMode { Crop, Adjust, Filters, Effects, Drawing, Resize, AI }

private enum class EditorOutputFormat(val title: String, val extension: String, val mimeType: String) {
    JPEG("JPEG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WebP", "webp", "image/webp")
}

private data class EditorExportSettings(
    val resizeEnabled: Boolean,
    val width: Int,
    val height: Int,
    val format: EditorOutputFormat,
    val quality: Int,
    val targetSizeBytes: Long?
)

private enum class MarkupTool(val title: String) {
    Pen("Pen"), Highlighter("Highlight"), Eraser("Erase"), Line("Line"),
    Arrow("Arrow"), Rectangle("Rect"), Ellipse("Oval"), Text("Text")
}

private data class MarkupPoint(val x: Float, val y: Float)

private data class EditorMarkup(
    val tool: MarkupTool,
    val points: List<MarkupPoint>,
    val text: String = "",
    val colorArgb: Int = android.graphics.Color.WHITE,
    val strokeWidthFraction: Float = 0.008f
)

private val EDITOR_MARKUP_COLORS = listOf(
    android.graphics.Color.WHITE, android.graphics.Color.BLACK,
    android.graphics.Color.RED, android.graphics.Color.YELLOW,
    android.graphics.Color.GREEN, android.graphics.Color.CYAN,
    android.graphics.Color.BLUE, android.graphics.Color.MAGENTA
)
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
    Emerald("Emerald", FilterCategory.Colour),
    Natural("Natural", FilterCategory.Portrait),
    Cream("Cream", FilterCategory.Portrait),
    Peach("Peach", FilterCategory.Portrait),
    Honey("Honey", FilterCategory.Portrait),
    KodakGold("Kodak Gold", FilterCategory.Film),
    Portra400("Portra 400", FilterCategory.Film),
    Fuji("Fuji", FilterCategory.Film),
    BlueHour("Blue Hour", FilterCategory.Film),
    TealCinema("Teal Cinema", FilterCategory.Film),
    Dusty("Dusty", FilterCategory.Vintage),
    Polaroid("Polaroid", FilterCategory.Vintage),
    Amber("Amber", FilterCategory.Vintage),
    FadedPrint("Faded Print", FilterCategory.Vintage),
    HighKey("High Key", FilterCategory.Monochrome),
    Dramatic("Dramatic", FilterCategory.Monochrome),
    Infrared("Infrared", FilterCategory.Monochrome),
    Arctic("Arctic", FilterCategory.Colour),
    RoseGold("Rose Gold", FilterCategory.Colour),
    Copper("Copper", FilterCategory.Colour),
    Lavender("Lavender", FilterCategory.Colour),
    Ocean("Ocean", FilterCategory.Colour)
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
    val exposure: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val filter: EditorFilter = EditorFilter.Original,
    val filterStrength: Float = 1f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val fade: Float = 0f,
    val softFocus: Float = 0f,
    val sharpen: Float = 0f,
    val pixelate: Float = 0f,
    val removePersonBackground: Boolean = false,
    val markups: List<EditorMarkup> = emptyList()
)

@Composable
fun ImageEditorScreen(
    source: MediaItem,
    onBack: () -> Unit,
    onSaved: (MediaItem) -> Unit,
    startInFilters: Boolean = false,
    startInAi: Boolean = false,
    startInDrawing: Boolean = false,
    startInResize: Boolean = false
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
    var mode by remember(source.uri, startInFilters, startInAi, startInDrawing, startInResize) {
        mutableStateOf(when {
            startInResize -> EditorMode.Resize
            startInDrawing -> EditorMode.Drawing
            startInAi -> EditorMode.AI
            startInFilters -> EditorMode.Filters
            else -> EditorMode.Crop
        })
    }
    var selectedFilterCategory by remember(source.uri) { mutableStateOf(FilterCategory.All) }
    var markupTool by remember(source.uri) { mutableStateOf(MarkupTool.Pen) }
    var markupColor by remember(source.uri) { mutableIntStateOf(android.graphics.Color.WHITE) }
    var markupStrokeWidth by remember(source.uri) { mutableFloatStateOf(0.008f) }
    var markupText by remember(source.uri) { mutableStateOf("Your text") }
    var resizeEnabled by remember(source.uri) { mutableStateOf(false) }
    var exportWidthText by remember(source.uri) { mutableStateOf(source.width.coerceAtLeast(1).toString()) }
    var exportHeightText by remember(source.uri) { mutableStateOf(source.height.coerceAtLeast(1).toString()) }
    var lockExportAspect by remember(source.uri) { mutableStateOf(true) }
    var exportFormat by remember(source.uri, startInAi) { mutableStateOf(if (startInAi) EditorOutputFormat.PNG else EditorOutputFormat.JPEG) }
    var exportQuality by remember(source.uri) { mutableFloatStateOf(92f) }
    var fitTargetSize by remember(source.uri) { mutableStateOf(false) }
    var targetSizeMbText by remember(source.uri) { mutableStateOf("2") }
    var loading by remember(source.uri) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCropHandles by remember { mutableStateOf(true) }
    var showOriginalPreview by remember(source.uri) { mutableStateOf(false) }
    var cropPreset by remember { mutableStateOf(CropPreset.Free) }

    LaunchedEffect(source.uri) {
        loading = true
        error = null
        val decoded = withContext(Dispatchers.IO) {
            runCatching { decodeEditorBitmap(context, source.uri, maxDimension = 2048) }.getOrNull()
        }
        sourceBitmap = decoded
        if (source.width <= 0 && decoded != null) exportWidthText = decoded.width.toString()
        if (source.height <= 0 && decoded != null) exportHeightText = decoded.height.toString()
        loading = false
        if (decoded == null) error = "Unable to decode this image"
    }

    BackHandler(enabled = !saving) { onBack() }

    val image = sourceBitmap
    fun saveEditedImage() {
        if (image == null || saving || loading) return
        val currentRecipe = recipe
        val requestedWidth = exportWidthText.toIntOrNull()
        val requestedHeight = exportHeightText.toIntOrNull()
        if (resizeEnabled && (requestedWidth == null || requestedHeight == null || requestedWidth <= 0 || requestedHeight <= 0)) {
            error = "Enter valid positive output dimensions"
            return
        }
        val targetBytes = if (fitTargetSize && exportFormat != EditorOutputFormat.PNG) {
            val targetMb = targetSizeMbText.toDoubleOrNull()
            if (targetMb == null || !targetMb.isFinite() || targetMb !in 0.05..200.0) {
                error = "Target size must be between 0.05 and 200 MB"
                return
            }
            (targetMb * 1024.0 * 1024.0).roundToInt().toLong()
        } else null
        val exportSettings = EditorExportSettings(
            resizeEnabled = resizeEnabled,
            width = requestedWidth ?: source.width.coerceAtLeast(1),
            height = requestedHeight ?: source.height.coerceAtLeast(1),
            format = exportFormat,
            quality = exportQuality.roundToInt().coerceIn(10, 100),
            targetSizeBytes = targetBytes
        )
        saving = true
        error = null
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val fullResolution = decodeEditorBitmap(context, source.uri, maxDimension = null)
                        ?: throw IOException("Unable to decode the original image for export")
                    try {
                        exportEditorResult(context, fullResolution, currentRecipe, source, exportSettings)
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
    val basePreviewRecipe = recipe.copy(markups = emptyList())
    LaunchedEffect(image, basePreviewRecipe) {
        val renderSource = image ?: run {
            preview = null
            return@LaunchedEffect
        }
        val renderResult = withContext(Dispatchers.Default) {
            runCatching {
                val base = applyEditorRecipe(renderSource, basePreviewRecipe, maxPreviewDimension = 2048)
                if (!basePreviewRecipe.removePersonBackground) {
                    base
                } else {
                    try {
                        removePersonBackground(base)
                    } finally {
                        if (base !== renderSource && !base.isRecycled) base.recycle()
                    }
                }
            }
        }
        val rendered = renderResult.getOrNull()
        if (renderResult.isFailure) {
            error = renderResult.exceptionOrNull()?.message ?: "AI preview could not be generated"
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
    LaunchedEffect(preview, image, resizeEnabled) {
        val rendered = preview
        val original = image
        if (!resizeEnabled && rendered != null && original != null) {
            val baseWidth = source.width.takeIf { it > 0 } ?: original.width
            val baseHeight = source.height.takeIf { it > 0 } ?: original.height
            exportWidthText = (rendered.width.toFloat() * baseWidth / original.width.coerceAtLeast(1)).roundToInt().coerceAtLeast(1).toString()
            exportHeightText = (rendered.height.toFloat() * baseHeight / original.height.coerceAtLeast(1)).roundToInt().coerceAtLeast(1).toString()
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
                else Icon(Icons.Default.Save, contentDescription = "Save ${exportFormat.title} copy")
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val currentPreview = if (showOriginalPreview) image else preview
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
                            if (mode == EditorMode.Crop && showCropHandles && !showOriginalPreview) {
                                CropOverlay(
                                    recipe = recipe,
                                    onRecipeChange = ::updateRecipe,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (mode == EditorMode.Drawing && !showOriginalPreview) {
                                DrawMarkupOverlay(
                                    layers = recipe.markups,
                                    tool = markupTool,
                                    colorArgb = markupColor,
                                    strokeWidthFraction = markupStrokeWidth,
                                    text = markupText,
                                    onCommit = { layer -> updateRecipe(recipe.copy(markups = recipe.markups + layer)) },
                                    onTextTap = { point ->
                                        if (markupText.isNotBlank()) {
                                            updateRecipe(recipe.copy(markups = recipe.markups + EditorMarkup(
                                                tool = MarkupTool.Text, points = listOf(point), text = markupText,
                                                colorArgb = markupColor, strokeWidthFraction = markupStrokeWidth
                                            )))
                                        } else { error = "Enter some text before placing it" }
                                    },
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { showOriginalPreview = !showOriginalPreview },
                enabled = image != null && preview != null && !loading
            ) {
                Text(if (showOriginalPreview) "Show edited result" else "Compare with original")
            }
            Text(
                if (showOriginalPreview) "Before" else "After",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
        ) {
            items(EditorMode.entries.size) { index ->
                val target = EditorMode.entries[index]
                val selected = mode == target
                Surface(
                    modifier = Modifier.clip(CircleShape).clickable { mode = target },
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape
                ) {
                    Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (target) {
                                EditorMode.Crop -> Icons.Default.Crop
                                EditorMode.Adjust -> Icons.Default.Tune
                                EditorMode.Filters -> Icons.Default.AutoAwesome
                                EditorMode.Effects -> Icons.Default.FilterVintage
                                EditorMode.Drawing -> Icons.Default.Brush
                                EditorMode.Resize -> Icons.Default.Save
                                EditorMode.AI -> Icons.Default.AutoAwesome
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
                                EditorMode.Drawing -> "Draw"
                                EditorMode.Resize -> "Export"
                                EditorMode.AI -> "AI"
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
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AdjustmentSlider(
                    "Brightness", recipe.brightness, -0.5f..0.5f,
                    valueLabel = { (it * 100f).roundToInt().toString() }
                ) { updateRecipe(recipe.copy(brightness = it)) }
                AdjustmentSlider(
                    "Exposure", recipe.exposure, -2f..2f,
                    valueLabel = { "${(it * 10f).roundToInt() / 10f} EV" }
                ) { updateRecipe(recipe.copy(exposure = it)) }
                AdjustmentSlider("Contrast", recipe.contrast, 0.5f..1.8f) {
                    updateRecipe(recipe.copy(contrast = it))
                }
                AdjustmentSlider("Saturation", recipe.saturation, 0f..2f) {
                    updateRecipe(recipe.copy(saturation = it))
                }
                AdjustmentSlider(
                    "Temperature", recipe.temperature, -1f..1f,
                    valueLabel = { (it * 100f).roundToInt().let { v -> if (v > 0) "+$v" else "$v" } }
                ) { updateRecipe(recipe.copy(temperature = it)) }
                AdjustmentSlider(
                    "Tint", recipe.tint, -1f..1f,
                    valueLabel = { (it * 100f).roundToInt().let { v -> if (v > 0) "+$v" else "$v" } }
                ) { updateRecipe(recipe.copy(tint = it)) }
                TextButton(
                    onClick = {
                        updateRecipe(recipe.copy(
                            brightness = 0f, exposure = 0f, contrast = 1f,
                            saturation = 1f, temperature = 0f, tint = 0f
                        ))
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Reset adjustments") }
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
                    "40 non-destructive colour presets. Strength blends each filter with the original.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (mode == EditorMode.Drawing) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 250.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MarkupTool.entries.size) { index ->
                        val tool = MarkupTool.entries[index]
                        val selected = markupTool == tool
                        Surface(modifier = Modifier.clip(CircleShape).clickable { markupTool = tool }, shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(tool.title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (markupTool == MarkupTool.Text) {
                    OutlinedTextField(value = markupText, onValueChange = { markupText = it }, modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 2, label = { Text("Text to place") }, supportingText = { Text("Tap the image to add a text layer") })
                } else {
                    Text(
                        when (markupTool) {
                            MarkupTool.Pen -> "Draw directly on the image"
                            MarkupTool.Highlighter -> "Transparent highlighter stroke"
                            MarkupTool.Eraser -> "Erase annotation strokes only; the photo remains intact"
                            MarkupTool.Line -> "Drag to place a straight line"
                            MarkupTool.Arrow -> "Drag from the tail toward the arrow tip"
                            MarkupTool.Rectangle -> "Drag to outline a rectangle"
                            MarkupTool.Ellipse -> "Drag to outline an oval"
                            MarkupTool.Text -> "Tap to place text"
                        }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Colour", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EDITOR_MARKUP_COLORS.size) { index ->
                            val swatch = EDITOR_MARKUP_COLORS[index]
                            val selected = markupColor == swatch
                            Surface(modifier = Modifier.size(27.dp).clip(CircleShape).clickable { markupColor = swatch }, shape = CircleShape, color = Color(swatch), border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {}
                        }
                    }
                }
                if (markupTool != MarkupTool.Text) {
                    AdjustmentSlider("Brush size", markupStrokeWidth, 0.002f..0.035f, valueLabel = { (it * 1000f).roundToInt().toString() }) { markupStrokeWidth = it }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${recipe.markups.size} annotation ${if (recipe.markups.size == 1) "layer" else "layers"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { updateRecipe(recipe.copy(markups = recipe.markups.dropLast(1))) }, enabled = recipe.markups.isNotEmpty() && !saving) { Text("Remove last") }
                    TextButton(onClick = { updateRecipe(recipe.copy(markups = emptyList())) }, enabled = recipe.markups.isNotEmpty() && !saving) { Text("Clear all") }
                }
            }
        } else if (mode == EditorMode.Resize) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Resize output", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Keep the edited dimensions or set a custom pixel size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = resizeEnabled, onCheckedChange = { resizeEnabled = it })
                }
                if (resizeEnabled) {
                    val sourceAspect = (preview?.width ?: source.width.coerceAtLeast(1)).toFloat() / (preview?.height ?: source.height.coerceAtLeast(1)).coerceAtLeast(1)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = exportWidthText,
                            onValueChange = { value ->
                                exportWidthText = value.filter { it.isDigit() }.take(5)
                                val parsed = exportWidthText.toIntOrNull()
                                if (lockExportAspect && parsed != null && sourceAspect > 0f) exportHeightText = (parsed / sourceAspect).roundToInt().coerceAtLeast(1).toString()
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Width (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = exportHeightText,
                            onValueChange = { value ->
                                exportHeightText = value.filter { it.isDigit() }.take(5)
                                val parsed = exportHeightText.toIntOrNull()
                                if (lockExportAspect && parsed != null && sourceAspect > 0f) exportWidthText = (parsed * sourceAspect).roundToInt().coerceAtLeast(1).toString()
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Height (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lock aspect ratio", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Switch(checked = lockExportAspect, onCheckedChange = { lockExportAspect = it })
                    }
                    Text("Large dimensions are automatically capped at 40 megapixels to protect device memory.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Output format", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EditorOutputFormat.entries.size) { index ->
                        val format = EditorOutputFormat.entries[index]
                        val selected = exportFormat == format
                        Surface(
                            modifier = Modifier.clip(CircleShape).clickable {
                                exportFormat = format
                                if (format == EditorOutputFormat.PNG) fitTargetSize = false
                            },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(format.title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (exportFormat != EditorOutputFormat.PNG) {
                    AdjustmentSlider("Quality", exportQuality, 10f..100f, valueLabel = { "${it.roundToInt()}%" }) { exportQuality = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Compress to target size", style = MaterialTheme.typography.labelMedium)
                            Text("Finds the highest quality that fits, where possible", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = fitTargetSize, onCheckedChange = { fitTargetSize = it })
                    }
                    if (fitTargetSize) {
                        OutlinedTextField(
                            value = targetSizeMbText,
                            onValueChange = { targetSizeMbText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(7) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Target file size (MB)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            supportingText = { Text("Allowed range: 0.05–200 MB. Complex images may not reach the target at minimum quality.") }
                        )
                    }
                } else {
                    Text("PNG is lossless. Quality and target-size compression are not applied; resizing still works.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val estimateSourceWidth = source.width.takeIf { it > 0 } ?: (image?.width ?: 1)
                val estimateSourceHeight = source.height.takeIf { it > 0 } ?: (image?.height ?: 1)
                val naturalWidth = if (preview != null && image != null) (preview!!.width.toFloat() * estimateSourceWidth / image.width.coerceAtLeast(1)).roundToInt() else estimateSourceWidth
                val naturalHeight = if (preview != null && image != null) (preview!!.height.toFloat() * estimateSourceHeight / image.height.coerceAtLeast(1)).roundToInt() else estimateSourceHeight
                val estimateWidth = if (resizeEnabled) exportWidthText.toIntOrNull()?.coerceAtLeast(1) ?: naturalWidth else naturalWidth
                val estimateHeight = if (resizeEnabled) exportHeightText.toIntOrNull()?.coerceAtLeast(1) ?: naturalHeight else naturalHeight
                val estimatedBytes = estimateEditorOutputBytes(source.sizeBytes, estimateSourceWidth, estimateSourceHeight, estimateWidth, estimateHeight, exportFormat, exportQuality.roundToInt())
                Text("Estimated size: ${String.format(Locale.US, "%.2f MB", estimatedBytes / (1024f * 1024f))} (rough estimate)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Source: ${source.width} × ${source.height} px • ${(source.sizeBytes / (1024f * 1024f)).let { String.format(Locale.US, "%.2f MB", it) }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Export creates a new file in Pictures/Emre. The original image is not overwritten.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (mode == EditorMode.AI) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Person background removal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Runs on this device with the bundled ML Kit Selfie Segmenter. It isolates people; it is not a general object-removal model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EditorAction(
                    if (recipe.removePersonBackground) "Restore background" else "Remove person background",
                    Icons.Default.AutoAwesome
                ) {
                    showOriginalPreview = false
                    updateRecipe(recipe.copy(removePersonBackground = !recipe.removePersonBackground))
                }
                if (recipe.removePersonBackground) {
                    Text(
                        "Transparent preview enabled. Saving creates a PNG copy in Pictures/Emre; the source photo is not changed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Best results: a clearly visible person, good lighting and an image at least 256 × 256 pixels.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
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
                AdjustmentSlider("Soft focus", recipe.softFocus, 0f..1f) {
                    updateRecipe(recipe.copy(softFocus = it))
                }
                AdjustmentSlider("Sharpen", recipe.sharpen, 0f..1f) {
                    updateRecipe(recipe.copy(sharpen = it))
                }
                AdjustmentSlider("Pixelate", recipe.pixelate, 0f..1f) {
                    updateRecipe(recipe.copy(pixelate = it))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            updateRecipe(recipe.copy(
                                vignette = 0f, grain = 0f, fade = 0f,
                                softFocus = 0f, sharpen = 0f, pixelate = 0f
                            ))
                        }
                    ) { Text("Reset effects") }
                }
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
    valueLabel: (Float) -> String = { "${(it * 100f).roundToInt()}%" },
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(86.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(
            valueLabel(value),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(52.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun DrawMarkupOverlay(
    layers: List<EditorMarkup>,
    tool: MarkupTool,
    colorArgb: Int,
    strokeWidthFraction: Float,
    text: String,
    onCommit: (EditorMarkup) -> Unit,
    onTextTap: (MarkupPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLayers by rememberUpdatedState(layers)
    val currentColor by rememberUpdatedState(colorArgb)
    val currentWidth by rememberUpdatedState(strokeWidthFraction)
    val currentOnCommit by rememberUpdatedState(onCommit)
    val currentOnTextTap by rememberUpdatedState(onTextTap)
    var inProgress by remember(tool) { mutableStateOf<EditorMarkup?>(null) }

    Canvas(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .pointerInput(tool) {
                if (tool == MarkupTool.Text) {
                    detectTapGestures { offset ->
                        currentOnTextTap(
                            MarkupPoint(
                                (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
                                (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            )
                        )
                    }
                } else {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val point = MarkupPoint(
                                (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
                                (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            )
                            inProgress = EditorMarkup(tool, listOf(point, point), colorArgb = currentColor, strokeWidthFraction = currentWidth)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val current = inProgress ?: return@detectDragGestures
                            val point = MarkupPoint(
                                (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
                                (change.position.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            )
                            val points = when (tool) {
                                MarkupTool.Pen, MarkupTool.Highlighter, MarkupTool.Eraser -> current.points + point
                                else -> listOf(current.points.first(), point)
                            }
                            inProgress = current.copy(points = points)
                        },
                        onDragEnd = { inProgress?.let(currentOnCommit); inProgress = null },
                        onDragCancel = { inProgress = null }
                    )
                }
            }
    ) {
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            val checkpoint = canvas.saveLayer(0f, 0f, size.width, size.height, null)
            drawEditorMarkupLayers(canvas, size.width, size.height, layers + listOfNotNull(inProgress))
            canvas.restoreToCount(checkpoint)
        }
    }
}

private fun drawEditorMarkupLayers(
    canvas: android.graphics.Canvas,
    width: Float,
    height: Float,
    layers: List<EditorMarkup>
) {
    if (layers.isEmpty() || width <= 0f || height <= 0f) return
    layers.forEach { layer ->
        if (layer.points.isEmpty()) return@forEach
        val first = layer.points.first()
        val last = layer.points.last()
        val startX = first.x.coerceIn(0f, 1f) * width
        val startY = first.y.coerceIn(0f, 1f) * height
        val endX = last.x.coerceIn(0f, 1f) * width
        val endY = last.y.coerceIn(0f, 1f) * height
        val baseStrokeWidth = (layer.strokeWidthFraction.coerceIn(0.002f, 0.08f) * minOf(width, height)).coerceAtLeast(1f)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.colorArgb
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = baseStrokeWidth
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isDither = true
        }
        when (layer.tool) {
            MarkupTool.Pen, MarkupTool.Highlighter, MarkupTool.Eraser -> {
                if (layer.tool == MarkupTool.Highlighter) {
                    paint.alpha = (android.graphics.Color.alpha(layer.colorArgb) * 0.34f).roundToInt()
                    paint.strokeWidth = baseStrokeWidth * 3.2f
                } else if (layer.tool == MarkupTool.Eraser) {
                    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                    paint.strokeWidth = baseStrokeWidth * 3.8f
                }
                if (layer.points.size == 1 || (layer.points.size == 2 && first == last)) {
                    canvas.drawCircle(startX, startY, paint.strokeWidth / 2f, paint)
                } else {
                    val path = android.graphics.Path().apply {
                        moveTo(startX, startY)
                        layer.points.drop(1).forEach { point ->
                            lineTo(point.x.coerceIn(0f, 1f) * width, point.y.coerceIn(0f, 1f) * height)
                        }
                    }
                    canvas.drawPath(path, paint)
                }
            }
            MarkupTool.Line -> canvas.drawLine(startX, startY, endX, endY, paint)
            MarkupTool.Arrow -> {
                canvas.drawLine(startX, startY, endX, endY, paint)
                val angle = kotlin.math.atan2(endY - startY, endX - startX)
                val head = (baseStrokeWidth * 4.5f).coerceAtLeast(12f)
                val spread = Math.PI.toFloat() / 6f
                val arrowPath = android.graphics.Path().apply {
                    moveTo(endX, endY)
                    lineTo(endX - head * kotlin.math.cos(angle - spread), endY - head * kotlin.math.sin(angle - spread))
                    moveTo(endX, endY)
                    lineTo(endX - head * kotlin.math.cos(angle + spread), endY - head * kotlin.math.sin(angle + spread))
                }
                canvas.drawPath(arrowPath, paint)
            }
            MarkupTool.Rectangle -> canvas.drawRect(minOf(startX, endX), minOf(startY, endY), maxOf(startX, endX), maxOf(startY, endY), paint)
            MarkupTool.Ellipse -> canvas.drawOval(minOf(startX, endX), minOf(startY, endY), maxOf(startX, endX), maxOf(startY, endY), paint)
            MarkupTool.Text -> {
                paint.style = android.graphics.Paint.Style.FILL
                paint.xfermode = null
                paint.textSize = (layer.strokeWidthFraction.coerceIn(0.025f, 0.14f) * height).coerceAtLeast(12f)
                paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                layer.text.split(10.toChar()).forEachIndexed { index, line ->
                    canvas.drawText(line, startX, startY + index * paint.textSize * 1.18f, paint)
                }
            }
        }
        paint.xfermode = null
    }
}

private fun renderEditorMarkupLayers(source: Bitmap, layers: List<EditorMarkup>): Bitmap {
    if (layers.isEmpty()) return source
    val output = source.copy(Bitmap.Config.ARGB_8888, true)
        ?: throw IOException("Could not allocate a canvas for annotations")
    try {
        val canvas = android.graphics.Canvas(output)
        val checkpoint = canvas.saveLayer(0f, 0f, output.width.toFloat(), output.height.toFloat(), null)
        drawEditorMarkupLayers(canvas, output.width.toFloat(), output.height.toFloat(), layers)
        canvas.restoreToCount(checkpoint)
        return output
    } catch (failure: Throwable) {
        if (!output.isRecycled) output.recycle()
        throw failure
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

    val hasAdjustments = recipe.brightness != 0f ||
        recipe.exposure != 0f || recipe.contrast != 1f || recipe.saturation != 1f ||
        recipe.temperature != 0f || recipe.tint != 0f
    val hasFilter = recipe.filter != EditorFilter.Original && recipe.filterStrength > 0f
    var adjusted = cropped
    if (hasAdjustments || hasFilter) {
        val brightnessOffset = recipe.brightness * 255f
        val exposureScale = 2f.pow(recipe.exposure.coerceIn(-2f, 2f))
        val temperatureOffset = recipe.temperature.coerceIn(-1f, 1f) * 28f
        val tintOffset = recipe.tint.coerceIn(-1f, 1f) * 18f
        val contrast = recipe.contrast
        val saturation = recipe.saturation
        val lumR = 0.213f * (1f - saturation)
        val lumG = 0.715f * (1f - saturation)
        val lumB = 0.072f * (1f - saturation)
        val adjustmentMatrix = android.graphics.ColorMatrix(floatArrayOf(
            exposureScale * contrast * (lumR + saturation), exposureScale * contrast * lumG, exposureScale * contrast * lumB, 0f, brightnessOffset + temperatureOffset - tintOffset * 0.35f,
            exposureScale * contrast * lumR, exposureScale * contrast * (lumG + saturation), exposureScale * contrast * lumB, 0f, brightnessOffset + tintOffset,
            exposureScale * contrast * lumR, exposureScale * contrast * lumG, exposureScale * contrast * (lumB + saturation), 0f, brightnessOffset - temperatureOffset - tintOffset * 0.35f,
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

    val hasSpatialEffects = recipe.vignette > 0f || recipe.grain > 0f || recipe.fade > 0f ||
        recipe.softFocus > 0f || recipe.sharpen > 0f || recipe.pixelate > 0f
    if (!hasSpatialEffects) return adjusted
    val effected = applyEditorEffects(
        adjusted, recipe.vignette, recipe.grain, recipe.fade,
        recipe.softFocus, recipe.sharpen, recipe.pixelate
    )
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
    EditorFilter.Natural -> buildToneMatrix(1.02f, 1.01f, 2f, 1f, 0f)
    EditorFilter.Cream -> buildToneMatrix(0.86f, 0.93f, 16f, 12f, 7f)
    EditorFilter.Peach -> buildToneMatrix(1.03f, 0.98f, 18f, 3f, 1f)
    EditorFilter.Honey -> buildToneMatrix(1.08f, 1.04f, 22f, 10f, -8f)
    EditorFilter.KodakGold -> buildToneMatrix(1.12f, 1.08f, 18f, 7f, -12f)
    EditorFilter.Portra400 -> buildToneMatrix(0.94f, 0.98f, 10f, 5f, 2f)
    EditorFilter.Fuji -> buildToneMatrix(1.1f, 1.05f, -5f, 4f, 11f)
    EditorFilter.BlueHour -> buildToneMatrix(0.98f, 1.08f, -10f, 1f, 24f)
    EditorFilter.TealCinema -> floatArrayOf(
        0.98f, 0.08f, -0.02f, 0f, 5f,
        0.02f, 1.02f, 0.03f, 0f, 2f,
        -0.04f, 0.14f, 1.08f, 0f, 14f,
        0f, 0f, 0f, 1f, 0f
    )
    EditorFilter.Dusty -> buildToneMatrix(0.78f, 0.88f, 18f, 14f, 11f)
    EditorFilter.Polaroid -> buildToneMatrix(0.9f, 0.94f, 21f, 12f, 3f)
    EditorFilter.Amber -> buildToneMatrix(0.88f, 1.02f, 24f, 10f, -15f)
    EditorFilter.FadedPrint -> buildToneMatrix(0.76f, 0.82f, 31f, 29f, 24f)
    EditorFilter.HighKey -> buildToneMatrix(0f, 0.84f, 28f, 28f, 28f)
    EditorFilter.Dramatic -> buildToneMatrix(0f, 1.55f, -20f, -20f, -20f)
    EditorFilter.Infrared -> floatArrayOf(
        0.72f, 0.24f, 0.04f, 0f, 8f,
        0.72f, 0.24f, 0.04f, 0f, 8f,
        0.72f, 0.24f, 0.04f, 0f, 8f,
        0f, 0f, 0f, 1f, 0f
    )
    EditorFilter.Arctic -> buildToneMatrix(1.02f, 1.08f, -8f, 3f, 19f)
    EditorFilter.RoseGold -> buildToneMatrix(1.04f, 1.01f, 20f, 5f, 10f)
    EditorFilter.Copper -> buildToneMatrix(0.94f, 1.12f, 22f, 5f, -14f)
    EditorFilter.Lavender -> buildToneMatrix(0.98f, 1.01f, 9f, 0f, 18f)
    EditorFilter.Ocean -> buildToneMatrix(1.12f, 1.05f, -10f, 5f, 22f)
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
    fade: Float,
    softFocus: Float,
    sharpen: Float,
    pixelate: Float
): Bitmap {
    val vignetteAmount = vignette.coerceIn(0f, 1f) * 0.62f
    val grainAmount = grain.coerceIn(0f, 1f) * 26f
    val fadeAmount = fade.coerceIn(0f, 1f)
    val softAmount = softFocus.coerceIn(0f, 1f) * 0.72f
    val sharpenAmount = sharpen.coerceIn(0f, 1f) * 0.72f
    val pixelAmount = pixelate.coerceIn(0f, 1f) * 0.92f

    if (
        vignetteAmount <= 0f && grainAmount <= 0f && fadeAmount <= 0f &&
        softAmount <= 0f && sharpenAmount <= 0f && pixelAmount <= 0f
    ) return source

    val width = source.width
    val height = source.height
    var blurBitmap: Bitmap? = null
    var pixelBitmap: Bitmap? = null
    var output: Bitmap? = null
    var completed = false

    try {
        if (softAmount > 0f && width > 1 && height > 1) {
            val scale = (1f - softFocus.coerceIn(0f, 1f) * 0.84f).coerceIn(0.16f, 1f)
            val blurWidth = (width * scale).roundToInt().coerceIn(1, width)
            val blurHeight = (height * scale).roundToInt().coerceIn(1, height)
            if (blurWidth < width || blurHeight < height) {
                blurBitmap = Bitmap.createScaledBitmap(source, blurWidth, blurHeight, true)
            }
        }

        if (pixelAmount > 0f && width > 1 && height > 1) {
            val blockSize = (2f + pixelate.coerceIn(0f, 1f) * 46f).roundToInt().coerceAtLeast(2)
            val pixelWidth = ((width + blockSize - 1) / blockSize).coerceIn(1, width)
            val pixelHeight = ((height + blockSize - 1) / blockSize).coerceIn(1, height)
            if (pixelWidth < width || pixelHeight < height) {
                pixelBitmap = Bitmap.createScaledBitmap(source, pixelWidth, pixelHeight, false)
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output = result

        val sourceRow = IntArray(width)
        val aboveRow = IntArray(width)
        val belowRow = IntArray(width)
        val outputRow = IntArray(width)
        val centerX = (width - 1) / 2f
        val centerY = (height - 1) / 2f
        val maxRadiusSquared = (centerX * centerX + centerY * centerY).coerceAtLeast(1f)

        val blur = blurBitmap
        val blurWidth = blur?.width ?: 0
        val blurHeight = blur?.height ?: 0
        val blurTop = if (blur != null) IntArray(blurWidth) else null
        val blurBottom = if (blur != null) IntArray(blurWidth) else null

        val pixel = pixelBitmap
        val pixelWidth = pixel?.width ?: 0
        val pixelHeight = pixel?.height ?: 0
        val pixelRow = if (pixel != null) IntArray(pixelWidth) else null

        for (y in 0 until height) {
            source.getPixels(sourceRow, 0, width, 0, y, width, 1)
            source.getPixels(aboveRow, 0, width, 0, (y - 1).coerceAtLeast(0), width, 1)
            source.getPixels(belowRow, 0, width, 0, (y + 1).coerceAtMost(height - 1), width, 1)

            var blurY1 = 0
            var blurFractionY = 0f
            if (blur != null && blurTop != null && blurBottom != null) {
                val mappedY = if (height <= 1) 0f else y * (blurHeight - 1).toFloat() / (height - 1)
                val blurY0 = mappedY.toInt().coerceIn(0, blurHeight - 1)
                blurY1 = (blurY0 + 1).coerceAtMost(blurHeight - 1)
                blurFractionY = mappedY - blurY0
                blur.getPixels(blurTop, 0, blurWidth, 0, blurY0, blurWidth, 1)
                blur.getPixels(blurBottom, 0, blurWidth, 0, blurY1, blurWidth, 1)
            }

            if (pixel != null && pixelRow != null) {
                val mappedY = (y.toLong() * pixelHeight / height).toInt().coerceIn(0, pixelHeight - 1)
                pixel.getPixels(pixelRow, 0, pixelWidth, 0, mappedY, pixelWidth, 1)
            }

            val dy = y - centerY
            for (x in 0 until width) {
                val sourcePixel = sourceRow[x]
                val alpha = android.graphics.Color.alpha(sourcePixel)
                var red = android.graphics.Color.red(sourcePixel).toFloat()
                var green = android.graphics.Color.green(sourcePixel).toFloat()
                var blue = android.graphics.Color.blue(sourcePixel).toFloat()

                if (sharpenAmount > 0f) {
                    val leftPixel = sourceRow[(x - 1).coerceAtLeast(0)]
                    val rightPixel = sourceRow[(x + 1).coerceAtMost(width - 1)]
                    val upPixel = aboveRow[x]
                    val downPixel = belowRow[x]
                    val averageRed = (
                        android.graphics.Color.red(leftPixel) +
                        android.graphics.Color.red(rightPixel) +
                        android.graphics.Color.red(upPixel) +
                        android.graphics.Color.red(downPixel)
                    ) * 0.25f
                    val averageGreen = (
                        android.graphics.Color.green(leftPixel) +
                        android.graphics.Color.green(rightPixel) +
                        android.graphics.Color.green(upPixel) +
                        android.graphics.Color.green(downPixel)
                    ) * 0.25f
                    val averageBlue = (
                        android.graphics.Color.blue(leftPixel) +
                        android.graphics.Color.blue(rightPixel) +
                        android.graphics.Color.blue(upPixel) +
                        android.graphics.Color.blue(downPixel)
                    ) * 0.25f
                    red += (red - averageRed) * sharpenAmount
                    green += (green - averageGreen) * sharpenAmount
                    blue += (blue - averageBlue) * sharpenAmount
                }

                if (blur != null && blurTop != null && blurBottom != null) {
                    val mappedX = if (width <= 1) 0f else x * (blurWidth - 1).toFloat() / (width - 1)
                    val blurX0 = mappedX.toInt().coerceIn(0, blurWidth - 1)
                    val blurX1 = (blurX0 + 1).coerceAtMost(blurWidth - 1)
                    val fractionX = mappedX - blurX0
                    val blurredRed = bilinearChannel(blurTop, blurBottom, blurX0, blurX1, fractionX, blurFractionY, 0)
                    val blurredGreen = bilinearChannel(blurTop, blurBottom, blurX0, blurX1, fractionX, blurFractionY, 1)
                    val blurredBlue = bilinearChannel(blurTop, blurBottom, blurX0, blurX1, fractionX, blurFractionY, 2)
                    red += (blurredRed - red) * softAmount
                    green += (blurredGreen - green) * softAmount
                    blue += (blurredBlue - blue) * softAmount
                }

                if (pixel != null && pixelRow != null) {
                    val pixelX = (x.toLong() * pixelWidth / width).toInt().coerceIn(0, pixelWidth - 1)
                    val pixelPixel = pixelRow[pixelX]
                    red += (android.graphics.Color.red(pixelPixel) - red) * pixelAmount
                    green += (android.graphics.Color.green(pixelPixel) - green) * pixelAmount
                    blue += (android.graphics.Color.blue(pixelPixel) - blue) * pixelAmount
                }

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

                outputRow[x] = android.graphics.Color.argb(
                    alpha,
                    red.toInt().coerceIn(0, 255),
                    green.toInt().coerceIn(0, 255),
                    blue.toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outputRow, 0, width, 0, y, width, 1)
        }

        completed = true
        return result
    } finally {
        blurBitmap?.takeIf { it !== source && !it.isRecycled }?.recycle()
        pixelBitmap?.takeIf { it !== source && it !== blurBitmap && !it.isRecycled }?.recycle()
        if (!completed) output?.takeIf { !it.isRecycled }?.recycle()
    }
}

private fun bilinearChannel(
    top: IntArray,
    bottom: IntArray,
    x0: Int,
    x1: Int,
    fractionX: Float,
    fractionY: Float,
    channel: Int
): Float {
    fun component(pixel: Int): Int = when (channel) {
        0 -> android.graphics.Color.red(pixel)
        1 -> android.graphics.Color.green(pixel)
        else -> android.graphics.Color.blue(pixel)
    }
    val topValue = component(top[x0]) + (component(top[x1]) - component(top[x0])) * fractionX
    val bottomValue = component(bottom[x0]) + (component(bottom[x1]) - component(bottom[x0])) * fractionX
    return topValue + (bottomValue - topValue) * fractionY
}

private suspend fun decodeEditorBitmap(
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

private suspend fun removePersonBackground(source: Bitmap): Bitmap {
    require(source.width >= 256 && source.height >= 256) {
        "Person segmentation requires an image at least 256 × 256 pixels"
    }
    val options = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        .enableRawSizeMask()
        .build()
    val segmenter = Segmentation.getClient(options)
    val closed = AtomicBoolean(false)
    fun closeSegmenter() {
        if (closed.compareAndSet(false, true)) segmenter.close()
    }

    val mask = try {
        suspendCancellableCoroutine<SegmentationMask> { continuation ->
            try {
                segmenter.process(InputImage.fromBitmap(source, 0))
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                    .addOnFailureListener { failure ->
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
            } catch (failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
    } catch (failure: Throwable) {
        closeSegmenter()
        throw failure
    }

    try {
        val maskWidth = mask.width
        val maskHeight = mask.height
        if (maskWidth <= 0 || maskHeight <= 0) {
            throw IOException("The segmentation model returned an empty mask")
        }
        val confidence = mask.buffer.duplicate()
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        if (confidence.remaining() < maskWidth * maskHeight) {
            throw IOException("The segmentation mask is incomplete")
        }

        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var completed = false
        try {
            val sourceRow = IntArray(width)
            val outputRow = IntArray(width)
            val xScale = if (width <= 1) 0f else (maskWidth - 1).toFloat() / (width - 1)
            val yScale = if (height <= 1) 0f else (maskHeight - 1).toFloat() / (height - 1)

            for (y in 0 until height) {
                source.getPixels(sourceRow, 0, width, 0, y, width, 1)
                val mappedY = y * yScale
                val y0 = mappedY.toInt().coerceIn(0, maskHeight - 1)
                val y1 = (y0 + 1).coerceAtMost(maskHeight - 1)
                val fy = mappedY - y0
                for (x in 0 until width) {
                    val mappedX = x * xScale
                    val x0 = mappedX.toInt().coerceIn(0, maskWidth - 1)
                    val x1 = (x0 + 1).coerceAtMost(maskWidth - 1)
                    val fx = mappedX - x0
                    val m00 = confidence.get(y0 * maskWidth + x0)
                    val m10 = confidence.get(y0 * maskWidth + x1)
                    val m01 = confidence.get(y1 * maskWidth + x0)
                    val m11 = confidence.get(y1 * maskWidth + x1)
                    val top = m00 + (m10 - m00) * fx
                    val bottom = m01 + (m11 - m01) * fx
                    val alphaMask = (top + (bottom - top) * fy).coerceIn(0f, 1f)
                    val pixel = sourceRow[x]
                    val alpha = (android.graphics.Color.alpha(pixel) * alphaMask).roundToInt().coerceIn(0, 255)
                    outputRow[x] = android.graphics.Color.argb(
                        alpha,
                        android.graphics.Color.red(pixel),
                        android.graphics.Color.green(pixel),
                        android.graphics.Color.blue(pixel)
                    )
                }
                output.setPixels(outputRow, 0, width, 0, y, width, 1)
            }
            completed = true
            return output
        } finally {
            if (!completed && !output.isRecycled) output.recycle()
        }
    } finally {
        closeSegmenter()
    }
}

private suspend fun scanMediaFile(context: Context, file: File, mimeType: String): Uri =
    suspendCancellableCoroutine { continuation ->
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeType)
        ) { _, scannedUri ->
            if (!continuation.isActive) return@scanFile
            if (scannedUri != null) {
                continuation.resume(scannedUri)
            } else {
                continuation.resumeWithException(IOException("Media scanner could not index the exported image"))
            }
        }
    }

private fun estimateEditorOutputBytes(
    sourceBytes: Long,
    sourceWidth: Int,
    sourceHeight: Int,
    outputWidth: Int,
    outputHeight: Int,
    format: EditorOutputFormat,
    quality: Int
): Long {
    if (sourceBytes <= 0L || sourceWidth <= 0 || sourceHeight <= 0) return 0L
    val pixelRatio = (outputWidth.coerceAtLeast(1).toDouble() * outputHeight.coerceAtLeast(1)) /
        (sourceWidth.toDouble() * sourceHeight.toDouble())
    val formatFactor = when (format) {
        EditorOutputFormat.JPEG -> 0.9
        EditorOutputFormat.WEBP -> 0.68
        EditorOutputFormat.PNG -> 1.35
    }
    val qualityFactor = if (format == EditorOutputFormat.PNG) 1.0 else
        (quality.coerceIn(10, 100) / 92.0).pow(1.5)
    return (sourceBytes * pixelRatio * formatFactor * qualityFactor).toLong().coerceAtLeast(1L)
}

internal fun normalizedExportDimensions(
    requestedWidth: Int,
    requestedHeight: Int
): Pair<Int, Int> {
    require(requestedWidth > 0 && requestedHeight > 0) {
        "Output dimensions must be positive"
    }
    val maxDimension = 16_384f
    val maxPixels = 40_000_000.0
    val width = requestedWidth.toDouble()
    val height = requestedHeight.toDouble()
    val pixelScale = minOf(1.0, kotlin.math.sqrt(maxPixels / (width * height)))
    val scale = minOf(pixelScale, maxDimension / width, maxDimension / height)
    return Pair(
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1)
    )
}

private fun resizeEditorBitmap(source: Bitmap, settings: EditorExportSettings): Bitmap {
    if (!settings.resizeEnabled) return source
    val (width, height) = normalizedExportDimensions(settings.width, settings.height)
    if (width == source.width && height == source.height) return source
    return Bitmap.createScaledBitmap(source, width, height, true)
}

private fun flattenBitmapOnWhite(source: Bitmap): Bitmap {
    if (!source.hasAlpha()) return source
    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)
        output.setHasAlpha(false)
        return output
    } catch (failure: Throwable) {
        if (!output.isRecycled) output.recycle()
        throw failure
    }
}

private fun writeCompressedBitmap(
    bitmap: Bitmap,
    format: Bitmap.CompressFormat,
    quality: Int,
    destination: File
) {
    destination.outputStream().buffered().use { stream ->
        if (!bitmap.compress(format, quality.coerceIn(10, 100), stream)) {
            throw IOException("Image encoding failed")
        }
        stream.flush()
    }
}

private data class CompressionOutcome(
    val quality: Int,
    val targetReached: Boolean
)

private fun encodeBitmapWithTargetSize(
    bitmap: Bitmap,
    format: Bitmap.CompressFormat,
    quality: Int,
    targetSizeBytes: Long?,
    destination: File
): CompressionOutcome {
    val requestedQuality = quality.coerceIn(10, 100)
    if (targetSizeBytes == null) {
        writeCompressedBitmap(bitmap, format, requestedQuality, destination)
        return CompressionOutcome(requestedQuality, true)
    }
    var low = 10
    var high = requestedQuality
    var bestQuality = -1
    var lastQuality = -1
    var attempts = 0
    while (low <= high && attempts < 9) {
        val candidate = (low + high) / 2
        writeCompressedBitmap(bitmap, format, candidate, destination)
        lastQuality = candidate
        attempts += 1
        val withinTarget = destination.length() > 0L && destination.length() <= targetSizeBytes
        if (withinTarget) {
            bestQuality = candidate
            low = candidate + 1
        } else {
            high = candidate - 1
        }
    }
    val chosenQuality = if (bestQuality >= 10) bestQuality else 10
    if (chosenQuality != lastQuality || destination.length() == 0L) {
        writeCompressedBitmap(bitmap, format, chosenQuality, destination)
    }
    val targetReached = destination.length() > 0L && destination.length() <= targetSizeBytes
    return CompressionOutcome(chosenQuality, targetReached)
}

private suspend fun exportEditorResult(
    context: Context,
    source: Bitmap,
    recipe: EditorRecipe,
    media: MediaItem,
    settings: EditorExportSettings
): MediaItem {
    val transformed = applyEditorRecipe(source, recipe)
    val processedOutput = try {
        if (recipe.removePersonBackground) removePersonBackground(transformed) else transformed
    } catch (failure: Throwable) {
        if (transformed !== source && !transformed.isRecycled) transformed.recycle()
        throw failure
    }

    var annotatedOutput: Bitmap? = null
    var resizedOutput: Bitmap? = null
    var encodedOutput: Bitmap? = null
    var uri: Uri? = null
    var legacyFile: File? = null
    var tempFile: File? = null
    try {
        annotatedOutput = renderEditorMarkupLayers(processedOutput, recipe.markups)
        resizedOutput = resizeEditorBitmap(annotatedOutput, settings)
        encodedOutput = if (settings.format == EditorOutputFormat.JPEG && resizedOutput.hasAlpha()) {
            flattenBitmapOnWhite(resizedOutput)
        } else {
            resizedOutput
        }

        val outputMimeType = settings.format.mimeType
        val extension = settings.format.extension
        val outputFormat = when (settings.format) {
            EditorOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            EditorOutputFormat.PNG -> Bitmap.CompressFormat.PNG
            EditorOutputFormat.WEBP -> Bitmap.CompressFormat.WEBP
        }
        val outputQuality = if (settings.format == EditorOutputFormat.PNG) 100 else settings.quality
        val targetBytes = settings.targetSizeBytes.takeIf { settings.format != EditorOutputFormat.PNG }
        val name = "EMRE_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.$extension"

        tempFile = File.createTempFile("emre-export-", ".$extension", context.cacheDir)
        encodeBitmapWithTargetSize(
            bitmap = encodedOutput,
            format = outputFormat,
            quality = outputQuality,
            targetSizeBytes = targetBytes,
            destination = tempFile
        )

        if (settings.format == EditorOutputFormat.JPEG && !recipe.removePersonBackground) {
            copyExifMetadata(context, Uri.parse(media.uri), tempFile, encodedOutput.width, encodedOutput.height)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, outputMimeType)
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
            uri = scanMediaFile(context, legacyFile, outputMimeType)
        }

        val outputUri = uri ?: throw IOException("Image export did not produce a URI")
        val width = encodedOutput.width
        val height = encodedOutput.height
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
            mimeType = outputMimeType,
            dateAddedSeconds = now / 1000L,
            dateTakenMillis = media.dateTakenMillis,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            relativePath = "Pictures/Emre/",
            isVideo = false
        )
    } catch (failure: Throwable) {
        uri?.let { outputUri ->
            if (outputUri.scheme == "content") {
                runCatching { context.contentResolver.delete(outputUri, null, null) }
            }
        }
        legacyFile?.let { if (it.exists()) it.delete() }
        throw failure
    } finally {
        tempFile?.let { if (it.exists()) it.delete() }
        encodedOutput?.let {
            if (it !== resizedOutput && it !== annotatedOutput && it !== processedOutput && it !== transformed && it !== source && !it.isRecycled) it.recycle()
        }
        resizedOutput?.let {
            if (it !== annotatedOutput && it !== processedOutput && it !== transformed && it !== source && !it.isRecycled) it.recycle()
        }
        annotatedOutput?.let {
            if (it !== processedOutput && it !== transformed && it !== source && !it.isRecycled) it.recycle()
        }
        if (processedOutput !== transformed && processedOutput !== source && !processedOutput.isRecycled) processedOutput.recycle()
        if (transformed !== source && !transformed.isRecycled) transformed.recycle()
    }
}
