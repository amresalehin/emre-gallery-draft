package com.amresalehin.emregallery.editor.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Current active mode / subpanel in the redesigned Photoshop Mobile Overlays Workspace.
 */
enum class OverlayActivePanel {
    NONE,               // Standard quick properties strip
    LAYERS_LIST,        // "Layers" bottom sheet
    BLEND_AND_OPACITY,  // "Blend and opacity" panel
    TRANSFORM,          // "Transform" panel (Scale & Rotate, Skew, Distort)
    SELECT_AREA,        // "Select area" / "Create mask" panel
    MODIFY_MASK,        // "Modify mask" numeric keypad & slider panel
    ADJUSTMENT_BW,      // "Black and white" channel slider inspector
    ADJUSTMENT_HUE_SAT, // "Hue and Saturation" channel slider inspector
    ADD_IMAGE_SHEET,    // "Add image" bottom sheet (Photos, Stock, AI)
    ADD_ADJUSTMENT_SHEET// "Add adjustment layer" bottom sheet
}

/**
 * Master Redesigned Overlays Workspace Panel:
 * Completely faithful to the Photoshop Mobile / Lightroom screen recording.
 * Preserves the existing app's top bar and bottom-most navigation dock while providing:
 * - Floating Layers Badge `< [Thumb]` on right edge of canvas
 * - Floating `+` Speed Dial FAB (Empty, Fill, Type, Adjustment, Image layer)
 * - Interactive Transform Bounding Box with 8 handle points & rotation stalk
 * - Animated Toast Capsules ("A new layer has been added", "Layer masked", "Mask modified")
 * - Layers Stack manager sheet with reordering, locking, visibility, renaming, and context menu
 * - Blend and Opacity panel with 18 Photoshop blend modes and precise opacity slider
 * - Selection / Cutout Masking suite with One-Tap "Subject" isolation
 * - Modify Mask panel with custom numeric keypad and vertical slider
 * - Black and White adjustment layer with 6 color channel sliders (Reds, Yellows, Greens, Cyans, Blues, Magentas)
 * - Curated stock image options (Nebula, PropPlane, Astronaut, Whale, Canvas)
 */
