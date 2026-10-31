package com.amresalehin.emregallery.editor

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class EditorTool(val label: String) {
    CROP("Crop"),
    FREE_CROP("Free Crop"),
    SHAPE_CROP("Shape Crop"),
    DISPERSION("Dispersion"),
    CLONE("Clone"),
    AI_REPLACE("AI Replace"),
    STRETCH("Stretch"),
    MOTION("Motion"),
    SELECTION("Selection"),
    CURVES("Curves"),
    ADJUST("Adjust"),
    AI_ENHANCE("AI Enhance"),
    TILT_SHIFT("Tilt Shift"),
    PERSPECTIVE("Perspective"),
    RESIZE("Resize"),
    ROTATE("Rotate"),
    AI_EXPAND("AI Expand"),
    TOOLS("Tools"),
    EFFECTS("Effects"),
    RETOUCH("Retouch"),
    REMOVE_BG("Remove BG"),
    STICKER("Sticker"),
    CUTOUT("Cutout"),
    TEXT("Text"),
    ADD_PHOTO("Add Photo"),
    REMOVE("Remove"),
    REPLAY("Replay"),
    FIT("Fit"),
    BRUSHES("Brushes"),
    BORDER("Border"),
    SHAPE("Shape"),
    MASK("Mask"),
    DRAW("Draw"),
    AI_TRY_ON("AI Try On"),
    LENS_FLARE("Lens Flare"),
    FRAME("Frame"),
    CALLOUT("Callout"),
    AI_TOOLS("Edit with AI")
}

enum class ToolSheetSubItem(val label: String) {
    CROP("Crop"),
    FREE_CROP("Free Crop"),
    SHAPE_CROP("Shape Crop"),
    ROTATE("Rotate"),
    FLIP("Flip"),
    STRAIGHTEN("Straighten"),
    CLONE("Clone"),
    PERSPECTIVE("Perspective"),
    RESIZE("Resize"),
    ADJUST("Adjust"),
    ENHANCE("AI Enhance"),
    AI_REPLACE("AI Replace"),
    AI_EXPAND("AI Expand"),
    TILT_SHIFT("Tilt Shift"),
    CURVES("Curves"),
    SELECTION("Selection"),
    STRETCH("Stretch"),
    MOTION("Motion"),
    DISPERSION("Dispersion")
}

enum class AspectRatioPreset(val label: String, val ratio: Float?) {
    FREE("Free", null),
    ORIGINAL("Original", null),
    SQUARE("1:1", 1.0f),
    STORY("9:16", 9f / 16f),
    PORTRAIT("4:5", 4f / 5f),
    POST("3:2", 3f / 2f),
    LANDSCAPE("16:9", 16f / 9f),
    STANDARD("4:3", 4f / 3f),
    COVER("2:1", 2.0f)
}

enum class FilterType(val displayName: String) {
    NONE("Original"),
    VIVID("Vivid"),
    FILM("Film 400"),
    NOIR("Noir"),
    BW("Classic B&W"),
    WARM_SUNSET("Sunset"),
    CYBER("Teal & Orange"),
    VINTAGE("Vintage '70"),
    SEPIA("Sepia Tone"),
    DRAMATIC("Dramatic"),
    PASTEL("Pastel Glow"),
    EMERALD("Emerald"),
    COLD_MIST("Cold Mist")
}

data class TransformParams(
    val cropRect: RectF? = null, // relative [0f, 0f, 1f, 1f]
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val straightenAngle: Float = 0f, // -45 to +45
    val perspectiveX: Float = 0f, // -30 to +30
    val perspectiveY: Float = 0f, // -30 to +30
    val scaleFactor: Float = 1.0f
)

data class AdjustmentParams(
    val brightness: Float = 0f, // -100..100
    val contrast: Float = 0f,   // -100..100
    val saturation: Float = 0f, // -100..100
    val exposure: Float = 0f,   // -100..100
    val highlights: Float = 0f, // -100..100
    val shadows: Float = 0f,    // -100..100
    val temperature: Float = 0f,// -100..100 (warm/cool)
    val tint: Float = 0f,       // -100..100 (green/magenta)
    val sharpness: Float = 0f,  // 0..100
    val blur: Float = 0f,       // 0..100
    val vignette: Float = 0f,   // 0..100
    val grain: Float = 0f       // 0..100
) {
    val isDefault: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f &&
                exposure == 0f && highlights == 0f && shadows == 0f &&
                temperature == 0f && tint == 0f && sharpness == 0f &&
                blur == 0f && vignette == 0f && grain == 0f
}

enum class RetouchType(val label: String) {
    BLEMISH("Blemish Removal"),
    OBJECT_REMOVAL("Object Erase"),
    SKIN_SMOOTH("Skin Smoothing"),
    RED_EYE("Red-Eye Fix"),
    SELECTIVE_BRUSH("Selective Retouch")
}

data class RetouchStroke(
    val type: RetouchType,
    val points: List<Offset>,
    val radius: Float = 28f,
    val intensity: Float = 1.0f
)

enum class BackgroundMode(val label: String) {
    ORIGINAL("Original"),
    TRANSPARENT("Transparent"),
    BLUR("Portrait Blur"),
    SOLID("Studio Solid"),
    GRADIENT("Gradient"),
    STAGE("Studio Stage")
}

