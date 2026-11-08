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


fun MemoriesStrip(
    media: List<MediaItem> = emptyList(),
    dayGroups: List<DayGroup>? = null,
    precomputedStories: List<com.amresalehin.emregallery.data.StoryGroup>? = null,
    onOpenMemory: (stories: List<StoryGroup>, startIndex: Int) -> Unit
) {
    val stories = remember(precomputedStories, dayGroups, if (precomputedStories == null && dayGroups == null) media else null) {
        if (precomputedStories != null) {
            return@remember precomputedStories.map { it.title to it.items }
        }
        val resolvedGroups = dayGroups ?: if (media.isNotEmpty()) groupMediaByDay(media) else emptyList()
        if (resolvedGroups.isEmpty()) return@remember emptyList()

        val today = Calendar.getInstance()
        val currentYear = today.get(Calendar.YEAR)
        val todayMonth = today.get(Calendar.MONTH)
        val todayDay = today.get(Calendar.DAY_OF_YEAR)

        val cal = Calendar.getInstance()
        val onThisDayMap = mutableMapOf<Int, MutableList<MediaItem>>()
        val tripCandidates = mutableListOf<Pair<String, List<MediaItem>>>()
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

        for (group in resolvedGroups) {
            if (group.dayKey <= 0L || group.items.isEmpty()) continue
            cal.timeInMillis = group.dayKey
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val d = cal.get(Calendar.DAY_OF_YEAR)

            if (m == todayMonth && d == todayDay && y < currentYear) {
                onThisDayMap.getOrPut(y) { mutableListOf() }.addAll(group.items)
            }
            if (group.items.size >= 4) {
                val label = dateFormat.format(Date(group.dayKey))
                tripCandidates.add(label to group.items)
            }
        }

        val result = mutableListOf<Pair<String, List<MediaItem>>>()

        // Google Photos-style "On this day" stories: real photos from this calendar date in prior years.
        onThisDayMap.toSortedMap(compareByDescending { it }).forEach { (year, entries) ->
            result += "On this day · $year" to entries
        }

        // Real date-based trip/story groups from the library
        for (trip in tripCandidates) {
            if (result.none { it.second.any { storyItem -> storyItem.uri == trip.second.first().uri } }) {
                result += trip
            }
            if (result.size >= 10) break
        }

        result.filter { it.second.isNotEmpty() }.take(10)
    }

    if (stories.isEmpty()) return

    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(stories.size, key = { index -> stories[index].first }) { index ->
                val (title, storyMedia) = stories[index]
                val cover = storyMedia.first()
                val memInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isMemPressed by memInteractionSource.collectIsPressedAsState()
                val memScale by animateFloatAsState(
                    targetValue = if (isMemPressed) 0.94f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "mem_card_scale"
                )
                Box(
                    Modifier.width(132.dp).height(184.dp)
                        .trackThumbnailBounds(cover.uri)
                        .graphicsLayer {
                            scaleX = memScale
                            scaleY = memScale
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = memInteractionSource,
                            indication = ripple(bounded = true),
                            onClick = {
                                val storyGroups = stories.map { StoryGroup(it.first, it.second) }
                                onOpenMemory(storyGroups, index)
                            }
                        )
                ) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = remember(cover.uri, context) {
                            ThumbnailUtils.buildThumbnailRequest(context, cover.uri, 264, 368)
                        },
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            alpha = if (ThumbnailBoundsTracker.activeViewerUri == cover.uri) 0f else 1f
                        },
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxWidth().height(78.dp).align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${storyMedia.size} photos and videos",
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}



