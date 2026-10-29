package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Top and bottom edge gradient shading behind the overlay controls.
 */
@Composable
fun BoxScope.ViewerEdgeShading(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(260)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val edgeShadeHeight = maxHeight * 0.22f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(edgeShadeHeight)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.62f),
                                Color.Black.copy(alpha = 0.38f),
                                Color.Black.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(edgeShadeHeight)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.16f),
                                Color.Black.copy(alpha = 0.42f),
                                Color.Black.copy(alpha = 0.68f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Top action bar with back, share, favorite, and more options.
 */
@Composable
fun ViewerTopAppBar(
    isFavourite: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleFavourite: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewerButton(Icons.Default.ArrowBack, "Back", onBack)
        Spacer(Modifier.weight(1f))
        ViewerButton(Icons.Default.Share, "Share", onShare)
        ViewerButton(
            icon = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = "Favourite",
            onClick = onToggleFavourite
        )
        ViewerButton(Icons.Default.MoreVert, "More", onMore)
    }
}

/**
 * Story top bar with segmented progress indicators, memory title, and story controls.
 */
@Composable
fun ViewerStoryTopBar(
    storyTitle: String,
    currentIndex: Int,
    itemCount: Int,
    currentProgress: Float,
    isFavourite: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleFavourite: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Segmented progress indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val count = itemCount.coerceAtLeast(1)
            for (i in 0 until count) {
                val segmentProgress = when {
                    i < currentIndex -> 1f
                    i == currentIndex -> currentProgress.coerceIn(0f, 1f)
                    else -> 0f
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                ) {
                    if (segmentProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = segmentProgress)
                                .background(Color.White)
                        )
                    }
                }
            }
        }

        // Header with Back button, Title + subtitle, and action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViewerButton(Icons.Default.ArrowBack, "Close memory", onBack)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = storyTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${currentIndex + 1} of $itemCount",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            ViewerButton(Icons.Default.Share, "Share", onShare)
            ViewerButton(
                icon = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = "Favourite",
                onClick = onToggleFavourite
            )
            ViewerButton(Icons.Default.MoreVert, "More", onMore)
        }
    }
}

/**
 * Soft "End of memory" overlay shown after the last photo in a memory.
 */
@Composable
fun EndOfStoryOverlay(
    storyTitle: String,
    itemCount: Int,
    onReplay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-dismiss after 6 seconds if left untouched
    LaunchedEffect(Unit) {
        delay(6000L)
        onClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "You're all caught up",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = storyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "$itemCount photos and videos remembered",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Done")
                    }

                    Button(
                        onClick = onReplay,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Replay")
                    }
                }
            }
        }
    }
}

/**
 * Bottom action bar with Edit and Delete/Restore buttons.
 */
@Composable
fun ViewerBottomActionBar(
    isBin: Boolean,
    onEdit: () -> Unit,
    onToggleBin: () -> Unit,
    onDeletePermanently: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBin) {
            ViewerAction(
                icon = Icons.Default.Restore,
                label = "Restore",
                onClick = onToggleBin
            )
            if (onDeletePermanently != null) {
                ViewerAction(
                    icon = Icons.Default.DeleteForever,
                    label = "Delete permanently",
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onDeletePermanently
                )
            }
        } else {
            ViewerAction(Icons.Default.Tune, "Edit", onEdit)
            ViewerAction(
                icon = Icons.Default.Delete,
                label = "Delete",
                onClick = onToggleBin
            )
        }
    }
}

/**
 * Bottom sheet for extra media actions (Archive, Lock, Bin, Info).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerMoreBottomSheet(
    isBin: Boolean,
    isHidden: Boolean,
    isArchived: Boolean,
    onToggleBin: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleArchive: () -> Unit,
    onDeletePermanently: (() -> Unit)? = null,
    onShowDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (isBin) {
            ListItem(
                headlineContent = { Text("Restore to gallery") },
                leadingContent = { Icon(Icons.Default.Restore, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleBin() }
            )
            if (onDeletePermanently != null) {
                ListItem(
                    headlineContent = { Text("Delete permanently from device") },
                    leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier.clickable { onDismiss(); onDeletePermanently() }
                )
            }
        } else {
            ListItem(
                headlineContent = { Text(if (isHidden) "Unhide (return to gallery)" else "Move to Locked folder") },
                leadingContent = { Icon(if (isHidden) Icons.Default.Visibility else Icons.Default.Lock, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleHide() }
            )
            ListItem(
                headlineContent = { Text(if (isArchived) "Unarchive" else "Archive") },
                leadingContent = { Icon(Icons.Default.Archive, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleArchive() }
            )
            ListItem(
                headlineContent = { Text("Move to Bin") },
                leadingContent = { Icon(Icons.Default.Delete, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleBin() }
            )
        }
        ListItem(
            headlineContent = { Text("Media information") },
            leadingContent = { Icon(Icons.Default.Info, null) },
            modifier = Modifier.clickable { onDismiss(); onShowDetails() }
        )
        ListItem(
            headlineContent = { Text("Cancel") },
            leadingContent = { Icon(Icons.Default.Close, null) },
            modifier = Modifier.clickable { onDismiss() }
        )
        Spacer(Modifier.height(24.dp))
    }
}
