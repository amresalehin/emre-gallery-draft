package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amresalehin.emregallery.data.BrowserShortcut
import com.amresalehin.emregallery.data.BrowserSite
import java.util.*


fun BrowserHomeContent(
    shortcuts: List<BrowserShortcut>,
    recentSites: List<BrowserSite>,
    onNavigate: (String) -> Unit,
    onAddShortcutClick: () -> Unit,
    onEditShortcutClick: (BrowserShortcut) -> Unit,
    onSeeAllHistoryClick: () -> Unit,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    // If shortcuts and history are both absent, keep completely blank as instructed!
    if (shortcuts.isEmpty() && recentSites.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BrowserThemeColors.background(isDark))
                .testTag("browser_empty_home_page")
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BrowserThemeColors.background(isDark))
            .verticalScroll(rememberScrollState())
            .padding(top = 18.dp, bottom = 24.dp)
    ) {
        // 1. SHORTCUTS ROW (only rendered when real shortcuts exist)
        if (shortcuts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.Start),
                verticalAlignment = Alignment.Top
            ) {
                // Render real shortcuts
                shortcuts.take(4).forEach { item ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .combinedClickable(
                                onClick = { onNavigate(item.url) },
                                onLongClick = { onEditShortcutClick(item) }
                            )
                            .padding(vertical = 4.dp)
                            .testTag("shortcut_${item.id}")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = BrowserThemeColors.itemSurface(isDark),
                            border = BorderStroke(1.dp, BrowserThemeColors.itemOutline(isDark)),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when (item.iconKey) {
                                    "youtube" -> YouTubeLogo()
                                    "wikipedia" -> WikipediaLogo()
                                    "github" -> GitHubLogo()
                                    "drive" -> GoogleDriveLogo()
                                    else -> BrandFavicon(item.url, item.title, isDark = isDark)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = BrowserThemeColors.textPrimary(isDark),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Add button for user flexible pinning
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onAddShortcutClick)
                        .padding(vertical = 4.dp)
                        .testTag("shortcut_add_button")
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = BrowserThemeColors.itemSurface(isDark),
                        border = BorderStroke(1.dp, BrowserThemeColors.itemOutline(isDark)),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add shortcut",
                                tint = BrowserThemeColors.iconTint(isDark),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Add",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = BrowserThemeColors.textPrimary(isDark),
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
        }

        // 2. RECENT SECTION HEADER (only rendered when real history exists)
        if (recentSites.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = BrowserThemeColors.iconTint(isDark),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Recent",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrowserThemeColors.textPrimary(isDark)
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSeeAllHistoryClick)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "See all",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = BrowserThemeColors.textSecondary(isDark)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "See all history",
                        tint = BrowserThemeColors.textSecondary(isDark),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 3. RECENT ITEMS CARD
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BrowserThemeColors.cardSurface(isDark),
                border = BorderStroke(1.dp, BrowserThemeColors.cardOutline(isDark)),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("recent_history_card")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val displayList = recentSites.take(5)
                    displayList.forEachIndexed { index, site ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = BrowserThemeColors.divider(isDark),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(site.url) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("recent_item_$index"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrandFavicon(
                                domain = site.domain,
                                title = site.title,
                                isDark = isDark
                            )

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = site.title,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrowserThemeColors.textPrimary(isDark),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = site.domain,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = BrowserThemeColors.textSecondary(isDark),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = BrowserThemeColors.secondaryIconTint(isDark),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = formatRelativeBrowserTime(site.lastVisitedMillis),
                                    fontSize = 12.sp,
                                    color = BrowserThemeColors.secondaryIconTint(isDark)
                                )
                                Spacer(Modifier.width(4.dp))
                                NorthEastArrowIcon(tint = BrowserThemeColors.secondaryIconTint(isDark))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 4. DIALOGS & BOTTOM SHEETS
// =========================================================================

/**
 * Dialog for adding a new flexible pinned shortcut.
 */
@Composable
