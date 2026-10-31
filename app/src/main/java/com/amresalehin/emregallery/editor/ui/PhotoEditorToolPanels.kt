package com.amresalehin.emregallery.editor.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
 * Universal Mode Header for consistent Cancel / Apply behavior across editing modes.
 * Displays a clean Cancel (✕), Mode Title, optional Reset, and Apply (✓).
 */
@Composable
fun EditorModeHeader(
    title: String,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onReset: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Cancel button
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(40.dp)
                .testTag("mode_cancel_button")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                tint = Color(0xFFDDDDDD),
                modifier = Modifier.size(20.dp)
            )
        }

        // Mode Title & optional Reset button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.4.sp
            )
            if (onReset != null) {
                Surface(
                    onClick = onReset,
                    shape = CircleShape,
                    color = Color(0xFF222222),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.testTag("mode_reset_button")
                ) {
                    Text(
                        text = "Reset",
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Apply button
        Surface(
            onClick = onApply,
            shape = CircleShape,
            color = Color.White,
            contentColor = Color.Black,
            modifier = Modifier
                .size(36.dp)
                .testTag("mode_apply_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Apply",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Target parameter definition when user taps on any slider's numeric value
 * to open precise numeric text input.
 */
data class NumericInputTarget(
    val title: String,
    val currentValue: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val defaultValue: Float = 0f,
    val isEv: Boolean = false,
    val unit: String = "",
    val onApply: (Float) -> Unit
)

val LocalNumericInputRequester = staticCompositionLocalOf<(NumericInputTarget) -> Unit> { {} }

/**
 * Material 3 Dialog for typing exact numeric values with quick preset chips,
 * validation, and haptic feedback.
 */
@Composable
fun NumericInputDialog(
    target: NumericInputTarget,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val minAllowed = if (target.isEv) target.valueRange.start / 20f else target.valueRange.start
    val maxAllowed = if (target.isEv) target.valueRange.endInclusive / 20f else target.valueRange.endInclusive
    val defaultDisp = if (target.isEv) target.defaultValue / 20f else target.defaultValue

    val initialText = if (target.isEv) {
        String.format(Locale.US, "%.2f", target.currentValue / 20f)
    } else if (target.valueRange.endInclusive <= 5f) {
        String.format(Locale.US, "%.2f", target.currentValue)
    } else {
        target.currentValue.toInt().toString()
    }

    var textInput by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val parsedValue = textInput.toFloatOrNull()
    val isValid = parsedValue != null && parsedValue in minAllowed..maxAllowed
    val helperOrError = when {
        textInput.isBlank() -> "Enter a value"
        parsedValue == null -> "Invalid number"
        parsedValue !in minAllowed..maxAllowed -> {
            val minStr = if (target.isEv) String.format(Locale.US, "%.2f", minAllowed) else minAllowed.toInt().toString()
            val maxStr = if (target.isEv) String.format(Locale.US, "%.2f", maxAllowed) else maxAllowed.toInt().toString()
            "Allowed: $minStr to $maxStr ${target.unit}".trim()
        }
        else -> {
            val minStr = if (target.isEv) String.format(Locale.US, "%.2f", minAllowed) else minAllowed.toInt().toString()
            val maxStr = if (target.isEv) String.format(Locale.US, "%.2f", maxAllowed) else maxAllowed.toInt().toString()
            "Range: $minStr to $maxStr ${target.unit}".trim()
        }
    }

    val applyValue: (Float) -> Unit = { raw ->
        val finalVal = if (target.isEv) raw * 20f else raw
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        target.onApply(finalVal.coerceIn(target.valueRange.start, target.valueRange.endInclusive))
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = target.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                if (target.unit.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF2C2C2E)
                    ) {
                        Text(
                            text = target.unit,
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    singleLine = true,
                    isError = textInput.isNotBlank() && !isValid,
                    supportingText = {
                        Text(
                            text = helperOrError,
                            color = if (textInput.isNotBlank() && !isValid) Color(0xFFFF5252) else Color(0xFF888888),
                            fontSize = 11.sp
                        )
                    },
                    trailingIcon = {
                        if (textInput.isNotEmpty()) {
                            IconButton(onClick = { textInput = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF444444),
                        errorBorderColor = Color(0xFFFF5252),
                        cursorColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("numeric_input_field")
                )

                Spacer(Modifier.height(10.dp))

                // Quick preset chips
                val presets: List<Float> = when {
                    target.isEv -> listOf(-2.0f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 2.0f)
                    minAllowed < 0f && maxAllowed > 0f -> listOf(-100f, -50f, -25f, 0f, 25f, 50f, 100f)
                    target.valueRange.endInclusive == 150f -> listOf(0f, 25f, 50f, 75f, 100f, 150f)
                    target.valueRange.endInclusive <= 3f -> listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
                    else -> listOf(0f, 25f, 50f, 75f, 100f)
                }

                Text(
                    text = "Quick Presets",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { p ->
                        val pStr = if (target.isEv) {
                            String.format(Locale.US, "%.1f", p)
                        } else if (target.valueRange.endInclusive <= 3f) {
                            String.format(Locale.US, "%.1f", p)
                        } else {
                            p.toInt().toString()
                        }
                        Surface(
                            onClick = {
                                textInput = pStr
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            shape = CircleShape,
                            color = Color(0xFF262626),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Text(
                                text = if (p > 0 && target.isEv) "+$pStr" else pStr,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (isValid) applyValue(parsedValue!!) },
                enabled = isValid,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF333333),
                    disabledContentColor = Color(0xFF666666)
                ),
                modifier = Modifier.testTag("numeric_input_apply")
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        val defVal = if (target.isEv) 0.00f else defaultDisp
                        val defStr = if (target.isEv) "0.00" else if (target.valueRange.endInclusive <= 3f) String.format(Locale.US, "%.2f", defVal) else defVal.toInt().toString()
                        textInput = defStr
                    }
                ) {
                    Text("Reset ↺", color = Color(0xFFAAAAAA))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White)
                }
            }
        }
    )
}

/**
 * Minimal single slider strip shown while dragging any slider.
 * Features centered zero position for bipolar values, center notch, bounds and haptics.
 */
@Composable
fun MinimalSingleSliderRow(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    gradientBrush: Brush? = null,
    activeColor: Color = Color.White,
    isEv: Boolean = false,
    minLabel: String? = null,
    maxLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isBipolar = valueRange.start < 0f && valueRange.endInclusive > 0f
    val isNearZero = isBipolar && abs(value) <= (if (isEv) 0.8f else 1.2f)
    val lastSign = remember { mutableIntStateOf(if (value > 0.05f) 1 else if (value < -0.05f) -1 else 0) }

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            contentAlignment = Alignment.Center
        ) {
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
                val strokeH = 5.dp.toPx()

                val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                val currentX = trackStartX + fraction * trackWidth

                val zeroFraction = if (isBipolar) {
                    ((0f - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                } else 0f
                val zeroX = trackStartX + zeroFraction * trackWidth

                if (gradientBrush != null) {
                    drawLine(
                        brush = gradientBrush,
                        start = Offset(trackStartX, centerY),
                        end = Offset(trackEndX, centerY),
                        strokeWidth = strokeH,
                        cap = StrokeCap.Round
                    )
                } else {
                    drawLine(
                        color = Color(0xFF2C2C2E),
                        start = Offset(trackStartX, centerY),
                        end = Offset(trackEndX, centerY),
                        strokeWidth = strokeH,
                        cap = StrokeCap.Round
                    )

                    if (isBipolar) {
                        if (currentX > zeroX) {
                            drawLine(
                                color = activeColor,
                                start = Offset(zeroX, centerY),
                                end = Offset(currentX, centerY),
                                strokeWidth = strokeH,
                                cap = StrokeCap.Round
                            )
                        } else if (currentX < zeroX) {
                            drawLine(
                                color = activeColor,
                                start = Offset(currentX, centerY),
                                end = Offset(zeroX, centerY),
                                strokeWidth = strokeH,
                                cap = StrokeCap.Round
                            )
                        }
                    } else {
                        drawLine(
                            color = activeColor,
                            start = Offset(trackStartX, centerY),
                            end = Offset(currentX, centerY),
                            strokeWidth = strokeH,
                            cap = StrokeCap.Round
                        )
                    }
                }

                if (isBipolar) {
                    val tickColor = if (isNearZero) Color.White else Color(0xFF888888)
                    drawLine(
                        color = tickColor,
                        start = Offset(zeroX, centerY - 6.dp.toPx()),
                        end = Offset(zeroX, centerY + 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Slider(
                value = value,
                onValueChange = { raw ->
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
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Bound labels under minimal slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = effectiveMinLabel,
                color = Color(0xFF666666),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            if (isBipolar) {
                Text(
                    text = "0",
                    color = if (isNearZero) Color.White else Color(0xFF666666),
                    fontSize = 10.sp,
                    fontWeight = if (isNearZero) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = effectiveMaxLabel,
                color = Color(0xFF666666),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Primary editing tool categories in Lightcraft & Emre Gallery:
 * [Auto], [Presets], [Crop], [Adjust], [Masking], [Healing]
 */
enum class MainBottomTab {
    AUTO, PRESETS, CROP, ADJUST, MASKING, HEALING
}

/**
 * Clean floating navigation dock for the photo editor.
 * Modeled after Emre's FloatingNavigationBar with pure monochrome styling,
 * spring-animated active indicator, and generous 48dp touch targets.
 */
@Composable
fun MainBottomTierBar(
    currentTab: MainBottomTab,
    onSelectTab: (MainBottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xF2161616),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    Triple(MainBottomTab.ADJUST, "Adjust", Icons.Default.Tune),
                    Triple(MainBottomTab.PRESETS, "Presets", Icons.Default.FilterVintage),
                    Triple(MainBottomTab.CROP, "Crop", Icons.Default.Crop),
                    Triple(MainBottomTab.MASKING, "Masking", Icons.Default.Brush),
                    Triple(MainBottomTab.HEALING, "Healing", Icons.Default.Healing),
                    Triple(MainBottomTab.AUTO, "Auto", Icons.Default.AutoFixHigh)
                )

                tabs.forEach { (tab, label, icon) ->
                    val selected = currentTab == tab
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.92f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "dock_scale"
                    )

                    val pillWidth by animateDpAsState(
                        targetValue = if (selected) 46.dp else 36.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "dock_pill_width"
                    )

                    val pillBg by animateColorAsState(
                        targetValue = if (selected) Color.White else Color.Transparent,
                        label = "dock_pill_bg"
                    )

                    val iconTint by animateColorAsState(
                        targetValue = if (selected) Color.Black else Color(0xFF8E8E93),
                        label = "dock_icon_tint"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelectTab(tab) }
                            )
                            .padding(vertical = 4.dp)
                            .testTag(
                                when (tab) {
                                    MainBottomTab.AUTO -> "bottom_tab_auto"
                                    MainBottomTab.PRESETS -> "bottom_tab_presets"
                                    MainBottomTab.CROP -> "bottom_tab_crop"
                                    MainBottomTab.ADJUST -> "bottom_tab_adjust"
                                    MainBottomTab.MASKING -> "bottom_tab_masking"
                                    MainBottomTab.HEALING -> "bottom_tab_healing"
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = pillBg,
                                modifier = Modifier
                                    .height(30.dp)
                                    .width(pillWidth)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = iconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else Color(0xFF8E8E93),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Adjustment categories in Lightcraft developer engine:
 * [Auto], [Light], [Color], [Blur], [Effects], [Detail], [Optics], [Profiles]
 */
enum class AdjustCategory(val label: String) {
    AUTO("Auto"),
    LIGHT("Light"),
    COLOR("Color"),
    BLUR("Blur"),
    EFFECTS("Effects"),
    DETAIL("Detail"),
    OPTICS("Optics"),
    PROFILES("Profiles")
}

/**
 * Secondary floating category strip for Adjust tools.
 * Clean horizontal scrollable pill chips with monochrome aesthetics.
 */
@Composable
fun AdjustCategoryStrip(
    activeCategory: AdjustCategory,
    onSelectCategory: (AdjustCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val categories = listOf(
            AdjustCategory.AUTO to Icons.Default.AutoAwesome,
            AdjustCategory.LIGHT to Icons.Default.WbSunny,
            AdjustCategory.COLOR to Icons.Default.DeviceThermostat,
            AdjustCategory.BLUR to Icons.Default.WaterDrop,
            AdjustCategory.EFFECTS to Icons.Default.CropSquare,
            AdjustCategory.DETAIL to Icons.Default.ChangeHistory,
            AdjustCategory.OPTICS to Icons.Default.Camera,
            AdjustCategory.PROFILES to Icons.Default.PhotoLibrary
        )

        categories.forEach { (cat, icon) ->
            val isSelected = activeCategory == cat
            Surface(
                onClick = { onSelectCategory(cat) },
                shape = CircleShape,
                color = if (isSelected) Color.White else Color(0xFF1E1E1E),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) Color.White else Color.White.copy(alpha = 0.08f)
                ),
                modifier = Modifier.testTag("category_${cat.label.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.Black else Color(0xFF8E8E93),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = cat.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else Color(0xFFCCCCCC)
                    )
                }
            }
        }
    }
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
    activeTrackColor: Color = Color.White,
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
    val isBipolar = valueRange.start < 0f && valueRange.endInclusive > 0f
    val numericRequester = LocalNumericInputRequester.current

    val isModified = abs(value - defaultValue) > (if (isEv) 0.05f else 0.4f)
    val isNearZero = isBipolar && abs(value) <= (if (isEv) 0.8f else 1.2f)

    val lastSign = remember { mutableIntStateOf(if (value > 0.05f) 1 else if (value < -0.05f) -1 else 0) }

    val resetToDefault: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onDragStart()
        onValueChange(defaultValue)
        onValueChangeFinished()
    }

    val openNumericInput: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        numericRequester(
            NumericInputTarget(
                title = title,
                currentValue = value,
                valueRange = valueRange,
                defaultValue = defaultValue,
                isEv = isEv,
                unit = unit,
                onApply = { newVal ->
                    onDragStart()
                    onValueChange(newVal)
                    onValueChangeFinished()
                }
            )
        )
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
                color = if (isModified) Color.White else Color(0xFFCCCCCC),
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
                        tint = Color(0xFFAAAAAA),
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
                        drawLine(
                            color = Color(0xFF2C2C2E),
                            start = Offset(trackStartX, centerY),
                            end = Offset(trackEndX, centerY),
                            strokeWidth = strokeH,
                            cap = StrokeCap.Round
                        )

                        // Draw Active Fill
                        if (isBipolar) {
                            if (currentX > zeroX) {
                                drawLine(
                                    color = activeTrackColor,
                                    start = Offset(zeroX, centerY),
                                    end = Offset(currentX, centerY),
                                    strokeWidth = strokeH,
                                    cap = StrokeCap.Round
                                )
                            } else if (currentX < zeroX) {
                                drawLine(
                                    color = activeTrackColor,
                                    start = Offset(currentX, centerY),
                                    end = Offset(zeroX, centerY),
                                    strokeWidth = strokeH,
                                    cap = StrokeCap.Round
                                )
                            }
                        } else {
                            drawLine(
                                color = activeTrackColor,
                                start = Offset(trackStartX, centerY),
                                end = Offset(currentX, centerY),
                                strokeWidth = strokeH,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Draw Center Tick Notch for bipolar sliders
                    if (isBipolar) {
                        val tickColor = if (isNearZero) Color.White else Color(0xFF777777)
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
                        thumbColor = Color.White,
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
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.2).sp
                )
                if (isBipolar) {
                    Text(
                        text = "0",
                        color = if (isNearZero) Color.White else Color(0xFF666666),
                        fontSize = 9.sp,
                        fontWeight = if (isNearZero) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = effectiveMaxLabel,
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.2).sp
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        // 3. Right numeric value button: Tap for precise numeric input, Double-tap to reset
        Surface(
            onClick = openNumericInput,
            shape = RoundedCornerShape(6.dp),
            color = if (isModified) Color(0x33FFFFFF) else Color(0x18FFFFFF),
            border = BorderStroke(
                0.5.dp,
                if (isModified) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f)
            ),
            modifier = Modifier
                .width(68.dp)
                .testTag("slider_value_$title")
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { openNumericInput() },
                            onDoubleTap = { resetToDefault() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayValue,
                    color = Color.White,
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
    onOpenCurve: () -> Unit,
    onSliderDragStart: (name: String, formattedVal: String) -> Unit,
    onSliderChange: (prop: String, value: Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Curve button on top right (clean monochrome pill)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                onClick = onOpenCurve,
                shape = CircleShape,
                color = Color(0xFF1E1E1E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.testTag("light_panel_curve_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "Curve",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Curve",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

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
 * 2. CURVE CONTROLS BAR
 * Minimal floating channel selector and action controls.
 */
@Composable
fun CurveControlsBar(
    activeChannel: CurveChannel,
    onSelectChannel: (CurveChannel) -> Unit,
    onResetCurve: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xFF141414),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel selector dots: RGB (White), Red, Green, Blue
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val channels = listOf(
                        Triple(CurveChannel.RGB, Color.White, "RGB"),
                        Triple(CurveChannel.RED, Color(0xFFFF5252), "Red"),
                        Triple(CurveChannel.GREEN, Color(0xFF69F0AE), "Green"),
                        Triple(CurveChannel.BLUE, Color(0xFF448AFF), "Blue")
                    )

                    channels.forEach { (channel, dotColor, label) ->
                        val isSel = activeChannel == channel
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isSel) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    width = if (isSel) 1.5.dp else 0.5.dp,
                                    color = if (isSel) Color.White else Color(0xFF555555),
                                    shape = CircleShape
                                )
                                .clickable { onSelectChannel(channel) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reset
                    IconButton(onClick = onResetCurve, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset curve",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Done
                    Surface(
                        onClick = onDone,
                        shape = CircleShape,
                        color = Color.White,
                        contentColor = Color.Black
                    ) {
                        Text(
                            text = "Done",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
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
    onOpenGrading: () -> Unit,
    onOpenMix: () -> Unit,
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
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top action pills: [ B & W ], [ Grading ], [ Mix ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // B&W Toggle
            Surface(
                onClick = onToggleBw,
                shape = CircleShape,
                color = if (isBlackAndWhite) Color.White else Color(0xFF1E1E1E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "B & W",
                        color = if (isBlackAndWhite) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Grading
            Surface(
                onClick = onOpenGrading,
                shape = CircleShape,
                color = Color(0xFF1E1E1E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.weight(1.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = "Grading",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Grading",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Mix (HSL)
            Surface(
                onClick = onOpenMix,
                shape = CircleShape,
                color = Color(0xFF1E1E1E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Color Mix",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Mix",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

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

/**
 * In-panel HSL Color Mix (8 color bands with hue, saturation, luminance tuning).
 */
@Composable
fun ColorMixPanel(
    selectedBand: ColorBand,
    onSelectBand: (ColorBand) -> Unit,
    hslAdjustment: HslBandAdjustment,
    onHueChange: (Float) -> Unit,
    onSatChange: (Float) -> Unit,
    onLumChange: (Float) -> Unit,
    onResetBand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 8 color swatches row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorBand.entries.forEach { band ->
                val isSel = selectedBand == band
                val dotColor = when (band) {
                    ColorBand.RED -> Color(0xFFFF3B30)
                    ColorBand.ORANGE -> Color(0xFFFF9500)
                    ColorBand.YELLOW -> Color(0xFFFFCC00)
                    ColorBand.GREEN -> Color(0xFF34C759)
                    ColorBand.CYAN -> Color(0xFF32ADE6)
                    ColorBand.BLUE -> Color(0xFF007AFF)
                    ColorBand.PURPLE -> Color(0xFF5856D6)
                    ColorBand.MAGENTA -> Color(0xFFAF52DE)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isSel) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            width = if (isSel) 2.dp else 0.5.dp,
                            color = if (isSel) Color.White else Color(0xFF444444),
                            shape = CircleShape
                        )
                        .clickable { onSelectBand(band) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedBand.label} Color",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Surface(
                onClick = onResetBand,
                shape = CircleShape,
                color = Color(0xFF222222)
            ) {
                Text(
                    text = "Reset Band",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        val hueStr = if (hslAdjustment.hueShift > 0) "+${hslAdjustment.hueShift.toInt()}" else "${hslAdjustment.hueShift.toInt()}"
        LrSliderRow(
            title = "Hue",
            value = hslAdjustment.hueShift,
            displayValue = hueStr,
            valueRange = -100f..100f,
            onDragStart = {},
            onValueChange = onHueChange,
            onValueChangeFinished = {}
        )

        val satStr = if (hslAdjustment.satShift > 0) "+${hslAdjustment.satShift.toInt()}" else "${hslAdjustment.satShift.toInt()}"
        LrSliderRow(
            title = "Saturation",
            value = hslAdjustment.satShift,
            displayValue = satStr,
            valueRange = -100f..100f,
            onDragStart = {},
            onValueChange = onSatChange,
            onValueChangeFinished = {}
        )

        val lumStr = if (hslAdjustment.lumShift > 0) "+${hslAdjustment.lumShift.toInt()}" else "${hslAdjustment.lumShift.toInt()}"
        LrSliderRow(
            title = "Luminance",
            value = hslAdjustment.lumShift,
            displayValue = lumStr,
            valueRange = -100f..100f,
            onDragStart = {},
            onValueChange = onLumChange,
            onValueChangeFinished = {}
        )
    }
}

/**
 * In-panel Color Grading (Split Toning for Shadows & Highlights).
 */
@Composable
fun ColorGradingPanel(
    splitToning: SplitToningParams,
    onShadowHueChange: (Float) -> Unit,
    onShadowSatChange: (Float) -> Unit,
    onHighlightHueChange: (Float) -> Unit,
    onHighlightSatChange: (Float) -> Unit,
    onBalanceChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Shadows Tone", color = Color(0xFFDDDDDD), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Surface(
                onClick = onReset,
                shape = CircleShape,
                color = Color(0xFF222222)
            ) {
                Text(
                    text = "Reset",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        LrSliderRow(
            title = "Hue",
            value = splitToning.shadowHue,
            displayValue = "${splitToning.shadowHue.toInt()}°",
            valueRange = 0f..360f,
            onDragStart = {},
            onValueChange = onShadowHueChange,
            onValueChangeFinished = {}
        )
        LrSliderRow(
            title = "Saturation",
            value = splitToning.shadowSat,
            displayValue = "${splitToning.shadowSat.toInt()}",
            valueRange = 0f..100f,
            onDragStart = {},
            onValueChange = onShadowSatChange,
            onValueChangeFinished = {}
        )

        Spacer(Modifier.height(6.dp))
        Text("Highlights Tone", color = Color(0xFFDDDDDD), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        LrSliderRow(
            title = "Hue",
            value = splitToning.highlightHue,
            displayValue = "${splitToning.highlightHue.toInt()}°",
            valueRange = 0f..360f,
            onDragStart = {},
            onValueChange = onHighlightHueChange,
            onValueChangeFinished = {}
        )
        LrSliderRow(
            title = "Saturation",
            value = splitToning.highlightSat,
            displayValue = "${splitToning.highlightSat.toInt()}",
            valueRange = 0f..100f,
            onDragStart = {},
            onValueChange = onHighlightSatChange,
            onValueChangeFinished = {}
        )

        Spacer(Modifier.height(6.dp))
        val balStr = if (splitToning.balance > 0) "+${splitToning.balance.toInt()}" else "${splitToning.balance.toInt()}"
        LrSliderRow(
            title = "Balance",
            value = splitToning.balance,
            displayValue = balStr,
            valueRange = -100f..100f,
            onDragStart = {},
            onValueChange = onBalanceChange,
            onValueChangeFinished = {}
        )
    }
}

/**
 * 4. EFFECTS PANEL
 * Minimal tabs: Effects, Vignette, Grain
 */
enum class EffectsTab { EFFECTS, VIGNETTE, GRAIN }

@Composable
fun EffectsPanel(
    texture: Float,
    clarity: Float,
    dehaze: Float,
    vignette: Float,
    grain: Float,
    onSliderDragStart: (name: String, formattedVal: String) -> Unit,
    onSliderChange: (prop: String, value: Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(EffectsTab.EFFECTS) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Sub-tabs row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                EffectsTab.EFFECTS to "Effects",
                EffectsTab.VIGNETTE to "Vignette",
                EffectsTab.GRAIN to "Grain"
            ).forEach { (tab, label) ->
                val isSel = activeTab == tab
                Surface(
                    onClick = { activeTab = tab },
                    shape = CircleShape,
                    color = if (isSel) Color.White else Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, if (isSel) Color.White else Color.White.copy(alpha = 0.08f))
                ) {
                    Text(
                        text = label,
                        color = if (isSel) Color.Black else Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when (activeTab) {
            EffectsTab.EFFECTS -> {
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
            }
            EffectsTab.VIGNETTE -> {
                val vigStr = when {
                    vignette > 0f -> "+${vignette.toInt()}"
                    vignette < 0f -> "−${(-vignette).toInt()}"
                    else -> "0"
                }
                LrSliderRow(
                    title = "Amount",
                    value = vignette,
                    displayValue = vigStr,
                    valueRange = -100f..100f,
                    minLabel = "−100",
                    maxLabel = "+100",
                    onDragStart = { onSliderDragStart("Amount", vigStr) },
                    onValueChange = { onSliderChange("Vignette", it) },
                    onValueChangeFinished = onSliderChangeFinished
                )
            }
            EffectsTab.GRAIN -> {
                val grainStr = "${grain.toInt()}"
                LrSliderRow(
                    title = "Amount",
                    value = grain,
                    displayValue = grainStr,
                    valueRange = 0f..100f,
                    minLabel = "0",
                    maxLabel = "+100",
                    onDragStart = { onSliderDragStart("Amount", grainStr) },
                    onValueChange = { onSliderChange("Grain", it) },
                    onValueChangeFinished = onSliderChangeFinished
                )
            }
        }
    }
}

/**
 * 5. DETAIL PANEL
 * Clean monochrome sharpening, noise and color noise controls.
 */
enum class DetailTab { SHARPENING, NOISE, COLOR_NOISE }

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
    var activeTab by remember { mutableStateOf(DetailTab.SHARPENING) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Sub-tabs row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                DetailTab.SHARPENING to "Sharpening",
                DetailTab.NOISE to "Noise",
                DetailTab.COLOR_NOISE to "Color noise"
            ).forEach { (tab, label) ->
                val isSel = activeTab == tab
                Surface(
                    onClick = { activeTab = tab },
                    shape = CircleShape,
                    color = if (isSel) Color.White else Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, if (isSel) Color.White else Color.White.copy(alpha = 0.08f))
                ) {
                    Text(
                        text = label,
                        color = if (isSel) Color.Black else Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when (activeTab) {
            DetailTab.SHARPENING -> {
                val amountStr = if (sharpness > 0) "+${sharpness.toInt()}" else "${sharpness.toInt()}"
                LrSliderRow(
                    title = "Amount",
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
            }
            DetailTab.NOISE -> {
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
            }
            DetailTab.COLOR_NOISE -> {
                val colorNoiseStr = "${colorNoiseReduction.toInt()}"
                LrSliderRow(
                    title = "Color Noise",
                    value = colorNoiseReduction,
                    displayValue = colorNoiseStr,
                    valueRange = 0f..100f,
                    onDragStart = { onSliderDragStart("Color Noise", colorNoiseStr) },
                    onValueChange = { onSliderChange("Color noise", it) },
                    onValueChangeFinished = onSliderChangeFinished
                )
            }
        }
    }
}

/**
 * 6. OPTICS PANEL
 * Monochrome switches and distortion slider.
 */
@Composable
fun OpticsPanel(
    removeChromaticAberration: Boolean,
    enableLensCorrections: Boolean,
    distortionCorrection: Float,
    onToggleChromaticAberration: (Boolean) -> Unit,
    onToggleLensCorrections: (Boolean) -> Unit,
    onSliderDragStart: (name: String, formattedVal: String) -> Unit,
    onSliderChange: (prop: String, value: Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Toggle 1: Remove Chromatic Aberration
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Remove Chromatic Aberration",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = removeChromaticAberration,
                onCheckedChange = onToggleChromaticAberration,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = Color(0xFFAAAAAA),
                    uncheckedTrackColor = Color(0xFF2C2C2E)
                )
            )
        }

        // Toggle 2: Enable Lens Corrections
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable Lens Corrections",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = enableLensCorrections,
                onCheckedChange = onToggleLensCorrections,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = Color(0xFFAAAAAA),
                    uncheckedTrackColor = Color(0xFF2C2C2E)
                )
            )
        }

        if (enableLensCorrections) {
            Spacer(Modifier.height(6.dp))
            val distStr = "+${distortionCorrection.toInt()}"
            LrSliderRow(
                title = "Distortion",
                value = distortionCorrection,
                displayValue = distStr,
                valueRange = 0f..200f,
                minLabel = "0",
                maxLabel = "+200",
                onDragStart = { onSliderDragStart("Distortion", distStr) },
                onValueChange = { onSliderChange("Distortion Correction", it) },
                onValueChangeFinished = onSliderChangeFinished
            )
        }
    }
}

/**
 * 7. PROFILES & PRESETS
 * Clean monochrome preview cards and group selection.
 */
val PresetCategoriesWithCounts = listOf(
    Pair("Favorites", 2),
    Pair("Basic", 2),
    Pair("Artistic", 8),
    Pair("B&W", 17),
    Pair("Film-Inspired", 12),
    Pair("Modern", 10),
    Pair("Vintage", 10)
)

fun generatePresetsForCategory(cat: String): List<ProfilePreset> = when (cat) {
    "Favorites" -> listOf(
        ProfilePreset("fav_color", "Color", "Favorites", FilterType.VIVID, isFavorite = true),
        ProfilePreset("fav_bw", "Monochrome", "Favorites", FilterType.BW, isFavorite = true)
    )
    "Basic" -> listOf(
        ProfilePreset("basic_color", "Color", "Basic", FilterType.VIVID, isFavorite = true),
        ProfilePreset("basic_mono", "Monochrome", "Basic", FilterType.BW)
    )
    "Artistic" -> (1..8).map {
        ProfilePreset("art_$it", "Artistic %02d".format(it), "Artistic", FilterType.entries[it % FilterType.entries.size])
    }
    "B&W" -> (1..17).map {
        ProfilePreset("bw_$it", "B&W %02d".format(it), "B&W", if (it % 2 == 0) FilterType.NOIR else FilterType.BW)
    }
    "Film-Inspired" -> (1..12).map {
        ProfilePreset("film_$it", "Film %02d".format(it), "Film-Inspired", FilterType.FILM)
    }
    "Modern" -> (1..10).map {
        ProfilePreset("mod_$it", "Modern %02d".format(it), "Modern", FilterType.CYBER)
    }
    "Vintage" -> (1..10).map {
        ProfilePreset("vin_$it", "Vintage %02d".format(it), "Vintage", FilterType.VINTAGE)
    }
    else -> listOf(ProfilePreset("def", "Color", cat, FilterType.VIVID))
}

@Composable
fun ProfilesPanel(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onApplyProfile: (ProfilePreset) -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCategorySheet by remember { mutableStateOf(false) }
    var activeProfileId by remember { mutableStateOf("basic_color") }

    val presets = remember(selectedCategory) { generatePresetsForCategory(selectedCategory) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(vertical = 8.dp)
    ) {
        // Group dropdown chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { showCategorySheet = true },
                shape = CircleShape,
                color = Color(0xFF1E1E1E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = selectedCategory,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select category",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Horizontal Carousel of Profile Cards
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(presets) { preset ->
                val isSelected = activeProfileId == preset.id
                ProfilePreviewCard(
                    preset = preset,
                    isSelected = isSelected,
                    onClick = {
                        activeProfileId = preset.id
                        onApplyProfile(preset)
                    }
                )
            }
        }
    }

    if (showCategorySheet) {
        AlertDialog(
            onDismissRequest = { showCategorySheet = false },
            confirmButton = {},
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Select Profile Group", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(PresetCategoriesWithCounts) { (cat, count) ->
                        val isCurrent = cat == selectedCategory
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectCategory(cat)
                                    showCategorySheet = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cat,
                                color = if (isCurrent) Color.White else Color(0xFFCCCCCC),
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = count.toString(),
                                color = Color(0xFF888888),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ProfilePreviewCard(
    preset: ProfilePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
    ) {
        // Thumbnail gradient simulation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(
                    when (preset.category) {
                        "B&W" -> Brush.verticalGradient(listOf(Color(0xFF444444), Color(0xFF111111)))
                        "Vintage" -> Brush.linearGradient(listOf(Color(0xFF7D6E63), Color(0xFF5D4037)))
                        "Film-Inspired" -> Brush.linearGradient(listOf(Color(0xFF4A5568), Color(0xFF2D3748)))
                        "Artistic" -> Brush.linearGradient(listOf(Color(0xFF6B46C1), Color(0xFF319795)))
                        else -> Brush.linearGradient(listOf(Color(0xFF555555), Color(0xFF222222)))
                    }
                )
        )

        // Title bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) Color.White else Color(0xFF161616))
                .padding(vertical = 6.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = preset.name,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
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
    onApply: () -> Unit,
    onAutoLevel: (() -> Unit)? = null,
    onResetCrop: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
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
                        Icon(Icons.Default.RotateRight, null, Modifier.size(16.dp), tint = Color.White)
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

            if (onAutoLevel != null) {
                item {
                    Surface(
                        onClick = onAutoLevel,
                        shape = CircleShape,
                        color = Color(0xFF1E1E1E),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Auto-Level", color = Color.White, fontSize = 12.sp)
                        }
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

/**
 * 9. BRUSHES & MASKING PANEL
 */
@Composable
fun BrushesPanel(
    selectedBrush: BrushType,
    brushColor: Color,
    brushWidth: Float,
    onSelectBrush: (BrushType) -> Unit,
    onColorSelect: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color.White, Color.LightGray, Color.Gray, Color.DarkGray, Color.Black
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Size slider
        LrSliderRow(
            title = "Brush Size",
            value = brushWidth,
            displayValue = "${brushWidth.toInt()}px",
            valueRange = 2f..80f,
            defaultValue = 10f,
            unit = "px",
            minLabel = "2px",
            maxLabel = "80px",
            onDragStart = {},
            onValueChange = onWidthChange,
            onValueChangeFinished = {}
        )

        // Palette & Brush types row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Monochrome swatches
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { col ->
                    val isSel = brushColor == col
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(
                                width = if (isSel) 2.dp else 1.dp,
                                color = if (isSel) Color.White else Color(0xFF444444),
                                shape = CircleShape
                            )
                            .clickable { onColorSelect(col) }
                    )
                }
            }

            // Brush types
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(BrushType.entries) { brush ->
                    val isSelected = selectedBrush == brush
                    Surface(
                        onClick = { onSelectBrush(brush) },
                        shape = CircleShape,
                        color = if (isSelected) Color.White else Color(0xFF1E1E1E),
                        border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.08f))
                    ) {
                        Text(
                            text = brush.label,
                            color = if (isSelected) Color.Black else Color(0xFFCCCCCC),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 10. RETOUCH & HEALING PANEL
 */
@Composable
fun RetouchPanel(
    activeType: RetouchType,
    brushRadius: Float,
    onSelectType: (RetouchType) -> Unit,
    onRadiusChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Radius slider
        LrSliderRow(
            title = "Healing Size",
            value = brushRadius,
            displayValue = "${brushRadius.toInt()}px",
            valueRange = 8f..100f,
            defaultValue = 15f,
            unit = "px",
            minLabel = "8px",
            maxLabel = "100px",
            onDragStart = {},
            onValueChange = onRadiusChange,
            onValueChangeFinished = {}
        )

        // Retouch types chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            items(listOf(RetouchType.BLEMISH, RetouchType.OBJECT_REMOVAL, RetouchType.SKIN_SMOOTH, RetouchType.CLONE_STAMP, RetouchType.PATCH_HEALING)) { type ->
                val isSelected = activeType == type
                Surface(
                    onClick = { onSelectType(type) },
                    shape = CircleShape,
                    color = if (isSelected) Color.White else Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.08f))
                ) {
                    Text(
                        text = type.label,
                        color = if (isSelected) Color.Black else Color(0xFFCCCCCC),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
