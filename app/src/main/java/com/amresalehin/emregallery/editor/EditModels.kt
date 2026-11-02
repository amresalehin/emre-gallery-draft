package com.amresalehin.emregallery.editor

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class EditorMode(val displayName: String) {
    NONE("Inspect"),
    ADJUST("Adjust"),
    CURVES("Tone Curves"),
    COLOR_MIX("Color Mix"),
    COLOR_GRADING("Color Grading"),
    CROP("Crop & Rotate"),
    PRESETS("Presets & Profiles"),
    BRUSHES("Masking & Draw"),
    RETOUCH("Healing & Retouch")
}

enum class EditorTool(val label: String) {
    CROP("Crop"),
    TOOLS("Tools"),
    ADJUST("Adjust"),
    EFFECTS("Effects"),
    RETOUCH("Retouch"),
    REMOVE_BG("Cutout & BG"),
    BRUSHES("Draw"),
    TEXT("Text"),
    STICKER("Sticker"),
    SHAPE("Shapes"),
    FRAME("Frames"),
    CALLOUT("Callout"),
    LENS_FLARE("Lens Flare"),
    AI_TOOLS("AI Magic"),
    REPLAY("Replay"),
    CURVES("Curves")
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

enum class ShapeCropType(val label: String) {
    RECTANGLE("Rect"),
    CIRCLE("Circle"),
    OVAL("Oval"),
    TRIANGLE("Triangle"),
    HEART("Heart"),
    STAR("Star"),
    HEXAGON("Hexagon"),
    DIAMOND("Diamond"),
    SHIELD("Shield")
}

data class CornerPinPoint(val x: Float, val y: Float)

data class MeshWarpPoint(
    val origX: Float,
    val origY: Float,
    val currX: Float,
    val currY: Float
)

enum class LiquifyMode(val label: String) {
    PUSH("Push"),
    PULL("Pull"),
    EXPAND("Expand"),
    SHRINK("Shrink")
}

data class LiquifyPoint(
    val x: Float,
    val y: Float,
    val radius: Float = 40f,
    val strength: Float = 0.5f,
    val mode: LiquifyMode = LiquifyMode.PUSH
)

data class TransformParams(
    val cropRect: RectF? = null, // relative [0f, 0f, 1f, 1f]
    val shapeCrop: ShapeCropType? = null,
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val straightenAngle: Float = 0f, // -45 to +45
    val perspectiveX: Float = 0f, // -30 to +30
    val perspectiveY: Float = 0f, // -30 to +30
    val cornerPins: List<CornerPinPoint>? = null, // 4 points: TL, TR, BR, BL
    val meshPoints: List<MeshWarpPoint>? = null,
    val liquifyPoints: List<LiquifyPoint> = emptyList(),
    val stretchAmount: Float = 0f, // -50 to 50
    val motionAmount: Float = 0f,  // 0 to 100
    val motionAngle: Float = 0f,   // 0 to 360
    val dispersionAmount: Float = 0f, // 0 to 100
    val scaleFactor: Float = 1.0f
)

enum class ColorBand(val label: String, val baseHue: Float) {
    RED("Red", 0f),
    ORANGE("Orange", 30f),
    YELLOW("Yellow", 60f),
    GREEN("Green", 120f),
    CYAN("Cyan", 180f),
    BLUE("Blue", 240f),
    PURPLE("Purple", 280f),
    MAGENTA("Magenta", 320f)
}

data class HslBandAdjustment(
    val hueShift: Float = 0f,  // -180..+180
    val satShift: Float = 0f,  // -100..+100
    val lumShift: Float = 0f   // -100..+100
)

data class CurvePoint(val inVal: Float, val outVal: Float)

data class RgbCurves(
    val master: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val red: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val green: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val blue: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
)

data class LevelsParams(
    val blackPoint: Float = 0f,     // 0..100
    val midtonesGamma: Float = 1.0f,// 0.2..3.0
    val whitePoint: Float = 255f    // 150..255
)

data class ColorBalanceParams(
    val shadowRed: Float = 0f,
    val shadowGreen: Float = 0f,
    val shadowBlue: Float = 0f,
    val midtoneRed: Float = 0f,
    val midtoneGreen: Float = 0f,
    val midtoneBlue: Float = 0f,
    val highlightRed: Float = 0f,
    val highlightGreen: Float = 0f,
    val highlightBlue: Float = 0f
)

data class SplitToningParams(
    val highlightHue: Float = 45f,
    val highlightSat: Float = 0f,  // 0..100
    val shadowHue: Float = 215f,
    val shadowSat: Float = 0f,     // 0..100
    val balance: Float = 0f        // -100..+100
)

enum class GradientMapType(val label: String) {
    NONE("None"),
    SEPIA_GOLD("Sepia Gold"),
    SUNSET_GLOW("Sunset Glow"),
    CYBER_NEON("Cyber Neon"),
    DEEP_OCEAN("Deep Ocean"),
    EMERALD_FOREST("Emerald Forest"),
    VINTAGE_PASTEL("Vintage Pastel"),
    BW_CONTRAST("Monochrome High")
}

data class DuotoneParams(
    val enabled: Boolean = false,
    val shadowColor: Color = Color(0xFF16213E),
    val highlightColor: Color = Color(0xFFE94560)
)

data class ColorReplaceParams(
    val enabled: Boolean = false,
    val sourceHue: Float = 0f,
    val targetHue: Float = 120f,
    val tolerance: Float = 30f
)

data class ColorSplashParams(
    val enabled: Boolean = false,
    val targetHue: Float = 0f,   // 0=Red, 60=Yellow, 120=Green, 240=Blue, etc.
    val tolerance: Float = 40f
)

data class AdjustmentParams(
    val brightness: Float = 0f, // -100..100
    val contrast: Float = 0f,   // -100..100
    val saturation: Float = 0f, // -100..100
    val exposure: Float = 0f,   // -100..100
    val highlights: Float = 0f, // -100..100
    val shadows: Float = 0f,    // -100..100
    val whites: Float = 0f,     // -100..100
    val blacks: Float = 0f,     // -100..100
    val temperature: Float = 0f,// -100..100 (warm/cool)
    val tint: Float = 0f,       // -100..100 (green/magenta)
    val texture: Float = 0f,    // -100..100
    val clarity: Float = 0f,    // -100..100
    val sharpness: Float = 0f,  // 0..100
    val sharpnessRadius: Float = 1.0f,
    val sharpnessDetail: Float = 25f,
    val blur: Float = 0f,       // 0..100
    val vignette: Float = 0f,   // 0..100
    val vignetteMidpoint: Float = 50f,
    val vignetteFeather: Float = 50f,
    val vignetteRoundness: Float = 0f,
    val grain: Float = 0f,      // 0..100
    val grainSize: Float = 25f,
    val grainRoughness: Float = 50f,
    val vibrance: Float = 0f,   // -100..100
    val dehaze: Float = 0f,     // 0..100
    val dodge: Float = 0f,      // 0..100
    val burn: Float = 0f,       // 0..100
    val noiseReduction: Float = 0f, // 0..100
    val colorNoiseReduction: Float = 0f, // 0..100
    val removeChromaticAberration: Boolean = false,
    val enableLensCorrections: Boolean = false,
    val distortionCorrection: Float = 100f,
    val vignettingCorrection: Float = 100f,
    val whiteBalanceKelvin: Float = 5500f, // 2000K to 10000K
    val whiteBalancePreset: String = "As Shot",
    val isBlackAndWhite: Boolean = false,
    val hslAdjustments: Map<ColorBand, HslBandAdjustment> = emptyMap(),
    val curves: RgbCurves = RgbCurves(),
    val levels: LevelsParams = LevelsParams(),
    val colorBalance: ColorBalanceParams = ColorBalanceParams(),
    val splitToning: SplitToningParams = SplitToningParams(),
    val gradientMap: GradientMapType = GradientMapType.NONE,
    val duotone: DuotoneParams = DuotoneParams(),
    val colorReplace: ColorReplaceParams = ColorReplaceParams(),
    val colorSplash: ColorSplashParams = ColorSplashParams()
)

enum class CurveChannel {
    RGB, RED, GREEN, BLUE
}

data class ProfilePreset(
    val id: String,
    val name: String,
    val category: String,
    val filterType: FilterType,
    val isFavorite: Boolean = false,
    val isNew: Boolean = false,
    val exposureOffset: Float = 0f,
    val contrastOffset: Float = 0f,
    val brightnessOffset: Float = 0f,
    val fadeOffset: Float = 0f,
    val tempOffset: Float = 0f,
    val tintOffset: Float = 0f,
    val vibranceOffset: Float = 0f,
    val saturationOffset: Float = 0f,
    val blendMode: String = "Normal"
)

enum class PresetTier3Category(val displayName: String) {
    SIMPLE("SIMPLE"),
    FX("FX"),
    BW("B&W"),
    BLUR("BLUR"),
    SKETCH("SKETCH"),
    COLORS("COLORS"),
    ARTISTIC("ARTISTIC"),
    MAGIC("MAGIC"),
    CANVAS("CANVAS")
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
    COLD_MIST("Cold Mist"),

