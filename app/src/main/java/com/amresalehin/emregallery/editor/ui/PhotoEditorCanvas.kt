package com.amresalehin.emregallery.editor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.editor.BrushType
import com.amresalehin.emregallery.editor.DrawingStroke
import com.amresalehin.emregallery.editor.EditorTool
import com.amresalehin.emregallery.editor.RetouchStroke
import com.amresalehin.emregallery.editor.RetouchType
import kotlin.math.max
import kotlin.math.min

@Composable
fun PhotoEditorCanvas(
    bitmap: Bitmap?,
    activeTool: EditorTool,
    brushType: BrushType,
    brushColor: Color,
    brushWidth: Float,
    activeRetouchType: RetouchType,
    cropAspectRatio: Float?,
    onDrawingStrokeFinished: (DrawingStroke) -> Unit,
    onRetouchStrokeFinished: (RetouchStroke) -> Unit,
    onCropRectPreview: (android.graphics.RectF?) -> Unit = {},
    onCropRectChanged: (android.graphics.RectF?) -> Unit,
    modifier: Modifier = Modifier
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var cropDragHandle by remember { mutableStateOf("NONE") }

    // Live drawing points for active in-flight gesture
    val livePoints = remember { mutableStateListOf<Offset>() }

    // Live Crop box bounds in normalized [0..1]
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(1f) }
    var cropBottom by remember { mutableFloatStateOf(1f) }

    // Adjust crop aspect ratio if preset changed
    LaunchedEffect(cropAspectRatio, bitmap) {
        if (cropAspectRatio != null && bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            if (cropAspectRatio > imgRatio) {
                // Wider than image
                val h = imgRatio / cropAspectRatio
                cropLeft = 0f
                cropRight = 1f
                cropTop = ((1f - h) / 2f).coerceIn(0f, 0.45f)
                cropBottom = (1f - cropTop).coerceIn(0.55f, 1f)
            } else {
                // Taller than image
                val w = cropAspectRatio / imgRatio
                cropTop = 0f
                cropBottom = 1f
                cropLeft = ((1f - w) / 2f).coerceIn(0f, 0.45f)
                cropRight = (1f - cropLeft).coerceIn(0.55f, 1f)
            }
            onCropRectChanged(android.graphics.RectF(cropLeft, cropTop, cropRight, cropBottom))
        }
    }

    val isDrawingMode = activeTool == EditorTool.BRUSHES || activeTool == EditorTool.DRAW || activeTool == EditorTool.DISPERSION
    val isRetouchMode = activeTool == EditorTool.RETOUCH || activeTool == EditorTool.REMOVE || activeTool == EditorTool.CLONE
    val isCropMode = activeTool == EditorTool.CROP || activeTool == EditorTool.FREE_CROP || (cropAspectRatio != null)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(isDrawingMode, isRetouchMode, isCropMode) {
                if (!isDrawingMode && !isRetouchMode && !isCropMode) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            val target = if (zoomScale > 1.05f) 1f else 2.25f
                            if (target == 1f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            } else {
                                val factor = target / zoomScale
                                val center = Offset(size.width / 2f, size.height / 2f)
                                panOffset = panOffset - (tap - center) * (factor - 1f)
                                zoomScale = target
                            }
                        }
                    )
                }
            }
            .then(
                if (isDrawingMode || isRetouchMode) {
                    Modifier.pointerInput(isDrawingMode, isRetouchMode, brushType, brushColor, brushWidth, activeRetouchType, bitmap) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                livePoints.clear()
                                livePoints.add(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                livePoints.add(change.position)
                            },
                            onDragEnd = {
                                if (livePoints.isNotEmpty()) {
                                    val pts = livePoints.toList()
                                    if (isDrawingMode) {
                                        onDrawingStrokeFinished(
                                            DrawingStroke(
                                                id = System.currentTimeMillis().toString(),
                                                points = pts,
                                                color = brushColor,
                                                width = brushWidth,
                                                brushType = brushType
                                            )
                                        )
                                    } else if (isRetouchMode) {
                                        onRetouchStrokeFinished(
                                            RetouchStroke(
                                                type = activeRetouchType,
                                                points = pts,
                                                radius = brushWidth * 1.5f
                                            )
                                        )
                                    }
                                }
                                livePoints.clear()
                            },
                            onDragCancel = {
                                livePoints.clear()
                            }
                        )
                    }
                } else if (isCropMode) {
                    Modifier.pointerInput(bitmap, cropAspectRatio, zoomScale, panOffset) {
                        val image = bitmap ?: return@pointerInput
                        val fitScale = min(size.width / image.width.toFloat(), size.height / image.height.toFloat()) * 0.94f * zoomScale
                        val drawW = image.width * fitScale
                        val drawH = image.height * fitScale
                        val imageLeft = (size.width - drawW) / 2f + panOffset.x
                        val imageTop = (size.height - drawH) / 2f + panOffset.y
                        val hit = 28.dp.toPx()
                        val minEdge = 0.08f

                        detectDragGestures(
                            onDragStart = { down ->
                                val left = imageLeft + cropLeft * drawW
                                val top = imageTop + cropTop * drawH
                                val right = imageLeft + cropRight * drawW
                                val bottom = imageTop + cropBottom * drawH
                                val nearLeft = kotlin.math.abs(down.x - left) <= hit
                                val nearRight = kotlin.math.abs(down.x - right) <= hit
                                val nearTop = kotlin.math.abs(down.y - top) <= hit
                                val nearBottom = kotlin.math.abs(down.y - bottom) <= hit
                                cropDragHandle = when {
                                    nearLeft && nearTop -> "TL"
                                    nearRight && nearTop -> "TR"
                                    nearLeft && nearBottom -> "BL"
                                    nearRight && nearBottom -> "BR"
                                    nearTop && down.x in left..right -> "T"
                                    nearBottom && down.x in left..right -> "B"
                                    nearLeft && down.y in top..bottom -> "L"
                                    nearRight && down.y in top..bottom -> "R"
                                    down.x in left..right && down.y in top..bottom -> "MOVE"
                                    else -> "NONE"
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (cropDragHandle != "NONE") {
                                    change.consume()
                                    val dx = dragAmount.x / max(drawW, 1f)
                                    val dy = dragAmount.y / max(drawH, 1f)
                                    when (cropDragHandle) {
                                        "TL" -> {
                                            cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minEdge)
                                            cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minEdge)
                                        }
                                        "TR" -> {
                                            cropRight = (cropRight + dx).coerceIn(cropLeft + minEdge, 1f)
                                            cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minEdge)
                                        }
                                        "BL" -> {
                                            cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minEdge)
                                            cropBottom = (cropBottom + dy).coerceIn(cropTop + minEdge, 1f)
                                        }
                                        "BR" -> {
                                            cropRight = (cropRight + dx).coerceIn(cropLeft + minEdge, 1f)
                                            cropBottom = (cropBottom + dy).coerceIn(cropTop + minEdge, 1f)
                                        }
                                        "T" -> cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minEdge)
                                        "B" -> cropBottom = (cropBottom + dy).coerceIn(cropTop + minEdge, 1f)
                                        "L" -> cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minEdge)
                                        "R" -> cropRight = (cropRight + dx).coerceIn(cropLeft + minEdge, 1f)
                                        "MOVE" -> {
                                            val width = cropRight - cropLeft
                                            val height = cropBottom - cropTop
                                            cropLeft = (cropLeft + dx).coerceIn(0f, 1f - width)
                                            cropTop = (cropTop + dy).coerceIn(0f, 1f - height)
                                            cropRight = cropLeft + width
                                            cropBottom = cropTop + height
                                        }
                                    }
                                    onCropRectPreview(android.graphics.RectF(cropLeft, cropTop, cropRight, cropBottom))
                                }
                            },
                            onDragEnd = {
                                if (cropDragHandle != "NONE") {
                                    onCropRectChanged(android.graphics.RectF(cropLeft, cropTop, cropRight, cropBottom))
                                }
                                cropDragHandle = "NONE"
                            },
                            onDragCancel = { cropDragHandle = "NONE" }
                        )
                    }
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(1f, 4f)
                            if (zoomScale > 1f) {
                                panOffset += pan
                            } else {
                                panOffset = Offset.Zero
                            }
                        }
                    }
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (bitmap == null || bitmap.isRecycled) return@Canvas

            val cWidth = size.width
            val cHeight = size.height

            // Calculate destination rect to aspect-fit bitmap inside canvas
            val bWidth = bitmap.width.toFloat()
            val bHeight = bitmap.height.toFloat()
            val scale = min(cWidth / bWidth, cHeight / bHeight) * 0.94f * zoomScale

            val drawW = bWidth * scale
            val drawH = bHeight * scale
            val left = (cWidth - drawW) / 2f + panOffset.x
            val top = (cHeight - drawH) / 2f + panOffset.y

            // Draw image
            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize(drawW.toInt(), drawH.toInt())
            )

            // Draw in-progress live drawing path
            if (livePoints.size > 1 && isDrawingMode) {
                val path = Path()
                livePoints.forEachIndexed { idx, pt ->
                    if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                drawPath(
                    path = path,
                    color = brushColor,
                    style = Stroke(
                        width = brushWidth * density,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw in-progress live retouch points
            if (livePoints.isNotEmpty() && isRetouchMode) {
                val indicatorColor = Color.White.copy(alpha = 0.45f)
                for (pt in livePoints) {
                    drawCircle(
                        color = indicatorColor,
                        radius = brushWidth * 1.5f * density,
                        center = pt
                    )
                }
            }

            // Draw Crop Overlay Grid & Handles
            if (isCropMode) {
                val cropPixelL = left + cropLeft * drawW
                val cropPixelT = top + cropTop * drawH
                val cropPixelR = left + cropRight * drawW
                val cropPixelB = top + cropBottom * drawH
                val cropW = cropPixelR - cropPixelL
                val cropH = cropPixelB - cropPixelT

                // Darken outside area
                val scrimColor = Color.Black.copy(alpha = 0.55f)
                drawRect(scrimColor, Offset(0f, 0f), Size(cWidth, cropPixelT))
                drawRect(scrimColor, Offset(0f, cropPixelB), Size(cWidth, cHeight - cropPixelB))
                drawRect(scrimColor, Offset(0f, cropPixelT), Size(cropPixelL, cropH))
                drawRect(scrimColor, Offset(cropPixelR, cropPixelT), Size(cWidth - cropPixelR, cropH))

                // Crop box border
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cropPixelL, cropPixelT),
                    size = Size(cropW, cropH),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Rule-of-thirds grid lines
                val oneThirdW = cropW / 3f
                val oneThirdH = cropH / 3f
                val gridColor = Color.White.copy(alpha = 0.45f)
                val gridStroke = Stroke(width = 1.dp.toPx())

                drawLine(gridColor, Offset(cropPixelL + oneThirdW, cropPixelT), Offset(cropPixelL + oneThirdW, cropPixelB), gridStroke.width)
                drawLine(gridColor, Offset(cropPixelL + oneThirdW * 2, cropPixelT), Offset(cropPixelL + oneThirdW * 2, cropPixelB), gridStroke.width)
                drawLine(gridColor, Offset(cropPixelL, cropPixelT + oneThirdH), Offset(cropPixelR, cropPixelT + oneThirdH), gridStroke.width)
                drawLine(gridColor, Offset(cropPixelL, cropPixelT + oneThirdH * 2), Offset(cropPixelR, cropPixelT + oneThirdH * 2), gridStroke.width)

                // Corner bracket handles
                val handleLen = 22.dp.toPx()
                val handleStroke = 3.5.dp.toPx()
                val handleColor = Color.White

                // Top-Left
                drawLine(handleColor, Offset(cropPixelL, cropPixelT), Offset(cropPixelL + handleLen, cropPixelT), handleStroke)
                drawLine(handleColor, Offset(cropPixelL, cropPixelT), Offset(cropPixelL, cropPixelT + handleLen), handleStroke)
                // Top-Right
                drawLine(handleColor, Offset(cropPixelR, cropPixelT), Offset(cropPixelR - handleLen, cropPixelT), handleStroke)
                drawLine(handleColor, Offset(cropPixelR, cropPixelT), Offset(cropPixelR, cropPixelT + handleLen), handleStroke)
                // Bottom-Left
                drawLine(handleColor, Offset(cropPixelL, cropPixelB), Offset(cropPixelL + handleLen, cropPixelB), handleStroke)
                drawLine(handleColor, Offset(cropPixelL, cropPixelB), Offset(cropPixelL, cropPixelB - handleLen), handleStroke)
                // Bottom-Right
                drawLine(handleColor, Offset(cropPixelR, cropPixelB), Offset(cropPixelR - handleLen, cropPixelB), handleStroke)
                drawLine(handleColor, Offset(cropPixelR, cropPixelB), Offset(cropPixelR, cropPixelB - handleLen), handleStroke)
            }
        }
    }
}
