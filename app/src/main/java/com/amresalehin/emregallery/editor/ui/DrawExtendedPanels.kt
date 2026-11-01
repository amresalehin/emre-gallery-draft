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
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*

enum class DrawSubTab(val label: String, val icon: ImageVector) {
    BRUSH("Brush", Icons.Default.Brush),
    ERASER("Eraser", Icons.Default.AutoFixNormal),
    COLOR("Color", Icons.Default.Palette),
    SHAPES("Shapes", Icons.Default.Category),
    LAYERS("Layers", Icons.Default.Layers),
    SYMMETRY("Symmetry", Icons.Default.Flip)
}

/**
 * Refactored PicsArt-style Draw Panel retaining Emre's grey-white-black monochrome design system.
 * Free of redundant buttons, unnecessary chatty explanations, and colored UI chrome.
 */
@Composable
fun DrawExtendedPanel(
    selectedBrush: BrushType,
    onSelectBrush: (BrushType) -> Unit,
    brushColor: Color,
    onColorSelect: (Color) -> Unit,
    brushWidth: Float,
    onWidthChange: (Float) -> Unit,
    brushOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    brushHardness: Float,
    onHardnessChange: (Float) -> Unit,
    brushFlow: Float,
    onFlowChange: (Float) -> Unit,

    // Eyedropper
    isEyedropperActive: Boolean,
    onToggleEyedropper: () -> Unit,

    // Layers
    drawLayers: List<DrawLayer>,
    activeLayerId: String,
    onAddLayer: () -> Unit,
    onSelectLayer: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onDuplicateLayer: (String) -> Unit,
    onToggleLayerVisibility: (String) -> Unit,
    onToggleLayerLock: (String) -> Unit,
    onSetLayerOpacity: (String, Float) -> Unit,
    onCommitLayerOpacity: () -> Unit,
    onSetLayerBlendMode: (String, DrawBlendMode) -> Unit,
    onMergeLayerDown: (String) -> Unit,
    onMoveLayer: (String, Int) -> Unit,

    // Quick Canvas Actions
    onClearLayer: () -> Unit,
    onInvertStrokes: () -> Unit,

    // Symmetry
    symmetryMode: DrawSymmetryMode,
    onSelectSymmetryMode: (DrawSymmetryMode) -> Unit,

    // Shape Stamping
    onStampShape: (shape: DrawShapeType, isFilled: Boolean, strokeWidth: Float, radius: Float) -> Unit,

    modifier: Modifier = Modifier
) {
    var activeSubTab by remember {
        mutableStateOf(if (selectedBrush == BrushType.ERASER) DrawSubTab.ERASER else DrawSubTab.BRUSH)
    }

    var recentColors by remember {
        mutableStateOf(
            listOf(
                Color.White, Color(0xFFE0E0E0), Color(0xFF757575), Color.Black,
                Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00),
                Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFFD500F9)
            )
        )
    }

    // Previous brush cache when toggling between eraser and brushes
    var lastDrawingBrush by remember {
        mutableStateOf(if (selectedBrush != BrushType.ERASER) selectedBrush else BrushType.PEN)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // --- 1. Clean Emre-Style Monochrome Navigation Pill Dock ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawSubTab.entries.forEach { tab ->
                val isSelected = activeSubTab == tab
                Surface(
                    onClick = {
                        activeSubTab = tab
                        if (tab == DrawSubTab.ERASER) {
                            if (selectedBrush != BrushType.ERASER) {
                                lastDrawingBrush = selectedBrush
                            }
                            onSelectBrush(BrushType.ERASER)
                        } else if (tab == DrawSubTab.BRUSH && selectedBrush == BrushType.ERASER) {
                            onSelectBrush(lastDrawingBrush)
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) Color.White else Color(0xFF1E1E1E),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) Color.Black else Color(0xFF8E8E93),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (tab == DrawSubTab.LAYERS) "${tab.label} (${drawLayers.size})" else tab.label,
                            color = if (isSelected) Color.Black else Color(0xFFCCCCCC),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

        // --- 2. Sub-tab Content Pane ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            when (activeSubTab) {
                DrawSubTab.BRUSH -> {
                    BrushPane(
                        selectedBrush = if (selectedBrush == BrushType.ERASER) lastDrawingBrush else selectedBrush,
                        onSelectBrush = {
                            lastDrawingBrush = it
                            onSelectBrush(it)
                        },
                        brushColor = brushColor,
                        brushWidth = brushWidth,
                        onWidthChange = onWidthChange,
                        brushOpacity = brushOpacity,
                        onOpacityChange = onOpacityChange,
                        brushHardness = brushHardness,
                        onHardnessChange = onHardnessChange
                    )
                }
                DrawSubTab.ERASER -> {
                    EraserPane(
                        eraserWidth = brushWidth,
                        onWidthChange = onWidthChange,
                        eraserOpacity = brushOpacity,
                        onOpacityChange = onOpacityChange,
                        eraserHardness = brushHardness,
                        onHardnessChange = onHardnessChange,
                        onClearLayer = onClearLayer
                    )
                }
                DrawSubTab.COLOR -> {
                    ColorPane(
                        brushColor = brushColor,
                        onColorSelect = { col ->
                            onColorSelect(col)
                            if (col !in recentColors) {
                                recentColors = (listOf(col) + recentColors).take(10)
                            }
                        },
                        isEyedropperActive = isEyedropperActive,
                        onToggleEyedropper = onToggleEyedropper,
                        recentColors = recentColors
                    )
                }
                DrawSubTab.SHAPES -> {
                    ShapesPane(
                        brushColor = brushColor,
                        brushWidth = brushWidth,
                        brushOpacity = brushOpacity,
                        onStampShape = onStampShape
                    )
                }
                DrawSubTab.LAYERS -> {
                    LayersPane(
                        drawLayers = drawLayers,
                        activeLayerId = activeLayerId,
                        onAddLayer = onAddLayer,
                        onSelectLayer = onSelectLayer,
                        onDeleteLayer = onDeleteLayer,
                        onDuplicateLayer = onDuplicateLayer,
                        onToggleLayerVisibility = onToggleLayerVisibility,
                        onToggleLayerLock = onToggleLayerLock,
                        onSetLayerOpacity = onSetLayerOpacity,
                        onCommitLayerOpacity = onCommitLayerOpacity,
                        onSetLayerBlendMode = onSetLayerBlendMode,
                        onMergeLayerDown = onMergeLayerDown,
                        onMoveLayer = onMoveLayer,
                        onClearLayer = onClearLayer,
                        onInvertStrokes = onInvertStrokes
                    )
                }
                DrawSubTab.SYMMETRY -> {
                    SymmetryPane(
                        symmetryMode = symmetryMode,
                        onSelectSymmetryMode = onSelectSymmetryMode
                    )
                }
            }
        }
    }
}