    // Simple / Tonal Filters
    DODGER("Dodger"),
    BRL2("BRL2"),
    BRL4("BRL4"),
    APR2("APR2"),
    WARM_COLOR("Warm Color"),
    LIGHT_CROSS("Light Cross"),
    CRISP("Crisp"),
    SHARPEN("Sharpen"),
    RETRO("Retro"),
    SUNNY("Sunny"),
    WARM_AMBER("Warm Amber"),

    // FX Filters
    HDR1("HDR1"),
    NOISE("Noise"),
    FILM2("Film2"),
    FILM3("Film3"),
    HOLGA2("Holga2"),
    VNYL3("VNYL3"),
    INDIE1("INDIE1"),
    GLITCH("Glitch"),

    // B&W Filters
    BW_HICON("B&W HiCon"),
    BW_LOWCON("B&W LowCon"),
    FILM_BW("Film B&W"),

    // Blur Filters
    BLUR("Blur"),
    FOCAL_ZOOM("Focal Zoom"),
    SMART_BLUR("Smart Blur"),

    // Sketch Filters
    CARTOON1("Cartoon1"),
    CARTOON2("Cartoon2"),
    SKTCH1("SKTCH1"),
    INK_CONTOUR("Ink Contour"),

    // Colors Filters
    COLOR_SPLASH("Color Splash"),
    HUE_SHIFT("Hue Shift"),
    INVERT("Invert"),
    COLORIZE("Colorize"),
    POP_ART("Pop Art"),