data class BackgroundSettings(
    val mode: BackgroundMode = BackgroundMode.ORIGINAL,
    val blurRadius: Float = 25f,
    val solidColor: Color = Color.White,
    val gradientColors: List<Color> = listOf(Color(0xFFFF758C), Color(0xFFFF7EB3)),
    val isCutoutActive: Boolean = false
)

enum class BrushType(val label: String) {
    PEN("Pen"),
    GLOW("Neon Glow"),
    HIGHLIGHTER("Marker"),
    DOTTED("Dotted"),
    ERASER("Eraser")
}

data class DrawingStroke(
    val id: String,
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val brushType: BrushType
)

data class TextOverlayItem(
    val id: String,
    val text: String,
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.5f,
    val color: Color = Color.White,
    val backgroundColor: Color = Color.Black.copy(alpha = 0.5f),
    val fontSizeSp: Float = 24f,
    val isBold: Boolean = true,
    val rotation: Float = 0f,
    val scale: Float = 1.0f
)

data class StickerItem(
    val id: String,
    val emojiOrRes: String,
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.5f,
    val sizeDp: Float = 64f,
    val rotation: Float = 0f,
    val scale: Float = 1.0f
)

enum class ShapeType(val label: String) {
    RECTANGLE("Rectangle"),
    CIRCLE("Circle"),
    STAR("Star"),
    HEART("Heart"),
    HEXAGON("Hexagon"),
    ARROW("Arrow")
}

data class ShapeItem(
    val id: String,
    val shapeType: ShapeType,
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.5f,
    val widthRatio: Float = 0.35f,
    val heightRatio: Float = 0.35f,
    val strokeColor: Color = Color.White,
    val fillColor: Color = Color.Transparent,
    val strokeWidth: Float = 4f,
    val rotation: Float = 0f,
    val isMask: Boolean = false
)

enum class FrameStyle(val label: String) {
    NONE("None"),
    POLAROID("Polaroid"),
    FILM_STRIP("Film 35mm"),
    MODERN_WHITE("Gallery White"),
    DOUBLE_BORDER("Double Outline"),
    VINTAGE_FADED("Retro Fade"),
    MINIMAL_BLACK("Minimal Dark")
}

data class BorderSettings(
    val widthRatio: Float = 0f, // 0 to 0.15f
    val color: Color = Color.White,
    val cornerRadiusRatio: Float = 0f,
    val paddingRatio: Float = 0f
)

data class LensFlareItem(
    val id: String,
    val xRatio: Float = 0.3f,
    val yRatio: Float = 0.25f,
    val intensity: Float = 0.8f,
    val flareType: LensFlareType = LensFlareType.SUNBURST
)

enum class LensFlareType(val label: String) {
    SUNBURST("Sunburst"),
    ANAMORPHIC("Anamorphic Blue"),
    GOLDEN_HALO("Golden Halo"),
    RAINBOW_RING("Rainbow Ring")
}

data class CalloutItem(
    val id: String,
    val text: String,
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.4f,
    val isThoughtBubble: Boolean = false,
    val tailPointRatio: Offset = Offset(0.4f, 0.65f),
    val backgroundColor: Color = Color.White,
    val textColor: Color = Color.Black
)

enum class AiStyleTransfer(val label: String) {
    CYBERPUNK("Cyberpunk Neon"),
    ANIME_VIBRANT("Anime Pop"),
    CHARCOAL_SKETCH("Charcoal Sketch"),
    OIL_PAINTING("Impressionist"),
    VINTAGE_SEPIA("Sepia Antique")
}

enum class AiTryOnStyle(val label: String, val description: String) {
    LEATHER_JACKET("Biker Leather", "Sharp matte leather styling"),
    FORMAL_SUIT("Classic Tuxedo", "Tailored midnight black blazer"),
    NEON_HOODIE("Cyber Streetwear", "Neon accents streetwear hoodie"),
    DENIM_VINTAGE("Vintage Denim", "Distressed washed denim jacket"),
    HAWAIIAN_SUMMER("Tropical Silk", "Vibrant floral summer shirt")
}

data class AiEditSettings(
    val autoEnhanceEnabled: Boolean = false,
    val autoEnhanceIntensity: Float = 1.0f,
    val activeStyleTransfer: AiStyleTransfer? = null,
    val activeTryOn: AiTryOnStyle? = null,
    val expandOutpaintRatio: Float = 0f // 0f to 0.5f expansion
)

/**
 * Immutable snapshot of the entire editable session.
 * Used for the operation history stack, undo, redo, and animated replay.
 */
data class EditSessionState(
    val transform: TransformParams = TransformParams(),
    val adjustments: AdjustmentParams = AdjustmentParams(),
    val filter: FilterType = FilterType.NONE,
    val filterIntensity: Float = 1.0f,
    val retouchStrokes: List<RetouchStroke> = emptyList(),
    val background: BackgroundSettings = BackgroundSettings(),
    val drawingStrokes: List<DrawingStroke> = emptyList(),
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val stickers: List<StickerItem> = emptyList(),
    val shapes: List<ShapeItem> = emptyList(),
    val frame: FrameStyle = FrameStyle.NONE,
    val border: BorderSettings = BorderSettings(),
    val lensFlares: List<LensFlareItem> = emptyList(),
    val callouts: List<CalloutItem> = emptyList(),
    val aiSettings: AiEditSettings = AiEditSettings(),
    val description: String = "Initial Image"
)
