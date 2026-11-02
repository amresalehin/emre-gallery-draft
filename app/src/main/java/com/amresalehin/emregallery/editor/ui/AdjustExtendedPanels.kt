package com.amresalehin.emregallery.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*
import kotlin.math.sin

/**
 * Levels Panel with interactive histogram and Black Point, Midtones Gamma, White Point controls.
 */
@Composable
fun LevelsPanel(
    levels: LevelsParams,
    onLevelsChange: (LevelsParams) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Levels Adjustment",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(36.dp).testTag("levels_reset_button")
            ) {
                Icon(Icons.Default.RestartAlt, "Reset Levels", tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Visual Histogram Simulation Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0A0A))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw mock tonal distribution curve
                val path = Path()
                path.moveTo(0f, h)
                for (i in 0..60) {
                    val x = (i / 60f) * w
                    val normalizedX = i / 60f
                    // Bell-curve shape biased by midtones
                    val peak = 0.5f * levels.midtonesGamma
                    val dist = kotlin.math.abs(normalizedX - peak)
                    val curveY = h - (h * 0.85f * kotlin.math.exp(-dist * dist * 12f).toFloat())
                    path.lineTo(x, curveY.coerceIn(h * 0.15f, h))
                }
                path.lineTo(w, h)
                path.close()

                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        listOf(Color(0x994FC3F7), Color(0x224FC3F7))
                    )
                )

                // Black point marker
                val bpX = (levels.blackPoint / 100f) * (w * 0.4f)
                drawLine(
                    color = Color(0xFF64B5F6),
                    start = Offset(bpX, 0f),
                    end = Offset(bpX, h),
                    strokeWidth = 2.dp.toPx()
                )

                // White point marker
                val wpNorm = (levels.whitePoint - 150f) / 105f
                val wpX = w - ((1f - wpNorm) * (w * 0.4f))
                drawLine(
                    color = Color(0xFFFFB74D),
                    start = Offset(wpX, 0f),
                    end = Offset(wpX, h),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Black Point Slider (0..100)
        LrSliderRow(
            title = "Black Point",
            value = levels.blackPoint,
            displayValue = "${levels.blackPoint.toInt()}",
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "100",
            onDragStart = {},
            onValueChange = { onLevelsChange(levels.copy(blackPoint = it)) },
            onValueChangeFinished = {}
        )

        // Midtones Gamma Slider (0.2..3.0, default 1.0)
        LrSliderRow(
            title = "Midtones (Gamma)",
            value = levels.midtonesGamma,
            displayValue = String.format(java.util.Locale.US, "%.2f", levels.midtonesGamma),
            valueRange = 0.2f..3.0f,
            minLabel = "0.20",
            maxLabel = "3.00",
            onDragStart = {},
            onValueChange = { onLevelsChange(levels.copy(midtonesGamma = it)) },
            onValueChangeFinished = {}
        )

        // White Point Slider (150..255)
        LrSliderRow(
            title = "White Point",
            value = levels.whitePoint,
            displayValue = "${levels.whitePoint.toInt()}",
            valueRange = 150f..255f,
            minLabel = "150",
            maxLabel = "255",
            onDragStart = {},
            onValueChange = { onLevelsChange(levels.copy(whitePoint = it)) },
            onValueChangeFinished = {}
        )
    }
}

/**
 * Color Balance Panel with Shadows, Midtones, and Highlights selection
 * and Cyan/Red, Magenta/Green, Yellow/Blue sliders.
 */
