package com.amresalehin.emregallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.amresalehin.emregallery.R

@Composable
fun FloatingSearchPill(
    label: String,
    onClick: () -> Unit,
    showMoreOptions: Boolean = false,
    onMenuClick: () -> Unit = {},
    onOptionsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("floating_search_pill")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp, end = if (showMoreOptions) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(44.dp).testTag("open_side_panel")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open navigation menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onClick)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showMoreOptions) {
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("home_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "View options",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingNavigationBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("floating_navigation_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Destination.HOME to ("Home" to Icons.Default.Home),
                Destination.ALBUMS to ("Albums" to Icons.Default.PhotoLibrary),
                Destination.POCKET to ("Pocket" to Icons.Default.Bookmark),
                Destination.SETTINGS to ("Settings" to Icons.Default.Settings)
            )

            items.forEach { (dest, pair) ->
                val (label, icon) = pair
                val selected = current == dest
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val pillWidth by animateDpAsState(
                    targetValue = if (selected) 58.dp else 40.dp,
                    animationSpec = snap(),
                    label = "nav_pill_width"
                )
                val pillColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    animationSpec = snap(),
                    label = "nav_pill_color"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = snap(),
                    label = "nav_icon_tint"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.94f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
                    label = "nav_item_press"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true, radius = 32.dp),
                            onClick = { onSelect(dest) }
                        )
                        .padding(vertical = 4.dp)
                        .testTag("nav_item_${label.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = pillColor,
                            modifier = Modifier.height(30.dp).width(pillWidth)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

