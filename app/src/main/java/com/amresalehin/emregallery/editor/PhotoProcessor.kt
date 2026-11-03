package com.amresalehin.emregallery.editor

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.amresalehin.emregallery.editor.processing.BitmapLoader
import com.amresalehin.emregallery.editor.processing.FilterMatrixPresets
import com.amresalehin.emregallery.editor.processing.ImageSpatialFilters
import com.amresalehin.emregallery.editor.processing.SubjectMaskExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Facade coordinating image transformation, tone adjustment, spatial filters,
 * AI pipelines, and final compositing.
 */
object PhotoProcessor {

    /**
     * Efficiently loads and downsamples a bitmap from Uri to prevent OOM.
     */
    suspend fun loadSampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap? = BitmapLoader.loadSampledBitmap(context, uri, maxDimension)

    /**
     * Applies geometric transformations: crop, rotation, flips, fine straighten, and perspective skew.
     */
    fun applyTransform(src: Bitmap, params: TransformParams): Bitmap {
        var bmp = src

        // 1. Crop if specified
        params.cropRect?.let { crop ->
            val left = (crop.left.coerceIn(0f, 1f) * bmp.width).toInt().coerceIn(0, bmp.width - 1)
            val top = (crop.top.coerceIn(0f, 1f) * bmp.height).toInt().coerceIn(0, bmp.height - 1)
            val right = (crop.right.coerceIn(0f, 1f) * bmp.width).toInt().coerceIn(left + 1, bmp.width)
            val bottom = (crop.bottom.coerceIn(0f, 1f) * bmp.height).toInt().coerceIn(top + 1, bmp.height)
            val cropW = right - left
            val cropH = bottom - top
            if (cropW > 0 && cropH > 0 && (cropW != bmp.width || cropH != bmp.height)) {
                bmp = Bitmap.createBitmap(bmp, left, top, cropW, cropH)
            }
        }

        // 2. Shape Crop if specified
        params.shapeCrop?.let { shape ->
            bmp = PhotoTransformEngine.applyShapeCrop(bmp, shape)
        }

        // 3. Corner Pin Perspective Warp if 4 points provided
        if (!params.cornerPins.isNullOrEmpty() && params.cornerPins.size == 4) {
            bmp = PhotoTransformEngine.applyCornerPinPerspective(bmp, params.cornerPins)
        }

        // 4. Matrix transformations: rotation, flips, straighten, perspective
        val matrix = Matrix()
        val totalRotation = (params.rotationDegrees.toFloat() + params.straightenAngle)
        if (totalRotation != 0f) {
            matrix.postRotate(totalRotation, bmp.width / 2f, bmp.height / 2f)
        }
        val scaleX = if (params.flipHorizontal) -1f else 1f
        val scaleY = if (params.flipVertical) -1f else 1f
        if (scaleX != 1f || scaleY != 1f) {
            matrix.postScale(scaleX, scaleY, bmp.width / 2f, bmp.height / 2f)
        }

        // Perspective skew
        if (params.perspectiveX != 0f || params.perspectiveY != 0f) {
            val skewX = (params.perspectiveX / 100f) * 0.45f
            val skewY = (params.perspectiveY / 100f) * 0.45f
            matrix.postSkew(skewX, skewY)
        }

        if (params.scaleFactor != 1.0f && params.scaleFactor > 0.1f) {
            matrix.postScale(params.scaleFactor, params.scaleFactor)
        }

        if (!matrix.isIdentity) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }

        // 5. Liquify
        if (params.liquifyPoints.isNotEmpty()) {
            bmp = PhotoTransformEngine.applyLiquify(bmp, params.liquifyPoints)
        }

        // 6. Dispersion fragments
        if (params.dispersionAmount > 0f) {
            bmp = PhotoTransformEngine.applyDispersion(bmp, params.dispersionAmount)
        }

        // 7. Motion streaks
        if (params.motionAmount > 0f) {
            bmp = PhotoTransformEngine.applyMotion(bmp, params.motionAmount, params.motionAngle)
        }

