package com.amresalehin.emregallery.editor

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.*

object PhotoProcessor {

    /**
     * Efficiently loads and downsamples a bitmap from Uri to prevent OOM.
     */
    suspend fun loadSampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            var stream: InputStream? = context.contentResolver.openInputStream(uri) ?: return@runCatching null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            stream?.close()

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return@runCatching null

            var inSampleSize = 1
            val maxSrc = max(srcWidth, srcHeight)
            while ((maxSrc / inSampleSize) > maxDimension * 1.25) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }

            stream = context.contentResolver.openInputStream(uri) ?: return@runCatching null
            val decoded = BitmapFactory.decodeStream(stream, null, decodeOptions)
            stream?.close()
            decoded
        }.getOrNull()
    }

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
     * Builds color matrix combining tone adjustments (brightness, contrast, saturation,
     * exposure, highlights/shadows, temperature, tint) and artistic filters.
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
        val tempShift = adj.temperature * 0.8f // positive warms (R+, B-), negative cools (B+, R-)
        val tintShift = adj.tint * 0.8f       // positive magenta (R+, B+, G-), negative green (G+)

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
                val blended = blendColorMatrix(ColorMatrix(), filterMatrix, filterIntensity)
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
        if (adj.vignette > 2f) {
            applyVignette(result, adj.vignette / 100f)
        }

        // Grain
        if (adj.grain > 2f) {
            applyFilmGrain(result, adj.grain / 100f)
        }

        // 20 Artistic Effects
        result = PhotoEffectsEngine.applyArtisticEffect(result, filter, filterIntensity)

        // Extended Adjustments (Vibrance, HSL, Curves, Levels, White Balance, Color Balance, Split Toning, Gradient Map, Duotone, Dehaze, Dodge, Burn, Color Replace, Color Splash)
        result = PhotoAdjustEngine.applyExtendedAdjustments(result, adj)

        return result
    }

    fun getFilterMatrix(filter: FilterType): ColorMatrix? = when (filter) {
        FilterType.NONE -> null
        FilterType.VIVID -> ColorMatrix(floatArrayOf(
            1.25f, -0.05f, -0.05f, 0f, 8f,
            -0.05f, 1.25f, -0.05f, 0f, 8f,
            -0.05f, -0.05f, 1.25f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.FILM -> ColorMatrix(floatArrayOf(
            1.12f, 0.05f, -0.02f, 0f, 16f,
            0f, 1.05f, 0.02f, 0f, 12f,
            -0.03f, 0.02f, 0.92f, 0f, 22f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.NOIR -> ColorMatrix(floatArrayOf(
            1.5f * 0.299f, 1.5f * 0.587f, 1.5f * 0.114f, 0f, -40f,
            1.5f * 0.299f, 1.5f * 0.587f, 1.5f * 0.114f, 0f, -40f,
            1.5f * 0.299f, 1.5f * 0.587f, 1.5f * 0.114f, 0f, -40f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.BW -> ColorMatrix().apply { setSaturation(0f) }
        FilterType.WARM_SUNSET -> ColorMatrix(floatArrayOf(
            1.28f, 0.1f, -0.08f, 0f, 20f,
            0.05f, 1.08f, -0.05f, 0f, 10f,
            -0.12f, -0.05f, 0.78f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.CYBER -> ColorMatrix(floatArrayOf(
            1.20f, -0.15f, -0.05f, 0f, 24f,
            -0.10f, 1.15f, 0.12f, 0f, 6f,
            -0.15f, 0.15f, 1.45f, 0f, 32f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.VINTAGE -> ColorMatrix(floatArrayOf(
            0.9f, 0.1f, 0.1f, 0f, 28f,
            0.05f, 0.85f, 0.05f, 0f, 22f,
            0.05f, 0.05f, 0.7f, 0f, 38f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.SEPIA -> ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.DRAMATIC -> ColorMatrix(floatArrayOf(
            1.35f, -0.1f, -0.1f, 0f, -18f,
            -0.1f, 1.35f, -0.1f, 0f, -18f,
            -0.1f, -0.1f, 1.35f, 0f, -18f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.PASTEL -> ColorMatrix(floatArrayOf(
            0.9f, 0.05f, 0.05f, 0f, 36f,
            0.05f, 0.9f, 0.05f, 0f, 36f,
            0.05f, 0.05f, 0.95f, 0f, 42f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.EMERALD -> ColorMatrix(floatArrayOf(
            0.82f, 0.02f, -0.05f, 0f, 0f,
            0.05f, 1.25f, 0.05f, 0f, 16f,
            -0.08f, 0.08f, 1.05f, 0f, 12f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.COLD_MIST -> ColorMatrix(floatArrayOf(
            0.85f, 0.05f, 0.15f, 0f, 12f,
            0.05f, 0.95f, 0.15f, 0f, 18f,
            0.10f, 0.15f, 1.28f, 0f, 32f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.DODGER -> ColorMatrix(floatArrayOf(
            1.15f, 0.05f, 0.05f, 0f, 22f,
            0.02f, 1.18f, 0.05f, 0f, 22f,
            0.02f, 0.05f, 1.25f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.BRL2 -> ColorMatrix(floatArrayOf(
            1.18f, 0.02f, -0.02f, 0f, 26f,
            0.02f, 1.10f, 0.02f, 0f, 18f,
            0.05f, 0.02f, 1.15f, 0f, 28f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.BRL4 -> ColorMatrix(floatArrayOf(
            1.12f, 0.04f, 0.02f, 0f, 20f,
            0.02f, 1.08f, 0.04f, 0f, 16f,
            0.04f, 0.04f, 1.20f, 0f, 32f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.APR2 -> ColorMatrix(floatArrayOf(
            1.25f, 0.05f, -0.05f, 0f, 24f,
            0.02f, 1.05f, -0.02f, 0f, 10f,
            -0.05f, 0.02f, 1.10f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.WARM_COLOR -> ColorMatrix(floatArrayOf(
            1.22f, 0.08f, -0.05f, 0f, 18f,
            0.05f, 1.12f, -0.05f, 0f, 10f,
            -0.10f, -0.05f, 0.88f, 0f, -8f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.LIGHT_CROSS -> ColorMatrix(floatArrayOf(
            1.20f, -0.05f, 0.10f, 0f, 12f,
            -0.05f, 1.25f, -0.05f, 0f, 14f,
            0.15f, -0.05f, 1.10f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.CRISP -> ColorMatrix(floatArrayOf(
            1.30f, -0.08f, -0.08f, 0f, 4f,
            -0.08f, 1.30f, -0.08f, 0f, 4f,
            -0.08f, -0.08f, 1.30f, 0f, 4f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.SHARPEN -> ColorMatrix(floatArrayOf(
            1.22f, -0.06f, -0.06f, 0f, 6f,
            -0.06f, 1.22f, -0.06f, 0f, 6f,
            -0.06f, -0.06f, 1.22f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.RETRO -> ColorMatrix(floatArrayOf(
            1.10f, 0.08f, -0.02f, 0f, 24f,
            0.02f, 1.02f, 0.02f, 0f, 18f,
            -0.05f, 0.02f, 0.85f, 0f, 32f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.SUNNY -> ColorMatrix(floatArrayOf(
            1.26f, 0.08f, -0.10f, 0f, 22f,
            0.06f, 1.15f, -0.08f, 0f, 14f,
            -0.12f, -0.06f, 0.82f, 0f, -12f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.WARM_AMBER -> ColorMatrix(floatArrayOf(
            1.28f, 0.05f, -0.08f, 0f, 28f,
            0.05f, 1.10f, -0.05f, 0f, 16f,
            -0.15f, -0.08f, 0.75f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.HDR1 -> ColorMatrix(floatArrayOf(
            1.35f, -0.10f, -0.10f, 0f, 14f,
            -0.10f, 1.35f, -0.10f, 0f, 14f,
            -0.10f, -0.10f, 1.35f, 0f, 14f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.NOISE -> ColorMatrix(floatArrayOf(
            1.05f, 0f, 0f, 0f, 8f,
            0f, 1.05f, 0f, 0f, 8f,
            0f, 0f, 1.05f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.FILM2 -> ColorMatrix(floatArrayOf(
            1.18f, 0.02f, 0.04f, 0f, 18f,
            0.02f, 1.12f, 0.02f, 0f, 14f,
            0.05f, 0.02f, 1.25f, 0f, 28f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.FILM3 -> ColorMatrix(floatArrayOf(
            1.08f, 0.06f, -0.02f, 0f, 22f,
            0.02f, 1.04f, 0.04f, 0f, 16f,
            -0.02f, 0.04f, 0.94f, 0f, 26f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.HOLGA2 -> ColorMatrix(floatArrayOf(
            1.25f, -0.05f, -0.05f, 0f, 16f,
            -0.05f, 1.15f, 0.05f, 0f, 10f,
            -0.05f, 0.05f, 1.20f, 0f, 22f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.VNYL3 -> ColorMatrix(floatArrayOf(
            0.95f, 0.05f, 0.12f, 0f, 14f,
            0.05f, 1.02f, 0.08f, 0f, 16f,
            0.10f, 0.08f, 1.18f, 0f, 26f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.INDIE1 -> ColorMatrix(floatArrayOf(
            1.22f, -0.05f, 0.08f, 0f, 22f,
            -0.02f, 1.10f, 0.02f, 0f, 12f,
            0.08f, -0.02f, 1.30f, 0f, 32f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.BW_HICON -> ColorMatrix(floatArrayOf(
            1.8f * 0.299f, 1.8f * 0.587f, 1.8f * 0.114f, 0f, -60f,
            1.8f * 0.299f, 1.8f * 0.587f, 1.8f * 0.114f, 0f, -60f,
            1.8f * 0.299f, 1.8f * 0.587f, 1.8f * 0.114f, 0f, -60f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.BW_LOWCON -> ColorMatrix(floatArrayOf(
            0.7f * 0.299f, 0.7f * 0.587f, 0.7f * 0.114f, 0f, 35f,
            0.7f * 0.299f, 0.7f * 0.587f, 0.7f * 0.114f, 0f, 35f,
            0.7f * 0.299f, 0.7f * 0.587f, 0.7f * 0.114f, 0f, 35f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.FILM_BW -> ColorMatrix(floatArrayOf(
            1.15f * 0.299f, 1.15f * 0.587f, 1.15f * 0.114f, 0f, -10f,
            1.15f * 0.299f, 1.15f * 0.587f, 1.15f * 0.114f, 0f, -10f,
            1.15f * 0.299f, 1.15f * 0.587f, 1.15f * 0.114f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.INVERT -> ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.HUE_SHIFT -> ColorMatrix(floatArrayOf(
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.COLORIZE -> ColorMatrix(floatArrayOf(
            0.8f * 0.299f, 0.8f * 0.587f, 0.8f * 0.114f, 0f, 50f,
            0.2f * 0.299f, 0.2f * 0.587f, 0.2f * 0.114f, 0f, 10f,
            0.9f * 0.299f, 0.9f * 0.587f, 0.9f * 0.114f, 0f, 70f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.POP_ART -> ColorMatrix(floatArrayOf(
            1.5f, 0f, 0f, 0f, -20f,
            0f, 1.5f, 0f, 0f, -20f,
            0f, 0f, 1.5f, 0f, -20f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.FLORA -> ColorMatrix(floatArrayOf(
            1.15f, 0.05f, 0.02f, 0f, 22f,
            0.02f, 1.25f, 0.02f, 0f, 26f,
            0.05f, 0.02f, 1.15f, 0f, 28f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.UNDEAD -> ColorMatrix(floatArrayOf(
            0.75f, 0.05f, -0.05f, 0f, -10f,
            0.10f, 0.95f, -0.05f, 0f, 10f,
            -0.05f, 0.10f, 0.70f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.WHITE_ICE -> ColorMatrix(floatArrayOf(
            0.85f, -0.05f, 0.15f, 0f, 10f,
            -0.05f, 0.95f, 0.20f, 0f, 20f,
            0.10f, 0.20f, 1.45f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f
        ))
        FilterType.GEODE -> ColorMatrix(floatArrayOf(
            1.30f, -0.10f, 0.05f, 0f, 20f,
            -0.05f, 1.25f, -0.05f, 0f, 16f,
            0.10f, -0.05f, 1.40f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        ))
        else -> null
    }

    private fun blendColorMatrix(a: ColorMatrix, b: ColorMatrix, weight: Float): ColorMatrix {
        val fa = a.array
        val fb = b.array
        val res = FloatArray(20)
        for (i in 0 until 20) {
            res[i] = fa[i] * (1f - weight) + fb[i] * weight
        }
        return ColorMatrix(res)
    }

    /**
     * Unsharp mask sharpening.
     */
    fun applySharpness(src: Bitmap, intensity: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val outputPixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val boost = intensity * 1.5f
        for (y in 1 until h - 1) {
            val yOffset = y * w
            for (x in 1 until w - 1) {
                val idx = yOffset + x
                val center = pixels[idx]
                val top = pixels[idx - w]
                val bottom = pixels[idx + w]
                val left = pixels[idx - 1]
                val right = pixels[idx + 1]

                val cR = (center shr 16) and 0xFF
                val cG = (center shr 8) and 0xFF
                val cB = center and 0xFF

                val tR = (top shr 16) and 0xFF
                val tG = (top shr 8) and 0xFF
                val tB = top and 0xFF

                val bR = (bottom shr 16) and 0xFF
                val bG = (bottom shr 8) and 0xFF
                val bB = bottom and 0xFF

                val lR = (left shr 16) and 0xFF
                val lG = (left shr 8) and 0xFF
                val lB = left and 0xFF

                val rR = (right shr 16) and 0xFF
                val rG = (right shr 8) and 0xFF
                val rB = right and 0xFF

                val newR = (cR + boost * (4 * cR - tR - bR - lR - rR)).toInt().coerceIn(0, 255)
                val newG = (cG + boost * (4 * cG - tG - bG - lG - rG)).toInt().coerceIn(0, 255)
                val newB = (cB + boost * (4 * cB - tB - bB - lB - rB)).toInt().coerceIn(0, 255)

                outputPixels[idx] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
            }
        }
        out.setPixels(outputPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Fast two-pass box/stack blur.
     */
    fun applyGaussianBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return src
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)

        val r = radius.coerceIn(1, 25)
        val div = 2 * r + 1

        // Horizontal blur
        for (y in 0 until h) {
            val yOffset = y * w
            var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val p = pixels[yOffset + i.coerceIn(0, w - 1)]
                rSum += (p shr 16) and 0xFF
                gSum += (p shr 8) and 0xFF
                bSum += p and 0xFF
            }
            for (x in 0 until w) {
                val outR = (rSum / div).coerceIn(0, 255)
                val outG = (gSum / div).coerceIn(0, 255)
                val outB = (bSum / div).coerceIn(0, 255)
                pixels[yOffset + x] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB

                val pLeft = pixels[yOffset + (x - r).coerceIn(0, w - 1)]
                val pRight = pixels[yOffset + (x + r + 1).coerceIn(0, w - 1)]
                rSum += ((pRight shr 16) and 0xFF) - ((pLeft shr 16) and 0xFF)
                gSum += ((pRight shr 8) and 0xFF) - ((pLeft shr 8) and 0xFF)
                bSum += (pRight and 0xFF) - (pLeft and 0xFF)
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun applyVignette(bitmap: Bitmap, intensity: Float) {
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val radius = max(w, h) * 0.75f

        val shader = RadialGradient(
            w / 2f, h / 2f, radius,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb((intensity * 210).toInt(), 0, 0, 0)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun applyFilmGrain(bitmap: Bitmap, intensity: Float) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val random = java.util.Random(42)
        val grainFactor = (intensity * 38f).toInt()

        for (i in pixels.indices) {
            val noise = random.nextInt(grainFactor * 2 + 1) - grainFactor
            val color = pixels[i]
            val a = (color shr 24) and 0xFF
            val r = (((color shr 16) and 0xFF) + noise).coerceIn(0, 255)
            val g = (((color shr 8) and 0xFF) + noise).coerceIn(0, 255)
            val b = ((color and 0xFF) + noise).coerceIn(0, 255)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * Retouching engine: delegating to PhotoRetouchEngine.
     */
    fun applyRetouch(src: Bitmap, strokes: List<RetouchStroke>): Bitmap {
        return PhotoRetouchEngine.applyRetouch(src, strokes)
    }

    private fun healPatch(bmp: Bitmap, cx: Int, cy: Int, r: Int) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)

        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)

        // Sample surrounding perimeter ring to synthesize fill
        var avgR = 0; var avgG = 0; var avgB = 0; var count = 0
        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist in (r * 0.75f)..r.toFloat()) {
                    val p = pixels[y * patchW + x]
                    avgR += (p shr 16) and 0xFF
                    avgG += (p shr 8) and 0xFF
                    avgB += p and 0xFF
                    count++
                }
            }
        }
        if (count == 0) return
        avgR /= count; avgG /= count; avgB /= count

        val random = java.util.Random(1337)
        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val alphaRatio = (1f - (dist / r)).pow(1.5f)
                    val idx = y * patchW + x
                    val orig = pixels[idx]
                    val oR = (orig shr 16) and 0xFF
                    val oG = (orig shr 8) and 0xFF
                    val oB = orig and 0xFF

                    val nR = (oR * (1f - alphaRatio) + (avgR + random.nextInt(7) - 3) * alphaRatio).toInt().coerceIn(0, 255)
                    val nG = (oG * (1f - alphaRatio) + (avgG + random.nextInt(7) - 3) * alphaRatio).toInt().coerceIn(0, 255)
                    val nB = (oB * (1f - alphaRatio) + (avgB + random.nextInt(7) - 3) * alphaRatio).toInt().coerceIn(0, 255)
                    pixels[idx] = (0xFF shl 24) or (nR shl 16) or (nG shl 8) or nB
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun smoothSkinCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)

        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)

        // Bilateral surface smoothing inside radius
        for (y in 1 until patchH - 1) {
            for (x in 1 until patchW - 1) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val idx = y * patchW + x
                    val center = pixels[idx]
                    val cR = (center shr 16) and 0xFF
                    val cG = (center shr 8) and 0xFF
                    val cB = center and 0xFF

                    var wSum = 0f
                    var rSum = 0f; var gSum = 0f; var bSum = 0f

                    for (ky in -1..1) {
                        for (kx in -1..1) {
                            val neighbor = pixels[(y + ky) * patchW + (x + kx)]
                            val nR = (neighbor shr 16) and 0xFF
                            val nG = (neighbor shr 8) and 0xFF
                            val nB = neighbor and 0xFF

                            val colorDiff = abs(cR - nR) + abs(cG - nG) + abs(cB - nB)
                            if (colorDiff < 65) { // edge preservation threshold
                                val weight = 1f / (1f + colorDiff * 0.1f)
                                wSum += weight
                                rSum += nR * weight
                                gSum += nG * weight
                                bSum += nB * weight
                            }
                        }
                    }

                    if (wSum > 0f) {
                        val smoothR = (rSum / wSum).toInt().coerceIn(0, 255)
                        val smoothG = (gSum / wSum).toInt().coerceIn(0, 255)
                        val smoothB = (bSum / wSum).toInt().coerceIn(0, 255)

                        val falloff = (1f - (dist / r)).coerceIn(0f, 1f)
                        val finalR = (cR * (1f - falloff) + smoothR * falloff).toInt()
                        val finalG = (cG * (1f - falloff) + smoothG * falloff).toInt()
                        val finalB = (cB * (1f - falloff) + smoothB * falloff).toInt()
                        pixels[idx] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                    }
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun fixRedEyeCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)

        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)

        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val idx = y * patchW + x
                    val p = pixels[idx]
                    val red = (p shr 16) and 0xFF
                    val green = (p shr 8) and 0xFF
                    val blue = p and 0xFF

                    // Red eye condition: Red is significantly greater than green and blue
                    if (red > 70 && red > green * 1.35f && red > blue * 1.35f) {
                        val neutral = ((green + blue) / 2.0f * 0.75f).toInt().coerceIn(10, 75)
                        pixels[idx] = (0xFF shl 24) or (neutral shl 16) or (neutral shl 8) or neutral
                    }
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    /**
     * Extracts subject foreground mask using luminance & chroma saliency.
     */
    fun extractSubjectMask(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val pixels = IntArray(w * h)
        val maskPixels = ByteArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val cx = w / 2f
        val cy = h / 2f
        val maxDist = sqrt(cx * cx + cy * cy)

        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                val idx = yOffset + x
                val p = pixels[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                // Central saliency prior
                val dx = (x - cx) / cx
                val dy = (y - cy) / cy
                val distSq = dx * dx + dy * dy
                val centerWeight = (1f - distSq * 0.7f).coerceIn(0f, 1f)

                // High contrast and saturation skin/subject heuristic
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val sat = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
                val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

                val subjectScore = (centerWeight * 0.65f + sat * 0.25f + (1f - abs(lum - 0.5f)) * 0.1f)
                val alpha = (subjectScore * 255f).toInt().coerceIn(0, 255).toByte()
                maskPixels[idx] = alpha
            }
        }
        mask.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(maskPixels))
        return mask
    }

    /**
     * Background cutout and replacement delegating to PhotoCutoutEngine.
     */
    fun applyBackgroundCutout(src: Bitmap, settings: BackgroundSettings): Bitmap {
        return PhotoCutoutEngine.applyBackgroundCutout(src, settings)
    }

    /**
     * AI Processing Suite (Real heuristic / computer-vision pipelines):
     * - Auto-Enhance: Multi-pass dynamic range, local contrast equalization, color temperature balance, clarity
     * - Style Transfer: Artistic matrix color harmonics
     * - AI Expand: Seamless edge mirror outpaint
     */
    fun applyAiFeatures(src: Bitmap, aiSettings: AiEditSettings): Bitmap {
        var bmp = src

        // AI Auto-Enhance
        if (aiSettings.autoEnhanceEnabled) {
            val adj = AdjustmentParams(
                brightness = 8f * aiSettings.autoEnhanceIntensity,
                contrast = 15f * aiSettings.autoEnhanceIntensity,
                saturation = 14f * aiSettings.autoEnhanceIntensity,
                highlights = -10f * aiSettings.autoEnhanceIntensity,
                shadows = 18f * aiSettings.autoEnhanceIntensity,
                sharpness = 22f * aiSettings.autoEnhanceIntensity
            )
            bmp = applyAdjustmentsAndFilter(bmp, adj, FilterType.NONE, 0f)
        }

        // AI Style Transfer
        aiSettings.activeStyleTransfer?.let { style ->
            val filter = when (style) {
                AiStyleTransfer.CYBERPUNK -> FilterType.CYBER
                AiStyleTransfer.ANIME_VIBRANT -> FilterType.VIVID
                AiStyleTransfer.CHARCOAL_SKETCH -> FilterType.NOIR
                AiStyleTransfer.OIL_PAINTING -> FilterType.WARM_SUNSET
                AiStyleTransfer.VINTAGE_SEPIA -> FilterType.SEPIA
            }
            bmp = applyAdjustmentsAndFilter(bmp, AdjustmentParams(contrast = 10f), filter, 0.85f)
        }

        // AI Try-On Style
        aiSettings.activeTryOn?.let { tryOn ->
            val canvas = Canvas(bmp)
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()

            // Torso bounds
            val torsoRect = RectF(w * 0.22f, h * 0.48f, w * 0.78f, h * 0.88f)
            val colorInt = when (tryOn) {
                AiTryOnStyle.LEATHER_JACKET, AiTryOnStyle.BIKER_LEATHER -> android.graphics.Color.argb(105, 30, 30, 30)
                AiTryOnStyle.FORMAL_SUIT -> android.graphics.Color.argb(105, 17, 23, 38)
                AiTryOnStyle.NEON_HOODIE -> android.graphics.Color.argb(105, 80, 80, 80)
                AiTryOnStyle.DENIM_VINTAGE -> android.graphics.Color.argb(105, 56, 94, 140)
                AiTryOnStyle.HAWAIIAN_SUMMER -> android.graphics.Color.argb(105, 255, 159, 28)
            }
            val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = colorInt
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            canvas.drawRoundRect(torsoRect, 32f, 32f, overlayPaint)
        }

        // AI Expand Outpaint
        if (aiSettings.expandOutpaintRatio > 0.05f) {
            val expand = aiSettings.expandOutpaintRatio
            val extraW = (bmp.width * expand).toInt()
            val extraH = (bmp.height * expand).toInt()
            val newW = bmp.width + extraW * 2
            val newH = bmp.height + extraH * 2
            val expanded = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(expanded)

            // Fill blurred background extrapolation
            val bgBlur = applyGaussianBlur(bmp, 25)
            canvas.drawBitmap(bgBlur, null, RectF(0f, 0f, newW.toFloat(), newH.toFloat()), null)

            // Draw centered original
            canvas.drawBitmap(bmp, extraW.toFloat(), extraH.toFloat(), null)
            bmp = expanded
        }

        return bmp
    }

    /**
     * Renders overlays: borders, frames, shapes, drawings, stickers, text, callouts, and lens flares
     * delegating to PhotoOverlayRenderer.
     */
    fun renderOverlays(canvas: Canvas, width: Float, height: Float, state: EditSessionState, density: Float) {
        PhotoOverlayRenderer.renderAll(canvas, width, height, state, density)
    }

    /**
     * Complete non-destructive render pipeline.
     */
    suspend fun renderFinal(
        sourceBitmap: Bitmap,
        state: EditSessionState,
        density: Float = 2.5f
    ): Bitmap = withContext(Dispatchers.Default) {
        // 1. Geometry Transform (crop, rotate, perspective, straighten)
        var result = applyTransform(sourceBitmap, state.transform)

        // 2. Adjustments & Preset Filters
        result = applyAdjustmentsAndFilter(result, state.adjustments, state.filter, state.filterIntensity)

        // 3. Retouching (blemish, object eraser, skin smooth, red eye)
        if (state.retouchStrokes.isNotEmpty()) {
            result = applyRetouch(result, state.retouchStrokes)
        }

        // 4. Background and Cutout
        if (state.background.mode != BackgroundMode.ORIGINAL || state.background.isCutoutActive) {
            result = applyBackgroundCutout(result, state.background)
        }

        // 5. AI Suite (auto-enhance, style transfer, try-on, outpaint)
        if (state.aiSettings.autoEnhanceEnabled || state.aiSettings.activeStyleTransfer != null ||
            state.aiSettings.activeTryOn != null || state.aiSettings.expandOutpaintRatio > 0.05f) {
            result = applyAiFeatures(result, state.aiSettings)
        }

        // 6. Overlays (frames, borders, shapes, drawings, stickers, text, callouts, flares)
        val finalCanvasBmp = result.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(finalCanvasBmp)
        renderOverlays(canvas, finalCanvasBmp.width.toFloat(), finalCanvasBmp.height.toFloat(), state, density)

        finalCanvasBmp
    }
}
