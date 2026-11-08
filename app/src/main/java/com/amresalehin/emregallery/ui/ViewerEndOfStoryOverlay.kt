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

    // Fade-in animation for smooth celestial transition
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
                val cx = sparkle.xRatio * w
                val cy = sparkle.yRatio * h
                val r = sparkle.sizeDp.dp.toPx()
                drawCircle(
                    color = Color.White.copy(alpha = sparkle.alpha * 0.75f),
                    radius = r,
                    center = Offset(cx, cy)
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
                        // Fallback icon if no items are loaded
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
            // Replay Button
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
                        onClick = onReplay
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Replay memories",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Close Button
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
                        onClick = onClose
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close memories",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Bottom action bar with Lock, Archive, Move (for move to folder), Delete/Restore buttons.
 */
@Composable
