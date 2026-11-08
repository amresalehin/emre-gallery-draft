package com.amresalehin.emregallery.ui
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
fun BrowserTopBar(
    addressInput: String,
    onAddressChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onHamburgerClick: () -> Unit,
    onDiscoveredMediaClick: () -> Unit,
    discoveredCount: Int = 0,
    onNewTabClick: () -> Unit,
    onReloadClick: () -> Unit,
    onPinCurrentSite: () -> Unit,
    onShareClick: () -> Unit,
    onDesktopToggle: (Boolean) -> Unit,
    isDesktopMode: Boolean = false,
    onClearHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isWebPage: Boolean = false,
    onHomeClick: (() -> Unit)? = null,
    onCloseBrowser: (() -> Unit)? = null,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }
    val barBorderColor by animateColorAsState(
        targetValue = if (isInputFocused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        } else {
            BrowserThemeColors.barOutline(isDark)
        },
        animationSpec = tween(200),
        label = "top_bar_border_color"
    )
    val barBorderWidth by animateDpAsState(
        targetValue = if (isInputFocused) 1.8.dp else 1.2.dp,
        animationSpec = tween(200),
        label = "top_bar_border_width"
    )
    Surface(
        shape = CircleShape,
        color = BrowserThemeColors.barSurface(isDark),
        tonalElevation = if (isInputFocused) 8.dp else 6.dp,
        shadowElevation = if (isInputFocused) 10.dp else 8.dp,
        border = BorderStroke(barBorderWidth, barBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("browser_monochrome_top_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isWebPage && onHomeClick != null) {
                        onHomeClick()
                    } else {
                        onHamburgerClick()
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .testTag("top_bar_hamburger_btn")
            ) {
                Icon(
                    imageVector = if (isWebPage) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                    contentDescription = if (isWebPage) "Back to home" else "Menu",
                    tint = BrowserThemeColors.iconTint(isDark),
                    modifier = Modifier.size(22.dp)
                )
            }
            AnimatedVisibility(
                visible = isInputFocused,
                enter = fadeIn(animationSpec = tween(180)) + expandHorizontally(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(150)) + shrinkHorizontally(animationSpec = tween(150))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 2.dp, end = 6.dp)
                            .size(20.dp)
                            .testTag("top_bar_search_icon")
                    )
                }
            }
            if (!isInputFocused && isWebPage) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Secure site",
                    tint = BrowserThemeColors.secondaryIconTint(isDark),
                    modifier = Modifier
                        .padding(start = 2.dp, end = 6.dp)
                        .size(16.dp)
                )
            } else if (!isInputFocused) {
                Spacer(Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (addressInput.isEmpty()) {
                    Text(
                        text = "Search or type URL",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = BrowserThemeColors.inputHint(isDark),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = addressInput,
                    onValueChange = onAddressChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (addressInput.isNotBlank()) {
                                onNavigate(addressInput)
                            }
                        }
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = BrowserThemeColors.textPrimary(isDark),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(if (isInputFocused) MaterialTheme.colorScheme.primary else BrowserThemeColors.textPrimary(isDark)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            isInputFocused = focusState.isFocused
                        }
                        .testTag("top_bar_address_input")
                )
            }
            if (addressInput.isNotEmpty()) {
                IconButton(
                    onClick = { onAddressChange("") },
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("top_bar_clear_input_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = BrowserThemeColors.secondaryIconTint(isDark),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = isInputFocused && addressInput.isNotBlank(),
                enter = fadeIn(animationSpec = tween(150)) + expandHorizontally(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(120)) + shrinkHorizontally(animationSpec = tween(120))
            ) {
                IconButton(
                    onClick = {
                        if (addressInput.isNotBlank()) {
                            onNavigate(addressInput)
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(36.dp)
                        .testTag("top_bar_submit_query_btn")
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Submit query",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDiscoveredMediaClick)
                    .testTag("top_bar_download_media_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DownloadForOffline,
                    contentDescription = "View discovered media",
                    tint = if (discoveredCount > 0) MaterialTheme.colorScheme.primary else BrowserThemeColors.iconTint(isDark),
                    modifier = Modifier.size(24.dp)
                )
                if (discoveredCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-1).dp, y = 1.dp)
                            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                            .testTag("top_bar_media_count_badge")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (discoveredCount > 99) "99+" else "$discoveredCount",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            BrowserTopBarMoreMenu(
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it },
                isDark = isDark,
                isDesktopMode = isDesktopMode,
                onHomeClick = onHomeClick,
                onNewTabClick = onNewTabClick,
                onReloadClick = onReloadClick,
                onPinCurrentSite = onPinCurrentSite,
                onShareClick = onShareClick,
                onDesktopToggle = onDesktopToggle,
                onSettingsClick = onSettingsClick,
                onClearHistoryClick = onClearHistoryClick,
                onCloseBrowser = onCloseBrowser
            )
        }
    }
}
