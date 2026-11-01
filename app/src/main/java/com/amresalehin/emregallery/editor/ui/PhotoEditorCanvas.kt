package com.amresalehin.emregallery.editor.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Handle types for interactive crop rectangle manipulation.
 */
private enum class CropHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT,
    CENTER_MOVE
}

/**
 * Professional Photo Editor Canvas:
 * - Professional multi-touch gestures (Pinch-to-zoom 1x..5x, two-finger pan, double-tap to zoom/fit).
 * - Interactive Crop Box with 8 handle points (4 corners + 4 edges + center pan) and aspect ratio lock.
 * - Interactive Tone Curve with channel selection, point dragging, and tap-to-add.
 * - Long-press for instant Before / After comparison with animated badge.
 * - Brush & Retouch live drawing with brush radius footprint indicator.
 */
@Composable
fun PhotoEditorCanvas(
    bitmap: Bitmap?,
    originalBitmap: Bitmap? = null,
    activeTool: EditorTool,
    brushType: BrushType,
    brushColor: Color,
    brushWidth: Float,
    activeRetouchType: RetouchType,
    retouchIntensity: Float = 1.0f,
    retouchTintColor: Color = Color(0xFF2979FF),
    cropAspectRatio: Float?,
    isCurveMode: Boolean = false,
    activeCurveChannel: CurveChannel = CurveChannel.RGB,
    curvePoints: List<CurvePoint> = emptyList(),
    onCurvePointChanged: ((channel: CurveChannel, index: Int, inVal: Float, outVal: Float) -> Unit)? = null,
    onCurvePointAdded: ((channel: CurveChannel, inVal: Float, outVal: Float) -> Unit)? = null,
    onDrawingStrokeFinished: (DrawingStroke) -> Unit,
    onRetouchStrokeFinished: (RetouchStroke) -> Unit,
    onCropRectPreview: (RectF?) -> Unit = {},
    onCropRectChanged: (RectF?) -> Unit,
    modifier: Modifier = Modifier
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Professional Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Before & After comparison state
    var isShowingBefore by remember { mutableStateOf(false) }

    // Drawing & Retouching live points
    val livePoints = remember { mutableStateListOf<Offset>() }
    var liveFingerPos by remember { mutableStateOf<Offset?>(null) }

    // Tone Curve active dragging point index
    var draggingPointIndex by remember { mutableStateOf<Int?>(null) }

    // Crop box normalized bounds [0f..1f]
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(1f) }
    var cropBottom by remember { mutableFloatStateOf(1f) }

    // Active crop handle being dragged
    var activeCropHandle by remember { mutableStateOf(CropHandle.NONE) }
    var cropDragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var initialCropLeft by remember { mutableFloatStateOf(0f) }
    var initialCropTop by remember { mutableFloatStateOf(0f) }
    var initialCropRight by remember { mutableFloatStateOf(1f) }
    var initialCropBottom by remember { mutableFloatStateOf(1f) }

    // Adjust crop aspect ratio when preset changes
    LaunchedEffect(cropAspectRatio, bitmap) {
        if (cropAspectRatio != null && bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            if (cropAspectRatio > imgRatio) {
                val h = imgRatio / cropAspectRatio
                cropLeft = 0f
                cropRight = 1f
                cropTop = ((1f - h) / 2f).coerceIn(0f, 0.45f)
                cropBottom = (1f - cropTop).coerceIn(0.55f, 1f)
            } else {
                val w = cropAspectRatio / imgRatio
                cropTop = 0f
                cropBottom = 1f
                cropLeft = ((1f - w) / 2f).coerceIn(0f, 0.45f)
                cropRight = (1f - cropLeft).coerceIn(0.55f, 1f)
            }
            onCropRectChanged(RectF(cropLeft, cropTop, cropRight, cropBottom))
        }
    }

    val isDrawingMode = activeTool == EditorTool.BRUSHES || activeTool == EditorTool.DRAW
    val isRetouchMode = activeTool == EditorTool.RETOUCH || activeTool == EditorTool.REMOVE || activeTool == EditorTool.CLONE
    val isCropMode = activeTool == EditorTool.CROP || activeTool == EditorTool.FREE_CROP || (cropAspectRatio != null)

    // Base dimensions calculation
    val bWidth = (bitmap?.width ?: 1).toFloat()
    val bHeight = (bitmap?.height ?: 1).toFloat()
    val cWidth = containerSize.width.toFloat().coerceAtLeast(1f)
    val cHeight = containerSize.height.toFloat().coerceAtLeast(1f)

    // Fit image to viewport
    val fitScale = min(cWidth / bWidth, cHeight / bHeight) * 0.94f
    val currentScale = fitScale * zoomScale
    val drawW = bWidth * currentScale
    val drawH = bHeight * currentScale

    // Center image and clamp pan when zoomed
    val maxPanX = max(0f, (drawW - cWidth) / 2f + 80f)
    val maxPanY = max(0f, (drawH - cHeight) / 2f + 80f)
    val clampedPanX = if (zoomScale > 1f) panOffset.x.coerceIn(-maxPanX, maxPanX) else 0f
    val clampedPanY = if (zoomScale > 1f) panOffset.y.coerceIn(-maxPanY, maxPanY) else 0f

    val imgLeft = (cWidth - drawW) / 2f + clampedPanX
    val imgTop = (cHeight - drawH) / 2f + clampedPanY

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .then(
                when {
                    isDrawingMode || isRetouchMode -> {
                        Modifier.pointerInput(isDrawingMode, isRetouchMode, brushType, brushColor, brushWidth, activeRetouchType, bitmap) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    livePoints.clear()
                                    livePoints.add(offset)
                                    liveFingerPos = offset
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    livePoints.add(change.position)
                                    liveFingerPos = change.position
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
                                                    radius = brushWidth * 1.5f,
                                                    intensity = retouchIntensity,
                                                    tintColor = retouchTintColor
                                                )
                                            )
                                        }
                                    }
                                    livePoints.clear()
                                    liveFingerPos = null
                                },
                                onDragCancel = {
                                    livePoints.clear()
                                    liveFingerPos = null
                                }
                            )
                        }
                    }
                    isCropMode -> {
                        Modifier.pointerInput(cropLeft, cropTop, cropRight, cropBottom, drawW, drawH, imgLeft, imgTop, cropAspectRatio) {
                            detectDragGestures(
                                onDragStart = { touchOffset ->
                                    cropDragStartOffset = touchOffset
                                    initialCropLeft = cropLeft
                                    initialCropTop = cropTop
                                    initialCropRight = cropRight
                                    initialCropBottom = cropBottom

                                    val pxLeft = imgLeft + cropLeft * drawW
                                    val pxTop = imgTop + cropTop * drawH
                                    val pxRight = imgLeft + cropRight * drawW
                                    val pxBottom = imgTop + cropBottom * drawH

                                    val handleTouchRadius = 40.dp.toPx()

                                    // Check Corners first
                                    val distTL = hypot(touchOffset.x - pxLeft, touchOffset.y - pxTop)
                                    val distTR = hypot(touchOffset.x - pxRight, touchOffset.y - pxTop)
                                    val distBL = hypot(touchOffset.x - pxLeft, touchOffset.y - pxBottom)
                                    val distBR = hypot(touchOffset.x - pxRight, touchOffset.y - pxBottom)

                                    activeCropHandle = when {
                                        distTL <= handleTouchRadius -> CropHandle.TOP_LEFT
                                        distTR <= handleTouchRadius -> CropHandle.TOP_RIGHT
                                        distBL <= handleTouchRadius -> CropHandle.BOTTOM_LEFT
                                        distBR <= handleTouchRadius -> CropHandle.BOTTOM_RIGHT
                                        // Check Edges
                                        touchOffset.y in (pxTop - 24f)..(pxTop + 24f) && touchOffset.x in pxLeft..pxRight -> CropHandle.EDGE_TOP
                                        touchOffset.y in (pxBottom - 24f)..(pxBottom + 24f) && touchOffset.x in pxLeft..pxRight -> CropHandle.EDGE_BOTTOM
                                        touchOffset.x in (pxLeft - 24f)..(pxLeft + 24f) && touchOffset.y in pxTop..pxBottom -> CropHandle.EDGE_LEFT
                                        touchOffset.x in (pxRight - 24f)..(pxRight + 24f) && touchOffset.y in pxTop..pxBottom -> CropHandle.EDGE_RIGHT
                                        // Check Inside Move
                                        touchOffset.x in pxLeft..pxRight && touchOffset.y in pxTop..pxBottom -> CropHandle.CENTER_MOVE
                                        else -> CropHandle.NONE
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (activeCropHandle == CropHandle.NONE) return@detectDragGestures

                                    val totalDx = (change.position.x - cropDragStartOffset.x) / drawW
                                    val totalDy = (change.position.y - cropDragStartOffset.y) / drawH
                                    val minSpan = 0.08f

                                    when (activeCropHandle) {
                                        CropHandle.TOP_LEFT -> {
                                            cropLeft = (initialCropLeft + totalDx).coerceIn(0f, cropRight - minSpan)
                                            cropTop = (initialCropTop + totalDy).coerceIn(0f, cropBottom - minSpan)
                                        }
                                        CropHandle.TOP_RIGHT -> {
                                            cropRight = (initialCropRight + totalDx).coerceIn(cropLeft + minSpan, 1f)
                                            cropTop = (initialCropTop + totalDy).coerceIn(0f, cropBottom - minSpan)
                                        }
                                        CropHandle.BOTTOM_LEFT -> {
                                            cropLeft = (initialCropLeft + totalDx).coerceIn(0f, cropRight - minSpan)
                                            cropBottom = (initialCropBottom + totalDy).coerceIn(cropTop + minSpan, 1f)
                                        }
                                        CropHandle.BOTTOM_RIGHT -> {
                                            cropRight = (initialCropRight + totalDx).coerceIn(cropLeft + minSpan, 1f)
                                            cropBottom = (initialCropBottom + totalDy).coerceIn(cropTop + minSpan, 1f)
                                        }
                                        CropHandle.EDGE_TOP -> {
                                            cropTop = (initialCropTop + totalDy).coerceIn(0f, cropBottom - minSpan)
                                        }
                                        CropHandle.EDGE_BOTTOM -> {
                                            cropBottom = (initialCropBottom + totalDy).coerceIn(cropTop + minSpan, 1f)
                                        }
                                        CropHandle.EDGE_LEFT -> {
                                            cropLeft = (initialCropLeft + totalDx).coerceIn(0f, cropRight - minSpan)
                                        }
                                        CropHandle.EDGE_RIGHT -> {
                                            cropRight = (initialCropRight + totalDx).coerceIn(cropLeft + minSpan, 1f)
                                        }
                                        CropHandle.CENTER_MOVE -> {
                                            val spanW = initialCropRight - initialCropLeft
                                            val spanH = initialCropBottom - initialCropTop
                                            val newL = (initialCropLeft + totalDx).coerceIn(0f, 1f - spanW)
                                            val newT = (initialCropTop + totalDy).coerceIn(0f, 1f - spanH)
                                            cropLeft = newL
                                            cropRight = newL + spanW
                                            cropTop = newT
                                            cropBottom = newT + spanH
                                        }
                                        CropHandle.NONE -> {}
                                    }
                                    onCropRectPreview(RectF(cropLeft, cropTop, cropRight, cropBottom))
                                },
                                onDragEnd = {
                                    activeCropHandle = CropHandle.NONE
                                    onCropRectChanged(RectF(cropLeft, cropTop, cropRight, cropBottom))
                                },
                                onDragCancel = {
                                    activeCropHandle = CropHandle.NONE
                                }
                            )
                        }
                    }
                    isCurveMode -> {
                        Modifier.pointerInput(curvePoints, drawW, drawH, imgLeft, imgTop, activeCurveChannel) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    var closestIdx = -1
                                    var minDist = 48.dp.toPx()
                                    curvePoints.forEachIndexed { idx, pt ->
                                        val px = imgLeft + pt.inVal * drawW
                                        val py = imgTop + (1f - pt.outVal) * drawH
                                        val dist = hypot(startOffset.x - px, startOffset.y - py)
                                        if (dist < minDist) {
                                            minDist = dist
                                            closestIdx = idx
                                        }
                                    }
                                    if (closestIdx >= 0) {
                                        draggingPointIndex = closestIdx
                                    } else if (curvePoints.size < 7) {
                                        val newIn = ((startOffset.x - imgLeft) / drawW).coerceIn(0.05f, 0.95f)
                                        val newOut = (1f - (startOffset.y - imgTop) / drawH).coerceIn(0f, 1f)
                                        onCurvePointAdded?.invoke(activeCurveChannel, newIn, newOut)
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val idx = draggingPointIndex ?: return@detectDragGestures
                                    val newIn = if (idx == 0) 0f else if (idx == curvePoints.lastIndex) 1f else ((change.position.x - imgLeft) / drawW).coerceIn(0f, 1f)
                                    val newOut = (1f - (change.position.y - imgTop) / drawH).coerceIn(0f, 1f)
                                    onCurvePointChanged?.invoke(activeCurveChannel, idx, newIn, newOut)
                                },
                                onDragEnd = {
                                    draggingPointIndex = null
                                },
                                onDragCancel = {
                                    draggingPointIndex = null
                                }
                            )
                        }
                    }
                    else -> {
                        // Multi-gesture support:
                        // 1. Two-finger pinch-to-zoom & pan
                        // 2. Double-tap to toggle 2.5x zoom / reset to fit
                        // 3. Long press for instant "Before / After" compare
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { tapOffset ->
                                        if (zoomScale > 1.1f) {
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                        } else {
                                            zoomScale = 2.5f
                                            // Center pan around double-tap location
                                            val centerX = cWidth / 2f
                                            val centerY = cHeight / 2f
                                            panOffset = Offset(
                                                (centerX - tapOffset.x) * 1.5f,
                                                (centerY - tapOffset.y) * 1.5f
                                            )
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (zoomScale * zoom).coerceIn(1.0f, 5.0f)
                                    zoomScale = newScale
                                    if (newScale > 1f) {
                                        panOffset = Offset(
                                            panOffset.x + pan.x,
                                            panOffset.y + pan.y
                                        )
                                    } else {
                                        panOffset = Offset.Zero
                                    }
                                }
                            }
                            .pointerInput(bitmap, originalBitmap) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var isLongPressed = false
                                    val downTime = System.currentTimeMillis()
                                    val startPos = down.position

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val curTime = System.currentTimeMillis()
                                        val curPos = event.changes.firstOrNull()?.position ?: startPos
                                        val dist = hypot(curPos.x - startPos.x, curPos.y - startPos.y)

                                        if (!isLongPressed && curTime - downTime > 260 && event.changes.size == 1 && dist < 30f) {
                                            isLongPressed = true
                                            isShowingBefore = true
                                        }
                                        if (event.changes.all { !it.pressed }) {
                                            if (isLongPressed) {
                                                isShowingBefore = false
                                            }
                                            break
                                        }
                                    }
                                }
                            }
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentBitmap = if (isShowingBefore) (originalBitmap ?: bitmap) else bitmap
            if (currentBitmap == null || currentBitmap.isRecycled) return@Canvas

            // Draw image on canvas
            drawImage(
                image = currentBitmap.asImageBitmap(),
                dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
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

            // Draw in-progress live retouch points and live brush circle footprint
            if (isRetouchMode || isDrawingMode) {
                val radiusPx = brushWidth * (if (isRetouchMode) 1.5f else 1.0f) * density
                liveFingerPos?.let { pos ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = radiusPx,
                        center = pos,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = pos
                    )
                }
            }

            // Draw Tone Curve Overlay (4x4 Grid, Spline curve, and control point handles)
            if (isCurveMode) {
                val gridColor = Color.White.copy(alpha = 0.28f)
                val gridStroke = 1.dp.toPx()

                // Draw 4x4 Grid
                for (i in 1..3) {
                    val gx = imgLeft + (drawW * (i / 4f))
                    val gy = imgTop + (drawH * (i / 4f))
                    drawLine(gridColor, Offset(gx, imgTop), Offset(gx, imgTop + drawH), gridStroke)
                    drawLine(gridColor, Offset(imgLeft, gy), Offset(imgLeft + drawW, gy), gridStroke)
                }

                // Channel curve line color
                val curveColor = when (activeCurveChannel) {
                    CurveChannel.RGB -> Color.White
                    CurveChannel.RED -> Color(0xFFFF5252)
                    CurveChannel.GREEN -> Color(0xFF69F0AE)
                    CurveChannel.BLUE -> Color(0xFF448AFF)
                }

                if (curvePoints.isNotEmpty()) {
                    val sortedPts = curvePoints.sortedBy { it.inVal }
                    val path = Path()

                    // Sample 64 points along smooth Hermite spline
                    for (step in 0..64) {
                        val t = step / 64f
                        val outVal = evaluateSpline(sortedPts, t)
                        val px = imgLeft + t * drawW
                        val py = imgTop + (1f - outVal.coerceIn(0f, 1f)) * drawH
                        if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }

                    drawPath(
                        path = path,
                        color = curveColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Control point handles
                    sortedPts.forEachIndexed { idx, pt ->
                        val px = imgLeft + pt.inVal * drawW
                        val py = imgTop + (1f - pt.outVal) * drawH
                        val isDragging = draggingPointIndex == idx

                        // Outer halo
                        drawCircle(
                            color = curveColor,
                            radius = if (isDragging) 11.dp.toPx() else 7.dp.toPx(),
                            center = Offset(px, py),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Inner fill
                        drawCircle(
                            color = if (isDragging) curveColor else Color.Black.copy(alpha = 0.8f),
                            radius = if (isDragging) 6.dp.toPx() else 4.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // Draw Professional Crop Overlay Grid & 8 Handles
            if (isCropMode) {
                val cropPixelL = imgLeft + cropLeft * drawW
                val cropPixelT = imgTop + cropTop * drawH
                val cropPixelR = imgLeft + cropRight * drawW
                val cropPixelB = imgTop + cropBottom * drawH
                val cropW = cropPixelR - cropPixelL
                val cropH = cropPixelB - cropPixelT

                // Darken outside area (scrim)
                val scrimColor = Color.Black.copy(alpha = 0.58f)
                drawRect(scrimColor, Offset(0f, 0f), Size(cWidth, cropPixelT))
                drawRect(scrimColor, Offset(0f, cropPixelB), Size(cWidth, cHeight - cropPixelB))
                drawRect(scrimColor, Offset(0f, cropPixelT), Size(cropPixelL, cropH))
                drawRect(scrimColor, Offset(cropPixelR, cropPixelT), Size(cWidth - cropPixelR, cropH))

                // Outer border line
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cropPixelL, cropPixelT),
                    size = Size(cropW, cropH),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Rule-of-thirds grid lines
                val oneThirdW = cropW / 3f
                val oneThirdH = cropH / 3f
                val gridColor = Color.White.copy(alpha = 0.45f)
                val gridStroke = 1.dp.toPx()

                drawLine(gridColor, Offset(cropPixelL + oneThirdW, cropPixelT), Offset(cropPixelL + oneThirdW, cropPixelB), gridStroke)
                drawLine(gridColor, Offset(cropPixelL + oneThirdW * 2, cropPixelT), Offset(cropPixelL + oneThirdW * 2, cropPixelB), gridStroke)
                drawLine(gridColor, Offset(cropPixelL, cropPixelT + oneThirdH), Offset(cropPixelR, cropPixelT + oneThirdH), gridStroke)
                drawLine(gridColor, Offset(cropPixelL, cropPixelT + oneThirdH * 2), Offset(cropPixelR, cropPixelT + oneThirdH * 2), gridStroke)

                // 4 Corner bracket handles (High contrast thick white brackets)
                val cornerLen = 22.dp.toPx()
                val cornerStroke = 3.5.dp.toPx()
                val handleColor = Color.White

                // Top-Left
                drawLine(handleColor, Offset(cropPixelL, cropPixelT), Offset(cropPixelL + cornerLen, cropPixelT), cornerStroke)
                drawLine(handleColor, Offset(cropPixelL, cropPixelT), Offset(cropPixelL, cropPixelT + cornerLen), cornerStroke)
                // Top-Right
                drawLine(handleColor, Offset(cropPixelR, cropPixelT), Offset(cropPixelR - cornerLen, cropPixelT), cornerStroke)
                drawLine(handleColor, Offset(cropPixelR, cropPixelT), Offset(cropPixelR, cropPixelT + cornerLen), cornerStroke)
                // Bottom-Left
                drawLine(handleColor, Offset(cropPixelL, cropPixelB), Offset(cropPixelL + cornerLen, cropPixelB), cornerStroke)
                drawLine(handleColor, Offset(cropPixelL, cropPixelB), Offset(cropPixelL, cropPixelB - cornerLen), cornerStroke)
                // Bottom-Right
                drawLine(handleColor, Offset(cropPixelR, cropPixelB), Offset(cropPixelR - cornerLen, cropPixelB), cornerStroke)
                drawLine(handleColor, Offset(cropPixelR, cropPixelB), Offset(cropPixelR, cropPixelB - cornerLen), cornerStroke)

                // 4 Edge center bars
                val edgeBarLen = 16.dp.toPx()
                // Top edge
                drawLine(handleColor, Offset(cropPixelL + cropW / 2f - edgeBarLen / 2f, cropPixelT), Offset(cropPixelL + cropW / 2f + edgeBarLen / 2f, cropPixelT), cornerStroke)
                // Bottom edge
                drawLine(handleColor, Offset(cropPixelL + cropW / 2f - edgeBarLen / 2f, cropPixelB), Offset(cropPixelL + cropW / 2f + edgeBarLen / 2f, cropPixelB), cornerStroke)
                // Left edge
                drawLine(handleColor, Offset(cropPixelL, cropPixelT + cropH / 2f - edgeBarLen / 2f), Offset(cropPixelL, cropPixelT + cropH / 2f + edgeBarLen / 2f), cornerStroke)
                // Right edge
                drawLine(handleColor, Offset(cropPixelR, cropPixelT + cropH / 2f - edgeBarLen / 2f), Offset(cropPixelR, cropPixelT + cropH / 2f + edgeBarLen / 2f), cornerStroke)
            }
        }

        // Floating Reset Zoom Pill (Visible when zoomed in)
        AnimatedVisibility(
            visible = zoomScale > 1.15f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xDD1E1E1E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clickable {
                        zoomScale = 1.0f
                        panOffset = Offset.Zero
                    }
                    .testTag("reset_zoom_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOutMap,
                        contentDescription = "Reset Zoom",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${(zoomScale * 100).toInt()}% • Reset",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Before & After overlay pill (minimal monochrome indicator)
        AnimatedVisibility(
            visible = isShowingBefore,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xEE121212),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                shadowElevation = 10.dp
            ) {
                Text(
                    text = "BEFORE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Evaluates piecewise smooth Hermite interpolation for tone curves.
 */
private fun evaluateSpline(points: List<CurvePoint>, t: Float): Float {
    if (points.isEmpty()) return t
    if (points.size == 1) return points[0].outVal
    if (t <= points.first().inVal) return points.first().outVal
    if (t >= points.last().inVal) return points.last().outVal

    for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        if (t >= p0.inVal && t <= p1.inVal) {
            val frac = if (p1.inVal != p0.inVal) (t - p0.inVal) / (p1.inVal - p0.inVal) else 0f
            val smoothFrac = frac * frac * (3f - 2f * frac)
            return p0.outVal + smoothFrac * (p1.outVal - p0.outVal)
        }
    }
    return t
}
