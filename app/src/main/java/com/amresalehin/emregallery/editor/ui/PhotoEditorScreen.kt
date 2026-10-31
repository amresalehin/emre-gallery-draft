package com.amresalehin.emregallery.editor.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    LaunchedEffect(item.uri) {
        viewModel.initialize(context, item)
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                PhotoEditorTopBar(
                    canUndo = canUndo && !isReplaying,
                    canRedo = canRedo && !isReplaying,
                    onClose = {
                        if (canUndo) showDiscardDialog = true else onBack()
                    },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onQuickSave = { doSave(false, 95) },
                    onContinueExport = { showExportSheet = true },
                    onEditWithAi = { showAiSheet = true }
                )

                // Main Photo Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
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

                    // Replay Progress Indicator Banner
                    if (isReplaying) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    progress = { replayProgress },
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.primary,
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

                // Bottom Tools Bar
                PhotoEditorBottomBar(
                    activeTool = activeTool,
                    onSelectTool = { tool ->
                        viewModel.setActiveTool(tool)
                        when (tool) {
                            EditorTool.CROP -> {
                                viewModel.setActiveSubItem(ToolSheetSubItem.CROP)
                                selectedCropPreset = AspectRatioPreset.FREE
                            }
                            EditorTool.ADJUST -> {
                                viewModel.setActiveAdjustment("Brightness")
                            }
                            EditorTool.EFFECTS -> {
                                // Effects panel displays
                            }
                            EditorTool.RETOUCH -> {
                                activeRetouchType = RetouchType.BLEMISH
                            }
                            EditorTool.REMOVE_BG -> {
                                // Background panel displays
                            }
                            EditorTool.BRUSHES -> {
                                selectedBrushType = BrushType.PEN
                            }
                            EditorTool.TEXT -> showTextDialog = true
                            EditorTool.STICKER -> showStickerSheet = true
                            EditorTool.SHAPE -> {
                                val types = ShapeType.entries
                                val currentType = sessionState.shapes.lastOrNull()?.shapeType
                                val nextIndex = if (currentType != null) (types.indexOf(currentType) + 1) % types.size else 0
                                viewModel.addShape(types[nextIndex], strokeColor = Color.White)
                            }
                            EditorTool.FRAME -> {
                                val nextFrame = when (sessionState.frame) {
                                    FrameStyle.NONE -> FrameStyle.POLAROID
                                    FrameStyle.POLAROID -> FrameStyle.FILM_STRIP
                                    FrameStyle.FILM_STRIP -> FrameStyle.MODERN_WHITE
                                    FrameStyle.MODERN_WHITE -> FrameStyle.DOUBLE_BORDER
                                    FrameStyle.DOUBLE_BORDER -> FrameStyle.VINTAGE_FADED
                                    FrameStyle.VINTAGE_FADED -> FrameStyle.MINIMAL_BLACK
                                    FrameStyle.MINIMAL_BLACK -> FrameStyle.NONE
                                }
                                viewModel.setFrame(nextFrame)
                            }
                            EditorTool.CALLOUT -> showCalloutDialog = true
                            EditorTool.LENS_FLARE -> {
                                val flareTypes = LensFlareType.entries
                                val currentFlare = sessionState.lensFlares.lastOrNull()?.flareType
                                val nextIndex = if (currentFlare != null) (flareTypes.indexOf(currentFlare) + 1) % flareTypes.size else 0
                                viewModel.addLensFlare(flareTypes[nextIndex])
                            }
                            EditorTool.AI_TOOLS, EditorTool.AI_TRY_ON, EditorTool.AI_REPLACE, EditorTool.AI_EXPAND -> {
                                showAiSheet = true
                            }
                            EditorTool.REPLAY -> viewModel.startReplay()
                            else -> {}
                        }
                    }
                )
            }

            // Processing overlay dialog
            if (isProcessing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
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
