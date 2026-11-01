package com.amresalehin.emregallery.editor.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*

/**
 * Subtool definitions for each Editing Domain.
 */
enum class AdjustSubtool(val label: String) {
    LIGHT("Light"),
    COLOR("Color"),
    DETAIL("Detail"),
    CURVES("Curves"),
    EFFECTS("Effects"),
    OPTICS("Optics"),
    BLUR("Blur"),
    LEVELS("Levels"),
    BALANCE("Balance"),
    PROFILES("Profiles")
}

enum class CropSubtool(val label: String) {
    ASPECT("Aspect"),
    TRANSFORM("Rotate & Flip"),
    STRAIGHTEN("Straighten"),
    PERSPECTIVE("Perspective"),
    LIQUIFY("Liquify")
}

enum class RetouchSubtool(val label: String) {
    BLEMISH("Blemish"),
    SMOOTH("Smooth"),
    HEAL("Heal"),
    SKIN_TONE("Skin Tone")
}

enum class MaskSubtool(val label: String) {
    BRUSH("Brush"),
    LINEAR("Linear"),
    RADIAL("Radial"),
    SUBJECT("Subject"),
    SETTINGS("Settings")
}

enum class OverlaysSubtool(val label: String) {
    TEXT("Text"),
    STICKERS("Stickers"),
    SHAPES("Shapes"),
    CALLOUT("Callout"),
    FRAMES("Frames")
}

enum class AiSubtool(val label: String) {
    EXPAND("Generative Expand"),
    ENHANCE("Neural Enhance"),
    STYLES("Style Transfer")
}

/**
 * Zone 04: Contextual Editing Panel
 * Structure: Active Domain → Tools → Subtools → Parameters → Presets
 * Role: Updates dynamically to surface relevant controls based on the active editing domain.
 *
 * Distinctive Emre styling:
 * - Rounded elevated panel card with subtle top drag handle
 * - Subtools pill row allowing progressive disclosure
 * - Parameter controls container (sliders, curves, presets, tools)
 */
