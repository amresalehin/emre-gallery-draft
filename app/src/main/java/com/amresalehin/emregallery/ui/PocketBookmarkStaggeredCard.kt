package com.amresalehin.emregallery.ui
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.MediaInboxStack
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
@Composable
fun PocketBookmarkStaggeredCard(
    stack: MediaInboxStack,
    index: Int,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardSurface = PocketThemeColors.cardSurface(isDark)
    val textColor = PocketThemeColors.primaryText(isDark)
    val secondaryText = PocketThemeColors.secondaryText(isDark)
    val dividerCol = PocketThemeColors.divider(isDark)
    val previewHeight = remember(index) {
        when (index % 4) {
            0 -> 180.dp
            1 -> 130.dp
            2 -> 140.dp
            else -> 195.dp
        }
    }
    val firstMedia = stack.items.firstOrNull()
    val mediaModel = remember(firstMedia) {
        firstMedia?.let {
            if (it.localUri.isNotBlank()) {
                val f = File(it.localUri.removePrefix("file://"))
                if (f.exists()) f else it.sourceUrl.ifBlank { it.localUri }
            } else {
                it.sourceUrl
            }
        }
    }
    val faviconUrl = remember(stack.sourceDomain) {
        if (stack.sourceDomain.isNotBlank()) {
            "https://www.google.com/s2/favicons?domain=${stack.sourceDomain}&sz=128"
        } else ""
    }
    val dateTimeFormatted = remember(stack.timestampMillis) {
        val sdf = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        sdf.format(Date(stack.timestampMillis))
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = cardSurface,
            tonalElevation = if (isSelected) 4.dp else 1.dp,
            shadowElevation = if (isSelected) 6.dp else 2.dp,
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) textColor else (if (isDark) dividerCol.copy(alpha = 0.7f) else dividerCol)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight)
                        .background(PocketThemeColors.thumbnailBg(isDark)),
                    contentAlignment = Alignment.Center
                ) {
                    if (mediaModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(mediaModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = stack.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (faviconUrl.isNotBlank()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PocketThemeColors.faviconSurface(isDark),
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(faviconUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = stack.sourceDomain,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stack.sourceDomain,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = PocketThemeColors.placeholderIcon(isDark),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    if (stack.items.size > 1) {
                        Surface(
                            shape = CircleShape,
                            color = PocketThemeColors.badgeBg(isDark),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = PocketThemeColors.badgeText(isDark),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${stack.items.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PocketThemeColors.badgeText(isDark)
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stack.title.ifBlank { stack.sourceDomain.ifBlank { "Untitled Bookmark" } },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = dateTimeFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp
                        ),
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PocketThemeColors.pillSurface(isDark),
                        border = BorderStroke(0.5.dp, if (isDark) dividerCol.copy(alpha = 0.7f) else dividerCol),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (faviconUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(faviconUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = secondaryText,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(10.dp)
                                    .background(if (isDark) dividerCol else Color(0xFFD0D0D0))
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = stack.sourceDomain.ifBlank { "bookmark" },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        if (isSelectionMode) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) (if (isDark) Color(0xFFEEEEEE) else Color(0xFF202020)) else (if (isDark) Color(0xFF242424) else Color(0xFFF2F2F2)),
                border = BorderStroke(
                    1.5.dp,
                    if (isSelected) (if (isDark) Color(0xFF101010) else Color(0xFFFFFFFF)) else (if (isDark) Color(0xFF666666) else Color(0xFFB0B0B0))
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (isDark) Color(0xFF161616) else Color(0xFFFFFFFF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
/**
 * Bookmark Viewer / Detail Page conforming strictly to image_a086dfff.png
 * Features:
 * - Top Bar: Circular Back button on left, 3 circular buttons on right (Reader/Browser, Copy, Delete)
 * - Center visual: Layered card stack deck with image/cover and `[🖼️ X / Y]` pagination badge
 * - Bottom sheet: Drag handle + rounded capsule cards for:
 *   1. 📅 Time
 *   2. 🔗 Source (clickable with open icon)
 *   3. 📄 Note (clickable to edit)
 *   4. 🏷️ Tags (chips of domain/type)
 *   5. ≡ Title
 *   6. ≡ Description
 */
