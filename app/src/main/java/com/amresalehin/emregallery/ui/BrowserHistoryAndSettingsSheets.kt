package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.BrowserSite
import java.util.*


fun BrowserHistoryBottomSheet(
    history: List<BrowserSite>,
    onNavigate: (String) -> Unit,
    onDeleteSite: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean = false
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(history, search) {
        if (search.isBlank()) history else {
            history.filter {
                it.title.contains(search, ignoreCase = true) || it.domain.contains(search, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BrowserThemeColors.barSurface(isDark),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Browsing History",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrowserThemeColors.textPrimary(isDark)
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear All", color = Color(0xFFEF4444))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Search bar
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search history...", color = BrowserThemeColors.inputHint(isDark)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrowserThemeColors.iconTint(isDark)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BrowserThemeColors.textPrimary(isDark),
                    unfocusedTextColor = BrowserThemeColors.textPrimary(isDark),
                    focusedBorderColor = BrowserThemeColors.textPrimary(isDark),
                    unfocusedBorderColor = BrowserThemeColors.cardOutline(isDark)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (search.isBlank()) "No browsing history" else "No matching results",
                        color = BrowserThemeColors.textSecondary(isDark),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.url }) { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = BrowserThemeColors.itemSurface(isDark),
                            border = BorderStroke(1.dp, BrowserThemeColors.itemOutline(isDark)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onNavigate(item.url)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BrandFavicon(item.domain, item.title, isDark = isDark)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = BrowserThemeColors.textPrimary(isDark),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.domain} • ${formatRelativeBrowserTime(item.lastVisitedMillis)}",
                                        fontSize = 12.sp,
                                        color = BrowserThemeColors.textSecondary(isDark),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSite(item.url) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete entry",
                                        tint = BrowserThemeColors.secondaryIconTint(isDark),
                                        modifier = Modifier.size(16.dp)
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
 * Bottom sheet displaying open tabs grid, allowing switching, closing, and opening new tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable


fun BrowserSettingsBottomSheet(
    isDesktopMode: Boolean,
    onDesktopModeChange: (Boolean) -> Unit,
    onClearCacheClick: () -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean = false
) {
    var adBlockEnabled by remember { mutableStateOf(true) }
    var jsEnabled by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BrowserThemeColors.barSurface(isDark),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Browser Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = BrowserThemeColors.textPrimary(isDark)
            )

            Spacer(Modifier.height(16.dp))

            // Ad Block Engine Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Ad & Tracker Blocker", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = BrowserThemeColors.textPrimary(isDark))
                    Text("Blocks intrusive scripts and ads", fontSize = 12.sp, color = BrowserThemeColors.textSecondary(isDark))
                }
                Switch(
                    checked = adBlockEnabled,
                    onCheckedChange = { adBlockEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BrowserThemeColors.background(isDark),
                        checkedTrackColor = BrowserThemeColors.textPrimary(isDark)
                    )
                )
            }

            HorizontalDivider(color = BrowserThemeColors.divider(isDark))

            // Desktop Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Request Desktop Site", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = BrowserThemeColors.textPrimary(isDark))
                    Text("Forces desktop layout for sites", fontSize = 12.sp, color = BrowserThemeColors.textSecondary(isDark))
                }
                Switch(
                    checked = isDesktopMode,
                    onCheckedChange = onDesktopModeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BrowserThemeColors.background(isDark),
                        checkedTrackColor = BrowserThemeColors.textPrimary(isDark)
                    )
                )
            }

            HorizontalDivider(color = BrowserThemeColors.divider(isDark))

            // JavaScript Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("JavaScript Execution", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = BrowserThemeColors.textPrimary(isDark))
                    Text("Required for interactive web apps", fontSize = 12.sp, color = BrowserThemeColors.textSecondary(isDark))
                }
                Switch(
                    checked = jsEnabled,
                    onCheckedChange = { jsEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BrowserThemeColors.background(isDark),
                        checkedTrackColor = BrowserThemeColors.textPrimary(isDark)
                    )
                )
            }

            HorizontalDivider(color = BrowserThemeColors.divider(isDark))

            // Clear Cache & Cookies
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onClearCacheClick()
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Clear Cache & Cookies", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFFEF4444))
                    Text("Frees storage and resets web sessions", fontSize = 12.sp, color = BrowserThemeColors.textSecondary(isDark))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BrowserThemeColors.textSecondary(isDark))
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
