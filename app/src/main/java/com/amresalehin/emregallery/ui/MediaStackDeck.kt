package com.amresalehin.emregallery.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.MediaInboxItem
import com.amresalehin.emregallery.data.OkHttpDownloader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Interactive stacked photo deck with swipe-to-advance/rewind physics and numeric page indicator.
 */
@Composable
fun MediaStackDeck(
    stackId: String,
    items: List<MediaInboxItem>,
    currentIndex: Int,
    onCurrentIndexChange: (Int) -> Unit,
    onOpenMedia: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dragOffsetX = remember(stackId) { Animatable(0f) }
    val dragOffsetY = remember(stackId) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("functional_stacked_photo_deck"),
            contentAlignment = Alignment.Center
        ) {
            if (items.isEmpty()) {
                RealDeckMediaCard(
                    item = null,
                    elevation = 8.dp,
                    modifier = Modifier,
                    onClick = {}
                )
            } else {
                val maxDepth = 3
                val maxVisibleIndex = (currentIndex + maxDepth).coerceAtMost(items.size - 1)
                val dragDistance = sqrt(dragOffsetX.value * dragOffsetX.value + dragOffsetY.value * dragOffsetY.value)
                val swipeProgress = (dragDistance / 350f).coerceIn(0f, 1f)

                for (i in maxVisibleIndex downTo currentIndex) {
                    val depth = i - currentIndex
                    val item = items[i]

                    val cardScale = when (depth) {
                        0 -> 1.0f
                        1 -> 0.93f + (1.0f - 0.93f) * swipeProgress
                        2 -> 0.86f + (0.93f - 0.86f) * swipeProgress
                        else -> 0.80f + (0.86f - 0.80f) * swipeProgress
                    }

                    val cardRotZ = when (depth) {
                        0 -> dragOffsetX.value * 0.04f
                        1 -> 3.5f * (1f - swipeProgress)
                        2 -> -4.5f + (3.5f - -4.5f) * swipeProgress
                        else -> 5.5f + (-4.5f - 5.5f) * swipeProgress
                    }

                    val cardTransX = when (depth) {
                        0 -> dragOffsetX.value
                        1 -> (14f * density) * (1f - swipeProgress)
                        2 -> (-16f * density) + ((14f * density) - (-16f * density)) * swipeProgress
                        else -> (18f * density) + ((-16f * density) - (18f * density)) * swipeProgress
                    }

                    val cardTransY = when (depth) {
                        0 -> dragOffsetY.value
                        1 -> (8f * density) * (1f - swipeProgress)
                        2 -> (16f * density) + ((8f * density) - (16f * density)) * swipeProgress
                        else -> (24f * density) + ((16f * density) - (24f * density)) * swipeProgress
                    }

                    val cardAlpha = when (depth) {
                        0 -> (1f - swipeProgress * 0.25f).coerceIn(0.75f, 1f)
                        else -> (0.88f + 0.12f * swipeProgress).coerceIn(0.88f, 1f)
                    }

                    val cardElevation = when (depth) {
                        0 -> 16.dp
                        1 -> 10.dp
                        2 -> 6.dp
                        else -> 3.dp
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                                rotationZ = cardRotZ
                                translationX = cardTransX
                                translationY = cardTransY
                                alpha = cardAlpha
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        RealDeckMediaCard(
                            item = item,
                            elevation = cardElevation,
                            modifier = if (depth == 0) {
                                Modifier.pointerInput(currentIndex, items.size) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var hasMoved = false
                                        var dragDeltaX = 0f
                                        var dragDeltaY = 0f
                                        val touchSlop = viewConfiguration.touchSlop

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                            if (change.changedToUp()) {
                                                change.consume()
                                                break
                                            }

                                            val posDelta = change.positionChange()
                                            if (posDelta != Offset.Zero) {
                                                dragDeltaX += posDelta.x
                                                dragDeltaY += posDelta.y
                                                val dist = sqrt(dragDeltaX * dragDeltaX + dragDeltaY * dragDeltaY)
                                                if (dist > touchSlop) {
                                                    hasMoved = true
                                                }
                                                if (hasMoved) {
                                                    change.consume()
                                                    scope.launch {
                                                        dragOffsetX.snapTo(dragOffsetX.value + posDelta.x)
                                                        dragOffsetY.snapTo(dragOffsetY.value + posDelta.y)
                                                    }
                                                }
                                            }
                                        }

                                        val offsetX = dragOffsetX.value
                                        val offsetY = dragOffsetY.value
                                        val totalDrag = sqrt(offsetX * offsetX + offsetY * offsetY)

                                        if (!hasMoved || totalDrag < 45f) {
                                            val targetUri = item.localUri.ifBlank { item.sourceUrl }
                                            onOpenMedia(targetUri)
                                        } else if ((offsetX < -140f || offsetY < -150f) && currentIndex < items.size - 1) {
                                            scope.launch {
                                                val exitX = if (abs(offsetX) > 20f) (if (offsetX < 0) -1300f else 1300f) else 0f
                                                val exitY = if (abs(offsetY) > 20f) (if (offsetY < 0) -1300f else 1300f) else 0f
                                                launch { dragOffsetX.animateTo(exitX, tween(160)) }
                                                launch { dragOffsetY.animateTo(exitY, tween(160)) }
                                                delay(160)
                                                onCurrentIndexChange((currentIndex + 1).coerceAtMost(items.size - 1))
                                                dragOffsetX.snapTo(0f)
                                                dragOffsetY.snapTo(0f)
                                            }
                                        } else if (offsetX > 140f && currentIndex > 0) {
                                            scope.launch {
                                                launch { dragOffsetX.animateTo(1300f, tween(160)) }
                                                delay(160)
                                                onCurrentIndexChange((currentIndex - 1).coerceAtLeast(0))
                                                dragOffsetX.snapTo(0f)
                                                dragOffsetY.snapTo(0f)
                                            }
                                        } else {
                                            scope.launch {
                                                launch {
                                                    dragOffsetX.animateTo(
                                                        0f,
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    )
                                                }
                                                launch {
                                                    dragOffsetY.animateTo(
                                                        0f,
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier.clickable {
                                    scope.launch {
                                        dragOffsetX.snapTo(0f)
                                        dragOffsetY.snapTo(0f)
                                        onCurrentIndexChange(i)
                                    }
                                }
                            },
                            onClick = {
                                val targetUri = item.localUri.ifBlank { item.sourceUrl }
                                onOpenMedia(targetUri)
                            }
                        )
                    }
                }
            }
        }

        if (items.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 10.dp)
                    .testTag("numeric_pagination_indicator")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${currentIndex + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = " / ${items.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Real Media Card for the functional stacked photo deck (renders actual downloaded media items).
 */
@Composable
fun RealDeckMediaCard(
    item: MediaInboxItem?,
    elevation: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 8.dp,
        shadowElevation = elevation,
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.95f)),
        modifier = modifier
            .fillMaxWidth(0.82f)
            .fillMaxHeight(0.94f)
            .clip(RoundedCornerShape(32.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (item != null) {
                val context = LocalContext.current
                val localFile = remember(item.localUri) {
                    if (item.localUri.isNotBlank()) File(item.localUri.removePrefix("file://")) else null
                }
                val imageSource: Any = if (localFile != null && localFile.exists() && localFile.length() > 0L) {
                    localFile
                } else if (item.sourceUrl.isNotBlank()) {
                    item.sourceUrl
                } else {
                    item.localUri
                }

                val imageRequest = remember(imageSource) {
                    val rawStr = imageSource.toString()
                    val resolvedUrl = if (rawStr.startsWith("//")) "https:$rawStr" else rawStr
                    val refererHeader = item.sourceUrl.takeIf { it.isNotBlank() }
                        ?: runCatching {
                            val u = URI(resolvedUrl)
                            "${u.scheme ?: "https"}://${u.host}/"
                        }.getOrNull()
                    val builder = ImageRequest.Builder(context)
                        .data(if (imageSource is File) imageSource else resolvedUrl)
                        .crossfade(true)
                        .setHeader("User-Agent", OkHttpDownloader.DEFAULT_USER_AGENT)
                        .setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .setHeader("Sec-Fetch-Dest", "image")
                        .setHeader("Sec-Fetch-Mode", "no-cors")

                    if (!refererHeader.isNullOrBlank()) {
                        builder.setHeader("Referer", refererHeader)
                    }

                    val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(resolvedUrl)
                        .ifBlank { refererHeader?.let { OkHttpDownloader.sharedCookieJar.getCookieString(it) } }
                    if (!cookies.isNullOrBlank()) {
                        builder.setHeader("Cookie", cookies)
                    }

                    builder.build()
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Fit
                )

                if (item.isVideo || item.isStream) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = if (item.isStream) "STREAM" else item.format.ifBlank { "VIDEO" },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Landscape,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
        }
    }
}
