package com.amresalehin.emregallery.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.amresalehin.emregallery.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Older drawer opened paths are deprecated in favor of the current DownloaderResultsPage.
 * This composable delegates directly to DownloaderResultsPage to avoid maintaining duplicate
 * extraction pipelines.
 */
@Deprecated("Use DownloaderResultsPage directly instead")
@Composable
fun MediaScraperDrawer(
    initialUrl: String? = null,
    onClose: () -> Unit,
    onDownloadComplete: () -> Unit,
    onOpenBrowser: ((String) -> Unit)? = null,
    heightFraction: Float = 0.88f,
    showSearchBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!initialUrl.isNullOrBlank()) {
        DownloaderResultsPage(
            targetUrl = initialUrl,
            onBack = onClose,
            onOpenBrowser = onOpenBrowser,
            onDownloadComplete = { onDownloadComplete() },
            onViewSavedLibrary = onClose,
            modifier = modifier
        )
    }
}

/**
 * Real Scraped Media Card displayed in the 3-column grid
 */
@Composable
fun RealScrapedCard(
    item: ScrapedMediaItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    dynamicDimensions: Map<String, Pair<Int, Int>> = emptyMap()
) {
    val mosaicHeight = remember(item, dynamicDimensions[item.id]) {
        val (width, height) = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(item)
        if (width != null && height != null && width > 0) {
            val ratio = height.toFloat() / width.toFloat()
            when {
                ratio > 1.25f -> 160.dp // portrait
                ratio < 0.75f -> 100.dp // landscape
                else -> 125.dp
            }
        } else if (item.isStream || item.isVideo) {
            130.dp
        } else if (item.isAudio) {
            95.dp
        } else {
            val mod = Math.abs(item.url.hashCode()) % 4
            when (mod) {
                0 -> 155.dp
                1 -> 115.dp
                2 -> 140.dp
                else -> 120.dp
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .testTag("scraped_media_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mosaicHeight)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                val previewUrl = item.thumbnailUrl ?: item.url
                if (item.category == MediaCategory.AUDIO && item.thumbnailUrl == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    SubcomposeAsyncImage(
                        model = previewUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.isVideo || item.isStream) Icons.Default.VideoLibrary else Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    )
                }

                // Top Format / Resolution Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    val formatLabel = item.format.uppercase(Locale.ROOT).ifBlank {
                        if (item.isStream) "HLS" else if (item.isVideo) "MP4" else "IMG"
                    }
                    Text(
                        text = formatLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Checkbox indicator on top right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                ) {
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.40f),
                            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f)),
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                }
            }

            // Card Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (w, h) = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(item)
                    val dimText = when {
                        w != null && h != null -> "${w}x${h}"
                        w != null -> "${w}w"
                        !item.resolutionHint.isNullOrBlank() -> item.resolutionHint
                        else -> item.category.name.lowercase().replaceFirstChar { it.uppercase() }
                    }
                    Text(
                        text = dimText ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 3-Column Skeleton Grid:
 * Rendered in a dull, muted, greyed-out visual style.
 */
@Composable
fun ThreeColumnSkeletonGrid() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.40f),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThreeColumnSkeletonCard(heightDp = 125)
            ThreeColumnSkeletonCard(heightDp = 100)
            ThreeColumnSkeletonCard(heightDp = 115)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThreeColumnSkeletonCard(heightDp = 95)
            ThreeColumnSkeletonCard(heightDp = 135)
            ThreeColumnSkeletonCard(heightDp = 100)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThreeColumnSkeletonCard(heightDp = 115)
            ThreeColumnSkeletonCard(heightDp = 95)
            ThreeColumnSkeletonCard(heightDp = 115)
        }
    }
}

/**
 * Single 3-column skeleton card:
 * Low-contrast dull grey container, soft placeholder icon, and muted placeholder text bars.
 */
@Composable
fun ThreeColumnSkeletonCard(heightDp: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
                    modifier = Modifier.size(26.dp)
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp)
                ) {}
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.80f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
            }
        }
    }
}

