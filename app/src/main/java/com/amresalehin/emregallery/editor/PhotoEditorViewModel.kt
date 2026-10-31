package com.amresalehin.emregallery.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoEditorViewModel : ViewModel() {

    private val _sourceItem = MutableStateFlow<MediaItem?>(null)
    val sourceItem: StateFlow<MediaItem?> = _sourceItem.asStateFlow()

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _sessionState = MutableStateFlow(EditSessionState())
    val sessionState: StateFlow<EditSessionState> = _sessionState.asStateFlow()

    private val _activeTool = MutableStateFlow(EditorTool.CROP)
    val activeTool: StateFlow<EditorTool> = _activeTool.asStateFlow()

    private val _activeSubItem = MutableStateFlow<ToolSheetSubItem?>(ToolSheetSubItem.CROP)
    val activeSubItem: StateFlow<ToolSheetSubItem?> = _activeSubItem.asStateFlow()

    private val _activeAdjustment = MutableStateFlow("Brightness")
    val activeAdjustment: StateFlow<String> = _activeAdjustment.asStateFlow()

    // Undo / Redo history stacks
    private val undoStack = mutableListOf<EditSessionState>()
    private val redoStack = mutableListOf<EditSessionState>()

    // Baseline state before an ongoing slider drag/adjustment started
    private var sliderBaselineState: EditSessionState? = null

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingMessage = MutableStateFlow("")
    val processingMessage: StateFlow<String> = _processingMessage.asStateFlow()

    // Replay state
    private val _isReplaying = MutableStateFlow(false)
    val isReplaying: StateFlow<Boolean> = _isReplaying.asStateFlow()

    private val _replayProgress = MutableStateFlow(0f)
    val replayProgress: StateFlow<Float> = _replayProgress.asStateFlow()

    private var renderJob: kotlinx.coroutines.Job? = null

    fun initialize(context: Context, item: MediaItem) {
        if (_sourceItem.value?.uri == item.uri && _originalBitmap.value != null) return
        _sourceItem.value = item
        undoStack.clear()
        redoStack.clear()
        sliderBaselineState = null
        _canUndo.value = false
        _canRedo.value = false
        _sessionState.value = EditSessionState()

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Loading photo..."
            val bmp = PhotoProcessor.loadSampledBitmap(context, Uri.parse(item.uri), 1920)
            _originalBitmap.value = bmp
            _previewBitmap.value = bmp
            _isProcessing.value = false
            _processingMessage.value = ""
        }
    }

    private fun pushState(newState: EditSessionState) {
        commitSliderChange()
        undoStack.add(_sessionState.value)
        redoStack.clear()
        _sessionState.value = newState
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
        updateLivePreview()
    }

    fun commitSliderChange() {
        val baseline = sliderBaselineState ?: return
        sliderBaselineState = null
        if (baseline != _sessionState.value) {
            undoStack.add(baseline)
            redoStack.clear()
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = false
        }
    }

    fun undo() {
        commitSliderChange()
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(_sessionState.value)
            _sessionState.value = prev
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = true
            updateLivePreview()
        }
    }

    fun redo() {
        commitSliderChange()
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(_sessionState.value)
            _sessionState.value = next
            _canUndo.value = true
            _canRedo.value = redoStack.isNotEmpty()
            updateLivePreview()
        }
    }

    fun setActiveTool(tool: EditorTool) {
        commitSliderChange()
        _activeTool.value = tool
        _activeSubItem.value = null
    }

    fun setActiveSubItem(subItem: ToolSheetSubItem?) {
        commitSliderChange()
        _activeSubItem.value = subItem
    }

    fun setActiveAdjustment(prop: String) {
        commitSliderChange()
        _activeAdjustment.value = prop
    }

    // --- Transformations ---
    fun updateCropPreview(rect: RectF?) {
        _sessionState.value = _sessionState.value.copy(
            transform = _sessionState.value.transform.copy(cropRect = rect)
        )
        updateLivePreview()
    }

    fun setCrop(rect: RectF?) {
        commitCrop(rect)
    }

    fun commitCrop(rect: RectF?) {
        commitSliderChange()
        if (_sessionState.value.transform.cropRect != rect) {
            pushState(_sessionState.value.copy(
                transform = _sessionState.value.transform.copy(cropRect = rect),
                description = "Crop photo"
            ))
        }
    }

    fun rotate90() {
        val current = _sessionState.value.transform.rotationDegrees
        val next = (current + 90) % 360
        pushState(_sessionState.value.copy(
            transform = _sessionState.value.transform.copy(rotationDegrees = next),
            description = "Rotate 90°"
        ))
    }

    fun flipHorizontal() {
        val current = _sessionState.value.transform.flipHorizontal
        pushState(_sessionState.value.copy(
            transform = _sessionState.value.transform.copy(flipHorizontal = !current),
            description = "Flip horizontal"
        ))
    }

    fun flipVertical() {
        val current = _sessionState.value.transform.flipVertical
        pushState(_sessionState.value.copy(
            transform = _sessionState.value.transform.copy(flipVertical = !current),
            description = "Flip vertical"
        ))
    }

    fun setStraighten(angle: Float) {
        if (sliderBaselineState == null) {
            sliderBaselineState = _sessionState.value
        }
        _sessionState.value = _sessionState.value.copy(
            transform = _sessionState.value.transform.copy(straightenAngle = angle)
        )
        updateLivePreview()
    }

    fun commitStraighten(angle: Float) {
        commitSliderChange()
    }

    fun setPerspective(x: Float, y: Float) {
        _sessionState.value = _sessionState.value.copy(
            transform = _sessionState.value.transform.copy(perspectiveX = x, perspectiveY = y)
        )
        updateLivePreview()
    }

    // --- Adjustments ---
    fun updateAdjustmentValue(prop: String, value: Float) {
        if (sliderBaselineState == null) {
            sliderBaselineState = _sessionState.value
        }
        val cur = _sessionState.value.adjustments
        val updated = when (prop) {
            "Brightness" -> cur.copy(brightness = value)
            "Contrast" -> cur.copy(contrast = value)
            "Saturation" -> cur.copy(saturation = value)
            "Exposure" -> cur.copy(exposure = value)
            "Highlights" -> cur.copy(highlights = value)
            "Shadows" -> cur.copy(shadows = value)
            "Temperature" -> cur.copy(temperature = value)
            "Tint" -> cur.copy(tint = value)
            "Sharpness" -> cur.copy(sharpness = value)
            "Blur" -> cur.copy(blur = value)
            "Vignette" -> cur.copy(vignette = value)
            "Grain" -> cur.copy(grain = value)
            else -> cur
        }
        _sessionState.value = _sessionState.value.copy(adjustments = updated)
        updateLivePreview()
    }

    fun commitAdjustmentValue(prop: String, value: Float) {
        commitSliderChange()
    }

    // --- Filters ---
    fun setFilter(filter: FilterType, intensity: Float = 1.0f) {
        commitSliderChange()
        if (_sessionState.value.filter != filter) {
            pushState(_sessionState.value.copy(
                filter = filter,
                filterIntensity = intensity,
                description = "Filter ${filter.displayName}"
            ))
        }
    }

    fun updateFilterIntensity(intensity: Float) {
        if (sliderBaselineState == null) {
            sliderBaselineState = _sessionState.value
        }
        _sessionState.value = _sessionState.value.copy(
            filterIntensity = intensity
        )
        updateLivePreview()
    }

    // --- Retouch ---
    fun addRetouchStroke(stroke: RetouchStroke) {
        val updated = _sessionState.value.retouchStrokes + stroke
        pushState(_sessionState.value.copy(
            retouchStrokes = updated,
            description = stroke.type.label
        ))
    }

    // --- Background & Cutout ---
    fun setBackgroundMode(mode: BackgroundMode) {
        pushState(_sessionState.value.copy(
            background = _sessionState.value.background.copy(mode = mode),
            description = "Background ${mode.label}"
        ))
    }

    fun setBackgroundBlur(radius: Float) {
        if (sliderBaselineState == null) {
            sliderBaselineState = _sessionState.value
        }
        _sessionState.value = _sessionState.value.copy(
            background = _sessionState.value.background.copy(blurRadius = radius)
        )
        updateLivePreview()
    }

    fun setBackgroundSolidColor(color: Color) {
        pushState(_sessionState.value.copy(
            background = _sessionState.value.background.copy(mode = BackgroundMode.SOLID, solidColor = color),
            description = "Solid background"
        ))
    }

    fun setBackgroundGradient(colors: List<Color>) {
        pushState(_sessionState.value.copy(
            background = _sessionState.value.background.copy(mode = BackgroundMode.GRADIENT, gradientColors = colors),
            description = "Gradient background"
        ))
    }

    // --- Drawing & Brushes ---
    fun addDrawingStroke(stroke: DrawingStroke) {
        val list = _sessionState.value.drawingStrokes + stroke
        pushState(_sessionState.value.copy(drawingStrokes = list, description = "Brush stroke"))
    }

    // --- Text Overlays ---
    fun addTextOverlay(text: String, color: Color = Color.White, bg: Color = Color.Black.copy(alpha = 0.5f)) {
        val item = TextOverlayItem(
            id = System.currentTimeMillis().toString(),
            text = text,
            color = color,
            backgroundColor = bg
        )
        pushState(_sessionState.value.copy(textOverlays = _sessionState.value.textOverlays + item, description = "Add text"))
    }

    fun updateTextOverlay(updated: TextOverlayItem) {
        val list = _sessionState.value.textOverlays.map { if (it.id == updated.id) updated else it }
        _sessionState.value = _sessionState.value.copy(textOverlays = list)
        updateLivePreview()
    }

    fun removeTextOverlay(id: String) {
        val list = _sessionState.value.textOverlays.filterNot { it.id == id }
        pushState(_sessionState.value.copy(textOverlays = list, description = "Remove text"))
    }

    // --- Stickers ---
    fun addSticker(emoji: String) {
        val item = StickerItem(
            id = System.currentTimeMillis().toString(),
            emojiOrRes = emoji
        )
        pushState(_sessionState.value.copy(stickers = _sessionState.value.stickers + item, description = "Add sticker"))
    }

    fun updateSticker(updated: StickerItem) {
        val list = _sessionState.value.stickers.map { if (it.id == updated.id) updated else it }
        _sessionState.value = _sessionState.value.copy(stickers = list)
        updateLivePreview()
    }

    // --- Shapes ---
    fun addShape(shapeType: ShapeType, strokeColor: Color = Color.White, fillColor: Color = Color.Transparent, isMask: Boolean = false) {
        val item = ShapeItem(
            id = System.currentTimeMillis().toString(),
            shapeType = shapeType,
            strokeColor = strokeColor,
            fillColor = fillColor,
            isMask = isMask
        )
        pushState(_sessionState.value.copy(shapes = _sessionState.value.shapes + item, description = "Add shape"))
    }

    // --- Frames & Borders ---
    fun setFrame(frame: FrameStyle) {
        pushState(_sessionState.value.copy(frame = frame, description = "Frame ${frame.label}"))
    }

    fun setBorder(border: BorderSettings) {
        pushState(_sessionState.value.copy(border = border, description = "Border"))
    }

    // --- Callouts ---
    fun addCallout(text: String, isThought: Boolean = false) {
        val item = CalloutItem(
            id = System.currentTimeMillis().toString(),
            text = text,
            isThoughtBubble = isThought
        )
        pushState(_sessionState.value.copy(callouts = _sessionState.value.callouts + item, description = "Add callout"))
    }

    // --- Lens Flare ---
    fun addLensFlare(flareType: LensFlareType) {
        val item = LensFlareItem(
            id = System.currentTimeMillis().toString(),
            flareType = flareType
        )
        pushState(_sessionState.value.copy(lensFlares = _sessionState.value.lensFlares + item, description = "Add flare"))
    }

    // --- AI Tools ---
    fun toggleAiAutoEnhance(enabled: Boolean) {
        val updated = _sessionState.value.aiSettings.copy(autoEnhanceEnabled = enabled)
        pushState(_sessionState.value.copy(aiSettings = updated, description = "AI Auto Enhance"))
    }

    fun setAiStyleTransfer(style: AiStyleTransfer?) {
        val updated = _sessionState.value.aiSettings.copy(activeStyleTransfer = style)
        pushState(_sessionState.value.copy(aiSettings = updated, description = style?.label ?: "Remove style"))
    }

    fun setAiTryOn(style: AiTryOnStyle?) {
        val updated = _sessionState.value.aiSettings.copy(activeTryOn = style)
        pushState(_sessionState.value.copy(aiSettings = updated, description = style?.label ?: "Remove attire"))
    }

    fun setAiExpand(ratio: Float) {
        val updated = _sessionState.value.aiSettings.copy(expandOutpaintRatio = ratio)
        pushState(_sessionState.value.copy(aiSettings = updated, description = "AI Expand ${(ratio * 100).toInt()}%"))
    }

    fun updateAiExpand(ratio: Float) {
        if (sliderBaselineState == null) {
            sliderBaselineState = _sessionState.value
        }
        val updated = _sessionState.value.aiSettings.copy(expandOutpaintRatio = ratio)
        _sessionState.value = _sessionState.value.copy(aiSettings = updated)
        updateLivePreview()
    }

    private fun updateLivePreview() {
        val original = _originalBitmap.value ?: return
        val currentSnapshot = _sessionState.value
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val preview = PhotoProcessor.renderFinal(original, currentSnapshot)
            withContext(Dispatchers.Main) {
                _previewBitmap.value = preview
            }
        }
    }

    /**
     * Cinematic step-by-step replay of editing history.
     */
    fun startReplay() {
        if (undoStack.isEmpty()) return
        viewModelScope.launch {
            _isReplaying.value = true
            val fullHistory = undoStack + listOf(_sessionState.value)
            val total = fullHistory.size
            for ((idx, step) in fullHistory.withIndex()) {
                _sessionState.value = step
                _replayProgress.value = (idx + 1).toFloat() / total
                updateLivePreview()
                delay(650)
            }
            delay(400)
            _isReplaying.value = false
            _replayProgress.value = 1f
        }
    }

    /**
     * Saves the final rendered photo to MediaStore.
     */
    suspend fun saveResult(
        context: Context,
        mediaRepository: MediaRepository,
        isPng: Boolean = false,
        quality: Int = 95
    ): MediaItem? = withContext(Dispatchers.Default) {
        _isProcessing.value = true
        _processingMessage.value = "Rendering photo at high resolution..."
        val original = _originalBitmap.value ?: return@withContext null
        val finalBitmap = PhotoProcessor.renderFinal(original, _sessionState.value)

        _processingMessage.value = "Saving to gallery..."
        val baseName = _sourceItem.value?.name?.substringBeforeLast('.') ?: "IMG_EDITED"
        val saved = mediaRepository.saveEditedBitmap(finalBitmap, isPng, quality, baseName)
        _isProcessing.value = false
        _processingMessage.value = ""
        saved
    }
}
