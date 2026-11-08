package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.*


@Composable
internal fun BrowserTopBarMoreMenu(
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    isDark: Boolean,
    isDesktopMode: Boolean,
    onHomeClick: (() -> Unit)?,
    onNewTabClick: () -> Unit,
    onReloadClick: () -> Unit,
    onPinCurrentSite: () -> Unit,
    onShareClick: () -> Unit,
    onDesktopToggle: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onCloseBrowser: (() -> Unit)?
) {
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { onShowMenuChange(true) },
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("top_bar_more_menu_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More controls",
                        tint = BrowserThemeColors.iconTint(isDark),
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onShowMenuChange(false) },
                    modifier = Modifier
                        .background(BrowserThemeColors.barSurface(isDark))
                        .widthIn(min = 210.dp)
                        .border(1.dp, BrowserThemeColors.barOutline(isDark), RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Home", color = BrowserThemeColors.textPrimary(isDark), fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = BrowserThemeColors.iconTint(isDark)) },
                        onClick = {
                            onShowMenuChange(false)
                            onHomeClick?.invoke() ?: onNewTabClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New tab", color = BrowserThemeColors.textPrimary(isDark), fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = BrowserThemeColors.iconTint(isDark)) },
                        onClick = {
                            onShowMenuChange(false)
                            onNewTabClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Reload", color = BrowserThemeColors.textPrimary(isDark), fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = BrowserThemeColors.iconTint(isDark)) },
                        onClick = {
                            onShowMenuChange(false)
                            onReloadClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Shortcuts", color = BrowserThemeColors.textPrimary(isDark), fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = BrowserThemeColors.iconTint(isDark)) },
                        onClick = {
                            onShowMenuChange(false)
                            onPinCurrentSite()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share link", color = BrowserThemeColors.textPrimary(isDark), fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = BrowserThemeColors.iconTint(isDark)) },
                        onClick = {
                            onShowMenuChange(false)
                            onShareClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Desktop site", color = BrowserThemeColors.textPrimary(isDark), fontWeight = FontWeight.Medium)
                                if (isDesktopMode) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = BrowserThemeColors.iconTint(isDark), modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null, tint = BrowserThemeColors.iconTint(isDark)) },
                        onClick = {
                            onShowMenuChange(false)
                            onDesktopToggle(!isDesktopMode)
                        }
                    )
                    HorizontalDivider(color = BrowserThemeColors.divider(isDark))
                    DropdownMenuItem(
                        text = { Text("Browser settings", color = BrowserThemeColors.textPrimary(isDark), fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, tint = BrowserThemeColors.iconTint(isDark)) },
                        onClick = {
                            onShowMenuChange(false)
                            onSettingsClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear browsing history", color = BrowserThemeColors.textPrimary(isDark), fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = BrowserThemeColors.iconTint(isDark)) },
                        onClick = {
                            onShowMenuChange(false)
                            onClearHistoryClick()
                        }
                    )
                    if (onCloseBrowser != null) {
                        HorizontalDivider(color = BrowserThemeColors.divider(isDark))
                        DropdownMenuItem(
                            text = { Text("Close browser", color = Color(0xFFEF4444), fontWeight = FontWeight.Medium) },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFEF4444)) },
                            onClick = {
                                onShowMenuChange(false)
                                onCloseBrowser()
                            }
                        )
                    }
                }
            }
}

