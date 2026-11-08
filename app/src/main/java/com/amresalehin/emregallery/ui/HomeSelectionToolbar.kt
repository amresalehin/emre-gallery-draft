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
fun HomeSelectionToolbar(
    selectedUris: Set<String>,
    totalCount: Int,
    onClose: () -> Unit,
    onSelectAllToggle: () -> Unit,
    onShare: () -> Unit,
    onMoveToLocked: () -> Unit,
    onMoveToBin: () -> Unit,
    onAddToAlbum: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("floating_selection_toolbar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Cancel selection, count, select/deselect all
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp).testTag("selection_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit selection",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${selectedUris.size}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = onSelectAllToggle,
                    modifier = Modifier.size(40.dp).testTag("selection_select_all_button")
                ) {
                    val allSelected = selectedUris.size == totalCount && totalCount > 0
                    Icon(
                        imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                        contentDescription = if (allSelected) "Deselect all" else "Select all",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Right side: Action icons (Share, Lock, Bin, Add to Album)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(40.dp).testTag("selection_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share selected",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onMoveToLocked,
                    modifier = Modifier.size(40.dp).testTag("selection_lock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock selected",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onMoveToBin,
                    modifier = Modifier.size(40.dp).testTag("selection_bin_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Move to Bin",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(
                    onClick = onAddToAlbum,
                    modifier = Modifier.size(40.dp).testTag("selection_album_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Add to album",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
