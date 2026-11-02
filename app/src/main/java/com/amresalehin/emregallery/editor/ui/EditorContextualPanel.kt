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
enum class LightSubtool(val label: String) {
    ALL("All Sliders"),
    EXPOSURE("Exposure & Contrast"),
    TONE("Highlights & Shadows"),
    POINTS("Whites & Blacks")
}

enum class ColorSubtool(val label: String) {
    BASIC("Basic Color"),
    COLOR_MIX("Color Mix"),
    COLOR_GRADING("Color Grading"),
    BALANCE("Color Balance"),
    FX("Color FX")
}

enum class CurvesSubtool(val label: String) {
    RGB("RGB Master"),
    RED("Red Channel"),
    GREEN("Green Channel"),
    BLUE("Blue Channel"),
    PRESETS("Curve Presets")
}

enum class DetailSubtool(val label: String) {
    ALL("All Detail"),
    SHARPEN("Sharpening"),
    NOISE("Noise Reduction")
}

enum class VignetteSubtool(val label: String) {
    CUSTOM("Custom"),
    PRESETS("Presets")
}

enum class EffectsSubtool(val label: String) {
    ALL("All Effects"),
    TEXTURE_CLARITY("Texture & Clarity"),
    DEHAZE("Dehaze"),
    GRAIN("Grain"),
    DODGE_BURN("Dodge & Burn")
}

