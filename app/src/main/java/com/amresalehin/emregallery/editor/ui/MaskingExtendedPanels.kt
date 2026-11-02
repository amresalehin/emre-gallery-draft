package com.amresalehin.emregallery.editor.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

/**
 * Selection types retained for backwards compatibility.
 */
enum class MaskingSelectionType(val label: String) {
    BRUSH("Draw Brush"),
    MAGIC_WAND("Magic Wand"),
    LASSO("Lasso"),
    POLYGON("Polygon"),
    ELLIPSE("Ellipse")
}

/**
 * Adobe Lightroom Mobile's exact 5 adjustment categories in Masking mode:
 * [LIGHT], [COLOR], [EFFECTS], [DETAIL], [OPTICS]
 */
enum class MaskAdjustSubCategory(val label: String, val icon: ImageVector) {
    LIGHT("Light", Icons.Default.WbSunny),
    COLOR("Color", Icons.Default.Thermostat),
    EFFECTS("Effects", Icons.Default.AutoFixHigh),
    DETAIL("Detail", Icons.Default.ChangeHistory),
    OPTICS("Optics", Icons.Default.Lens)
}

/**
 * Replica of Adobe Lightroom Mobile for Android — Masking Section Panel.
 * Positioned right above the bottom-most navigation bar without changing top or bottom-most bars.
 */
