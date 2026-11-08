package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.*
import java.util.*


@Composable
internal fun DiscoveryContent(
    discoveryData: DiscoveryData?,
    recentSearches: List<String>,
    onSelectRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onSelectCategory: (DiscoveryCategory) -> Unit,
    onSelectEntity: (DiscoveryEntityItem) -> Unit,
    isDark: Boolean = false
) {
    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)
    val cardSurface = searchCardSurfaceColor(isDark)
    val dividerCol = searchDividerColor(isDark)
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
    ) {
        // 1. Recent Searches (Only if non-empty!)
        if (recentSearches.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Recent searches",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = textColor
                        )
                        Text(
                            text = "Clear all",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = secondaryText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onClearRecent)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("recent_search_clear_all")
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentSearches.take(8)) { recentQuery ->
                            Surface(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(CircleShape)
                                    .clickable { onSelectRecent(recentQuery) }
                                    .testTag("recent_chip_$recentQuery"),
                                shape = CircleShape,
                                color = cardSurface,
                                border = BorderStroke(1.dp, dividerCol)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = secondaryText
                                    )
                                    Text(
                                        text = recentQuery,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Search by Category (Photos, Videos, Documents, Favorites - only if non-empty)
        if (discoveryData != null && discoveryData.categories.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Search by category",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = textColor
                    )

                    // 2-column grid of compact category cards with mini-thumbnails
                    val chunkedCats = discoveryData.categories.chunked(2)
                    chunkedCats.forEach { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowCategories.forEach { category ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(108.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSelectCategory(category) }
                                        .testTag("category_card_${category.id}"),
                                    shape = RoundedCornerShape(14.dp),
                                    color = cardSurface,
                                    border = BorderStroke(1.dp, dividerCol.copy(alpha = 0.8f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                val catIcon = when (category.id) {
                                                    "cat_photos" -> Icons.Default.Image
                                                    "cat_videos" -> Icons.Default.Videocam
                                                    "cat_docs" -> Icons.Default.Description
                                                    "cat_favs" -> Icons.Default.Favorite
                                                    else -> Icons.Default.Folder
                                                }
                                                Icon(catIcon, null, modifier = Modifier.size(16.dp), tint = textColor)
                                                Text(
                                                    text = category.title,
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = textColor
                                                )
                                            }
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                null,
                                                modifier = Modifier.size(16.dp),
                                                tint = secondaryText
                                            )
                                        }

                                        // Mini-thumbnails row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            for (uri in category.sampleUris.take(3)) {
                                                AsyncImage(
                                                    model = remember(uri, context) {
                                                        ThumbnailUtils.buildThumbnailRequest(context, uri)
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(52.dp)
                                                        .clip(RoundedCornerShape(6.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            // Fill empty slots if fewer than 3
                                            for (i in 0 until (3 - category.sampleUris.size)) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                            if (rowCategories.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        discoveryEntityStripsItems(
            discoveryData = discoveryData,
            onSelectEntity = onSelectEntity,
            textColor = textColor,
            secondaryText = secondaryText,
            cardSurface = cardSurface,
            dividerCol = dividerCol,
            context = context
        )
    }
}
