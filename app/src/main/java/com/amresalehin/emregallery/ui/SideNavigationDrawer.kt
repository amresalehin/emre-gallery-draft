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
fun SideNavigationDrawer(
    visible: Boolean,
    destination: Destination,
    albumFilter: String?,
    onNavigate: (Destination) -> Unit,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = Modifier.fillMaxSize().zIndex(100f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
                .testTag("side_panel_scrim")
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(304.dp)
                    .animateEnterExit(
                        enter = slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = spring(
                                dampingRatio = 0.88f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                        exit = slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(180, easing = FastOutSlowInEasing)
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Keep taps inside drawer from dismissing */ }
                    .testTag("side_panel"),
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_app_logo),
                                    contentDescription = "Emre Gallery logo",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Emre Gallery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Your media library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close navigation menu")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(Modifier.height(12.dp))
                    val drawerSections = listOf(
                        listOf(
                            Triple(Destination.HOME, "Home", Icons.Default.Home),
                            Triple(Destination.ALBUMS, "Albums", Icons.Default.PhotoLibrary),
                            Triple(Destination.POCKET, "Pocket", Icons.Default.Bookmark),
                            Triple(Destination.FAVOURITES, "Favourites", Icons.Default.Favorite)
                        ),
                        listOf(
                            Triple(Destination.PEOPLE, "People & pets", Icons.Default.Face),
                            Triple(Destination.MAP, "Map & Places", Icons.Default.Map)
                        ),
                        listOf(
                            Triple(Destination.LOCKED_FOLDER, "Locked folder", Icons.Default.Lock),
                            Triple(Destination.ARCHIVE, "Archive", Icons.Default.Archive),
                            Triple(Destination.BIN, "Bin", Icons.Default.Delete)
                        ),
                        listOf(
                            Triple(Destination.BROWSER, "Browser", Icons.Default.TravelExplore),
                            Triple(Destination.SETTINGS, "Settings", Icons.Default.Settings)
                        )
                    )

                    drawerSections.forEachIndexed { sectionIndex, group ->
                        if (sectionIndex > 0) {
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            Spacer(Modifier.height(6.dp))
                        }
                        group.forEach { (dest, label, icon) ->
                            val selected = destination == dest && albumFilter == null
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                    .clickable {
                                        onNavigate(dest)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