    // Artistic Effects
    CNV1("CNV1"),
    OIL_PAINTING("Oil Painting"),
    POLYGON("Polygon"),
    POSTER("Poster"),
    HALFTONE_DOTS("Halftone Dots"),
    WATERCOLOR("Watercolor"),
    PENCIL_SKETCH("Pencil Sketch"),
    CARTOON("Cartoon"),
    HALFTONE("Halftone"),
    EMBOSS("Emboss"),
    MOSAIC("Mosaic"),
    PIXELATE("Pixelate"),
    KALEIDOSCOPE("Kaleidoscope"),
    DOUBLE_EXPOSURE("Double Exposure"),
    CHROMATIC_ABERRATION("Chromatic Shift"),
    RGB_GLITCH("RGB Glitch"),
    VHS("Analog VHS"),
    LIGHT_LEAK("Light Leak"),
    BOKEH("Bokeh Lights"),
    RADIAL_BLUR("Radial Blur"),
    MOTION_BLUR("Motion Blur"),
    TEXTURE_OVERLAY("Vintage Texture"),
    PATTERN_OVERLAY("Pattern Screen"),
    COLOR_HALATION("Color Halation"),

    // Magic Filters
    FLORA("Flora"),
    UNDEAD("Undead"),
    WHITE_ICE("White Ice"),
    GEODE("Geode"),
    SKETCHY("Sketchy"),
    POW("Pow"),
    FLARE("Flare"),
    ANIMATION("Animation"),

