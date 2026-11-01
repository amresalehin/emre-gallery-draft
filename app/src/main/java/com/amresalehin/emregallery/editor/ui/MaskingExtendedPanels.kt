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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*

enum class MaskingSelectionType(val label: String) {
    BRUSH("Draw Brush"),
    MAGIC_WAND("Magic Wand"),
    LASSO("Lasso"),
    POLYGON("Polygon"),
    ELLIPSE("Ellipse")
}

/**
 * Masking & Selection Panel with Magic Wand, Lasso, Polygon, Ellipse,
 * Brush Opacity, Flow, Brush Width, and Custom Color Palette.
 */
@Composable
fun MaskingExtendedPanel(
    selectedType: MaskingSelectionType,
    onSelectType: (MaskingSelectionType) -> Unit,
    selectedBrush: BrushType,
    onSelectBrush: (BrushType) -> Unit,
    brushColor: Color,
    onColorSelect: (Color) -> Unit,
    brushWidth: Float,
    onWidthChange: (Float) -> Unit,
    brushOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    brushFlow: Float,
    onFlowChange: (Float) -> Unit,
    magicWandTolerance: Float,
    onMagicWandToleranceChange: (Float) -> Unit,
    isSelectionActive: Boolean,
    onInvertMask: () -> Unit,
    onClearMask: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Invert Mask & Clear Actions at Top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onInvertMask,
                modifier = Modifier.weight(1f).height(36.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.InvertColors, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Invert Mask", color = Color.White, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onClearMask,
                modifier = Modifier.weight(1f).height(36.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clear Mask", color = Color(0xFFFF5252), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Tool-specific parameter controls
        when (selectedType) {
            MaskingSelectionType.BRUSH -> {
                // Brush Width Slider
                LrSliderRow(
                    title = "Brush Size",
                    value = brushWidth,
                    displayValue = "${brushWidth.toInt()} px",
                    valueRange = 2f..80f,
                    minLabel = "2 px",
                    maxLabel = "80 px",
                    onDragStart = {},
                    onValueChange = onWidthChange,
                    onValueChangeFinished = {}
                )

                // Brush Opacity Slider
                LrSliderRow(
                    title = "Brush Opacity",
                    value = brushOpacity * 100f,
                    displayValue = "${(brushOpacity * 100).toInt()}%",
                    valueRange = 10f..100f,
                    minLabel = "10%",
                    maxLabel = "100%",
                    onDragStart = {},
                    onValueChange = { onOpacityChange(it / 100f) },
                    onValueChangeFinished = {}
                )

                // Brush Flow Slider
                LrSliderRow(
                    title = "Brush Flow",
                    value = brushFlow * 100f,
                    displayValue = "${(brushFlow * 100).toInt()}%",
                    valueRange = 10f..100f,
                    minLabel = "10%",
                    maxLabel = "100%",
                    onDragStart = {},
                    onValueChange = { onFlowChange(it / 100f) },
                    onValueChangeFinished = {}
                )

                Spacer(Modifier.height(8.dp))

                // Rich Color Palette Swatches
                Text("Color Palette", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val colors = listOf(
                        Color.White, Color(0xFFE0E0E0), Color(0xFF757575), Color.Black,
                        Color(0xFFFF1744), Color(0xFFFF5252), Color(0xFFFF9100), Color(0xFFFFEA00),
                        Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFF2979FF), Color(0xFF651FFF),
                        Color(0xFFD500F9), Color(0xFFF50057), Color(0xFF795548), Color(0xFF607D8B)
                    )
                    items(colors) { c ->
                        val isSelected = brushColor == c
                        Surface(
                            onClick = { onColorSelect(c) },
                            shape = CircleShape,
                            color = c,
                            border = BorderStroke(if (isSelected) 2.5.dp else 1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (c == Color.White || c == Color(0xFFFFEA00)) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Tier 3: Brush Types Row (situated directly above Tier 2)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(BrushType.entries) { brush ->
                        val isSelected = selectedBrush == brush
                        Surface(
                            onClick = { onSelectBrush(brush) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color.White else Color(0xFF222222),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = brush.label,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
            MaskingSelectionType.MAGIC_WAND -> {
                Text("Magic Wand Color Selector", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                LrSliderRow(
                    title = "Tolerance",
                    value = magicWandTolerance,
                    displayValue = "±${magicWandTolerance.toInt()}",
                    valueRange = 5f..90f,
                    minLabel = "5",
                    maxLabel = "90",
                    onDragStart = {},
                    onValueChange = onMagicWandToleranceChange,
                    onValueChangeFinished = {}
                )
                Text(
                    text = "Tap any color region on the photo canvas to select connected pixels.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            MaskingSelectionType.LASSO -> {
                Text("Lasso Freehand Selection", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Draw an enclosing loop around any object on the canvas to create a custom mask.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }
            MaskingSelectionType.POLYGON -> {
                Text("Polygon Vertex Selection", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tap sequentially on the canvas to place straight-edge polygonal anchor points.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }
            MaskingSelectionType.ELLIPSE -> {
                Text("Elliptical Radial Selection", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Drag from the center outwards on the canvas to select an elliptical or circular mask region.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Tier 2: Selection Mode Switcher Pills (Brush, Magic Wand, Lasso, Polygon, Ellipse) - docked right above bottom bar
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MaskingSelectionType.entries) { type ->
                val isSelected = selectedType == type
                val icon = when (type) {
                    MaskingSelectionType.BRUSH -> Icons.Default.Brush
                    MaskingSelectionType.MAGIC_WAND -> Icons.Default.AutoFixNormal
                    MaskingSelectionType.LASSO -> Icons.Default.Gesture
                    MaskingSelectionType.POLYGON -> Icons.Default.Polyline
                    MaskingSelectionType.ELLIPSE -> Icons.Default.PanoramaFishEye
                }
                Surface(
                    onClick = { onSelectType(type) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = type.label,
                            tint = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = type.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
