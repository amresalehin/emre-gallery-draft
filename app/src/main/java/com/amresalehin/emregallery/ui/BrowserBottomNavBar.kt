package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@Composable
fun BrowserBottomNavBar(
    tabCount: Int = 0,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = BrowserThemeColors.barSurface(isDark),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.2.dp, BrowserThemeColors.barOutline(isDark)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .testTag("browser_monochrome_bottom_nav")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Downloads
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenDownloads)
                    .testTag("bottom_nav_downloads")
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = "Downloads",
                    tint = BrowserThemeColors.iconTint(isDark),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Downloads",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrowserThemeColors.textPrimary(isDark)
                )
            }

            // 2. History
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenHistory)
                    .testTag("bottom_nav_history")
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "History",
                    tint = BrowserThemeColors.iconTint(isDark),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "History",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrowserThemeColors.textPrimary(isDark)
                )
            }

            // 3. Tabs (with count badge ONLY if tabCount > 0)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenTabs)
                    .testTag("bottom_nav_tabs")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Tab,
                        contentDescription = "Tabs",
                        tint = BrowserThemeColors.iconTint(isDark),
                        modifier = Modifier.size(24.dp)
                    )
                    if (tabCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-4).dp)
                                .size(15.dp)
                                .background(BrowserThemeColors.badgeBg(isDark), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$tabCount",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrowserThemeColors.badgeText(isDark)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Tabs",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrowserThemeColors.textPrimary(isDark)
                )
            }

            // 4. Browser Settings
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenBrowserSettings)
                    .testTag("bottom_nav_browser_settings")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Browser settings",
                    tint = BrowserThemeColors.iconTint(isDark),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Browser",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrowserThemeColors.textPrimary(isDark)
                )
            }
        }
    }
}

// =========================================================================
// 3. BROWSER HOME CONTENT (Shortcuts with flexible pinning & Recent history)
// If absent, keep completely blank.
// =========================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