    // Canvas Filters
    CANVAS1("Canvas 1"),
    CANVAS2("Canvas 2"),
    GRAINY_CANVAS("Grainy Canvas"),
    LINEN("Linen"),
    PAPER("Paper"),
    GRUNGE("Grunge")
}

enum class RetouchType(val label: String) {
    BLEMISH("Blemish Removal"),
    OBJECT_REMOVAL("Object Erase"),
    SKIN_SMOOTH("Skin Smoothing"),
    RED_EYE("Red-Eye Fix"),
    SELECTIVE_BRUSH("Selective Retouch"),
    PATCH_HEALING("Patch Healing"),
    CLONE_STAMP("Clone Stamp"),
    SMUDGE_BRUSH("Smudge Brush"),
    DODGE_BRUSH("Dodge Brush"),
    BURN_BRUSH("Burn Brush"),
    TEETH_WHITENING("Teeth Whitening"),
    EYE_ENHANCEMENT("Eye Enhance"),
    EYE_COLOUR("Eye Colour"),
    WRINKLE_REMOVAL("Wrinkle Removal"),
    FACE_RESHAPE("Face Reshape"),
    NOSE_RESHAPE("Nose Reshape"),
    LIP_RESHAPE("Lip Reshape"),
    HAIR_RECOLOUR("Hair Recolour"),
    MAKEUP_BRUSH("Makeup Brush"),
    SKIN_TONE("Skin Tone")
}

data class RetouchStroke(
    val type: RetouchType,
    val points: List<Offset>,
    val radius: Float = 28f,
    val intensity: Float = 1.0f,
    val sourcePoint: Offset? = null, // for clone stamp & patch tool
    val tintColor: Color = Color(0xFF2979FF) // for eye color, hair, makeup
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
    val isCutoutActive: Boolean = false,
    val aiSubjectIsolation: Boolean = false,
    val personOnly: Boolean = false,
    val hairRefinement: Boolean = true,
    val invertMask: Boolean = false,
    val maskFeather: Float = 4f,     // 0..20f
    val maskDensity: Float = 1.0f,   // 0..1f
    val maskBlur: Float = 0f,        // 0..20f
    val subjectShadow: Boolean = false,
    val shadowDistance: Float = 16f,
    val shadowAngle: Float = 45f,
    val shadowBlur: Float = 18f,
    val shadowOpacity: Float = 0.55f
)

enum class DrawBlendMode(val label: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    DARKEN("Darken"),
    LIGHTEN("Lighten"),
    ADD("Add / Glow")
}

data class DrawLayer(
    val id: String,
    val name: String,
    val isVisible: Boolean = true,
    val opacity: Float = 1.0f,
    val blendMode: DrawBlendMode = DrawBlendMode.NORMAL,
    val isLocked: Boolean = false
)

enum class DrawSymmetryMode(val label: String) {
    OFF("Off"),
    VERTICAL("Vertical Mirror"),
    HORIZONTAL("Horizontal Mirror"),
    QUAD("4-Way Radial")
}

enum class DrawShapeType(val label: String) {
    LINE("Line"),
    ARROW("Arrow"),
    RECTANGLE("Rectangle"),
    ROUNDED_RECT("Rounded Rect"),
    CIRCLE("Circle"),
    TRIANGLE("Triangle"),
    STAR("Star"),
    HEART("Heart"),
    POLYGON("Polygon")
}

enum class BrushType(val label: String) {
    PEN("Pen"),
    GLOW("Neon Glow"),
    HIGHLIGHTER("Marker"),
    DOTTED("Dotted"),
    ERASER("Eraser"),
    PENCIL("Pencil Texture"),
    SOFT_AIRBRUSH("Soft Airbrush"),
    HARD_AIRBRUSH("Hard Airbrush"),
    SPRAY_PAINT("Spray Paint"),
    TEXTURE("Texture Brush"),
    PATTERN("Pattern Brush"),
    WATERCOLOR("Watercolor"),
    OIL("Oil Impasto"),
    CALLIGRAPHY("Calligraphy"),
    SMUDGE("Smudge"),
    BLUR("Blur Brush"),
    PIXEL("Pixel Brush"),
    SHAPE("Shape Scatter"),
    SPARKLE("Sparkle / Glitter"),
    RAINBOW("Rainbow Brush"),
    CHISEL("Chisel Pen")
}

data class DrawingStroke(
    val id: String,
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val brushType: BrushType,
    val opacity: Float = 1.0f,
    val hardness: Float = 1.0f,
    val layerId: String = "layer_1"
)

enum class TextFontFamily(val label: String) {
    DEFAULT("System Clean"),
    SANS_SERIF("Modern Sans"),
    SERIF("Classic Serif"),
    MONOSPACE("Code Mono"),
    CURSIVE("Script Calligraphy"),
    DISPLAY("Impact Bold"),
    TECH("Futuristic Tech"),
    HANDWRITING("Handwritten Casual")
}

enum class TextFontWeight(val label: String) {
    THIN("Thin"),
    NORMAL("Regular"),
    BOLD("Bold"),
    BLACK("Heavy Black")
}

enum class TextWarpStyle(val label: String) {
    NONE("Normal"),
    ARCH("Arch Upper"),
    FLAG("Flag Wave"),
    FISH("Fish Eye"),
    WAVE("Sinusoidal Wave")
}

enum class TextAlignment(val label: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right")
}

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
    val scale: Float = 1.0f,
    val fontFamily: TextFontFamily = TextFontFamily.DEFAULT,
    val fontWeight: TextFontWeight = TextFontWeight.BOLD,
    val curvedArcDegrees: Float = 0f, // -180..180
    val gradientColors: List<Color>? = null,
    val strokeWidth: Float = 0f,
    val strokeColor: Color = Color.Black,
    val shadowColor: Color? = null,
    val shadowRadius: Float = 6f,
    val shadowOffset: Offset = Offset(2f, 2f),
    val glowColor: Color? = null,
    val glowRadius: Float = 12f,
    val warpStyle: TextWarpStyle = TextWarpStyle.NONE,
    val isOutlineOnly: Boolean = false,
    val characterSpacing: Float = 0f,
    val alignment: TextAlignment = TextAlignment.CENTER
)