/**
 * PicsArt Brush Settings:
 * Clean interactive preview card + brush presets carousel + size/opacity/hardness sliders.
 */
@Composable
private fun BrushPane(
    selectedBrush: BrushType,
    onSelectBrush: (BrushType) -> Unit,
    brushColor: Color,
    brushWidth: Float,
    onWidthChange: (Float) -> Unit,
    brushOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    brushHardness: Float,
    onHardnessChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Live Brush Stroke Preview Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1B1B1B),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.width(96.dp)) {
                    Text(
                        text = selectedBrush.label,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${brushWidth.toInt()}px • ${(brushOpacity * 100).toInt()}% • ${(brushHardness * 100).toInt()}%",
                        color = Color(0xFF8E8E93),
                        fontSize = 10.sp
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val path = Path().apply {
                            moveTo(12f, h * 0.65f)
                            cubicTo(
                                w * 0.35f, h * 0.15f,
                                w * 0.65f, h * 0.85f,
                                w - 12f, h * 0.35f
                            )
                        }

                        val strokeAlpha = (brushColor.alpha * brushOpacity).coerceIn(0.05f, 1f)
                        val strokeW = (brushWidth * 1.15f).coerceIn(2f, 36f)

                        drawPath(
                            path = path,
                            color = brushColor.copy(alpha = strokeAlpha),
                            style = Stroke(
                                width = strokeW,
                                cap = if (selectedBrush in listOf(BrushType.CHISEL, BrushType.CALLIGRAPHY, BrushType.HIGHLIGHTER)) StrokeCap.Square else StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        if (selectedBrush == BrushType.GLOW) {
                            drawPath(
                                path = path,
                                color = Color.White,
                                style = Stroke(width = strokeW * 0.35f, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Sliders
        LrSliderRow(
            title = "Size",
            value = brushWidth,
            displayValue = "${brushWidth.toInt()} px",
            valueRange = 2f..100f,
            minLabel = "2 px",
            maxLabel = "100 px",
            onDragStart = {},
            onValueChange = onWidthChange,
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Opacity",
            value = brushOpacity * 100f,
            displayValue = "${(brushOpacity * 100).toInt()}%",
            valueRange = 5f..100f,
            minLabel = "5%",
            maxLabel = "100%",
            onDragStart = {},
            onValueChange = { onOpacityChange(it / 100f) },
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Hardness",
            value = brushHardness * 100f,
            displayValue = "${(brushHardness * 100).toInt()}%",
            valueRange = 0f..100f,
            minLabel = "0%",
            maxLabel = "100%",
            onDragStart = {},
            onValueChange = { onHardnessChange(it / 100f) },
            onValueChangeFinished = {}
        )

        Spacer(Modifier.height(8.dp))

        // Brush Carousel in Emre's Clean Monochrome Style
        val brushes = listOf(
            BrushType.PEN to "Pen",
            BrushType.GLOW to "Neon Glow",
            BrushType.SPARKLE to "Sparkle",
            BrushType.RAINBOW to "Rainbow",
            BrushType.HIGHLIGHTER to "Marker",
            BrushType.PENCIL to "Pencil",
            BrushType.SOFT_AIRBRUSH to "Soft Air",
            BrushType.HARD_AIRBRUSH to "Hard Air",
            BrushType.WATERCOLOR to "Watercolor",
            BrushType.OIL to "Oil",
            BrushType.CALLIGRAPHY to "Calligraphy",
            BrushType.SPRAY_PAINT to "Spray",
            BrushType.DOTTED to "Dotted",
            BrushType.CHISEL to "Chisel"
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(brushes) { (brush, label) ->
                val isSelected = selectedBrush == brush
                Surface(
                    onClick = { onSelectBrush(brush) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else Color(0xFFCCCCCC),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated PicsArt Eraser Pane:
 * Size, Opacity, Hardness, and a single Clean Clear Action.
 */
@Composable
private fun EraserPane(
    eraserWidth: Float,
    onWidthChange: (Float) -> Unit,
    eraserOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    eraserHardness: Float,
    onHardnessChange: (Float) -> Unit,
    onClearLayer: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Live Eraser Tip Preview Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1B1B1B),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Eraser Active",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${eraserWidth.toInt()}px • ${(eraserOpacity * 100).toInt()}% opacity",
                        color = Color(0xFF8E8E93),
                        fontSize = 10.sp
                    )
                }

                // Visual Eraser Tip Circle Representation
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF242424), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val tipSize = (eraserWidth * 0.4f).coerceIn(6f, 36f)
                    Box(
                        modifier = Modifier
                            .size(tipSize.dp)
                            .border(
                                1.5.dp,
                                Color.White.copy(alpha = eraserOpacity.coerceIn(0.2f, 1f)),
                                CircleShape
                            )
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        LrSliderRow(
            title = "Eraser Size",
            value = eraserWidth,
            displayValue = "${eraserWidth.toInt()} px",
            valueRange = 2f..100f,
            minLabel = "2 px",
            maxLabel = "100 px",
            onDragStart = {},
            onValueChange = onWidthChange,
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Eraser Opacity",
            value = eraserOpacity * 100f,
            displayValue = "${(eraserOpacity * 100).toInt()}%",
            valueRange = 5f..100f,
            minLabel = "5%",
            maxLabel = "100%",
            onDragStart = {},
            onValueChange = { onOpacityChange(it / 100f) },
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Eraser Hardness",
            value = eraserHardness * 100f,
            displayValue = "${(eraserHardness * 100).toInt()}%",
            valueRange = 0f..100f,
            minLabel = "0%",
            maxLabel = "100%",
            onDragStart = {},
            onValueChange = { onHardnessChange(it / 100f) },
            onValueChangeFinished = {}
        )

        Spacer(Modifier.height(10.dp))

        // Clean Monochrome Clear Button
        OutlinedButton(
            onClick = onClearLayer,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Icon(Icons.Default.DeleteOutline, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clear Active Layer Strokes", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Color Palette in Emre's Monochrome Shell:
 * Swatches are colorful, while all buttons/sliders maintain grey/white/black styling.
 */
@Composable
private fun ColorPane(
    brushColor: Color,
    onColorSelect: (Color) -> Unit,
    isEyedropperActive: Boolean,
    onToggleEyedropper: () -> Unit,
    recentColors: List<Color>
) {
    var activeHue by remember { mutableFloatStateOf(0f) }
    val hexString = String.format("#%02X%02X%02X",
        (brushColor.red * 255).toInt(),
        (brushColor.green * 255).toInt(),
        (brushColor.blue * 255).toInt()
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Current Color Banner + Eyedropper in Emre Monochrome Styling
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1B1B1B),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(brushColor)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = hexString,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Eyedropper Button in Emre Monochrome Style
                Surface(
                    onClick = onToggleEyedropper,
                    shape = RoundedCornerShape(16.dp),
                    color = if (isEyedropperActive) Color.White else Color(0xFF262626),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Colorize,
                            contentDescription = "Eyedropper",
                            tint = if (isEyedropperActive) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isEyedropperActive) "Sampling..." else "Eyedropper",
                            color = if (isEyedropperActive) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Recent Colors Swatches
        if (recentColors.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(recentColors) { c ->
                    val isSel = brushColor == c
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                if (isSel) 2.dp else 1.dp,
                                if (isSel) Color.White else Color.White.copy(alpha = 0.2f),
                                CircleShape
                            )
                            .clickable { onColorSelect(c) }
                    ) {
                        if (isSel) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = if (c.red > 0.6f && c.green > 0.6f) Color.Black else Color.White,
                                modifier = Modifier.size(13.dp).align(Alignment.Center)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Rainbow Hue Bar
        val rainbow = listOf(
            Color.Red, Color(0xFFFF8000), Color.Yellow, Color.Green,
            Color.Cyan, Color.Blue, Color(0xFF8000FF), Color(0xFFFF0080), Color.Red
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(rainbow))
        )
        Slider(
            value = activeHue,
            onValueChange = {
                activeHue = it
                val hsv = floatArrayOf(it, 1f, 1f)
                val colorInt = android.graphics.Color.HSVToColor(hsv)
                onColorSelect(Color(colorInt))
            },
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Core Palette Matrix (Clean grid of 12 vibrant tones)
        val swatches = listOf(
            Color.White, Color(0xFFCCCCCC), Color(0xFF777777), Color.Black,
            Color(0xFFFF1744), Color(0xFFFF5252), Color(0xFFFF9100), Color(0xFFFFEA00),
            Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFF2979FF), Color(0xFFD500F9)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            swatches.take(6).forEach { c ->
                ColorCircle(c, brushColor == c) { onColorSelect(c) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            swatches.drop(6).forEach { c ->
                ColorCircle(c, brushColor == c) { onColorSelect(c) }
            }
        }
    }
}

@Composable
private fun ColorCircle(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                if (isSelected) 2.5.dp else 1.dp,
                if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                CircleShape
            )
            .clickable(onClick = onClick)
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (color.red > 0.6f && color.green > 0.6f) Color.Black else Color.White,
                modifier = Modifier.size(15.dp).align(Alignment.Center)
            )
        }
    }
}

/**
 * Geometric Shapes Stamping in Monochrome Styling.
 */
@Composable
private fun ShapesPane(
    brushColor: Color,
    brushWidth: Float,
    brushOpacity: Float,
    onStampShape: (shape: DrawShapeType, isFilled: Boolean, strokeWidth: Float, radius: Float) -> Unit
) {
    var selectedShape by remember { mutableStateOf(DrawShapeType.CIRCLE) }
    var isFilled by remember { mutableStateOf(false) }
    var shapeRadius by remember { mutableFloatStateOf(100f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            items(DrawShapeType.entries) { shape ->
                val isSel = selectedShape == shape
                Surface(
                    onClick = { selectedShape = shape },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSel) Color.White else Color(0xFF222222),
                    border = BorderStroke(1.dp, if (isSel) Color.White else Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = shape.label,
                            color = if (isSel) Color.Black else Color(0xFFCCCCCC),
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Stroke vs Fill Toggle (Emre Monochrome)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = { isFilled = false },
                shape = RoundedCornerShape(12.dp),
                color = if (!isFilled) Color.White else Color(0xFF202020),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "Stroke (Outline)",
                        color = if (!isFilled) Color.Black else Color(0xFFCCCCCC),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
            Surface(
                onClick = { isFilled = true },
                shape = RoundedCornerShape(12.dp),
                color = if (isFilled) Color.White else Color(0xFF202020),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "Fill (Solid)",
                        color = if (isFilled) Color.Black else Color(0xFFCCCCCC),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        LrSliderRow(
            title = "Shape Size",
            value = shapeRadius,
            displayValue = "${shapeRadius.toInt()} px",
            valueRange = 30f..250f,
            minLabel = "30 px",
            maxLabel = "250 px",
            onDragStart = {},
            onValueChange = { shapeRadius = it },
            onValueChangeFinished = {}
        )

        Button(
            onClick = {
                onStampShape(selectedShape, isFilled, brushWidth, shapeRadius)
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .testTag("stamp_shape_button")
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Stamp Shape onto Layer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

/**
 * Clean Monochrome Layers Manager:
 * Stack cards, opacity slider, blend mode chips, reorder, merge.
 */
@Composable
private fun LayersPane(
    drawLayers: List<DrawLayer>,
    activeLayerId: String,
    onAddLayer: () -> Unit,
    onSelectLayer: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onDuplicateLayer: (String) -> Unit,
    onToggleLayerVisibility: (String) -> Unit,
    onToggleLayerLock: (String) -> Unit,
    onSetLayerOpacity: (String, Float) -> Unit,
    onCommitLayerOpacity: () -> Unit,
    onSetLayerBlendMode: (String, DrawBlendMode) -> Unit,
    onMergeLayerDown: (String) -> Unit,
    onMoveLayer: (String, Int) -> Unit,
    onClearLayer: () -> Unit,
    onInvertStrokes: () -> Unit
) {
    val activeLayer = drawLayers.firstOrNull { it.id == activeLayerId } ?: drawLayers.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Layers (${drawLayers.size})",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onAddLayer,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp).testTag("add_draw_layer_button")
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Layer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Layers Stack Cards
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            drawLayers.asReversed().forEach { layer ->
                val isSelected = layer.id == activeLayerId
                Surface(
                    onClick = { onSelectLayer(layer.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFF262626) else Color(0xFF1B1B1B),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onToggleLayerVisibility(layer.id) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Visibility",
                                    tint = if (layer.isVisible) Color.White else Color(0xFF666666),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = layer.name,
                                    color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${(layer.opacity * 100).toInt()}% • ${layer.blendMode.label}",
                                    color = Color(0xFF888888),
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = { onToggleLayerLock(layer.id) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Lock",
                                    tint = if (layer.isLocked) Color.White else Color(0xFF666666),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDuplicateLayer(layer.id) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(14.dp))
                            }
                            if (drawLayers.size > 1) {
                                IconButton(
                                    onClick = { onDeleteLayer(layer.id) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeLayer != null) {
            Spacer(Modifier.height(8.dp))
            LrSliderRow(
                title = "Layer Opacity",
                value = activeLayer.opacity * 100f,
                displayValue = "${(activeLayer.opacity * 100).toInt()}%",
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onDragStart = {},
                onValueChange = { onSetLayerOpacity(activeLayer.id, it / 100f) },
                onValueChangeFinished = onCommitLayerOpacity
            )

            // Blend Mode Chips in Monochrome
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(DrawBlendMode.entries) { mode ->
                    val isSel = activeLayer.blendMode == mode
                    Surface(
                        onClick = { onSetLayerBlendMode(activeLayer.id, mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSel) Color.White else Color(0xFF222222),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = mode.label,
                                color = if (isSel) Color.Black else Color(0xFFCCCCCC),
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Reorder and Merge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onMoveLayer(activeLayer.id, 1) },
                    modifier = Modifier.weight(1f).height(32.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(13.dp), tint = Color.White)
                    Spacer(Modifier.width(3.dp))
                    Text("Up", color = Color.White, fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = { onMoveLayer(activeLayer.id, -1) },
                    modifier = Modifier.weight(1f).height(32.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(13.dp), tint = Color.White)
                    Spacer(Modifier.width(3.dp))
                    Text("Down", color = Color.White, fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = { onMergeLayerDown(activeLayer.id) },
                    modifier = Modifier.weight(1.3f).height(32.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.CallMerge, null, modifier = Modifier.size(13.dp), tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Merge Down", color = Color.White, fontSize = 10.sp)
                }
            }
        }
    }
}

/**
 * Symmetry Mode in Clean Monochrome:
 * Concise buttons and a brief hint.
 */
@Composable
private fun SymmetryPane(
    symmetryMode: DrawSymmetryMode,
    onSelectSymmetryMode: (DrawSymmetryMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DrawSymmetryMode.entries.forEach { mode ->
                val isSel = symmetryMode == mode
                Surface(
                    onClick = { onSelectSymmetryMode(mode) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSel) Color.White else Color(0xFF222222),
                    border = BorderStroke(1.dp, if (isSel) Color.White else Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSel) Color.Black else Color(0xFFCCCCCC),
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1B1B1B),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when (symmetryMode) {
                    DrawSymmetryMode.OFF -> "Symmetry is disabled."
                    DrawSymmetryMode.VERTICAL -> "Vertical mirror: strokes reflect across the vertical center line."
                    DrawSymmetryMode.HORIZONTAL -> "Horizontal mirror: strokes reflect across the horizontal horizon."
                    DrawSymmetryMode.QUAD -> "4-Way Quad: strokes mirror across all quadrants for radial patterns."
                },
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
