package com.amresalehin.emregallery.editor

import android.graphics.*
import android.graphics.Color as AndroidColor
import kotlin.math.*
import kotlin.random.Random

/**
 * Curated high-resolution stock assets matching the video screen record:
 * - "space" / "Nebula" (Deep space cosmic nebula with starry field)
 * - "propplane" / "PropPlane" (Classic retro vintage propeller airplane in black and white)
 * - "astronaut" / "chista-astronaut" (Astronaut in silver space suit in lotus pose)
 * - "whale" (Blue whale gliding in deep space/ocean)
 * - "white_canvas" (Studio white canvas)
 */
object StockPhotoAssets {

    private val cache = mutableMapOf<String, Bitmap>()

    fun getStockBitmap(key: String, targetWidth: Int = 1080, targetHeight: Int = 1440): Bitmap {
        val normKey = key.lowercase().trim()
        val cacheKey = "${normKey}_${targetWidth}x${targetHeight}"
        cache[cacheKey]?.let { if (!it.isRecycled) return it }

        val bmp = when {
            normKey.contains("space") || normKey.contains("nebula") -> createNebulaBitmap(targetWidth, targetHeight)
            normKey.contains("plane") || normKey.contains("prop") -> createPropPlaneBitmap(targetWidth, targetHeight)
            normKey.contains("astronaut") -> createAstronautBitmap(targetWidth, targetHeight)
            normKey.contains("whale") -> createWhaleBitmap(targetWidth, targetHeight)
            normKey.contains("white") || normKey.contains("canvas") -> createCanvasBitmap(targetWidth, targetHeight)
            else -> createNebulaBitmap(targetWidth, targetHeight)
        }
        cache[cacheKey] = bmp
        return bmp
    }

    private fun createNebulaBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Dark deep-space gradient background
        val bgPaint = Paint().apply {
            shader = RadialGradient(
                w * 0.5f, h * 0.45f,
                max(w, h) * 0.75f,
                intArrayOf(
                    AndroidColor.rgb(38, 20, 68),
                    AndroidColor.rgb(18, 12, 36),
                    AndroidColor.rgb(8, 6, 18),
                    AndroidColor.rgb(3, 2, 8)
                ),
                floatArrayOf(0f, 0.4f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Colorful cosmic nebula dust clouds
        val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }

        // Center golden cosmic glow
        nebulaPaint.shader = RadialGradient(
            w * 0.52f, h * 0.42f,
            w * 0.45f,
            intArrayOf(
                AndroidColor.argb(160, 245, 180, 110),
                AndroidColor.argb(110, 190, 80, 140),
                AndroidColor.argb(50, 70, 30, 110),
                AndroidColor.TRANSPARENT
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), nebulaPaint)

        // Cyan/blue nebula arm
        nebulaPaint.shader = RadialGradient(
            w * 0.35f, h * 0.55f,
            w * 0.5f,
            intArrayOf(
                AndroidColor.argb(140, 60, 160, 230),
                AndroidColor.argb(80, 40, 90, 180),
                AndroidColor.argb(30, 20, 40, 100),
                AndroidColor.TRANSPARENT
            ),
            floatArrayOf(0f, 0.4f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), nebulaPaint)

        // Magenta/violet cosmic cloud
        nebulaPaint.shader = RadialGradient(
            w * 0.65f, h * 0.35f,
            w * 0.45f,
            intArrayOf(
                AndroidColor.argb(130, 220, 70, 160),
                AndroidColor.argb(70, 140, 40, 120),
                AndroidColor.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), nebulaPaint)

        // Starfield
        val rnd = Random(42)
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
        }

        // Small background stars
        for (i in 0 until 600) {
            val sx = rnd.nextFloat() * w
            val sy = rnd.nextFloat() * h
            val sr = 0.5f + rnd.nextFloat() * 1.6f
            val alpha = 80 + rnd.nextInt(175)
            starPaint.alpha = alpha
            canvas.drawCircle(sx, sy, sr, starPaint)
        }

        // Prominent bright stars with optical crosses
        starPaint.alpha = 255
        for (i in 0 until 18) {
            val sx = rnd.nextFloat() * w
            val sy = rnd.nextFloat() * h
            val sr = 2.5f + rnd.nextFloat() * 2.5f
            canvas.drawCircle(sx, sy, sr, starPaint)

            // Star spike
            val spikeLen = sr * 4.5f
            val spikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(180, 255, 255, 255)
                strokeWidth = 1.2f
            }
            canvas.drawLine(sx - spikeLen, sy, sx + spikeLen, sy, spikePaint)
            canvas.drawLine(sx, sy - spikeLen, sx, sy + spikeLen, spikePaint)
        }

