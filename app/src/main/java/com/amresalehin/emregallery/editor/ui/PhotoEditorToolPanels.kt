package com.amresalehin.emregallery.editor.ui

import androidx.compose.animation.*
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*

@Composable
fun monochromeFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    labelColor = MaterialTheme.colorScheme.onSurface
)

data class ToolBarItem(
    val tool: EditorTool,
    val icon: ImageVector,
    val label: String,
    val hasBadge: Boolean = false,
    val badgeText: String? = null
)

val EditorToolBarItems = listOf(
    ToolBarItem(EditorTool.CROP, Icons.Default.Crop, "Crop"),
    ToolBarItem(EditorTool.ADJUST, Icons.Default.Tune, "Adjust"),
    ToolBarItem(EditorTool.EFFECTS, Icons.Default.AutoFixHigh, "Effects"),
    ToolBarItem(EditorTool.RETOUCH, Icons.Default.Face, "Retouch"),
    ToolBarItem(EditorTool.REMOVE_BG, Icons.Default.LayersClear, "Cutout & BG"),
    ToolBarItem(EditorTool.BRUSHES, Icons.Default.Brush, "Draw"),
    ToolBarItem(EditorTool.TEXT, Icons.Default.TextFields, "Text"),
    ToolBarItem(EditorTool.STICKER, Icons.Default.SentimentSatisfiedAlt, "Sticker"),
    ToolBarItem(EditorTool.SHAPE, Icons.Default.Category, "Shapes"),
    ToolBarItem(EditorTool.FRAME, Icons.Default.PhotoLibrary, "Frames"),
    ToolBarItem(EditorTool.CALLOUT, Icons.Default.ChatBubbleOutline, "Callout"),
    ToolBarItem(EditorTool.LENS_FLARE, Icons.Default.WbSunny, "Lens Flare"),
    ToolBarItem(EditorTool.AI_TOOLS, Icons.Default.AutoAwesome, "AI Magic"),
    ToolBarItem(EditorTool.REPLAY, Icons.Default.Replay, "Replay")
)

/**
 * Bottom horizontally scrollable tools navigation bar using native monochrome styling.
 */
