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

    private val _thumbnailBitmap = MutableStateFlow<Bitmap?>(null)
    val thumbnailBitmap: StateFlow<Bitmap?> = _thumbnailBitmap.asStateFlow()

    private val _sessionState = MutableStateFlow(EditSessionState())
    val sessionState: StateFlow<EditSessionState> = _sessionState.asStateFlow()

    private val _activeTool = MutableStateFlow(EditorTool.CROP)
    val activeTool: StateFlow<EditorTool> = _activeTool.asStateFlow()

    private val _currentMode = MutableStateFlow(EditorMode.NONE)
    val currentMode: StateFlow<EditorMode> = _currentMode.asStateFlow()

    private var modeBaselineState: EditSessionState? = null

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
            if (bmp != null) {
                try {
                    val targetSize = 120
                    val minDim = minOf(bmp.width, bmp.height)
                    val startX = (bmp.width - minDim) / 2
                    val startY = (bmp.height - minDim) / 2
                    val square = Bitmap.createBitmap(bmp, startX, startY, minDim, minDim)
                    _thumbnailBitmap.value = Bitmap.createScaledBitmap(square, targetSize, targetSize, true)
                } catch (_: Exception) {
                    _thumbnailBitmap.value = null
                }
            }
            _isProcessing.value = false
            _processingMessage.value = ""
        }
    }

    fun setThumbnailBitmapForTesting(bitmap: Bitmap?) {
        _thumbnailBitmap.value = bitmap
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

    // --- Mode Lifecycle (Cancel / Apply Behavior) ---
    fun enterMode(mode: EditorMode) {
        commitSliderChange()
        modeBaselineState = _sessionState.value
        _currentMode.value = mode
        when (mode) {
            EditorMode.CROP -> _activeTool.value = EditorTool.CROP
            EditorMode.ADJUST -> _activeTool.value = EditorTool.ADJUST
            EditorMode.CURVES -> _activeTool.value = EditorTool.CURVES
            EditorMode.COLOR_MIX -> _activeTool.value = EditorTool.ADJUST
            EditorMode.COLOR_GRADING -> _activeTool.value = EditorTool.ADJUST
            EditorMode.BRUSHES -> _activeTool.value = EditorTool.BRUSHES
            EditorMode.RETOUCH -> _activeTool.value = EditorTool.RETOUCH
            EditorMode.PRESETS -> _activeTool.value = EditorTool.EFFECTS
            EditorMode.NONE -> _activeTool.value = EditorTool.ADJUST
        }
    }

    fun applyModeChanges(description: String = "") {
        commitSliderChange()
        val baseline = modeBaselineState
        modeBaselineState = null
        val current = _sessionState.value
        if (baseline != null && baseline != current) {
            undoStack.add(baseline)
            redoStack.clear()
            _canUndo.value = true
            _canRedo.value = false
            val desc = if (description.isNotEmpty()) description else "${_currentMode.value.displayName} applied"
            _sessionState.value = current.copy(description = desc)
        }
        _currentMode.value = EditorMode.NONE
        _activeTool.value = EditorTool.ADJUST
    }

    fun cancelModeChanges() {
        sliderBaselineState = null
        val baseline = modeBaselineState
        modeBaselineState = null
        if (baseline != null) {
            _sessionState.value = baseline
            updateLivePreview()
        }
        _currentMode.value = EditorMode.NONE
        _activeTool.value = EditorTool.ADJUST
    }

    fun hasModeChanges(): Boolean {
        val baseline = modeBaselineState ?: return false
        return baseline != _sessionState.value
    }

    fun resetCrop() {
        _sessionState.value = _sessionState.value.copy(
            transform = _sessionState.value.transform.copy(
                cropRect = null,
                straightenAngle = 0f,
                rotationDegrees = 0,
                flipHorizontal = false,
                flipVertical = false
            )
        )
        updateLivePreview()
    }

    fun resetCurves() {
        val defaultPts = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
        val emptyCurves = RgbCurves(
            master = defaultPts,
            red = defaultPts,
            green = defaultPts,
            blue = defaultPts
        )
        _sessionState.value = _sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(curves = emptyCurves)
        )
        updateLivePreview()
    }

    fun resetColorMix() {
        _sessionState.value = _sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(hslAdjustments = emptyMap())
        )
        updateLivePreview()
    }

    fun resetColorBand(band: ColorBand) {
        val updated = _sessionState.value.adjustments.hslAdjustments.toMutableMap()
        updated.remove(band)
        _sessionState.value = _sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(hslAdjustments = updated)
        )
        updateLivePreview()
    }

    fun resetAdjustments() {
        _sessionState.value = _sessionState.value.copy(
            adjustments = AdjustmentParams()
        )
        updateLivePreview()
    }

    fun clearDrawingStrokes() {
        _sessionState.value = _sessionState.value.copy(drawingStrokes = emptyList())
        updateLivePreview()
    }

    fun clearRetouchStrokes() {
        _sessionState.value = _sessionState.value.copy(retouchStrokes = emptyList())
        updateLivePreview()
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

    fun setShapeCrop(shape: ShapeCropType?) {
        pushState(_sessionState.value.copy(
            transform = _sessionState.value.transform.copy(shapeCrop = shape),
            description = "Shape Crop ${shape?.label ?: "None"}"
        ))
    }

    fun setCornerPins(pins: List<CornerPinPoint>?) {
        pushState(_sessionState.value.copy(
            transform = _sessionState.value.transform.copy(cornerPins = pins),
            description = "Corner Pin Perspective"
        ))
    }

    fun addLiquifyPoint(point: LiquifyPoint) {
        val list = _sessionState.value.transform.liquifyPoints + point
        pushState(_sessionState.value.copy(
            transform = _sessionState.value.transform.copy(liquifyPoints = list),
            description = "Liquify ${point.mode.label}"
        ))
    }

    fun setDispersion(amount: Float) {
        pushState(_sessionState.value.copy(
            transform = _sessionState.value.transform.copy(dispersionAmount = amount),
            description = "Dispersion"
        ))
    }

    fun setMotion(amount: Float, angle: Float) {
        pushState(_sessionState.value.copy(
            transform = _sessionState.value.transform.copy(motionAmount = amount, motionAngle = angle),
            description = "Motion Blur"
        ))
    }

    fun setHslAdjustment(band: ColorBand, adj: HslBandAdjustment) {
        val updatedMap = _sessionState.value.adjustments.hslAdjustments.toMutableMap()
        updatedMap[band] = adj
        pushState(_sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(hslAdjustments = updatedMap),
            description = "HSL ${band.label}"
        ))
    }

    fun setCurves(curves: RgbCurves) {
        pushState(_sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(curves = curves),
            description = "RGB Curves"
        ))
    }

    fun setLevels(levels: LevelsParams) {
        pushState(_sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(levels = levels),
            description = "Levels"
        ))
    }

    fun setColorBalance(balance: ColorBalanceParams) {
        pushState(_sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(colorBalance = balance),
            description = "Color Balance"
        ))
    }

    fun setSplitToning(split: SplitToningParams) {
        pushState(_sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(splitToning = split),
            description = "Split Toning"
        ))
    }

    fun setGradientMap(gradMap: GradientMapType) {
        pushState(_sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(gradientMap = gradMap),
            description = "Gradient Map ${gradMap.label}"
        ))
    }

    fun setDuotone(duotone: DuotoneParams) {
        pushState(_sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(duotone = duotone),
            description = "Duotone"
        ))
    }

    fun setColorReplace(replace: ColorReplaceParams) {
        pushState(_sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(colorReplace = replace),
            description = "Color Replace"
        ))
    }

    fun setColorSplash(splash: ColorSplashParams) {
        pushState(_sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(colorSplash = splash),
            description = "Color Splash"
        ))
    }

    fun setBackgroundSettings(settings: BackgroundSettings) {
        pushState(_sessionState.value.copy(
            background = settings,
            description = "Cutout & Background"
        ))
    }

    // Copy / Paste Settings cache
    private var copiedSettingsState: EditSessionState? = null

    fun copySettings(): Boolean {
        copiedSettingsState = _sessionState.value
        return true
    }

    fun hasCopiedSettings(): Boolean = copiedSettingsState != null

    fun pasteSettings(): Boolean {
        val copied = copiedSettingsState ?: return false
        pushState(copied.copy(description = "Paste settings"))
        return true
    }

    fun revertToOriginal() {
        pushState(EditSessionState(description = "Revert to original"))
    }

    fun applyAutoTone() {
        // Disabled per user instruction
    }

    fun toggleBlackAndWhite() {
        val cur = _sessionState.value.adjustments
        val isBw = !cur.isBlackAndWhite
        pushState(_sessionState.value.copy(
            adjustments = cur.copy(isBlackAndWhite = isBw),
            description = if (isBw) "Black & White" else "Color"
        ))
    }

    fun setWhiteBalancePreset(preset: String) {
        val kelvin = when (preset) {
            "Daylight" -> 5500f
            "Cloudy" -> 6500f
            "Shade" -> 7500f
            "Tungsten" -> 3200f
            "Fluorescent" -> 4000f
            "Flash" -> 5500f
            "Custom" -> _sessionState.value.adjustments.whiteBalanceKelvin
            else -> 5500f // "As Shot"
        }
        val temp = when (preset) {
            "Daylight" -> 0f
            "Cloudy" -> 15f
            "Shade" -> 25f
            "Tungsten" -> -30f
            "Fluorescent" -> -10f
            "Flash" -> 5f
            "Custom" -> _sessionState.value.adjustments.temperature
            else -> 0f
        }
        val cur = _sessionState.value.adjustments
        pushState(_sessionState.value.copy(
            adjustments = cur.copy(
                whiteBalancePreset = preset,
                whiteBalanceKelvin = kelvin,
                temperature = temp
            ),
            description = "WB: $preset"
        ))
    }

    fun setRemoveChromaticAberration(enabled: Boolean) {
        val cur = _sessionState.value.adjustments
        pushState(_sessionState.value.copy(
            adjustments = cur.copy(removeChromaticAberration = enabled),
            description = "Chromatic Aberration"
        ))
    }

    fun setEnableLensCorrections(enabled: Boolean) {
        val cur = _sessionState.value.adjustments
        pushState(_sessionState.value.copy(
            adjustments = cur.copy(enableLensCorrections = enabled),
            description = "Lens Corrections"
        ))
    }

    // --- Curve Manipulation ---
    fun updateCurvePoint(channel: CurveChannel, index: Int, inVal: Float, outVal: Float) {
        val cur = _sessionState.value.adjustments.curves
        val pts = when (channel) {
            CurveChannel.RGB -> cur.master.toMutableList()
            CurveChannel.RED -> cur.red.toMutableList()
            CurveChannel.GREEN -> cur.green.toMutableList()
            CurveChannel.BLUE -> cur.blue.toMutableList()
        }
        if (index in pts.indices) {
            pts[index] = CurvePoint(inVal.coerceIn(0f, 1f), outVal.coerceIn(0f, 1f))
            pts.sortBy { it.inVal }
            val updatedCurves = when (channel) {
                CurveChannel.RGB -> cur.copy(master = pts)
                CurveChannel.RED -> cur.copy(red = pts)
                CurveChannel.GREEN -> cur.copy(green = pts)
                CurveChannel.BLUE -> cur.copy(blue = pts)
            }
            _sessionState.value = _sessionState.value.copy(
                adjustments = _sessionState.value.adjustments.copy(curves = updatedCurves)
            )
            updateLivePreview()
        }
    }

    fun addCurvePoint(channel: CurveChannel, inVal: Float, outVal: Float) {
        val cur = _sessionState.value.adjustments.curves
        val pts = when (channel) {
            CurveChannel.RGB -> cur.master.toMutableList()
            CurveChannel.RED -> cur.red.toMutableList()
            CurveChannel.GREEN -> cur.green.toMutableList()
            CurveChannel.BLUE -> cur.blue.toMutableList()
        }
        pts.add(CurvePoint(inVal.coerceIn(0f, 1f), outVal.coerceIn(0f, 1f)))
        pts.sortBy { it.inVal }
        val updatedCurves = when (channel) {
            CurveChannel.RGB -> cur.copy(master = pts)
            CurveChannel.RED -> cur.copy(red = pts)
            CurveChannel.GREEN -> cur.copy(green = pts)
            CurveChannel.BLUE -> cur.copy(blue = pts)
        }
        pushState(_sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(curves = updatedCurves),
            description = "Curve point"
        ))
    }

    fun resetCurve(channel: CurveChannel) {
        val cur = _sessionState.value.adjustments.curves
        val defaultPts = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
        val updatedCurves = when (channel) {
            CurveChannel.RGB -> cur.copy(master = defaultPts)
            CurveChannel.RED -> cur.copy(red = defaultPts)
            CurveChannel.GREEN -> cur.copy(green = defaultPts)
            CurveChannel.BLUE -> cur.copy(blue = defaultPts)
        }
        pushState(_sessionState.value.copy(
            adjustments = _sessionState.value.adjustments.copy(curves = updatedCurves),
            description = "Reset curve"
        ))
    }

    // --- Profile Presets ---
    fun applyProfile(profile: ProfilePreset) {
        commitSliderChange()
        val cur = _sessionState.value.adjustments
        val updated = cur.copy(
            exposure = profile.exposureOffset,
            contrast = profile.contrastOffset,
            brightness = profile.brightnessOffset,
            temperature = profile.tempOffset,
            tint = profile.tintOffset,
            vibrance = profile.vibranceOffset,
            saturation = profile.saturationOffset
        )
        val intensity = if (profile.fadeOffset > 0f) (100f - profile.fadeOffset) / 100f else 1.0f
        pushState(_sessionState.value.copy(
            adjustments = updated,
            filter = profile.filterType,
            filterIntensity = intensity,
            filterBlendMode = profile.blendMode,
            description = "Preset ${profile.name}"
        ))
    }

    fun updatePresetIntensity(intensity: Float) {
        _sessionState.value = _sessionState.value.copy(
            filterIntensity = intensity.coerceIn(0f, 1f)
        )
        updateLivePreview()
    }

    fun updatePresetBlendMode(blendMode: String) {
        _sessionState.value = _sessionState.value.copy(
            filterBlendMode = blendMode
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
            "Whites" -> cur.copy(whites = value)
            "Blacks" -> cur.copy(blacks = value)
            "Temperature", "Temp" -> cur.copy(temperature = value)
            "Tint" -> cur.copy(tint = value)
            "Texture" -> cur.copy(texture = value)
            "Clarity" -> cur.copy(clarity = value)
            "Sharpness", "Sharpening", "Amount" -> cur.copy(sharpness = value)
            "Radius" -> cur.copy(sharpnessRadius = value)
            "Detail" -> cur.copy(sharpnessDetail = value)
            "Noise Reduction", "Noise" -> cur.copy(noiseReduction = value)
            "Color Noise Reduction", "Color noise" -> cur.copy(colorNoiseReduction = value)
            "Blur" -> cur.copy(blur = value)
            "Vignette" -> cur.copy(vignette = value)
            "Grain" -> cur.copy(grain = value)
            "Vibrance" -> cur.copy(vibrance = value)
            "Dehaze" -> cur.copy(dehaze = value)
            "Dodge" -> cur.copy(dodge = value)
            "Burn" -> cur.copy(burn = value)
            "Distortion Correction" -> cur.copy(distortionCorrection = value)
            "Vignetting Correction" -> cur.copy(vignettingCorrection = value)
            "White Balance" -> cur.copy(whiteBalanceKelvin = value)
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
    fun addTextOverlay(item: TextOverlayItem) {
        pushState(_sessionState.value.copy(textOverlays = _sessionState.value.textOverlays + item, description = "Add text"))
    }

    fun addTextOverlay(text: String, color: Color = Color.White, bg: Color = Color.Black.copy(alpha = 0.5f)) {
        val item = TextOverlayItem(
            id = System.currentTimeMillis().toString(),
            text = text,
            color = color,
            backgroundColor = bg
        )
        addTextOverlay(item)
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

    fun duplicateTextOverlay(id: String) {
        val existing = _sessionState.value.textOverlays.firstOrNull { it.id == id } ?: return
        val dup = existing.copy(
            id = System.currentTimeMillis().toString(),
            xRatio = (existing.xRatio + 0.05f).coerceIn(0.1f, 0.9f),
            yRatio = (existing.yRatio + 0.05f).coerceIn(0.1f, 0.9f)
        )
        pushState(_sessionState.value.copy(textOverlays = _sessionState.value.textOverlays + dup, description = "Duplicate text"))
    }

    // --- Stickers ---
    fun addSticker(item: StickerItem) {
        pushState(_sessionState.value.copy(stickers = _sessionState.value.stickers + item, description = "Add sticker"))
    }

    fun addSticker(emoji: String) {
        val item = StickerItem(
            id = System.currentTimeMillis().toString(),
            emojiOrRes = emoji
        )
        addSticker(item)
    }

    fun updateSticker(updated: StickerItem) {
        val list = _sessionState.value.stickers.map { if (it.id == updated.id) updated else it }
        _sessionState.value = _sessionState.value.copy(stickers = list)
        updateLivePreview()
    }

    fun removeSticker(id: String) {
        val list = _sessionState.value.stickers.filterNot { it.id == id }
        pushState(_sessionState.value.copy(stickers = list, description = "Remove sticker"))
    }

    fun duplicateSticker(id: String) {
        val existing = _sessionState.value.stickers.firstOrNull { it.id == id } ?: return
        val dup = existing.copy(
            id = System.currentTimeMillis().toString(),
            xRatio = (existing.xRatio + 0.05f).coerceIn(0.1f, 0.9f),
            yRatio = (existing.yRatio + 0.05f).coerceIn(0.1f, 0.9f)
        )
        pushState(_sessionState.value.copy(stickers = _sessionState.value.stickers + dup, description = "Duplicate sticker"))
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

    fun updateShape(updated: ShapeItem) {
        val list = _sessionState.value.shapes.map { if (it.id == updated.id) updated else it }
        _sessionState.value = _sessionState.value.copy(shapes = list)
        updateLivePreview()
    }

    fun removeShape(id: String) {
        val list = _sessionState.value.shapes.filterNot { it.id == id }
        pushState(_sessionState.value.copy(shapes = list, description = "Remove shape"))
    }

    fun duplicateShape(id: String) {
        val existing = _sessionState.value.shapes.firstOrNull { it.id == id } ?: return
        val dup = existing.copy(
            id = System.currentTimeMillis().toString(),
            xRatio = (existing.xRatio + 0.05f).coerceIn(0.1f, 0.9f),
            yRatio = (existing.yRatio + 0.05f).coerceIn(0.1f, 0.9f)
        )
        pushState(_sessionState.value.copy(shapes = _sessionState.value.shapes + dup, description = "Duplicate shape"))
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

    fun addCalloutWithStyle(text: String, style: CalloutStyle) {
        val item = CalloutItem(
            id = System.currentTimeMillis().toString(),
            text = text,
            style = style,
            isThoughtBubble = style == CalloutStyle.THOUGHT_BUBBLE || style == CalloutStyle.CLOUD_BUBBLE
        )
        pushState(_sessionState.value.copy(callouts = _sessionState.value.callouts + item, description = "Add callout"))
    }

    fun updateCallout(updated: CalloutItem) {
        val list = _sessionState.value.callouts.map { if (it.id == updated.id) updated else it }
        _sessionState.value = _sessionState.value.copy(callouts = list)
        updateLivePreview()
    }

    fun removeCallout(id: String) {
        val list = _sessionState.value.callouts.filterNot { it.id == id }
        pushState(_sessionState.value.copy(callouts = list, description = "Remove callout"))
    }

    // --- Lens Flare ---
    fun addLensFlare(flareType: LensFlareType) {
        val item = LensFlareItem(
            id = System.currentTimeMillis().toString(),
            flareType = flareType
        )
        pushState(_sessionState.value.copy(lensFlares = _sessionState.value.lensFlares + item, description = "Add flare"))
    }

    fun updateLensFlare(updated: LensFlareItem) {
        val list = _sessionState.value.lensFlares.map { if (it.id == updated.id) updated else it }
        _sessionState.value = _sessionState.value.copy(lensFlares = list)
        updateLivePreview()
    }

    fun removeLensFlare(id: String) {
        val list = _sessionState.value.lensFlares.filterNot { it.id == id }
        pushState(_sessionState.value.copy(lensFlares = list, description = "Remove flare"))
    }

    // --- Overlay Deletion Aliases ---
    fun deleteTextOverlay(id: String) = removeTextOverlay(id)
    fun deleteSticker(id: String) = removeSticker(id)
    fun deleteShape(id: String) = removeShape(id)
    fun deleteCallout(id: String) = removeCallout(id)
    fun deleteLensFlare(id: String) = removeLensFlare(id)

    // --- Transform & Liquify Helpers ---
    fun clearLiquify() = clearLiquifyPoints()
    fun clearLiquifyPoints() {
        pushState(_sessionState.value.copy(
            transform = _sessionState.value.transform.copy(liquifyPoints = emptyList()),
            description = "Clear Liquify"
        ))
    }

    fun setMotionBlur(amount: Float, angle: Float) = setMotion(amount, angle)

    // --- Masking Helpers ---
    fun invertMask() {
        val activeMask = _sessionState.value.maskLayers.firstOrNull { it.id == _sessionState.value.activeMaskId }
        if (activeMask != null) {
            invertMaskLayer(activeMask.id)
            return
        }
        val strokes = _sessionState.value.drawingStrokes
        if (strokes.isEmpty()) return
        pushState(_sessionState.value.copy(
            drawingStrokes = strokes.map { s ->
                s.copy(color = if (s.color.alpha > 0.5f) s.color.copy(alpha = 0.2f) else s.color.copy(alpha = 1.0f))
            },
            description = "Invert Mask"
        ))
        updateLivePreview()
    }

    fun clearMask() {
        val activeMaskId = _sessionState.value.activeMaskId
        if (activeMaskId != null) {
            deleteMaskLayer(activeMaskId)
        }
        clearDrawingStrokes()
    }

    // --- LightCraft Masking Layer Management ---
    fun createMaskLayer(initialType: MaskComponentType = MaskComponentType.BRUSH, name: String? = null): LightcraftMaskLayer {
        val existing = _sessionState.value.maskLayers
        val layerNum = existing.size + 1
        val layerName = name ?: "Mask $layerNum"
        val layerId = java.util.UUID.randomUUID().toString()
        val initialComp = MaskComponent(
            id = "${layerId}_c1",
            type = initialType,
            combineMode = MaskCombineMode.ADD
        )
        val newLayer = LightcraftMaskLayer(
            id = layerId,
            name = layerName,
            components = listOf(initialComp)
        )
        val updated = existing + newLayer
        pushState(_sessionState.value.copy(
            maskLayers = updated,
            activeMaskId = layerId,
            activeComponentId = initialComp.id,
            description = "New $layerName"
        ))
        return newLayer
    }

    fun selectMaskLayer(id: String) {
        val layer = _sessionState.value.maskLayers.firstOrNull { it.id == id } ?: return
        _sessionState.value = _sessionState.value.copy(
            activeMaskId = id,
            activeComponentId = layer.components.firstOrNull()?.id
        )
    }

    fun deleteMaskLayer(id: String) {
        val existing = _sessionState.value.maskLayers
        val updated = existing.filterNot { it.id == id }
        val newActiveId = if (_sessionState.value.activeMaskId == id) updated.firstOrNull()?.id else _sessionState.value.activeMaskId
        pushState(_sessionState.value.copy(
            maskLayers = updated,
            activeMaskId = newActiveId,
            description = "Delete Mask"
        ))
    }

    fun duplicateMaskLayer(id: String) {
        val existing = _sessionState.value.maskLayers
        val target = existing.firstOrNull { it.id == id } ?: return
        val newId = java.util.UUID.randomUUID().toString()
        val copyLayer = target.copy(
            id = newId,
            name = "${target.name} Copy"
        )
        pushState(_sessionState.value.copy(
            maskLayers = existing + copyLayer,
            activeMaskId = newId,
            description = "Duplicate Mask"
        ))
    }

    fun toggleMaskLayerEnabled(id: String) {
        val existing = _sessionState.value.maskLayers
        val updated = existing.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
        pushState(_sessionState.value.copy(
            maskLayers = updated,
            description = "Toggle Mask Visibility"
        ))
    }

    fun invertMaskLayer(id: String) {
        val existing = _sessionState.value.maskLayers
        val updated = existing.map { if (it.id == id) it.copy(isInverted = !it.isInverted) else it }
        pushState(_sessionState.value.copy(
            maskLayers = updated,
            description = "Invert Mask Layer"
        ))
    }

    fun setMaskAmount(id: String, amount: Float) {
        if (sliderBaselineState == null) {
            sliderBaselineState = _sessionState.value
        }
        val existing = _sessionState.value.maskLayers
        val updated = existing.map { if (it.id == id) it.copy(amount = amount.coerceIn(0f, 2f)) else it }
        _sessionState.value = _sessionState.value.copy(maskLayers = updated)
        updateLivePreview()
    }

    fun addComponentToMask(maskId: String, type: MaskComponentType, combineMode: MaskCombineMode = MaskCombineMode.ADD) {
        val existing = _sessionState.value.maskLayers
        val target = existing.firstOrNull { it.id == maskId } ?: return
        val compId = java.util.UUID.randomUUID().toString()
        val newComp = MaskComponent(
            id = compId,
            type = type,
            combineMode = combineMode
        )
        val updated = existing.map {
            if (it.id == maskId) it.copy(components = it.components + newComp) else it
        }
        pushState(_sessionState.value.copy(
            maskLayers = updated,
            activeMaskId = maskId,
            activeComponentId = compId,
            description = "Add ${type.label}"
        ))
    }

    fun removeComponentFromMask(maskId: String, componentId: String) {
        val existing = _sessionState.value.maskLayers
        val updated = existing.map {
            if (it.id == maskId) it.copy(components = it.components.filterNot { c -> c.id == componentId }) else it
        }
        pushState(_sessionState.value.copy(
            maskLayers = updated,
            description = "Remove Component"
        ))
    }

    fun selectMaskComponent(maskId: String, componentId: String) {
        _sessionState.value = _sessionState.value.copy(
            activeMaskId = maskId,
            activeComponentId = componentId
        )
    }

    fun updateMaskComponent(maskId: String, updatedComp: MaskComponent) {
        val existing = _sessionState.value.maskLayers
        val updated = existing.map { layer ->
            if (layer.id == maskId) {
                val newComps = layer.components.map { if (it.id == updatedComp.id) updatedComp else it }
                layer.copy(components = newComps)
            } else layer
        }
        _sessionState.value = _sessionState.value.copy(maskLayers = updated)
        updateLivePreview()
    }

    fun updateMaskLocalAdjustment(maskId: String, prop: String, value: Float) {
        if (sliderBaselineState == null) {
            sliderBaselineState = _sessionState.value
        }
        val existing = _sessionState.value.maskLayers
        val updated = existing.map { layer ->
            if (layer.id == maskId) {
                val adj = layer.adjustments
                val newAdj = when (prop) {
                    "Exposure" -> adj.copy(exposure = value)
                    "Contrast" -> adj.copy(contrast = value)
                    "Highlights" -> adj.copy(highlights = value)
                    "Shadows" -> adj.copy(shadows = value)
                    "Whites" -> adj.copy(whites = value)
                    "Blacks" -> adj.copy(blacks = value)
                    "Temperature", "Temp" -> adj.copy(temperature = value)
                    "Tint" -> adj.copy(tint = value)
                    "Saturation" -> adj.copy(saturation = value)
                    "Vibrance" -> adj.copy(vibrance = value)
                    "Texture" -> adj.copy(texture = value)
                    "Clarity" -> adj.copy(clarity = value)
                    "Dehaze" -> adj.copy(dehaze = value)
                    "Sharpness" -> adj.copy(sharpness = value)
                    "Blur" -> adj.copy(blur = value)
                    "Noise Reduction", "Noise" -> adj.copy(noiseReduction = value)
                    else -> adj
                }
                layer.copy(adjustments = newAdj)
            } else layer
        }
        _sessionState.value = _sessionState.value.copy(maskLayers = updated)
        updateLivePreview()
    }

    fun setMaskColorTint(maskId: String, color: Color?) {
        val existing = _sessionState.value.maskLayers
        val updated = existing.map { layer ->
            if (layer.id == maskId) {
                layer.copy(adjustments = layer.adjustments.copy(colorTint = color))
            } else layer
        }
        pushState(_sessionState.value.copy(
            maskLayers = updated,
            description = "Mask Color Tint"
        ))
    }

    fun resetMaskAdjustments(maskId: String) {
        val existing = _sessionState.value.maskLayers
        val updated = existing.map { layer ->
            if (layer.id == maskId) {
                layer.copy(adjustments = MaskLocalAdjustments(), amount = 1.0f)
            } else layer
        }
        pushState(_sessionState.value.copy(
            maskLayers = updated,
            description = "Reset Mask Adjustments"
        ))
    }

    fun setMaskOverlaySettings(settings: MaskOverlaySettings) {
        _sessionState.value = _sessionState.value.copy(maskOverlaySettings = settings)
    }

    fun toggleMaskOverlay(enabled: Boolean? = null) {
        val current = _sessionState.value.maskOverlaySettings
        val next = enabled ?: !current.showOverlay
        _sessionState.value = _sessionState.value.copy(
            maskOverlaySettings = current.copy(showOverlay = next)
        )
    }

    fun setMaskOverlayColor(color: MaskOverlayColor) {
        val current = _sessionState.value.maskOverlaySettings
        _sessionState.value = _sessionState.value.copy(
            maskOverlaySettings = current.copy(overlayColor = color)
        )
    }

    fun setMaskOverlayOpacity(opacity: Float) {
        val current = _sessionState.value.maskOverlaySettings
        _sessionState.value = _sessionState.value.copy(
            maskOverlaySettings = current.copy(opacity = opacity.coerceIn(0.1f, 1f))
        )
    }

    fun addStrokeToActiveMask(stroke: DrawingStroke) {
        val activeMaskId = _sessionState.value.activeMaskId
        if (activeMaskId == null) {
            val layer = createMaskLayer(MaskComponentType.BRUSH)
            val comp = layer.components.first()
            updateMaskComponent(layer.id, comp.copy(brushStrokes = comp.brushStrokes + stroke))
        } else {
            val mask = _sessionState.value.maskLayers.firstOrNull { it.id == activeMaskId }
            val activeCompId = _sessionState.value.activeComponentId
            val comp = mask?.components?.firstOrNull { it.id == activeCompId && it.type == MaskComponentType.BRUSH }
                ?: mask?.components?.firstOrNull { it.type == MaskComponentType.BRUSH }

            if (comp != null) {
                updateMaskComponent(activeMaskId, comp.copy(brushStrokes = comp.brushStrokes + stroke))
            } else {
                addComponentToMask(activeMaskId, MaskComponentType.BRUSH)
                val updatedMask = _sessionState.value.maskLayers.firstOrNull { it.id == activeMaskId }
                val newComp = updatedMask?.components?.lastOrNull()
                if (newComp != null) {
                    updateMaskComponent(activeMaskId, newComp.copy(brushStrokes = listOf(stroke)))
                }
            }
        }
    }

    fun setScaleFactor(scale: Float) {
        pushState(_sessionState.value.copy(
            transform = _sessionState.value.transform.copy(scaleFactor = scale),
            description = "Scale ${(scale * 100).toInt()}%"
        ))
    }

    // --- AI Tools ---
    fun toggleAiAutoEnhance(enabled: Boolean) {
        val updated = _sessionState.value.aiSettings.copy(autoEnhanceEnabled = enabled)
        pushState(_sessionState.value.copy(aiSettings = updated, description = if (enabled) "AI Auto Enhance" else "AI Auto Enhance Off"))
    }

    fun setAiAutoEnhanceIntensity(intensity: Float) {
        val updated = _sessionState.value.aiSettings.copy(autoEnhanceIntensity = intensity)
        _sessionState.value = _sessionState.value.copy(aiSettings = updated)
        updateLivePreview()
    }

    fun setAiStyleTransfer(style: AiStyleTransfer?) {
        val updated = _sessionState.value.aiSettings.copy(activeStyleTransfer = style)
        pushState(_sessionState.value.copy(aiSettings = updated, description = style?.label ?: "Remove style"))
    }

    fun setAiTryOnStyle(style: AiTryOnStyle?) = setAiTryOn(style)

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
     * Returns the full list of operation descriptions up to current state.
     */
    fun getHistoryList(): List<String> {
        val list = undoStack.map { it.description } + listOf(_sessionState.value.description)
        return list
    }

    fun getCurrentHistoryIndex(): Int = undoStack.size

    /**
     * Jumps back to a specific step in the history stack.
     */
    fun jumpToHistoryStep(targetIndex: Int) {
        val fullHistory = undoStack + listOf(_sessionState.value)
        if (targetIndex !in fullHistory.indices) return
        commitSliderChange()
        val targetState = fullHistory[targetIndex]
        undoStack.clear()
        for (i in 0 until targetIndex) {
            undoStack.add(fullHistory[i])
        }
        redoStack.clear()
        for (i in fullHistory.lastIndex downTo targetIndex + 1) {
            redoStack.add(fullHistory[i])
        }
        _sessionState.value = targetState
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        updateLivePreview()
    }

    /**
     * Cinematic step-by-step replay of editing history.
     */
    fun startReplay() {
        if (undoStack.isEmpty()) return
        viewModelScope.launch {
            _isReplaying.value = true
            val activeState = _sessionState.value
            val fullHistory = undoStack + listOf(activeState)
            val total = fullHistory.size
            for ((idx, step) in fullHistory.withIndex()) {
                _sessionState.value = step
                _replayProgress.value = (idx + 1).toFloat() / total
                updateLivePreview()
                delay(650)
            }
            delay(400)
            _sessionState.value = activeState
            updateLivePreview()
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
