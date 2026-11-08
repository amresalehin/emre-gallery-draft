package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.MediaInboxStack
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun PocketBookmarkViewerPage(
    stack: MediaInboxStack,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onDelete: () -> Unit,
    onEditNote: () -> Unit,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textColor = PocketThemeColors.primaryText(isDark)
    val secondaryText = PocketThemeColors.secondaryText(isDark)
    val cardSurface = PocketThemeColors.stackFront(isDark)
    val barSurface = PocketThemeColors.barSurface(isDark)
    val dividerCol = PocketThemeColors.divider(isDark)

    val dateFormatted = remember(stack.timestampMillis) {
        val sdf = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        sdf.format(Date(stack.timestampMillis))
    }

    val mediaItems = stack.items
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { maxOf(1, mediaItems.size) })

    val faviconUrl = remember(stack.sourceDomain) {
        if (stack.sourceDomain.isNotBlank()) {
            "https://www.google.com/s2/favicons?domain=${stack.sourceDomain}&sz=128"
        } else ""
    }

    val derivedTags = remember(stack) {
        val tags = mutableListOf<String>()
        if (stack.sourceDomain.isNotBlank()) {
            tags.add(stack.sourceDomain.removePrefix("www."))
        }
        if (stack.items.isNotEmpty()) {
            tags.add("${stack.items.size} Media")
        } else if (stack.sourceUrl.isNotBlank()) {
            tags.add("Web Link")
        }
        if (tags.isEmpty()) {
            tags.add("Bookmark")
        }
        tags
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(PocketThemeColors.background(isDark)),
        color = PocketThemeColors.background(isDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            PocketViewerTopBar(
                stack = stack,
                barSurface = barSurface,
                textColor = textColor,
                dividerCol = dividerCol,
                isDark = isDark,
                context = context,
                onBack = onBack,
                onOpenBrowser = onOpenBrowser,
                onDelete = onDelete
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = PocketThemeColors.stackLayer1(isDark),
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .height(180.dp)
                            .offset(y = (-10).dp)
                    ) {}

                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = PocketThemeColors.stackLayer2(isDark),
                        modifier = Modifier
                            .fillMaxWidth(0.93f)
                            .height(185.dp)
                            .offset(y = (-5).dp)
                    ) {}

                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = PocketThemeColors.stackFront(isDark),
                        shadowElevation = if (isDark) 8.dp else 4.dp,
                        border = BorderStroke(1.dp, if (isDark) dividerCol.copy(alpha = 0.7f) else dividerCol),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(195.dp)
                    ) {
                        if (mediaItems.isNotEmpty()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val item = mediaItems[page]
                                val mediaPath = remember(item) {
                                    if (item.localUri.isNotBlank()) {
                                        val f = File(item.localUri.removePrefix("file://"))
                                        if (f.exists()) f else item.sourceUrl
                                    } else item.sourceUrl
                                }

                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(mediaPath)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = PocketThemeColors.webGradient(isDark)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (faviconUrl.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = PocketThemeColors.faviconSurface(isDark),
                                            shadowElevation = 3.dp,
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(faviconUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = stack.sourceDomain,
                                                    modifier = Modifier.size(34.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = null,
                                            tint = textColor,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = stack.sourceDomain.ifBlank { "Bookmark Preview" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = CircleShape,
                    color = barSurface,
                    border = BorderStroke(1.dp, if (isDark) dividerCol.copy(alpha = 0.7f) else dividerCol),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        val currentPage = if (mediaItems.isNotEmpty()) pagerState.currentPage + 1 else 1
                        val totalPages = maxOf(1, mediaItems.size)
                        Text(
                            text = "$currentPage / $totalPages",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            PocketViewerBottomDrawerSurface(
                stack = stack,
                dateFormatted = dateFormatted,
                faviconUrl = faviconUrl,
                derivedTags = derivedTags,
                textColor = textColor,
                secondaryText = secondaryText,
                dividerCol = dividerCol,
                isDark = isDark,
                onOpenBrowser = onOpenBrowser,
                onEditNote = onEditNote
            )
        }
    }
}