        return bmp
    }

    private fun createPropPlaneBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Dramatic black and white cloud background
        val skyPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(
                    AndroidColor.rgb(35, 38, 42),
                    AndroidColor.rgb(90, 95, 102),
                    AndroidColor.rgb(180, 185, 192),
                    AndroidColor.rgb(120, 125, 132),
                    AndroidColor.rgb(40, 42, 46)
                ),
                floatArrayOf(0f, 0.25f, 0.55f, 0.8f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), skyPaint)

        // Billowing dramatic cloud puffs
        val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(90, 240, 242, 245)
            maskFilter = BlurMaskFilter(45f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(w * 0.25f, h * 0.35f, w * 0.28f, cloudPaint)
        canvas.drawCircle(w * 0.75f, h * 0.32f, w * 0.32f, cloudPaint)
        canvas.drawCircle(w * 0.5f, h * 0.25f, w * 0.35f, cloudPaint)

        // Airplane Fuselage & Nose Cowling (Centered front view, matching video)
        val cx = w * 0.5f
        val cy = h * 0.62f
        val planePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Airplane Wings spanning across bottom-mid
        val wingPath = Path().apply {
            moveTo(0f, cy + h * 0.12f)
            lineTo(w.toFloat(), cy + h * 0.12f)
            lineTo(w.toFloat(), cy + h * 0.05f)
            lineTo(cx + w * 0.15f, cy + h * 0.02f)
            lineTo(cx - w * 0.15f, cy + h * 0.02f)
            lineTo(0f, cy + h * 0.05f)
            close()
        }
        planePaint.color = AndroidColor.rgb(75, 78, 84)
        canvas.drawPath(wingPath, planePaint)

        // Metallic fuselage body
        planePaint.color = AndroidColor.rgb(55, 58, 64)
        val fuselageRect = RectF(cx - w * 0.24f, cy - h * 0.22f, cx + w * 0.24f, cy + h * 0.28f)
        canvas.drawRoundRect(fuselageRect, w * 0.24f, w * 0.24f, planePaint)

        // Cockpit canopy at top
        val canopyRect = RectF(cx - w * 0.12f, cy - h * 0.34f, cx + w * 0.12f, cy - h * 0.18f)
        planePaint.color = AndroidColor.rgb(20, 22, 26)
        canvas.drawRoundRect(canopyRect, w * 0.12f, w * 0.12f, planePaint)

        // Glass highlights
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(160, 220, 225, 235)
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawArc(canopyRect, 200f, 140f, false, highlightPaint)

        // Radial engine cowling (Front circle)
        planePaint.color = AndroidColor.rgb(32, 34, 38)
        val engineRadius = w * 0.21f
        canvas.drawCircle(cx, cy, engineRadius, planePaint)

        // Chrome engine rim
        val chromeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            shader = SweepGradient(
                cx, cy,
                intArrayOf(
                    AndroidColor.rgb(180, 185, 190),
                    AndroidColor.rgb(80, 85, 90),
                    AndroidColor.rgb(240, 245, 250),
                    AndroidColor.rgb(120, 125, 130),
                    AndroidColor.rgb(180, 185, 190)
                ),
                null
            )
        }
        canvas.drawCircle(cx, cy, engineRadius - 4f, chromeRim)

        // Propeller hub (Central cone)
        val hubRadius = w * 0.08f
        planePaint.color = AndroidColor.rgb(22, 24, 28)
        canvas.drawCircle(cx, cy, hubRadius, planePaint)

        // Propeller Blades (3-blade vintage aircraft)
        val bladeLength = w * 0.44f
        val bladeWidth = w * 0.085f
        val bladePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(18, 20, 24)
        }
        val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(220, 225, 230)
        }

        val angles = listOf(-90f, 30f, 150f)
        for (ang in angles) {
            canvas.save()
            canvas.rotate(ang, cx, cy)

            val bladeRect = RectF(cx - bladeWidth / 2f, cy - bladeLength, cx + bladeWidth / 2f, cy)
            canvas.drawRoundRect(bladeRect, bladeWidth / 2f, bladeWidth / 2f, bladePaint)

            // Yellow/White blade tip warning stripe
            val tipRect = RectF(cx - bladeWidth / 2f, cy - bladeLength, cx + bladeWidth / 2f, cy - bladeLength + 28f)
            canvas.drawRoundRect(tipRect, bladeWidth / 2f, bladeWidth / 2f, tipPaint)

            canvas.restore()
        }

        // Center chrome spinner cone
        val spinnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - 10f, cy - 10f, hubRadius * 0.9f,
                intArrayOf(AndroidColor.WHITE, AndroidColor.rgb(180, 185, 195), AndroidColor.rgb(40, 44, 50)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, hubRadius * 0.7f, spinnerPaint)

        return bmp
    }

    private fun createAstronautBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Studio / space background
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(AndroidColor.rgb(15, 18, 26), AndroidColor.rgb(45, 52, 70), AndroidColor.rgb(20, 24, 34)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val cx = w * 0.5f
        val cy = h * 0.48f

        // Lotus pose legs (crossed below torso)
        val silverSuitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(215, 222, 230)
        }
        val legLeft = RectF(cx - w * 0.36f, cy + h * 0.16f, cx + w * 0.05f, cy + h * 0.30f)
        val legRight = RectF(cx - w * 0.05f, cy + h * 0.16f, cx + w * 0.36f, cy + h * 0.30f)
        canvas.drawRoundRect(legLeft, 32f, 32f, silverSuitPaint)
        canvas.drawRoundRect(legRight, 32f, 32f, silverSuitPaint)

        // Torso / Silver space suit body
        val torsoRect = RectF(cx - w * 0.18f, cy - h * 0.06f, cx + w * 0.18f, cy + h * 0.22f)
        canvas.drawRoundRect(torsoRect, 40f, 40f, silverSuitPaint)

        // Arms raised in meditation gesture
        val armLeft = Path().apply {
            moveTo(cx - w * 0.16f, cy - h * 0.02f)
            quadTo(cx - w * 0.32f, cy - h * 0.16f, cx - w * 0.18f, cy - h * 0.22f)
            lineTo(cx - w * 0.14f, cy - h * 0.18f)
            quadTo(cx - w * 0.26f, cy - h * 0.12f, cx - w * 0.12f, cy + h * 0.02f)
            close()
        }
        val armRight = Path().apply {
            moveTo(cx + w * 0.16f, cy - h * 0.02f)
            quadTo(cx + w * 0.32f, cy - h * 0.16f, cx + w * 0.18f, cy - h * 0.22f)
            lineTo(cx + w * 0.14f, cy - h * 0.18f)
            quadTo(cx + w * 0.26f, cy - h * 0.12f, cx + w * 0.12f, cy + h * 0.02f)
            close()
        }
        canvas.drawPath(armLeft, silverSuitPaint)
        canvas.drawPath(armRight, silverSuitPaint)

        // Helmet (Black with reflective visor, matching video)
        val helmetRect = RectF(cx - w * 0.16f, cy - h * 0.32f, cx + w * 0.16f, cy - h * 0.08f)
        val helmetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(20, 22, 26)
        }
        canvas.drawRoundRect(helmetRect, w * 0.16f, w * 0.16f, helmetPaint)

        // Gold reflective visor arc
        val visorRect = RectF(cx - w * 0.11f, cy - h * 0.28f, cx + w * 0.11f, cy - h * 0.14f)
        val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                visorRect.left, visorRect.top, visorRect.right, visorRect.bottom,
                intArrayOf(AndroidColor.rgb(240, 190, 80), AndroidColor.rgb(180, 120, 40), AndroidColor.rgb(40, 30, 20)),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(visorRect, w * 0.08f, w * 0.08f, visorPaint)

        // Visor reflection flare
        val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(160, 255, 255, 255)
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawArc(visorRect, 200f, 80f, false, flarePaint)

        return bmp
    }

    private fun createWhaleBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Deep sea gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(AndroidColor.rgb(6, 18, 38), AndroidColor.rgb(12, 38, 72), AndroidColor.rgb(4, 12, 24)),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val cx = w * 0.5f
        val cy = h * 0.5f

        // Whale silhouette & form
        val whalePath = Path().apply {
            moveTo(cx - w * 0.38f, cy - h * 0.06f)
            cubicTo(cx - w * 0.2f, cy - h * 0.18f, cx + w * 0.15f, cy - h * 0.14f, cx + w * 0.36f, cy - h * 0.02f)
            // Tail fluke
            lineTo(cx + w * 0.44f, cy - h * 0.08f)
            lineTo(cx + w * 0.42f, cy)
            lineTo(cx + w * 0.45f, cy + h * 0.08f)
            lineTo(cx + w * 0.36f, cy + h * 0.02f)
            // Underbelly
            cubicTo(cx + w * 0.15f, cy + h * 0.14f, cx - w * 0.12f, cy + h * 0.16f, cx - w * 0.38f, cy - h * 0.06f)
            close()
        }

        val whalePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - w * 0.35f, cy - h * 0.15f, cx + w * 0.35f, cy + h * 0.15f,
                intArrayOf(AndroidColor.rgb(180, 210, 240), AndroidColor.rgb(90, 130, 180), AndroidColor.rgb(35, 60, 95)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(whalePath, whalePaint)

        // Flipper
        val finPath = Path().apply {
            moveTo(cx - w * 0.08f, cy + h * 0.02f)
            cubicTo(cx - w * 0.02f, cy + h * 0.18f, cx + w * 0.06f, cy + h * 0.22f, cx + w * 0.08f, cy + h * 0.16f)
            cubicTo(cx + w * 0.04f, cy + h * 0.08f, cx, cy + h * 0.04f, cx - w * 0.08f, cy + h * 0.02f)
            close()
        }
        canvas.drawPath(finPath, whalePaint)

        return bmp
    }

    private fun createCanvasBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(AndroidColor.WHITE)
        return bmp
    }
}