data class StickerItem(
    val id: String,
    val emojiOrRes: String = "",
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.5f,
    val sizeDp: Float = 64f,
    val rotation: Float = 0f,
    val scale: Float = 1.0f,
    val category: String = "Emojis",
    val outlineWidth: Float = 0f,
    val outlineColor: Color = Color.White,
    val tintColor: Color? = null,
    val shadowRadius: Float = 0f,
    val shadowColor: Color = Color.Black.copy(alpha = 0.4f),
    val isCustomImage: Boolean = false,
    val customUri: String? = null,
    val stickerId: String = emojiOrRes
)

enum class ShapeType(val label: String) {
    RECTANGLE("Rectangle"),
    ROUNDED_RECTANGLE("Rounded Rect"),
    CIRCLE("Circle"),
    STAR("Star"),
    HEART("Heart"),
    HEXAGON("Hexagon"),
    ARROW("Arrow"),
    TRIANGLE("Triangle"),
    POLYGON("Polygon"),
    LINE("Line"),
    CURVED_LINE("Curved Line"),
    FREEFORM("Freeform Vector")
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
    val gradientFill: List<Color>? = null,
    val strokeWidth: Float = 4f,
    val rotation: Float = 0f,
    val cornerRadius: Float = 16f,
    val polygonSides: Int = 5,
    val shadowRadius: Float = 0f,
    val shadowColor: Color = Color.Black.copy(alpha = 0.4f),
    val blurRadius: Float = 0f,
    val isMask: Boolean = false
)