@Composable
fun ColorBalancePanel(
    balance: ColorBalanceParams,
    onBalanceChange: (ColorBalanceParams) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTone by remember { mutableStateOf("Midtones") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Color Balance",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(36.dp).testTag("color_balance_reset")
            ) {
                Icon(Icons.Default.RestartAlt, "Reset Balance", tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Tone Selector Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Shadows", "Midtones", "Highlights").forEach { tone ->
                val isSelected = selectedTone == tone
                Surface(
                    onClick = { selectedTone = tone },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tone,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val currentRed = when (selectedTone) {
            "Shadows" -> balance.shadowRed
            "Highlights" -> balance.highlightRed
            else -> balance.midtoneRed
        }
        val currentGreen = when (selectedTone) {
            "Shadows" -> balance.shadowGreen
            "Highlights" -> balance.highlightGreen
            else -> balance.midtoneGreen
        }
        val currentBlue = when (selectedTone) {
            "Shadows" -> balance.shadowBlue
            "Highlights" -> balance.highlightBlue
            else -> balance.midtoneBlue
        }

        // Cyan / Red Slider (-50..+50)
        LrSliderRow(
            title = "Cyan ↔ Red",
            value = currentRed,
            displayValue = if (currentRed > 0) "+${currentRed.toInt()}" else "${currentRed.toInt()}",
            valueRange = -50f..50f,
            minLabel = "Cyan",
            maxLabel = "Red",
            onDragStart = {},
            onValueChange = { newVal ->
                val updated = when (selectedTone) {
                    "Shadows" -> balance.copy(shadowRed = newVal)
                    "Highlights" -> balance.copy(highlightRed = newVal)
                    else -> balance.copy(midtoneRed = newVal)
                }
                onBalanceChange(updated)
            },
            onValueChangeFinished = {}
        )

        // Magenta / Green Slider (-50..+50)
        LrSliderRow(
            title = "Magenta ↔ Green",
            value = currentGreen,
            displayValue = if (currentGreen > 0) "+${currentGreen.toInt()}" else "${currentGreen.toInt()}",
            valueRange = -50f..50f,
            minLabel = "Magenta",
            maxLabel = "Green",
            onDragStart = {},
            onValueChange = { newVal ->
                val updated = when (selectedTone) {
                    "Shadows" -> balance.copy(shadowGreen = newVal)
                    "Highlights" -> balance.copy(highlightGreen = newVal)
                    else -> balance.copy(midtoneGreen = newVal)
                }
                onBalanceChange(updated)
            },
            onValueChangeFinished = {}
        )

        // Yellow / Blue Slider (-50..+50)
        LrSliderRow(
            title = "Yellow ↔ Blue",
            value = currentBlue,
            displayValue = if (currentBlue > 0) "+${currentBlue.toInt()}" else "${currentBlue.toInt()}",
            valueRange = -50f..50f,
            minLabel = "Yellow",
            maxLabel = "Blue",
            onDragStart = {},
            onValueChange = { newVal ->
                val updated = when (selectedTone) {
                    "Shadows" -> balance.copy(shadowBlue = newVal)
                    "Highlights" -> balance.copy(highlightBlue = newVal)
                    else -> balance.copy(midtoneBlue = newVal)
                }
                onBalanceChange(updated)
            },
            onValueChangeFinished = {}
        )
    }
}

/**
 * Color FX Workspace for Gradient Maps, Duotone, Color Replace, and Color Splash.
 */
@Composable
fun ColorFxPanel(
    adj: AdjustmentParams,
    onGradientMapChange: (GradientMapType) -> Unit,
    onDuotoneChange: (DuotoneParams) -> Unit,
    onColorReplaceChange: (ColorReplaceParams) -> Unit,
    onColorSplashChange: (ColorSplashParams) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf("Gradient") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Subcategory Pills: Gradient, Duotone, Replace, Splash
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                "Gradient" to "Gradient Map",
                "Duotone" to "Duotone",
                "Replace" to "Color Replace",
                "Splash" to "Color Splash"
            )
            items(tabs) { (key, label) ->
                val isSelected = activeSubTab == key
                Surface(
                    onClick = { activeSubTab = key },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    modifier = Modifier.height(34.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        when (activeSubTab) {
            "Gradient" -> {
                Text("Gradient Maps", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(GradientMapType.entries) { grad ->
                        val isSelected = adj.gradientMap == grad
                        val gradientColors = when (grad) {
                            GradientMapType.NONE -> listOf(Color(0xFF333333), Color(0xFF666666))
                            GradientMapType.SEPIA_GOLD -> listOf(Color(0xFF2B1D0E), Color(0xFFFFD54F))
                            GradientMapType.SUNSET_GLOW -> listOf(Color(0xFF3E1F47), Color(0xFFFF7043))
                            GradientMapType.CYBER_NEON -> listOf(Color(0xFF001F3F), Color(0xFF00FFCC))
                            GradientMapType.DEEP_OCEAN -> listOf(Color(0xFF051937), Color(0xFF00BF72))
                            GradientMapType.EMERALD_FOREST -> listOf(Color(0xFF0B2B1A), Color(0xFFA8E063))
                            GradientMapType.VINTAGE_PASTEL -> listOf(Color(0xFF533E2D), Color(0xFFF4C2C2))
                            GradientMapType.BW_CONTRAST -> listOf(Color.Black, Color.White)
                        }

                        Surface(
                            onClick = { onGradientMapChange(grad) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E1E1E),
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.width(96.dp).height(74.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Brush.horizontalGradient(gradientColors))
                                )
                                Text(
                                    text = grad.label,
                                    color = if (isSelected) Color.White else Color(0xFFAAAAAA),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            "Duotone" -> {
                val duotone = adj.duotone
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Duotone", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Switch(
                        checked = duotone.enabled,
                        onCheckedChange = { onDuotoneChange(duotone.copy(enabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2979FF)
                        )
                    )
                }

                if (duotone.enabled) {
                    Spacer(Modifier.height(10.dp))
                    Text("Shadow Tint Color", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val shadowColors = listOf(
                            Color(0xFF16213E), Color(0xFF1A1A24), Color(0xFF2C061F),
                            Color(0xFF0F3443), Color(0xFF1F4037), Color(0xFF3A1C71)
                        )
                        items(shadowColors) { c ->
                            Surface(
                                onClick = { onDuotoneChange(duotone.copy(shadowColor = c)) },
                                shape = CircleShape,
                                color = c,
                                border = BorderStroke(if (duotone.shadowColor == c) 2.dp else 1.dp, Color.White),
                                modifier = Modifier.size(36.dp)
                            ) {}
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Highlight Tint Color", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val highlightColors = listOf(
                            Color(0xFFE94560), Color(0xFFFF9A8B), Color(0xFFFFD54F),
                            Color(0xFF34E89E), Color(0xFF00C9FF), Color(0xFFF857A6)
                        )
                        items(highlightColors) { c ->
                            Surface(
                                onClick = { onDuotoneChange(duotone.copy(highlightColor = c)) },
                                shape = CircleShape,
                                color = c,
                                border = BorderStroke(if (duotone.highlightColor == c) 2.dp else 1.dp, Color.White),
                                modifier = Modifier.size(36.dp)
                            ) {}
                        }
                    }
                }
            }
            "Replace" -> {
                val replace = adj.colorReplace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Color Replace", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Switch(
                        checked = replace.enabled,
                        onCheckedChange = { onColorReplaceChange(replace.copy(enabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2979FF)
                        )
                    )
                }

                if (replace.enabled) {
                    Spacer(Modifier.height(10.dp))
                    LrSliderRow(
                        title = "Source Hue",
                        value = replace.sourceHue,
                        displayValue = "${replace.sourceHue.toInt()}°",
                        valueRange = 0f..360f,
                        minLabel = "0°",
                        maxLabel = "360°",
                        onDragStart = {},
                        onValueChange = { onColorReplaceChange(replace.copy(sourceHue = it)) },
                        onValueChangeFinished = {}
                    )

                    LrSliderRow(
                        title = "Target Hue",
                        value = replace.targetHue,
                        displayValue = "${replace.targetHue.toInt()}°",
                        valueRange = 0f..360f,
                        minLabel = "0°",
                        maxLabel = "360°",
                        onDragStart = {},
                        onValueChange = { onColorReplaceChange(replace.copy(targetHue = it)) },
                        onValueChangeFinished = {}
                    )

                    LrSliderRow(
                        title = "Hue Tolerance",
                        value = replace.tolerance,
                        displayValue = "±${replace.tolerance.toInt()}°",
                        valueRange = 5f..90f,
                        minLabel = "5°",
                        maxLabel = "90°",
                        onDragStart = {},
                        onValueChange = { onColorReplaceChange(replace.copy(tolerance = it)) },
                        onValueChangeFinished = {}
                    )
                }
            }
            "Splash" -> {
                val splash = adj.colorSplash
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Color Splash", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Switch(
                        checked = splash.enabled,
                        onCheckedChange = { onColorSplashChange(splash.copy(enabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2979FF)
                        )
                    )
                }

                if (splash.enabled) {
                    Spacer(Modifier.height(10.dp))
                    Text("Target Splash Color", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val splashPresets = listOf(
                            "Red" to 0f,
                            "Orange" to 30f,
                            "Yellow" to 60f,
                            "Green" to 120f,
                            "Cyan" to 180f,
                            "Blue" to 240f,
                            "Purple" to 280f,
                            "Magenta" to 320f
                        )
                        items(splashPresets) { (label, hue) ->
                            val isSel = kotlin.math.abs(splash.targetHue - hue) < 10f
                            Surface(
                                onClick = { onColorSplashChange(splash.copy(targetHue = hue)) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSel) Color.White else Color(0xFF222222),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    LrSliderRow(
                        title = "Target Hue",
                        value = splash.targetHue,
                        displayValue = "${splash.targetHue.toInt()}°",
                        valueRange = 0f..360f,
                        minLabel = "0°",
                        maxLabel = "360°",
                        onDragStart = {},
                        onValueChange = { onColorSplashChange(splash.copy(targetHue = it)) },
                        onValueChangeFinished = {}
                    )

                    LrSliderRow(
                        title = "Tolerance",
                        value = splash.tolerance,
                        displayValue = "±${splash.tolerance.toInt()}°",
                        valueRange = 10f..80f,
                        minLabel = "10°",
                        maxLabel = "80°",
                        onDragStart = {},
                        onValueChange = { onColorSplashChange(splash.copy(tolerance = it)) },
                        onValueChangeFinished = {}
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Vignette Panel for the separate Vignette Domain.
 * Features an interactive visual preview canvas, precision sliders
 * (Amount, Midpoint, Feather, Roundness), and quick artistic presets.
 */
@Composable
fun VignettePanel(
    vignette: Float,
    vignetteMidpoint: Float,
    vignetteFeather: Float,
    vignetteRoundness: Float,
    onVignetteChange: (Float) -> Unit,
    onMidpointChange: (Float) -> Unit,
    onFeatherChange: (Float) -> Unit,
    onRoundnessChange: (Float) -> Unit,
    onApplyPreset: (amount: Float, midpoint: Float, feather: Float, roundness: Float) -> Unit,
    onReset: () -> Unit,
    onSliderDragStart: (name: String, formattedVal: String) -> Unit,
    onSliderDragFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Header with reset button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Vignette Framing",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(36.dp).testTag("vignette_reset_button")
            ) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = "Reset Vignette",
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Visual Vignette Preview Simulator Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C1C))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val maxRadius = kotlin.math.max(w, h) / 2f

                val isWhiteVignette = vignette > 0f
                val absVig = (kotlin.math.abs(vignette) / 100f).coerceIn(0f, 1f)
                val vigColor = if (isWhiteVignette) Color.White else Color.Black

                val midFrac = (vignetteMidpoint / 100f).coerceIn(0.1f, 0.9f)
                val featherFrac = (vignetteFeather / 100f).coerceIn(0.1f, 0.9f)
                val startStop = (midFrac * (1f - featherFrac * 0.5f)).coerceIn(0f, 0.8f)
                val endStop = (midFrac + (1f - midFrac) * featherFrac).coerceIn(startStop + 0.05f, 1f)

                drawRect(Color(0xFF262626))

                if (absVig > 0.02f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                startStop to Color.Transparent,
                                endStop to vigColor.copy(alpha = absVig * 0.85f),
                                1.0f to vigColor.copy(alpha = absVig)
                            ),
                            center = Offset(cx, cy),
                            radius = maxRadius
                        )
                    )
                }
            }

            Text(
                text = if (vignette == 0f) "Vignette Off" else if (vignette > 0) "White Vignette (+${vignette.toInt()})" else "Dark Vignette (${vignette.toInt()})",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(12.dp))

        // Quick Preset Chips
        Text(
            text = "Artistic Vignette Styles",
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val presets = listOf(
                "None" to (0f to Triple(50f, 50f, 0f)),
                "Subtle Dark" to (-25f to Triple(50f, 60f, 0f)),
                "Dramatic" to (-60f to Triple(40f, 70f, 10f)),
                "Vintage Oval" to (-75f to Triple(30f, 45f, -30f)),
                "White Dream" to (45f to Triple(45f, 80f, 20f)),
                "Spotlight" to (-90f to Triple(20f, 25f, 40f))
            )
            items(presets) { (title, params) ->
                val (amt, rest) = params
                val (mid, fth, rnd) = rest
                val isSelected = kotlin.math.abs(vignette - amt) < 3f

                Surface(
                    onClick = { onApplyPreset(amt, mid, fth, rnd) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = title,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Amount Slider (-100..+100)
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
            minLabel = "Dark",
            maxLabel = "White",
            onDragStart = { onSliderDragStart("Vignette Amount", vigStr) },
            onValueChange = onVignetteChange,
            onValueChangeFinished = onSliderDragFinished
        )

        // Midpoint Slider (0..100)
        val midStr = "${vignetteMidpoint.toInt()}"
        LrSliderRow(
            title = "Midpoint",
            value = vignetteMidpoint,
            displayValue = midStr,
            valueRange = 0f..100f,
            minLabel = "Small",
            maxLabel = "Large",
            onDragStart = { onSliderDragStart("Vignette Midpoint", midStr) },
            onValueChange = onMidpointChange,
            onValueChangeFinished = onSliderDragFinished
        )

        // Feather Slider (0..100)
        val featherStr = "${vignetteFeather.toInt()}"
        LrSliderRow(
            title = "Feather",
            value = vignetteFeather,
            displayValue = featherStr,
            valueRange = 0f..100f,
            minLabel = "Hard",
            maxLabel = "Soft",
            onDragStart = { onSliderDragStart("Vignette Feather", featherStr) },
            onValueChange = onFeatherChange,
            onValueChangeFinished = onSliderDragFinished
        )

        // Roundness Slider (-100..+100)
        val roundStr = "${vignetteRoundness.toInt()}"
        LrSliderRow(
            title = "Roundness",
            value = vignetteRoundness,
            displayValue = roundStr,
            valueRange = -100f..100f,
            minLabel = "Rect",
            maxLabel = "Circle",
            onDragStart = { onSliderDragStart("Vignette Roundness", roundStr) },
            onValueChange = onRoundnessChange,
            onValueChangeFinished = onSliderDragFinished
        )
    }
}

/**
 * Dedicated Curves Domain Panel for interactive RGB and per-channel tone curve adjustments.
 */
@Composable
fun CurvesDomainPanel(
    curves: RgbCurves,
    activeChannel: CurveChannel,
    onSelectChannel: (CurveChannel) -> Unit,
    onOpenFullscreenCurves: () -> Unit,
    onApplyPreset: (String) -> Unit,
    onResetChannel: (CurveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Channel Selector Dots & Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel switcher dots: RGB (White), Red, Green, Blue
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val channels = listOf(
                    Triple(CurveChannel.RGB, Color.White, "RGB"),
                    Triple(CurveChannel.RED, Color(0xFFFF5252), "Red"),
                    Triple(CurveChannel.GREEN, Color(0xFF69F0AE), "Green"),
                    Triple(CurveChannel.BLUE, Color(0xFF448AFF), "Blue")
                )

                channels.forEach { (channel, dotColor, label) ->
                    val isSel = activeChannel == channel
                    Surface(
                        onClick = { onSelectChannel(channel) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSel) Color(0xFF2C2C2C) else Color(0xFF181818),
                        border = BorderStroke(1.dp, if (isSel) dotColor else Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                color = if (isSel) Color.White else Color(0xFFAAAAAA),
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Reset Channel Button
            IconButton(
                onClick = { onResetChannel(activeChannel) },
                modifier = Modifier.size(34.dp).testTag("curves_reset_channel_button")
            ) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = "Reset Channel",
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Visual Curve Graph Preview Canvas
        val currentPoints = when (activeChannel) {
            CurveChannel.RGB -> curves.master
            CurveChannel.RED -> curves.red
            CurveChannel.GREEN -> curves.green
            CurveChannel.BLUE -> curves.blue
        }

        val channelColor = when (activeChannel) {
            CurveChannel.RGB -> Color.White
            CurveChannel.RED -> Color(0xFFFF5252)
            CurveChannel.GREEN -> Color(0xFF69F0AE)
            CurveChannel.BLUE -> Color(0xFF448AFF)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0A0A))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(10.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val w = size.width
                val h = size.height

                // Draw grid lines
                val gridColor = Color.White.copy(alpha = 0.08f)
                for (step in 1..3) {
                    val frac = step / 4f
                    drawLine(gridColor, Offset(w * frac, 0f), Offset(w * frac, h), 1f)
                    drawLine(gridColor, Offset(0f, h * frac), Offset(w, h * frac), 1f)
                }

                // Reference diagonal
                drawLine(
                    Color.White.copy(alpha = 0.15f),
                    Offset(0f, h),
                    Offset(w, 0f),
                    1f
                )

                // Curve path
                if (currentPoints.isNotEmpty()) {
                    val path = Path()
                    val sorted = currentPoints.sortedBy { it.inVal }
                    val first = sorted.first()
                    path.moveTo(first.inVal * w, h - (first.outVal * h))

                    for (i in 1 until sorted.size) {
                        val pt = sorted[i]
                        path.lineTo(pt.inVal * w, h - (pt.outVal * h))
                    }

                    drawPath(
                        path = path,
                        color = channelColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                    )

                    // Control Points Dots
                    sorted.forEach { pt ->
                        val px = pt.inVal * w
                        val py = h - (pt.outVal * h)
                        drawCircle(Color.Black, radius = 5.dp.toPx(), center = Offset(px, py))
                        drawCircle(channelColor, radius = 3.5.dp.toPx(), center = Offset(px, py))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Open Full-screen Interactive Curve Editor Action
        Surface(
            onClick = onOpenFullscreenCurves,
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF222222),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth().height(38.dp).testTag("curves_open_fullscreen_button")
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Open Full-Screen Tone Curve Editor",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Quick Curve Presets
        Text(
            text = "Curve Tone Presets",
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val presets = listOf(
                "Linear" to "Linear",
                "Medium Contrast" to "Medium Contrast",
                "High Contrast" to "High Contrast",
                "Matte Fade" to "Matte Fade",
                "Cross Process" to "Cross Process"
            )
            items(presets) { (label, key) ->
                Surface(
                    onClick = { onApplyPreset(key) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Blur domain panel: slider + presets + reset.
 */
@Composable
fun BlurPanel(
    blur: Float,
    onBlurChange: (Float) -> Unit,
    onReset: () -> Unit,
    onSliderDragStart: (name: String, formattedVal: String) -> Unit,
    onSliderDragFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEmreEditorColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.panelSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BlurOn,
                    contentDescription = null,
                    tint = colors.primaryText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Blur",
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            if (blur > 0.1f) {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(36.dp).testTag("blur_reset_button")
                ) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = "Reset Blur",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        val blurStr = "${blur.toInt()}"
        LrSliderRow(
            title = "Blur Amount",
            value = blur,
            displayValue = blurStr,
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "100",
            onDragStart = { onSliderDragStart("Blur", blurStr) },
            onValueChange = onBlurChange,
            onValueChangeFinished = onSliderDragFinished
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Blur Presets",
            color = colors.secondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))

        val presets = listOf(
            "Off" to 0f,
            "Subtle Soft" to 15f,
            "Portrait Bokeh" to 35f,
            "Medium Blur" to 55f,
            "Heavy Dream" to 75f,
            "Max Obscure" to 100f
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(presets) { (label, value) ->
                val isSelected = kotlin.math.abs(blur - value) < 1.0f
                Surface(
                    onClick = {
                        onBlurChange(value)
                        onSliderDragFinished()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) colors.primaryText else colors.elevatedSurface,
                    border = BorderStroke(1.dp, if (isSelected) colors.primaryText else colors.border),
                    modifier = Modifier.height(30.dp).testTag("blur_preset_${label.lowercase().replace(' ', '_')}")
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = if (isSelected) colors.baseSurface else colors.primaryText,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
