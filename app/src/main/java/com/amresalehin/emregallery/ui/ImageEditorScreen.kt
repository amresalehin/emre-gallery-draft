package com.amresalehin.emregallery.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class EditorMode { Crop, Adjust }
private enum class CropPreset(val title: String, val ratio: Float?) {
    Free("Free", null),
    Square("1:1", 1f),
    FourThree("4:3", 4f / 3f),
    ThreeTwo("3:2", 3f / 2f),
    SixteenNine("16:9", 16f / 9f)
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
    val saturation: Float = 1f
)

@Composable
fun ImageEditorScreen(
    source: MediaItem,
    onBack: () -> Unit,
    onSaved: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceBitmap by remember(source.uri) { mutableStateOf<Bitmap?>(null) }
    var recipe by remember(source.uri) { mutableStateOf(EditorRecipe()) }
    var mode by remember { mutableStateOf(EditorMode.Crop) }
    var loading by remember(source.uri) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCropHandles by remember { mutableStateOf(true) }
    var cropPreset by remember { mutableStateOf(CropPreset.Free) }

    LaunchedEffect(source.uri) {
        loading = true
        error = null
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(Uri.parse(source.uri))
                    .size(CoilSize.ORIGINAL)
                    .allowHardware(false)
                    .build()
                Coil.imageLoader(context).execute(request).drawable?.let { drawable ->
                    val bitmapDrawable = drawable as? android.graphics.drawable.BitmapDrawable
                    bitmapDrawable?.bitmap ?: throw IOException("Unsupported image decoder output")
                } ?: context.contentResolver.openInputStream(Uri.parse(source.uri))?.use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
        sourceBitmap = decoded
        loading = false
        if (decoded == null) error = "Unable to decode this image"
    }

    BackHandler(enabled = true) { onBack() }

    val image = sourceBitmap
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
            TextButton(
                onClick = {
                    recipe = EditorRecipe()
                    cropPreset = CropPreset.Free
                    showCropHandles = true
                },
                enabled = !saving
            ) { Text("Reset") }
            IconButton(
                onClick = {
                    val current = image ?: return@IconButton
                    saving = true
                    error = null
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching { exportEditorResult(context, current, recipe, source) }
                        }
                        saving = false
                        saved.onSuccess(onSaved).onFailure {
                            error = it.message ?: "Could not save the edited image"
                        }
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
            when {
                loading -> CircularProgressIndicator()
                preview != null -> {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Color(0xFF202020)),
                        contentAlignment = Alignment.Center
                    ) {
                        val imageAspect = preview.width.toFloat() / preview.height
                        val boundsAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
                        val displayWidth = if (imageAspect > boundsAspect) maxWidth else maxHeight * imageAspect
                        val displayHeight = if (imageAspect > boundsAspect) maxWidth / imageAspect else maxHeight
                        Box(Modifier.width(displayWidth).height(displayHeight)) {
                            androidx.compose.foundation.Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = "Edited image preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                            if (mode == EditorMode.Crop && showCropHandles) {
                                CropOverlay(
                                    recipe = recipe,
                                    onRecipeChange = { recipe = it },
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
                            if (target == EditorMode.Crop) Icons.Default.Crop else Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (target == EditorMode.Crop) "Crop" else "Adjust",
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
                item { EditorAction("90°", Icons.Default.Rotate90DegreesCcw) { recipe = recipe.copy(rotation = (recipe.rotation + 90) % 360) } }
                item { EditorAction("Flip H", Icons.Default.Flip) { recipe = recipe.copy(flipHorizontal = !recipe.flipHorizontal) } }
                item { EditorAction("Flip V", Icons.Default.Flip) { recipe = recipe.copy(flipVertical = !recipe.flipVertical) } }
                item { EditorAction("Reset crop", Icons.Default.Refresh) { recipe = recipe.copy(cropLeft=0f,cropTop=0f,cropRight=1f,cropBottom=1f); cropPreset = CropPreset.Free } }
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
                            recipe = applyCropPreset(recipe, preset.ratio)
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
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider("Brightness", recipe.brightness, -0.5f..0.5f) { recipe = recipe.copy(brightness = it) }
                AdjustmentSlider("Contrast", recipe.contrast, 0.5f..1.8f) { recipe = recipe.copy(contrast = it) }
                AdjustmentSlider("Saturation", recipe.saturation, 0f..2f) { recipe = recipe.copy(saturation = it) }
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
    Canvas(
        modifier = modifier.pointerInput(recipe) {
            detectDragGestures { change, drag ->
                change.consume()
                val dx = drag.x / size.width
                val dy = drag.y / size.height
                val leftGap = recipe.cropLeft
                val rightGap = 1f - recipe.cropRight
                val topGap = recipe.cropTop
                val bottomGap = 1f - recipe.cropBottom
                val next = when {
                    leftGap <= rightGap && leftGap <= topGap && leftGap <= bottomGap ->
                        recipe.copy(cropLeft = (recipe.cropLeft + dx).coerceIn(0f, recipe.cropRight - 0.1f))
                    rightGap <= topGap && rightGap <= bottomGap ->
                        recipe.copy(cropRight = (recipe.cropRight + dx).coerceIn(recipe.cropLeft + 0.1f, 1f))
                    topGap <= bottomGap ->
                        recipe.copy(cropTop = (recipe.cropTop + dy).coerceIn(0f, recipe.cropBottom - 0.1f))
                    else -> recipe.copy(cropBottom = (recipe.cropBottom + dy).coerceIn(recipe.cropTop + 0.1f, 1f))
                }
                onRecipeChange(next)
            }
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
        drawRect(Color.White, topLeft = Offset(l, t), size = Size(r-l,b-t), style = Stroke(width = 2.dp.toPx()))
        for (i in 1..2) {
            val x = l + (r-l) * i / 3f
            val y = t + (b-t) * i / 3f
            drawLine(Color.White.copy(alpha = 0.55f), Offset(x,t), Offset(x,b), strokeWidth = 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.55f), Offset(l,y), Offset(r,y), strokeWidth = 1.dp.toPx())
        }
        val handle = 10.dp.toPx()
        listOf(Offset(l,t),Offset(r,t),Offset(l,b),Offset(r,b)).forEach {
            drawCircle(Color.White, handle, it)
        }
    }
}

private fun applyCropPreset(recipe: EditorRecipe, ratio: Float?): EditorRecipe {
    if (ratio == null) return recipe.copy(cropLeft = 0f, cropTop = 0f, cropRight = 1f, cropBottom = 1f)
    val currentWidth = recipe.cropRight - recipe.cropLeft
    val currentHeight = recipe.cropBottom - recipe.cropTop
    val currentRatio = currentWidth / currentHeight
    val width: Float
    val height: Float
    if (currentRatio > ratio) {
        height = currentHeight
        width = (height * ratio).coerceAtMost(1f)
    } else {
        width = currentWidth
        height = (width / ratio).coerceAtMost(1f)
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
    val matrix = Matrix().apply {
        postRotate(recipe.rotation.toFloat())
        postScale(
            if (recipe.flipHorizontal) -1f else 1f,
            if (recipe.flipVertical) -1f else 1f
        )
    }
    val rotated = if (recipe.rotation % 360 == 0 && !recipe.flipHorizontal && !recipe.flipVertical) {
        working
    } else {
        // Matrix output bounds expand for quarter turns so the full image is retained.
        val values = FloatArray(9)
        matrix.getValues(values)
        val radians = Math.toRadians(recipe.rotation.toDouble())
        val boundWidth = kotlin.math.ceil(
            kotlin.math.abs(working.width * kotlin.math.cos(radians)) +
                kotlin.math.abs(working.height * kotlin.math.sin(radians))
        ).toInt().coerceAtLeast(1)
        val boundHeight = kotlin.math.ceil(
            kotlin.math.abs(working.width * kotlin.math.sin(radians)) +
                kotlin.math.abs(working.height * kotlin.math.cos(radians))
        ).toInt().coerceAtLeast(1)
        val centered = Matrix(matrix).apply {
            postTranslate((boundWidth - working.width) / 2f, (boundHeight - working.height) / 2f)
        }
        Bitmap.createBitmap(boundWidth, boundHeight, Bitmap.Config.ARGB_8888).also { target ->
            android.graphics.Canvas(target).drawBitmap(working, centered, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG))
        }
    }
    val left = (rotated.width * recipe.cropLeft).roundToInt().coerceIn(0, rotated.width - 1)
    val top = (rotated.height * recipe.cropTop).roundToInt().coerceIn(0, rotated.height - 1)
    val right = (rotated.width * recipe.cropRight).roundToInt().coerceIn(left + 1, rotated.width)
    val bottom = (rotated.height * recipe.cropBottom).roundToInt().coerceIn(top + 1, rotated.height)
    val cropped = if (left == 0 && top == 0 && right == rotated.width && bottom == rotated.height) {
        rotated
    } else Bitmap.createBitmap(rotated, left, top, right-left, bottom-top)

    if (recipe.brightness == 0f && recipe.contrast == 1f && recipe.saturation == 1f) {
        if (cropped !== rotated && rotated !== working && !rotated.isRecycled) rotated.recycle()
        if (working !== source && !working.isRecycled) working.recycle()
        return cropped
    }
    val result = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
    val brightnessOffset = recipe.brightness * 255f
    val contrast = recipe.contrast
    val saturation = recipe.saturation
    val lumR = 0.213f * (1f - saturation)
    val lumG = 0.715f * (1f - saturation)
    val lumB = 0.072f * (1f - saturation)
    val matrix = android.graphics.ColorMatrix(floatArrayOf(
        contrast * (lumR + saturation), contrast * lumG, contrast * lumB, 0f, brightnessOffset,
        contrast * lumR, contrast * (lumG + saturation), contrast * lumB, 0f, brightnessOffset,
        contrast * lumR, contrast * lumG, contrast * (lumB + saturation), 0f, brightnessOffset,
        0f, 0f, 0f, 1f, 0f
    ))
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
    }
    android.graphics.Canvas(result).drawBitmap(cropped,0f,0f,paint)
    if (cropped !== rotated && !cropped.isRecycled) cropped.recycle()
    if (rotated !== working && rotated !== cropped && !rotated.isRecycled) rotated.recycle()
    if (working !== source && !working.isRecycled) working.recycle()
    return result
}

private fun exportEditorResult(
    context: Context,
    source: Bitmap,
    recipe: EditorRecipe,
    media: MediaItem
): MediaItem {
    val output = applyEditorRecipe(source, recipe)
    val name = "EMRE_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
    var uri: Uri? = null
    var legacyFile: File? = null
    try {
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
            context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                if (!output.compress(Bitmap.CompressFormat.JPEG, 95, stream)) throw IOException("Image encoding failed")
                stream.flush()
            } ?: throw IOException("Could not open output stream")
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
        } else {
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val directory = File(pictures, "Emre")
            if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create Pictures/Emre")
            legacyFile = File(directory, name)
            legacyFile!!.outputStream().use { stream ->
                if (!output.compress(Bitmap.CompressFormat.JPEG, 95, stream)) throw IOException("Image encoding failed")
                stream.flush()
            }
            val scanned = android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(legacyFile!!.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
            uri = Uri.fromFile(legacyFile)
        }
        val outputUri = uri ?: throw IOException("Image export did not produce a URI")
        val width = output.width
        val height = output.height
        val sizeBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { context.contentResolver.openFileDescriptor(outputUri, "r")?.use { it.statSize } ?: 0L }.getOrDefault(0L)
        } else {
            legacyFile?.length() ?: 0L
        }
        return MediaItem(
            id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.content.ContentUris.parseId(outputUri) else legacyFile?.lastModified() ?: System.currentTimeMillis(),
            uri = outputUri.toString(),
            name = name,
            mimeType = "image/jpeg",
            dateAddedSeconds = System.currentTimeMillis() / 1000L,
            dateTakenMillis = System.currentTimeMillis(),
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            relativePath = "Pictures/Emre/",
            isVideo = false
        )
    } catch (t: Throwable) {
        uri?.let { outputUri ->
            if (outputUri.scheme == "content") runCatching { context.contentResolver.delete(outputUri, null, null) }
        }
        legacyFile?.let { if (it.exists()) it.delete() }
        throw t
    } finally {
        if (output !== source && !output.isRecycled) output.recycle()
    }
}
