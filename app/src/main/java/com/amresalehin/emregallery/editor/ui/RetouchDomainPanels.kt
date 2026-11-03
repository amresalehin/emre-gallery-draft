package com.amresalehin.emregallery.editor.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.RetouchType

private val AccentBlue = Color(0xFF2979FF)

/**
 * Shared Retouch Domain Header Row with Title, Icon, and Clear All action.
 */
@Composable
fun RetouchDomainHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    strokeCount: Int,
    onClearStrokes: () -> Unit,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        if (strokeCount > 0) {
            TextButton(
                onClick = onClearStrokes,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Clear All", color = Color(0xFFFF5252), fontSize = 12.sp)
            }
        }
    }
}

/**
 * Shared Retouch Brush Radius Slider
 */
@Composable
fun RetouchRadiusSlider(
    radius: Float,
    onRadiusChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 4f..150f
) {
    LrSliderRow(
        title = "Brush Radius",
        value = radius,
        displayValue = "${radius.toInt()} px",
        valueRange = valueRange,
        unit = "px",
        minLabel = "${valueRange.start.toInt()} px",
        maxLabel = "${valueRange.endInclusive.toInt()} px",
        onDragStart = {},
        onValueChange = onRadiusChange,
        onValueChangeFinished = {}
    )
}

/**
 * Shared Retouch Strength / Intensity Slider
 */
@Composable
fun RetouchStrengthSlider(
    title: String,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 10f..100f
) {
    LrPercentageSliderRow(
        title = title,
        value = intensity * 100f,
        valueRange = valueRange,
        unit = "%",
        minLabel = "${valueRange.start.toInt()}%",
        maxLabel = "${valueRange.endInclusive.toInt()}%",
        onSliderDragStart = { _, _ -> },
        onSliderChange = { _, v -> onIntensityChange(v / 100f) },
        onSliderChangeFinished = {}
    )
}

/**
 * Dedicated Domain Panel for Blemish Removal
 */
@Composable
fun BlemishDomainPanel(
    brushRadius: Float,
    onRadiusChange: (Float) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    strokeCount: Int,
    onClearStrokes: () -> Unit,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RetouchDomainHeader(
            title = "Blemish Eraser",
            icon = Icons.Default.Face,
            strokeCount = strokeCount,
            onClearStrokes = onClearStrokes,
            colors = colors
        )
        RetouchRadiusSlider(radius = brushRadius, onRadiusChange = onRadiusChange, valueRange = 4f..120f)
        RetouchStrengthSlider(title = "Removal Strength", intensity = intensity, onIntensityChange = onIntensityChange)
    }
}

/**
 * Dedicated Domain Panel for Skin Smoothing
 */
@Composable
fun SmoothDomainPanel(
    brushRadius: Float,
    onRadiusChange: (Float) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    strokeCount: Int,
    onClearStrokes: () -> Unit,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RetouchDomainHeader(
            title = "Skin Smoothing",
            icon = Icons.Default.AutoFixNormal,
            strokeCount = strokeCount,
            onClearStrokes = onClearStrokes,
            colors = colors
        )
        RetouchRadiusSlider(radius = brushRadius, onRadiusChange = onRadiusChange, valueRange = 8f..150f)
        RetouchStrengthSlider(title = "Smoothing Strength", intensity = intensity, onIntensityChange = onIntensityChange)
    }
}

/**
 * Dedicated Domain Panel for Healing & Patch
 */
@Composable
fun HealDomainPanel(
    brushRadius: Float,
    onRadiusChange: (Float) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    strokeCount: Int,
    onClearStrokes: () -> Unit,
    modifier: Modifier = Modifier,
    activeType: RetouchType = RetouchType.PATCH_HEALING,
    onSelectType: (RetouchType) -> Unit = {},
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RetouchDomainHeader(
            title = "Healing & Patch",
            icon = Icons.Default.Healing,
            strokeCount = strokeCount,
            onClearStrokes = onClearStrokes,
            colors = colors
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val healTypes = listOf(
                RetouchType.PATCH_HEALING to "Patch",
                RetouchType.CLONE_STAMP to "Clone",
                RetouchType.OBJECT_REMOVAL to "Remove",
                RetouchType.WRINKLE_REMOVAL to "Wrinkle"
            )
            items(healTypes) { (type, label) ->
                val isSelected = activeType == type
                Surface(
                    onClick = { onSelectType(type) },
                    shape = CircleShape,
                    color = if (isSelected) AccentBlue else Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, if (isSelected) AccentBlue else colors.border)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else colors.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        RetouchRadiusSlider(radius = brushRadius, onRadiusChange = onRadiusChange, valueRange = 6f..140f)
        RetouchStrengthSlider(title = "Blend Strength", intensity = intensity, onIntensityChange = onIntensityChange)
    }
}

/**
 * Dedicated Domain Panel for Skin Tone Correction
 */
@Composable
fun SkinToneDomainPanel(
    brushRadius: Float,
    onRadiusChange: (Float) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    tintColor: Color,
    onTintColorChange: (Color) -> Unit,
    strokeCount: Int,
    onClearStrokes: () -> Unit,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    val skinSwatches = listOf(
        Color(0xFFFFE0B2) to "Fair",
        Color(0xFFFFCC80) to "Light",
        Color(0xFFD7CCC8) to "Ivory",
        Color(0xFFBCAAA4) to "Beige",
        Color(0xFF8D6E63) to "Warm",
        Color(0xFF6D4C41) to "Amber",
        Color(0xFF5D4037) to "Mocha",
        Color(0xFFFFAB91) to "Blush"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RetouchDomainHeader(
            title = "Skin Tone Correction",
            icon = Icons.Default.ColorLens,
            strokeCount = strokeCount,
            onClearStrokes = onClearStrokes,
            colors = colors
        )

        Text(
            text = "Tone Palette:",
            color = colors.secondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(skinSwatches) { (swatch, label) ->
                val isSelected = tintColor == swatch
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onTintColorChange(swatch) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                BorderStroke(
                                    2.dp,
                                    if (isSelected) AccentBlue else colors.border
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = if (swatch.red + swatch.green + swatch.blue > 2f) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = if (isSelected) AccentBlue else colors.secondaryText,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        RetouchRadiusSlider(radius = brushRadius, onRadiusChange = onRadiusChange, valueRange = 8f..150f)
        RetouchStrengthSlider(title = "Tone Warmth / Opacity", intensity = intensity, onIntensityChange = onIntensityChange)
    }
}