@Composable
fun PhotoEditorBottomBar(
    activeTool: EditorTool,
    onSelectTool: (EditorTool) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorToolBarItems.forEach { item ->
                val isSelected = activeTool == item.tool
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onSelectTool(item.tool) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("tool_${item.label.lowercase().replace(" ", "_")}")
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                                    else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // PRO badge in native monochrome
                        if (item.hasBadge && item.badgeText != null) {
                            Box(
                                modifier = Modifier
                                    .offset(x = 6.dp, y = (-4).dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = item.badgeText,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Grid drawer of tools matching the video's "Tools" menu.
 */
@Composable
fun ToolsGridSheet(
    onSelectSubItem: (ToolSheetSubItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Pair(ToolSheetSubItem.CROP, Icons.Default.Crop),
        Pair(ToolSheetSubItem.FREE_CROP, Icons.Default.CropFree),
        Pair(ToolSheetSubItem.SHAPE_CROP, Icons.Default.Category),
        Pair(ToolSheetSubItem.DISPERSION, Icons.Default.BlurOn),
        Pair(ToolSheetSubItem.CLONE, Icons.Default.ContentCopy),
        Pair(ToolSheetSubItem.AI_REPLACE, Icons.Default.AutoFixHigh),
        Pair(ToolSheetSubItem.STRETCH, Icons.Default.OpenInFull),
        Pair(ToolSheetSubItem.MOTION, Icons.Default.FastForward),
        Pair(ToolSheetSubItem.SELECTION, Icons.Default.SelectAll),
        Pair(ToolSheetSubItem.CURVES, Icons.Default.ShowChart),
        Pair(ToolSheetSubItem.ADJUST, Icons.Default.Tune),
        Pair(ToolSheetSubItem.ENHANCE, Icons.Default.AutoAwesome),
        Pair(ToolSheetSubItem.TILT_SHIFT, Icons.Default.Camera),
        Pair(ToolSheetSubItem.PERSPECTIVE, Icons.Default.Transform),
        Pair(ToolSheetSubItem.RESIZE, Icons.Default.PhotoSizeSelectLarge),
        Pair(ToolSheetSubItem.ROTATE, Icons.Default.RotateRight),
        Pair(ToolSheetSubItem.AI_EXPAND, Icons.Default.AspectRatio)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Tools",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val chunked = items.chunked(4)
        for (row in chunked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                for ((subItem, icon) in row) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clickable { onSelectSubItem(subItem) }
                            .padding(vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = subItem.label, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subItem.label,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Crop & Geometry adjustment panel.
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Quick Actions: Rotate, Flip H, Flip V
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onRotate90) {
                Icon(Icons.Default.RotateRight, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rotate 90°")
            }
            TextButton(onClick = onFlipH) {
                Icon(Icons.Default.Flip, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Flip H")
            }
            TextButton(onClick = onFlipV) {
                Icon(Icons.Default.Flip, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Flip V")
            }
        }

        // Straighten Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Straighten", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(70.dp))
            Slider(
                value = straightenAngle,
                onValueChange = onStraightenChange,
                onValueChangeFinished = onStraightenFinished,
                valueRange = -45f..45f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )
            Text("${straightenAngle.toInt()}°", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
        }

        // Aspect ratio presets row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            items(AspectRatioPreset.entries) { preset ->
                val isSelected = selectedPreset == preset
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.label) },
                    colors = monochromeFilterChipColors()
                )
            }
        }
    }
}

/**
 * Photo Adjustments panel (Brightness, Contrast, Saturation, etc.)
 */
@Composable
fun AdjustmentsPanel(
    activeProperty: String,
    currentValue: Float,
    onSelectProperty: (String) -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val properties = listOf(
        "Brightness", "Contrast", "Saturation", "Exposure",
        "Highlights", "Shadows", "Temperature", "Tint",
        "Sharpness", "Blur", "Vignette", "Grain"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        // Value slider with label
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(activeProperty, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
            Slider(
                value = currentValue,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = if (activeProperty in listOf("Sharpness", "Blur", "Vignette", "Grain")) 0f..100f else -100f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )
            Text(currentValue.toInt().toString(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
        }

        Spacer(Modifier.height(8.dp))

        // Property tabs
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(properties) { prop ->
                val isSelected = activeProperty == prop
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectProperty(prop) },
                    label = { Text(prop) },
                    colors = monochromeFilterChipColors()
                )
            }
        }
    }
}

/**
 * Preset Effects & Filters panel.
 */
@Composable
fun EffectsFiltersPanel(
    selectedFilter: FilterType,
    filterIntensity: Float,
    onSelectFilter: (FilterType) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onIntensityChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        if (selectedFilter != FilterType.NONE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Intensity", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(60.dp))
                Slider(
                    value = filterIntensity,
                    onValueChange = onIntensityChange,
                    onValueChangeFinished = onIntensityChangeFinished,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text("${(filterIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            items(FilterType.entries) { filter ->
                val isSelected = selectedFilter == filter
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onSelectFilter(filter) }
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when (filter) {
                                    FilterType.NONE -> Color(0xFF606060)
                                    FilterType.VIVID -> Color(0xFF505050)
                                    FilterType.FILM -> Color(0xFF888888)
                                    FilterType.NOIR -> Color(0xFF1A1A1A)
                                    FilterType.BW -> Color(0xFFAAAAAA)
                                    FilterType.WARM_SUNSET -> Color(0xFF757575)
                                    FilterType.CYBER -> Color(0xFF5A5A5A)
                                    FilterType.VINTAGE -> Color(0xFF6E6E6E)
                                    FilterType.SEPIA -> Color(0xFF858585)
                                    FilterType.DRAMATIC -> Color(0xFF333333)
                                    FilterType.PASTEL -> Color(0xFFB0B0B0)
                                    FilterType.EMERALD -> Color(0xFF484848)
                                    FilterType.COLD_MIST -> Color(0xFF9E9E9E)
                                }
                            )
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = filter.displayName,
                        fontSize = 10.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Retouch Panel (Blemish, Object Removal, Skin Smooth, Red Eye).
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
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Brush Size", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(80.dp))
            Slider(
                value = brushRadius,
                onValueChange = onRadiusChange,
                valueRange = 8f..80f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )
            Text("${brushRadius.toInt()}px", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(RetouchType.entries) { type ->
                val isSelected = activeType == type
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectType(type) },
                    label = { Text(type.label) },
                    colors = monochromeFilterChipColors()
                )
            }
        }
    }
}

/**
 * Background Cutout & Replacement panel.
 */
@Composable
fun RemoveBgPanel(
    currentMode: BackgroundMode,
    blurRadius: Float,
    onSelectMode: (BackgroundMode) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
    onBlurRadiusChangeFinished: (() -> Unit)? = null,
    onSelectColor: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        if (currentMode == BackgroundMode.BLUR) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Blur Depth", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(80.dp))
                Slider(
                    value = blurRadius,
                    onValueChange = onBlurRadiusChange,
                    onValueChangeFinished = onBlurRadiusChangeFinished,
                    valueRange = 5f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text("${blurRadius.toInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(30.dp))
            }
        }

        Spacer(Modifier.height(6.dp))

        // Mode choices
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(BackgroundMode.entries) { mode ->
                val isSelected = currentMode == mode
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectMode(mode) },
                    label = { Text(mode.label) },
                    colors = monochromeFilterChipColors()
                )
            }
        }

        // Color swatches if SOLID mode
        if (currentMode == BackgroundMode.SOLID) {
            val colors = listOf(Color.White, Color(0xFFEEEEEE), Color(0xFFCCCCCC), Color(0xFF888888), Color(0xFF444444), Color(0xFF222222), Color.Black)
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                colors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(1.dp, Color.Gray, CircleShape)
                            .clickable { onSelectColor(c) }
                    )
                }
            }
        }
    }
}

