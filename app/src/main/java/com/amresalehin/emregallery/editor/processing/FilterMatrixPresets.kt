package com.amresalehin.emregallery.editor.processing

import android.graphics.ColorMatrix
import com.amresalehin.emregallery.editor.FilterType

/**
 * ColorMatrix presets and matrix blending utilities.
 */
object FilterMatrixPresets {

    fun blendColorMatrix(a: ColorMatrix, b: ColorMatrix, weight: Float): ColorMatrix {
        val fa = a.array
        val fb = b.array
        val res = FloatArray(20)
        for (i in 0 until 20) {
            res[i] = fa[i] * (1f - weight) + fb[i] * weight
        }
        return ColorMatrix(res)
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
}
