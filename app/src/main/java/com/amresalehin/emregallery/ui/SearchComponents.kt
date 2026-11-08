package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.*
import java.util.*

// One UI / Samsung Gallery-inspired neutral color tokens
object SearchColors {
    val LightBg = Color(0xFFF1F2F4)
    val LightBarSurface = Color(0xFFE7E9ED)
    val LightCardSurface = Color(0xFFFAFAFB)
    val LightPrimaryText = Color(0xFF26313D)
    val LightSecondaryText = Color(0xFF77808C)
    val LightSelectedSurface = Color(0xFFDCE1E7)
    val LightDivider = Color(0xFFD9DDE2)

    val DarkBg = Color(0xFF131518)
    val DarkBarSurface = Color(0xFF1E2228)
    val DarkCardSurface = Color(0xFF23272F)
    val DarkPrimaryText = Color(0xFFE3E5E8)
    val DarkSecondaryText = Color(0xFF939AA3)
    val DarkSelectedSurface = Color(0xFF2E343D)
    val DarkDivider = Color(0xFF2C3038)
}

@Composable
fun searchBgColor(isDark: Boolean): Color = if (isDark) SearchColors.DarkBg else SearchColors.LightBg

@Composable
fun searchBarSurfaceColor(isDark: Boolean): Color = if (isDark) SearchColors.DarkBarSurface else SearchColors.LightBarSurface

@Composable
fun searchCardSurfaceColor(isDark: Boolean): Color = if (isDark) SearchColors.DarkCardSurface else SearchColors.LightCardSurface

@Composable
fun searchPrimaryTextColor(isDark: Boolean): Color = if (isDark) SearchColors.DarkPrimaryText else SearchColors.LightPrimaryText

@Composable
fun searchSecondaryTextColor(isDark: Boolean): Color = if (isDark) SearchColors.DarkSecondaryText else SearchColors.LightSecondaryText

@Composable
fun searchSelectedSurfaceColor(isDark: Boolean): Color = if (isDark) SearchColors.DarkSelectedSurface else SearchColors.LightSelectedSurface

@Composable
fun searchDividerColor(isDark: Boolean): Color = if (isDark) SearchColors.DarkDivider else SearchColors.LightDivider

/**
 * Compact, single-row rounded capsule search bar.
 * Contains: Back button inside leading edge, Search icon, text input with visible caret,
 * Clear button (when query is non-empty), and Filter funnel button inside the trailing edge.
 */
@Composable
fun SearchBarCapsule(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onBack: () -> Unit,
    onOpenFilterSheet: () -> Unit,
    hasActiveFilters: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    placeholder: String = "Search photos, people, places…"
) {
    val barColor = searchBarSurfaceColor(isDark)
    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)
    val dividerCol = searchDividerColor(isDark)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = CircleShape,
        color = barColor,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Inside Leading Back Button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("search_back_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Search Icon
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = secondaryText,
                modifier = Modifier
                    .padding(start = 2.dp, end = 8.dp)
                    .size(20.dp)
            )

            // Search Input with visible caret
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("search_input_field"),
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(textColor),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank()) {
                                onSearchSubmit(query.trim())
                            }
                        }
                    )
                )
            }

            // Clear Query Action 'X'
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("search_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Subtle vertical separator between text field and filter icon inside bar
            Spacer(
                modifier = Modifier
                    .height(20.dp)
                    .width(1.dp)
                    .background(dividerCol.copy(alpha = 0.7f))
            )

            // Trailing Filter Funnel Action Inside Search Bar
            Box {
                IconButton(
                    onClick = onOpenFilterSheet,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("search_filter_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Search filters",
                        tint = if (hasActiveFilters) textColor else secondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (hasActiveFilters) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(7.dp)
                            .background(textColor, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Horizontally scrollable quick-filter chips row.
 * Includes interactive toggle pills: Videos, Images, Gifs, GPS, No GPS,
 * Text, Without Text, Face, No Face, Time, and dynamic entity chips.
 * Tap to select, tap again to unselect.
 */
