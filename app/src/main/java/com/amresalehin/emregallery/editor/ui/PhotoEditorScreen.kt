package com.amresalehin.emregallery.editor.ui

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
 * Gallery — Editor Screen (Emre Design System)
 *
 * 5 Principal Zones:
 * 01. Top Toolbar (Persistent): Back · Versions · Save Options
 * 02. Main Workspace: Image / Video Canvas (Live rendering, preview, transformations)
 * 03. Operation Toolbar: Cancel · Undo · Redo · Reset · Apply
 * 04. Contextual Editing Panel: Active Domain → Tools → Subtools → Parameters → Presets
 * 05. Bottom Navigation: Docked primary access layer across core editing areas
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
    val colors = rememberEmreEditorColors()

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

    // 05. Bottom Navigation Domain State
    var selectedDomain by remember { mutableStateOf(EditorDomain.ADJUST) }

    // 04. Contextual Subtool States
    var activeAdjustSubtool by remember { mutableStateOf(AdjustSubtool.LIGHT) }
    var activeCropSubtool by remember { mutableStateOf(CropSubtool.ASPECT) }
    var activeRetouchSubtool by remember { mutableStateOf(RetouchSubtool.BLEMISH) }
    var activeMaskSubtool by remember { mutableStateOf(MaskSubtool.BRUSH) }
    var activeOverlaysSubtool by remember { mutableStateOf(OverlaysSubtool.TEXT) }
    var activeAiSubtool by remember { mutableStateOf(AiSubtool.EXPAND) }

    // Precision sub-mode channel & band states
    var activeCurveChannel by remember { mutableStateOf(CurveChannel.RGB) }
    var selectedProfileCategory by remember { mutableStateOf("ALL") }
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
    var brushHardness by remember { mutableFloatStateOf(1.0f) }
    var brushFlow by remember { mutableFloatStateOf(1.0f) }
    var isEyedropperActive by remember { mutableStateOf(false) }
    var maskingSelectionType by remember { mutableStateOf(MaskingSelectionType.BRUSH) }

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
    var showLrMaskCreationSheet by remember { mutableStateOf(false) }

    // Back Handler: Respects history dialog, active mode Cancel first, then tool domain, then discard confirmation
    BackHandler {
        when {
            showHistoryDialog -> showHistoryDialog = false
            currentMode != EditorMode.NONE -> viewModel.cancelModeChanges()
            showAiSheet -> showAiSheet = false
            showExportSheet -> showExportSheet = false
            selectedDomain != EditorDomain.ADJUST -> {
                selectedDomain = EditorDomain.ADJUST
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

    // 03. Operation Toolbar Handlers
    val handleCancel: () -> Unit = {
        if (currentMode != EditorMode.NONE) {
            viewModel.cancelModeChanges()
            Toast.makeText(context, "Cancelled ${currentMode.displayName}", Toast.LENGTH_SHORT).show()
        } else if (canUndo) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    val handleReset: () -> Unit = {
        when {
            currentMode == EditorMode.CURVES -> viewModel.resetCurves()
            currentMode == EditorMode.COLOR_MIX -> viewModel.resetColorMix()
            currentMode == EditorMode.COLOR_GRADING -> viewModel.setSplitToning(SplitToningParams())
            selectedDomain == EditorDomain.ADJUST -> {
                when (activeAdjustSubtool) {
                    AdjustSubtool.LIGHT -> {
                        viewModel.updateAdjustmentValue("Exposure", 0f)
                        viewModel.updateAdjustmentValue("Contrast", 0f)
                        viewModel.updateAdjustmentValue("Highlights", 0f)
                        viewModel.updateAdjustmentValue("Shadows", 0f)
                        viewModel.updateAdjustmentValue("Whites", 0f)
                        viewModel.updateAdjustmentValue("Blacks", 0f)
                    }
                    AdjustSubtool.COLOR -> {
                        viewModel.updateAdjustmentValue("Temp", 0f)
                        viewModel.updateAdjustmentValue("Tint", 0f)
                        viewModel.updateAdjustmentValue("Vibrance", 0f)
                        viewModel.updateAdjustmentValue("Saturation", 0f)
                    }
                    AdjustSubtool.DETAIL -> {
                        viewModel.updateAdjustmentValue("Sharpening", 0f)
                        viewModel.updateAdjustmentValue("Noise Reduction", 0f)
                    }
                    AdjustSubtool.EFFECTS -> {
                        viewModel.updateAdjustmentValue("Texture", 0f)
                        viewModel.updateAdjustmentValue("Clarity", 0f)
                        viewModel.updateAdjustmentValue("Dehaze", 0f)
                        viewModel.updateAdjustmentValue("Vignette", 0f)
                        viewModel.updateAdjustmentValue("Grain", 0f)
                    }
                    AdjustSubtool.BLUR -> viewModel.updateAdjustmentValue("Blur", 0f)
                    AdjustSubtool.LEVELS -> viewModel.setLevels(LevelsParams())
                    AdjustSubtool.BALANCE -> viewModel.setColorBalance(ColorBalanceParams())
                    AdjustSubtool.CURVES -> viewModel.resetCurves()
                    AdjustSubtool.OPTICS -> {
                        viewModel.setRemoveChromaticAberration(false)
                        viewModel.setEnableLensCorrections(false)
                        viewModel.updateAdjustmentValue("Distortion", 0f)
                        viewModel.updateAdjustmentValue("Vignetting", 0f)
                    }
                    AdjustSubtool.PROFILES -> viewModel.setFilter(FilterType.NONE)
                }
                viewModel.commitSliderChange()
            }
            selectedDomain == EditorDomain.CROP -> viewModel.resetCrop()
            selectedDomain == EditorDomain.PRESETS -> viewModel.setFilter(FilterType.NONE)
            selectedDomain == EditorDomain.RETOUCH -> viewModel.clearRetouchStrokes()
            selectedDomain == EditorDomain.MASK -> viewModel.clearMask()
            selectedDomain == EditorDomain.OVERLAYS -> viewModel.clearDrawingStrokes(sessionState.activeDrawLayerId)
            selectedDomain == EditorDomain.AI_TOOLS -> viewModel.setAiExpand(0f)
        }
        Toast.makeText(context, "Reset ${selectedDomain.label}", Toast.LENGTH_SHORT).show()
    }

    val handleApply: () -> Unit = {
        viewModel.commitSliderChange()
        if (currentMode != EditorMode.NONE) {
            viewModel.applyModeChanges(currentMode.displayName)
            Toast.makeText(context, "${currentMode.displayName} applied", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "${selectedDomain.label} applied", Toast.LENGTH_SHORT).show()
        }
    }

    CompositionLocalProvider(LocalEmreEditorColors provides colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.baseSurface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLandscape) {
                    // ==========================================
                    // LANDSCAPE-SPECIFIC TWO-COLUMN COMPOSITION
                    // ==========================================
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 01. Top Toolbar (Persistent)
                        EditorTopToolbar(
                            onBack = handleCancel,
                            onOpenVersions = { showHistoryDialog = true },
                            onQuickSave = { doSave(false, 95) },
                            onSaveCopy = { doSave(false, 95) },
                            onExportOptions = { showExportSheet = true },
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
                            versionCount = viewModel.getHistoryList().size,
                            colors = colors
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            // Left Column: 02. Main Workspace + 03. Operation Toolbar
                            Column(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .fillMaxHeight()
                            ) {
                                EditorWorkspace(
                                    previewBitmap = previewBitmap,
                                    originalBitmap = originalBitmap,
                                    activeTool = activeTool,
                                    selectedBrushType = selectedBrushType,
                                    selectedBrushColor = selectedBrushColor,
                                    brushWidth = brushWidth,
                                    brushOpacity = brushOpacity,
                                    brushHardness = brushHardness,
                                    activeRetouchType = activeRetouchType,
                                    healingIntensity = healingIntensity,
                                    healingTintColor = healingTintColor,
                                    cropAspectRatio = if (selectedDomain == EditorDomain.CROP) selectedCropPreset.ratio else null,
                                    isCurveMode = currentMode == EditorMode.CURVES,
                                    activeCurveChannel = activeCurveChannel,
                                    sessionState = sessionState,
                                    viewModel = viewModel,
                                    activeDraggingSliderName = activeDraggingSliderName,
                                    activeDraggingSliderValue = activeDraggingSliderValue,
                                    isReplaying = isReplaying,
                                    replayProgress = replayProgress,
                                    isEyedropperActive = isEyedropperActive,
                                    onColorPicked = {
                                        selectedBrushColor = it
                                        isEyedropperActive = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    colors = colors
                                )

                                EditorOperationToolbar(
                                    canUndo = canUndo && !isReplaying,
                                    canRedo = canRedo && !isReplaying,
                                    onCancel = handleCancel,
                                    onUndo = { viewModel.undo() },
                                    onRedo = { viewModel.redo() },
                                    onReset = handleReset,
                                    onApply = handleApply,
                                    colors = colors
                                )
                            }

                            // Right Column: 04. Contextual Editing Panel + 05. Bottom Navigation
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                EditorContextualPanel(
                                    selectedDomain = selectedDomain,
                                    sessionState = sessionState,
                                    viewModel = viewModel,
                                    activeAdjustSubtool = activeAdjustSubtool,
                                    onSelectAdjustSubtool = { activeAdjustSubtool = it },
                                    activeCropSubtool = activeCropSubtool,
                                    onSelectCropSubtool = { activeCropSubtool = it },
                                    activeRetouchSubtool = activeRetouchSubtool,
                                    onSelectRetouchSubtool = { activeRetouchSubtool = it },
                                    activeMaskSubtool = activeMaskSubtool,
                                    onSelectMaskSubtool = { activeMaskSubtool = it },
                                    activeOverlaysSubtool = activeOverlaysSubtool,
                                    onSelectOverlaysSubtool = { activeOverlaysSubtool = it },
                                    activeAiSubtool = activeAiSubtool,
                                    onSelectAiSubtool = { activeAiSubtool = it },
                                    onSliderDragStart = ::startSliderDrag,
                                    onSliderDragFinished = ::finishSliderDrag,
                                    onOpenCurvesMode = { viewModel.enterMode(EditorMode.CURVES) },
                                    onOpenColorMixMode = { viewModel.enterMode(EditorMode.COLOR_MIX) },
                                    onOpenColorGradingMode = { viewModel.enterMode(EditorMode.COLOR_GRADING) },
                                    selectedProfileCategory = selectedProfileCategory,
                                    onSelectProfileCategory = { selectedProfileCategory = it },
                                    selectedCropPreset = selectedCropPreset,
                                    onSelectCropPreset = { selectedCropPreset = it },
                                    currentLiquifyMode = currentLiquifyMode,
                                    onLiquifyModeChange = { currentLiquifyMode = it },
                                    liquifyRadius = liquifyRadius,
                                    onLiquifyRadiusChange = { liquifyRadius = it },
                                    liquifyStrength = liquifyStrength,
                                    onLiquifyStrengthChange = { liquifyStrength = it },
                                    activeRetouchType = activeRetouchType,
                                    onSelectRetouchType = { activeRetouchType = it },
                                    healingIntensity = healingIntensity,
                                    onHealingIntensityChange = { healingIntensity = it },
                                    healingTintColor = healingTintColor,
                                    onHealingTintColorChange = { healingTintColor = it },
                                    brushWidth = brushWidth,
                                    onBrushWidthChange = { brushWidth = it },
                                    maskingSelectionType = maskingSelectionType,
                                    onSelectMaskingType = { maskingSelectionType = it },
                                    brushOpacity = brushOpacity,
                                    onBrushOpacityChange = { brushOpacity = it },
                                    brushFlow = brushFlow,
                                    onBrushFlowChange = { brushFlow = it },
                                    selectedBrushType = selectedBrushType,
                                    onSelectBrushType = { selectedBrushType = it },
                                    selectedBrushColor = selectedBrushColor,
                                    onSelectBrushColor = { selectedBrushColor = it },
                                    onOpenMaskCreationSheet = { showLrMaskCreationSheet = true },
                                    onFrameChange = { viewModel.setFrame(it) },
                                    onBorderChange = { viewModel.setBorder(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    colors = colors
                                )

                                EditorBottomNavigation(
                                    selectedDomain = selectedDomain,
                                    onSelectDomain = { domain ->
                                        selectedDomain = domain
                                        when (domain) {
                                            EditorDomain.ADJUST -> viewModel.setActiveTool(EditorTool.ADJUST)
                                            EditorDomain.CROP -> viewModel.setActiveTool(EditorTool.CROP)
                                            EditorDomain.PRESETS -> viewModel.setActiveTool(EditorTool.EFFECTS)
                                            EditorDomain.RETOUCH -> viewModel.setActiveTool(EditorTool.RETOUCH)
                                            EditorDomain.MASK -> viewModel.setActiveTool(EditorTool.BRUSHES)
                                            EditorDomain.OVERLAYS -> viewModel.setActiveTool(EditorTool.TEXT)
                                            EditorDomain.AI_TOOLS -> viewModel.setActiveTool(EditorTool.AI_TOOLS)
                                        }
                                    },
                                    colors = colors
                                )
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // PORTRAIT COMPOSITION (5 PRINCIPAL ZONES)
                    // ==========================================
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.baseSurface)
                    ) {
                        // 01. Top Toolbar (Persistent)
                        EditorTopToolbar(
                            onBack = handleCancel,
                            onOpenVersions = { showHistoryDialog = true },
                            onQuickSave = { doSave(false, 95) },
                            onSaveCopy = { doSave(false, 95) },
                            onExportOptions = { showExportSheet = true },
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
                            versionCount = viewModel.getHistoryList().size,
                            colors = colors
                        )

                        // 02. Main Workspace (Image / Video Canvas)
                        EditorWorkspace(
                            previewBitmap = previewBitmap,
                            originalBitmap = originalBitmap,
                            activeTool = activeTool,
                            selectedBrushType = selectedBrushType,
                            selectedBrushColor = selectedBrushColor,
                            brushWidth = brushWidth,
                            brushOpacity = brushOpacity,
                            brushHardness = brushHardness,
                            activeRetouchType = activeRetouchType,
                            healingIntensity = healingIntensity,
                            healingTintColor = healingTintColor,
                            cropAspectRatio = if (selectedDomain == EditorDomain.CROP) selectedCropPreset.ratio else null,
                            isCurveMode = currentMode == EditorMode.CURVES,
                            activeCurveChannel = activeCurveChannel,
                            sessionState = sessionState,
                            viewModel = viewModel,
                            activeDraggingSliderName = activeDraggingSliderName,
                            activeDraggingSliderValue = activeDraggingSliderValue,
                            isReplaying = isReplaying,
                            replayProgress = replayProgress,
                            isEyedropperActive = isEyedropperActive,
                            onColorPicked = {
                                selectedBrushColor = it
                                isEyedropperActive = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            colors = colors
                        )

                        // 03. Operation Toolbar (Cancel · Undo · Redo · Reset · Apply)
                        EditorOperationToolbar(
                            canUndo = canUndo && !isReplaying,
                            canRedo = canRedo && !isReplaying,
                            onCancel = handleCancel,
                            onUndo = { viewModel.undo() },
                            onRedo = { viewModel.redo() },
                            onReset = handleReset,
                            onApply = handleApply,
                            colors = colors
                        )

                        // 04. Contextual Editing Panel
                        val contextualPanelMaxHeight = (configuration.screenHeightDp * 0.40f).dp.coerceIn(220.dp, 340.dp)
                        EditorContextualPanel(
                            selectedDomain = selectedDomain,
                            sessionState = sessionState,
                            viewModel = viewModel,
                            activeAdjustSubtool = activeAdjustSubtool,
                            onSelectAdjustSubtool = { activeAdjustSubtool = it },
                            activeCropSubtool = activeCropSubtool,
                            onSelectCropSubtool = { activeCropSubtool = it },
                            activeRetouchSubtool = activeRetouchSubtool,
                            onSelectRetouchSubtool = { activeRetouchSubtool = it },
                            activeMaskSubtool = activeMaskSubtool,
                            onSelectMaskSubtool = { activeMaskSubtool = it },
                            activeOverlaysSubtool = activeOverlaysSubtool,
                            onSelectOverlaysSubtool = { activeOverlaysSubtool = it },
                            activeAiSubtool = activeAiSubtool,
                            onSelectAiSubtool = { activeAiSubtool = it },
                            onSliderDragStart = ::startSliderDrag,
                            onSliderDragFinished = ::finishSliderDrag,
                            onOpenCurvesMode = { viewModel.enterMode(EditorMode.CURVES) },
                            onOpenColorMixMode = { viewModel.enterMode(EditorMode.COLOR_MIX) },
                            onOpenColorGradingMode = { viewModel.enterMode(EditorMode.COLOR_GRADING) },
                            selectedProfileCategory = selectedProfileCategory,
                            onSelectProfileCategory = { selectedProfileCategory = it },
                            selectedCropPreset = selectedCropPreset,
                            onSelectCropPreset = { selectedCropPreset = it },
                            currentLiquifyMode = currentLiquifyMode,
                            onLiquifyModeChange = { currentLiquifyMode = it },
                            liquifyRadius = liquifyRadius,
                            onLiquifyRadiusChange = { liquifyRadius = it },
                            liquifyStrength = liquifyStrength,
                            onLiquifyStrengthChange = { liquifyStrength = it },
                            activeRetouchType = activeRetouchType,
                            onSelectRetouchType = { activeRetouchType = it },
                            healingIntensity = healingIntensity,
                            onHealingIntensityChange = { healingIntensity = it },
                            healingTintColor = healingTintColor,
                            onHealingTintColorChange = { healingTintColor = it },
                            brushWidth = brushWidth,
                            onBrushWidthChange = { brushWidth = it },
                            maskingSelectionType = maskingSelectionType,
                            onSelectMaskingType = { maskingSelectionType = it },
                            brushOpacity = brushOpacity,
                            onBrushOpacityChange = { brushOpacity = it },
                            brushFlow = brushFlow,
                            onBrushFlowChange = { brushFlow = it },
                            selectedBrushType = selectedBrushType,
                            onSelectBrushType = { selectedBrushType = it },
                            selectedBrushColor = selectedBrushColor,
                            onSelectBrushColor = { selectedBrushColor = it },
                            onOpenMaskCreationSheet = { showLrMaskCreationSheet = true },
                            onFrameChange = { viewModel.setFrame(it) },
                            onBorderChange = { viewModel.setBorder(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = contextualPanelMaxHeight),
                            colors = colors
                        )

                        // 05. Bottom Navigation (Docked primary access layer)
                        EditorBottomNavigation(
                            selectedDomain = selectedDomain,
                            onSelectDomain = { domain ->
                                selectedDomain = domain
                                when (domain) {
                                    EditorDomain.ADJUST -> viewModel.setActiveTool(EditorTool.ADJUST)
                                    EditorDomain.CROP -> viewModel.setActiveTool(EditorTool.CROP)
                                    EditorDomain.PRESETS -> viewModel.setActiveTool(EditorTool.EFFECTS)
                                    EditorDomain.RETOUCH -> viewModel.setActiveTool(EditorTool.RETOUCH)
                                    EditorDomain.MASK -> viewModel.setActiveTool(EditorTool.BRUSHES)
                                    EditorDomain.OVERLAYS -> viewModel.setActiveTool(EditorTool.TEXT)
                                    EditorDomain.AI_TOOLS -> viewModel.setActiveTool(EditorTool.AI_TOOLS)
                                }
                            },
                            colors = colors
                        )
                    }
                }

                // Processing Overlay
                if (isProcessing) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = colors.baseSurface.copy(alpha = 0.75f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = colors.elevatedSurface,
                                border = BorderStroke(1.dp, colors.border),
                                tonalElevation = 8.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = colors.primaryText,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = processingMessage.ifEmpty { "Processing..." },
                                        color = colors.primaryText,
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
                title = {
                    Text(
                        "Discard changes?",
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Are you sure you want to discard your photo edits?",
                        color = colors.secondaryText
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardDialog = false
                            onBack()
                        }
                    ) {
                        Text(
                            "Discard",
                            color = Color(0xFFCF6679),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text("Keep Editing", color = colors.primaryText)
                    }
                },
                containerColor = colors.elevatedSurface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Export Sheet
        if (showExportSheet) {
            ModalBottomSheet(
                onDismissRequest = { showExportSheet = false },
                containerColor = colors.elevatedSurface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        "Export Photo",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            showExportSheet = false
                            doSave(false, 95)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.activeControlBg,
                            contentColor = colors.activeControlFg
                        )
                    ) {
                        Text(
                            "Save high-quality JPEG (95%)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            showExportSheet = false
                            doSave(true, 100)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Text(
                            "Save lossless PNG",
                            color = colors.primaryText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // AI Magic Sheet
        if (showAiSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAiSheet = false },
                containerColor = colors.elevatedSurface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        "AI Magic Tools",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.setAiExpand(0.25f)
                            showAiSheet = false
                            Toast.makeText(context, "Generative Expand Preview enabled", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Text(
                            "Generative Outpainting / Expand",
                            color = colors.primaryText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // Versions / Edit History Dialog
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
}
