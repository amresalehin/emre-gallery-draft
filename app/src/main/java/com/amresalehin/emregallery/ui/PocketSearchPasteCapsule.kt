package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*


@Composable
fun PocketSearchPasteCapsule(
    query: String,
    onQueryChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onOpenFilterSheet: () -> Unit,
    hasActiveFilters: Boolean,
    onBookmarkAction: () -> Unit,
    onSearchSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val barColor = PocketThemeColors.barSurface(isDark)
    val textColor = PocketThemeColors.primaryText(isDark)
    val secondaryText = PocketThemeColors.secondaryText(isDark)
    val dividerCol = PocketThemeColors.divider(isDark)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = CircleShape,
        color = barColor,
        border = BorderStroke(1.dp, if (isDark) dividerCol else Color(0xFFDCDCDC)),
        tonalElevation = if (isDark) 2.dp else 1.dp,
        shadowElevation = if (isDark) 2.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Button: Back button if focused or query is non-empty, otherwise Hamburger Menu
            IconButton(
                onClick = {
                    if (isFocused || query.isNotEmpty()) {
                        onQueryChange("")
                        onFocusChange(false)
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    } else {
                        onMenuClick()
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .testTag(if (isFocused || query.isNotEmpty()) "pocket_search_back_button" else "pocket_menu_button")
            ) {
                Icon(
                    imageVector = if (isFocused || query.isNotEmpty()) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                    contentDescription = if (isFocused || query.isNotEmpty()) "Back" else "Open navigation menu",
                    tint = textColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (!isFocused && query.isEmpty()) {
                // Divider |
                Spacer(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(if (isDark) dividerCol.copy(alpha = 0.8f) else Color(0xFFDCDCDC))
                )
            }

            Spacer(Modifier.width(8.dp))

            // Search / URL Input with visible caret
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search library or paste URL",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
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
                        .onFocusChanged { focusState ->
                            onFocusChange(focusState.isFocused)
                        }
                        .testTag("pocket_search_input"),
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(textColor),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
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

            // Trailing actions:
            // When focused (or query non-empty), shows cross (clear) and bookmark icon (matching Maindownloader behavior)
            if (isFocused || query.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Cross (clear) button
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("pocket_search_clear_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Bookmark icon button (monochrome theme)
                    IconButton(
                        onClick = onBookmarkAction,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("pocket_search_bookmark_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "Save bookmark",
                            tint = textColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                // Divider |
                Spacer(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(if (isDark) dividerCol.copy(alpha = 0.8f) else Color(0xFFDCDCDC))
                )

                // Trailing Filter Sliders Action inside the search bar
                Box {
                    IconButton(
                        onClick = onOpenFilterSheet,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("pocket_tune_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filter and sort bookmarks",
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
}

/**
 * Pocket Selection Mode Toolbar
 */
