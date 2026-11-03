package com.amresalehin.emregallery.editor.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Extended Crop & Transform subcategory navigation and controls:
 * Aspect Ratio, Straighten, Perspective, Flip, Rotate, Shape Crop, Liquify, Dispersion, Motion Blur, Resize.
 */
@Composable
fun CropExtendedPanel(
    transform: TransformParams,
    selectedCropPreset: AspectRatioPreset,
    onSelectCropPreset: (AspectRatioPreset) -> Unit,
    onRotate90: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onStraightenChange: (Float) -> Unit,
    onStraightenFinished: () -> Unit,
    onAutoLevel: () -> Unit,
    onShapeCropChange: (ShapeCropType?) -> Unit,
    onPerspectiveChange: (x: Float, y: Float) -> Unit,
    onCornerPinReset: () -> Unit,
    onCornerPinEnable: () -> Unit,
    onLiquifyModeChange: (LiquifyMode) -> Unit,
    currentLiquifyMode: LiquifyMode,
    liquifyRadius: Float,
    onLiquifyRadiusChange: (Float) -> Unit,
    liquifyStrength: Float,
    onLiquifyStrengthChange: (Float) -> Unit,
    onClearLiquify: () -> Unit,
    onDispersionChange: (Float) -> Unit,
    onMotionChange: (amount: Float, angle: Float) -> Unit,
    onScaleFactorChange: (Float) -> Unit,
    onResetCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTool by remember { mutableStateOf("Crop") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        when (activeSubTool) {
            "Crop" -> {
                CropControlsPanel(
                    selectedPreset = selectedCropPreset,
                    straightenAngle = transform.straightenAngle,
                    perspectiveX = transform.perspectiveX,
                    perspectiveY = transform.perspectiveY,
                    onSelectPreset = onSelectCropPreset,
                    onRotate90 = onRotate90,
                    onFlipH = onFlipH,
                    onFlipV = onFlipV,
                    onStraightenChange = onStraightenChange,
                    onStraightenFinished = onStraightenFinished,
                    onPerspectiveChange = onPerspectiveChange,
                    onAutoLevel = onAutoLevel,
                    onResetCrop = onResetCrop
                )
            }
            "Shape" -> {
                ShapeCropSubPanel(
                    currentShape = transform.shapeCrop,
                    onSelectShape = onShapeCropChange
                )
            }
            "Warp" -> {
                LiquifySubPanel(
                    currentMode = currentLiquifyMode,
                    onModeChange = onLiquifyModeChange,
                    radius = liquifyRadius,
                    onRadiusChange = onLiquifyRadiusChange,
                    strength = liquifyStrength,
                    onStrengthChange = onLiquifyStrengthChange,
                    pointCount = transform.liquifyPoints.size,
                    onClear = onClearLiquify
                )
            }
            "Motion" -> {
                MotionDispersionSubPanel(
                    dispersionAmount = transform.dispersionAmount,
                    onDispersionChange = onDispersionChange,
                    motionAmount = transform.motionAmount,
                    motionAngle = transform.motionAngle,
                    onMotionChange = onMotionChange
                )
            }
            "Resize" -> {
                ResizeSubPanel(
                    scaleFactor = transform.scaleFactor,
                    onScaleFactorChange = onScaleFactorChange
                )
            }
        }

        // Optional secondary tool pills if user wants Shape/Liquify/Resize
        if (transform.shapeCrop != null || activeSubTool != "Crop") {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val subTools = listOf(
                    "Crop" to "Crop & Transform",
                    "Shape" to "Shape Crop",
                    "Warp" to "Liquify",
                    "Motion" to "Dispersion",
                    "Resize" to "Resize"
                )
                items(subTools) { (id, label) ->
                    val isSelected = activeSubTool == id
                    Surface(
                        onClick = { activeSubTool = id },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color.White else Color(0xFF222222),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modes for the 6 circular transform buttons
 */
enum class CropTransformTool(val label: String) {
    STRAIGHTEN("Straighten"),
    FLIP_H("Flip Horizontal"),
    FLIP_V("Flip Vertical"),
    PERSPECTIVE_Y("Vertical perspective"),
    PERSPECTIVE_X("Horizontal perspective"),
    ROTATE("Rotate 90°")
}

/**
 * Pixel-perfect Crop & Transform Panel matching user reference:
 * - Row 1: Aspect Ratio Pills (✓ Free, Original, 1:1, 9:16, 4:5, 3:2, ...)
 * - Row 2: 6 Circular Buttons (Straighten, Flip H, Flip V, Vertical Perspective, Horizontal Perspective, Rotate)
 * - Row 3: Active Tool Title ("Straighten", "Vertical perspective", etc.)
 * - Row 4: Ruler Scale Dial ([Spirit Level] [Center Mark & Ruler Wheel] [0° Badge])
 */
@Composable
fun CropControlsPanel(
    selectedPreset: AspectRatioPreset,
    straightenAngle: Float,
    perspectiveX: Float = 0f,
    perspectiveY: Float = 0f,
    onSelectPreset: (AspectRatioPreset) -> Unit,
    onRotate90: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onStraightenChange: (Float) -> Unit,
    onStraightenFinished: (() -> Unit)? = null,
    onPerspectiveChange: ((x: Float, y: Float) -> Unit)? = null,
    onApply: () -> Unit = {},
    onAutoLevel: (() -> Unit)? = null,
    onResetCrop: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    // Color definitions matching the screenshot aesthetic
    val containerBg = if (isDark) Color(0xFF1E1F24) else Color(0xFFF4F3F7)
    val containerBorder = if (isDark) Color(0xFF33353C) else Color(0xFFE4E4EB)
    val activePillBg = if (isDark) Color(0xFFE2E2E6) else Color(0xFF2C2D32)
    val activePillFg = if (isDark) Color(0xFF1C1C20) else Color(0xFFFFFFFF)
    val inactivePillBg = if (isDark) Color(0xFF292A30) else Color(0xFFEBEBF0)
    val inactivePillFg = if (isDark) Color(0xFFE2E2E6) else Color(0xFF1F2024)
    val primaryText = if (isDark) Color(0xFFF0F0F5) else Color(0xFF1F2024)

    // Current active transform tool
    var activeTool by remember { mutableStateOf(CropTransformTool.STRAIGHTEN) }

    // Resolve current angle and limits based on active tool
    val currentAngle = when (activeTool) {
        CropTransformTool.STRAIGHTEN -> straightenAngle
        CropTransformTool.PERSPECTIVE_Y -> perspectiveY
        CropTransformTool.PERSPECTIVE_X -> perspectiveX
        else -> straightenAngle
    }

    val valueRange = when (activeTool) {
        CropTransformTool.STRAIGHTEN -> -45f..45f
        CropTransformTool.PERSPECTIVE_Y, CropTransformTool.PERSPECTIVE_X -> -30f..30f
        else -> -45f..45f
    }

    val onAngleChange: (Float) -> Unit = { newAngle ->
        when (activeTool) {
            CropTransformTool.STRAIGHTEN -> onStraightenChange(newAngle)
            CropTransformTool.PERSPECTIVE_Y -> onPerspectiveChange?.invoke(perspectiveX, newAngle)
            CropTransformTool.PERSPECTIVE_X -> onPerspectiveChange?.invoke(newAngle, perspectiveY)
            else -> onStraightenChange(newAngle)
        }
    }

    val onResetActiveAngle: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (activeTool) {
            CropTransformTool.STRAIGHTEN -> {
                onStraightenChange(0f)
                onStraightenFinished?.invoke()
            }
            CropTransformTool.PERSPECTIVE_Y -> {
                onPerspectiveChange?.invoke(perspectiveX, 0f)
            }
            CropTransformTool.PERSPECTIVE_X -> {
                onPerspectiveChange?.invoke(0f, perspectiveY)
            }
            else -> {
                onStraightenChange(0f)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerBg,
        border = BorderStroke(1.dp, containerBorder),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("crop_main_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            // -------------------------------------------------------------
            // ROW 1: Aspect Ratio Pills (✓ Free, Original, 1:1, 9:16, 4:5, 3:2, ...)
            // -------------------------------------------------------------
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(AspectRatioPreset.entries) { preset ->
                    val isSelected = selectedPreset == preset
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectPreset(preset)
                        },
                        shape = CircleShape,
                        color = if (isSelected) activePillBg else inactivePillBg,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("crop_aspect_${preset.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = activePillFg,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                text = preset.label,
                                color = if (isSelected) activePillFg else inactivePillFg,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // -------------------------------------------------------------
            // ROW 2: 6 Circular Action Buttons (Straighten, Flip H, Flip V, Perspective Y, Perspective X, Rotate)
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: Straighten
                val isStraightenActive = activeTool == CropTransformTool.STRAIGHTEN
                Surface(
                    onClick = {
                        activeTool = CropTransformTool.STRAIGHTEN
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    shape = CircleShape,
                    color = if (isStraightenActive) activePillBg else inactivePillBg,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("crop_tool_straighten")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        StraightenIcon(
                            tint = if (isStraightenActive) activePillFg else inactivePillFg,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Button 2: Flip Horizontal
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFlipH()
                    },
                    shape = CircleShape,
                    color = inactivePillBg,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("crop_tool_flip_h")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FlipHorizontalIcon(
                            tint = inactivePillFg,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Button 3: Flip Vertical
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFlipV()
                    },
                    shape = CircleShape,
                    color = inactivePillBg,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("crop_tool_flip_v")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FlipVerticalIcon(
                            tint = inactivePillFg,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Button 4: Vertical Perspective
                val isPerspYActive = activeTool == CropTransformTool.PERSPECTIVE_Y
                Surface(
                    onClick = {
                        activeTool = CropTransformTool.PERSPECTIVE_Y
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    shape = CircleShape,
                    color = if (isPerspYActive) activePillBg else inactivePillBg,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("crop_tool_perspective_y")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PerspectiveVerticalIcon(
                            tint = if (isPerspYActive) activePillFg else inactivePillFg,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Button 5: Horizontal Perspective
                val isPerspXActive = activeTool == CropTransformTool.PERSPECTIVE_X
                Surface(
                    onClick = {
                        activeTool = CropTransformTool.PERSPECTIVE_X
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    shape = CircleShape,
                    color = if (isPerspXActive) activePillBg else inactivePillBg,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("crop_tool_perspective_x")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PerspectiveHorizontalIcon(
                            tint = if (isPerspXActive) activePillFg else inactivePillFg,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Button 6: Rotate 90°
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRotate90()
                    },
                    shape = CircleShape,
                    color = inactivePillBg,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("crop_tool_rotate")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RotateIcon(
                            tint = inactivePillFg,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // -------------------------------------------------------------
            // ROW 3: Active Tool Title ("Straighten", "Vertical perspective", etc.)
            // -------------------------------------------------------------
            Text(
                text = activeTool.label,
                color = primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .testTag("crop_active_tool_title")
            )

            Spacer(Modifier.height(10.dp))

            // -------------------------------------------------------------
            // ROW 4: Ruler Control Row ([Spirit Level] [Center Indicator & Scale Dial] [0° Badge])
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Icon Button: Auto-level / Level Gauge
                Surface(
                    onClick = {
                        if (activeTool == CropTransformTool.STRAIGHTEN && onAutoLevel != null) {
                            onAutoLevel()
                        } else {
                            onResetActiveAngle()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = inactivePillBg,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("crop_auto_level_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SpiritLevelIcon(
                            tint = inactivePillFg,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Center Ruler Dial Container
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = inactivePillBg,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("crop_ruler_container")
                ) {
                    RulerAngleWheel(
                        value = currentAngle,
                        onValueChange = onAngleChange,
                        onValueChangeFinished = { onStraightenFinished?.invoke() },
                        valueRange = valueRange,
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Right Angle Badge Button (Tap resets to 0°)
                Surface(
                    onClick = onResetActiveAngle,
                    shape = RoundedCornerShape(12.dp),
                    color = inactivePillBg,
                    modifier = Modifier
                        .height(46.dp)
                        .widthIn(min = 46.dp)
                        .testTag("crop_degree_badge")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = "${currentAngle.roundToInt()}°",
                            color = inactivePillFg,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draggable Horizontal Ruler Wheel Dial:
 * - Center vertical black indicator line
 * - Degree ticks with labels (-10, 0, 10, etc.)
 * - Smooth quadratic falloff on edges for seamless fading
 */
@Composable
fun RulerAngleWheel(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val pixelsPerDegree = with(density) { 7.5.dp.toPx() }
    val lastIntegerDegree = remember { mutableIntStateOf(value.roundToInt()) }

    val tickColor = if (isDark) Color(0xFFD4D4DA) else Color(0xFF2C2D31)
    val centerLineColor = if (isDark) Color.White else Color(0xFF1B1B1F)

    val textPaint = remember(isDark, density) {
        Paint().apply {
            color = if (isDark) android.graphics.Color.parseColor("#C8C8D0") else android.graphics.Color.parseColor("#2C2D31")
            textSize = with(density) { 9.5.sp.toPx() }
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }

    Box(
        modifier = modifier
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { /* start drag */ },
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // Dragging right pulls left ticks (decreasing angle), dragging left pulls right ticks (increasing angle)
                        val deltaDegrees = -dragAmount / pixelsPerDegree
                        val newValue = (value + deltaDegrees).coerceIn(valueRange)
                        val newIntDegree = newValue.roundToInt()
                        if (newIntDegree != lastIntegerDegree.intValue) {
                            if (newIntDegree == 0 || newIntDegree % 5 == 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            lastIntegerDegree.intValue = newIntDegree
                        }
                        onValueChange(newValue)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val maxDist = cx.coerceAtLeast(1f)

            val minDeg = valueRange.start.toInt()
            val maxDeg = valueRange.endInclusive.toInt()

            val visibleSpan = (cx / pixelsPerDegree).toInt() + 2
            val startDeg = (value.toInt() - visibleSpan).coerceAtLeast(minDeg)
            val endDeg = (value.toInt() + visibleSpan).coerceAtMost(maxDeg)

            val baseTickTop = h * 0.28f

            for (deg in startDeg..endDeg) {
                val tickX = cx + (deg - value) * pixelsPerDegree
                if (tickX < 0f || tickX > w) continue

                val dist = abs(tickX - cx)
                val alpha = (1f - (dist / maxDist).let { it * it }).coerceIn(0f, 1f)
                if (alpha <= 0.01f) continue

                val isMajor = deg % 10 == 0
                val isMedium = deg % 5 == 0 && !isMajor
                val tickHeight = when {
                    isMajor -> 18.dp.toPx()
                    isMedium -> 12.dp.toPx()
                    else -> 6.5.dp.toPx()
                }

                val strokeW = if (isMajor) 1.5.dp.toPx() else 1.0.dp.toPx()

                drawLine(
                    color = tickColor.copy(alpha = alpha * (if (isMajor) 0.95f else if (isMedium) 0.65f else 0.40f)),
                    start = Offset(tickX, baseTickTop),
                    end = Offset(tickX, baseTickTop + tickHeight),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )

                if (isMajor) {
                    textPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
                    drawContext.canvas.nativeCanvas.drawText(
                        "$deg",
                        tickX,
                        baseTickTop + tickHeight + 11.dp.toPx(),
                        textPaint
                    )
                }
            }

            // Bold center line indicator
            val centerH = 26.dp.toPx()
            drawLine(
                color = centerLineColor,
                start = Offset(cx, (h - centerH) / 2f),
                end = Offset(cx, (h + centerH) / 2f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

// -------------------------------------------------------------
// CUSTOM CANVAS ICON RENDERERS (Pixel-matched to user image)
// -------------------------------------------------------------

/**
 * 1. Straighten Icon: Tilted rectangle with horizontal dashed level line
 */
@Composable
fun StraightenIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Rotate canvas to draw tilted rectangle
        rotate(-12f, pivot = center) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.18f, h * 0.28f),
                size = Size(w * 0.64f, h * 0.44f),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Horizontal dashed line through center
        val dashW = 3.dp.toPx()
        val dashG = 2.5.dp.toPx()
        val y = center.y
        var x = w * 0.10f
        val endX = w * 0.90f
        while (x < endX) {
            val nextX = (x + dashW).coerceAtMost(endX)
            drawLine(
                color = tint,
                start = Offset(x, y),
                end = Offset(nextX, y),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
            x += dashW + dashG
        }
    }
}

/**
 * 2. Flip Horizontal Icon: Rectangle with solid left half and dashed right half
 */
@Composable
fun FlipHorizontalIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val top = h * 0.22f
        val bottom = h * 0.78f
        val cx = center.x
        val left = w * 0.26f
        val right = w * 0.74f

        // Left solid half
        val leftPath = Path().apply {
            moveTo(cx, top)
            lineTo(left + 3.dp.toPx(), top)
            quadraticTo(left, top, left, top + 3.dp.toPx())
            lineTo(left, bottom - 3.dp.toPx())
            quadraticTo(left, bottom, left + 3.dp.toPx(), bottom)
            lineTo(cx, bottom)
        }
        drawPath(leftPath, color = tint, style = Stroke(width = stroke))

        // Center line
        drawLine(tint, Offset(cx, top), Offset(cx, bottom), strokeWidth = stroke)

        // Right dashed half
        val dashW = 2.5.dp.toPx()
        val dashG = 2.dp.toPx()
        drawDashedLineSegment(tint, Offset(cx, top), Offset(right, top), stroke, dashW, dashG)
        drawDashedLineSegment(tint, Offset(right, top), Offset(right, bottom), stroke, dashW, dashG)
        drawDashedLineSegment(tint, Offset(right, bottom), Offset(cx, bottom), stroke, dashW, dashG)
    }
}

/**
 * 3. Flip Vertical Icon: Rectangle with solid top half and dashed bottom half
 */
@Composable
fun FlipVerticalIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val left = w * 0.22f
        val right = w * 0.78f
        val cy = center.y
        val top = h * 0.26f
        val bottom = h * 0.74f

        // Top solid half
        val topPath = Path().apply {
            moveTo(left, cy)
            lineTo(left, top + 3.dp.toPx())
            quadraticTo(left, top, left, top + 3.dp.toPx())
            lineTo(right - 3.dp.toPx(), top)
            quadraticTo(right, top, right, top + 3.dp.toPx())
            lineTo(right, cy)
        }
        drawPath(topPath, color = tint, style = Stroke(width = stroke))

        // Center line
        drawLine(tint, Offset(left, cy), Offset(right, cy), strokeWidth = stroke)

        // Bottom dashed half
        val dashW = 2.5.dp.toPx()
        val dashG = 2.dp.toPx()
        drawDashedLineSegment(tint, Offset(left, cy), Offset(left, bottom), stroke, dashW, dashG)
        drawDashedLineSegment(tint, Offset(left, bottom), Offset(right, bottom), stroke, dashW, dashG)
        drawDashedLineSegment(tint, Offset(right, bottom), Offset(right, cy), stroke, dashW, dashG)
    }
}

/**
 * 4. Perspective Vertical Icon: 3D perspective trapezoid grid
 */
@Composable
fun PerspectiveVerticalIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val topY = h * 0.22f
        val bottomY = h * 0.78f
        val topInset = w * 0.16f
        val topLeft = Offset(w * 0.22f + topInset, topY)
        val topRight = Offset(w * 0.78f - topInset, topY)
        val bottomLeft = Offset(w * 0.16f, bottomY)
        val bottomRight = Offset(w * 0.84f, bottomY)

        val path = Path().apply {
            moveTo(topLeft.x, topLeft.y)
            lineTo(topRight.x, topRight.y)
            lineTo(bottomRight.x, bottomRight.y)
            lineTo(bottomLeft.x, bottomLeft.y)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = stroke))

        // Horizontal dividers
        for (i in 1..2) {
            val f = i / 3f
            val ly = topY + (bottomY - topY) * f
            val lx1 = topLeft.x + (bottomLeft.x - topLeft.x) * f
            val lx2 = topRight.x + (bottomRight.x - topRight.x) * f
            drawLine(tint, Offset(lx1, ly), Offset(lx2, ly), strokeWidth = stroke * 0.8f)
        }
        // Vertical dividers (3 vertical lines inside)
        for (i in 1..3) {
            val f = i / 4f
            val tx = topLeft.x + (topRight.x - topLeft.x) * f
            val bx = bottomLeft.x + (bottomRight.x - bottomLeft.x) * f
            drawLine(tint, Offset(tx, topY), Offset(bx, bottomY), strokeWidth = stroke * 0.8f)
        }
    }
}

/**
 * 5. Perspective Horizontal Icon: 3D trapezoid grid with horizontal double-arrow
 */
@Composable
fun PerspectiveHorizontalIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        val topY = h * 0.18f
        val bottomY = h * 0.65f
        val topInset = w * 0.14f
        val topLeft = Offset(w * 0.22f + topInset, topY)
        val topRight = Offset(w * 0.78f - topInset, topY)
        val bottomLeft = Offset(w * 0.16f, bottomY)
        val bottomRight = Offset(w * 0.84f, bottomY)

        val path = Path().apply {
            moveTo(topLeft.x, topLeft.y)
            lineTo(topRight.x, topRight.y)
            lineTo(bottomRight.x, bottomRight.y)
            lineTo(bottomLeft.x, bottomLeft.y)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = stroke))

        // Grid horizontal
        val ly = (topY + bottomY) / 2f
        val lx1 = (topLeft.x + bottomLeft.x) / 2f
        val lx2 = (topRight.x + bottomRight.x) / 2f
        drawLine(tint, Offset(lx1, ly), Offset(lx2, ly), strokeWidth = stroke * 0.8f)

        // Grid vertical (2 inner lines)
        for (i in 1..2) {
            val f = i / 3f
            val tx = topLeft.x + (topRight.x - topLeft.x) * f
            val bx = bottomLeft.x + (bottomRight.x - bottomLeft.x) * f
            drawLine(tint, Offset(tx, topY), Offset(bx, bottomY), strokeWidth = stroke * 0.8f)
        }

        // Horizontal double-headed arrow underneath
        val arrowY = h * 0.80f
        val ax1 = w * 0.24f
        val ax2 = w * 0.76f
        drawLine(tint, Offset(ax1, arrowY), Offset(ax2, arrowY), strokeWidth = stroke)
        // Left head
        drawLine(tint, Offset(ax1 + 3.5.dp.toPx(), arrowY - 3.dp.toPx()), Offset(ax1, arrowY), strokeWidth = stroke)
        drawLine(tint, Offset(ax1 + 3.5.dp.toPx(), arrowY + 3.dp.toPx()), Offset(ax1, arrowY), strokeWidth = stroke)
        // Right head
        drawLine(tint, Offset(ax2 - 3.5.dp.toPx(), arrowY - 3.dp.toPx()), Offset(ax2, arrowY), strokeWidth = stroke)
        drawLine(tint, Offset(ax2 - 3.5.dp.toPx(), arrowY + 3.dp.toPx()), Offset(ax2, arrowY), strokeWidth = stroke)
    }
}

/**
 * 6. Rotate Icon: Open circular arc with arrow tip
 */
@Composable
fun RotateIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val r = w * 0.28f
        drawArc(
            color = tint,
            startAngle = -50f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val rad = Math.toRadians(-50.0)
        val tipX = center.x + (r * Math.cos(rad)).toFloat()
        val tipY = center.y + (r * Math.sin(rad)).toFloat()
        drawLine(tint, Offset(tipX - 1.dp.toPx(), tipY - 5.dp.toPx()), Offset(tipX, tipY), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(tipX + 5.dp.toPx(), tipY - 1.dp.toPx()), Offset(tipX, tipY), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/**
 * 7. Spirit Level / Ruler Icon
 */
@Composable
fun SpiritLevelIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val left = w * 0.16f
        val right = w * 0.84f
        val top = h * 0.30f
        val bottom = h * 0.70f

        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = stroke)
        )

        // Ruler ticks inside hanging down from the top edge
        val tickCount = 5
        val spacing = (right - left) / (tickCount + 1)
        for (i in 1..tickCount) {
            val tx = left + i * spacing
            val tickH = if (i == 3) 7.dp.toPx() else 4.5.dp.toPx()
            drawLine(
                color = tint,
                start = Offset(tx, top),
                end = Offset(tx, top + tickH),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawDashedLineSegment(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float,
    dashLength: Float,
    gapLength: Float
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
    if (dist <= 0f) return
    val nx = dx / dist
    val ny = dy / dist
    var cur = 0f
    while (cur < dist) {
        val next = (cur + dashLength).coerceAtMost(dist)
        drawLine(
            color = color,
            start = Offset(start.x + nx * cur, start.y + ny * cur),
            end = Offset(start.x + nx * next, start.y + ny * next),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        cur += dashLength + gapLength
    }
}

// -------------------------------------------------------------
// EXTENDED SUB-PANELS (Preserved for full editing functionality)
// -------------------------------------------------------------

@Composable
fun ShapeCropSubPanel(
    currentShape: ShapeCropType?,
    onSelectShape: (ShapeCropType?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Geometric Shape Crop", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (currentShape != null) {
                TextButton(onClick = { onSelectShape(null) }) {
                    Text("Clear Shape", color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ShapeCropType.entries) { shape ->
                val isSelected = currentShape == shape
                val icon = when (shape) {
                    ShapeCropType.RECTANGLE -> Icons.Default.CropSquare
                    ShapeCropType.CIRCLE -> Icons.Default.Circle
                    ShapeCropType.OVAL -> Icons.Default.PanoramaFishEye
                    ShapeCropType.TRIANGLE -> Icons.Default.ChangeHistory
                    ShapeCropType.HEART -> Icons.Default.Favorite
                    ShapeCropType.STAR -> Icons.Default.Star
                    ShapeCropType.HEXAGON -> Icons.Default.Hexagon
                    ShapeCropType.DIAMOND -> Icons.Default.Diamond
                    ShapeCropType.SHIELD -> Icons.Default.Shield
                }
                Surface(
                    onClick = { onSelectShape(if (isSelected) null else shape) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color.White else Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.size(68.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = shape.label,
                            tint = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = shape.label,
                            color = if (isSelected) Color.Black else Color(0xFFAAAAAA),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PerspectiveSubPanel(
    perspectiveX: Float,
    perspectiveY: Float,
    cornerPins: List<CornerPinPoint>?,
    onPerspectiveChange: (x: Float, y: Float) -> Unit,
    onCornerPinReset: () -> Unit,
    onCornerPinEnable: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Perspective Skew & Pinning", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        LrSliderRow(
            title = "Horizontal Skew",
            value = perspectiveX,
            displayValue = "${perspectiveX.toInt()}°",
            valueRange = -30f..30f,
            minLabel = "−30°",
            maxLabel = "+30°",
            onDragStart = {},
            onValueChange = { onPerspectiveChange(it, perspectiveY) },
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Vertical Skew",
            value = perspectiveY,
            displayValue = "${perspectiveY.toInt()}°",
            valueRange = -30f..30f,
            minLabel = "−30°",
            maxLabel = "+30°",
            onDragStart = {},
            onValueChange = { onPerspectiveChange(perspectiveX, it) },
            onValueChangeFinished = {}
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isCornerPinActive = !cornerPins.isNullOrEmpty()
            Button(
                onClick = {
                    if (isCornerPinActive) onCornerPinReset() else onCornerPinEnable()
                },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCornerPinActive) Color(0xFF2979FF) else Color(0xFF262626),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.PinDrop, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isCornerPinActive) "Disable 4-Pin" else "Enable 4-Corner Pin", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    onPerspectiveChange(0f, 0f)
                    onCornerPinReset()
                },
                modifier = Modifier.height(42.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text("Reset", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun LiquifySubPanel(
    currentMode: LiquifyMode,
    onModeChange: (LiquifyMode) -> Unit,
    radius: Float,
    onRadiusChange: (Float) -> Unit,
    strength: Float,
    onStrengthChange: (Float) -> Unit,
    pointCount: Int,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Liquify & Warp ($pointCount strokes)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (pointCount > 0) {
                TextButton(onClick = onClear) {
                    Text("Clear All", color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquifyMode.entries.forEach { mode ->
                val isSelected = currentMode == mode
                Surface(
                    onClick = { onModeChange(mode) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LrSliderRow(
            title = "Brush Radius",
            value = radius,
            displayValue = "${radius.toInt()} px",
            valueRange = 10f..150f,
            minLabel = "10 px",
            maxLabel = "150 px",
            onDragStart = {},
            onValueChange = onRadiusChange,
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Deformation Strength",
            value = strength * 100f,
            displayValue = "${(strength * 100).toInt()}%",
            valueRange = 10f..100f,
            minLabel = "10%",
            maxLabel = "100%",
            onDragStart = {},
            onValueChange = { onStrengthChange(it / 100f) },
            onValueChangeFinished = {}
        )

        Text(
            text = "Tap or drag across photo canvas to distort and sculpt pixels.",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun MotionDispersionSubPanel(
    dispersionAmount: Float,
    onDispersionChange: (Float) -> Unit,
    motionAmount: Float,
    motionAngle: Float,
    onMotionChange: (amount: Float, angle: Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Dispersion & Directional Motion Blur", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        LrSliderRow(
            title = "Dispersion Amount",
            value = dispersionAmount,
            displayValue = "${dispersionAmount.toInt()}",
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "100",
            onDragStart = {},
            onValueChange = onDispersionChange,
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Motion Blur Amount",
            value = motionAmount,
            displayValue = "${motionAmount.toInt()}",
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "100",
            onDragStart = {},
            onValueChange = { onMotionChange(it, motionAngle) },
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Motion Direction Angle",
            value = motionAngle,
            displayValue = "${motionAngle.toInt()}°",
            valueRange = 0f..360f,
            minLabel = "0°",
            maxLabel = "360°",
            onDragStart = {},
            onValueChange = { onMotionChange(motionAmount, it) },
            onValueChangeFinished = {}
        )
    }
}

@Composable
fun ResizeSubPanel(
    scaleFactor: Float,
    onScaleFactorChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Image Scaling & Resize", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                "25%" to 0.25f,
                "50%" to 0.50f,
                "75%" to 0.75f,
                "100%" to 1.0f,
                "150%" to 1.5f,
                "200%" to 2.0f
            )
            presets.forEach { (label, factor) ->
                val isSelected = abs(scaleFactor - factor) < 0.05f
                Surface(
                    onClick = { onScaleFactorChange(factor) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LrSliderRow(
            title = "Continuous Scale",
            value = scaleFactor * 100f,
            displayValue = "${(scaleFactor * 100).toInt()}%",
            valueRange = 25f..250f,
            minLabel = "25%",
            maxLabel = "250%",
            onDragStart = {},
            onValueChange = { onScaleFactorChange(it / 100f) },
            onValueChangeFinished = {}
        )
    }
}
