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
        // Top Header Row
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
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Blemish Eraser",
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (strokeCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AccentBlue.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, AccentBlue)
                    ) {
                        Text(
                            text = "$strokeCount applied",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
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

        // Brush Radius Slider
        LrSliderRow(
            title = "Brush Radius",
            value = brushRadius,
            displayValue = "${brushRadius.toInt()} px",
            valueRange = 4f..120f,
            minLabel = "4 px",
            maxLabel = "120 px",
            onDragStart = {},
            onValueChange = onRadiusChange,
            onValueChangeFinished = {}
        )

        // Removal Strength Slider
        LrSliderRow(
            title = "Removal Strength",
            value = intensity * 100f,
            displayValue = "${(intensity * 100).toInt()}%",
            valueRange = 10f..100f,
            minLabel = "10%",
            maxLabel = "100%",
            onDragStart = {},
            onValueChange = { onIntensityChange(it / 100f) },
            onValueChangeFinished = {}
        )

        // Helper instruction card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.elevatedSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Tap directly on pimples, spots, or skin marks to erase them seamlessly.",
                    color = colors.secondaryText,
                    fontSize = 11.sp
                )
            }
        }
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
        // Top Header Row
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
                    imageVector = Icons.Default.AutoFixNormal,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Skin Smoothing",
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (strokeCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AccentBlue.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, AccentBlue)
                    ) {
                        Text(
                            text = "$strokeCount applied",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
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

        // Brush Radius Slider
        LrSliderRow(
            title = "Brush Radius",
            value = brushRadius,
            displayValue = "${brushRadius.toInt()} px",
            valueRange = 8f..150f,
            minLabel = "8 px",
            maxLabel = "150 px",
            onDragStart = {},
            onValueChange = onRadiusChange,
            onValueChangeFinished = {}
        )

        // Smoothing Intensity Slider
        LrSliderRow(
            title = "Smoothing Strength",
            value = intensity * 100f,
            displayValue = "${(intensity * 100).toInt()}%",
            valueRange = 10f..100f,
            minLabel = "10%",
            maxLabel = "100%",
            onDragStart = {},
            onValueChange = { onIntensityChange(it / 100f) },
            onValueChangeFinished = {}
        )

        // Helper instruction card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.elevatedSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Brush gently over skin areas to soften wrinkles while preserving natural texture.",
                    color = colors.secondaryText,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Dedicated Domain Panel for Healing & Patch
 */
@Composable
fun HealDomainPanel(
    activeType: RetouchType,
    onSelectType: (RetouchType) -> Unit,
    brushRadius: Float,
    onRadiusChange: (Float) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    strokeCount: Int,
    onClearStrokes: () -> Unit,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    val healModes = listOf(
        RetouchType.PATCH_HEALING to "Patch Healing",
        RetouchType.CLONE_STAMP to "Clone Stamp",
        RetouchType.OBJECT_REMOVAL to "Object Erase",
        RetouchType.WRINKLE_REMOVAL to "Wrinkle Fix"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Row
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
                    imageVector = Icons.Default.Healing,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Healing & Patch",
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (strokeCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AccentBlue.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, AccentBlue)
                    ) {
                        Text(
                            text = "$strokeCount applied",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
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

        // Healing Mode Selector
        Text(
            text = "Healing Technique:",
            color = colors.secondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            healModes.forEach { (mode, label) ->
                val isSelected = activeType == mode
                Surface(
                    onClick = { onSelectType(mode) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) AccentBlue.copy(alpha = 0.2f) else colors.elevatedSurface,
                    border = BorderStroke(1.dp, if (isSelected) AccentBlue else colors.border),
                    modifier = Modifier.testTag("heal_mode_${mode.name.lowercase()}")
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) AccentBlue else colors.primaryText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Brush Radius Slider
        LrSliderRow(
            title = "Brush Radius",
            value = brushRadius,
            displayValue = "${brushRadius.toInt()} px",
            valueRange = 6f..140f,
            minLabel = "6 px",
            maxLabel = "140 px",
            onDragStart = {},
            onValueChange = onRadiusChange,
            onValueChangeFinished = {}
        )

        // Blend Strength Slider
        LrSliderRow(
            title = "Blend Strength",
            value = intensity * 100f,
            displayValue = "${(intensity * 100).toInt()}%",
            valueRange = 10f..100f,
            minLabel = "10%",
            maxLabel = "100%",
            onDragStart = {},
            onValueChange = { onIntensityChange(it / 100f) },
            onValueChangeFinished = {}
        )
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
        // Top Header Row
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
                    imageVector = Icons.Default.ColorLens,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Skin Tone Correction",
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (strokeCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AccentBlue.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, AccentBlue)
                    ) {
                        Text(
                            text = "$strokeCount applied",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
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

        // Palette Swatches Row
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

        // Brush Radius Slider
        LrSliderRow(
            title = "Brush Radius",
            value = brushRadius,
            displayValue = "${brushRadius.toInt()} px",
            valueRange = 8f..150f,
            minLabel = "8 px",
            maxLabel = "150 px",
            onDragStart = {},
            onValueChange = onRadiusChange,
            onValueChangeFinished = {}
        )

        // Tint Warmth / Opacity Slider
        LrSliderRow(
            title = "Tone Warmth / Opacity",
            value = intensity * 100f,
            displayValue = "${(intensity * 100).toInt()}%",
            valueRange = 10f..100f,
            minLabel = "10%",
            maxLabel = "100%",
            onDragStart = {},
            onValueChange = { onIntensityChange(it / 100f) },
            onValueChangeFinished = {}
        )
    }
}
