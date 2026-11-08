package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.BrowserTabItem
import java.util.*


fun BrowserTabsBottomSheet(
    tabs: List<BrowserTabItem>,
    currentUrl: String,
    onSelectTab: (BrowserTabItem) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean = false
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BrowserThemeColors.barSurface(isDark),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tabs (${tabs.size})",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrowserThemeColors.textPrimary(isDark)
                )
                Button(
                    onClick = {
                        onNewTab()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrowserThemeColors.textPrimary(isDark)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = BrowserThemeColors.background(isDark), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Tab", color = BrowserThemeColors.background(isDark))
                }
            }

            Spacer(Modifier.height(16.dp))

            if (tabs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No open tabs",
                        color = BrowserThemeColors.textSecondary(isDark),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val isSelected = tab.url.equals(currentUrl, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = BrowserThemeColors.itemSurface(isDark),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) BrowserThemeColors.textPrimary(isDark) else BrowserThemeColors.itemOutline(isDark)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clickable {
                                    onSelectTab(tab)
                                    onDismiss()
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tab.title.ifBlank { "New Tab" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BrowserThemeColors.textPrimary(isDark),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (tabs.size > 1) {
                                        IconButton(
                                            onClick = { onCloseTab(tab.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close tab",
                                                tint = BrowserThemeColors.iconTint(isDark),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(BrowserThemeColors.cardSurface(isDark), RoundedCornerShape(10.dp))
                                        .border(1.dp, BrowserThemeColors.cardOutline(isDark), RoundedCornerShape(10.dp))
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.url.ifBlank { "Home Page" },
                                        fontSize = 11.sp,
                                        color = BrowserThemeColors.textSecondary(isDark),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet for browser settings: ad-blocker, desktop mode, search engine, clearing cache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