/**
 * Comprehensive parser for media dimensions from resolution hints, URL patterns, and titles.
 */
fun parseScrapedMediaDimensions(item: ScrapedMediaItem): Pair<Int?, Int?> {
    val hint = item.resolutionHint?.trim()
    if (!hint.isNullOrBlank()) {
        val match = Regex("""(\d{2,5})\s*[xX×]\s*(\d{2,5})""").find(hint)
        if (match != null) {
            val w = match.groupValues[1].toIntOrNull()
            val h = match.groupValues[2].toIntOrNull()
            if (w != null && h != null && w > 0 && h > 0) return Pair(w, h)
        }
        val pMatch = Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE).find(hint)
        if (pMatch != null) {
            val h = pMatch.groupValues[1].toIntOrNull()
            if (h != null && h > 0) {
                return Pair((h * 16) / 9, h)
            }
        }
        val wMatch = Regex("""(\d{2,5})w""", RegexOption.IGNORE_CASE).find(hint)
        if (wMatch != null) {
            val w = wMatch.groupValues[1].toIntOrNull()
            if (w != null && w > 0) return Pair(w, null)
        }
        val pxMatch = Regex("""(\d{2,5})px""", RegexOption.IGNORE_CASE).find(hint)
        if (pxMatch != null) {
            val w = pxMatch.groupValues[1].toIntOrNull()
            if (w != null && w > 0) return Pair(w, null)
        }
    }

    // 1. WxH in URL or Title (e.g., 800x600, 1920x1080)
    val urlMatch = Regex("""(?:^|[/_.-])(\d{2,5})\s*[xX×]\s*(\d{2,5})(?:[/_.-]|$)""").find(item.url)
        ?: Regex("""(?:^|[/_.-])(\d{2,5})\s*[xX×]\s*(\d{2,5})(?:[/_.-]|$)""").find(item.title)
    if (urlMatch != null) {
        val w = urlMatch.groupValues[1].toIntOrNull()
        val h = urlMatch.groupValues[2].toIntOrNull()
        if (w != null && h != null && w >= 10 && h >= 10) {
            return Pair(w, h)
        }
    }

    // 2. Wikimedia / standard thumbnail pattern: /(\d+)px- or title starting with (\d+)px
    val pxMatch = Regex("""(?:^|[/_-])(\d{2,5})px(?:[-_./]|$)""", RegexOption.IGNORE_CASE).find(item.url)
        ?: Regex("""(?:^|[/_-])(\d{2,5})px(?:[-_./]|$)""", RegexOption.IGNORE_CASE).find(item.title)
    if (pxMatch != null) {
        val w = pxMatch.groupValues[1].toIntOrNull()
        if (w != null && w >= 10) {
            return Pair(w, null)
        }
    }

    // 3. Query params: ?width=800&height=600 or ?w=800&h=600
    val wQuery = Regex("""[?&](?:width|w)=(\d{2,5})""", RegexOption.IGNORE_CASE).find(item.url)?.groupValues?.get(1)?.toIntOrNull()
    val hQuery = Regex("""[?&](?:height|h)=(\d{2,5})""", RegexOption.IGNORE_CASE).find(item.url)?.groupValues?.get(1)?.toIntOrNull()
    if (wQuery != null || hQuery != null) {
        return Pair(wQuery, hQuery)
    }

    // 4. Folder dimension descriptor e.g. /736x/, /474x/, /236x/
    val folderDim = Regex("""/(\d{2,5})x/""").find(item.url)?.groupValues?.get(1)?.toIntOrNull()
    if (folderDim != null && folderDim >= 10) {
        return Pair(folderDim, null)
    }

    return Pair(null, null)
}

/**
 * Evaluates whether a media item satisfies the active dimension filter.
 */
