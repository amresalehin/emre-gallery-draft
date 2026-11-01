package com.amresalehin.emregallery.editor.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.verticalScroll
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
import kotlin.math.abs

enum class MaskingSelectionType(val label: String) {
    BRUSH("Draw Brush"),
    MAGIC_WAND("Magic Wand"),
    LASSO("Lasso"),
    POLYGON("Polygon"),
    ELLIPSE("Ellipse")
}

enum class MaskingMainTab(val label: String) {
    LIGHTCRAFT_MASKS("LightCraft Masks"),
    CREATIVE_DRAW("Creative Draw")
}

enum class MaskAdjustSubCategory(val label: String) {
    LIGHT("Light"),
    COLOR("Color"),
    DETAIL("Detail")
}

/**
 * Professional LightCraft Masking & Draw Panel.
 * Implements LightCraft's local adjustment workflow:
 * - Mask Layers stack with visibility, amount, and invert
 * - Mask Components (Brush, Linear Gradient, Radial Gradient, Luminance Range, Color Range, AI Subject/Sky/Background)
 * - Boolean combiners (Add, Subtract, Intersect)
 * - 17 Local Adjustments per mask (Exposure, Contrast, Highlights, Shadows, Temp, Tint, Clarity, etc.)
 * - Real-time Mask Overlay toggle (Ruby Red, Emerald, Blue, White, B&W)
 * - Creative Draw mode with artistic brushes & vibrant palette
 */