        return bmp
    }

    /**
     * Builds color matrix combining tone adjustments and artistic filters.
     */
    fun applyAdjustmentsAndFilter(
        src: Bitmap,
        adj: AdjustmentParams,
        filter: FilterType,
        filterIntensity: Float
    ): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cm = ColorMatrix()

        // Exposure & Brightness
        val exposureScale = (1f + (adj.exposure / 100f)).coerceAtLeast(0.1f)
        val brightnessOffset = (adj.brightness * 2.55f)

        // Contrast
        val contrastFactor = if (adj.contrast >= 0) {
            1f + (adj.contrast / 100f) * 1.5f
        } else {
            1f + (adj.contrast / 100f) * 0.8f
        }
        val contrastOffset = 128f * (1f - contrastFactor)

        // Temperature (Warmth) & Tint
        val tempShift = adj.temperature * 0.8f
        val tintShift = adj.tint * 0.8f

        val rScale = exposureScale * contrastFactor * (1f + tempShift / 150f).coerceIn(0.5f, 2.0f)
        val gScale = exposureScale * contrastFactor * (1f - tintShift / 200f).coerceIn(0.5f, 2.0f)
        val bScale = exposureScale * contrastFactor * (1f - tempShift / 150f).coerceIn(0.5f, 2.0f)

        val rOffset = brightnessOffset + contrastOffset + (if (tempShift > 0) tempShift else 0f) + (if (tintShift > 0) tintShift else 0f)
        val gOffset = brightnessOffset + contrastOffset + (if (tintShift < 0) -tintShift else 0f)
        val bOffset = brightnessOffset + contrastOffset + (if (tempShift < 0) -tempShift else 0f) + (if (tintShift > 0) tintShift else 0f)

        val toneMatrix = ColorMatrix(floatArrayOf(
            rScale, 0f, 0f, 0f, rOffset,
            0f, gScale, 0f, 0f, gOffset,
            0f, 0f, bScale, 0f, bOffset,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(toneMatrix)

        // Saturation & B&W
        val effectiveSat = if (adj.isBlackAndWhite) 0f else (1f + (adj.saturation / 100f)).coerceIn(0f, 3.0f)
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(effectiveSat)
        cm.postConcat(satMatrix)

        // Whites and Blacks tone shifts
        val whitesShift = adj.whites * 0.6f
        val blacksShift = adj.blacks * 0.6f
        if (whitesShift != 0f || blacksShift != 0f) {
            val wbMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, whitesShift + blacksShift,
                0f, 1f, 0f, 0f, whitesShift + blacksShift,
                0f, 0f, 1f, 0f, whitesShift + blacksShift,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(wbMatrix)
        }

        // Preset filter matrix
        val filterMatrix = getFilterMatrix(filter)
        if (filterMatrix != null && filterIntensity > 0f) {
            if (filterIntensity >= 0.99f) {
                cm.postConcat(filterMatrix)
            } else {
                val blended = FilterMatrixPresets.blendColorMatrix(ColorMatrix(), filterMatrix, filterIntensity)
                cm.postConcat(blended)
            }
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)

        // Sharpness & Texture
        var result = out
        val totalSharp = (adj.sharpness + adj.texture * 0.4f).coerceAtLeast(0f)
        if (totalSharp > 2f) {
            result = applySharpness(result, (totalSharp / 100f) * adj.sharpnessRadius)
        }

        // Blur
        if (adj.blur > 2f) {
            result = applyGaussianBlur(result, (adj.blur / 100f * 25f).toInt().coerceAtLeast(1))
        }

        // Vignette
        if (kotlin.math.abs(adj.vignette) > 2f) {
            ImageSpatialFilters.applyVignette(
                result,
                adj.vignette / 100f,
                adj.vignetteMidpoint / 100f,
                adj.vignetteFeather / 100f,
                adj.vignetteRoundness / 100f
            )
        }

        // Grain
        if (adj.grain > 2f) {
            ImageSpatialFilters.applyFilmGrain(result, adj.grain / 100f)
        }

        // 20 Artistic Effects
        result = PhotoEffectsEngine.applyArtisticEffect(result, filter, filterIntensity)

        // Extended Adjustments
        result = PhotoAdjustEngine.applyExtendedAdjustments(result, adj)

        return result
    }

    fun getFilterMatrix(filter: FilterType): ColorMatrix? = FilterMatrixPresets.getFilterMatrix(filter)

    fun applySharpness(src: Bitmap, intensity: Float): Bitmap =
        ImageSpatialFilters.applySharpness(src, intensity)

    fun applyGaussianBlur(src: Bitmap, radius: Int): Bitmap =
        ImageSpatialFilters.applyGaussianBlur(src, radius)

    fun applyRetouch(src: Bitmap, strokes: List<RetouchStroke>): Bitmap =
        PhotoRetouchEngine.applyRetouch(src, strokes)

    fun extractSubjectMask(src: Bitmap): Bitmap =
        SubjectMaskExtractor.extractSubjectMask(src)

    fun applyBackgroundCutout(src: Bitmap, settings: BackgroundSettings): Bitmap =
        PhotoCutoutEngine.applyBackgroundCutout(src, settings)

    fun renderOverlays(canvas: Canvas, width: Float, height: Float, state: EditSessionState, density: Float) {
        PhotoOverlayRenderer.renderAll(canvas, width, height, state, density)
    }

    suspend fun renderFinal(
        sourceBitmap: Bitmap,
        state: EditSessionState,
        density: Float = 2.5f
    ): Bitmap = withContext(Dispatchers.Default) {
        // 1. Geometry Transform
        var result = applyTransform(sourceBitmap, state.transform)

        // 2. Adjustments & Preset Filters
        result = applyAdjustmentsAndFilter(result, state.adjustments, state.filter, state.filterIntensity)

        // 3. Retouching
        if (state.retouchStrokes.isNotEmpty()) {
            result = applyRetouch(result, state.retouchStrokes)
        }

        // 4. Background and Cutout
        if (state.background.mode != BackgroundMode.ORIGINAL || state.background.isCutoutActive) {
            result = applyBackgroundCutout(result, state.background)
        }

        // 5. LightCraft Masking Layers
        if (state.maskLayers.isNotEmpty()) {
            result = PhotoMaskEngine.applyMaskLayers(result, state.maskLayers)
        }

        // 6. Overlays
        val finalCanvasBmp = result.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(finalCanvasBmp)
        renderOverlays(canvas, finalCanvasBmp.width.toFloat(), finalCanvasBmp.height.toFloat(), state, density)

        finalCanvasBmp
    }
}