fun passesDimensionFilter(
    item: ScrapedMediaItem,
    targetMinWidth: Int,
    targetMinHeight: Int,
    dynamicDimensions: Map<String, Pair<Int, Int>>
): Boolean {
    // 1. Any Dimensions setting (default 0, 0) matches everything
    if (targetMinWidth <= 0 && targetMinHeight <= 0) return true

    // 2. Audio, Video, and Stream files have no image-based spatial filtering requirements; exclude them from dimension filtering
    if (item.category == MediaCategory.AUDIO || item.category == MediaCategory.VIDEOS ||
        item.category == MediaCategory.STREAMS || item.isVideo || item.isAudio || item.isStream
    ) {
        return true
    }

    // 3. Reject decorative small UI elements, buttons, icons, or donate badges
    val lowerUrl = item.url.lowercase(Locale.ROOT)
    val lowerTitle = item.title.lowercase(Locale.ROOT)
    val isTinyUiMedia = lowerUrl.contains("donate") || lowerUrl.contains("/icon") ||
            lowerUrl.contains("-icon.") || lowerUrl.contains("_icon.") ||
            lowerUrl.contains("favicon") || lowerUrl.contains("badge") ||
            lowerTitle.contains("donate") || lowerTitle.contains("favicon") ||
            lowerTitle.contains("icon")
    if (isTinyUiMedia) {
        return false
    }

    val (w, h) = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(item)

    // Treat unknown dimensions as pending or excluded in strict mode
    if (targetMinWidth > 0 && w == null) return false
    if (targetMinHeight > 0 && h == null) return false

    // When strict filtering is active, require every enabled dimension constraint to be satisfied
    if (targetMinWidth > 0 && (w == null || w < targetMinWidth)) return false
    if (targetMinHeight > 0 && (h == null || h < targetMinHeight)) return false

    return true
}

/**
 * Lightweight background prober for media items with undetermined dimensions.
 * Uses OkHttp with HTTP Range 0-8191 and enforces a hard limit of 8192 bytes read from stream.
 */
object ImageDimensionProber {
    private val dimCache = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val sizeCache = ConcurrentHashMap<String, Long>()

    suspend fun probe(url: String, referer: String? = null): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val cleanUrl = if (url.startsWith("//")) "https:$url" else url
        dimCache[cleanUrl]?.let { return@withContext it }

        try {
            val reqBuilder = okhttp3.Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", OkHttpDownloader.DEFAULT_USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Range", "bytes=0-8191")

            if (!referer.isNullOrBlank()) {
                reqBuilder.header("Referer", referer)
            } else if (cleanUrl.contains("wikimedia.org") || cleanUrl.contains("wikipedia.org")) {
                reqBuilder.header("Referer", "https://en.wikipedia.org/")
            }

            OkHttpDownloader.client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val contentRange = resp.header("Content-Range")
                    val totalSize = if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                        contentRange.substringAfterLast("/").trim().toLongOrNull()
                    } else if (resp.code == 200) {
                        resp.header("Content-Length")?.toLongOrNull()
                    } else null

                    if (totalSize != null && totalSize > 0L) {
                        sizeCache[cleanUrl] = totalSize
                    }

                    val stream = resp.body?.byteStream() ?: return@withContext null
                    val maxProbeBytes = 8192
                    val buffer = ByteArray(maxProbeBytes)
                    var totalRead = 0
                    while (totalRead < maxProbeBytes) {
                        val read = stream.read(buffer, totalRead, maxProbeBytes - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    if (totalRead > 0) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(buffer, 0, totalRead, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            val dims = Pair(opts.outWidth, opts.outHeight)
                            dimCache[cleanUrl] = dims
                            return@withContext dims
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    fun getCached(url: String): Pair<Int, Int>? {
        val cleanUrl = if (url.startsWith("//")) "https:$url" else url
        return dimCache[cleanUrl]
    }

    fun getCachedSize(url: String): Long? {
        val cleanUrl = if (url.startsWith("//")) "https:$url" else url
        return sizeCache[cleanUrl]
    }
}
