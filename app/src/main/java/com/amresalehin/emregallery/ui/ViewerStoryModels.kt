package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaItem


internal data class ConstellationCard(
    val xOffsetDp: Float,
    val yOffsetDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val cornerRadiusDp: Float,
    val alpha: Float,
    val zIndex: Float,
    val borderAlpha: Float
)

internal data class AmbientSparkle(
    val xRatio: Float,
    val yRatio: Float,
    val sizeDp: Float,
    val alpha: Float
)

internal val MemorySparkles = listOf(
    AmbientSparkle(0.18f, 0.28f, 2.5f, 0.45f),
    AmbientSparkle(0.25f, 0.22f, 1.8f, 0.35f),
    AmbientSparkle(0.35f, 0.16f, 3.0f, 0.55f),
    AmbientSparkle(0.48f, 0.14f, 2.0f, 0.40f),
    AmbientSparkle(0.62f, 0.15f, 2.4f, 0.50f),
    AmbientSparkle(0.74f, 0.20f, 1.8f, 0.30f),
    AmbientSparkle(0.82f, 0.26f, 2.8f, 0.60f),
    AmbientSparkle(0.88f, 0.38f, 2.0f, 0.35f),
    AmbientSparkle(0.12f, 0.42f, 2.2f, 0.40f),
    AmbientSparkle(0.15f, 0.55f, 3.2f, 0.65f),
    AmbientSparkle(0.85f, 0.52f, 2.5f, 0.45f),
    AmbientSparkle(0.82f, 0.62f, 2.0f, 0.35f),
    AmbientSparkle(0.24f, 0.66f, 2.6f, 0.50f),
    AmbientSparkle(0.38f, 0.70f, 1.8f, 0.30f),
    AmbientSparkle(0.55f, 0.72f, 2.8f, 0.55f),
    AmbientSparkle(0.68f, 0.68f, 2.2f, 0.40f),
    AmbientSparkle(0.50f, 0.24f, 2.0f, 0.45f),
    AmbientSparkle(0.42f, 0.32f, 1.5f, 0.35f),
    AmbientSparkle(0.58f, 0.30f, 1.5f, 0.35f),
    AmbientSparkle(0.70f, 0.40f, 2.0f, 0.40f),
    AmbientSparkle(0.30f, 0.44f, 2.0f, 0.40f)
)

internal val ConstellationCards = listOf(
    // 0: Center Hero Card (Largest, front and center)
    ConstellationCard(0f, -24f, 112f, 150f, 18f, 1.0f, 20f, 0.35f),

    // 1-8: Tier 1 Inner Ring (surrounding the hero card)
    ConstellationCard(-76f, -76f, 80f, 96f, 14f, 0.95f, 12f, 0.28f),
    ConstellationCard(78f, -72f, 82f, 96f, 14f, 0.95f, 12f, 0.28f),
    ConstellationCard(-74f, 62f, 80f, 94f, 14f, 0.95f, 12f, 0.28f),
    ConstellationCard(80f, 58f, 82f, 96f, 14f, 0.95f, 12f, 0.28f),
    ConstellationCard(-100f, -6f, 74f, 90f, 13f, 0.90f, 10f, 0.24f),
    ConstellationCard(102f, -4f, 76f, 92f, 13f, 0.90f, 10f, 0.24f),
    ConstellationCard(-2f, -126f, 70f, 86f, 13f, 0.90f, 10f, 0.24f),
    ConstellationCard(2f, 102f, 72f, 88f, 13f, 0.90f, 10f, 0.24f),

    // 9-22: Tier 2 Mid Ring (medium cards forming the body of the nebula)
    ConstellationCard(-128f, -76f, 52f, 64f, 10f, 0.82f, 6f, 0.20f),
    ConstellationCard(130f, -66f, 54f, 66f, 10f, 0.82f, 6f, 0.20f),
    ConstellationCard(-130f, 50f, 52f, 64f, 10f, 0.82f, 6f, 0.20f),
    ConstellationCard(126f, 56f, 54f, 66f, 10f, 0.82f, 6f, 0.20f),
    ConstellationCard(-56f, -152f, 50f, 62f, 10f, 0.80f, 6f, 0.20f),
    ConstellationCard(60f, -148f, 50f, 62f, 10f, 0.80f, 6f, 0.20f),
    ConstellationCard(-50f, 138f, 50f, 62f, 10f, 0.80f, 6f, 0.20f),
    ConstellationCard(54f, 134f, 50f, 62f, 10f, 0.80f, 6f, 0.20f),
    ConstellationCard(-148f, -10f, 48f, 58f, 10f, 0.78f, 5f, 0.18f),
    ConstellationCard(150f, -6f, 48f, 58f, 10f, 0.78f, 5f, 0.18f),
    ConstellationCard(-94f, -136f, 48f, 56f, 10f, 0.78f, 5f, 0.18f),
    ConstellationCard(98f, -130f, 48f, 56f, 10f, 0.78f, 5f, 0.18f),
    ConstellationCard(-98f, 120f, 48f, 56f, 10f, 0.78f, 5f, 0.18f),
    ConstellationCard(100f, 116f, 48f, 56f, 10f, 0.78f, 5f, 0.18f),

    // 23-36: Tier 3 Outer Ring (smaller softly fading peripheral cards)
    ConstellationCard(-168f, -54f, 34f, 40f, 8f, 0.60f, 2f, 0.14f),
    ConstellationCard(170f, -48f, 34f, 40f, 8f, 0.60f, 2f, 0.14f),
    ConstellationCard(-164f, 42f, 34f, 40f, 8f, 0.60f, 2f, 0.14f),
    ConstellationCard(166f, 38f, 34f, 40f, 8f, 0.60f, 2f, 0.14f),
    ConstellationCard(-114f, -182f, 32f, 38f, 7f, 0.55f, 2f, 0.12f),
    ConstellationCard(112f, -176f, 32f, 38f, 7f, 0.55f, 2f, 0.12f),
    ConstellationCard(-110f, 164f, 32f, 38f, 7f, 0.55f, 2f, 0.12f),
    ConstellationCard(108f, 160f, 32f, 38f, 7f, 0.55f, 2f, 0.12f),
    ConstellationCard(-2f, -188f, 30f, 36f, 7f, 0.50f, 1f, 0.12f),
    ConstellationCard(0f, 174f, 30f, 36f, 7f, 0.50f, 1f, 0.12f),
    ConstellationCard(-178f, -6f, 30f, 36f, 7f, 0.50f, 1f, 0.12f),
    ConstellationCard(180f, -2f, 30f, 36f, 7f, 0.50f, 1f, 0.12f),
    ConstellationCard(-142f, -114f, 30f, 36f, 7f, 0.50f, 1f, 0.12f),
    ConstellationCard(144f, -108f, 30f, 36f, 7f, 0.50f, 1f, 0.12f)
)

/**
 * End-of-memories constellation screen with real thumbnails from the stories,
 * sparkling ambient dust, and dedicated Replay and Close pill buttons.
 * Stays visible indefinitely without automatic dismissal.
 */
@Composable
