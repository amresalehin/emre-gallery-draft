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
    // Active Subtool states
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
    // Retouch domain (default fallbacks)
    activeRetouchType: RetouchType = RetouchType.BLEMISH,
    onSelectRetouchType: (RetouchType) -> Unit = {},
    healingIntensity: Float = 1.0f,
    onHealingIntensityChange: (Float) -> Unit = {},
    healingTintColor: Color = Color(0xFFFFCC80),
    onHealingTintColorChange: (Color) -> Unit = {},
    brushWidth: Float = 10f,
    onBrushWidthChange: (Float) -> Unit = {},
    // Independent retouch domain controls
    blemishRadius: Float = 24f,
    onBlemishRadiusChange: (Float) -> Unit = {},
    blemishIntensity: Float = 1.0f,
    onBlemishIntensityChange: (Float) -> Unit = {},
    smoothRadius: Float = 36f,
    onSmoothRadiusChange: (Float) -> Unit = {},
    smoothIntensity: Float = 0.6f,
    onSmoothIntensityChange: (Float) -> Unit = {},
    healRadius: Float = 30f,
    onHealRadiusChange: (Float) -> Unit = {},
    healIntensity: Float = 1.0f,
    onHealIntensityChange: (Float) -> Unit = {},
    healRetouchType: RetouchType = RetouchType.PATCH_HEALING,
    onSelectHealRetouchType: (RetouchType) -> Unit = {},
    skinToneRadius: Float = 40f,
    onSkinToneRadiusChange: (Float) -> Unit = {},
    skinToneIntensity: Float = 0.5f,
    onSkinToneIntensityChange: (Float) -> Unit = {},
    skinToneColor: Color = Color(0xFFFFCC80),
    onSkinToneColorChange: (Color) -> Unit = {},
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
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top drag handle indicator
            EmreDragHandle(colors = colors)

            val domainsWithoutSubtools = setOf(
                EditorDomain.LIGHT,
                EditorDomain.COLOR,
                EditorDomain.CURVES,
                EditorDomain.EFFECTS,
                EditorDomain.DETAIL,
                EditorDomain.BLUR,
                EditorDomain.VIGNETTE,
                EditorDomain.CROP,
                EditorDomain.BLEMISH,
                EditorDomain.SMOOTH,
                EditorDomain.HEAL,
                EditorDomain.SKIN_TONE
            )
            if (selectedDomain !in domainsWithoutSubtools) {
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
                        EditorDomain.LIGHT,
                        EditorDomain.COLOR,
                        EditorDomain.CURVES,
                        EditorDomain.EFFECTS,
                        EditorDomain.DETAIL,
                        EditorDomain.BLUR,
                        EditorDomain.VIGNETTE,
                        EditorDomain.CROP,
                        EditorDomain.BLEMISH,
                        EditorDomain.SMOOTH,
                        EditorDomain.HEAL,
                        EditorDomain.SKIN_TONE -> {
                            // Dedicated single-panel domains without tier 2 subtool pills
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
                }
            }
            Spacer(Modifier.height(4.dp))
        }

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
                    EditorDomain.COLOR -> {
                        val adj = sessionState.adjustments
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
                    EditorDomain.CURVES -> {
                        val adj = sessionState.adjustments
                        var currentCurveChannel by remember { mutableStateOf(CurveChannel.RGB) }
                        CurvesDomainPanel(
                            curves = adj.curves,
                            activeChannel = currentCurveChannel,
                            onSelectChannel = { ch ->
                                currentCurveChannel = ch
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
                    EditorDomain.DETAIL -> {
                        val adj = sessionState.adjustments
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
                    EditorDomain.BLEMISH -> {
                        BlemishDomainPanel(
                            brushRadius = blemishRadius,
                            onRadiusChange = onBlemishRadiusChange,
                            intensity = blemishIntensity,
                            onIntensityChange = onBlemishIntensityChange,
                            strokeCount = sessionState.retouchStrokes.count { it.type == RetouchType.BLEMISH },
                            onClearStrokes = { viewModel.clearRetouchStrokes(RetouchType.BLEMISH) },
                            colors = colors
                        )
                    }
                    EditorDomain.SMOOTH -> {
                        SmoothDomainPanel(
                            brushRadius = smoothRadius,
                            onRadiusChange = onSmoothRadiusChange,
                            intensity = smoothIntensity,
                            onIntensityChange = onSmoothIntensityChange,
                            strokeCount = sessionState.retouchStrokes.count { it.type == RetouchType.SKIN_SMOOTH },
                            onClearStrokes = { viewModel.clearRetouchStrokes(RetouchType.SKIN_SMOOTH) },
                            colors = colors
                        )
                    }
                    EditorDomain.HEAL -> {
                        HealDomainPanel(
                            activeType = healRetouchType,
                            onSelectType = onSelectHealRetouchType,
                            brushRadius = healRadius,
                            onRadiusChange = onHealRadiusChange,
                            intensity = healIntensity,
                            onIntensityChange = onHealIntensityChange,
                            strokeCount = sessionState.retouchStrokes.count { it.type in listOf(RetouchType.PATCH_HEALING, RetouchType.CLONE_STAMP, RetouchType.OBJECT_REMOVAL, RetouchType.WRINKLE_REMOVAL) },
                            onClearStrokes = { viewModel.clearRetouchStrokes(RetouchType.PATCH_HEALING, RetouchType.CLONE_STAMP, RetouchType.OBJECT_REMOVAL, RetouchType.WRINKLE_REMOVAL) },
                            colors = colors
                        )
                    }
                    EditorDomain.SKIN_TONE -> {
                        SkinToneDomainPanel(
                            brushRadius = skinToneRadius,
                            onRadiusChange = onSkinToneRadiusChange,
                            intensity = skinToneIntensity,
                            onIntensityChange = onSkinToneIntensityChange,
                            tintColor = skinToneColor,
                            onTintColorChange = onSkinToneColorChange,
                            strokeCount = sessionState.retouchStrokes.count { it.type == RetouchType.SKIN_TONE },
                            onClearStrokes = { viewModel.clearRetouchStrokes(RetouchType.SKIN_TONE) },
                            colors = colors
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
                }
            }
        }
    }
}