@Composable
fun EditorContextualPanel(
    selectedDomain: EditorDomain,
    sessionState: EditSessionState,
    viewModel: PhotoEditorViewModel,
    // Subtool states
    activeAdjustSubtool: AdjustSubtool,
    onSelectAdjustSubtool: (AdjustSubtool) -> Unit,
    activeCropSubtool: CropSubtool,
    onSelectCropSubtool: (CropSubtool) -> Unit,
    activeRetouchSubtool: RetouchSubtool,
    onSelectRetouchSubtool: (RetouchSubtool) -> Unit,
    activeMaskSubtool: MaskSubtool,
    onSelectMaskSubtool: (MaskSubtool) -> Unit,
    activeOverlaysSubtool: OverlaysSubtool,
    onSelectOverlaysSubtool: (OverlaysSubtool) -> Unit,
    activeAiSubtool: AiSubtool,
    onSelectAiSubtool: (AiSubtool) -> Unit,
    // Adjust domain actions
    onSliderDragStart: (String, String, Float) -> Unit,
    onSliderDragFinished: () -> Unit,
    onOpenCurvesMode: () -> Unit,
    onOpenColorMixMode: () -> Unit,
    onOpenColorGradingMode: () -> Unit,
    // Presets domain
    selectedProfileCategory: String,
    onSelectProfileCategory: (String) -> Unit,
    // Crop domain
    selectedCropPreset: AspectRatioPreset,
    onSelectCropPreset: (AspectRatioPreset) -> Unit,
    currentLiquifyMode: LiquifyMode,
    onLiquifyModeChange: (LiquifyMode) -> Unit,
    liquifyRadius: Float,
    onLiquifyRadiusChange: (Float) -> Unit,
    liquifyStrength: Float,
    onLiquifyStrengthChange: (Float) -> Unit,
    // Retouch domain
    activeRetouchType: RetouchType,
    onSelectRetouchType: (RetouchType) -> Unit,
    healingIntensity: Float,
    onHealingIntensityChange: (Float) -> Unit,
    healingTintColor: Color,
    onHealingTintColorChange: (Color) -> Unit,
    brushWidth: Float,
    onBrushWidthChange: (Float) -> Unit,
    // Mask domain
    maskingSelectionType: MaskingSelectionType,
    onSelectMaskingType: (MaskingSelectionType) -> Unit,
    brushOpacity: Float,
    onBrushOpacityChange: (Float) -> Unit,
    brushFlow: Float,
    onBrushFlowChange: (Float) -> Unit,
    selectedBrushType: BrushType,
    onSelectBrushType: (BrushType) -> Unit,
    selectedBrushColor: Color,
    onSelectBrushColor: (Color) -> Unit,
    onOpenMaskCreationSheet: () -> Unit,
    // Overlays domain
    onFrameChange: (FrameStyle) -> Unit,
    onBorderChange: (BorderSettings) -> Unit,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.panelSurface,
        border = BorderStroke(1.dp, colors.border),
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top drag handle indicator
            EmreDragHandle(colors = colors)

            // Dynamic Subtools Pill Row based on Active Domain
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (selectedDomain) {
                    EditorDomain.ADJUST -> {
                        AdjustSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeAdjustSubtool,
                                onClick = { onSelectAdjustSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_adjust_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.CROP -> {
                        CropSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeCropSubtool,
                                onClick = { onSelectCropSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_crop_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.PRESETS -> {
                        listOf("All", "Simple", "Portraits", "Cinematic", "Film", "B&W", "Vintage", "Duotone").forEach { cat ->
                            EmrePillButton(
                                label = cat,
                                isSelected = cat.equals(selectedProfileCategory, ignoreCase = true),
                                onClick = { onSelectProfileCategory(cat.uppercase()) },
                                colors = colors,
                                testTag = "subtool_preset_${cat.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.RETOUCH -> {
                        RetouchSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeRetouchSubtool,
                                onClick = {
                                    onSelectRetouchSubtool(sub)
                                    when (sub) {
                                        RetouchSubtool.BLEMISH -> onSelectRetouchType(RetouchType.BLEMISH)
                                        RetouchSubtool.SMOOTH -> onSelectRetouchType(RetouchType.SKIN_SMOOTH)
                                        RetouchSubtool.HEAL -> onSelectRetouchType(RetouchType.BLEMISH)
                                        RetouchSubtool.SKIN_TONE -> onSelectRetouchType(RetouchType.SKIN_TONE)
                                    }
                                },
                                colors = colors,
                                testTag = "subtool_retouch_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.MASK -> {
                        MaskSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeMaskSubtool,
                                onClick = { onSelectMaskSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_mask_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.OVERLAYS -> {
                        OverlaysSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeOverlaysSubtool,
                                onClick = { onSelectOverlaysSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_overlays_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.AI_TOOLS -> {
                        AiSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeAiSubtool,
                                onClick = { onSelectAiSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_ai_${sub.name.lowercase()}"
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Contextual Parameters Area (scrollable parameters box)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedDomain) {
                    EditorDomain.ADJUST -> {
                        val adj = sessionState.adjustments
                        when (activeAdjustSubtool) {
                            AdjustSubtool.LIGHT -> {
                                LightPanel(
                                    exposure = adj.exposure,
                                    contrast = adj.contrast,
                                    highlights = adj.highlights,
                                    shadows = adj.shadows,
                                    whites = adj.whites,
                                    blacks = adj.blacks,
                                    onOpenCurve = onOpenCurvesMode,
                                    onSliderDragStart = { name, formatted ->
                                        val initVal = when (name) {
                                            "Exposure" -> adj.exposure
                                            "Contrast" -> adj.contrast
                                            "Highlights" -> adj.highlights
                                            "Shadows" -> adj.shadows
                                            "Whites" -> adj.whites
                                            else -> adj.blacks
                                        }
                                        onSliderDragStart(name, formatted, initVal)
                                    },
                                    onSliderChange = { prop, val_ ->
                                        viewModel.updateAdjustmentValue(prop, val_)
                                    },
                                    onSliderChangeFinished = onSliderDragFinished
                                )
                            }
                            AdjustSubtool.COLOR -> {
                                ColorPanel(
                                    isBlackAndWhite = adj.isBlackAndWhite,
                                    whiteBalancePreset = adj.whiteBalancePreset,
                                    temperature = adj.temperature,
                                    tint = adj.tint,
                                    vibrance = adj.vibrance,
                                    saturation = adj.saturation,
                                    onToggleBw = { viewModel.toggleBlackAndWhite() },
                                    onOpenGrading = onOpenColorGradingMode,
                                    onOpenMix = onOpenColorMixMode,
                                    onSelectWbPreset = { viewModel.setWhiteBalancePreset(it) },
                                    onSliderDragStart = { name, formatted ->
                                        val initVal = when (name) {
                                            "Temp" -> adj.temperature
                                            "Tint" -> adj.tint
                                            "Vibrance" -> adj.vibrance
                                            else -> adj.saturation
                                        }
                                        onSliderDragStart(name, formatted, initVal)
                                    },
                                    onSliderChange = { prop, val_ ->
                                        viewModel.updateAdjustmentValue(prop, val_)
                                    },
                                    onSliderChangeFinished = onSliderDragFinished
                                )
                            }
                            AdjustSubtool.DETAIL -> {
                                DetailPanel(
                                    sharpness = adj.sharpness,
                                    radius = adj.sharpnessRadius,
                                    detail = adj.sharpnessDetail,
                                    noiseReduction = adj.noiseReduction,
                                    colorNoiseReduction = adj.colorNoiseReduction,
                                    onSliderDragStart = { name, formatted ->
                                        val initVal = when (name) {
                                            "Sharpening" -> adj.sharpness
                                            "Radius" -> adj.sharpnessRadius
                                            "Detail" -> adj.sharpnessDetail
                                            "Noise Reduction" -> adj.noiseReduction
                                            else -> adj.colorNoiseReduction
                                        }
                                        onSliderDragStart(name, formatted, initVal)
                                    },
                                    onSliderChange = { prop, val_ ->
                                        viewModel.updateAdjustmentValue(prop, val_)
                                    },
                                    onSliderChangeFinished = onSliderDragFinished
                                )
                            }
                            AdjustSubtool.CURVES -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Interactive Tone Curve Editor",
                                        color = colors.primaryText,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Tap below to open full-screen precision RGB curve graph with master and individual channel controls.",
                                        color = colors.secondaryText,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    EmrePillButton(
                                        label = "Open Tone Curves",
                                        isSelected = true,
                                        onClick = onOpenCurvesMode,
                                        icon = Icons.Default.ShowChart,
                                        colors = colors
                                    )
                                }
                            }
                            AdjustSubtool.EFFECTS -> {
                                EffectsPanel(
                                    texture = adj.texture,
                                    clarity = adj.clarity,
                                    dehaze = adj.dehaze,
                                    vignette = adj.vignette,
                                    grain = adj.grain,
                                    dodge = adj.dodge,
                                    burn = adj.burn,
                                    vignetteMidpoint = adj.vignetteMidpoint,
                                    vignetteFeather = adj.vignetteFeather,
                                    vignetteRoundness = adj.vignetteRoundness,
                                    grainSize = adj.grainSize,
                                    grainRoughness = adj.grainRoughness,
                                    onSliderDragStart = { name, formatted ->
                                        onSliderDragStart(name, formatted, 0f)
                                    },
                                    onSliderChange = { prop, val_ ->
                                        viewModel.updateAdjustmentValue(prop, val_)
                                    },
                                    onSliderChangeFinished = onSliderDragFinished
                                )
                            }
                            AdjustSubtool.OPTICS -> {
                                OpticsPanel(
                                    removeChromaticAberration = adj.removeChromaticAberration,
                                    enableLensCorrections = adj.enableLensCorrections,
                                    distortionCorrection = adj.distortionCorrection,
                                    vignettingCorrection = adj.vignettingCorrection,
                                    onToggleChromaticAberration = { viewModel.setRemoveChromaticAberration(it) },
                                    onToggleLensCorrections = { viewModel.setEnableLensCorrections(it) },
                                    onSliderDragStart = { name, formatted ->
                                        val initVal = if (name == "Distortion") adj.distortionCorrection else adj.vignettingCorrection
                                        onSliderDragStart(name, formatted, initVal)
                                    },
                                    onSliderChange = { prop, val_ ->
                                        viewModel.updateAdjustmentValue(prop, val_)
                                    },
                                    onSliderChangeFinished = onSliderDragFinished
                                )
                            }
                            AdjustSubtool.BLUR -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    val blurStr = "${adj.blur.toInt()}"
                                    LrSliderRow(
                                        title = "Blur Amount",
                                        value = adj.blur,
                                        displayValue = blurStr,
                                        valueRange = 0f..100f,
                                        minLabel = "0",
                                        maxLabel = "+100",
                                        onDragStart = { onSliderDragStart("Blur", blurStr, adj.blur) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Blur", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                }
                            }
                            AdjustSubtool.LEVELS -> {
                                LevelsPanel(
                                    levels = adj.levels,
                                    onLevelsChange = { viewModel.setLevels(it) },
                                    onReset = { viewModel.setLevels(LevelsParams()) }
                                )
                            }
                            AdjustSubtool.BALANCE -> {
                                ColorBalancePanel(
                                    balance = adj.colorBalance,
                                    onBalanceChange = { viewModel.setColorBalance(it) },
                                    onReset = { viewModel.setColorBalance(ColorBalanceParams()) }
                                )
                            }
                            AdjustSubtool.PROFILES -> {
                                ProfilesPanel(
                                    selectedCategory = selectedProfileCategory,
                                    onSelectCategory = onSelectProfileCategory,
                                    onApplyProfile = { viewModel.applyProfile(it) },
                                    thumbnailBitmap = viewModel.thumbnailBitmap.collectAsState().value,
                                    onClose = { onSelectAdjustSubtool(AdjustSubtool.LIGHT) },
                                    onConfirm = { onSelectAdjustSubtool(AdjustSubtool.LIGHT) }
                                )
                            }
                        }
                    }
                    EditorDomain.CROP -> {
                        when (activeCropSubtool) {
                            CropSubtool.ASPECT -> {
                                CropExtendedPanel(
                                    transform = sessionState.transform,
                                    selectedCropPreset = selectedCropPreset,
                                    onSelectCropPreset = onSelectCropPreset,
                                    onRotate90 = { viewModel.rotate90() },
                                    onFlipH = { viewModel.flipHorizontal() },
                                    onFlipV = { viewModel.flipVertical() },
                                    onStraightenChange = { viewModel.setStraighten(it) },
                                    onStraightenFinished = { viewModel.commitSliderChange() },
                                    onAutoLevel = {},
                                    onShapeCropChange = { viewModel.setShapeCrop(it) },
                                    onPerspectiveChange = { x, y -> viewModel.setPerspective(x, y) },
                                    onCornerPinReset = { viewModel.resetCrop() },
                                    onCornerPinEnable = {},
                                    onLiquifyModeChange = onLiquifyModeChange,
                                    currentLiquifyMode = currentLiquifyMode,
                                    liquifyRadius = liquifyRadius,
                                    onLiquifyRadiusChange = onLiquifyRadiusChange,
                                    liquifyStrength = liquifyStrength,
                                    onLiquifyStrengthChange = onLiquifyStrengthChange,
                                    onClearLiquify = { viewModel.clearLiquify() },
                                    onDispersionChange = { viewModel.setDispersion(it) },
                                    onMotionChange = { amt, ang -> viewModel.setMotionBlur(amt, ang) },
                                    onScaleFactorChange = { viewModel.setScaleFactor(it) },
                                    onResetCrop = { viewModel.resetCrop() }
                                )
                            }
                            CropSubtool.TRANSFORM -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    EmrePillButton(
                                        label = "Rotate 90°",
                                        isSelected = false,
                                        onClick = { viewModel.rotate90() },
                                        icon = Icons.Default.RotateRight,
                                        colors = colors
                                    )
                                    EmrePillButton(
                                        label = "Flip H",
                                        isSelected = sessionState.transform.flipHorizontal,
                                        onClick = { viewModel.flipHorizontal() },
                                        icon = Icons.Default.Flip,
                                        colors = colors
                                    )
                                    EmrePillButton(
                                        label = "Flip V",
                                        isSelected = sessionState.transform.flipVertical,
                                        onClick = { viewModel.flipVertical() },
                                        icon = Icons.Default.SwapVert,
                                        colors = colors
                                    )
                                }
                            }
                            CropSubtool.STRAIGHTEN -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    val angle = sessionState.transform.straightenAngle
                                    val str = String.format("%.1f°", angle)
                                    LrSliderRow(
                                        title = "Straighten",
                                        value = angle,
                                        displayValue = str,
                                        valueRange = -45f..45f,
                                        minLabel = "−45°",
                                        maxLabel = "+45°",
                                        onDragStart = { onSliderDragStart("Straighten", str, angle) },
                                        onValueChange = { viewModel.setStraighten(it) },
                                        onValueChangeFinished = {
                                            viewModel.commitSliderChange()
                                            onSliderDragFinished()
                                        }
                                    )
                                }
                            }
                            CropSubtool.PERSPECTIVE -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    val px = sessionState.transform.perspectiveX
                                    val py = sessionState.transform.perspectiveY
                                    val strX = String.format("%.0f", px)
                                    val strY = String.format("%.0f", py)
                                    LrSliderRow(
                                        title = "Horizontal",
                                        value = px,
                                        displayValue = strX,
                                        valueRange = -30f..30f,
                                        onDragStart = { onSliderDragStart("Perspective X", strX, px) },
                                        onValueChange = { viewModel.setPerspective(it, py) },
                                        onValueChangeFinished = {
                                            viewModel.commitSliderChange()
                                            onSliderDragFinished()
                                        }
                                    )
                                    LrSliderRow(
                                        title = "Vertical",
                                        value = py,
                                        displayValue = strY,
                                        valueRange = -30f..30f,
                                        onDragStart = { onSliderDragStart("Perspective Y", strY, py) },
                                        onValueChange = { viewModel.setPerspective(px, it) },
                                        onValueChangeFinished = {
                                            viewModel.commitSliderChange()
                                            onSliderDragFinished()
                                        }
                                    )
                                }
                            }
                            CropSubtool.LIQUIFY -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        LiquifyMode.values().forEach { mode ->
                                            EmrePillButton(
                                                label = mode.label,
                                                isSelected = mode == currentLiquifyMode,
                                                onClick = { onLiquifyModeChange(mode) },
                                                colors = colors
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    LrSliderRow(
                                        title = "Radius",
                                        value = liquifyRadius,
                                        displayValue = "${liquifyRadius.toInt()}px",
                                        valueRange = 10f..100f,
                                        onDragStart = {},
                                        onValueChange = onLiquifyRadiusChange,
                                        onValueChangeFinished = {}
                                    )
                                    LrSliderRow(
                                        title = "Strength",
                                        value = liquifyStrength,
                                        displayValue = "${(liquifyStrength * 100).toInt()}%",
                                        valueRange = 0.1f..1.0f,
                                        onDragStart = {},
                                        onValueChange = onLiquifyStrengthChange,
                                        onValueChangeFinished = {}
                                    )
                                }
                            }
                        }
                    }
                    EditorDomain.PRESETS -> {
                        ProfilesPanel(
                            selectedCategory = selectedProfileCategory,
                            onSelectCategory = onSelectProfileCategory,
                            onApplyProfile = { viewModel.applyProfile(it) },
                            thumbnailBitmap = viewModel.thumbnailBitmap.collectAsState().value,
                            onPresetAdjust = { contrast, brightness, fade, blend ->
                                viewModel.updateAdjustmentValue("Contrast", contrast - 50f)
                                viewModel.updateAdjustmentValue("Brightness", brightness - 50f)
                                viewModel.updatePresetIntensity((100f - fade) / 100f)
                                viewModel.updatePresetBlendMode(blend)
                            }
                        )
                    }
                    EditorDomain.RETOUCH -> {
                        HealingExtendedPanel(
                            activeType = activeRetouchType,
                            onSelectType = onSelectRetouchType,
                            brushRadius = brushWidth * 1.5f,
                            onRadiusChange = { onBrushWidthChange(it / 1.5f) },
                            intensity = healingIntensity,
                            onIntensityChange = onHealingIntensityChange,
                            tintColor = healingTintColor,
                            onTintColorChange = onHealingTintColorChange,
                            strokeCount = sessionState.retouchStrokes.size,
                            onClearStrokes = { viewModel.clearRetouchStrokes() }
                        )
                    }
                    EditorDomain.MASK -> {
                        MaskingExtendedPanel(
                            selectedType = maskingSelectionType,
                            onSelectType = onSelectMaskingType,
                            selectedBrush = selectedBrushType,
                            onSelectBrush = onSelectBrushType,
                            brushColor = selectedBrushColor,
                            onColorSelect = onSelectBrushColor,
                            brushWidth = brushWidth,
                            onWidthChange = onBrushWidthChange,
                            brushOpacity = brushOpacity,
                            onOpacityChange = onBrushOpacityChange,
                            brushFlow = brushFlow,
                            onFlowChange = onBrushFlowChange,
                            magicWandTolerance = 30f,
                            onMagicWandToleranceChange = {},
                            isSelectionActive = sessionState.drawingStrokes.isNotEmpty() || sessionState.maskLayers.isNotEmpty(),
                            onInvertMask = { viewModel.invertMask() },
                            onClearMask = { viewModel.clearMask() },
                            maskLayers = sessionState.maskLayers,
                            activeMaskId = sessionState.activeMaskId,
                            activeComponentId = sessionState.activeComponentId,
                            overlaySettings = sessionState.maskOverlaySettings,
                            onCreateMaskLayer = { viewModel.createMaskLayer(it) },
                            onApplyRecipe = { viewModel.applyMaskRecipe(it) },
                            onSelectMaskLayer = { viewModel.selectMaskLayer(it) },
                            onDeleteMaskLayer = { viewModel.deleteMaskLayer(it) },
                            onDuplicateMaskLayer = { viewModel.duplicateMaskLayer(it) },
                            onToggleMaskEnabled = { viewModel.toggleMaskLayerEnabled(it) },
                            onInvertMaskLayer = { viewModel.invertMaskLayer(it) },
                            onSetMaskAmount = { id, amt -> viewModel.setMaskAmount(id, amt) },
                            onAddComponent = { maskId, type, mode -> viewModel.addComponentToMask(maskId, type, mode) },
                            onAddRecipeComponent = { maskId, recipe, mode -> viewModel.addRecipeComponentToMask(maskId, recipe, mode) },
                            onRemoveComponent = { maskId, compId -> viewModel.removeComponentFromMask(maskId, compId) },
                            onSelectComponent = { maskId, compId -> viewModel.selectMaskComponent(maskId, compId) },
                            onUpdateComponent = { maskId, comp -> viewModel.updateMaskComponent(maskId, comp) },
                            onUpdateAdjustment = { maskId, prop, val_ -> viewModel.updateMaskLocalAdjustment(maskId, prop, val_) },
                            onSetColorTint = { maskId, col -> viewModel.setMaskColorTint(maskId, col) },
                            onResetAdjustments = { maskId -> viewModel.resetMaskAdjustments(maskId) },
                            onToggleOverlay = { viewModel.toggleMaskOverlay() },
                            onSetOverlayColor = { viewModel.setMaskOverlayColor(it) },
                            onSetOverlayOpacity = { viewModel.setMaskOverlayOpacity(it) },
                            onSliderDragStart = { name, formatted ->
                                onSliderDragStart(name, formatted, 0f)
                            },
                            onSliderDragFinished = onSliderDragFinished,
                            showCreationSheet = false,
                            onDismissCreationSheet = {},
                            onOpenCreationSheet = onOpenMaskCreationSheet
                        )
                    }
                    EditorDomain.OVERLAYS -> {
                        OverlaysWorkspacePanel(
                            sessionState = sessionState,
                            viewModel = viewModel,
                            onFrameChange = onFrameChange,
                            onBorderChange = onBorderChange,
                            onAddText = { text, col, bgCol ->
                                viewModel.addTextOverlay(text, col, bgCol)
                            },
                            onUpdateText = { viewModel.updateTextOverlay(it) },
                            onDeleteText = { viewModel.deleteTextOverlay(it) },
                            onDuplicateText = { viewModel.duplicateTextOverlay(it) },
                            onAddSticker = { viewModel.addSticker(it) },
                            onUpdateSticker = { viewModel.updateSticker(it) },
                            onDeleteSticker = { viewModel.deleteSticker(it) },
                            onDuplicateSticker = { viewModel.duplicateSticker(it) },
                            onAddShape = { viewModel.addShape(it) },
                            onUpdateShape = { viewModel.updateShape(it) },
                            onDeleteShape = { viewModel.deleteShape(it) },
                            onDuplicateShape = { viewModel.duplicateShape(it) },
                            onAddCallout = { text, style ->
                                viewModel.addCalloutWithStyle(text, style)
                            },
                            onUpdateCallout = { viewModel.updateCallout(it) },
                            onDeleteCallout = { viewModel.deleteCallout(it) },
                            onAddLensFlare = { viewModel.addLensFlare(it) },
                            onUpdateLensFlare = { viewModel.updateLensFlare(it) },
                            onDeleteLensFlare = { viewModel.deleteLensFlare(it) },
                            onBackgroundChange = { viewModel.setBackgroundSettings(it) }
                        )
                    }
                    EditorDomain.AI_TOOLS -> {
                        AiToolsPanel(
                            aiSettings = sessionState.aiSettings,
                            onExpandRatioChange = { viewModel.setAiExpand(it) },
                            onSelectStyleTransfer = { viewModel.setAiStyleTransfer(it) },
                            onSelectTryOn = { viewModel.setAiTryOnStyle(it) }
                        )
                    }
                }
            }
        }
    }
}