@Composable
fun OverlaysWorkspacePanel(
    sessionState: EditSessionState,
    viewModel: PhotoEditorViewModel? = null,
    onAddLayer: (EditorOverlayLayer) -> Unit = {},
    onUpdateLayer: (EditorOverlayLayer) -> Unit = {},
    onSelectLayer: (String) -> Unit = {},
    onDeleteLayer: (String) -> Unit = {},
    onDuplicateLayer: (String) -> Unit = {},
    onToggleVisibility: (String) -> Unit = {},
    onToggleLock: (String) -> Unit = {},
    onSetOpacity: (String, Float) -> Unit = { _, _ -> },
    onSetBlendMode: (String, String) -> Unit = { _, _ -> },
    onMoveLayer: (String, Int) -> Unit = { _, _ -> },
    onRenameLayer: (String, String) -> Unit = { _, _ -> },
    onApplyMask: (String, LayerMask) -> Unit = { _, _ -> },
    onModifyMask: (String, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onRemoveMask: (String) -> Unit = {},
    onUpdateBwSettings: (String, BlackAndWhiteSettings) -> Unit = { _, _ -> },
    // Backwards-compatible callbacks
    onFrameChange: (FrameStyle) -> Unit = {},
    onBorderChange: (BorderSettings) -> Unit = {},
    onAddText: (String, Color, Color) -> Unit = { _, _, _ -> },
    onUpdateText: (TextOverlayItem) -> Unit = {},
    onDeleteText: (String) -> Unit = {},
    onDuplicateText: (String) -> Unit = {},
    onAddSticker: (String) -> Unit = {},
    onUpdateSticker: (StickerItem) -> Unit = {},
    onDeleteSticker: (String) -> Unit = {},
    onDuplicateSticker: (String) -> Unit = {},
    onAddShape: (ShapeType) -> Unit = {},
    onUpdateShape: (ShapeItem) -> Unit = {},
    onDeleteShape: (String) -> Unit = {},
    onDuplicateShape: (String) -> Unit = {},
    onAddCallout: (String, CalloutStyle) -> Unit = { _, _ -> },
    onUpdateCallout: (CalloutItem) -> Unit = {},
    onDeleteCallout: (String) -> Unit = {},
    onAddLensFlare: (LensFlareType) -> Unit = {},
    onUpdateLensFlare: (LensFlareItem) -> Unit = {},
    onDeleteLensFlare: (String) -> Unit = {},
    onBackgroundChange: (BackgroundSettings) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activePanel by remember { mutableStateOf(OverlayActivePanel.NONE) }
    var renameLayerTarget by remember { mutableStateOf<EditorOverlayLayer?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Auto-dismiss toast
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2200)
            toastMessage = null
        }
    }

    val activeLayer = sessionState.overlayLayers.firstOrNull { it.id == sessionState.activeOverlayLayerId }
        ?: sessionState.overlayLayers.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        // Context-sensitive Subpanel Content
        when (activePanel) {
            OverlayActivePanel.NONE -> {
                // Tier 2: Layer Properties Context Bar (shown directly above the bottom navigation dock)
                LayerPropertiesBar(
                    activeLayer = activeLayer,
                    onOpenBlend = { activePanel = OverlayActivePanel.BLEND_AND_OPACITY },
                    onOpenMask = {
                        if (activeLayer?.mask != null && activeLayer.mask.isEnabled) {
                            activePanel = OverlayActivePanel.MODIFY_MASK
                        } else {
                            activePanel = OverlayActivePanel.SELECT_AREA
                        }
                    },
                    onOpenTransform = { activePanel = OverlayActivePanel.TRANSFORM },
                    onOpenLayers = { activePanel = OverlayActivePanel.LAYERS_LIST },
                    onToggleVisibility = {
                        if (activeLayer != null) {
                            if (viewModel != null) viewModel.toggleOverlayLayerVisibility(activeLayer.id)
                            else onToggleVisibility(activeLayer.id)
                        }
                    },
                    onFlipH = {
                        if (activeLayer != null) {
                            val updated = activeLayer.copy(isFlippedH = !activeLayer.isFlippedH)
                            if (viewModel != null) viewModel.updateOverlayLayer(updated)
                            else onUpdateLayer(updated)
                        }
                    },
                    onToggleClipping = {
                        if (activeLayer != null) {
                            viewModel?.toggleOverlayLayerClipping(activeLayer.id)
                            val isNowClipped = !(activeLayer.isClipped)
                            toastMessage = if (isNowClipped) "Layer clipped" else "Layer un-clipped"
                        }
                    },
                    onInvertMask = {
                        if (activeLayer != null && activeLayer.mask != null) {
                            val cur = activeLayer.mask
                            val updated = cur.copy(isInverted = !cur.isInverted)
                            if (viewModel != null) viewModel.applyOverlayLayerMask(activeLayer.id, updated)
                            else onApplyMask(activeLayer.id, updated)
                            toastMessage = "Mask modified"
                        }
                    },
                    onRemoveMask = {
                        if (activeLayer != null) {
                            if (viewModel != null) viewModel.removeOverlayLayerMask(activeLayer.id)
                            else onRemoveMask(activeLayer.id)
                            toastMessage = "Mask removed"
                        }
                    },
                    onEditMask = {
                        activePanel = OverlayActivePanel.SELECT_AREA
                    },
                    onMoreOptions = { activePanel = OverlayActivePanel.LAYERS_LIST }
                )
            }
            OverlayActivePanel.LAYERS_LIST -> {
                LayersListPanel(
                    layers = sessionState.overlayLayers,
                    activeLayerId = activeLayer?.id,
                    onSelectLayer = {
                        if (viewModel != null) viewModel.selectOverlayLayer(it)
                        else onSelectLayer(it)
                    },
                    onClose = { activePanel = OverlayActivePanel.NONE },
                    onToggleVisibility = {
                        if (viewModel != null) viewModel.toggleOverlayLayerVisibility(it)
                        else onToggleVisibility(it)
                    },
                    onToggleLock = {
                        if (viewModel != null) viewModel.toggleOverlayLayerLock(it)
                        else onToggleLock(it)
                    },
                    onDeleteLayer = {
                        if (viewModel != null) viewModel.deleteOverlayLayer(it)
                        else onDeleteLayer(it)
                    },
                    onDuplicateLayer = {
                        if (viewModel != null) viewModel.duplicateOverlayLayer(it)
                        else onDuplicateLayer(it)
                    },
                    onMoveLayer = { id, dir ->
                        if (viewModel != null) viewModel.moveOverlayLayer(id, dir)
                        else onMoveLayer(id, dir)
                    },
                    onRenameRequest = { renameLayerTarget = it },
                    onOpenBlend = { activePanel = OverlayActivePanel.BLEND_AND_OPACITY },
                    onOpenMask = {
                        if (activeLayer?.mask != null && activeLayer.mask.isEnabled) {
                            activePanel = OverlayActivePanel.MODIFY_MASK
                        } else {
                            activePanel = OverlayActivePanel.SELECT_AREA
                        }
                    },
                    onOpenTransform = { activePanel = OverlayActivePanel.TRANSFORM }
                )
            }
            OverlayActivePanel.BLEND_AND_OPACITY -> {
                BlendAndOpacityPanel(
                    currentBlendMode = activeLayer?.blendMode ?: "Normal",
                    currentOpacity = activeLayer?.opacity ?: 1.0f,
                    onBlendModeChange = { bm ->
                        if (activeLayer != null) {
                            if (viewModel != null) viewModel.setOverlayLayerBlendMode(activeLayer.id, bm)
                            else onSetBlendMode(activeLayer.id, bm)
                        }
                    },
                    onOpacityChange = { op ->
                        if (activeLayer != null) {
                            if (viewModel != null) viewModel.setOverlayLayerOpacity(activeLayer.id, op)
                            else onSetOpacity(activeLayer.id, op)
                        }
                    },
                    onDone = {
                        if (viewModel != null) viewModel.commitOverlayLayerOpacity()
                        activePanel = OverlayActivePanel.NONE
                    }
                )
            }
            OverlayActivePanel.TRANSFORM -> {
                TransformControlPanel(
                    layer = activeLayer,
                    onFlipH = {
                        if (activeLayer != null) {
                            val updated = activeLayer.copy(isFlippedH = !activeLayer.isFlippedH)
                            if (viewModel != null) viewModel.updateOverlayLayer(updated)
                            else onUpdateLayer(updated)
                        }
                    },
                    onFlipV = {
                        if (activeLayer != null) {
                            val updated = activeLayer.copy(isFlippedV = !activeLayer.isFlippedV)
                            if (viewModel != null) viewModel.updateOverlayLayer(updated)
                            else onUpdateLayer(updated)
                        }
                    },
                    onReset = {
                        if (activeLayer != null) {
                            val updated = activeLayer.copy(
                                xRatio = 0.5f, yRatio = 0.5f,
                                scaleFactor = 1.0f, rotationDegrees = 0f,
                                isFlippedH = false, isFlippedV = false
                            )
                            if (viewModel != null) viewModel.updateOverlayLayer(updated)
                            else onUpdateLayer(updated)
                        }
                    },
                    onDone = { activePanel = OverlayActivePanel.NONE }
                )
            }
            OverlayActivePanel.SELECT_AREA -> {
                SelectAreaMaskPanel(
                    onApplySubjectMask = {
                        if (activeLayer != null) {
                            val mask = LayerMask(isEnabled = true, hasSubjectMask = true)
                            if (viewModel != null) viewModel.applyOverlayLayerMask(activeLayer.id, mask)
                            else onApplyMask(activeLayer.id, mask)
                            toastMessage = "Layer masked"
                            activePanel = OverlayActivePanel.NONE
                        }
                    },
                    onApplyInvertMask = {
                        if (activeLayer != null) {
                            val mask = LayerMask(isEnabled = true, isInverted = true, hasSubjectMask = true)
                            if (viewModel != null) viewModel.applyOverlayLayerMask(activeLayer.id, mask)
                            else onApplyMask(activeLayer.id, mask)
                            toastMessage = "Layer masked"
                            activePanel = OverlayActivePanel.NONE
                        }
                    },
                    onCancel = { activePanel = OverlayActivePanel.NONE }
                )
            }
            OverlayActivePanel.MODIFY_MASK -> {
                ModifyMaskKeypadPanel(
                    currentVal = activeLayer?.mask?.contractExpand?.toInt() ?: -2,
                    onValueApplied = { newVal ->
                        if (activeLayer != null) {
                            val curMask = activeLayer.mask ?: LayerMask()
                            val updated = curMask.copy(contractExpand = newVal.toFloat())
                            if (viewModel != null) viewModel.applyOverlayLayerMask(activeLayer.id, updated)
                            else onApplyMask(activeLayer.id, updated)
                            toastMessage = "Mask modified"
                        }
                        activePanel = OverlayActivePanel.NONE
                    },
                    onCancel = { activePanel = OverlayActivePanel.NONE }
                )
            }
            OverlayActivePanel.ADJUSTMENT_BW -> {
                if (activeLayer != null) {
                    BlackAndWhiteAdjustmentPanel(
                        settings = activeLayer.bwSettings,
                        onSettingsChange = { newBw ->
                            if (viewModel != null) viewModel.updateOverlayLayerAdjustment(activeLayer.id, newBw)
                            else onUpdateBwSettings(activeLayer.id, newBw)
                        },
                        onDone = { activePanel = OverlayActivePanel.NONE }
                    )
                } else {
                    activePanel = OverlayActivePanel.NONE
                }
            }
            OverlayActivePanel.ADJUSTMENT_HUE_SAT -> {
                if (activeLayer != null) {
                    HueSaturationAdjustmentPanel(
                        hue = activeLayer.hueShift,
                        sat = activeLayer.satShift,
                        lightness = activeLayer.lightnessShift,
                        isColorize = activeLayer.isColorize,
                        onValuesChange = { h, s, l, col ->
                            viewModel?.updateOverlayLayerHsl(activeLayer.id, h, s, l, col)
                        },
                        onDone = { activePanel = OverlayActivePanel.NONE }
                    )
                } else {
                    activePanel = OverlayActivePanel.NONE
                }
            }
            OverlayActivePanel.ADD_IMAGE_SHEET, OverlayActivePanel.ADD_ADJUSTMENT_SHEET -> {
                // Handled in modal dialogs/sheets
            }
        }
    }

    // Rename Layer Dialog
    if (renameLayerTarget != null) {
        RenameLayerDialog(
            currentName = renameLayerTarget!!.name,
            onDismiss = { renameLayerTarget = null },
            onRename = { newName ->
                if (viewModel != null) viewModel.renameOverlayLayer(renameLayerTarget!!.id, newName)
                else onRenameLayer(renameLayerTarget!!.id, newName)
                renameLayerTarget = null
            }
        )
    }
}

