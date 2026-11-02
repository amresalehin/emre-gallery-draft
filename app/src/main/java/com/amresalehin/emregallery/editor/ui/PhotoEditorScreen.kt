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
    var selectedDomain by remember { mutableStateOf(EditorDomain.LIGHT) }

    // 04. Contextual Subtool States (for domains with sub-options)
    var activeMaskSubtool by remember { mutableStateOf(MaskSubtool.BRUSH) }
    var activeTextSubtool by remember { mutableStateOf(TextSubtool.ALL) }
    var activeShapeSubtool by remember { mutableStateOf(ShapeSubtool.SHAPES) }
    var activeCalloutSubtool by remember { mutableStateOf(CalloutSubtool.STYLES) }
    var activeFrameSubtool by remember { mutableStateOf(FrameSubtool.FRAMES) }
    var activeAiSubtool by remember { mutableStateOf(AiSubtool.EXPAND) }

    // Precision sub-mode channel state
    var activeCurveChannel by remember { mutableStateOf(CurveChannel.RGB) }
    var selectedProfileCategory by remember { mutableStateOf("ALL") }

    // Floating HUD slider state for interactive drag feedback
    var activeDraggingSliderName by remember { mutableStateOf<String?>(null) }
    var activeDraggingSliderValue by remember { mutableStateOf("") }

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

    // Independent retouch tool states
    var blemishRadius by remember { mutableFloatStateOf(24f) }
    var blemishIntensity by remember { mutableFloatStateOf(1.0f) }

    var smoothRadius by remember { mutableFloatStateOf(36f) }
    var smoothIntensity by remember { mutableFloatStateOf(0.6f) }

    var healRadius by remember { mutableFloatStateOf(30f) }
    var healIntensity by remember { mutableFloatStateOf(1.0f) }
    var healRetouchType by remember { mutableStateOf(RetouchType.PATCH_HEALING) }

    var skinToneRadius by remember { mutableFloatStateOf(40f) }
    var skinToneIntensity by remember { mutableFloatStateOf(0.5f) }
    var skinToneColor by remember { mutableStateOf(Color(0xFFFFCC80)) }

    val currentRetouchType = when (selectedDomain) {
        EditorDomain.BLEMISH -> RetouchType.BLEMISH
        EditorDomain.SMOOTH -> RetouchType.SKIN_SMOOTH
        EditorDomain.HEAL -> healRetouchType
        EditorDomain.SKIN_TONE -> RetouchType.SKIN_TONE
        else -> activeRetouchType
    }

    val currentRetouchRadius = when (selectedDomain) {
        EditorDomain.BLEMISH -> blemishRadius
        EditorDomain.SMOOTH -> smoothRadius
        EditorDomain.HEAL -> healRadius
        EditorDomain.SKIN_TONE -> skinToneRadius
        else -> brushWidth * 1.5f
    }

    val currentRetouchIntensity = when (selectedDomain) {
        EditorDomain.BLEMISH -> blemishIntensity
        EditorDomain.SMOOTH -> smoothIntensity
        EditorDomain.HEAL -> healIntensity
        EditorDomain.SKIN_TONE -> skinToneIntensity
        else -> healingIntensity
    }

    val currentRetouchTintColor = when (selectedDomain) {
        EditorDomain.SKIN_TONE -> skinToneColor
        else -> healingTintColor
    }

    var currentLiquifyMode by remember { mutableStateOf(LiquifyMode.PUSH) }
    var liquifyRadius by remember { mutableFloatStateOf(40f) }
    var liquifyStrength by remember { mutableFloatStateOf(0.5f) }

    // Dialogs & Sheets
    var showAiSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Back Handler: Respects history dialog, active mode Cancel first, then tool domain, then discard confirmation
    BackHandler {
        when {
            showHistoryDialog -> showHistoryDialog = false
            currentMode != EditorMode.NONE -> viewModel.cancelModeChanges()
            showAiSheet -> showAiSheet = false
            showExportSheet -> showExportSheet = false
            selectedDomain != EditorDomain.LIGHT -> {
                selectedDomain = EditorDomain.LIGHT
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
            selectedDomain == EditorDomain.LIGHT -> {
                viewModel.updateAdjustmentValue("Exposure", 0f)
                viewModel.updateAdjustmentValue("Contrast", 0f)
                viewModel.updateAdjustmentValue("Highlights", 0f)
                viewModel.updateAdjustmentValue("Shadows", 0f)
                viewModel.updateAdjustmentValue("Whites", 0f)
                viewModel.updateAdjustmentValue("Blacks", 0f)
                viewModel.commitSliderChange()
            }
            selectedDomain == EditorDomain.COLOR -> {
                viewModel.updateAdjustmentValue("Temp", 0f)
                viewModel.updateAdjustmentValue("Tint", 0f)
                viewModel.updateAdjustmentValue("Vibrance", 0f)
                viewModel.updateAdjustmentValue("Saturation", 0f)
                viewModel.setColorBalance(ColorBalanceParams())
                viewModel.setSplitToning(SplitToningParams())
                viewModel.setGradientMap(GradientMapType.NONE)
                viewModel.setDuotone(DuotoneParams())
                viewModel.setColorReplace(ColorReplaceParams())
                viewModel.setColorSplash(ColorSplashParams())
                viewModel.commitSliderChange()
            }
            selectedDomain == EditorDomain.CURVES -> {
                viewModel.resetCurves()
            }
            selectedDomain == EditorDomain.EFFECTS -> {
                viewModel.updateAdjustmentValue("Texture", 0f)
                viewModel.updateAdjustmentValue("Clarity", 0f)
                viewModel.updateAdjustmentValue("Dehaze", 0f)
                viewModel.updateAdjustmentValue("Grain", 0f)
                viewModel.updateAdjustmentValue("Dodge", 0f)
                viewModel.updateAdjustmentValue("Burn", 0f)
                viewModel.commitSliderChange()
            }
            selectedDomain == EditorDomain.DETAIL -> {
                viewModel.updateAdjustmentValue("Sharpening", 0f)
                viewModel.updateAdjustmentValue("Noise Reduction", 0f)
                viewModel.updateAdjustmentValue("Radius", 1.0f)
                viewModel.updateAdjustmentValue("Detail", 25f)
                viewModel.updateAdjustmentValue("Color Noise Reduction", 0f)
                viewModel.commitSliderChange()
            }
            selectedDomain == EditorDomain.BLUR -> {
                viewModel.updateAdjustmentValue("Blur", 0f)
                viewModel.commitSliderChange()
            }
            selectedDomain == EditorDomain.VIGNETTE -> {
                viewModel.setVignettePreset(0f, 50f, 50f, 0f)
            }
            selectedDomain == EditorDomain.CROP -> viewModel.resetCrop()
            selectedDomain == EditorDomain.PRESETS -> viewModel.setFilter(FilterType.NONE)
            selectedDomain == EditorDomain.BLEMISH -> viewModel.clearRetouchStrokes(RetouchType.BLEMISH)
            selectedDomain == EditorDomain.SMOOTH -> viewModel.clearRetouchStrokes(RetouchType.SKIN_SMOOTH)
            selectedDomain == EditorDomain.HEAL -> viewModel.clearRetouchStrokes(
                RetouchType.PATCH_HEALING,
                RetouchType.CLONE_STAMP,
                RetouchType.OBJECT_REMOVAL,
                RetouchType.WRINKLE_REMOVAL
            )
            selectedDomain == EditorDomain.SKIN_TONE -> viewModel.clearRetouchStrokes(RetouchType.SKIN_TONE)
            selectedDomain == EditorDomain.MASK -> viewModel.clearMask()
            selectedDomain == EditorDomain.TEXT -> viewModel.clearTextOverlays()
            selectedDomain == EditorDomain.SHAPE -> viewModel.clearShapes()
            selectedDomain == EditorDomain.CALLOUT -> viewModel.clearCallouts()
            selectedDomain == EditorDomain.FRAME -> viewModel.clearFrame()
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
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val editorMaxHeight = maxHeight
                // Reusable composable components for Zone 01 - 05
                val renderTopToolbar = @Composable {
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
                }

                val renderWorkspace = @Composable { workspaceModifier: Modifier ->
                    EditorWorkspace(
                        previewBitmap = previewBitmap,
                        originalBitmap = originalBitmap,
                        activeTool = activeTool,
                        selectedBrushType = selectedBrushType,
                        selectedBrushColor = selectedBrushColor,
                        brushWidth = if (selectedDomain in listOf(EditorDomain.BLEMISH, EditorDomain.SMOOTH, EditorDomain.HEAL, EditorDomain.SKIN_TONE)) (currentRetouchRadius / 1.5f) else brushWidth,
                        brushOpacity = brushOpacity,
                        brushHardness = brushHardness,
                        activeRetouchType = currentRetouchType,
                        healingIntensity = currentRetouchIntensity,
                        healingTintColor = currentRetouchTintColor,
                        cropAspectRatio = if (selectedDomain == EditorDomain.CROP) selectedCropPreset.ratio else null,
                        isCurveMode = currentMode == EditorMode.CURVES || selectedDomain == EditorDomain.CURVES,
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
                        modifier = workspaceModifier,
                        colors = colors
                    )
                }

                val renderOperationToolbar = @Composable {
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

                val renderContextualPanel = @Composable { panelModifier: Modifier ->
                    EditorContextualPanel(
                        selectedDomain = selectedDomain,
                        sessionState = sessionState,
                        viewModel = viewModel,
                        activeMaskSubtool = activeMaskSubtool,
                        onSelectMaskSubtool = { activeMaskSubtool = it },
                        activeTextSubtool = activeTextSubtool,
                        onSelectTextSubtool = { activeTextSubtool = it },
                        activeShapeSubtool = activeShapeSubtool,
                        onSelectShapeSubtool = { activeShapeSubtool = it },
                        activeCalloutSubtool = activeCalloutSubtool,
                        onSelectCalloutSubtool = { activeCalloutSubtool = it },
                        activeFrameSubtool = activeFrameSubtool,
                        onSelectFrameSubtool = { activeFrameSubtool = it },
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
                        activeRetouchType = currentRetouchType,
                        onSelectRetouchType = { activeRetouchType = it },
                        healingIntensity = currentRetouchIntensity,
                        onHealingIntensityChange = { healingIntensity = it },
                        healingTintColor = currentRetouchTintColor,
                        onHealingTintColorChange = { healingTintColor = it },
                        brushWidth = brushWidth,
                        onBrushWidthChange = { brushWidth = it },
                        blemishRadius = blemishRadius,
                        onBlemishRadiusChange = { blemishRadius = it },
                        blemishIntensity = blemishIntensity,
                        onBlemishIntensityChange = { blemishIntensity = it },
                        smoothRadius = smoothRadius,
                        onSmoothRadiusChange = { smoothRadius = it },
                        smoothIntensity = smoothIntensity,
                        onSmoothIntensityChange = { smoothIntensity = it },
                        healRadius = healRadius,
                        onHealRadiusChange = { healRadius = it },
                        healIntensity = healIntensity,
                        onHealIntensityChange = { healIntensity = it },
                        healRetouchType = healRetouchType,
                        onSelectHealRetouchType = { healRetouchType = it },
                        skinToneRadius = skinToneRadius,
                        onSkinToneRadiusChange = { skinToneRadius = it },
                        skinToneIntensity = skinToneIntensity,
                        onSkinToneIntensityChange = { skinToneIntensity = it },
                        skinToneColor = skinToneColor,
                        onSkinToneColorChange = { skinToneColor = it },
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
                        onOpenMaskCreationSheet = {},
                        onFrameChange = { viewModel.setFrame(it) },
                        onBorderChange = { viewModel.setBorder(it) },
                        modifier = panelModifier,
                        colors = colors
                    )
                }

                val renderBottomNavigation = @Composable {
                    EditorBottomNavigation(
                        selectedDomain = selectedDomain,
                        onSelectDomain = { domain ->
                            selectedDomain = domain
                            when (domain) {
                                EditorDomain.LIGHT,
                                EditorDomain.COLOR,
                                EditorDomain.EFFECTS,
                                EditorDomain.DETAIL,
                                EditorDomain.BLUR,
                                EditorDomain.VIGNETTE -> viewModel.setActiveTool(EditorTool.ADJUST)
                                EditorDomain.CURVES -> viewModel.setActiveTool(EditorTool.CURVES)
                                EditorDomain.CROP -> viewModel.setActiveTool(EditorTool.CROP)
                                EditorDomain.PRESETS -> viewModel.setActiveTool(EditorTool.EFFECTS)
                                EditorDomain.BLEMISH -> {
                                    viewModel.setActiveTool(EditorTool.RETOUCH)
                                    activeRetouchType = RetouchType.BLEMISH
                                }
                                EditorDomain.SMOOTH -> {
                                    viewModel.setActiveTool(EditorTool.RETOUCH)
                                    activeRetouchType = RetouchType.SKIN_SMOOTH
                                }
                                EditorDomain.HEAL -> {
                                    viewModel.setActiveTool(EditorTool.RETOUCH)
                                    activeRetouchType = RetouchType.PATCH_HEALING
                                }
                                EditorDomain.SKIN_TONE -> {
                                    viewModel.setActiveTool(EditorTool.RETOUCH)
                                    activeRetouchType = RetouchType.SKIN_TONE
                                }
                                EditorDomain.MASK -> viewModel.setActiveTool(EditorTool.BRUSHES)
                                EditorDomain.TEXT -> viewModel.setActiveTool(EditorTool.TEXT)
                                EditorDomain.SHAPE -> viewModel.setActiveTool(EditorTool.SHAPE)
                                EditorDomain.CALLOUT -> viewModel.setActiveTool(EditorTool.CALLOUT)
                                EditorDomain.FRAME -> viewModel.setActiveTool(EditorTool.FRAME)
                                EditorDomain.AI_TOOLS -> viewModel.setActiveTool(EditorTool.AI_TOOLS)
                            }
                        },
                        colors = colors
                    )
                }

                if (isLandscape) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        renderTopToolbar()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .fillMaxHeight()
                            ) {
                                renderWorkspace(Modifier.fillMaxWidth().weight(1f))
                                renderOperationToolbar()
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                renderContextualPanel(Modifier.fillMaxWidth().weight(1f))
                                renderBottomNavigation()
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.baseSurface)
                    ) {
                        renderTopToolbar()
                        renderWorkspace(Modifier.fillMaxWidth().weight(1f))
                        renderOperationToolbar()
                        val contextualPanelMaxHeight = editorMaxHeight * 0.40f
                        renderContextualPanel(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = contextualPanelMaxHeight)
                        )
                        renderBottomNavigation()
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
