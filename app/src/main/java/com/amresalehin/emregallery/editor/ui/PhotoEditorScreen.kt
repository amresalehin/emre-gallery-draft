package com.amresalehin.emregallery.editor.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.MediaRepository
import com.amresalehin.emregallery.editor.*
import kotlinx.coroutines.launch

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
    var lightcraftToolSelected by remember(item.uri) { mutableStateOf(false) }
    var activeAdjustCategory by remember(item.uri) { mutableStateOf("Light") }

    LaunchedEffect(item.uri) {
        lightcraftToolSelected = false
        activeAdjustCategory = "Light"
        viewModel.initialize(context, item)
        viewModel.setActiveTool(EditorTool.ADJUST)
        viewModel.setActiveAdjustment("Exposure")
    }

    val previewBitmap by viewModel.previewBitmap.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val activeSubItem by viewModel.activeSubItem.collectAsStateWithLifecycle()
    val activeAdjustment by viewModel.activeAdjustment.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val processingMessage by viewModel.processingMessage.collectAsStateWithLifecycle()
    val isReplaying by viewModel.isReplaying.collectAsStateWithLifecycle()
    val replayProgress by viewModel.replayProgress.collectAsStateWithLifecycle()

    var showAiSheet by remember { mutableStateOf(false) }
    var showStickerSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showCalloutDialog by remember { mutableStateOf(false) }

    // Interactive parameters
    var selectedCropPreset by remember { mutableStateOf(AspectRatioPreset.FREE) }
    var selectedBrushType by remember { mutableStateOf(BrushType.PEN) }
    var selectedBrushColor by remember { mutableStateOf(Color.White) }
    var brushWidth by remember { mutableFloatStateOf(10f) }
    var activeRetouchType by remember { mutableStateOf(RetouchType.BLEMISH) }

    val currentAdjValue = remember(sessionState.adjustments, activeAdjustment) {
        when (activeAdjustment) {
            "Brightness" -> sessionState.adjustments.brightness
            "Contrast" -> sessionState.adjustments.contrast
            "Saturation" -> sessionState.adjustments.saturation
            "Exposure" -> sessionState.adjustments.exposure
            "Highlights" -> sessionState.adjustments.highlights
            "Shadows" -> sessionState.adjustments.shadows
            "Temperature" -> sessionState.adjustments.temperature
            "Tint" -> sessionState.adjustments.tint
            "Sharpness" -> sessionState.adjustments.sharpness
            "Blur" -> sessionState.adjustments.blur
            "Vignette" -> sessionState.adjustments.vignette
            "Grain" -> sessionState.adjustments.grain
            "Vibrance" -> sessionState.adjustments.vibrance
            "Dehaze" -> sessionState.adjustments.dehaze
            "Dodge" -> sessionState.adjustments.dodge
            "Burn" -> sessionState.adjustments.burn
            "White Balance" -> sessionState.adjustments.whiteBalanceKelvin
            else -> 0f
        }
    }

    // Android Back Handler
    BackHandler {
        when {
            showAiSheet -> showAiSheet = false
            showStickerSheet -> showStickerSheet = false
            showExportSheet -> showExportSheet = false
            activeSubItem != null -> viewModel.setActiveSubItem(null)
            lightcraftToolSelected -> { lightcraftToolSelected = false; viewModel.setActiveTool(EditorTool.ADJUST) }
          Surface(Modifier.fillMaxSize(), color = Color.Black) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val sectionVisible = lightcraftToolSelected && activeTool == EditorTool.ADJUST
            val cropMode = lightcraftToolSelected && (activeTool == EditorTool.CROP || activeTool == EditorTool.FREE_CROP)

            val photoStage: @Composable (Modifier) -> Unit = { stageModifier ->
                Box(stageModifier.background(Color.Black), contentAlignment = Alignment.Center) {
                    PhotoEditorCanvas(
                        bitmap = previewBitmap,
                        activeTool = activeTool,
                        brushType = selectedBrushType,
                        brushColor = selectedBrushColor,
                        brushWidth = brushWidth,
                        activeRetouchType = activeRetouchType,
                        cropAspectRatio = if (activeTool == EditorTool.CROP || activeTool == EditorTool.FREE_CROP || activeSubItem == ToolSheetSubItem.CROP) selectedCropPreset.ratio else null,
                        onDrawingStrokeFinished = { viewModel.addDrawingStroke(it) },
                        onRetouchStrokeFinished = { viewModel.addRetouchStroke(it) },
                        onCropRectPreview = { viewModel.updateCropPreview(it) },
                        onCropRectChanged = { viewModel.commitCrop(it) }
                    )
                    if (isReplaying) {
                        Surface(
                            Modifier.align(Alignment.TopCenter).padding(top = 62.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(progress = { replayProgress }, modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Replaying edit history…", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    PhotoEditorTopBar(
                        canUndo = canUndo && !isReplaying,
                        canRedo = canRedo && !isReplaying,
                        onClose = { if (canUndo) showDiscardDialog = true else onBack() },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onQuickSave = { doSave(false, 95) },
                        onContinueExport = { showExportSheet = true },
                        onEditWithAi = { showAiSheet = true },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }

            val editingPanel: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth()) {
                    // Contextual Tool Panels above Bottom Bar
                    AnimatedVisibility(
                        visible = activeTool == EditorTool.CROP || activeTool == EditorTool.FREE_CROP || activeSubItem == ToolSheetSubItem.CROP,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        CropControlsPanel(
                            selectedPreset = selectedCropPreset,
                            straightenAngle = sessionState.transform.straightenAngle,
                            onSelectPreset = { selectedCropPreset = it },
                            onRotate90 = { viewModel.rotate90() },
                            onFlipH = { viewModel.flipHorizontal() },
                            onFlipV = { viewModel.flipVertical() },
                            onStraightenChange = { viewModel.setStraighten(it) },
                            onStraightenFinished = { viewModel.commitSliderChange() },
                            onApply = { viewModel.setActiveSubItem(null) }
                        )
                    }
    
                    AnimatedVisibility(
                        visible = activeTool == EditorTool.ADJUST,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        AdjustmentsPanel(
                            activeProperty = activeAdjustment,
                            currentValue = currentAdjValue,
                            onSelectProperty = { viewModel.setActiveAdjustment(it) },
                            onValueChange = { viewModel.updateAdjustmentValue(activeAdjustment, it) },
                            onValueChangeFinished = { viewModel.commitSliderChange() }
                        )
                    }
    
                    AnimatedVisibility(
                        visible = activeTool == EditorTool.EFFECTS,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        EffectsFiltersPanel(
                            selectedFilter = sessionState.filter,
                            filterIntensity = sessionState.filterIntensity,
                            onSelectFilter = { viewModel.setFilter(it) },
                            onIntensityChange = { viewModel.updateFilterIntensity(it) },
                            onIntensityChangeFinished = { viewModel.commitSliderChange() }
                        )
                    }
    
                    AnimatedVisibility(
                        visible = activeTool == EditorTool.RETOUCH || activeTool == EditorTool.REMOVE || activeTool == EditorTool.CLONE,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        RetouchPanel(
                            activeType = activeRetouchType,
                            brushRadius = brushWidth * 1.5f,
                            onSelectType = { activeRetouchType = it },
                            onRadiusChange = { brushWidth = it / 1.5f }
                        )
                    }
    
                    AnimatedVisibility(
                        visible = activeTool == EditorTool.REMOVE_BG || activeTool == EditorTool.CUTOUT,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        RemoveBgPanel(
                            currentMode = sessionState.background.mode,
                            blurRadius = sessionState.background.blurRadius,
                            onSelectMode = { viewModel.setBackgroundMode(it) },
                            onBlurRadiusChange = { viewModel.setBackgroundBlur(it) },
                            onBlurRadiusChangeFinished = { viewModel.commitSliderChange() },
                            onSelectColor = { viewModel.setBackgroundSolidColor(it) }
                        )
                    }
    
                    AnimatedVisibility(
                        visible = activeTool == EditorTool.BRUSHES || activeTool == EditorTool.DRAW,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        BrushesPanel(
                            selectedBrush = selectedBrushType,
                            brushColor = selectedBrushColor,
                            brushWidth = brushWidth,
                            onSelectBrush = { selectedBrushType = it },
                            onColorSelect = { selectedBrushColor = it },
                            onWidthChange = { brushWidth = it }
                        )
                    }
    
                    AnimatedVisibility(
                        visible = activeTool == EditorTool.TOOLS,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        ToolsGridSheet(
                            onSelectSubItem = { subItem ->
                                when (subItem) {
                                    ToolSheetSubItem.AUTO_LEVEL -> viewModel.autoLevelStraighten()
                                    ToolSheetSubItem.SHAPE_CROP -> {
                                        val options = ShapeCropType.entries
                                        val current = sessionState.transform.shapeCrop
                                        val next = if (current != null) options[(options.indexOf(current) + 1) % options.size] else ShapeCropType.CIRCLE
                                        viewModel.setShapeCrop(next)
                                    }
                                    ToolSheetSubItem.CORNER_PIN -> viewModel.setCornerPins(
                                        listOf(CornerPinPoint(0.04f, 0.04f), CornerPinPoint(0.96f, 0.08f),
                                            CornerPinPoint(0.92f, 0.96f), CornerPinPoint(0.06f, 0.92f))
                                    )
                                    ToolSheetSubItem.LIQUIFY -> viewModel.addLiquifyPoint(LiquifyPoint(0.5f, 0.5f, mode = LiquifyMode.EXPAND))
                                    ToolSheetSubItem.SMUDGE -> { viewModel.setActiveTool(EditorTool.RETOUCH); activeRetouchType = RetouchType.SMUDGE_BRUSH }
                                    ToolSheetSubItem.PATCH -> { viewModel.setActiveTool(EditorTool.RETOUCH); activeRetouchType = RetouchType.PATCH_HEALING }
                                    ToolSheetSubItem.CLONE -> { viewModel.setActiveTool(EditorTool.RETOUCH); activeRetouchType = RetouchType.CLONE_STAMP }
                                    ToolSheetSubItem.DISPERSION -> {
                                        val current = sessionState.transform.dispersionAmount
                                        viewModel.setDispersion(if (current > 0f) 0f else 50f)
                                    }
                                    ToolSheetSubItem.STRETCH -> viewModel.setPerspective(20f, 0f)
                                    ToolSheetSubItem.MOTION -> {
                                        val current = sessionState.transform.motionAmount
                                        viewModel.setMotion(if (current > 0f) 0f else 45f, 45f)
                                    }
                                    ToolSheetSubItem.ROTATE -> viewModel.rotate90()
                                    ToolSheetSubItem.FLIP -> viewModel.flipHorizontal()
                                    ToolSheetSubItem.STRAIGHTEN, ToolSheetSubItem.CROP, ToolSheetSubItem.PERSPECTIVE -> viewModel.setActiveTool(EditorTool.CROP)
                                    else -> {}
                                }
                            }
                        )
                    }
                }
    
                }
            }

            val selectEditorTool: (EditorTool) -> Unit = { tool ->
                lightcraftToolSelected = true
                viewModel.setActiveTool(tool)
                when (tool) {
                    EditorTool.ADJUST -> viewModel.setActiveAdjustment(lightcraftDefaultAdjustment(activeAdjustCategory))
                    EditorTool.CROP -> { viewModel.setActiveSubItem(ToolSheetSubItem.CROP); selectedCropPreset = AspectRatioPreset.FREE }
                    EditorTool.RETOUCH -> activeRetouchType = RetouchType.BLEMISH
                    EditorTool.BRUSHES -> selectedBrushType = BrushType.PEN
                    else -> {}
                }
            }

            if (landscape) {
                val sideWidth = when {
                    !lightcraftToolSelected -> 54.dp
                    activeTool == EditorTool.ADJUST -> 430.dp
                    cropMode -> 369.dp
                    else -> 374.dp
                }
                Row(Modifier.fillMaxSize()) {
                    photoStage(Modifier.weight(1f).fillMaxHeight())
                    Row(Modifier.width(sideWidth).fillMaxHeight()) {
                        if (lightcraftToolSelected) {
                            LightcraftPanelSurface(Modifier.weight(1f).fillMaxHeight()) {
                                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { editingPanel() }
                            }
                        }
                        if (sectionVisible) {
                            LightcraftSectionNavigation(
                                activeSection = activeAdjustCategory,
                                onSelectSection = { section ->
                                    activeAdjustCategory = section
                                    viewModel.setActiveAdjustment(lightcraftDefaultAdjustment(section))
                                },
                                vertical = true
                            )
                        }
                        if (cropMode) {
                            LightcraftModeRail(
                                onCancel = { viewModel.setActiveSubItem(null); lightcraftToolSelected = false; viewModel.setActiveTool(EditorTool.ADJUST) },
                                onApply = { viewModel.commitSliderChange(); viewModel.setActiveSubItem(null); lightcraftToolSelected = false; viewModel.setActiveTool(EditorTool.ADJUST) }
                            )
                        } else {
                            LightcraftToolNavigation(activeTool, lightcraftToolSelected, ::selectEditorTool, vertical = true)
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    photoStage(Modifier.fillMaxWidth().weight(1f))
                    if (lightcraftToolSelected) {
                        val panelHeight = when {
                            cropMode -> 232.dp
                            activeTool == EditorTool.ADJUST -> 184.dp
                            activeTool == EditorTool.EFFECTS -> 229.dp
                            activeTool == EditorTool.TOOLS -> 306.dp
                            else -> 184.dp
                        }
                        LightcraftPanelSurface(Modifier.fillMaxWidth().heightIn(max = panelHeight)) {
                            Box(Modifier.fillMaxWidth().heightIn(max = panelHeight).verticalScroll(rememberScrollState())) { editingPanel() }
                        }
                    }
                    if (sectionVisible) {
                        LightcraftSectionNavigation(
                            activeSection = activeAdjustCategory,
                            onSelectSection = { section ->
                                activeAdjustCategory = section
                                viewModel.setActiveAdjustment(lightcraftDefaultAdjustment(section))
                            }
                        )
                    }
                    if (cropMode) {
                        LightcraftModeBar(
                            "Crop",
                            onCancel = { viewModel.setActiveSubItem(null); lightcraftToolSelected = false; viewModel.setActiveTool(EditorTool.ADJUST) },
                            onApply = { viewModel.commitSliderChange(); viewModel.setActiveSubItem(null); lightcraftToolSelected = false; viewModel.setActiveTool(EditorTool.ADJUST) }
                        )
                    } else {
                        LightcraftToolNavigation(activeTool, lightcraftToolSelected, ::selectEditorTool)
                    }
                }
            }

            if (isProcessing) {
                Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.55f)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF292929)), shape = RoundedCornerShape(4.dp)) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(14.dp))
                                Text(processingMessage.ifBlank { "Processing…" }, color = Color.White, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }

                            modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(16.dp))
                                Text(processingMessage.ifBlank { "Processing..." }, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    // AI Tools Sheet
    if (showAiSheet) {
        ModalBottomSheet(onDismissRequest = { showAiSheet = false }) {
            AiToolsSheet(
                aiSettings = sessionState.aiSettings,
                onToggleAutoEnhance = { viewModel.toggleAiAutoEnhance(it) },
                onSelectStyle = { viewModel.setAiStyleTransfer(it) },
                onSelectTryOn = { viewModel.setAiTryOn(it) },
                onExpandChange = { viewModel.updateAiExpand(it) },
                onExpandChangeFinished = { viewModel.commitSliderChange() },
                onDismiss = { showAiSheet = false }
            )
        }
    }

    // Sticker Picker Sheet
    if (showStickerSheet) {
        ModalBottomSheet(onDismissRequest = { showStickerSheet = false }) {
            StickerPickerSheet(
                onSelectSticker = { emoji ->
                    viewModel.addSticker(emoji)
                },
                onDismiss = { showStickerSheet = false }
            )
        }
    }

    // Export Options Sheet
    if (showExportSheet) {
        ExportBottomSheet(
            onConfirmSave = { isPng, quality ->
                doSave(isPng, quality)
            },
            onDismiss = { showExportSheet = false }
        )
    }

    // Text Input Dialog
    if (showTextDialog) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text Overlay") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Enter caption...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.addTextOverlay(input.trim())
                        }
                        showTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Callout Input Dialog
    if (showCalloutDialog) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCalloutDialog = false },
            title = { Text("Add Speech Callout") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Enter speech text...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.addCallout(input.trim())
                        }
                        showCalloutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Add Callout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalloutDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Discard Changes Confirmation Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Edits?") },
            text = { Text("You have unsaved edits. Are you sure you want to discard them and exit?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}