enum class FrameStyle(val label: String) {
    NONE("None"),
    POLAROID("Polaroid Classic"),
    FILM_STRIP("Film 35mm"),
    MODERN_WHITE("Gallery White"),
    DOUBLE_BORDER("Double Outline"),
    VINTAGE_FADED("Retro Fade"),
    MINIMAL_BLACK("Minimal Dark"),
    ROUNDED_FRAME("Rounded Corner"),
    DECORATIVE_VINTAGE("Decorative Antique"),
    CAMERA_VIEWFINDER("Camera Viewfinder"),
    INSTAX_MINI("Instax Mini"),
    SX70_VINTAGE("SX-70 Instant"),
    GRADIENT_BORDER("Gradient Glow"),
    PATTERN_STITCH("Stitched Border"),
    PATTERN_CHAIN("Vintage Gold Filigree"),
    SHADOW_FLOAT("3D Floating Card")
}

data class BorderSettings(
    val widthRatio: Float = 0f, // 0 to 0.15f
    val color: Color = Color.White,
    val cornerRadiusRatio: Float = 0f,
    val paddingRatio: Float = 0f,
    val innerShadow: Boolean = false
)

enum class LensFlareType(val label: String) {
    SUNBURST("Sunburst Core"),
    ANAMORPHIC("Anamorphic Blue"),
    GOLDEN_HALO("Golden Halo"),
    RAINBOW_RING("Rainbow Ring"),
    LENS_GHOSTS("Optical Lens Ghosts"),
    ANAMORPHIC_STREAKS("Horizontal Light Streaks"),
    SUN_RAYS("Volumetric Sun Rays"),
    LIGHT_LEAK("Analog Light Leak"),
    BOKEH_LIGHTS("Out-of-Focus Bokeh"),
    PRISM_REFRACTION("Prism Rainbow Beam"),
    GLOW_BLOOM("Dreamy Diffusion Bloom"),
    STAR_FILTER("Diffraction Star Sparkle")
}

data class LensFlareItem(
    val id: String,
    val xRatio: Float = 0.3f,
    val yRatio: Float = 0.25f,
    val intensity: Float = 0.8f,
    val flareType: LensFlareType = LensFlareType.SUNBURST
)

enum class CalloutStyle(val label: String) {
    SPEECH_BUBBLE("Speech Balloon"),
    THOUGHT_BUBBLE("Thought Cloud"),
    ARROW_CALLOUT("Arrow Pointer"),
    LABEL_CALLOUT("Label Tag"),
    NUMBERED_CALLOUT("Numbered Pin"),
    COMIC_BURST("Comic Burst"),
    CLOUD_BUBBLE("Cloud Shape"),
    DUAL_TAIL("Multi-tail Bubble")
}

data class CalloutItem(
    val id: String,
    val text: String,
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.4f,
    val isThoughtBubble: Boolean = false,
    val style: CalloutStyle = CalloutStyle.SPEECH_BUBBLE,
    val tailPointRatio: Offset = Offset(0.4f, 0.65f),
    val tailPoint2Ratio: Offset? = null,
    val badgeNumber: Int = 1,
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
    BIKER_LEATHER("Biker Leather", "Sharp matte leather styling"),
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
) {
    val autoEnhance: Boolean get() = autoEnhanceEnabled
    val generativeExpandRatio: Float get() = expandOutpaintRatio
    val styleTransfer: AiStyleTransfer? get() = activeStyleTransfer
    val tryOnStyle: AiTryOnStyle? get() = activeTryOn
}

enum class OverlayLayerType(val label: String) {
    IMAGE("Image layer"),
    ADJUSTMENT("Adjustment layer"),
    TYPE("Type layer"),
    FILL("Fill layer"),
    EMPTY("Empty layer")
}

