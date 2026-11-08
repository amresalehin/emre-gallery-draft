package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha


internal fun AlbumsEmptyState(
    onCreateClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.4f else 0.55f)
    val textBarPrimary = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.32f else 0.22f)
    val textBarSecondary = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.20f else 0.12f)

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(4) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .width(160.dp)
                    .height(220.dp)
                    .clickable { onCreateClick() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 2x2 placeholder grid
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(136.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PlaceholderGridCell(Modifier.weight(1f).fillMaxHeight())
                            PlaceholderGridCell(Modifier.weight(1f).fillMaxHeight())
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PlaceholderGridCell(Modifier.weight(1f).fillMaxHeight())
                            PlaceholderGridCell(Modifier.weight(1f).fillMaxHeight())
                        }
                    }

                    // Skeleton text bars at bottom
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(textBarPrimary)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.42f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(textBarSecondary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FoldersEmptyState() {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.4f else 0.55f)
    val textBarPrimary = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.32f else 0.22f)
    val textBarSecondary = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.20f else 0.12f)

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(4) {
            Surface(
                shape = FolderTabShape(),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .width(160.dp)
                    .height(220.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 10.dp, end = 10.dp, top = 26.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(136.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PlaceholderGridCell(Modifier.weight(1f).fillMaxHeight())
                            PlaceholderGridCell(Modifier.weight(1f).fillMaxHeight())
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PlaceholderGridCell(Modifier.weight(1f).fillMaxHeight())
                            PlaceholderGridCell(Modifier.weight(1f).fillMaxHeight())
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(textBarPrimary)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.42f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(textBarSecondary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlaceholderGridCell(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val cellBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.38f else 0.32f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(cellBg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}