/**
 * Brushes & Drawing panel.
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
    val palette = listOf(
        Color.White, Color(0xFFE0E0E0), Color(0xFFAAAAAA), Color(0xFF757575),
        Color(0xFF424242), Color.Black, Color(0xFF263238), Color(0xFF37474F)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Width", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(50.dp))
            Slider(
                value = brushWidth,
                onValueChange = onWidthChange,
                valueRange = 2f..48f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )
            Text("${brushWidth.toInt()}dp", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
        }

        Spacer(Modifier.height(6.dp))

        // Brush type selector
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(BrushType.entries) { type ->
                val isSelected = selectedBrush == type
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectBrush(type) },
                    label = { Text(type.label) },
                    colors = monochromeFilterChipColors()
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Color palette in monochrome & neutrals
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            palette.forEach { c ->
                val isSelected = brushColor == c
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray, CircleShape)
                        .clickable { onColorSelect(c) }
                )
            }
        }
    }
}

/**
 * AI Suite Bottom Sheet.
 */
@Composable
fun AiToolsSheet(
    aiSettings: AiEditSettings,
    onToggleAutoEnhance: (Boolean) -> Unit,
    onSelectStyle: (AiStyleTransfer?) -> Unit,
    onSelectTryOn: (AiTryOnStyle?) -> Unit,
    onExpandChange: (Float) -> Unit,
    onExpandChangeFinished: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("AI Photo Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close")
            }
        }

        Spacer(Modifier.height(16.dp))

        // AI Auto Enhance Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Auto-Enhance", fontWeight = FontWeight.Bold)
                    Text("Intelligent tone balance, clarity & dynamic range", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = aiSettings.autoEnhanceEnabled,
                    onCheckedChange = onToggleAutoEnhance,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // AI Try-On & Outfits
        Text("AI Virtual Try-On", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = aiSettings.activeTryOn == null,
                    onClick = { onSelectTryOn(null) },
                    label = { Text("None") },
                    colors = monochromeFilterChipColors()
                )
            }
            items(AiTryOnStyle.entries) { style ->
                FilterChip(
                    selected = aiSettings.activeTryOn == style,
                    onClick = { onSelectTryOn(style) },
                    label = { Text(style.label) },
                    colors = monochromeFilterChipColors()
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // AI Style Transfer
        Text("Neural Style Transfer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = aiSettings.activeStyleTransfer == null,
                    onClick = { onSelectStyle(null) },
                    label = { Text("Original") },
                    colors = monochromeFilterChipColors()
                )
            }
            items(AiStyleTransfer.entries) { style ->
                FilterChip(
                    selected = aiSettings.activeStyleTransfer == style,
                    onClick = { onSelectStyle(style) },
                    label = { Text(style.label) },
                    colors = monochromeFilterChipColors()
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // AI Expand Outpaint
        Text("AI Canvas Expand", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = aiSettings.expandOutpaintRatio,
                onValueChange = onExpandChange,
                onValueChangeFinished = onExpandChangeFinished,
                valueRange = 0f..0.4f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("${(aiSettings.expandOutpaintRatio * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Sticker & Emoji Picker Sheet.
 */
@Composable
fun StickerPickerSheet(
    onSelectSticker: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stickers = listOf(
        "✨", "🔥", "⭐", "🎉", "🕶️", "🚀", "👑", "💯",
        "⚡", "🌈", "📸", "🎨", "🌟", "😎", "🤩", "🥳",
        "🙌", "💫", "🎯", "💎", "⏳", "🎬", "💡", "✈️"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stickers & Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
        }

        Spacer(Modifier.height(12.dp))

        val chunked = stickers.chunked(6)
        for (row in chunked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                for (emoji in row) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectSticker(emoji); onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 26.sp)
                    }
                }
            }
        }
    }
}

/**
 * Export and Save Modal Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    onConfirmSave: (isPng: Boolean, quality: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var isPng by remember { mutableStateOf(false) }
    var quality by remember { mutableIntStateOf(95) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Export Photo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            // Format Selection
            Text("Format", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = !isPng,
                    onClick = { isPng = false },
                    label = { Text("JPEG (Best for Photos)") },
                    colors = monochromeFilterChipColors()
                )
                FilterChip(
                    selected = isPng,
                    onClick = { isPng = true },
                    label = { Text("PNG (Transparent)") },
                    colors = monochromeFilterChipColors()
                )
            }

            if (!isPng) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quality", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(60.dp))
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { quality = it.toInt() },
                        valueRange = 60f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text("$quality%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onConfirmSave(isPng, quality); onDismiss() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("confirm_export_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Save to Gallery", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