enum class AdjustmentLayerType(val label: String, val subtitle: String) {
    HUE_SATURATION("Hue and Saturation", "Change colors, saturation, and lightness"),
    BRIGHTNESS_CONTRAST("Brightness and Contrast", "Make simple adjustments to the tonal range"),
    BLACK_AND_WHITE("Black and white", "Change color to black and white"),
    COLOR_BALANCE("Color balance", "Adjust the balance of color throughout"),
    EXPOSURE("Exposure", "Make adjustments to the amount of light"),
    CURVES("Curves", "Fine-tune tone curve"),
    LEVELS("Levels", "Adjust tonal range and midtones")
}

data class BlackAndWhiteSettings(
    val reds: Float = 40f,
    val yellows: Float = 60f,
    val greens: Float = 40f,
    val cyans: Float = 60f,
    val blues: Float = 20f,
    val magentas: Float = 80f
)

data class LayerMask(
    val isEnabled: Boolean = true,
    val isInverted: Boolean = false,
    val feather: Float = 0f,
    val contractExpand: Float = 0f,
    val smooth: Float = 0f,
    val density: Float = 1.0f,
    val hasSubjectMask: Boolean = true
)

data class EditorOverlayLayer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Layer 1",
    val type: OverlayLayerType = OverlayLayerType.IMAGE,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val opacity: Float = 1.0f,
    val blendMode: String = "Normal",
    val isClipped: Boolean = false,
    val isMaskSelected: Boolean = false,
    // Normalized 0f..1f center position and sizing relative to base image
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.5f,
    val widthRatio: Float = 0.85f,
    val heightRatio: Float = 0.85f,
    val rotationDegrees: Float = 0f,
    val scaleFactor: Float = 1.0f,
    val isFlippedH: Boolean = false,
    val isFlippedV: Boolean = false,
    // Content payload
    val imageUri: String? = null,
    val stockImageKey: String? = null, // "space", "propplane", "astronaut", "whale", "white_canvas"
    val adjustmentType: AdjustmentLayerType? = null,
    val bwSettings: BlackAndWhiteSettings = BlackAndWhiteSettings(),
    val hueShift: Float = 0f,           // -180..+180
    val satShift: Float = 0f,           // -100..+100
    val lightnessShift: Float = 0f,     // -100..+100
    val isColorize: Boolean = false,
    val fillSolidColor: Color = Color.White,
    val textContent: String = "Sample Text",
    val textColor: Color = Color.White,
    val textBgColor: Color = Color.Transparent,
    val fontSizeSp: Float = 28f,
    // Mask as a direct property and child of this layer
    val mask: LayerMask? = null
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
    val filterBlendMode: String = "Normal",
    val retouchStrokes: List<RetouchStroke> = emptyList(),
    val background: BackgroundSettings = BackgroundSettings(),
    val drawingStrokes: List<DrawingStroke> = emptyList(),
    val drawLayers: List<DrawLayer> = listOf(DrawLayer("layer_1", "Layer 1")),
    val activeDrawLayerId: String = "layer_1",
    val drawSymmetryMode: DrawSymmetryMode = DrawSymmetryMode.OFF,
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val stickers: List<StickerItem> = emptyList(),
    val shapes: List<ShapeItem> = emptyList(),
    val frame: FrameStyle = FrameStyle.NONE,
    val border: BorderSettings = BorderSettings(),
    val lensFlares: List<LensFlareItem> = emptyList(),
    val callouts: List<CalloutItem> = emptyList(),
    val aiSettings: AiEditSettings = AiEditSettings(),
    val activeSelectionPath: List<Offset> = emptyList(),
    val isSelectionActive: Boolean = false,
    val maskLayers: List<LightcraftMaskLayer> = emptyList(),
    val activeMaskId: String? = null,
    val activeComponentId: String? = null,
    val maskOverlaySettings: MaskOverlaySettings = MaskOverlaySettings(),
    val overlayLayers: List<EditorOverlayLayer> = emptyList(),
    val activeOverlayLayerId: String? = null,
    val isMaskTargetActive: Boolean = false,
    val description: String = "Initial Image"
)
