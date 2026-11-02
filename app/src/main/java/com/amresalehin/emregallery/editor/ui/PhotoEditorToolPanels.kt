package com.amresalehin.emregallery.editor.ui

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*
import java.util.Locale
import kotlin.math.abs

/**
 * Top floating HUD capsule displaying the actively dragging slider and its value.
 * Calm, monochrome pill floating over the photo canvas.
 */
@Composable
fun SliderHudBanner(
    sliderName: String,
    sliderValue: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = Color(0xEE161616),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier.testTag("slider_hud_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sliderName,
                color = Color(0xFFAAAAAA),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = sliderValue,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}






/**
 * Primary editing tool categories in Lightcraft & Emre Gallery:
 * [Adjust], [Presets], [Crop], [Masking], [Healing]
 */
enum class MainBottomTab {
    ADJUST, PRESETS, CROP, DRAW, MASKING, HEALING, OVERLAYS, AI_TOOLS
}



/**
 * Emre precision slider row.
 * Features:
 * - Label on the left with double-tap to reset & visual Reset ↺ button when modified
 * - Center zero-centered slider for bipolar parameters, center notch tick, and range bounds
 * - Numeric value on the right in monospaced font
 * - Tapping numeric value opens exact numeric input dialog
 * - Double-tap label or numeric value resets to default
 * - Haptic feedback on zero snap/cross and reset
 * - Drag-specific visual transitions with spring animation
 */
@Composable
fun LrSliderRow(
    title: String,
    value: Float,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    gradientBrush: Brush? = null,
    activeTrackColor: Color = Color.Unspecified,
    onDragStart: () -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    defaultValue: Float = if (valueRange.start < 0f && valueRange.endInclusive > 0f) 0f else valueRange.start,
    isEv: Boolean = false,
    unit: String = "",
    minLabel: String? = null,
    maxLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val colors = LocalEmreEditorColors.current
    val resolvedActiveTrackColor = if (activeTrackColor != Color.Unspecified) activeTrackColor else colors.activeControlBg
    val isBipolar = valueRange.start < 0f && valueRange.endInclusive > 0f
    val isModified = abs(value - defaultValue) > (if (isEv) 0.05f else 0.4f)
    val isNearZero = isBipolar && abs(value) <= (if (isEv) 0.8f else 1.2f)

    val lastSign = remember { mutableIntStateOf(if (value > 0.05f) 1 else if (value < -0.05f) -1 else 0) }

    val resetToDefault: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onDragStart()
        onValueChange(defaultValue)
        onValueChangeFinished()
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    val trackHeight by animateDpAsState(
        targetValue = if (isDragged) 5.dp else 3.5.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "slider_track_height"
    )

    // Compute bounds labels
    val effectiveMinLabel = minLabel ?: if (isBipolar) {
        if (isEv) "−5.00" else "${valueRange.start.toInt()}"
    } else {
        if (valueRange.endInclusive <= 3f) String.format(Locale.US, "%.1f", valueRange.start) else "${valueRange.start.toInt()}"
    }
    val effectiveMaxLabel = maxLabel ?: if (isBipolar) {
        if (isEv) "+5.00" else "+${valueRange.endInclusive.toInt()}"
    } else {
        if (valueRange.endInclusive <= 3f) String.format(Locale.US, "%.1f", valueRange.endInclusive) else "+${valueRange.endInclusive.toInt()}"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Parameter label with double-tap to reset + Reset ↺ button when modified
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .width(96.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { resetToDefault() })
                }
        ) {
            Text(
                text = title,
                color = if (isModified) colors.primaryText else colors.secondaryText,
                fontSize = 13.sp,
                fontWeight = if (isModified) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isModified) {
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = { resetToDefault() },
                    modifier = Modifier
                        .size(20.dp)
                        .testTag("reset_slider_$title")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset $title",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        // 2. Middle Slider Track: Zero-centered with custom Canvas and bounds
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentAlignment = Alignment.Center
            ) {
                // Custom Canvas Track
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                ) {
                    val thumbRadiusPx = 10.dp.toPx()
                    val trackStartX = thumbRadiusPx
                    val trackEndX = size.width - thumbRadiusPx
                    val trackWidth = (trackEndX - trackStartX).coerceAtLeast(1f)
                    val centerY = size.height / 2f
                    val strokeH = trackHeight.toPx()

                    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                    val currentX = trackStartX + fraction * trackWidth

                    val zeroFraction = if (isBipolar) {
                        ((0f - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                    } else 0f
                    val zeroX = trackStartX + zeroFraction * trackWidth

                    // Draw Inactive background track
                    if (gradientBrush != null) {
                        drawLine(
                            brush = gradientBrush,
                            start = Offset(trackStartX, centerY),
                            end = Offset(trackEndX, centerY),
                            strokeWidth = strokeH,
                            cap = StrokeCap.Round
                        )
                    } else {
                        val inactiveColor = if (colors.isDark) Color(0xFF2C2C2E) else Color(0xFFE2E2E2)
                        drawLine(
                            color = inactiveColor,
                            start = Offset(trackStartX, centerY),
                            end = Offset(trackEndX, centerY),
                            strokeWidth = strokeH,
                            cap = StrokeCap.Round
                        )

                        // Draw Active Fill
                        if (isBipolar) {
                            if (currentX > zeroX) {
                                drawLine(
                                    color = resolvedActiveTrackColor,
                                    start = Offset(zeroX, centerY),
                                    end = Offset(currentX, centerY),
                                    strokeWidth = strokeH,
                                    cap = StrokeCap.Round
                                )
                            } else if (currentX < zeroX) {
                                drawLine(
                                    color = resolvedActiveTrackColor,
                                    start = Offset(currentX, centerY),
                                    end = Offset(zeroX, centerY),
                                    strokeWidth = strokeH,
                                    cap = StrokeCap.Round
                                )
                            }
                        } else {
                            drawLine(
                                color = resolvedActiveTrackColor,
                                start = Offset(trackStartX, centerY),
                                end = Offset(currentX, centerY),
                                strokeWidth = strokeH,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Draw Center Tick Notch for bipolar sliders
                    if (isBipolar) {
                        val tickColor = if (isNearZero) colors.primaryText else colors.secondaryText
                        drawLine(
                            color = tickColor,
                            start = Offset(zeroX, centerY - 5.dp.toPx()),
                            end = Offset(zeroX, centerY + 5.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Interactive Slider
                Slider(
                    value = value,
                    onValueChange = { raw ->
                        onDragStart()
                        val snapVal = if (isBipolar && abs(raw) < (if (isEv) 0.8f else 1.2f)) 0f else raw
                        val currentSign = if (snapVal > 0.05f) 1 else if (snapVal < -0.05f) -1 else 0
                        if (currentSign != lastSign.intValue) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastSign.intValue = currentSign
                        }
                        onValueChange(snapVal)
                    },
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = valueRange,
                    interactionSource = interactionSource,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.activeControlBg,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Subtle bounds tick labels underneath track: min, 0, max
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = effectiveMinLabel,
                    color = colors.secondaryText,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.2).sp
                )
                if (isBipolar) {
                    Text(
                        text = "0",
                        color = if (isNearZero) colors.primaryText else colors.secondaryText,
                        fontSize = 9.sp,
                        fontWeight = if (isNearZero) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = effectiveMaxLabel,
                    color = colors.secondaryText,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.2).sp
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        // 3. Right numeric value indicator: Tap or click to reset to default
        Surface(
            onClick = resetToDefault,
            shape = RoundedCornerShape(8.dp),
            color = if (isModified) colors.elevatedSurface else colors.panelSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .width(68.dp)
                .testTag("slider_value_$title")
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayValue,
                    color = if (isModified) colors.primaryText else colors.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 1. LIGHT PANEL
 * Clean monochrome sliders + Curves quick access pill.
 */
@Composable
fun LightPanel(
    exposure: Float,
    contrast: Float,
    highlights: Float,
    shadows: Float,
    whites: Float,
    blacks: Float,
    onOpenCurve: () -> Unit = {},
    onSliderDragStart: (name: String, formattedVal: String) -> Unit,
    onSliderChange: (prop: String, value: Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalEmreEditorColors.current.panelSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {

        // Exposure: -5.00 .. +5.00 EV
        val expEv = exposure / 20f
        val expStr = when {
            expEv > 0.001f -> "+%.2f EV".format(expEv)
            expEv < -0.001f -> "−%.2f EV".format(-expEv)
            else -> "0.00 EV"
        }
        LrSliderRow(
            title = "Exposure",
            value = exposure,
            displayValue = expStr,
            valueRange = -100f..100f,
            isEv = true,
            unit = "EV",
            minLabel = "−5.00",
            maxLabel = "+5.00",
            onDragStart = { onSliderDragStart("Exposure", expStr) },
            onValueChange = { onSliderChange("Exposure", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        // Contrast
        val contrastStr = when {
            contrast > 0f -> "+${contrast.toInt()}"
            contrast < 0f -> "−${(-contrast).toInt()}"
            else -> "0"
        }
        LrSliderRow(
            title = "Contrast",
            value = contrast,
            displayValue = contrastStr,
            valueRange = -100f..100f,
            minLabel = "−100",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Contrast", contrastStr) },
            onValueChange = { onSliderChange("Contrast", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        // Highlights
        val highStr = when {
            highlights > 0f -> "+${highlights.toInt()}"
            highlights < 0f -> "−${(-highlights).toInt()}"
            else -> "0"
        }
        LrSliderRow(
            title = "Highlights",
            value = highlights,
            displayValue = highStr,
            valueRange = -100f..100f,
            minLabel = "−100",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Highlights", highStr) },
            onValueChange = { onSliderChange("Highlights", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        // Shadows
        val shadowStr = when {
            shadows > 0f -> "+${shadows.toInt()}"
            shadows < 0f -> "−${(-shadows).toInt()}"
            else -> "0"
        }
        LrSliderRow(
            title = "Shadows",
            value = shadows,
            displayValue = shadowStr,
            valueRange = -100f..100f,
            minLabel = "−100",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Shadows", shadowStr) },
            onValueChange = { onSliderChange("Shadows", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        // Whites
        val whitesStr = when {
            whites > 0f -> "+${whites.toInt()}"
            whites < 0f -> "−${(-whites).toInt()}"
            else -> "0"
        }
        LrSliderRow(
            title = "Whites",
            value = whites,
            displayValue = whitesStr,
            valueRange = -100f..100f,
            minLabel = "−100",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Whites", whitesStr) },
            onValueChange = { onSliderChange("Whites", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        // Blacks
        val blacksStr = when {
            blacks > 0f -> "+${blacks.toInt()}"
            blacks < 0f -> "−${(-blacks).toInt()}"
            else -> "0"
        }
        LrSliderRow(
            title = "Blacks",
            value = blacks,
            displayValue = blacksStr,
            valueRange = -100f..100f,
            minLabel = "−100",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Blacks", blacksStr) },
            onValueChange = { onSliderChange("Blacks", it) },
            onValueChangeFinished = onSliderChangeFinished
        )
    }
}



/**
 * 3. COLOR PANEL
 * Monochrome controls with B&W, Mix, Grading and WB sliders.
 */
@Composable
fun ColorPanel(
    isBlackAndWhite: Boolean,
    whiteBalancePreset: String,
    temperature: Float,
    tint: Float,
    vibrance: Float,
    saturation: Float,
    onToggleBw: () -> Unit,
    onOpenGrading: () -> Unit = {},
    onOpenMix: () -> Unit = {},
    onSelectWbPreset: (String) -> Unit,
    onSliderDragStart: (name: String, formattedVal: String) -> Unit,
    onSliderChange: (prop: String, value: Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showWbMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalEmreEditorColors.current.panelSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Black & White Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Black & White",
                color = Color(0xFFCCCCCC),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = isBlackAndWhite,
                onCheckedChange = { onToggleBw() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2979FF),
                    uncheckedThumbColor = Color(0xFFAAAAAA),
                    uncheckedTrackColor = Color(0xFF222222)
                ),
                modifier = Modifier.testTag("color_bw_switch")
            )
        }

        Spacer(Modifier.height(6.dp))

        // White balance selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "White balance",
                color = Color(0xFFCCCCCC),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Box {
                Surface(
                    onClick = { showWbMenu = true },
                    shape = CircleShape,
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = whiteBalancePreset,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showWbMenu,
                    onDismissRequest = { showWbMenu = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    val presets = listOf("As Shot", "Daylight", "Cloudy", "Shade", "Tungsten", "Fluorescent", "Flash", "Custom")
                    presets.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p, color = Color.White) },
                            onClick = {
                                showWbMenu = false
                                onSelectWbPreset(p)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Temp slider with custom white balance Kelvin scaling
        val tempBrush = Brush.horizontalGradient(
            colors = listOf(Color(0xFF2979FF), Color(0xFF90CAF9), Color(0xFFFFD54F), Color(0xFFFF9100))
        )
        val kelvin = (5500 + (temperature * 38)).toInt()
        val tempStr = when {
            temperature > 0f -> "+${temperature.toInt()} (${kelvin}K)"
            temperature < 0f -> "−${(-temperature).toInt()} (${kelvin}K)"
            else -> "0 (${kelvin}K)"
        }
        LrSliderRow(
            title = "Temp",
            value = temperature,
            displayValue = tempStr,
            valueRange = -100f..100f,
            gradientBrush = tempBrush,
            minLabel = "2000K",
            maxLabel = "9500K",
            onDragStart = { onSliderDragStart("Temp", tempStr) },
            onValueChange = { onSliderChange("Temp", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        // Tint slider
        val tintBrush = Brush.horizontalGradient(
            colors = listOf(Color(0xFF00E676), Color(0xFFA5D6A7), Color(0xFFF48FB1), Color(0xFFE040FB))
        )
        val tintStr = when {
            tint > 0f -> "+${tint.toInt()}"
            tint < 0f -> "−${(-tint).toInt()}"
            else -> "0"
        }
        LrSliderRow(
            title = "Tint",
            value = tint,
            displayValue = tintStr,
            valueRange = -100f..100f,
            gradientBrush = tintBrush,
            minLabel = "−100",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Tint", tintStr) },
            onValueChange = { onSliderChange("Tint", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        // Vibrance
        val vibStr = when {
            vibrance > 0f -> "+${vibrance.toInt()}"
            vibrance < 0f -> "−${(-vibrance).toInt()}"
            else -> "0"
        }
        LrSliderRow(
            title = "Vibrance",
            value = vibrance,
            displayValue = vibStr,
            valueRange = -100f..100f,
            minLabel = "−100",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Vibrance", vibStr) },
            onValueChange = { onSliderChange("Vibrance", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        // Saturation
        val satStr = when {
            saturation > 0f -> "+${saturation.toInt()}"
            saturation < 0f -> "−${(-saturation).toInt()}"
            else -> "0"
        }
        LrSliderRow(
            title = "Saturation",
            value = saturation,
            displayValue = satStr,
            valueRange = -100f..100f,
            minLabel = "−100",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Saturation", satStr) },
            onValueChange = { onSliderChange("Saturation", it) },
            onValueChangeFinished = onSliderChangeFinished
        )
    }
}





@Composable
fun EffectsPanel(
    texture: Float,
    clarity: Float,
    dehaze: Float,
    vignette: Float,
    grain: Float,
    dodge: Float = 0f,
    burn: Float = 0f,
    vignetteMidpoint: Float = 50f,
    vignetteFeather: Float = 50f,
    vignetteRoundness: Float = 0f,
    grainSize: Float = 25f,
    grainRoughness: Float = 50f,
    onSliderDragStart: (name: String, formattedVal: String) -> Unit,
    onSliderChange: (prop: String, value: Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalEmreEditorColors.current.panelSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val texStr = when {
            texture > 0f -> "+${texture.toInt()}"
            texture < 0f -> "−${(-texture).toInt()}"
            else -> "0"
        }
        LrSliderRow(
            title = "Texture",
            value = texture,
            displayValue = texStr,
            valueRange = -100f..100f,
            minLabel = "−100",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Texture", texStr) },
            onValueChange = { onSliderChange("Texture", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        val clarStr = when {
            clarity > 0f -> "+${clarity.toInt()}"
            clarity < 0f -> "−${(-clarity).toInt()}"
            else -> "0"
        }
        LrSliderRow(
            title = "Clarity",
            value = clarity,
            displayValue = clarStr,
            valueRange = -100f..100f,
            minLabel = "−100",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Clarity", clarStr) },
            onValueChange = { onSliderChange("Clarity", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        val dehazeStr = when {
            dehaze > 0f -> "+${dehaze.toInt()}"
            dehaze < 0f -> "−${(-dehaze).toInt()}"
            else -> "0"
        }
        LrSliderRow(
            title = "Dehaze",
            value = dehaze,
            displayValue = dehazeStr,
            valueRange = -100f..100f,
            minLabel = "−100",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Dehaze", dehazeStr) },
            onValueChange = { onSliderChange("Dehaze", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        val grainStr = "${grain.toInt()}"
        LrSliderRow(
            title = "Grain",
            value = grain,
            displayValue = grainStr,
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Grain", grainStr) },
            onValueChange = { onSliderChange("Grain", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        val sizeStr = "${grainSize.toInt()}"
        LrSliderRow(
            title = "Grain Size",
            value = grainSize,
            displayValue = sizeStr,
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Grain Size", sizeStr) },
            onValueChange = { onSliderChange("grainSize", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        val roughStr = "${grainRoughness.toInt()}"
        LrSliderRow(
            title = "Grain Roughness",
            value = grainRoughness,
            displayValue = roughStr,
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Grain Roughness", roughStr) },
            onValueChange = { onSliderChange("grainRoughness", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        val dodgeStr = "${dodge.toInt()}"
        LrSliderRow(
            title = "Global Dodge",
            value = dodge,
            displayValue = dodgeStr,
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Dodge", dodgeStr) },
            onValueChange = { onSliderChange("Dodge", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        val burnStr = "${burn.toInt()}"
        LrSliderRow(
            title = "Global Burn",
            value = burn,
            displayValue = burnStr,
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Burn", burnStr) },
            onValueChange = { onSliderChange("Burn", it) },
            onValueChangeFinished = onSliderChangeFinished
        )
    }
}

/**
 * 5. DETAIL PANEL
 * Clean monochrome sharpening, noise and color noise controls.
 */
@Composable
fun DetailPanel(
    sharpness: Float,
    radius: Float,
    detail: Float,
    noiseReduction: Float,
    colorNoiseReduction: Float,
    onSliderDragStart: (name: String, formattedVal: String) -> Unit,
    onSliderChange: (prop: String, value: Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalEmreEditorColors.current.panelSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val amountStr = if (sharpness > 0) "+${sharpness.toInt()}" else "${sharpness.toInt()}"
        LrSliderRow(
            title = "Sharpening",
            value = sharpness,
            displayValue = amountStr,
            valueRange = 0f..150f,
            minLabel = "0",
            maxLabel = "+150",
            activeTrackColor = Color.White,
            onDragStart = { onSliderDragStart("Sharpening", amountStr) },
            onValueChange = { onSliderChange("Sharpening", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        val radStr = "%.2f px".format(radius)
        LrSliderRow(
            title = "Radius",
            value = radius,
            displayValue = radStr,
            valueRange = 0.5f..3.0f,
            unit = "px",
            minLabel = "0.50",
            maxLabel = "3.00",
            onDragStart = { onSliderDragStart("Radius", radStr) },
            onValueChange = { onSliderChange("Radius", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        val detStr = "+${detail.toInt()}"
        LrSliderRow(
            title = "Detail",
            value = detail,
            displayValue = detStr,
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Detail", detStr) },
            onValueChange = { onSliderChange("Detail", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        val noiseStr = "${noiseReduction.toInt()}"
        LrSliderRow(
            title = "Noise Reduction",
            value = noiseReduction,
            displayValue = noiseStr,
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Noise Reduction", noiseStr) },
            onValueChange = { onSliderChange("Noise Reduction", it) },
            onValueChangeFinished = onSliderChangeFinished
        )

        val colorNoiseStr = "${colorNoiseReduction.toInt()}"
        LrSliderRow(
            title = "Color Noise",
            value = colorNoiseReduction,
            displayValue = colorNoiseStr,
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "+100",
            onDragStart = { onSliderDragStart("Color Noise", colorNoiseStr) },
            onValueChange = { onSliderChange("Color noise", it) },
            onValueChangeFinished = onSliderChangeFinished
        )
    }
}





/**
 * 7. PROFILES & PRESETS
 * Clean monochrome preview cards and group selection.
 */
val PresetTier3Tabs = listOf(
    "SIMPLE",
    "FX",
    "B&W",
    "BLUR",
    "SKETCH",
    "COLORS",
    "ARTISTIC",
    "MAGIC",
    "CANVAS"
)

val PresetBlendModes = listOf(
    "Normal",
    "Multiply",
    "Color Burn",
    "Darken",
    "Lighten",
    "Screen",
    "Overlay"
)

fun generatePresetsForCategory(cat: String): List<ProfilePreset> = when (cat.uppercase()) {
    "SIMPLE" -> listOf(
        ProfilePreset("simple_none", "None", "SIMPLE", FilterType.NONE),
        ProfilePreset("simple_dodger", "Dodger", "SIMPLE", FilterType.DODGER),
        ProfilePreset("simple_brl2", "BRL2", "SIMPLE", FilterType.BRL2),
        ProfilePreset("simple_brl4", "BRL4", "SIMPLE", FilterType.BRL4),
        ProfilePreset("simple_apr2", "APR2", "SIMPLE", FilterType.APR2),
        ProfilePreset("simple_warm_color", "Warm Color", "SIMPLE", FilterType.WARM_COLOR),
        ProfilePreset("simple_light_cross", "Light Cross", "SIMPLE", FilterType.LIGHT_CROSS, contrastOffset = 50f, brightnessOffset = 50f, fadeOffset = 25f),
        ProfilePreset("simple_crisp", "Crisp", "SIMPLE", FilterType.CRISP),
        ProfilePreset("simple_sharpen", "Sharpen", "SIMPLE", FilterType.SHARPEN),
        ProfilePreset("simple_vibrant", "Vibrant", "SIMPLE", FilterType.VIVID),
        ProfilePreset("simple_retro", "Retro", "SIMPLE", FilterType.RETRO),
        ProfilePreset("simple_sunny", "Sunny", "SIMPLE", FilterType.SUNNY, fadeOffset = 0f),
        ProfilePreset("simple_vintage", "Vintage", "SIMPLE", FilterType.VINTAGE),
        ProfilePreset("simple_warm_amber", "Warm Amber", "SIMPLE", FilterType.WARM_AMBER)
    )
    "FX" -> listOf(
        ProfilePreset("fx_none", "None", "FX", FilterType.NONE),
        ProfilePreset("fx_hdr1", "HDR1", "FX", FilterType.HDR1),
        ProfilePreset("fx_noise", "Noise", "FX", FilterType.NOISE),
        ProfilePreset("fx_film", "Film", "FX", FilterType.FILM),
        ProfilePreset("fx_film2", "Film2", "FX", FilterType.FILM2),
        ProfilePreset("fx_film3", "Film3", "FX", FilterType.FILM3),
        ProfilePreset("fx_holga2", "Holga2", "FX", FilterType.HOLGA2),
        ProfilePreset("fx_vnyl3", "VNYL3", "FX", FilterType.VNYL3),
        ProfilePreset("fx_indie1", "INDIE1", "FX", FilterType.INDIE1, isNew = true),
        ProfilePreset("fx_glitch", "Glitch", "FX", FilterType.GLITCH),
        ProfilePreset("fx_cyber", "Cyber", "FX", FilterType.CYBER)
    )
    "B&W" -> listOf(
        ProfilePreset("bw_none", "None", "B&W", FilterType.NONE),
        ProfilePreset("bw_classic", "B&W", "B&W", FilterType.BW),
        ProfilePreset("bw_hicon", "B&W HiCon", "B&W", FilterType.BW_HICON),
        ProfilePreset("bw_lowcon", "B&W LowCon", "B&W", FilterType.BW_LOWCON),
        ProfilePreset("bw_filmbw", "Film B&W", "B&W", FilterType.FILM_BW),
        ProfilePreset("bw_noir", "Noir", "B&W", FilterType.NOIR),
        ProfilePreset("bw_sepia", "Sepia", "B&W", FilterType.SEPIA)
    )
    "BLUR" -> listOf(
        ProfilePreset("blur_none", "None", "BLUR", FilterType.NONE),
        ProfilePreset("blur_blur", "Blur", "BLUR", FilterType.BLUR),
        ProfilePreset("blur_focal", "Focal Zoom", "BLUR", FilterType.FOCAL_ZOOM),
        ProfilePreset("blur_radial", "Radial Blur", "BLUR", FilterType.RADIAL_BLUR),
        ProfilePreset("blur_motion", "Motion Blur", "BLUR", FilterType.MOTION_BLUR),
        ProfilePreset("blur_smart", "Smart Blur", "BLUR", FilterType.SMART_BLUR)
    )
    "SKETCH" -> listOf(
        ProfilePreset("sketch_none", "None", "SKETCH", FilterType.NONE),
        ProfilePreset("sketch_cartoon1", "Cartoon1", "SKETCH", FilterType.CARTOON1),
        ProfilePreset("sketch_cartoon2", "Cartoon2", "SKETCH", FilterType.CARTOON2),
        ProfilePreset("sketch_sktch1", "SKTCH1", "SKETCH", FilterType.SKTCH1),
        ProfilePreset("sketch_pencil", "Pencil", "SKETCH", FilterType.PENCIL_SKETCH),
        ProfilePreset("sketch_ink", "Ink Contour", "SKETCH", FilterType.INK_CONTOUR)
    )
    "COLORS" -> listOf(
        ProfilePreset("colors_none", "None", "COLORS", FilterType.NONE),
        ProfilePreset("colors_splash", "Color Splash", "COLORS", FilterType.COLOR_SPLASH),
        ProfilePreset("colors_hue", "Hue Shift", "COLORS", FilterType.HUE_SHIFT),
        ProfilePreset("colors_invert", "Invert", "COLORS", FilterType.INVERT),
        ProfilePreset("colors_colorize", "Colorize", "COLORS", FilterType.COLORIZE),
        ProfilePreset("colors_duotone", "Duotone", "COLORS", FilterType.VIVID),
        ProfilePreset("colors_popart", "Pop Art", "COLORS", FilterType.POP_ART)
    )
    "ARTISTIC" -> listOf(
        ProfilePreset("art_none", "None", "ARTISTIC", FilterType.NONE),
        ProfilePreset("art_cnv1", "CNV1", "ARTISTIC", FilterType.CNV1),
        ProfilePreset("art_oil", "OilPainting", "ARTISTIC", FilterType.OIL_PAINTING),
        ProfilePreset("art_polygon", "Polygon", "ARTISTIC", FilterType.POLYGON),
        ProfilePreset("art_poster", "Poster", "ARTISTIC", FilterType.POSTER),
        ProfilePreset("art_halftone", "Halftone Dots", "ARTISTIC", FilterType.HALFTONE_DOTS),
        ProfilePreset("art_water", "Watercolor", "ARTISTIC", FilterType.WATERCOLOR),
        ProfilePreset("art_impressionist", "Impressionist", "ARTISTIC", FilterType.OIL_PAINTING)
    )
    "MAGIC" -> listOf(
        ProfilePreset("magic_none", "None", "MAGIC", FilterType.NONE),
        ProfilePreset("magic_flora", "Flora", "MAGIC", FilterType.FLORA),
        ProfilePreset("magic_pastel", "Pastel", "MAGIC", FilterType.PASTEL),
        ProfilePreset("magic_undead", "Undead", "MAGIC", FilterType.UNDEAD),
        ProfilePreset("magic_ice", "White Ice", "MAGIC", FilterType.WHITE_ICE),
        ProfilePreset("magic_geode", "Geode", "MAGIC", FilterType.GEODE),
        ProfilePreset("magic_sketchy", "Sketchy", "MAGIC", FilterType.SKETCHY),
        ProfilePreset("magic_pow", "Pow", "MAGIC", FilterType.POW),
        ProfilePreset("magic_flare", "Flare", "MAGIC", FilterType.FLARE),
        ProfilePreset("magic_animation", "Animation", "MAGIC", FilterType.ANIMATION)
    )
    "CANVAS" -> listOf(
        ProfilePreset("canvas_none", "None", "CANVAS", FilterType.NONE),
        ProfilePreset("canvas_1", "Canvas 1", "CANVAS", FilterType.CANVAS1),
        ProfilePreset("canvas_2", "Canvas 2", "CANVAS", FilterType.CANVAS2),
        ProfilePreset("canvas_grainy", "Grainy Canvas", "CANVAS", FilterType.GRAINY_CANVAS),
        ProfilePreset("canvas_linen", "Linen", "CANVAS", FilterType.LINEN),
        ProfilePreset("canvas_paper", "Paper", "CANVAS", FilterType.PAPER),
        ProfilePreset("canvas_grunge", "Grunge", "CANVAS", FilterType.GRUNGE)
    )
    else -> listOf(
        ProfilePreset("def_none", "None", cat, FilterType.NONE),
        ProfilePreset("def_vivid", "Vivid", cat, FilterType.VIVID)
    )
}

/**
 * 7. PROFILES & PRESETS (TIER 2 & TIER 3 ARCHITECTURE)
 *
 * Tier 3: Bottom horizontal category strip:
 * [SIMPLE] [FX] [B&W] [BLUR] [SKETCH] [COLORS] [ARTISTIC] [MAGIC] [CANVAS]
 *
 * Tier 2: Directly above Tier 3, horizontal carousel of thumbnail preview cards
 * with live filter styling, title strip, cyan active selection border, and tune icon.
 *
 * Above Tier 2: Interactive slider controls (Contrast, Brightness, Fade) and Blend Modes
 * as displayed in Screenshots 11 & 12 when an adjustable preset is selected.
 */
@Composable
fun ProfilesPanel(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onApplyProfile: (ProfilePreset) -> Unit,
    onClose: () -> Unit = {},
    onConfirm: () -> Unit = {},
    modifier: Modifier = Modifier,
    thumbnailBitmap: Bitmap? = null,
    onPresetAdjust: ((contrast: Float, brightness: Float, fade: Float, blendMode: String) -> Unit)? = null
) {
    // Current category (defaults to SIMPLE)
    val activeCat = if (PresetTier3Tabs.any { it.equals(selectedCategory, ignoreCase = true) }) {
        selectedCategory.uppercase()
    } else {
        "SIMPLE"
    }

    var activeProfileId by remember(activeCat) {
        val initialList = generatePresetsForCategory(activeCat)
        mutableStateOf(initialList.firstOrNull()?.id ?: "")
    }

    // Interactive slider & blend adjustments for active preset
    var showAdjustSliders by remember { mutableStateOf(false) }
    var contrastValue by remember { mutableFloatStateOf(50f) }
    var brightnessValue by remember { mutableFloatStateOf(50f) }
    var fadeValue by remember { mutableFloatStateOf(0f) }
    var activeBlendMode by remember { mutableStateOf("Normal") }

    val presets = remember(activeCat) { generatePresetsForCategory(activeCat) }
    val currentPreset = presets.find { it.id == activeProfileId }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F0F))
    ) {
        // --- PRESET ADJUSTMENT PANEL (Above Tier 2) ---
        // Visible when an active preset (non-None) is selected, matching screenshots 11 and 12
        if (showAdjustSliders && currentPreset != null && currentPreset.filterType != FilterType.NONE) {
            PresetAdjustmentsPanel(
                presetName = currentPreset.name,
                hasMultiSliders = currentPreset.name.equals("Light Cross", ignoreCase = true) ||
                                 currentPreset.contrastOffset > 0f ||
                                 currentPreset.brightnessOffset > 0f,
                contrast = contrastValue,
                brightness = brightnessValue,
                fade = fadeValue,
                activeBlendMode = activeBlendMode,
                onContrastChange = {
                    contrastValue = it
                    onPresetAdjust?.invoke(contrastValue, brightnessValue, fadeValue, activeBlendMode)
                },
                onBrightnessChange = {
                    brightnessValue = it
                    onPresetAdjust?.invoke(contrastValue, brightnessValue, fadeValue, activeBlendMode)
                },
                onFadeChange = {
                    fadeValue = it
                    onPresetAdjust?.invoke(contrastValue, brightnessValue, fadeValue, activeBlendMode)
                },
                onBlendModeSelect = {
                    activeBlendMode = it
                    onPresetAdjust?.invoke(contrastValue, brightnessValue, fadeValue, activeBlendMode)
                }
            )
        }

        // --- TIER 2: PRESET ITEMS CAROUSEL ---
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets, key = { it.id }) { preset ->
                val isSelected = activeProfileId == preset.id
                PresetPreviewCard(
                    preset = preset,
                    isSelected = isSelected,
                    showTuneIcon = isSelected && preset.filterType != FilterType.NONE,
                    thumbnailBitmap = thumbnailBitmap,
                    onClick = {
                        if (activeProfileId == preset.id && preset.filterType != FilterType.NONE) {
                            // Tapping an already selected preset toggles the adjustment panel
                            showAdjustSliders = !showAdjustSliders
                        } else {
                            activeProfileId = preset.id
                            // Set initial values based on preset defaults
                            contrastValue = if (preset.contrastOffset > 0f) preset.contrastOffset else 50f
                            brightnessValue = if (preset.brightnessOffset > 0f) preset.brightnessOffset else 50f
                            fadeValue = preset.fadeOffset
                            activeBlendMode = preset.blendMode
                            showAdjustSliders = preset.filterType != FilterType.NONE
                            onApplyProfile(preset)
                            onPresetAdjust?.invoke(contrastValue, brightnessValue, fadeValue, activeBlendMode)
                        }
                    }
                )
            }
        }

        // --- TIER 3: BOTTOM CATEGORY STRIP ---
        PresetTier3CategoryStrip(
            categories = PresetTier3Tabs,
            activeCategory = activeCat,
            onSelectCategory = { cat ->
                onSelectCategory(cat)
                val newPresets = generatePresetsForCategory(cat)
                val firstPreset = newPresets.firstOrNull()
                if (firstPreset != null) {
                    activeProfileId = firstPreset.id
                    showAdjustSliders = false
                    onApplyProfile(firstPreset)
                }
            }
        )
    }
}

/**
 * Tier 3 Category Navigation Strip.
 * Scrollable row of ALL CAPS category text tabs:
 * [SIMPLE] [FX] [B&W] [BLUR] [SKETCH] [COLORS] [ARTISTIC] [MAGIC] [CANVAS]
 */
@Composable
fun PresetTier3CategoryStrip(
    categories: List<String>,
    activeCategory: String,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0D0D0D),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(categories) { cat ->
                val isSelected = cat.equals(activeCategory, ignoreCase = true)
                Text(
                    text = cat,
                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFDDDDDD),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSelectCategory(cat)
                        }
                        .testTag("preset_tier3_${cat.lowercase()}")
                )
            }
        }
    }
}

/**
 * Tier 2 Preset Thumbnail Preview Card.
 * Displays preview image of the photo with preset matrix applied live, cyan selection border,
 * tune sliders icon overlay when active, NEW badge, and dark label strip.
 */
@Composable
fun PresetPreviewCard(
    preset: ProfilePreset,
    isSelected: Boolean,
    showTuneIcon: Boolean,
    thumbnailBitmap: Bitmap? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(6.dp)
            )
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF161616))
            .testTag("preset_card_${preset.id}")
    ) {
        // Thumbnail preview box displaying actual photo with preset applied
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Color(0xFF1C1C1E))
        ) {
            if (thumbnailBitmap != null) {
                val matrix = remember(preset.filterType) {
                    PhotoProcessor.getFilterMatrix(preset.filterType)
                }
                val colorFilter = remember(matrix) {
                    matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it.array)) }
                }
                Image(
                    bitmap = thumbnailBitmap.asImageBitmap(),
                    contentDescription = preset.name,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (preset.filterType == FilterType.NONE) null else colorFilter,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF242424))
                )
            }

            // "NEW" Badge (Screenshot 10 - INDIE1)
            if (preset.isNew) {
                Surface(
                    color = Color(0xFFD81B60),
                    shape = RoundedCornerShape(topEnd = 6.dp, bottomStart = 4.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "NEW",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // Tune Sliders Icon on selected card (Screenshots 11 & 12)
            if (showTuneIcon) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Adjust preset",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Bottom label strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(if (isSelected) Color(0xFF181818) else Color(0xFF121212))
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = preset.name,
                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Adjustment Sliders & Blend Modes Bar
 * Replicates the exact fine-tuning controls shown in Screenshots 11 and 12:
 * Contrast (50), Brightness (50), Fade (25/0) +
 * Blend modes: Normal, Multiply, Color Burn, Darken, Lighten, Screen, Overlay
 */
@Composable
fun PresetAdjustmentsPanel(
    presetName: String,
    hasMultiSliders: Boolean,
    contrast: Float,
    brightness: Float,
    fade: Float,
    activeBlendMode: String,
    onContrastChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onFadeChange: (Float) -> Unit,
    onBlendModeSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Multi-sliders (Contrast, Brightness, Fade) or Single Fade Slider
        if (hasMultiSliders) {
            PresetSliderRow(
                label = "Contrast",
                value = contrast,
                onValueChange = onContrastChange
            )
            Spacer(Modifier.height(4.dp))
            PresetSliderRow(
                label = "Brightness",
                value = brightness,
                onValueChange = onBrightnessChange
            )
            Spacer(Modifier.height(4.dp))
        }

        // Fade Slider (Screenshots 11 & 12)
        PresetSliderRow(
            label = "Fade",
            value = fade,
            onValueChange = onFadeChange
        )

        Spacer(Modifier.height(8.dp))

        // Blend Modes Row (Normal, Multiply, Color Burn, Darken, Lighten, Screen, Overlay)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(PresetBlendModes) { mode ->
                val isSelected = mode.equals(activeBlendMode, ignoreCase = true)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onBlendModeSelect(mode)
                    }
                ) {
                    Text(
                        text = mode,
                        color = if (isSelected) Color.White else Color(0xFF888888),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(2.dp)
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(72.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF333333)
            ),
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value.toInt().toString(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * 8. CROP & GEOMETRY PANEL
 * Rotate, Flip, Straighten, and Aspect Ratio presets.
 */
@Composable
fun CropControlsPanel(
    selectedPreset: AspectRatioPreset,
    straightenAngle: Float,
    onSelectPreset: (AspectRatioPreset) -> Unit,
    onRotate90: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onStraightenChange: (Float) -> Unit,
    onStraightenFinished: (() -> Unit)? = null,
    onApply: () -> Unit = {},
    onAutoLevel: (() -> Unit)? = null,
    onResetCrop: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalEmreEditorColors.current.panelSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Quick Actions row: Rotate, Flip H, Flip V, Auto-Level
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Surface(
                    onClick = onRotate90,
                    shape = CircleShape,
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, null, Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Rotate 90°", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            item {
                Surface(
                    onClick = onFlipH,
                    shape = CircleShape,
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Flip, null, Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Flip H", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            item {
                Surface(
                    onClick = onFlipV,
                    shape = CircleShape,
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Flip, null, Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Flip V", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Straighten Slider
        val angleStr = when {
            straightenAngle > 0f -> "+${straightenAngle.toInt()}°"
            straightenAngle < 0f -> "−${(-straightenAngle).toInt()}°"
            else -> "0°"
        }
        LrSliderRow(
            title = "Straighten",
            value = straightenAngle,
            displayValue = angleStr,
            valueRange = -45f..45f,
            unit = "°",
            minLabel = "−45°",
            maxLabel = "+45°",
            onDragStart = {},
            onValueChange = onStraightenChange,
            onValueChangeFinished = { onStraightenFinished?.invoke() }
        )

        Spacer(Modifier.height(4.dp))

        // Aspect ratio presets row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AspectRatioPreset.entries) { preset ->
                val isSelected = selectedPreset == preset
                Surface(
                    onClick = { onSelectPreset(preset) },
                    shape = CircleShape,
                    color = if (isSelected) Color.White else Color(0xFF1E1E1E),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Text(
                        text = preset.label,
                        color = if (isSelected) Color.Black else Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