/**
 * Canvas Overlay Controls:
 * Renders the floating `< [Thumbnail]` badge, `+` Speed Dial, Transform Bounding Box,
 * and Toast Capsule directly on top of the photo canvas!
 */
@Composable
fun PhotoshopCanvasOverlaysControls(
    sessionState: EditSessionState,
    viewModel: PhotoEditorViewModel? = null,
    onAddLayer: (EditorOverlayLayer) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSpeedDialOpen by remember { mutableStateOf(false) }
    var isLayerRailExpanded by remember { mutableStateOf(false) }
    var showLayersSheet by remember { mutableStateOf(false) }
    var showAddImageSheet by remember { mutableStateOf(false) }
    var showAddAdjustmentSheet by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var renameLayerTarget by remember { mutableStateOf<EditorOverlayLayer?>(null) }

    LaunchedEffect(sessionState.description) {
        if (sessionState.description.startsWith("Layer clipped") ||
            sessionState.description.startsWith("Layer un-clipped") ||
            sessionState.description.startsWith("Apply layer mask")
        ) {
            toastMessage = if (sessionState.description.startsWith("Apply layer mask")) "Layer masked" else sessionState.description
        }
    }

    // Android Photo Picker Launcher (Zero-permission photo selection)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val count = sessionState.overlayLayers.size + 1
            val newLayer = EditorOverlayLayer(
                id = java.util.UUID.randomUUID().toString(),
                name = "Photo $count",
                type = OverlayLayerType.IMAGE,
                imageUri = uri.toString()
            )
            if (viewModel != null) viewModel.addOverlayLayer(newLayer) else onAddLayer(newLayer)
            toastMessage = "A new layer has been added"
            showAddImageSheet = false
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2400)
            toastMessage = null
        }
    }

    val activeLayer = sessionState.overlayLayers.firstOrNull { it.id == sessionState.activeOverlayLayerId }
        ?: sessionState.overlayLayers.lastOrNull()

    Box(modifier = modifier.fillMaxSize()) {
        // -------------------------------------------------------------
        // 1. Toast Notification Banner (Floating White Pill at Top)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 10.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = toastMessage ?: "",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 2. Interactive Transform Bounding Box with 8 Handles on Canvas
        // -------------------------------------------------------------
        if (activeLayer != null && activeLayer.isVisible) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = activeLayer.xRatio * size.width
                val cy = activeLayer.yRatio * size.height
                val lw = activeLayer.widthRatio * size.width * activeLayer.scaleFactor
                val lh = activeLayer.heightRatio * size.height * activeLayer.scaleFactor

                val l = cx - lw / 2f
                val t = cy - lh / 2f
                val r = cx + lw / 2f
                val b = cy + lh / 2f

                // Outer border line
                drawRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(l, t),
                    size = Size(lw, lh),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // 4 Corner handles (White circles with thin border)
                val handleRadius = 6.dp.toPx()
                val cornerPoints = listOf(
                    Offset(l, t), Offset(r, t),
                    Offset(l, b), Offset(r, b)
                )
                cornerPoints.forEach { pt ->
                    drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = handleRadius + 1.5.dp.toPx(), center = pt)
                    drawCircle(color = Color.White, radius = handleRadius, center = pt)
                }

                // 4 Edge center handles
                val edgePoints = listOf(
                    Offset(cx, t), Offset(cx, b),
                    Offset(l, cy), Offset(r, cy)
                )
                edgePoints.forEach { pt ->
                    drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = pt)
                }

                // Top rotation stalk and pin
                val stalkLength = 26.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = Offset(cx, t),
                    end = Offset(cx, t - stalkLength),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = Offset(cx, t - stalkLength))
            }
        }

        // -------------------------------------------------------------
        // 3. Floating Layer Stack Rail on Right Edge (Photoshop Mobile Style)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 90.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xDD1C1C1E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier.padding(4.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Header expand/collapse toggle row (< / >)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { isLayerRailExpanded = !isLayerRailExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isLayerRailExpanded) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isLayerRailExpanded) "Collapse layer rail" else "Expand layer rail",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (!isLayerRailExpanded) {
                            // Collapsed view: active layer thumbnail + child mask thumbnail
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (activeLayer?.isMaskSelected != true) 2.dp else 1.dp,
                                            color = if (activeLayer?.isMaskSelected != true) Color.White else Color.White.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (activeLayer != null) {
                                                viewModel?.setOverlayLayerMaskTarget(activeLayer.id, false)
                                            }
                                        }
                                ) {
                                    LayerThumbnailIcon(layer = activeLayer, size = 38.dp)
                                }

                                if (activeLayer?.mask != null && activeLayer.mask.isEnabled) {
                                    MaskThumbnailIcon(
                                        mask = activeLayer.mask,
                                        size = 38.dp,
                                        isSelected = activeLayer.isMaskSelected,
                                        onClick = {
                                            viewModel?.setOverlayLayerMaskTarget(activeLayer.id, true)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Expanded Vertical Layer Rail: Shows all layers in composition with child masks attached!
                    if (isLayerRailExpanded) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            for (layer in sessionState.overlayLayers.reversed()) {
                                val isLayerActive = layer.id == activeLayer?.id
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (layer.isClipped) {
                                        Icon(
                                            imageVector = Icons.Default.SubdirectoryArrowRight,
                                            contentDescription = "Clipped",
                                            tint = Color(0xFF64B5F6),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isLayerActive && !layer.isMaskSelected) 2.dp else 1.dp,
                                                color = if (isLayerActive && !layer.isMaskSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                viewModel?.selectOverlayLayer(layer.id)
                                                viewModel?.setOverlayLayerMaskTarget(layer.id, false)
                                            }
                                    ) {
                                        LayerThumbnailIcon(layer = layer, size = 38.dp)
                                    }

                                    if (layer.mask != null && layer.mask.isEnabled) {
                                        MaskThumbnailIcon(
                                            mask = layer.mask,
                                            size = 38.dp,
                                            isSelected = isLayerActive && layer.isMaskSelected,
                                            onClick = {
                                                viewModel?.selectOverlayLayer(layer.id)
                                                viewModel?.setOverlayLayerMaskTarget(layer.id, true)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 4. Floating + Speed Dial FAB on Bottom Right
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 18.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Speed Dial Menu Items
                AnimatedVisibility(
                    visible = isSpeedDialOpen,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpeedDialItem(
                            label = "Empty layer",
                            icon = Icons.Default.Layers,
                            onClick = {
                                isSpeedDialOpen = false
                                val count = sessionState.overlayLayers.size + 1
                                val layer = EditorOverlayLayer(
                                    name = "Layer $count",
                                    type = OverlayLayerType.EMPTY
                                )
                                if (viewModel != null) viewModel.addOverlayLayer(layer) else onAddLayer(layer)
                                toastMessage = "A new layer has been added"
                            }
                        )
                        SpeedDialItem(
                            label = "Fill layer",
                            icon = Icons.Default.FormatColorFill,
                            onClick = {
                                isSpeedDialOpen = false
                                val count = sessionState.overlayLayers.size + 1
                                val layer = EditorOverlayLayer(
                                    name = "Fill $count",
                                    type = OverlayLayerType.FILL,
                                    fillSolidColor = Color(0xFF2C3E50)
                                )
                                if (viewModel != null) viewModel.addOverlayLayer(layer) else onAddLayer(layer)
                                toastMessage = "A new layer has been added"
                            }
                        )
                        SpeedDialItem(
                            label = "Type layer",
                            icon = Icons.Default.TextFields,
                            onClick = {
                                isSpeedDialOpen = false
                                val count = sessionState.overlayLayers.size + 1
                                val layer = EditorOverlayLayer(
                                    name = "Type $count",
                                    type = OverlayLayerType.TYPE,
                                    textContent = "Sample Text"
                                )
                                if (viewModel != null) viewModel.addOverlayLayer(layer) else onAddLayer(layer)
                                toastMessage = "A new layer has been added"
                            }
                        )
                        SpeedDialItem(
                            label = "Adjustment layer",
                            icon = Icons.Default.Contrast,
                            onClick = {
                                isSpeedDialOpen = false
                                showAddAdjustmentSheet = true
                            }
                        )
                        SpeedDialItem(
                            label = "Image layer",
                            icon = Icons.Default.AddPhotoAlternate,
                            onClick = {
                                isSpeedDialOpen = false
                                showAddImageSheet = true
                            }
                        )
                    }
                }

                // Master FAB Button (+)
                val fabRotation by animateFloatAsState(
                    targetValue = if (isSpeedDialOpen) 45f else 0f,
                    label = "fab_rot"
                )
                Surface(
                    onClick = { isSpeedDialOpen = !isSpeedDialOpen },
                    shape = CircleShape,
                    color = Color.White,
                    contentColor = Color.Black,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("overlay_fab_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (isSpeedDialOpen) "Close menu" else "Add layer",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(26.dp)
                                .rotate(fabRotation)
                        )
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Add Image Bottom Sheet
    // -------------------------------------------------------------
    if (showAddImageSheet) {
        AddImageBottomSheet(
            onDismiss = { showAddImageSheet = false },
            onPickFromPhotos = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSelectStock = { stockKey, name ->
                val layer = EditorOverlayLayer(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    type = OverlayLayerType.IMAGE,
                    stockImageKey = stockKey
                )
                if (viewModel != null) viewModel.addOverlayLayer(layer) else onAddLayer(layer)
                toastMessage = "A new layer has been added"
                showAddImageSheet = false
            },
            onGenerateImage = {
                showAddImageSheet = false
                showPromptDialog = true
            }
        )
    }

    // -------------------------------------------------------------
    // Add Adjustment Layer Bottom Sheet
    // -------------------------------------------------------------
    if (showAddAdjustmentSheet) {
        AddAdjustmentBottomSheet(
            onDismiss = { showAddAdjustmentSheet = false },
            onSelectType = { type ->
                showAddAdjustmentSheet = false
                val count = sessionState.overlayLayers.count { it.type == OverlayLayerType.ADJUSTMENT } + 1
                val layer = EditorOverlayLayer(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "${type.label} $count",
                    type = OverlayLayerType.ADJUSTMENT,
                    adjustmentType = type
                )
                if (viewModel != null) viewModel.addOverlayLayer(layer) else onAddLayer(layer)
                toastMessage = "A new layer has been added"
            }
        )
    }

    // -------------------------------------------------------------
    // Generate Image Prompt Dialog
    // -------------------------------------------------------------
    if (showPromptDialog) {
        GenerateImagePromptDialog(
            onDismiss = { showPromptDialog = false },
            onGenerate = { prompt ->
                showPromptDialog = false
                val count = sessionState.overlayLayers.size + 1
                val stockKey = when {
                    prompt.contains("space", true) || prompt.contains("nebula", true) -> "nebula"
                    prompt.contains("plane", true) -> "propplane"
                    prompt.contains("astronaut", true) -> "astronaut"
                    prompt.contains("whale", true) -> "whale"
                    else -> "nebula"
                }
                val layer = EditorOverlayLayer(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "AI: $prompt",
                    type = OverlayLayerType.IMAGE,
                    stockImageKey = stockKey
                )
                if (viewModel != null) viewModel.addOverlayLayer(layer) else onAddLayer(layer)
                toastMessage = "A new layer has been added"
            }
        )
    }
}

// -----------------------------------------------------------------------------
// Sub-Components
// -----------------------------------------------------------------------------

@Composable
private fun SpeedDialItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF21E1E1E),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Layer properties context bar (docked directly above the bottom bar).
 * Dynamically switches between Layer Properties and Mask Properties depending on
 * whether the layer or its attached child mask thumbnail is selected.
 */
@Composable
private fun LayerPropertiesBar(
    activeLayer: EditorOverlayLayer?,
    onOpenBlend: () -> Unit,
    onOpenMask: () -> Unit,
    onOpenTransform: () -> Unit,
    onOpenLayers: () -> Unit,
    onToggleVisibility: () -> Unit,
    onFlipH: () -> Unit,
    onToggleClipping: () -> Unit = {},
    onInvertMask: () -> Unit = {},
    onRemoveMask: () -> Unit = {},
    onEditMask: () -> Unit = {},
    onMoreOptions: () -> Unit
) {
    if (activeLayer == null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "No overlay layers yet. Tap + to add",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }
            Surface(
                onClick = onOpenLayers,
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF222222),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Text(
                    text = "Layers",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    } else if (activeLayer.isMaskSelected) {
        // =====================================================================
        // MASK CHILD PROPERTIES BAR (When the attached mask thumbnail is tapped)
        // =====================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Quick Action Icons Row for Mask
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onInvertMask, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.InvertColors, "Invert mask", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onRemoveMask, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Remove mask", tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onOpenLayers, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Layers, "Layers", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(40.dp)) {
                    val isVis = activeLayer.mask?.isEnabled != false
                    Icon(
                        imageVector = if (isVis) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Mask Visibility",
                        tint = if (isVis) Color.White else Color(0xFF888888),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onMoreOptions, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreHoriz, "More", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            // Three Main Mask Action Pills: [Edit mask], [Modify mask], [Invert]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LayerActionPill(
                    icon = Icons.Default.AutoFixNormal,
                    label = "Edit mask",
                    onClick = onEditMask
                )

                LayerActionPill(
                    icon = Icons.Default.Tune,
                    label = "Modify mask",
                    onClick = onOpenMask
                )

                LayerActionPill(
                    icon = Icons.Default.InvertColors,
                    label = "Invert mask",
                    onClick = onInvertMask
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Mask properties • Child of ${activeLayer.name}",
                color = Color(0xFF64B5F6),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        // =====================================================================
        // LAYER PROPERTIES BAR (When the image/adjustment layer itself is tapped)
        // =====================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Quick Action Icons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clipping Toggle Icon (Layer clipped / unclipped to layer below)
                IconButton(onClick = onToggleClipping, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.SubdirectoryArrowRight,
                        contentDescription = if (activeLayer.isClipped) "Layer clipped" else "Layer unclipped",
                        tint = if (activeLayer.isClipped) Color(0xFF64B5F6) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onFlipH, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Flip, "Flip", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onOpenLayers, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Layers, "Layers", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(40.dp)) {
                    val isVis = activeLayer.isVisible
                    Icon(
                        imageVector = if (isVis) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Visibility",
                        tint = if (isVis) Color.White else Color(0xFF888888),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onMoreOptions, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreHoriz, "More", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            // Main Action Pills: [Blend and opacity], [Create mask / Edit mask], [Transform]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LayerActionPill(
                    icon = Icons.Default.Grain,
                    label = "Blend and opacity",
                    onClick = onOpenBlend
                )

                if (activeLayer.mask != null && activeLayer.mask.isEnabled) {
                    LayerActionPill(
                        icon = Icons.Default.AutoFixNormal,
                        label = "Edit mask",
                        onClick = onEditMask
                    )
                    LayerActionPill(
                        icon = Icons.Default.Tune,
                        label = "Modify mask",
                        onClick = onOpenMask
                    )
                } else {
                    LayerActionPill(
                        icon = Icons.Default.AutoFixNormal,
                        label = "Create mask",
                        onClick = onOpenMask
                    )
                }

                LayerActionPill(
                    icon = Icons.Default.CropRotate,
                    label = "Transform",
                    onClick = onOpenTransform
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Layer properties",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LayerActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = Color(0xFFCCCCCC),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Layers List Panel (The "Layers" sheet).
 */
@Composable
private fun LayersListPanel(
    layers: List<EditorOverlayLayer>,
    activeLayerId: String?,
    onSelectLayer: (String) -> Unit,
    onClose: () -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onDuplicateLayer: (String) -> Unit,
    onMoveLayer: (String, Int) -> Unit,
    onRenameRequest: (EditorOverlayLayer) -> Unit,
    onOpenBlend: () -> Unit,
    onOpenMask: () -> Unit,
    onOpenTransform: () -> Unit
) {
    var contextMenuLayer by remember { mutableStateOf<EditorOverlayLayer?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .background(Color(0xFF555555), CircleShape)
        )

        // Header: "Layers" + Close X
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Layers",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Scrollable List of Layers (Stacked top to bottom)
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (layers.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1E1E),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2C2C2C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Photo, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Background Photo", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Original canvas • Tap + to add layers", color = Color(0xFF888888), fontSize = 11.sp)
                            }
                            Icon(Icons.Default.Lock, null, tint = Color(0xFF666666), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            } else {
                items(layers.reversed()) { layer ->
                    val isSelected = layer.id == activeLayerId

                    Surface(
                        onClick = { onSelectLayer(layer.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF262626) else Color(0xFF1A1A1A),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Dual thumbnail (if masked) or single thumbnail + Name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Thumbnail(s)
                                if (layer.mask != null && layer.mask.isEnabled) {
                                    // Dual thumbnail: [Layer | Mask]
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        LayerThumbnailIcon(layer = layer, size = 42.dp)
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black)
                                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoFixNormal,
                                                contentDescription = "Mask",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                } else {
                                    LayerThumbnailIcon(layer = layer, size = 42.dp)
                                }

                                Spacer(Modifier.width(12.dp))

                                Text(
                                    text = layer.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onRenameRequest(layer) }
                                )
                            }

                            // Right: More (...), Lock, Eye
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { contextMenuLayer = layer },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.MoreHoriz, "More options", tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onToggleLock(layer.id) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Lock",
                                        tint = if (layer.isLocked) Color.White else Color(0xFF666666),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleVisibility(layer.id) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Visibility",
                                        tint = if (layer.isVisible) Color.White else Color(0xFF666666),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (layers.isNotEmpty()) {
            // Bottom three pills inside Layers panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LayerActionPill(Icons.Default.Grain, "Blend and", onOpenBlend)
                LayerActionPill(Icons.Default.AutoFixNormal, "Create mask", onOpenMask)
                LayerActionPill(Icons.Default.CropRotate, "Transform", onOpenTransform)
            }
        } else {
            Text(
                text = "Tap + at bottom-right to add an image, adjustment, or text layer",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
    }

    // Context Menu Sheet for a Layer
    if (contextMenuLayer != null) {
        LayerContextMenuSheet(
            layer = contextMenuLayer!!,
            onDismiss = { contextMenuLayer = null },
            onRename = {
                val target = contextMenuLayer!!
                contextMenuLayer = null
                onRenameRequest(target)
            },
            onDelete = {
                val id = contextMenuLayer!!.id
                contextMenuLayer = null
                onDeleteLayer(id)
            },
            onDuplicate = {
                val id = contextMenuLayer!!.id
                contextMenuLayer = null
                onDuplicateLayer(id)
            },
            onToggleVisibility = {
                val id = contextMenuLayer!!.id
                contextMenuLayer = null
                onToggleVisibility(id)
            },
            onToggleLock = {
                val id = contextMenuLayer!!.id
                contextMenuLayer = null
                onToggleLock(id)
            }
        )
    }
}

/**
 * Thumbnail preview for each layer.
 */
@Composable
private fun LayerThumbnailIcon(
    layer: EditorOverlayLayer?,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222222))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (layer == null) {
            Icon(Icons.Default.Layers, null, tint = Color.White, modifier = Modifier.size(18.dp))
            return@Box
        }

        when (layer.type) {
            OverlayLayerType.IMAGE -> {
                if (!layer.stockImageKey.isNullOrEmpty()) {
                    val bmp = remember(layer.stockImageKey) {
                        StockPhotoAssets.getStockBitmap(layer.stockImageKey, 80, 80)
                    }
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = layer.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            OverlayLayerType.ADJUSTMENT -> {
                Icon(Icons.Default.Contrast, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            OverlayLayerType.FILL -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(layer.fillSolidColor)
                )
            }
            OverlayLayerType.TYPE -> {
                Icon(Icons.Default.TextFields, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            OverlayLayerType.EMPTY -> {
                Icon(Icons.Default.Layers, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Thumbnail preview for the attached child mask of a layer.
 * Displays high-contrast black/white silhouette to clearly indicate mask geometry.
 */
@Composable
private fun MaskThumbnailIcon(
    mask: LayerMask?,
    size: androidx.compose.ui.unit.Dp,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(8.dp)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val w = this.size.width
            val h = this.size.height
            if (mask?.hasSubjectMask == true) {
                drawOval(
                    color = if (mask.isInverted) Color.Black else Color.White,
                    topLeft = Offset(w * 0.15f, h * 0.12f),
                    size = Size(w * 0.7f, h * 0.76f)
                )
            } else {
                drawRect(
                    color = if (mask?.isInverted == true) Color.Black else Color.White,
                    topLeft = Offset(w * 0.1f, h * 0.1f),
                    size = Size(w * 0.8f, h * 0.8f)
                )
            }
        }
    }
}

/**
 * Hue & Saturation Adjustment Panel (Matching 02:54 - 03:12 of the screen recording).
 * Provides Hue spectrum slider (-180..+180), Saturation, Lightness, and Colorize switch.
 */
@Composable
private fun HueSaturationAdjustmentPanel(
    hue: Float,
    sat: Float,
    lightness: Float,
    isColorize: Boolean,
    onValuesChange: (Float, Float, Float, Boolean) -> Unit,
    onDone: () -> Unit
) {
    var curHue by remember(hue) { mutableFloatStateOf(hue) }
    var curSat by remember(sat) { mutableFloatStateOf(sat) }
    var curLightness by remember(lightness) { mutableFloatStateOf(lightness) }
    var colorize by remember(isColorize) { mutableStateOf(isColorize) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Hue slider with continuous spectrum track
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hue", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${curHue.toInt()}", color = Color(0xFFAAAAAA), fontSize = 12.sp)
        }
        Slider(
            value = curHue,
            onValueChange = {
                curHue = it
                onValuesChange(curHue, curSat, curLightness, colorize)
            },
            valueRange = -180f..180f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFFE91E63),
                inactiveTrackColor = Color(0xFF333333)
            )
        )

        Spacer(Modifier.height(4.dp))

        // Saturation slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Saturation", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${curSat.toInt()}", color = Color(0xFFAAAAAA), fontSize = 12.sp)
        }
        Slider(
            value = curSat,
            onValueChange = {
                curSat = it
                onValuesChange(curHue, curSat, curLightness, colorize)
            },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF333333)
            )
        )

        Spacer(Modifier.height(4.dp))

        // Lightness slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Lightness", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${curLightness.toInt()}", color = Color(0xFFAAAAAA), fontSize = 12.sp)
        }
        Slider(
            value = curLightness,
            onValueChange = {
                curLightness = it
                onValuesChange(curHue, curSat, curLightness, colorize)
            },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF333333)
            )
        )

        Spacer(Modifier.height(6.dp))

        // Colorize Switch + Done button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = colorize,
                    onCheckedChange = {
                        colorize = it
                        onValuesChange(curHue, curSat, curLightness, colorize)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text("Colorize", color = Color.White, fontSize = 13.sp)
            }

            Surface(
                onClick = onDone,
                shape = CircleShape,
                color = Color.White,
                contentColor = Color.Black,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, "Done", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Hue and Saturation",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Blend and Opacity panel (Matching 02:07 - 02:14 of video).
 */
@Composable
private fun BlendAndOpacityPanel(
    currentBlendMode: String,
    currentOpacity: Float,
    onBlendModeChange: (String) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    val blendModes = listOf(
        "Normal", "Dissolve", "Darken", "Multiply", "Color burn", "Linear burn",
        "Lighten", "Screen", "Color dodge", "Linear dodge (add)", "Overlay",
        "Soft light", "Hard light", "Difference", "Exclusion", "Hue",
        "Saturation", "Color", "Luminosity"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Blend Modes Horizontal Scroll
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(blendModes) { mode ->
                val isSelected = currentBlendMode.equals(mode, ignoreCase = true)
                Surface(
                    onClick = { onBlendModeChange(mode) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = mode,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Opacity Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Opacity", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("${(currentOpacity * 100).toInt()}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Slider(
            value = currentOpacity,
            onValueChange = onOpacityChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF333333)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Bottom Bar: "Blend and opacity" + Checkmark
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(36.dp))
            Text(
                text = "Blend and opacity",
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Surface(
                onClick = onDone,
                shape = CircleShape,
                color = Color.White,
                contentColor = Color.Black,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, "Done", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * Transform Control Panel (Scale & Rotate, Skew, Distort).
 */
@Composable
private fun TransformControlPanel(
    layer: EditorOverlayLayer?,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    var activeSubMode by remember { mutableStateOf("Scale & Rotate") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Quick Flip & Options Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onFlipH, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Flip, "Flip H", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onFlipV, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.SwapVert, "Flip V", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.RestartAlt, "Reset", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Sub-modes: Scale & Rotate, Skew, Distort + Checkmark
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                modifier = Modifier.height(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("Scale & Rotate", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF222222),
                modifier = Modifier.height(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("Skew", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF222222),
                modifier = Modifier.height(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("Distort", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Surface(
                onClick = onDone,
                shape = CircleShape,
                color = Color.White,
                contentColor = Color.Black,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, "Done", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Transform",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Select Area / Cutout Mask Panel (Matching 02:22 - 04:17 of video).
 */
@Composable
private fun SelectAreaMaskPanel(
    onApplySubjectMask: () -> Unit,
    onApplyInvertMask: () -> Unit,
    onCancel: () -> Unit
) {
    var selectedTool by remember { mutableStateOf("Subject") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Selection modifiers pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onApplyInvertMask, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.InvertColors, "Invert selection", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onApplySubjectMask, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.SelectAll, "Select all", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Clear, "Clear", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Tools Strip: Subject, Background, Tap select, Brush, Lasso
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Subject" button (One-tap AI Subject Cutout, matching video 02:22)
            Surface(
                onClick = onApplySubjectMask,
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.height(42.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Subject", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                onClick = onApplyInvertMask,
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF222222),
                modifier = Modifier.height(42.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wallpaper, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Background", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Surface(
                onClick = { selectedTool = "Tap select" },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF222222),
                modifier = Modifier.height(42.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TouchApp, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tap select", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Surface(
                onClick = { selectedTool = "Brush" },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF222222),
                modifier = Modifier.height(42.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Brush, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Quick select brush", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Action Buttons: "Apply as mask", "Delete area"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color(0xFFAAAAAA))
            }

            Button(
                onClick = onApplySubjectMask,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("apply_as_mask_button")
            ) {
                Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apply as mask", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Select area",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Modify Mask Panel with Custom Numeric Keypad (Matching 05:07 - 05:11 of video).
 */
@Composable
private fun ModifyMaskKeypadPanel(
    currentVal: Int,
    onValueApplied: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var displayVal by remember { mutableStateOf(currentVal.toString()) }
    var isNegative by remember { mutableStateOf(currentVal < 0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Top options strip: Expand/Contract, Feather, Smooth, Make border, Transform mask
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.OpenInFull, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text("Expand/Contract", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BlurOn, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                Text("Feather", color = Color(0xFF888888), fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Gesture, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                Text("Smooth", color = Color(0xFF888888), fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CropSquare, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                Text("Make border", color = Color(0xFF888888), fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Number readout and sign toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayVal,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = {
                        val num = displayVal.toIntOrNull() ?: 0
                        displayVal = (-num).toString()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2A2A2A),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("+/-", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }

                IconButton(
                    onClick = { displayVal = "0" },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, "Reset", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Keypad Grid: 1-9, 0, Backspace, Checkmark
        val keys = listOf(
            listOf("7", "8", "9"),
            listOf("4", "5", "6"),
            listOf("1", "2", "3"),
            listOf("0", "DEL", "OK")
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { k ->
                    Surface(
                        onClick = {
                            when (k) {
                                "DEL" -> {
                                    displayVal = if (displayVal.length > 1 && displayVal != "-") {
                                        displayVal.dropLast(1)
                                    } else "0"
                                }
                                "OK" -> {
                                    val finalVal = displayVal.toIntOrNull() ?: 0
                                    onValueApplied(finalVal)
                                }
                                else -> {
                                    displayVal = if (displayVal == "0") k else displayVal + k
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (k == "OK") Color.White else Color(0xFF222222),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (k == "DEL") {
                                Icon(Icons.Default.Backspace, "Delete", tint = Color.White, modifier = Modifier.size(18.dp))
                            } else if (k == "OK") {
                                Icon(Icons.Default.Check, "Apply", tint = Color.Black, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = k,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Black & White Adjustment Layer Channel Sliders Panel (Matching 07:12 - 08:06 of video).
 */
@Composable
private fun BlackAndWhiteAdjustmentPanel(
    settings: BlackAndWhiteSettings,
    onSettingsChange: (BlackAndWhiteSettings) -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .verticalScroll(rememberScrollState())
    ) {
        BwChannelSliderRow("Reds", settings.reds, Color(0xFFFF4D4D)) {
            onSettingsChange(settings.copy(reds = it))
        }
        BwChannelSliderRow("Yellows", settings.yellows, Color(0xFFFFD23F)) {
            onSettingsChange(settings.copy(yellows = it))
        }
        BwChannelSliderRow("Greens", settings.greens, Color(0xFF4CAF50)) {
            onSettingsChange(settings.copy(greens = it))
        }
        BwChannelSliderRow("Cyans", settings.cyans, Color(0xFF00E5FF)) {
            onSettingsChange(settings.copy(cyans = it))
        }
        BwChannelSliderRow("Blues", settings.blues, Color(0xFF448AFF)) {
            onSettingsChange(settings.copy(blues = it))
        }
        BwChannelSliderRow("Magentas", settings.magentas, Color(0xFFE040FB)) {
            onSettingsChange(settings.copy(magentas = it))
        }

        Spacer(Modifier.height(8.dp))

        // Bottom label & Done checkmark
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(36.dp))
            Text(
                text = "Black and white",
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Surface(
                onClick = onDone,
                shape = CircleShape,
                color = Color.White,
                contentColor = Color.Black,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, "Done", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun BwChannelSliderRow(
    label: String,
    value: Float,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.width(72.dp)
        )

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -200f..300f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color(0xFF333333)
            ),
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value.toInt().toString(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp)
        )
    }
}

/**
 * Add Image Bottom Sheet (Add from Photos, Add from Stock, Generate image, Take a photo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddImageBottomSheet(
    onDismiss: () -> Unit,
    onPickFromPhotos: () -> Unit,
    onSelectStock: (String, String) -> Unit,
    onGenerateImage: () -> Unit
) {
    var showStockGrid by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: "Add image" + Close X
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showStockGrid) "Select Stock Photo" else "Add image",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!showStockGrid) {
                AddImageOptionItem(
                    title = "Add from Photos",
                    subtitle = "Add an image from your camera roll",
                    icon = Icons.Default.PhotoLibrary,
                    onClick = onPickFromPhotos
                )
                AddImageOptionItem(
                    title = "Add from Stock",
                    subtitle = "Nebula, PropPlane, Astronaut, Whale presets",
                    icon = Icons.Default.Collections,
                    onClick = { showStockGrid = true }
                )
                AddImageOptionItem(
                    title = "Generate image",
                    subtitle = "Use a prompt to start with a generated image",
                    icon = Icons.Default.AutoAwesome,
                    onClick = onGenerateImage
                )
                AddImageOptionItem(
                    title = "Take a photo",
                    subtitle = "Capture a photo with your camera",
                    icon = Icons.Default.PhotoCamera,
                    onClick = onPickFromPhotos
                )
            } else {
                // Stock Grid matching video
                val stocks = listOf(
                    Triple("Nebula", "space", "Nebula"),
                    Triple("PropPlane", "propplane", "PropPlane"),
                    Triple("chista-astronaut", "astronaut", "Astronaut"),
                    Triple("Whale", "whale", "Whale"),
                    Triple("White canvas", "white_canvas", "White canvas")
                )

                stocks.forEach { (name, key, title) ->
                    Surface(
                        onClick = { onSelectStock(key, name) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF282828),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bmp = remember(key) {
                                StockPhotoAssets.getStockBitmap(key, 80, 80)
                            }
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = name,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(title, color = Color(0xFFAAAAAA), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AddImageOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF262626),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color(0xFFAAAAAA), fontSize = 12.sp)
            }
        }
    }
}

/**
 * Add Adjustment Layer Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAdjustmentBottomSheet(
    onDismiss: () -> Unit,
    onSelectType: (AdjustmentLayerType) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: "Add adjustment layer" + Close X
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add adjustment layer",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            AdjustmentLayerType.entries.forEach { type ->
                Surface(
                    onClick = { onSelectType(type) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF262626),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when (type) {
                            AdjustmentLayerType.HUE_SATURATION -> Icons.Default.Palette
                            AdjustmentLayerType.BRIGHTNESS_CONTRAST -> Icons.Default.Brightness6
                            AdjustmentLayerType.BLACK_AND_WHITE -> Icons.Default.Contrast
                            AdjustmentLayerType.COLOR_BALANCE -> Icons.Default.Tune
                            AdjustmentLayerType.EXPOSURE -> Icons.Default.WbSunny
                            AdjustmentLayerType.CURVES -> Icons.Default.ShowChart
                            AdjustmentLayerType.LEVELS -> Icons.Default.BarChart
                        }
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(type.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(type.subtitle, color = Color(0xFFAAAAAA), fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Generate Image Prompt Dialog.
 */
@Composable
private fun GenerateImagePromptDialog(
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit
) {
    var prompt by remember { mutableStateOf("Deep space cosmic nebula") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Generate Image Layer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Enter a prompt to create an image layer:", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF444444)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onGenerate(prompt.ifBlank { "Deep space nebula" }) }) {
                Text("Generate", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFAAAAAA))
            }
        }
    )
}

/**
 * Rename Layer Dialog.
 */
@Composable
private fun RenameLayerDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF222222),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Rename layer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = "" }) {
                            Icon(Icons.Default.Clear, "Clear", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color(0xFF444444)
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onRename(text.ifBlank { currentName }) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF), contentColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Rename", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        }
    )
}

/**
 * Layer Context Menu Sheet (... button on layer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayerContextMenuSheet(
    layer: EditorOverlayLayer,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ContextMenuItem("Select multiple", Icons.Default.SelectAll, onClick = onDismiss)
            ContextMenuItem("Rename", Icons.Default.Edit, onClick = onRename)
            ContextMenuItem(if (layer.isVisible) "Hide" else "Show", if (layer.isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, onClick = onToggleVisibility)
            ContextMenuItem("Delete", Icons.Default.Delete, isDestructive = true, onClick = onDelete)
            ContextMenuItem(if (layer.isLocked) "Unlock" else "Lock", if (layer.isLocked) Icons.Default.LockOpen else Icons.Default.Lock, onClick = onToggleLock)
            ContextMenuItem("Unclip layer", Icons.Default.LinkOff, onClick = onDismiss)
            ContextMenuItem("Copy", Icons.Default.ContentCopy, onClick = onDismiss)
            ContextMenuItem("Duplicate", Icons.Default.ControlPointDuplicate, onClick = onDuplicate)
            ContextMenuItem("Load as selection", Icons.Default.CropFree, onClick = onDismiss)
            ContextMenuItem("Convert to smart object", Icons.Default.SmartButton, onClick = onDismiss)
            ContextMenuItem("Merge down", Icons.Default.MergeType, onClick = onDismiss)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ContextMenuItem(
    label: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isDestructive) Color(0xFFFF5252) else Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                color = if (isDestructive) Color(0xFFFF5252) else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
