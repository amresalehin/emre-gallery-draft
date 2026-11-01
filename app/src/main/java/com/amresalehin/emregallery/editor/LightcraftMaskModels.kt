package com.amresalehin.emregallery.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Boolean blend mode for combining mask components in LightCraft.
 */
enum class MaskCombineMode(val label: String, val symbol: String) {
    ADD("Add", "+"),
    SUBTRACT("Subtract", "−"),
    INTERSECT("Intersect", "×")
}

/**
 * Mask overlay display colors supported in LightCraft / Lightroom.
 */
enum class MaskOverlayColor(val label: String, val color: Color) {
    RUBY_RED("Ruby Red", Color(0xFFE53935)),
    EMERALD_GREEN("Emerald Green", Color(0xFF00E676)),
    ELECTRIC_BLUE("Electric Blue", Color(0xFF00B0FF)),
    PURE_WHITE("Pure White", Color(0xFFFFFFFF)),
    HIGH_CONTRAST_BW("High Contrast B&W", Color(0xFF212121))
}

/**
 * Overlay visualization settings.
 */
data class MaskOverlaySettings(
    val showOverlay: Boolean = true,
    val overlayColor: MaskOverlayColor = MaskOverlayColor.RUBY_RED,
    val opacity: Float = 0.5f
)

/**
 * LightCraft's signature 17 localized adjustments per mask layer.
 */
data class MaskLocalAdjustments(
    // 1-6. Light & Tone
    val exposure: Float = 0f,      // -100..+100
    val contrast: Float = 0f,      // -100..+100
    val highlights: Float = 0f,    // -100..+100
    val shadows: Float = 0f,       // -100..+100
    val whites: Float = 0f,        // -100..+100
    val blacks: Float = 0f,        // -100..+100

    // 7-11. Color & Temperature
    val temperature: Float = 0f,   // -100..+100 (Cool / Warm)
    val tint: Float = 0f,          // -100..+100 (Green / Magenta)
    val saturation: Float = 0f,    // -100..+100
    val vibrance: Float = 0f,      // -100..+100
    val colorTint: Color? = null,  // Localized color wash tint

    // 12-17. Presence & Detail
    val texture: Float = 0f,       // -100..+100
    val clarity: Float = 0f,       // -100..+100
    val dehaze: Float = 0f,        // -100..+100
    val sharpness: Float = 0f,     // 0..+100
    val blur: Float = 0f,          // 0..+100
    val noiseReduction: Float = 0f // 0..+100
) {
    fun isNeutral(): Boolean {
        return exposure == 0f && contrast == 0f && highlights == 0f && shadows == 0f &&
                whites == 0f && blacks == 0f && temperature == 0f && tint == 0f &&
                saturation == 0f && vibrance == 0f && colorTint == null &&
                texture == 0f && clarity == 0f && dehaze == 0f && sharpness == 0f &&
                blur == 0f && noiseReduction == 0f
    }
}

/**
 * Mask component types supported in LightCraft.
 */
enum class MaskComponentType(val label: String) {
    BRUSH("Brush"),
    LINEAR_GRADIENT("Linear Gradient"),
    RADIAL_GRADIENT("Radial Gradient"),
    LUMINANCE_RANGE("Luminance Range"),
    COLOR_RANGE("Color Range"),
    SELECT_SUBJECT("Select Subject"),
    SELECT_SKY("Select Sky"),
    SELECT_BACKGROUND("Select Background"),
    LASSO("Lasso Loop"),
    POLYGON("Polygon")
}

/**
 * Individual mask component inside a LightCraft mask layer.
 */
data class MaskComponent(
    val id: String = System.currentTimeMillis().toString(),
    val type: MaskComponentType = MaskComponentType.BRUSH,
    val combineMode: MaskCombineMode = MaskCombineMode.ADD,
    val isInverted: Boolean = false,
    val isEnabled: Boolean = true,

    // Brush parameters
    val brushSize: Float = 40f,
    val brushFeather: Float = 50f,   // 0..100%
    val brushFlow: Float = 1.0f,     // 0.1..1.0
    val brushDensity: Float = 1.0f,  // 0.1..1.0
    val isEraser: Boolean = false,
    val brushStrokes: List<DrawingStroke> = emptyList(),

    // Linear gradient parameters (normalized 0..1 coordinates)
    val linearStart: Offset = Offset(0.5f, 0.85f),
    val linearEnd: Offset = Offset(0.5f, 0.15f),
    val linearAngle: Float = 0f,     // -180..+180
    val linearFeather: Float = 50f,  // 0..100%

    // Radial gradient parameters
    val radialCenter: Offset = Offset(0.5f, 0.5f),
    val radialRadiusX: Float = 0.35f,
    val radialRadiusY: Float = 0.35f,
    val radialFeather: Float = 50f,

    // Luminance range parameters
    val lumMin: Float = 0f,          // 0..100
    val lumMax: Float = 100f,        // 0..100
    val lumFeather: Float = 25f,     // 0..100

    // Color range parameters
    val targetHue: Float = 0f,       // 0..360
    val colorTolerance: Float = 35f, // 5..90
    val colorFeather: Float = 25f,

    // Freehand / Polygon loop points
    val pathPoints: List<Offset> = emptyList()
)

/**
 * A full LightCraft Mask Layer with components, 17 local adjustments, and an overall amount slider.
 */
data class LightcraftMaskLayer(
    val id: String,
    val name: String = "Mask 1",
    val isEnabled: Boolean = true,
    val isInverted: Boolean = false,
    val amount: Float = 1.0f, // 0..2f (overall strength)
    val components: List<MaskComponent> = emptyList(),
    val adjustments: MaskLocalAdjustments = MaskLocalAdjustments()
)