@Composable
fun MaskingExtendedPanel(
    // Creative Draw bindings
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

    // LightCraft Masking bindings (with sensible defaults)
    maskLayers: List<LightcraftMaskLayer> = emptyList(),
    activeMaskId: String? = null,
    activeComponentId: String? = null,
    overlaySettings: MaskOverlaySettings = MaskOverlaySettings(),
    onCreateMaskLayer: (MaskComponentType) -> Unit = {},
    onSelectMaskLayer: (String) -> Unit = {},
    onDeleteMaskLayer: (String) -> Unit = {},
    onDuplicateMaskLayer: (String) -> Unit = {},
    onToggleMaskEnabled: (String) -> Unit = {},
    onInvertMaskLayer: (String) -> Unit = {},
    onSetMaskAmount: (String, Float) -> Unit = { _, _ -> },
    onAddComponent: (String, MaskComponentType, MaskCombineMode) -> Unit = { _, _, _ -> },
    onRemoveComponent: (String, String) -> Unit = { _, _ -> },
    onSelectComponent: (String, String) -> Unit = { _, _ -> },
    onUpdateComponent: (String, MaskComponent) -> Unit = { _, _ -> },
    onUpdateAdjustment: (String, String, Float) -> Unit = { _, _, _ -> },
    onSetColorTint: (String, Color?) -> Unit = { _, _ -> },
    onResetAdjustments: (String) -> Unit = {},
    onToggleOverlay: () -> Unit = {},
    onSetOverlayColor: (MaskOverlayColor) -> Unit = {},
    onSetOverlayOpacity: (Float) -> Unit = {},

    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(MaskingMainTab.LIGHTCRAFT_MASKS) }
    var adjustSubCat by remember { mutableStateOf(MaskAdjustSubCategory.LIGHT) }
    var showOverlayOptions by remember { mutableStateOf(false) }

    // Active layer and component
    val currentMask = maskLayers.firstOrNull { it.id == activeMaskId } ?: maskLayers.firstOrNull()
    val currentComp = currentMask?.components?.firstOrNull { it.id == activeComponentId }
        ?: currentMask?.components?.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // --- Top Row: Tab Switcher (LightCraft Masks vs Creative Draw) + Overlay Quick Toggle ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main Mode Tabs
            Row(
                modifier = Modifier
                    .background(Color(0xFF222222), RoundedCornerShape(20.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MaskingMainTab.entries.forEach { tab ->
                    val isSelected = activeTab == tab
                    Surface(
                        onClick = { activeTab = tab },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) Color(0xFFE53935) else Color.Transparent,
                        modifier = Modifier.height(30.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = tab.label,
                                color = if (isSelected) Color.White else Color(0xFFAAAAAA),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Mask Overlay Quick Toggle (Ruby Red / Emerald / etc.)
            Surface(
                onClick = { onToggleOverlay() },
                shape = RoundedCornerShape(16.dp),
                color = if (overlaySettings.showOverlay) overlaySettings.overlayColor.color.copy(alpha = 0.25f) else Color(0xFF222222),
                border = BorderStroke(1.dp, if (overlaySettings.showOverlay) overlaySettings.overlayColor.color else Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.height(30.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (overlaySettings.showOverlay) overlaySettings.overlayColor.color else Color.Gray, CircleShape)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (overlaySettings.showOverlay) "Overlay ON" else "Overlay OFF",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { showOverlayOptions = !showOverlayOptions },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Overlay Options",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // Overlay options popover panel
        AnimatedVisibility(visible = showOverlayOptions) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text("Mask Overlay Colors (LightCraft)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(MaskOverlayColor.entries) { oc ->
                        val isSel = overlaySettings.overlayColor == oc
                        Surface(
                            onClick = { onSetOverlayColor(oc) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) oc.color.copy(alpha = 0.35f) else Color(0xFF2B2B2B),
                            border = BorderStroke(if (isSel) 1.5.dp else 1.dp, if (isSel) oc.color else Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(12.dp).background(oc.color, CircleShape))
                                Spacer(Modifier.width(6.dp))
                                Text(oc.label, color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                LrSliderRow(
                    title = "Overlay Opacity",
                    value = overlaySettings.opacity * 100f,
                    displayValue = "${(overlaySettings.opacity * 100).toInt()}%",
                    valueRange = 10f..100f,
                    minLabel = "10%",
                    maxLabel = "100%",
                    onDragStart = {},
                    onValueChange = { onSetOverlayOpacity(it / 100f) },
                    onValueChangeFinished = {}
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (activeTab) {
            MaskingMainTab.LIGHTCRAFT_MASKS -> {
                LightcraftMasksWorkspace(
                    maskLayers = maskLayers,
                    currentMask = currentMask,
                    currentComp = currentComp,
                    adjustSubCat = adjustSubCat,
                    onSelectSubCat = { adjustSubCat = it },
                    onCreateMaskLayer = onCreateMaskLayer,
                    onSelectMaskLayer = onSelectMaskLayer,
                    onDeleteMaskLayer = onDeleteMaskLayer,
                    onDuplicateMaskLayer = onDuplicateMaskLayer,
                    onToggleMaskEnabled = onToggleMaskEnabled,
                    onInvertMaskLayer = onInvertMaskLayer,
                    onSetMaskAmount = onSetMaskAmount,
                    onAddComponent = onAddComponent,
                    onRemoveComponent = onRemoveComponent,
                    onSelectComponent = onSelectComponent,
                    onUpdateComponent = onUpdateComponent,
                    onUpdateAdjustment = onUpdateAdjustment,
                    onSetColorTint = onSetColorTint,
                    onResetAdjustments = onResetAdjustments,
                    onClearAllMasks = onClearMask
                )
            }
            MaskingMainTab.CREATIVE_DRAW -> {
                CreativeDrawWorkspace(
                    selectedType = selectedType,
                    onSelectType = onSelectType,
                    selectedBrush = selectedBrush,
                    onSelectBrush = onSelectBrush,
                    brushColor = brushColor,
                    onColorSelect = onColorSelect,
                    brushWidth = brushWidth,
                    onWidthChange = onWidthChange,
                    brushOpacity = brushOpacity,
                    onOpacityChange = onOpacityChange,
                    brushFlow = brushFlow,
                    onFlowChange = onFlowChange,
                    magicWandTolerance = magicWandTolerance,
                    onMagicWandToleranceChange = onMagicWandToleranceChange,
                    onInvertMask = onInvertMask,
                    onClearMask = onClearMask
                )
            }
        }
    }
}

/**
 * The full LightCraft Mask Layers, Components & 17 Local Adjustments Workspace.
 */
@Composable
private fun LightcraftMasksWorkspace(
    maskLayers: List<LightcraftMaskLayer>,
    currentMask: LightcraftMaskLayer?,
    currentComp: MaskComponent?,
    adjustSubCat: MaskAdjustSubCategory,
    onSelectSubCat: (MaskAdjustSubCategory) -> Unit,
    onCreateMaskLayer: (MaskComponentType) -> Unit,
    onSelectMaskLayer: (String) -> Unit,
    onDeleteMaskLayer: (String) -> Unit,
    onDuplicateMaskLayer: (String) -> Unit,
    onToggleMaskEnabled: (String) -> Unit,
    onInvertMaskLayer: (String) -> Unit,
    onSetMaskAmount: (String, Float) -> Unit,
    onAddComponent: (String, MaskComponentType, MaskCombineMode) -> Unit,
    onRemoveComponent: (String, String) -> Unit,
    onSelectComponent: (String, String) -> Unit,
    onUpdateComponent: (String, MaskComponent) -> Unit,
    onUpdateAdjustment: (String, String, Float) -> Unit,
    onSetColorTint: (String, Color?) -> Unit,
    onResetAdjustments: (String) -> Unit,
    onClearAllMasks: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // --- Mask Layers Stack Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "+ New Mask" Button
            FilledTonalButton(
                onClick = { onCreateMaskLayer(MaskComponentType.BRUSH) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF2C2C2C),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp).testTag("new_mask_button")
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Mask", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(8.dp))

            // Masks List Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(maskLayers) { layer ->
                    val isSelected = currentMask?.id == layer.id
                    Surface(
                        onClick = { onSelectMaskLayer(layer.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF37474F) else Color(0xFF1E1E1E),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF00B0FF) else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onToggleMaskEnabled(layer.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = if (layer.isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Visibility",
                                    tint = if (layer.isEnabled) Color.White else Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = layer.name,
                                color = if (isSelected) Color.White else Color(0xFFAAAAAA),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                            if (isSelected && maskLayers.size > 1) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onDeleteMaskLayer(layer.id) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete Mask",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (currentMask == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No mask layers yet", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onCreateMaskLayer(MaskComponentType.BRUSH) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create First Mask (LightCraft)")
                    }
                }
            }
            return
        }

        // --- Current Mask Header: Amount Slider & Quick Layer Actions ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Mask Amount (0..200%)
            Box(modifier = Modifier.weight(1f)) {
                LrSliderRow(
                    title = "Mask Strength (Amount)",
                    value = currentMask.amount * 100f,
                    displayValue = "${(currentMask.amount * 100).toInt()}%",
                    valueRange = 0f..200f,
                    minLabel = "0%",
                    maxLabel = "200%",
                    onDragStart = {},
                    onValueChange = { onSetMaskAmount(currentMask.id, it / 100f) },
                    onValueChangeFinished = {}
                )
            }

            Spacer(Modifier.width(8.dp))

            // Quick Layer Actions
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { onInvertMaskLayer(currentMask.id) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (currentMask.isInverted) Color(0xFF00E676) else Color.White.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.InvertColors,
                        contentDescription = "Invert",
                        tint = if (currentMask.isInverted) Color(0xFF00E676) else Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (currentMask.isInverted) "Inverted" else "Invert",
                        color = if (currentMask.isInverted) Color(0xFF00E676) else Color.White,
                        fontSize = 10.sp
                    )
                }

                IconButton(
                    onClick = { onDuplicateMaskLayer(currentMask.id) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, "Duplicate", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = { onDeleteMaskLayer(currentMask.id) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // --- LightCraft Mask Tools: Add Component Strip ---
        Text("Add Component to ${currentMask.name}", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val tools = listOf(
                MaskComponentType.BRUSH to Icons.Default.Brush,
                MaskComponentType.LINEAR_GRADIENT to Icons.Default.LinearScale,
                MaskComponentType.RADIAL_GRADIENT to Icons.Default.PanoramaFishEye,
                MaskComponentType.LUMINANCE_RANGE to Icons.Default.BrightnessMedium,
                MaskComponentType.COLOR_RANGE to Icons.Default.ColorLens,
                MaskComponentType.SELECT_SUBJECT to Icons.Default.Person,
                MaskComponentType.SELECT_SKY to Icons.Default.Cloud,
                MaskComponentType.SELECT_BACKGROUND to Icons.Default.Landscape
            )
            items(tools) { (compType, icon) ->
                Surface(
                    onClick = { onAddComponent(currentMask.id, compType, MaskCombineMode.ADD) },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF242424),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = Color(0xFF00B0FF), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ ${compType.label}", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // --- Active Components List with Combine Mode Pills (+ Add, - Subtract, x Intersect) ---
        if (currentMask.components.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(currentMask.components) { comp ->
                    val isSel = currentComp?.id == comp.id
                    Surface(
                        onClick = { onSelectComponent(currentMask.id, comp.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSel) Color(0xFF004D40) else Color(0xFF1E1E1E),
                        border = BorderStroke(1.dp, if (isSel) Color(0xFF00E676) else Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${comp.combineMode.symbol} ${comp.type.label}",
                                color = if (isSel) Color.White else Color(0xFFAAAAAA),
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.sp
                            )
                            if (currentMask.components.size > 1) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onRemoveComponent(currentMask.id, comp.id) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Remove", tint = Color(0xFFFF5252), modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // --- Selected Component Parameters Controls ---
        currentComp?.let { comp ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1B1B1B),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Combine mode row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Combine Mode:", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MaskCombineMode.entries.forEach { mode ->
                                val isM = comp.combineMode == mode
                                Surface(
                                    onClick = { onUpdateComponent(currentMask.id, comp.copy(combineMode = mode)) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isM) Color(0xFF00B0FF) else Color(0xFF2A2A2A),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${mode.symbol} ${mode.label}",
                                            color = if (isM) Color.Black else Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Specific tool parameters
                    when (comp.type) {
                        MaskComponentType.BRUSH -> {
                            LrSliderRow(
                                title = "Brush Size",
                                value = comp.brushSize,
                                displayValue = "${comp.brushSize.toInt()} px",
                                valueRange = 2f..120f,
                                minLabel = "2 px",
                                maxLabel = "120 px",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(brushSize = it)) },
                                onValueChangeFinished = {}
                            )
                            LrSliderRow(
                                title = "Brush Feather",
                                value = comp.brushFeather,
                                displayValue = "${comp.brushFeather.toInt()}%",
                                valueRange = 0f..100f,
                                minLabel = "0%",
                                maxLabel = "100%",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(brushFeather = it)) },
                                onValueChangeFinished = {}
                            )
                            LrSliderRow(
                                title = "Brush Flow",
                                value = comp.brushFlow * 100f,
                                displayValue = "${(comp.brushFlow * 100).toInt()}%",
                                valueRange = 10f..100f,
                                minLabel = "10%",
                                maxLabel = "100%",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(brushFlow = it / 100f)) },
                                onValueChangeFinished = {}
                            )
                            LrSliderRow(
                                title = "Brush Density",
                                value = comp.brushDensity * 100f,
                                displayValue = "${(comp.brushDensity * 100).toInt()}%",
                                valueRange = 10f..100f,
                                minLabel = "10%",
                                maxLabel = "100%",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(brushDensity = it / 100f)) },
                                onValueChangeFinished = {}
                            )
                        }
                        MaskComponentType.LINEAR_GRADIENT -> {
                            LrSliderRow(
                                title = "Gradient Feather",
                                value = comp.linearFeather,
                                displayValue = "${comp.linearFeather.toInt()}%",
                                valueRange = 0f..100f,
                                minLabel = "0%",
                                maxLabel = "100%",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(linearFeather = it)) },
                                onValueChangeFinished = {}
                            )
                            LrSliderRow(
                                title = "Angle",
                                value = comp.linearAngle,
                                displayValue = "${comp.linearAngle.toInt()}°",
                                valueRange = -180f..180f,
                                minLabel = "-180°",
                                maxLabel = "+180°",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(linearAngle = it)) },
                                onValueChangeFinished = {}
                            )
                        }
                        MaskComponentType.RADIAL_GRADIENT -> {
                            LrSliderRow(
                                title = "Radius X",
                                value = comp.radialRadiusX * 100f,
                                displayValue = "${(comp.radialRadiusX * 100).toInt()}%",
                                valueRange = 5f..100f,
                                minLabel = "5%",
                                maxLabel = "100%",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(radialRadiusX = it / 100f)) },
                                onValueChangeFinished = {}
                            )
                            LrSliderRow(
                                title = "Radius Y",
                                value = comp.radialRadiusY * 100f,
                                displayValue = "${(comp.radialRadiusY * 100).toInt()}%",
                                valueRange = 5f..100f,
                                minLabel = "5%",
                                maxLabel = "100%",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(radialRadiusY = it / 100f)) },
                                onValueChangeFinished = {}
                            )
                            LrSliderRow(
                                title = "Softness / Feather",
                                value = comp.radialFeather,
                                displayValue = "${comp.radialFeather.toInt()}%",
                                valueRange = 5f..100f,
                                minLabel = "5%",
                                maxLabel = "100%",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(radialFeather = it)) },
                                onValueChangeFinished = {}
                            )
                        }
                        MaskComponentType.LUMINANCE_RANGE -> {
                            LrSliderRow(
                                title = "Min Brightness",
                                value = comp.lumMin,
                                displayValue = "${comp.lumMin.toInt()}%",
                                valueRange = 0f..100f,
                                minLabel = "0%",
                                maxLabel = "100%",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(lumMin = it)) },
                                onValueChangeFinished = {}
                            )
                            LrSliderRow(
                                title = "Max Brightness",
                                value = comp.lumMax,
                                displayValue = "${comp.lumMax.toInt()}%",
                                valueRange = 0f..100f,
                                minLabel = "0%",
                                maxLabel = "100%",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(lumMax = it)) },
                                onValueChangeFinished = {}
                            )
                            LrSliderRow(
                                title = "Feather Falloff",
                                value = comp.lumFeather,
                                displayValue = "${comp.lumFeather.toInt()}%",
                                valueRange = 0f..100f,
                                minLabel = "0%",
                                maxLabel = "100%",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(lumFeather = it)) },
                                onValueChangeFinished = {}
                            )
                        }
                        MaskComponentType.COLOR_RANGE -> {
                            Text("Target Color Range", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val hues = listOf(
                                    0f to Color(0xFFFF1744),   // Red
                                    30f to Color(0xFFFF9100),  // Orange
                                    60f to Color(0xFFFFEA00),  // Yellow
                                    120f to Color(0xFF00E676), // Green
                                    180f to Color(0xFF00E5FF), // Cyan
                                    240f to Color(0xFF2979FF), // Blue
                                    280f to Color(0xFF651FFF), // Purple
                                    320f to Color(0xFFF50057)  // Magenta
                                )
                                items(hues) { (hue, col) ->
                                    val isH = abs(comp.targetHue - hue) < 5f
                                    Surface(
                                        onClick = { onUpdateComponent(currentMask.id, comp.copy(targetHue = hue)) },
                                        shape = CircleShape,
                                        color = col,
                                        border = BorderStroke(if (isH) 2.5.dp else 1.dp, if (isH) Color.White else Color.White.copy(alpha = 0.2f)),
                                        modifier = Modifier.size(24.dp)
                                    ) {}
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LrSliderRow(
                                title = "Color Tolerance",
                                value = comp.colorTolerance,
                                displayValue = "±${comp.colorTolerance.toInt()}",
                                valueRange = 5f..90f,
                                minLabel = "5",
                                maxLabel = "90",
                                onDragStart = {},
                                onValueChange = { onUpdateComponent(currentMask.id, comp.copy(colorTolerance = it)) },
                                onValueChangeFinished = {}
                            )
                        }
                        MaskComponentType.SELECT_SUBJECT -> {
                            Text("Automatic Subject isolation mask", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                        }
                        MaskComponentType.SELECT_SKY -> {
                            Text("Automatic Sky horizon isolation mask", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                        }
                        MaskComponentType.SELECT_BACKGROUND -> {
                            Text("Automatic Background isolation mask", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                        }
                        MaskComponentType.LASSO, MaskComponentType.POLYGON -> {
                            Text("Freehand / Polygon anchor mask", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // --- LightCraft 17 Local Adjustments Section ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("17 Localized Adjustments (LightCraft)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            TextButton(
                onClick = { onResetAdjustments(currentMask.id) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Text("Reset Adjustments", color = Color(0xFFFF5252), fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Subcategory Pills (Light / Color / Detail)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MaskAdjustSubCategory.entries.forEach { subCat ->
                val isSel = adjustSubCat == subCat
                Surface(
                    onClick = { onSelectSubCat(subCat) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSel) Color.White else Color(0xFF222222),
                    modifier = Modifier.height(28.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = subCat.label,
                            color = if (isSel) Color.Black else Color.White,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 17 Adjustments Sliders
        val adj = currentMask.adjustments
        when (adjustSubCat) {
            MaskAdjustSubCategory.LIGHT -> {
                LrSliderRow(title = "Exposure", value = adj.exposure, displayValue = "${adj.exposure.toInt()}", valueRange = -100f..100f, minLabel = "-100", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Exposure", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Contrast", value = adj.contrast, displayValue = "${adj.contrast.toInt()}", valueRange = -100f..100f, minLabel = "-100", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Contrast", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Highlights", value = adj.highlights, displayValue = "${adj.highlights.toInt()}", valueRange = -100f..100f, minLabel = "-100", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Highlights", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Shadows", value = adj.shadows, displayValue = "${adj.shadows.toInt()}", valueRange = -100f..100f, minLabel = "-100", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Shadows", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Whites", value = adj.whites, displayValue = "${adj.whites.toInt()}", valueRange = -100f..100f, minLabel = "-100", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Whites", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Blacks", value = adj.blacks, displayValue = "${adj.blacks.toInt()}", valueRange = -100f..100f, minLabel = "-100", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Blacks", it) }, onValueChangeFinished = {})
            }
            MaskAdjustSubCategory.COLOR -> {
                LrSliderRow(title = "Temperature", value = adj.temperature, displayValue = "${adj.temperature.toInt()}", valueRange = -100f..100f, minLabel = "Cool", maxLabel = "Warm", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Temperature", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Tint", value = adj.tint, displayValue = "${adj.tint.toInt()}", valueRange = -100f..100f, minLabel = "Green", maxLabel = "Magenta", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Tint", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Saturation", value = adj.saturation, displayValue = "${adj.saturation.toInt()}", valueRange = -100f..100f, minLabel = "-100", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Saturation", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Vibrance", value = adj.vibrance, displayValue = "${adj.vibrance.toInt()}", valueRange = -100f..100f, minLabel = "-100", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Vibrance", it) }, onValueChangeFinished = {})

                Spacer(Modifier.height(4.dp))
                Text("Localized Color Wash Tint", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tintColors = listOf(
                        null to "None",
                        Color(0xFFFF5252) to "Red",
                        Color(0xFFFF9100) to "Amber",
                        Color(0xFFFFEA00) to "Yellow",
                        Color(0xFF00E676) to "Green",
                        Color(0xFF00E5FF) to "Cyan",
                        Color(0xFF2979FF) to "Blue",
                        Color(0xFF651FFF) to "Purple",
                        Color(0xFFF50057) to "Rose"
                    )
                    items(tintColors) { (col, _) ->
                        val isSel = adj.colorTint == col
                        Surface(
                            onClick = { onSetColorTint(currentMask.id, col) },
                            shape = CircleShape,
                            color = col ?: Color(0xFF222222),
                            border = BorderStroke(if (isSel) 2.5.dp else 1.dp, if (isSel) Color.White else Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.size(26.dp)
                        ) {
                            if (col == null) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Block, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
            MaskAdjustSubCategory.DETAIL -> {
                LrSliderRow(title = "Texture", value = adj.texture, displayValue = "${adj.texture.toInt()}", valueRange = -100f..100f, minLabel = "-100", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Texture", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Clarity", value = adj.clarity, displayValue = "${adj.clarity.toInt()}", valueRange = -100f..100f, minLabel = "-100", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Clarity", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Dehaze", value = adj.dehaze, displayValue = "${adj.dehaze.toInt()}", valueRange = -100f..100f, minLabel = "-100", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Dehaze", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Sharpness", value = adj.sharpness, displayValue = "${adj.sharpness.toInt()}", valueRange = 0f..100f, minLabel = "0", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Sharpness", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Blur Smoothing", value = adj.blur, displayValue = "${adj.blur.toInt()}", valueRange = 0f..100f, minLabel = "0", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Blur", it) }, onValueChangeFinished = {})
                LrSliderRow(title = "Noise Reduction", value = adj.noiseReduction, displayValue = "${adj.noiseReduction.toInt()}", valueRange = 0f..100f, minLabel = "0", maxLabel = "+100", onDragStart = {}, onValueChange = { onUpdateAdjustment(currentMask.id, "Noise Reduction", it) }, onValueChangeFinished = {})
            }
        }
    }
}

/**
 * Creative Draw Workspace with artistic brushes & vibrant color palette.
 */
@Composable
private fun CreativeDrawWorkspace(
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
    onInvertMask: () -> Unit,
    onClearMask: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Quick Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onInvertMask,
                modifier = Modifier.weight(1f).height(34.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.InvertColors, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Invert Strokes", color = Color.White, fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = onClearMask,
                modifier = Modifier.weight(1f).height(34.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clear Strokes", color = Color(0xFFFF5252), fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(6.dp))

        // Color Palette Swatches
        Text("Color Palette", color = Color(0xFFAAAAAA), fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
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
                    modifier = Modifier.size(30.dp)
                ) {
                    if (isSelected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = if (c == Color.White || c == Color(0xFFFFEA00)) Color.Black else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Brush Types Row
        Text("Brush Textures", color = Color(0xFFAAAAAA), fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(BrushType.entries) { brush ->
                val isSelected = selectedBrush == brush
                Surface(
                    onClick = { onSelectBrush(brush) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    modifier = Modifier.height(28.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = brush.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