enum class BlurSubtool(val label: String) {
    CUSTOM("Blur Slider"),
    PRESETS("Presets")
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
    STICKERS("Stickers"),
    LAYERS("Layers")
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
    activeLightSubtool: LightSubtool = LightSubtool.ALL,
    onSelectLightSubtool: (LightSubtool) -> Unit = {},
    activeColorSubtool: ColorSubtool = ColorSubtool.BASIC,
    onSelectColorSubtool: (ColorSubtool) -> Unit = {},
    activeCurvesSubtool: CurvesSubtool = CurvesSubtool.RGB,
    onSelectCurvesSubtool: (CurvesSubtool) -> Unit = {},
    activeEffectsSubtool: EffectsSubtool = EffectsSubtool.ALL,
    onSelectEffectsSubtool: (EffectsSubtool) -> Unit = {},
    activeDetailSubtool: DetailSubtool = DetailSubtool.ALL,
    onSelectDetailSubtool: (DetailSubtool) -> Unit = {},
    activeBlurSubtool: BlurSubtool = BlurSubtool.CUSTOM,
    onSelectBlurSubtool: (BlurSubtool) -> Unit = {},
    activeVignetteSubtool: VignetteSubtool = VignetteSubtool.CUSTOM,
    onSelectVignetteSubtool: (VignetteSubtool) -> Unit = {},
    activeCropSubtool: CropSubtool,
    onSelectCropSubtool: (CropSubtool) -> Unit,
    activeRetouchSubtool: RetouchSubtool,
    onSelectRetouchSubtool: (RetouchSubtool) -> Unit,
    activeMaskSubtool: MaskSubtool,
    onSelectMaskSubtool: (MaskSubtool) -> Unit,
    activeTextSubtool: TextSubtool = TextSubtool.ALL,
    onSelectTextSubtool: (TextSubtool) -> Unit = {},
    activeShapeSubtool: ShapeSubtool = ShapeSubtool.SHAPES,
    onSelectShapeSubtool: (ShapeSubtool) -> Unit = {},
    activeCalloutSubtool: CalloutSubtool = CalloutSubtool.STYLES,
    onSelectCalloutSubtool: (CalloutSubtool) -> Unit = {},
    activeFrameSubtool: FrameSubtool = FrameSubtool.FRAMES,
    onSelectFrameSubtool: (FrameSubtool) -> Unit = {},
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
                    EditorDomain.LIGHT -> {
                        LightSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeLightSubtool,
                                onClick = { onSelectLightSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_light_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.COLOR -> {
                        ColorSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeColorSubtool,
                                onClick = { onSelectColorSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_color_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.CURVES -> {
                        CurvesSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeCurvesSubtool,
                                onClick = { onSelectCurvesSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_curves_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.EFFECTS -> {
                        EffectsSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeEffectsSubtool,
                                onClick = { onSelectEffectsSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_effects_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.DETAIL -> {
                        DetailSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeDetailSubtool,
                                onClick = { onSelectDetailSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_detail_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.BLUR -> {
                        BlurSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeBlurSubtool,
                                onClick = { onSelectBlurSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_blur_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.VIGNETTE -> {
                        VignetteSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeVignetteSubtool,
                                onClick = { onSelectVignetteSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_vignette_${sub.name.lowercase()}"
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
                    EditorDomain.TEXT -> {
                        TextSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeTextSubtool,
                                onClick = { onSelectTextSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_text_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.SHAPE -> {
                        ShapeSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeShapeSubtool,
                                onClick = { onSelectShapeSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_shape_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.CALLOUT -> {
                        CalloutSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeCalloutSubtool,
                                onClick = { onSelectCalloutSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_callout_${sub.name.lowercase()}"
                            )
                        }
                    }
                    EditorDomain.FRAME -> {
                        FrameSubtool.values().forEach { sub ->
                            EmrePillButton(
                                label = sub.label,
                                isSelected = sub == activeFrameSubtool,
                                onClick = { onSelectFrameSubtool(sub) },
                                colors = colors,
                                testTag = "subtool_frame_${sub.name.lowercase()}"
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
                    EditorDomain.LIGHT -> {
                        val adj = sessionState.adjustments
                        when (activeLightSubtool) {
                            LightSubtool.ALL -> {
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
                            LightSubtool.EXPOSURE -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    val expEv = adj.exposure / 20f
                                    val expStr = when {
                                        expEv > 0.001f -> "+%.2f EV".format(expEv)
                                        expEv < -0.001f -> "−%.2f EV".format(-expEv)
                                        else -> "0.00 EV"
                                    }
                                    LrSliderRow(
                                        title = "Exposure",
                                        value = adj.exposure,
                                        displayValue = expStr,
                                        valueRange = -100f..100f,
                                        isEv = true,
                                        unit = "EV",
                                        minLabel = "−5.00",
                                        maxLabel = "+5.00",
                                        onDragStart = { onSliderDragStart("Exposure", expStr, adj.exposure) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Exposure", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                    val contrastStr = when {
                                        adj.contrast > 0f -> "+${adj.contrast.toInt()}"
                                        adj.contrast < 0f -> "−${(-adj.contrast).toInt()}"
                                        else -> "0"
                                    }
                                    LrSliderRow(
                                        title = "Contrast",
                                        value = adj.contrast,
                                        displayValue = contrastStr,
                                        valueRange = -100f..100f,
                                        minLabel = "−100",
                                        maxLabel = "+100",
                                        onDragStart = { onSliderDragStart("Contrast", contrastStr, adj.contrast) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Contrast", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                }
                            }
                            LightSubtool.TONE -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    val highStr = when {
                                        adj.highlights > 0f -> "+${adj.highlights.toInt()}"
                                        adj.highlights < 0f -> "−${(-adj.highlights).toInt()}"
                                        else -> "0"
                                    }
                                    LrSliderRow(
                                        title = "Highlights",
                                        value = adj.highlights,
                                        displayValue = highStr,
                                        valueRange = -100f..100f,
                                        minLabel = "−100",
                                        maxLabel = "+100",
                                        onDragStart = { onSliderDragStart("Highlights", highStr, adj.highlights) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Highlights", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                    val shadowStr = when {
                                        adj.shadows > 0f -> "+${adj.shadows.toInt()}"
                                        adj.shadows < 0f -> "−${(-adj.shadows).toInt()}"
                                        else -> "0"
                                    }
                                    LrSliderRow(
                                        title = "Shadows",
                                        value = adj.shadows,
                                        displayValue = shadowStr,
                                        valueRange = -100f..100f,
                                        minLabel = "−100",
                                        maxLabel = "+100",
                                        onDragStart = { onSliderDragStart("Shadows", shadowStr, adj.shadows) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Shadows", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                }
                            }
                            LightSubtool.POINTS -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    val whitesStr = when {
                                        adj.whites > 0f -> "+${adj.whites.toInt()}"
                                        adj.whites < 0f -> "−${(-adj.whites).toInt()}"
                                        else -> "0"
                                    }
                                    LrSliderRow(
                                        title = "Whites",
                                        value = adj.whites,
                                        displayValue = whitesStr,
                                        valueRange = -100f..100f,
                                        minLabel = "−100",
                                        maxLabel = "+100",
                                        onDragStart = { onSliderDragStart("Whites", whitesStr, adj.whites) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Whites", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                    val blacksStr = when {
                                        adj.blacks > 0f -> "+${adj.blacks.toInt()}"
                                        adj.blacks < 0f -> "−${(-adj.blacks).toInt()}"
                                        else -> "0"
                                    }
                                    LrSliderRow(
                                        title = "Blacks",
                                        value = adj.blacks,
                                        displayValue = blacksStr,
                                        valueRange = -100f..100f,
                                        minLabel = "−100",
                                        maxLabel = "+100",
                                        onDragStart = { onSliderDragStart("Blacks", blacksStr, adj.blacks) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Blacks", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                }
                            }
                        }
                    }
                    EditorDomain.COLOR -> {
                        val adj = sessionState.adjustments
                        when (activeColorSubtool) {
                            ColorSubtool.BASIC -> {
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
                            ColorSubtool.COLOR_MIX -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "8-Band Targeted Color Mix (HSL)",
                                        color = colors.primaryText,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Adjust specific hues, saturations, and luminances independently across 8 spectral color bands.",
                                        color = colors.secondaryText,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    EmrePillButton(
                                        label = "Open Precision Color Mix",
                                        isSelected = true,
                                        onClick = onOpenColorMixMode,
                                        icon = Icons.Default.Palette,
                                        colors = colors
                                    )
                                }
                            }
                            ColorSubtool.COLOR_GRADING -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Split Toning & Color Grading",
                                        color = colors.primaryText,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Grade Shadows and Highlights with cinematic chromatic hues and tonal balance.",
                                        color = colors.secondaryText,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    EmrePillButton(
                                        label = "Open Color Grading Wheels",
                                        isSelected = true,
                                        onClick = onOpenColorGradingMode,
                                        icon = Icons.Default.Tune,
                                        colors = colors
                                    )
                                }
                            }
                            ColorSubtool.BALANCE -> {
                                ColorBalancePanel(
                                    balance = adj.colorBalance,
                                    onBalanceChange = { viewModel.setColorBalance(it) },
                                    onReset = { viewModel.setColorBalance(ColorBalanceParams()) }
                                )
                            }
                            ColorSubtool.FX -> {
                                ColorFxPanel(
                                    adj = adj,
                                    onGradientMapChange = { viewModel.setGradientMap(it) },
                                    onDuotoneChange = { viewModel.setDuotone(it) },
                                    onColorReplaceChange = { viewModel.setColorReplace(it) },
                                    onColorSplashChange = { viewModel.setColorSplash(it) }
                                )
                            }
                        }
                    }
                    EditorDomain.CURVES -> {
                        val adj = sessionState.adjustments
                        val currentChannel = when (activeCurvesSubtool) {
                            CurvesSubtool.RED -> CurveChannel.RED
                            CurvesSubtool.GREEN -> CurveChannel.GREEN
                            CurvesSubtool.BLUE -> CurveChannel.BLUE
                            else -> CurveChannel.RGB
                        }
                        CurvesDomainPanel(
                            curves = adj.curves,
                            activeChannel = currentChannel,
                            onSelectChannel = { ch ->
                                when (ch) {
                                    CurveChannel.RGB -> onSelectCurvesSubtool(CurvesSubtool.RGB)
                                    CurveChannel.RED -> onSelectCurvesSubtool(CurvesSubtool.RED)
                                    CurveChannel.GREEN -> onSelectCurvesSubtool(CurvesSubtool.GREEN)
                                    CurveChannel.BLUE -> onSelectCurvesSubtool(CurvesSubtool.BLUE)
                                }
                            },
                            onOpenFullscreenCurves = onOpenCurvesMode,
                            onApplyPreset = { presetKey ->
                                viewModel.applyCurvePreset(presetKey)
                            },
                            onResetChannel = { ch ->
                                viewModel.resetCurve(ch)
                            }
                        )
                    }
                    EditorDomain.EFFECTS -> {
                        val adj = sessionState.adjustments
                        when (activeEffectsSubtool) {
                            EffectsSubtool.ALL -> {
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
                            EffectsSubtool.TEXTURE_CLARITY -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    val texStr = when {
                                        adj.texture > 0f -> "+${adj.texture.toInt()}"
                                        adj.texture < 0f -> "−${(-adj.texture).toInt()}"
                                        else -> "0"
                                    }
                                    LrSliderRow(
                                        title = "Texture",
                                        value = adj.texture,
                                        displayValue = texStr,
                                        valueRange = -100f..100f,
                                        minLabel = "−100",
                                        maxLabel = "+100",
                                        onDragStart = { onSliderDragStart("Texture", texStr, adj.texture) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Texture", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                    val clarStr = when {
                                        adj.clarity > 0f -> "+${adj.clarity.toInt()}"
                                        adj.clarity < 0f -> "−${(-adj.clarity).toInt()}"
                                        else -> "0"
                                    }
                                    LrSliderRow(
                                        title = "Clarity",
                                        value = adj.clarity,
                                        displayValue = clarStr,
                                        valueRange = -100f..100f,
                                        minLabel = "−100",
                                        maxLabel = "+100",
                                        onDragStart = { onSliderDragStart("Clarity", clarStr, adj.clarity) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Clarity", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                }
                            }
                            EffectsSubtool.DEHAZE -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    val dehazeStr = when {
                                        adj.dehaze > 0f -> "+${adj.dehaze.toInt()}"
                                        adj.dehaze < 0f -> "−${(-adj.dehaze).toInt()}"
                                        else -> "0"
                                    }
                                    LrSliderRow(
                                        title = "Dehaze",
                                        value = adj.dehaze,
                                        displayValue = dehazeStr,
                                        valueRange = -100f..100f,
                                        minLabel = "−100",
                                        maxLabel = "+100",
                                        onDragStart = { onSliderDragStart("Dehaze", dehazeStr, adj.dehaze) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Dehaze", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                }
                            }
                            EffectsSubtool.GRAIN -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    val grainStr = "${adj.grain.toInt()}"
                                    LrSliderRow(
                                        title = "Grain Amount",
                                        value = adj.grain,
                                        displayValue = grainStr,
                                        valueRange = 0f..100f,
                                        minLabel = "0",
                                        maxLabel = "100",
                                        onDragStart = { onSliderDragStart("Grain", grainStr, adj.grain) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Grain", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                    val sizeStr = "${adj.grainSize.toInt()}"
                                    LrSliderRow(
                                        title = "Grain Size",
                                        value = adj.grainSize,
                                        displayValue = sizeStr,
                                        valueRange = 10f..60f,
                                        minLabel = "10",
                                        maxLabel = "60",
                                        onDragStart = { onSliderDragStart("Grain Size", sizeStr, adj.grainSize) },
                                        onValueChange = { viewModel.updateAdjustmentValue("grainSize", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                    val roughStr = "${adj.grainRoughness.toInt()}"
                                    LrSliderRow(
                                        title = "Grain Roughness",
                                        value = adj.grainRoughness,
                                        displayValue = roughStr,
                                        valueRange = 0f..100f,
                                        minLabel = "0",
                                        maxLabel = "100",
                                        onDragStart = { onSliderDragStart("Grain Roughness", roughStr, adj.grainRoughness) },
                                        onValueChange = { viewModel.updateAdjustmentValue("grainRoughness", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                }
                            }
                            EffectsSubtool.DODGE_BURN -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    val dodgeStr = "${adj.dodge.toInt()}"
                                    LrSliderRow(
                                        title = "Global Dodge",
                                        value = adj.dodge,
                                        displayValue = dodgeStr,
                                        valueRange = 0f..100f,
                                        minLabel = "0",
                                        maxLabel = "100",
                                        onDragStart = { onSliderDragStart("Dodge", dodgeStr, adj.dodge) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Dodge", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                    val burnStr = "${adj.burn.toInt()}"
                                    LrSliderRow(
                                        title = "Global Burn",
                                        value = adj.burn,
                                        displayValue = burnStr,
                                        valueRange = 0f..100f,
                                        minLabel = "0",
                                        maxLabel = "100",
                                        onDragStart = { onSliderDragStart("Burn", burnStr, adj.burn) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Burn", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                }
                            }
                        }
                    }
                    EditorDomain.DETAIL -> {
                        val adj = sessionState.adjustments
                        when (activeDetailSubtool) {
                            DetailSubtool.ALL -> {
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
                            DetailSubtool.SHARPEN -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    val sharpStr = "${adj.sharpness.toInt()}"
                                    LrSliderRow(
                                        title = "Sharpening",
                                        value = adj.sharpness,
                                        displayValue = sharpStr,
                                        valueRange = 0f..100f,
                                        minLabel = "0",
                                        maxLabel = "100",
                                        onDragStart = { onSliderDragStart("Sharpening", sharpStr, adj.sharpness) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Sharpness", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                    val radStr = String.format(java.util.Locale.US, "%.1f", adj.sharpnessRadius)
                                    LrSliderRow(
                                        title = "Radius",
                                        value = adj.sharpnessRadius,
                                        displayValue = radStr,
                                        valueRange = 0.5f..3.0f,
                                        minLabel = "0.5",
                                        maxLabel = "3.0",
                                        onDragStart = { onSliderDragStart("Radius", radStr, adj.sharpnessRadius) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Radius", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                    val detStr = "${adj.sharpnessDetail.toInt()}"
                                    LrSliderRow(
                                        title = "Detail",
                                        value = adj.sharpnessDetail,
                                        displayValue = detStr,
                                        valueRange = 0f..100f,
                                        minLabel = "0",
                                        maxLabel = "100",
                                        onDragStart = { onSliderDragStart("Detail", detStr, adj.sharpnessDetail) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Detail", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                }
                            }
                            DetailSubtool.NOISE -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    val nrStr = "${adj.noiseReduction.toInt()}"
                                    LrSliderRow(
                                        title = "Luminance Noise",
                                        value = adj.noiseReduction,
                                        displayValue = nrStr,
                                        valueRange = 0f..100f,
                                        minLabel = "0",
                                        maxLabel = "100",
                                        onDragStart = { onSliderDragStart("Noise Reduction", nrStr, adj.noiseReduction) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Noise Reduction", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                    val cnrStr = "${adj.colorNoiseReduction.toInt()}"
                                    LrSliderRow(
                                        title = "Color Noise",
                                        value = adj.colorNoiseReduction,
                                        displayValue = cnrStr,
                                        valueRange = 0f..100f,
                                        minLabel = "0",
                                        maxLabel = "100",
                                        onDragStart = { onSliderDragStart("Color Noise", cnrStr, adj.colorNoiseReduction) },
                                        onValueChange = { viewModel.updateAdjustmentValue("Color Noise Reduction", it) },
                                        onValueChangeFinished = onSliderDragFinished
                                    )
                                }
                            }
                        }
                    }
                    EditorDomain.VIGNETTE -> {
                        val adj = sessionState.adjustments
                        VignettePanel(
                            vignette = adj.vignette,
                            vignetteMidpoint = adj.vignetteMidpoint,
                            vignetteFeather = adj.vignetteFeather,
                            vignetteRoundness = adj.vignetteRoundness,
                            onVignetteChange = { viewModel.updateAdjustmentValue("Vignette", it) },
                            onMidpointChange = { viewModel.updateAdjustmentValue("vignetteMidpoint", it) },
                            onFeatherChange = { viewModel.updateAdjustmentValue("vignetteFeather", it) },
                            onRoundnessChange = { viewModel.updateAdjustmentValue("vignetteRoundness", it) },
                            onApplyPreset = { amt, mid, fth, rnd ->
                                viewModel.setVignettePreset(amt, mid, fth, rnd)
                            },
                            onReset = {
                                viewModel.setVignettePreset(0f, 50f, 50f, 0f)
                            },
                            onSliderDragStart = { name, formatted ->
                                onSliderDragStart(name, formatted, 0f)
                            },
                            onSliderDragFinished = onSliderDragFinished
                        )
                    }
                    EditorDomain.BLUR -> {
                        val adj = sessionState.adjustments
                        BlurPanel(
                            blur = adj.blur,
                            onBlurChange = { viewModel.updateAdjustmentValue("Blur", it) },
                            onReset = {
                                viewModel.updateAdjustmentValue("Blur", 0f)
                                viewModel.commitSliderChange()
                            },
                            onSliderDragStart = { name, formatted ->
                                onSliderDragStart(name, formatted, adj.blur)
                            },
                            onSliderDragFinished = onSliderDragFinished
                        )
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
                    EditorDomain.TEXT -> {
                        TextPanel(
                            sessionState = sessionState,
                            activeSubtool = activeTextSubtool,
                            viewModel = viewModel,
                            colors = colors
                        )
                    }
                    EditorDomain.SHAPE -> {
                        ShapePanel(
                            sessionState = sessionState,
                            activeSubtool = activeShapeSubtool,
                            viewModel = viewModel,
                            colors = colors
                        )
                    }
                    EditorDomain.CALLOUT -> {
                        CalloutPanel(
                            sessionState = sessionState,
                            activeSubtool = activeCalloutSubtool,
                            viewModel = viewModel,
                            colors = colors
                        )
                    }
                    EditorDomain.FRAME -> {
                        FramePanel(
                            sessionState = sessionState,
                            activeSubtool = activeFrameSubtool,
                            viewModel = viewModel,
                            onFrameChange = onFrameChange,
                            onBorderChange = onBorderChange,
                            colors = colors
                        )
                    }
                    EditorDomain.OVERLAYS -> {
                        when (activeOverlaysSubtool) {
                            OverlaysSubtool.STICKERS -> {
                                StickersPanel(
                                    sessionState = sessionState,
                                    viewModel = viewModel,
                                    colors = colors
                                )
                            }
                            OverlaysSubtool.LAYERS -> {
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
                        }
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