@Composable
fun MaskingExtendedPanel(
    // Creative Draw bindings (retained for backward compatibility)
    selectedType: MaskingSelectionType = MaskingSelectionType.BRUSH,
    onSelectType: (MaskingSelectionType) -> Unit = {},
    selectedBrush: BrushType = BrushType.PEN,
    onSelectBrush: (BrushType) -> Unit = {},
    brushColor: Color = Color.White,
    onColorSelect: (Color) -> Unit = {},
    brushWidth: Float = 40f,
    onWidthChange: (Float) -> Unit = {},
    brushOpacity: Float = 1.0f,
    onOpacityChange: (Float) -> Unit = {},
    brushFlow: Float = 1.0f,
    onFlowChange: (Float) -> Unit = {},
    magicWandTolerance: Float = 32f,
    onMagicWandToleranceChange: (Float) -> Unit = {},
    isSelectionActive: Boolean = false,
    onInvertMask: () -> Unit = {},
    onClearMask: () -> Unit = {},

    // Adobe Lightroom Masking Bindings
    maskLayers: List<LightcraftMaskLayer> = emptyList(),
    activeMaskId: String? = null,
    activeComponentId: String? = null,
    overlaySettings: MaskOverlaySettings = MaskOverlaySettings(),
    onCreateMaskLayer: (MaskComponentType) -> Unit = {},
    onApplyRecipe: (MaskQuickRecipe) -> Unit = {},
    onSelectMaskLayer: (String) -> Unit = {},
    onDeleteMaskLayer: (String) -> Unit = {},
    onDuplicateMaskLayer: (String) -> Unit = {},
    onToggleMaskEnabled: (String) -> Unit = {},
    onInvertMaskLayer: (String) -> Unit = {},
    onSetMaskAmount: (String, Float) -> Unit = { _, _ -> },
    onAddComponent: (String, MaskComponentType, MaskCombineMode) -> Unit = { _, _, _ -> },
    onAddRecipeComponent: (String, MaskQuickRecipe, MaskCombineMode) -> Unit = { _, _, _ -> },
    onRemoveComponent: (String, String) -> Unit = { _, _ -> },
    onSelectComponent: (String, String) -> Unit = { _, _ -> },
    onUpdateComponent: (String, MaskComponent) -> Unit = { _, _ -> },
    onUpdateAdjustment: (String, String, Float) -> Unit = { _, _, _ -> },
    onSetColorTint: (String, Color?) -> Unit = { _, _ -> },
    onResetAdjustments: (String) -> Unit = {},
    onToggleOverlay: () -> Unit = {},
    onSetOverlayColor: (MaskOverlayColor) -> Unit = {},
    onSetOverlayOpacity: (Float) -> Unit = {},

    // Interactive slider drag HUD hooks
    onSliderDragStart: (String, String) -> Unit = { _, _ -> },
    onSliderDragFinished: () -> Unit = {},

    // Triggers for creation sheet and progress dialog
    showCreationSheet: Boolean = false,
    onDismissCreationSheet: () -> Unit = {},
    onOpenCreationSheet: () -> Unit = {},

    modifier: Modifier = Modifier
) {
    var activeCategory by remember { mutableStateOf(MaskAdjustSubCategory.LIGHT) }
    var detectingTitle by remember { mutableStateOf<String?>(null) }
    var detectingSubtitle by remember { mutableStateOf<String?>(null) }
    var pendingCreationType by remember { mutableStateOf<MaskComponentType?>(null) }

    // Active layer and component
    val currentMask = maskLayers.firstOrNull { it.id == activeMaskId } ?: maskLayers.firstOrNull()

    // Handle detection dialog auto-completion (Frame 01:58)
    LaunchedEffect(pendingCreationType) {
        val type = pendingCreationType ?: return@LaunchedEffect
        delay(600)
        onCreateMaskLayer(type)
        detectingTitle = null
        detectingSubtitle = null
        pendingCreationType = null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF000000))
    ) {
        // --- Category Strip (Exact match to Frame 01:00 & 01:18) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Category Icons: Light, Color, Effects, Detail, Optics
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaskAdjustSubCategory.entries.forEach { cat ->
                    val isSelected = activeCategory == cat
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { activeCategory = cat }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .testTag("lr_mask_tab_${cat.name.lowercase()}")
                    ) {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = cat.label,
                            tint = if (isSelected) Color.White else Color(0xFF888888),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = cat.label,
                            color = if (isSelected) Color.White else Color(0xFF888888),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(2.dp)
                                .background(if (isSelected) Color.White else Color.Transparent, CircleShape)
                        )
                    }
                }
            }

            // Right Actions: Previous, Reset
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { /* Previous adjustments */ }
                        .padding(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.History, "Previous", tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(2.dp))
                    Text("Previous", color = Color(0xFF888888), fontSize = 10.sp)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            if (currentMask != null) onResetAdjustments(currentMask.id)
                        }
                        .padding(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Reset", tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(2.dp))
                    Text("Reset", color = Color(0xFF888888), fontSize = 10.sp)
                }
            }
        }

        // --- Subtitle Bar: Centered "Masking" with White Drag Handle (Frame 01:00) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 3.dp)
                    .background(Color.White.copy(alpha = 0.4f), CircleShape)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Masking",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // --- Sliders Area for the Active Category ---
        val adj = currentMask?.adjustments ?: MaskLocalAdjustments()
        val activeMaskRef = currentMask

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            when (activeCategory) {
                MaskAdjustSubCategory.LIGHT -> {
                    LrPrecisionSlider(
                        name = "Exposure",
                        value = adj.exposure,
                        displayValue = formatLrExposure(adj.exposure),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Exposure", formatLrExposure(adj.exposure)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Exposure", it)
                            onSliderDragStart("Exposure", formatLrExposure(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                    LrPrecisionSlider(
                        name = "Contrast",
                        value = adj.contrast,
                        displayValue = formatSignedInt(adj.contrast),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Contrast", formatSignedInt(adj.contrast)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Contrast", it)
                            onSliderDragStart("Contrast", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                    LrPrecisionSlider(
                        name = "Highlights",
                        value = adj.highlights,
                        displayValue = formatSignedInt(adj.highlights),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Highlights", formatSignedInt(adj.highlights)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Highlights", it)
                            onSliderDragStart("Highlights", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                    LrPrecisionSlider(
                        name = "Shadows",
                        value = adj.shadows,
                        displayValue = formatSignedInt(adj.shadows),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Shadows", formatSignedInt(adj.shadows)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Shadows", it)
                            onSliderDragStart("Shadows", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                    LrPrecisionSlider(
                        name = "Whites",
                        value = adj.whites,
                        displayValue = formatSignedInt(adj.whites),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Whites", formatSignedInt(adj.whites)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Whites", it)
                            onSliderDragStart("Whites", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                    LrPrecisionSlider(
                        name = "Blacks",
                        value = adj.blacks,
                        displayValue = formatSignedInt(adj.blacks),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Blacks", formatSignedInt(adj.blacks)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Blacks", it)
                            onSliderDragStart("Blacks", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                }
                MaskAdjustSubCategory.COLOR -> {
                    LrPrecisionSlider(
                        name = "Temp",
                        value = adj.temperature,
                        displayValue = formatSignedInt(adj.temperature),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Temp", formatSignedInt(adj.temperature)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Temperature", it)
                            onSliderDragStart("Temp", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                    LrPrecisionSlider(
                        name = "Tint",
                        value = adj.tint,
                        displayValue = formatSignedInt(adj.tint),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Tint", formatSignedInt(adj.tint)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Tint", it)
                            onSliderDragStart("Tint", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                    LrPrecisionSlider(
                        name = "Saturation",
                        value = adj.saturation,
                        displayValue = formatSignedInt(adj.saturation),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Saturation", formatSignedInt(adj.saturation)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Saturation", it)
                            onSliderDragStart("Saturation", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                    LrPrecisionSlider(
                        name = "Hue",
                        value = adj.vibrance,
                        displayValue = formatSignedInt(adj.vibrance),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Hue", formatSignedInt(adj.vibrance)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Vibrance", it)
                            onSliderDragStart("Hue", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                }
                MaskAdjustSubCategory.EFFECTS -> {
                    LrPrecisionSlider(
                        name = "Texture",
                        value = adj.texture,
                        displayValue = formatSignedInt(adj.texture),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Texture", formatSignedInt(adj.texture)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Texture", it)
                            onSliderDragStart("Texture", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                    LrPrecisionSlider(
                        name = "Clarity",
                        value = adj.clarity,
                        displayValue = formatSignedInt(adj.clarity),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Clarity", formatSignedInt(adj.clarity)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Clarity", it)
                            onSliderDragStart("Clarity", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                    LrPrecisionSlider(
                        name = "Dehaze",
                        value = adj.dehaze,
                        displayValue = formatSignedInt(adj.dehaze),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Dehaze", formatSignedInt(adj.dehaze)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Dehaze", it)
                            onSliderDragStart("Dehaze", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                }
                MaskAdjustSubCategory.DETAIL -> {
                    LrPrecisionSlider(
                        name = "Noise",
                        value = adj.noiseReduction,
                        displayValue = "${adj.noiseReduction.toInt()}",
                        valueRange = 0f..100f,
                        onDragStart = { onSliderDragStart("Noise", "${adj.noiseReduction.toInt()}") },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Noise Reduction", it)
                            onSliderDragStart("Noise", "${it.toInt()}")
                        },
                        onDragEnd = onSliderDragFinished
                    )
                    LrPrecisionSlider(
                        name = "Sharpness",
                        value = adj.sharpness,
                        displayValue = formatSignedInt(adj.sharpness),
                        valueRange = -100f..100f,
                        onDragStart = { onSliderDragStart("Sharpness", formatSignedInt(adj.sharpness)) },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Sharpness", it)
                            onSliderDragStart("Sharpness", formatSignedInt(it))
                        },
                        onDragEnd = onSliderDragFinished
                    )
                }
                MaskAdjustSubCategory.OPTICS -> {
                    LrPrecisionSlider(
                        name = "Blur",
                        value = adj.blur,
                        displayValue = "${adj.blur.toInt()}",
                        valueRange = 0f..100f,
                        onDragStart = { onSliderDragStart("Blur", "${adj.blur.toInt()}") },
                        onValueChange = {
                            if (activeMaskRef != null) onUpdateAdjustment(activeMaskRef.id, "Blur", it)
                            onSliderDragStart("Blur", "${it.toInt()}")
                        },
                        onDragEnd = onSliderDragFinished
                    )
                }
            }
        }
    }

    // --- "Create new Mask" Modal Bottom Sheet (Frames 01:02 - 01:04) ---
    if (showCreationSheet) {
        LightroomCreateNewMaskSheet(
            onDismiss = onDismissCreationSheet,
            onSelect = { type ->
                onDismissCreationSheet()
                if (type == MaskComponentType.SELECT_SKY) {
                    detectingTitle = "Select Sky"
                    detectingSubtitle = "Detecting sky..."
                    pendingCreationType = type
                } else if (type == MaskComponentType.SELECT_SUBJECT) {
                    detectingTitle = "Select Subject"
                    detectingSubtitle = "Detecting subject..."
                    pendingCreationType = type
                } else {
                    onCreateMaskLayer(type)
                }
            }
        )
    }

    // --- "Detecting sky..." / "Detecting subject..." Dialog (Frame 01:58) ---
    if (detectingTitle != null) {
        LightroomDetectingProgressDialog(
            title = detectingTitle!!,
            subtitle = detectingSubtitle ?: "Processing...",
            onCancel = {
                detectingTitle = null
                detectingSubtitle = null
                pendingCreationType = null
            }
        )
    }
}


/**
 * Exact replica of the "Create new Mask" Bottom Sheet (Frames 01:02 - 01:04).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LightroomCreateNewMaskSheet(
    onDismiss: () -> Unit,
    onSelect: (MaskComponentType) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 8.dp).size(width = 36.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: "Create new Mask" + Help '?' icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create new Mask",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.Transparent, CircleShape)
                        .border(1.dp, Color(0xFF888888), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", color = Color(0xFF888888), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // The 8 Exact Lightroom Mobile Items (Frames 01:02 - 01:04)
            val items = listOf(
                Triple("Select Subject", Icons.Default.Person, MaskComponentType.SELECT_SUBJECT),
                Triple("Select Sky", Icons.Default.Cloud, MaskComponentType.SELECT_SKY),
                Triple("Brush", Icons.Default.Brush, MaskComponentType.BRUSH),
                Triple("Linear Gradient", Icons.Default.LinearScale, MaskComponentType.LINEAR_GRADIENT),
                Triple("Radial Gradient", Icons.Default.RadioButtonUnchecked, MaskComponentType.RADIAL_GRADIENT),
                Triple("Color Range", Icons.Default.Palette, MaskComponentType.COLOR_RANGE),
                Triple("Luminance Range", Icons.Default.WbSunny, MaskComponentType.LUMINANCE_RANGE),
                Triple("Depth Range", Icons.Default.Layers, MaskComponentType.SELECT_BACKGROUND)
            )

            items.forEach { (label, icon, type) ->
                Surface(
                    onClick = { onSelect(type) },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        // Emre White indicator dot on right
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Animated "Detecting sky..." / "Detecting subject..." Progress Dialog.
 */
@Composable
private fun LightroomDetectingProgressDialog(
    title: String,
    subtitle: String,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(subtitle, color = Color(0xFFAAAAAA), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    color = Color.White,
                    trackColor = Color(0xFF333333),
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color.White, fontSize = 13.sp)
            }
        }
    )
}

/**
 * Lightroom Mobile Precision Slider Row (Frames 01:18, 01:39, 02:29).
 * Features:
 * - Title on left, numeric value on right
 * - Subtle slider track with centered zero detent for bipolar parameters
 * - Custom gradient track support (for Temp and Tint)
 */
@Composable
private fun LrPrecisionSlider(
    name: String,
    value: Float,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    trackBrush: Brush? = null,
    onDragStart: () -> Unit = {},
    onValueChange: (Float) -> Unit,
    onDragEnd: () -> Unit = {}
) {
    val isBipolar = valueRange.start < 0f && valueRange.endInclusive > 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Label & Numeric readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                color = Color(0xFFE0E0E0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = displayValue,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Custom Slider Track with Thumb & Zero Notch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                val cy = size.height / 2f
                val h = 2.dp.toPx()
                val w = size.width

                if (trackBrush != null) {
                    drawLine(
                        brush = trackBrush,
                        start = Offset(0f, cy),
                        end = Offset(w, cy),
                        strokeWidth = 3.dp.toPx()
                    )
                } else {
                    drawLine(
                        color = Color(0xFF383838),
                        start = Offset(0f, cy),
                        end = Offset(w, cy),
                        strokeWidth = h
                    )
                }

                if (isBipolar) {
                    // Center zero tick mark
                    val cx = w / 2f
                    drawLine(
                        color = Color(0xFF777777),
                        start = Offset(cx, cy - 4.dp.toPx()),
                        end = Offset(cx, cy + 4.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }

            Slider(
                value = value,
                onValueChange = {
                    onDragStart()
                    onValueChange(it)
                },
                onValueChangeFinished = onDragEnd,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


private fun formatLrExposure(v: Float): String {
    val stops = v / 20f
    return if (stops > 0) "+%.2f".format(stops) else "%.2f".format(stops)
}

private fun formatSignedInt(v: Float): String {
    val i = v.toInt()
    return if (i > 0) "+$i" else "$i"
}
