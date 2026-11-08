package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.*


object PocketThemeColors {
    fun background(isDark: Boolean): Color =
        if (isDark) Color(0xFF101010) else Color(0xFFFFFFFF)

    fun barSurface(isDark: Boolean): Color =
        if (isDark) Color(0xFF1C1C1C) else Color(0xFFF7F7F7)

    fun cardSurface(isDark: Boolean): Color =
        if (isDark) Color(0xFF181818) else Color(0xFFFFFFFF)

    fun primaryText(isDark: Boolean): Color =
        if (isDark) Color(0xFFEDEDED) else Color(0xFF141414)

    fun secondaryText(isDark: Boolean): Color =
        if (isDark) Color(0xFFA0A0A0) else Color(0xFF6E6E6E)

    fun divider(isDark: Boolean): Color =
        if (isDark) Color(0xFF2C2C2C) else Color(0xFFE2E2E2)

    fun thumbnailBg(isDark: Boolean): Color =
        if (isDark) Color(0xFF202020) else Color(0xFFF2F2F2)

    fun placeholderIcon(isDark: Boolean): Color =
        if (isDark) Color(0xFF707070) else Color(0xFFA0A0A0)

    fun pillSurface(isDark: Boolean): Color =
        if (isDark) Color(0xFF242424) else Color(0xFFF2F2F2)

    fun badgeBg(isDark: Boolean): Color =
        if (isDark) Color(0xFF222222).copy(alpha = 0.9f) else Color(0xFF1A1A1A).copy(alpha = 0.85f)

    fun badgeText(isDark: Boolean): Color =
        if (isDark) Color(0xFFEDEDED) else Color(0xFFFFFFFF)

    fun stackLayer1(isDark: Boolean): Color =
        if (isDark) Color(0xFF141414) else Color(0xFFE2E2E2)

    fun stackLayer2(isDark: Boolean): Color =
        if (isDark) Color(0xFF1C1C1C) else Color(0xFFECECEC)

    fun stackFront(isDark: Boolean): Color =
        if (isDark) Color(0xFF242424) else Color(0xFFFFFFFF)

    fun webGradient(isDark: Boolean): List<Color> =
        if (isDark) listOf(Color(0xFF222222), Color(0xFF181818))
        else listOf(Color(0xFFF7F7F7), Color(0xFFEAEAEA))

    fun faviconSurface(isDark: Boolean): Color =
        if (isDark) Color(0xFF282828) else Color(0xFFFFFFFF)

    fun drawerSurface(isDark: Boolean): Color =
        if (isDark) Color(0xFF181818) else Color(0xFFFFFFFF)

    fun drawerHandle(isDark: Boolean): Color =
        if (isDark) Color(0xFF484848) else Color(0xFFBDBDBD)

    fun detailRowSurface(isDark: Boolean): Color =
        if (isDark) Color(0xFF202020) else Color(0xFFF7F7F7)

    fun tagChipBg(isDark: Boolean): Color =
        if (isDark) Color(0xFF282828) else Color(0xFFECECEC)
}

/**
 * Pocket Screen
 * Displays saved bookmarks and web collections in a clean, modern staggered masonry layout.
 * Unified search & paste bar aligns with gallery search bar behavior, removing separate headers
 * and embedding navigation and filter controls within the capsule.
 * Clicking any bookmark card opens the Bookmark Viewer / Detail page with stacked card presentation
 * and structured metadata (Time, Source, Note, Tags, Title, Description).
 */
@OptIn(ExperimentalMaterial3Api::class)


@Composable
fun PocketSelectionToolbar(
    selectedCount: Int,
    allSelected: Boolean,
    onClearSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean = false
) {
    val barColor = PocketThemeColors.barSurface(isDark)
    val textColor = PocketThemeColors.primaryText(isDark)

    Surface(
        shape = CircleShape,
        color = barColor,
        tonalElevation = if (isDark) 4.dp else 2.dp,
        shadowElevation = if (isDark) 4.dp else 2.dp,
        border = BorderStroke(1.5.dp, textColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("pocket_selection_toolbar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier.size(38.dp).testTag("selection_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit selection",
                    tint = textColor
                )
            }

            Spacer(Modifier.width(4.dp))

            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.weight(1f).testTag("selection_count_text")
            )

            IconButton(
                onClick = onToggleSelectAll,
                modifier = Modifier.size(38.dp).testTag("selection_select_all_button")
            ) {
                Icon(
                    imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                    contentDescription = if (allSelected) "Deselect all" else "Select all",
                    tint = textColor
                )
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.size(38.dp).testTag("selection_share_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share selected bookmarks",
                    tint = textColor
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(38.dp).testTag("pocket_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete selected",
                    tint = textColor
                )
            }
        }
    }
}

/**
 * Pocket Bookmark Staggered Card conforming to image_15171895.png
 * Features:
 * - Rounded rectangle container (22.dp)
 * - Top preview image placeholder or real image/preview with varied aspect heights for masonry stagger
 * - Bottom white/surface pill section with Line 1 (Title) and Line 2 (Subtitle/Domain)
 */
@OptIn(ExperimentalFoundationApi::class)


@Composable
fun PocketEmptyState(
    searchQuery: String = "",
    onAddBookmark: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // When no bookmark is there: full empty as requested (clean empty background, no graphics/text/buttons)
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("pocket_empty_state")
    )
}

/**
 * Add Bookmark Dialog
 */




