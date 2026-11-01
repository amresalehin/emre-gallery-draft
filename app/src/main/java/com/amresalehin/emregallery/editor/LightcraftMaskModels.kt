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
/**
 * Mask component types supported in LightCraft & Emre Gallery.
 * Includes Adobe Lightroom's core selections (Subject, Sky, Background, People & Features)
 * as well as Manual (Brush, Linear/Radial Gradients) and Range (Luminance, Color).
 */
enum class MaskComponentType(val label: String, val iconName: String = "AutoFixNormal") {
    // Adobe Lightroom AI & Intelligent Selections
    SELECT_SUBJECT("Select Subject", "Person"),
    SELECT_SKY("Select Sky", "Cloud"),
    SELECT_BACKGROUND("Select Background", "Landscape"),

    // People & Portrait Features
    PEOPLE_ALL("Select Person", "Face"),
    PEOPLE_FACIAL_SKIN("Facial Skin", "FaceRetouching"),
    PEOPLE_BODY_SKIN("Body Skin", "Accessibility"),
    PEOPLE_EYES("Eyes", "Visibility"),
    PEOPLE_LIPS("Lips", "Favorite"),
    PEOPLE_TEETH("Teeth", "SentimentSatisfied"),
    PEOPLE_HAIR("Hair", "Spa"),

    // Manual Precision Tools
    BRUSH("Brush", "Brush"),
    LINEAR_GRADIENT("Linear Gradient", "LinearScale"),
    RADIAL_GRADIENT("Radial Gradient", "RadioButtonUnchecked"),

    // Range Masks
    COLOR_RANGE("Color Range", "Palette"),
    LUMINANCE_RANGE("Luminance Range", "WbSunny"),

    // Freehand / Polygon
    LASSO("Lasso Loop", "Gesture"),
    POLYGON("Polygon", "Polyline")
}

/**
 * PicsArt-inspired 1-tap Smart Mask Recipes.
 * Instantly creates a targeted mask with pre-configured, professional adjustments.
 */
enum class MaskQuickRecipe(
    val title: String,
    val subtitle: String,
    val componentType: MaskComponentType,
    val defaultAdjustments: MaskLocalAdjustments
) {
    SMOOTH_FACIAL_SKIN(
        title = "Smooth Facial Skin",
        subtitle = "Soften pores, reduce blemishes & enhance skin glow",
        componentType = MaskComponentType.PEOPLE_FACIAL_SKIN,
        defaultAdjustments = MaskLocalAdjustments(
            texture = -35f,
            clarity = -20f,
            sharpness = 12f,
            highlights = 8f,
            temperature = 4f
        )
    ),
    ENHANCE_EYES(
        title = "Pop & Enhance Eyes",
        subtitle = "Sharpen irises, boost clarity & brighten eye catchlights",
        componentType = MaskComponentType.PEOPLE_EYES,
        defaultAdjustments = MaskLocalAdjustments(
            exposure = 22f,
            clarity = 45f,
            sharpness = 50f,
            saturation = 15f,
            highlights = 15f
        )
    ),
    WHITEN_TEETH(
        title = "Whiten Teeth",
        subtitle = "Brighten smile & neutralize yellow discoloration",
        componentType = MaskComponentType.PEOPLE_TEETH,
        defaultAdjustments = MaskLocalAdjustments(
            exposure = 25f,
            saturation = -40f,
            whites = 25f
        )
    ),
    HAIR_GLOSS(
        title = "Hair Shine & Depth",
        subtitle = "Deepen contrast, enrich rich tones & add glossy sheen",
        componentType = MaskComponentType.PEOPLE_HAIR,
        defaultAdjustments = MaskLocalAdjustments(
            contrast = 25f,
            texture = 20f,
            saturation = 15f,
            blacks = -15f
        )
    ),
    SELECT_SUBJECT_POP(
        title = "Subject Spotlight",
        subtitle = "Separate subject from background with exposure & clarity boost",
        componentType = MaskComponentType.SELECT_SUBJECT,
        defaultAdjustments = MaskLocalAdjustments(
            exposure = 16f,
            contrast = 15f,
            clarity = 22f,
            vibrance = 15f,
            texture = 10f
        )
    ),
    DARKEN_DRAMATIC_SKY(
        title = "Dramatic Deep Sky",
        subtitle = "Bring out cloud contours, reduce exposure & boost dehaze",
        componentType = MaskComponentType.SELECT_SKY,
        defaultAdjustments = MaskLocalAdjustments(
            exposure = -30f,
            contrast = 25f,
            dehaze = 35f,
            saturation = 20f,
            highlights = -20f
        )
    ),
    PORTRAIT_BACKGROUND_BLUR(
        title = "Portrait Depth Blur",
        subtitle = "Create creamy DSLR shallow depth of field behind subject",
        componentType = MaskComponentType.SELECT_BACKGROUND,
        defaultAdjustments = MaskLocalAdjustments(
            blur = 55f,
            exposure = -15f,
            texture = -25f,
            clarity = -15f
        )
    ),
    GOLDEN_HOUR_GRADIENT(
        title = "Golden Hour Sunlight",
        subtitle = "Gradual warm sunlight wash across the horizon",
        componentType = MaskComponentType.LINEAR_GRADIENT,
        defaultAdjustments = MaskLocalAdjustments(
            temperature = 40f,
            tint = 15f,
            exposure = 12f,
            highlights = 15f
        )
    ),
    SPOTLIGHT_FOCUS(
        title = "Radial Center Spotlight",
        subtitle = "Focus bright lighting on center with subtle surrounding falloff",
        componentType = MaskComponentType.RADIAL_GRADIENT,
        defaultAdjustments = MaskLocalAdjustments(
            exposure = 20f,
            contrast = 12f,
            clarity = 15f
        )
    )
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
