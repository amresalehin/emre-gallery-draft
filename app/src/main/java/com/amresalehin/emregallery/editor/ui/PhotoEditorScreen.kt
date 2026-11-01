package com.amresalehin.emregallery.editor.ui

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.MediaRepository
import com.amresalehin.emregallery.editor.*
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Main Photo Editor screen for Emre Gallery.
 * Features:
 * - Proper editing modes (Crop & Rotate, Tone Curves, Color Mix, Color Grading, Presets, Brushes, Retouch)
 * - Consistent Cancel / Apply behavior across all modes
 * - Context-sensitive tool navigation
 * - Adaptive editing panels
 * - Professional canvas gestures (pinch-to-zoom 1x..5x, two-finger pan, double-tap zoom/fit, 8-handle crop box)
 * - Landscape-specific layout (side-by-side inspector pane)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    item: MediaItem,
    mediaRepository: MediaRepository,
    onBack: () -> Unit,
    onSaved: (MediaItem) -> Unit,
    viewModel: PhotoEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(item.uri) {
        viewModel.initialize(context, item)
    }

    val previewBitmap by viewModel.previewBitmap.collectAsStateWithLifecycle()
    val originalBitmap by viewModel.originalBitmap.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val processingMessage by viewModel.processingMessage.collectAsStateWithLifecycle()
    val isReplaying by viewModel.isReplaying.collectAsStateWithLifecycle()
    val replayProgress by viewModel.replayProgress.collectAsStateWithLifecycle()
    val thumbnailBitmap by viewModel.thumbnailBitmap.collectAsStateWithLifecycle()

    // Navigation & Tool State
    var currentBottomTab by remember { mutableStateOf(MainBottomTab.ADJUST) }
    var activeAdjustCategory by remember { mutableStateOf(AdjustCategory.LIGHT) }
    var activeCurveChannel by remember { mutableStateOf(CurveChannel.RGB) }
    var selectedProfileCategory by remember { mutableStateOf("SIMPLE") }
    var selectedColorMixBand by remember { mutableStateOf(ColorBand.RED) }

    // Floating HUD slider state for interactive drag feedback
    var activeDraggingSliderName by remember { mutableStateOf<String?>(null) }
    var activeDraggingSliderValue by remember { mutableStateOf("") }
    var activeDraggingSliderCurrentValue by remember { mutableFloatStateOf(0f) }

    // Secondary editing tools state
    var selectedCropPreset by remember { mutableStateOf(AspectRatioPreset.FREE) }
    var selectedBrushType by remember { mutableStateOf(BrushType.PEN) }
    var selectedBrushColor by remember { mutableStateOf(Color.White) }
    var brushWidth by remember { mutableFloatStateOf(10f) }
    var brushOpacity by remember { mutableFloatStateOf(1.0f) }
    var brushFlow by remember { mutableFloatStateOf(1.0f) }
    var maskingSelectionType by remember { mutableStateOf(MaskingSelectionType.BRUSH) }
    var magicWandTolerance by remember { mutableFloatStateOf(30f) }

    var activeRetouchType by remember { mutableStateOf(RetouchType.BLEMISH) }
    var healingIntensity by remember { mutableFloatStateOf(1.0f) }
    var healingTintColor by remember { mutableStateOf(Color(0xFF2979FF)) }

    var currentLiquifyMode by remember { mutableStateOf(LiquifyMode.PUSH) }
    var liquifyRadius by remember { mutableFloatStateOf(40f) }
    var liquifyStrength by remember { mutableFloatStateOf(0.5f) }

    // Dialogs & Sheets
    var showAiSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Back Handler: Respects history dialog, active mode Cancel first, then tool tab, then discard confirmation
    BackHandler {
        when {
            showHistoryDialog -> showHistoryDialog = false
            currentMode != EditorMode.NONE -> viewModel.cancelModeChanges()
            showAiSheet -> showAiSheet = false
            showExportSheet -> showExportSheet = false
            currentBottomTab != MainBottomTab.ADJUST -> {
                currentBottomTab = MainBottomTab.ADJUST
                viewModel.setActiveTool(EditorTool.ADJUST)
            }
            canUndo -> showDiscardDialog = true
            else -> onBack()
        }
    }

    val doSave: (Boolean, Int) -> Unit = { isPng, quality ->
        scope.launch {
            val saved = viewModel.saveResult(context, mediaRepository, isPng, quality)
            if (saved != null) {
                Toast.makeText(context, "Saved photo to gallery", Toast.LENGTH_SHORT).show()
                onSaved(saved)
            } else {
                Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val sharePhoto: () -> Unit = {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share photo", Toast.LENGTH_SHORT).show()
        }
    }

    fun startSliderDrag(name: String, formattedVal: String, initialVal: Float) {
        activeDraggingSliderName = name
        activeDraggingSliderValue = formattedVal
        activeDraggingSliderCurrentValue = initialVal
    }

    fun finishSliderDrag() {
        activeDraggingSliderName = null
        viewModel.commitSliderChange()
    }

    // Adaptive Panel Content Composable used in both Portrait & Landscape
    @Composable
    fun PanelContent(modifier: Modifier = Modifier) {
        Column(modifier = modifier.fillMaxWidth()) {
            if (currentMode in listOf(EditorMode.CURVES, EditorMode.COLOR_MIX, EditorMode.COLOR_GRADING)) {
                // Tone Curves, Color Mix, and Color Grading sub-inspectors
                when (currentMode) {
                    EditorMode.CURVES -> {
                        EditorModeHeader(
                            title = "Tone Curves",
                            onCancel = { viewModel.cancelModeChanges() },
                            onApply = { viewModel.applyModeChanges("Tone Curves") },
                            onReset = { viewModel.resetCurves() }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            CurveControlsBar(
                                activeChannel = activeCurveChannel,
                                onSelectChannel = { activeCurveChannel = it },
                                onResetCurve = { viewModel.resetCurve(activeCurveChannel) },
                                onDone = { viewModel.applyModeChanges("Tone Curves") }
                            )
                        }
                    }
                    EditorMode.COLOR_MIX -> {
                        EditorModeHeader(
                            title = "Color Mix (HSL)",
                            onCancel = { viewModel.cancelModeChanges() },
                            onApply = { viewModel.applyModeChanges("Color Mix") },
                            onReset = { viewModel.resetColorMix() }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            val currentHsl = sessionState.adjustments.hslAdjustments[selectedColorMixBand] ?: HslBandAdjustment()
                            ColorMixPanel(
                                selectedBand = selectedColorMixBand,
                                onSelectBand = { selectedColorMixBand = it },
                                hslAdjustment = currentHsl,
                                onHueChange = {
                                    viewModel.setHslAdjustment(selectedColorMixBand, currentHsl.copy(hueShift = it))
                                },
                                onSatChange = {
                                    viewModel.setHslAdjustment(selectedColorMixBand, currentHsl.copy(satShift = it))
                                },
                                onLumChange = {
                                    viewModel.setHslAdjustment(selectedColorMixBand, currentHsl.copy(lumShift = it))
                                },
                                onResetBand = {
                                    viewModel.resetColorBand(selectedColorMixBand)
                                }
                            )
                        }
                    }
                    EditorMode.COLOR_GRADING -> {
                        EditorModeHeader(
                            title = "Color Grading",
                            onCancel = { viewModel.cancelModeChanges() },
                            onApply = { viewModel.applyModeChanges("Color Grading") },
                            onReset = {
                                viewModel.setSplitToning(SplitToningParams())
                            }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            val split = sessionState.adjustments.splitToning
                            ColorGradingPanel(
                                splitToning = split,
                                onShadowHueChange = { viewModel.setSplitToning(split.copy(shadowHue = it)) },
                                onShadowSatChange = { viewModel.setSplitToning(split.copy(shadowSat = it)) },
                                onHighlightHueChange = { viewModel.setSplitToning(split.copy(highlightHue = it)) },
                                onHighlightSatChange = { viewModel.setSplitToning(split.copy(highlightSat = it)) },
                                onBalanceChange = { viewModel.setSplitToning(split.copy(balance = it)) },
                                onReset = { viewModel.setSplitToning(SplitToningParams()) }
                            )
                        }
                    }
                    else -> {}
                }
            } else {
                // Tier 1 Active Panel (Adjust, Presets, Crop, Masking, Healing)
                when (currentBottomTab) {
                    MainBottomTab.ADJUST -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (activeAdjustCategory) {
                                AdjustCategory.LIGHT -> {
                                    val adj = sessionState.adjustments
                                    LightPanel(
                                        exposure = adj.exposure,
                                        contrast = adj.contrast,
                                        highlights = adj.highlights,
                                        shadows = adj.shadows,
                                        whites = adj.whites,
                                        blacks = adj.blacks,
                                        onOpenCurve = { viewModel.enterMode(EditorMode.CURVES) },
                                        onSliderDragStart = { name, formatted ->
                                            val initVal = when (name) {
                                                "Exposure" -> adj.exposure
                                                "Contrast" -> adj.contrast
                                                "Highlights" -> adj.highlights
                                                "Shadows" -> adj.shadows
                                                "Whites" -> adj.whites
                                                else -> adj.blacks
                                            }
                                            startSliderDrag(name, formatted, initVal)
                                        },
                                        onSliderChange = { prop, val_ ->
                                            viewModel.updateAdjustmentValue(prop, val_)
                                        },
                                        onSliderChangeFinished = { finishSliderDrag() }
                                    )
                                }
                                AdjustCategory.COLOR -> {
                                    val adj = sessionState.adjustments
                                    ColorPanel(
                                        isBlackAndWhite = adj.isBlackAndWhite,
                                        whiteBalancePreset = adj.whiteBalancePreset,
                                        temperature = adj.temperature,
                                        tint = adj.tint,
                                        vibrance = adj.vibrance,
                                        saturation = adj.saturation,
                                        onToggleBw = { viewModel.toggleBlackAndWhite() },
                                        onOpenGrading = { viewModel.enterMode(EditorMode.COLOR_GRADING) },
                                        onOpenMix = { viewModel.enterMode(EditorMode.COLOR_MIX) },
                                        onSelectWbPreset = { viewModel.setWhiteBalancePreset(it) },
                                        onSliderDragStart = { name, formatted ->
                                            val initVal = when (name) {
                                                "Temp" -> adj.temperature
                                                "Tint" -> adj.tint
                                                "Vibrance" -> adj.vibrance
                                                else -> adj.saturation
                                            }
                                            startSliderDrag(name, formatted, initVal)
                                        },
                                        onSliderChange = { prop, val_ ->
                                            viewModel.updateAdjustmentValue(prop, val_)
                                        },
                                        onSliderChangeFinished = { finishSliderDrag() }
                                    )
                                }
                                AdjustCategory.EFFECTS -> {
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
                                            val initVal = when (name) {
                                                "Texture" -> adj.texture
                                                "Clarity" -> adj.clarity
                                                "Dehaze" -> adj.dehaze
                                                "Vignette" -> adj.vignette
                                                "Grain" -> adj.grain
                                                "Dodge" -> adj.dodge
                                                "Burn" -> adj.burn
                                                "Vignette Midpoint" -> adj.vignetteMidpoint
                                                "Vignette Feather" -> adj.vignetteFeather
                                                "Vignette Roundness" -> adj.vignetteRoundness
                                                "Grain Size" -> adj.grainSize
                                                "Grain Roughness" -> adj.grainRoughness
                                                else -> 0f
                                            }
                                            startSliderDrag(name, formatted, initVal)
                                        },
                                        onSliderChange = { prop, val_ ->
                                            viewModel.updateAdjustmentValue(prop, val_)
                                        },
                                        onSliderChangeFinished = { finishSliderDrag() }
                                    )
                                }
                                AdjustCategory.DETAIL -> {
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
                                            startSliderDrag(name, formatted, initVal)
                                        },
                                        onSliderChange = { prop, val_ ->
                                            viewModel.updateAdjustmentValue(prop, val_)
                                        },
                                        onSliderChangeFinished = { finishSliderDrag() }
                                    )
                                }
                                AdjustCategory.OPTICS -> {
                                    val adj = sessionState.adjustments
                                    OpticsPanel(
                                        removeChromaticAberration = adj.removeChromaticAberration,
                                        enableLensCorrections = adj.enableLensCorrections,
                                        distortionCorrection = adj.distortionCorrection,
                                        vignettingCorrection = adj.vignettingCorrection,
                                        onToggleChromaticAberration = { viewModel.setRemoveChromaticAberration(it) },
                                        onToggleLensCorrections = { viewModel.setEnableLensCorrections(it) },
                                        onSliderDragStart = { name, formatted ->
                                            val initVal = if (name == "Distortion") adj.distortionCorrection else adj.vignettingCorrection
                                            startSliderDrag(name, formatted, initVal)
                                        },
                                        onSliderChange = { prop, val_ ->
                                            viewModel.updateAdjustmentValue(prop, val_)
                                        },
                                        onSliderChangeFinished = { finishSliderDrag() }
                                    )
                                }
                                AdjustCategory.BLUR -> {
                                    val adj = sessionState.adjustments
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF141414))
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
                                            onDragStart = { startSliderDrag("Blur", blurStr, adj.blur) },
                                            onValueChange = { viewModel.updateAdjustmentValue("Blur", it) },
                                            onValueChangeFinished = { finishSliderDrag() }
                                        )
                                    }
                                }
                                AdjustCategory.LEVELS -> {
                                    val levels = sessionState.adjustments.levels
                                    LevelsPanel(
                                        levels = levels,
                                        onLevelsChange = { viewModel.setLevels(it) },
                                        onReset = { viewModel.setLevels(LevelsParams()) }
                                    )
                                }
                                AdjustCategory.COLOR_BAL -> {
                                    val bal = sessionState.adjustments.colorBalance
                                    ColorBalancePanel(
                                        balance = bal,
                                        onBalanceChange = { viewModel.setColorBalance(it) },
                                        onReset = { viewModel.setColorBalance(ColorBalanceParams()) }
                                    )
                                }
                                AdjustCategory.COLOR_FX -> {
                                    ColorFxPanel(
                                        adj = sessionState.adjustments,
                                        onGradientMapChange = { viewModel.setGradientMap(it) },
                                        onDuotoneChange = { viewModel.setDuotone(it) },
                                        onColorReplaceChange = { viewModel.setColorReplace(it) },
                                        onColorSplashChange = { viewModel.setColorSplash(it) }
                                    )
                                }
                                AdjustCategory.PROFILES -> {
                                    ProfilesPanel(
                                        selectedCategory = selectedProfileCategory,
                                        onSelectCategory = { selectedProfileCategory = it },
                                        onApplyProfile = { viewModel.applyProfile(it) },
                                        thumbnailBitmap = thumbnailBitmap,
                                        onClose = { activeAdjustCategory = AdjustCategory.LIGHT },
                                        onConfirm = { activeAdjustCategory = AdjustCategory.LIGHT }
                                    )
                                }
                                else -> {}
                            }
                        }

                        // Category Strip under Adjust (docked above bottom dock)
                        AdjustCategoryStrip(
                            activeCategory = activeAdjustCategory,
                            onSelectCategory = { cat ->
                                activeAdjustCategory = cat
                            }
                        )
                    }
                    MainBottomTab.PRESETS -> {
                        // Presets Panel: Tier 2 (Live preview cards) + Tier 3 (Category pills)
                        ProfilesPanel(
                            selectedCategory = selectedProfileCategory,
                            onSelectCategory = { selectedProfileCategory = it },
                            onApplyProfile = { viewModel.applyProfile(it) },
                            thumbnailBitmap = thumbnailBitmap,
                            onPresetAdjust = { contrast, brightness, fade, blend ->
                                viewModel.updateAdjustmentValue("Contrast", contrast - 50f)
                                viewModel.updateAdjustmentValue("Brightness", brightness - 50f)
                                viewModel.updatePresetIntensity((100f - fade) / 100f)
                                viewModel.updatePresetBlendMode(blend)
                            }
                        )
                    }
                    MainBottomTab.CROP -> {
                        CropExtendedPanel(
                            transform = sessionState.transform,
                            selectedCropPreset = selectedCropPreset,
                            onSelectCropPreset = { selectedCropPreset = it },
                            onRotate90 = { viewModel.rotate90() },
                            onFlipH = { viewModel.flipHorizontal() },
                            onFlipV = { viewModel.flipVertical() },
                            onStraightenChange = { viewModel.setStraighten(it) },
                            onStraightenFinished = { viewModel.commitSliderChange() },
                            onAutoLevel = { /* auto-level disabled */ },
                            onShapeCropChange = { viewModel.setShapeCrop(it) },
                            onPerspectiveChange = { x, y -> viewModel.setPerspective(x, y) },
                            onCornerPinReset = { viewModel.resetCrop() },
                            onCornerPinEnable = { /* corner pin mode */ },
                            onLiquifyModeChange = { currentLiquifyMode = it },
                            currentLiquifyMode = currentLiquifyMode,
                            liquifyRadius = liquifyRadius,
                            onLiquifyRadiusChange = { liquifyRadius = it },
                            liquifyStrength = liquifyStrength,
                            onLiquifyStrengthChange = { liquifyStrength = it },
                            onClearLiquify = { viewModel.clearLiquify() },
                            onDispersionChange = { viewModel.setDispersion(it) },
                            onMotionChange = { amt, ang -> viewModel.setMotionBlur(amt, ang) },
                            onScaleFactorChange = { viewModel.setScaleFactor(it) },
                            onResetCrop = { viewModel.resetCrop() }
                        )
                    }
                    MainBottomTab.MASKING -> {
                        MaskingExtendedPanel(
                            selectedType = maskingSelectionType,
                            onSelectType = { maskingSelectionType = it },
                            selectedBrush = selectedBrushType,
                            onSelectBrush = { selectedBrushType = it },
                            brushColor = selectedBrushColor,
                            onColorSelect = { selectedBrushColor = it },
                            brushWidth = brushWidth,
                            onWidthChange = { brushWidth = it },
                            brushOpacity = brushOpacity,
                            onOpacityChange = { brushOpacity = it },
                            brushFlow = brushFlow,
                            onFlowChange = { brushFlow = it },
                            magicWandTolerance = magicWandTolerance,
                            onMagicWandToleranceChange = { magicWandTolerance = it },
                            isSelectionActive = sessionState.drawingStrokes.isNotEmpty() || sessionState.maskLayers.isNotEmpty(),
                            onInvertMask = { viewModel.invertMask() },
                            onClearMask = { viewModel.clearMask() },

                            maskLayers = sessionState.maskLayers,
                            activeMaskId = sessionState.activeMaskId,
                            activeComponentId = sessionState.activeComponentId,
                            overlaySettings = sessionState.maskOverlaySettings,
                            onCreateMaskLayer = { viewModel.createMaskLayer(it) },
                            onSelectMaskLayer = { viewModel.selectMaskLayer(it) },
                            onDeleteMaskLayer = { viewModel.deleteMaskLayer(it) },
                            onDuplicateMaskLayer = { viewModel.duplicateMaskLayer(it) },
                            onToggleMaskEnabled = { viewModel.toggleMaskLayerEnabled(it) },
                            onInvertMaskLayer = { viewModel.invertMaskLayer(it) },
                            onSetMaskAmount = { id, amt -> viewModel.setMaskAmount(id, amt) },
                            onAddComponent = { maskId, type, mode -> viewModel.addComponentToMask(maskId, type, mode) },
                            onRemoveComponent = { maskId, compId -> viewModel.removeComponentFromMask(maskId, compId) },
                            onSelectComponent = { maskId, compId -> viewModel.selectMaskComponent(maskId, compId) },
                            onUpdateComponent = { maskId, comp -> viewModel.updateMaskComponent(maskId, comp) },
                            onUpdateAdjustment = { maskId, prop, val_ -> viewModel.updateMaskLocalAdjustment(maskId, prop, val_) },
                            onSetColorTint = { maskId, col -> viewModel.setMaskColorTint(maskId, col) },
                            onResetAdjustments = { maskId -> viewModel.resetMaskAdjustments(maskId) },
                            onToggleOverlay = { viewModel.toggleMaskOverlay() },
                            onSetOverlayColor = { viewModel.setMaskOverlayColor(it) },
                            onSetOverlayOpacity = { viewModel.setMaskOverlayOpacity(it) }
                        )
                    }
                    MainBottomTab.HEALING -> {
                        HealingExtendedPanel(
                            activeType = activeRetouchType,
                            onSelectType = { activeRetouchType = it },
                            brushRadius = brushWidth * 1.5f,
                            onRadiusChange = { brushWidth = it / 1.5f },
                            intensity = healingIntensity,
                            onIntensityChange = { healingIntensity = it },
                            tintColor = healingTintColor,
                            onTintColorChange = { healingTintColor = it },
                            strokeCount = sessionState.retouchStrokes.size,
                            onClearStrokes = { viewModel.clearRetouchStrokes() }
                        )
                    }
                    MainBottomTab.OVERLAYS -> {
                        OverlaysWorkspacePanel(
                            sessionState = sessionState,
                            onFrameChange = { viewModel.setFrame(it) },
                            onBorderChange = { viewModel.setBorder(it) },
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
                    MainBottomTab.AI_TOOLS -> {
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF000000)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLandscape) {
                // ==========================================
                // LANDSCAPE-SPECIFIC TWO-COLUMN LAYOUT
                // ==========================================
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Side: Maximized Canvas Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        PhotoEditorCanvas(
                            bitmap = previewBitmap,
                            originalBitmap = originalBitmap,
                            activeTool = activeTool,
                            brushType = selectedBrushType,
                            brushColor = selectedBrushColor,
                            brushWidth = brushWidth,
                            activeRetouchType = activeRetouchType,
                            retouchIntensity = healingIntensity,
                            retouchTintColor = healingTintColor,
                            cropAspectRatio = if (currentBottomTab == MainBottomTab.CROP) selectedCropPreset.ratio else null,
                            isCurveMode = currentMode == EditorMode.CURVES,
                            activeCurveChannel = activeCurveChannel,
                            curvePoints = when (activeCurveChannel) {
                                CurveChannel.RGB -> sessionState.adjustments.curves.master
                                CurveChannel.RED -> sessionState.adjustments.curves.red
                                CurveChannel.GREEN -> sessionState.adjustments.curves.green
                                CurveChannel.BLUE -> sessionState.adjustments.curves.blue
                            },
                            onCurvePointChanged = { channel, idx, inVal, outVal ->
                                viewModel.updateCurvePoint(channel, idx, inVal, outVal)
                            },
                            onCurvePointAdded = { channel, inVal, outVal ->
                                viewModel.addCurvePoint(channel, inVal, outVal)
                            },
                            onDrawingStrokeFinished = { stroke ->
                                if (currentBottomTab == MainBottomTab.MASKING) {
                                    viewModel.addStrokeToActiveMask(stroke)
                                } else {
                                    viewModel.addDrawingStroke(stroke)
                                }
                            },
                            onRetouchStrokeFinished = { viewModel.addRetouchStroke(it) },
                            onCropRectPreview = { viewModel.updateCropPreview(it) },
                            onCropRectChanged = { viewModel.commitCrop(it) },
                            maskOverlaySettings = sessionState.maskOverlaySettings,
                            activeMaskLayer = sessionState.maskLayers.firstOrNull { it.id == sessionState.activeMaskId }
                        )

                        // Top-Left floating overlay action bar in landscape
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xCC161616),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                            ) {
                                IconButton(
                                    onClick = {
                                        if (currentMode != EditorMode.NONE) {
                                            viewModel.cancelModeChanges()
                                        } else if (canUndo) {
                                            showDiscardDialog = true
                                        } else {
                                            onBack()
                                        }
                                    },
                                    modifier = Modifier.size(40.dp).testTag("editor_close_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color(0xCC161616),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                                    IconButton(
                                        onClick = { viewModel.undo() },
                                        enabled = canUndo && !isReplaying,
                                        modifier = Modifier.size(40.dp).testTag("editor_undo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = "Undo",
                                            tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.28f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.redo() },
                                        enabled = canRedo && !isReplaying,
                                        modifier = Modifier.size(40.dp).testTag("editor_redo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Redo,
                                            contentDescription = "Redo",
                                            tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.28f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Floating HUD Slider Banner at Top
                        if (activeDraggingSliderName != null) {
                            SliderHudBanner(
                                sliderName = activeDraggingSliderName!!,
                                sliderValue = activeDraggingSliderValue,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp)
                            )
                        }

                        // Replay progress indicator
                        if (isReplaying) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp),
                                shape = CircleShape,
                                color = Color(0xDD181818),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        progress = { replayProgress },
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Replaying edit history...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }

                    // Right Side: Dedicated Inspector Pane
                    Surface(
                        modifier = Modifier
                            .widthIn(min = 340.dp, max = 420.dp)
                            .fillMaxHeight(),
                        color = Color(0xF4141414),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        ) {
                            // Top action icons in landscape sidebar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (currentMode != EditorMode.NONE) currentMode.displayName else "Develop",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = sharePhoto,
                                        modifier = Modifier.size(36.dp).testTag("editor_share_button")
                                    ) {
                                        Icon(Icons.Default.Share, "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    Surface(
                                        onClick = { doSave(false, 95) },
                                        shape = CircleShape,
                                        color = Color.White,
                                        contentColor = Color.Black,
                                        modifier = Modifier.size(36.dp).testTag("editor_save_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Check, "Save", tint = Color.Black, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            // Scrollable Tool Inspector Pane
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                PanelContent(modifier = Modifier.fillMaxSize())
                            }

                            // Bottom Mode Selectors in Landscape
                            MainBottomTierBar(
                                currentTab = currentBottomTab,
                                onSelectTab = { tab ->
                                    currentBottomTab = tab
                                    when (tab) {
                                        MainBottomTab.ADJUST -> viewModel.setActiveTool(EditorTool.ADJUST)
                                        MainBottomTab.PRESETS -> viewModel.setActiveTool(EditorTool.EFFECTS)
                                        MainBottomTab.CROP -> viewModel.setActiveTool(EditorTool.CROP)
                                        MainBottomTab.MASKING -> viewModel.setActiveTool(EditorTool.BRUSHES)
                                        MainBottomTab.HEALING -> viewModel.setActiveTool(EditorTool.RETOUCH)
                                        MainBottomTab.OVERLAYS -> viewModel.setActiveTool(EditorTool.TEXT)
                                        MainBottomTab.AI_TOOLS -> viewModel.setActiveTool(EditorTool.AI_TOOLS)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // ==========================================
                // PORTRAIT LAYOUT
                // ==========================================
                Column(modifier = Modifier.fillMaxSize()) {
                    // Minimal Top Bar (No unnecessary headers, monochrome icons)
                    PhotoEditorTopBar(
                        canUndo = canUndo && !isReplaying,
                        canRedo = canRedo && !isReplaying,
                        onClose = {
                            if (currentMode != EditorMode.NONE) {
                                viewModel.cancelModeChanges()
                            } else if (canUndo) {
                                showDiscardDialog = true
                            } else {
                                onBack()
                            }
                        },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onQuickSave = { doSave(false, 95) },
                        onShare = sharePhoto,
                        onRevert = {
                            viewModel.revertToOriginal()
                            Toast.makeText(context, "Reverted to original", Toast.LENGTH_SHORT).show()
                        },
                        onCopySettings = {
                            viewModel.copySettings()
                            Toast.makeText(context, "Settings copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onPasteSettings = {
                            if (viewModel.pasteSettings()) {
                                Toast.makeText(context, "Settings applied", Toast.LENGTH_SHORT).show()
                            }
                        },
                        canPasteSettings = viewModel.hasCopiedSettings(),
                        onContinueExport = { showExportSheet = true },
                        onEditWithAi = { showAiSheet = true },
                        onStartReplay = { viewModel.startReplay() },
                        onOpenHistory = { showHistoryDialog = true }
                    )

                    // Main Photo Canvas with Professional multi-touch gestures
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        PhotoEditorCanvas(
                            bitmap = previewBitmap,
                            originalBitmap = originalBitmap,
                            activeTool = activeTool,
                            brushType = selectedBrushType,
                            brushColor = selectedBrushColor,
                            brushWidth = brushWidth,
                            activeRetouchType = activeRetouchType,
                            retouchIntensity = healingIntensity,
                            retouchTintColor = healingTintColor,
                            cropAspectRatio = if (currentBottomTab == MainBottomTab.CROP) selectedCropPreset.ratio else null,
                            isCurveMode = currentMode == EditorMode.CURVES,
                            activeCurveChannel = activeCurveChannel,
                            curvePoints = when (activeCurveChannel) {
                                CurveChannel.RGB -> sessionState.adjustments.curves.master
                                CurveChannel.RED -> sessionState.adjustments.curves.red
                                CurveChannel.GREEN -> sessionState.adjustments.curves.green
                                CurveChannel.BLUE -> sessionState.adjustments.curves.blue
                            },
                            onCurvePointChanged = { channel, idx, inVal, outVal ->
                                viewModel.updateCurvePoint(channel, idx, inVal, outVal)
                            },
                            onCurvePointAdded = { channel, inVal, outVal ->
                                viewModel.addCurvePoint(channel, inVal, outVal)
                            },
                            onDrawingStrokeFinished = { stroke ->
                                if (currentBottomTab == MainBottomTab.MASKING) {
                                    viewModel.addStrokeToActiveMask(stroke)
                                } else {
                                    viewModel.addDrawingStroke(stroke)
                                }
                            },
                            onRetouchStrokeFinished = { viewModel.addRetouchStroke(it) },
                            onCropRectPreview = { viewModel.updateCropPreview(it) },
                            onCropRectChanged = { viewModel.commitCrop(it) },
                            maskOverlaySettings = sessionState.maskOverlaySettings,
                            activeMaskLayer = sessionState.maskLayers.firstOrNull { it.id == sessionState.activeMaskId }
                        )

                        // Floating Slider HUD Banner at Top
                        if (activeDraggingSliderName != null) {
                            SliderHudBanner(
                                sliderName = activeDraggingSliderName!!,
                                sliderValue = activeDraggingSliderValue,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp)
                            )
                        }

                        // Replay Progress Indicator Banner
                        if (isReplaying) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp),
                                shape = CircleShape,
                                color = Color(0xDD181818),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        progress = { replayProgress },
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Replaying edit history...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }

                    // Contextual Tool Panels Container (Content-sized sheet with maximum height)
                    val configuration = LocalConfiguration.current
                    val maxSheetHeight = if (currentBottomTab == MainBottomTab.PRESETS) {
                        (configuration.screenHeightDp * 0.50f).dp.coerceIn(280.dp, 400.dp)
                    } else {
                        (configuration.screenHeightDp * 0.44f).dp.coerceIn(240.dp, 360.dp)
                    }

                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = Color(0xF7141414),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxSheetHeight)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PanelContent(modifier = Modifier.weight(1f, fill = false))

                            // Clean Floating Navigation Dock
                            MainBottomTierBar(
                                currentTab = currentBottomTab,
                                onSelectTab = { tab ->
                                    currentBottomTab = tab
                                    when (tab) {
                                        MainBottomTab.ADJUST -> viewModel.setActiveTool(EditorTool.ADJUST)
                                        MainBottomTab.PRESETS -> viewModel.setActiveTool(EditorTool.EFFECTS)
                                        MainBottomTab.CROP -> viewModel.setActiveTool(EditorTool.CROP)
                                        MainBottomTab.MASKING -> viewModel.setActiveTool(EditorTool.BRUSHES)
                                        MainBottomTab.HEALING -> viewModel.setActiveTool(EditorTool.RETOUCH)
                                        MainBottomTab.OVERLAYS -> viewModel.setActiveTool(EditorTool.TEXT)
                                        MainBottomTab.AI_TOOLS -> viewModel.setActiveTool(EditorTool.AI_TOOLS)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Processing overlay dialog
            if (isProcessing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1E1E1E),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            tonalElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = processingMessage.ifEmpty { "Processing..." },
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Discard Confirmation Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to discard your photo edits?", color = Color(0xFFCCCCCC)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    }
                ) {
                    Text("Discard", color = Color(0xFFCF6679), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Export Sheet
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            containerColor = Color(0xFF181818),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text("Export Photo", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        showExportSheet = false
                        doSave(false, 95)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Save high-quality JPEG (95%)", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        showExportSheet = false
                        doSave(true, 100)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text("Save lossless PNG", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // AI Magic Sheet
    if (showAiSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAiSheet = false },
            containerColor = Color(0xFF181818),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text("AI Magic Tools", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.setAiExpand(0.25f)
                        showAiSheet = false
                        Toast.makeText(context, "Generative Expand Preview enabled", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text("Generative Outpainting / Expand", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showHistoryDialog) {
        EditHistoryDialog(
            historyList = viewModel.getHistoryList(),
            currentStepIndex = viewModel.getCurrentHistoryIndex(),
            onJumpToStep = { viewModel.jumpToHistoryStep(it) },
            onStartReplay = { viewModel.startReplay() },
            onDismiss = { showHistoryDialog = false }
        )
    }
}

private fun formatSliderValue(prop: String, value: Float): String {
    return when (prop) {
        "Exposure" -> {
            val ev = value / 20f
            val sign = if (ev > 0.001f) "+" else if (ev < -0.001f) "−" else ""
            val formatted = String.format(Locale.US, "%.2f", kotlin.math.abs(ev))
            "$sign$formatted EV"
        }
        "Temp" -> {
            val rounded = value.toInt()
            val kelvin = (5500 + (value * 40f)).toInt().coerceIn(2000, 9500)
            val sign = if (rounded > 0) "+$rounded" else if (rounded < 0) "−${kotlin.math.abs(rounded)}" else "0"
            "$sign (${kelvin}K)"
        }
        "Radius" -> {
            String.format(Locale.US, "%.2f px", value)
        }
        "Straighten" -> {
            val sign = if (value > 0.05f) "+" else if (value < -0.05f) "−" else ""
            val formatted = String.format(Locale.US, "%.1f°", kotlin.math.abs(value))
            "$sign$formatted"
        }
        else -> {
            val rounded = value.toInt()
            if (rounded > 0) "+$rounded"
            else if (rounded < 0) "−${kotlin.math.abs(rounded)}"
            else "0"
        }
    }
}
