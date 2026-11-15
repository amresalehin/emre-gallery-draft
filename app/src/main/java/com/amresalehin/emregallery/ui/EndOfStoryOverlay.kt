package com.amresalehin.emregallery.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaItem

private data class ConstellationCard(
    val xOffsetDp: Float,
    val yOffsetDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val cornerRadiusDp: Float,
    val alpha: Float,
    val zIndex: Float,
    val borderAlpha: Float
)

private data class AmbientSparkle(
    val xRatio: Float,
    val yRatio: Float,
    val sizeDp: Float,
    val alpha: Float
)

private val MemorySparkles = listOf(
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

private val ConstellationCards = listOf(
    // 0: Center Hero Card
    ConstellationCard(0f, -24f, 112f, 150f, 18f, 1.0f, 20f, 0.35f),
    // 1-8: Tier 1 Inner Ring
    ConstellationCard(-76f, -76f, 80f, 96f, 14f, 0.95f, 12f, 0.28f),
    ConstellationCard(78f, -72f, 82f, 96f, 14f, 0.95f, 12f, 0.28f),
    ConstellationCard(-74f, 62f, 80f, 94f, 14f, 0.95f, 12f, 0.28f),
    ConstellationCard(80f, 58f, 82f, 96f, 14f, 0.95f, 12f, 0.28f),
    ConstellationCard(-100f, -6f, 74f, 90f, 13f, 0.90f, 10f, 0.24f),
    ConstellationCard(102f, -4f, 76f, 92f, 13f, 0.90f, 10f, 0.24f),
    ConstellationCard(-2f, -126f, 70f, 86f, 13f, 0.90f, 10f, 0.24f),
    ConstellationCard(2f, 102f, 72f, 88f, 13f, 0.90f, 10f, 0.24f),
    // 9-22: Tier 2 Mid Ring
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
    // 23-36: Tier 3 Outer Ring
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
 * End-of-memories constellation screen with thumbnails from the story,
 * ambient starlight sparkles, and Replay / Close pill buttons.
 */
@Composable
fun EndOfStoryOverlay(
    storyItems: List<MediaItem> = emptyList(),
    storyTitle: String = "",
    itemCount: Int = storyItems.size,
    onReplay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    val alphaAnim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "end_of_story_alpha"
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "end_of_story_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF09090D))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume taps on background */ }
    ) {
        // Ambient starlight sparkles
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = alphaAnim }
        ) {
            val w = size.width
            val h = size.height
            for (sparkle in MemorySparkles) {
                drawCircle(
                    color = Color.White.copy(alpha = sparkle.alpha * 0.75f),
                    radius = sparkle.sizeDp.dp.toPx(),
                    center = Offset(sparkle.xRatio * w, sparkle.yRatio * h)
                )
            }
        }

        // Spherical constellation of real thumbnail cards
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = alphaAnim
                    scaleX = scaleAnim
                    scaleY = scaleAnim
                },
            contentAlignment = Alignment.Center
        ) {
            for ((index, card) in ConstellationCards.withIndex()) {
                val item = if (storyItems.isNotEmpty()) {
                    storyItems[index % storyItems.size]
                } else null

                val targetWidthPx = (card.widthDp * density.density).toInt().coerceAtLeast(80)
                val targetHeightPx = (card.heightDp * density.density).toInt().coerceAtLeast(80)

                StoryConstellationTile(
                    card = card,
                    item = item,
                    targetWidthPx = targetWidthPx,
                    targetHeightPx = targetHeightPx,
                    context = context
                )
            }
        }

        // Replay and Close action pill buttons side-by-side
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 44.dp)
                .graphicsLayer { alpha = alphaAnim },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoryEndPillButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Replay memories",
                onClick = onReplay
            )
            StoryEndPillButton(
                icon = Icons.Default.Close,
                contentDescription = "Close memories",
                onClick = onClose
            )
        }
    }
}

@Composable
private fun StoryConstellationTile(
    card: ConstellationCard,
    item: MediaItem?,
    targetWidthPx: Int,
    targetHeightPx: Int,
    context: android.content.Context
) {
    Box(
        modifier = Modifier
            .offset(x = card.xOffsetDp.dp, y = card.yOffsetDp.dp)
            .size(width = card.widthDp.dp, height = card.heightDp.dp)
            .zIndex(card.zIndex)
            .graphicsLayer { alpha = card.alpha }
            .shadow(
                elevation = if (card.zIndex >= 15f) 12.dp else if (card.zIndex >= 10f) 6.dp else 2.dp,
                shape = RoundedCornerShape(card.cornerRadiusDp.dp),
                clip = false
            )
            .clip(RoundedCornerShape(card.cornerRadiusDp.dp))
            .background(Color(0xFF16161B))
            .border(
                BorderStroke(
                    width = if (card.zIndex >= 15f) 1.2.dp else 1.dp,
                    color = Color.White.copy(alpha = card.borderAlpha)
                ),
                shape = RoundedCornerShape(card.cornerRadiusDp.dp)
            )
    ) {
        if (item != null) {
            AsyncImage(
                model = remember(item.uri, context) {
                    ThumbnailUtils.buildThumbnailRequest(
                        context = context,
                        uri = item.uri,
                        widthPx = targetWidthPx,
                        heightPx = targetHeightPx
                    )
                },
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size((card.widthDp * 0.35f).dp)
                )
            }
        }

        // Subtle darkening glass overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.28f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun StoryEndPillButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(80.dp)
            .height(48.dp)
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.40f)),
                RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}
