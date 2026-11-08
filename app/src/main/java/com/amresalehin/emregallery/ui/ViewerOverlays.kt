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

/**
 * Top and bottom edge gradient shading behind the overlay controls.
 */
@Composable
fun BoxScope.ViewerEdgeShading(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(260)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val edgeShadeHeight = maxHeight * 0.22f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(edgeShadeHeight)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.62f),
                                Color.Black.copy(alpha = 0.38f),
                                Color.Black.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(edgeShadeHeight)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.16f),
                                Color.Black.copy(alpha = 0.42f),
                                Color.Black.copy(alpha = 0.68f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Top action bar with back, share, favorite, and more options.
 */
@Composable
fun ViewerTopAppBar(
    isFavourite: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleFavourite: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewerButton(Icons.Default.ArrowBack, "Back", onBack)
        Spacer(Modifier.weight(1f))
        ViewerButton(Icons.Default.Share, "Share", onShare)
        ViewerButton(
            icon = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = "Favourite",
            onClick = onToggleFavourite
        )
        ViewerButton(Icons.Default.MoreVert, "More", onMore)
    }
}

/**
 * Story top bar with segmented progress indicators, memory title, and story controls.
 */
@Composable
fun ViewerStoryTopBar(
    storyTitle: String,
    currentIndex: Int,
    itemCount: Int,
    currentProgress: Float,
    isFavourite: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleFavourite: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Segmented progress indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val count = itemCount.coerceAtLeast(1)
            for (i in 0 until count) {
                val segmentProgress = when {
                    i < currentIndex -> 1f
                    i == currentIndex -> currentProgress.coerceIn(0f, 1f)
                    else -> 0f
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                ) {
                    if (segmentProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = segmentProgress)
                                .background(Color.White)
                        )
                    }
                }
            }
        }

        // Header with Back button, Title + subtitle, and action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViewerButton(Icons.Default.ArrowBack, "Close memory", onBack)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = storyTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${currentIndex + 1} of $itemCount",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            ViewerButton(Icons.Default.Share, "Share", onShare)
            ViewerButton(
                icon = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = "Favourite",
                onClick = onToggleFavourite
            )
            ViewerButton(Icons.Default.MoreVert, "More", onMore)
        }
    }
}

