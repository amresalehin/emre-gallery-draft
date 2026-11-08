package com.amresalehin.emregallery.ui

import android.net.Uri
import com.amresalehin.emregallery.data.StoryGroup
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import coil.size.Precision
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt


@Composable
fun HomeScreenSkeleton(
    gridColumns: Int = 3,
    gridStyle: String = "masonry",
    showMemories: Boolean = true,
    containerWidth: Dp = 360.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    val isSquare = (gridStyle == "square")
    val validCols = gridColumns.coerceIn(1, 6)
    val gap = 4.dp
    val totalGap = gap * (validCols - 1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_screen_skeleton"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (showMemories) {
            // Memories row skeleton matching actual MemoriesStrip dimensions (132.dp x 184.dp, 16.dp corners)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .width(132.dp)
                            .height(184.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alphaAnim)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alphaAnim * 0.7f)
                                )
                        )
                    }
                }
            }
        }

        // Section 1 header skeleton
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alphaAnim)
                    )
            )
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alphaAnim * 0.8f)
                    )
            )
        }

        // Section 1: Justified mosaic rows matching the exact layout algorithm of JustifiedMosaicRow
        repeat(3) { rowIndex ->
            val aspects = if (isSquare) {
                List(validCols) { 1f }
            } else {
                List(validCols) { colIndex ->
                    when ((rowIndex + colIndex).mod(4)) {
                        0 -> 0.72f
                        1 -> 0.9f
                        2 -> 1.0f
                        else -> 1.35f
                    }
                }
            }
            val aspectsSum = aspects.sum().coerceAtLeast(0.1f)
            val rowHeight = ((containerWidth - totalGap) / aspectsSum).coerceIn(72.dp, 220.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                aspects.forEach { aspect ->
                    Box(
                        modifier = Modifier
                            .weight(aspect)
                            .height(rowHeight)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alphaAnim)
                            )
                    )
                }
            }
        }

        // Section 2 header skeleton
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alphaAnim)
                    )
            )
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alphaAnim * 0.8f)
                    )
            )
        }

        // Section 2: Justified mosaic rows matching the exact layout algorithm of JustifiedMosaicRow
        repeat(2) { rowIndex ->
            val actualRowIndex = rowIndex + 3
            val aspects = if (isSquare) {
                List(validCols) { 1f }
            } else {
                List(validCols) { colIndex ->
                    when ((actualRowIndex + colIndex).mod(4)) {
                        0 -> 0.72f
                        1 -> 0.9f
                        2 -> 1.0f
                        else -> 1.35f
                    }
                }
            }
            val aspectsSum = aspects.sum().coerceAtLeast(0.1f)
            val rowHeight = ((containerWidth - totalGap) / aspectsSum).coerceIn(72.dp, 220.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                aspects.forEach { aspect ->
                    Box(
                        modifier = Modifier
                            .weight(aspect)
                            .height(rowHeight)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alphaAnim)
                            )
                    )
                }
            }
        }
    }
}

@Composable
