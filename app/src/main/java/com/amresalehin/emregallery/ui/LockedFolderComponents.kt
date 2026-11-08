package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
internal fun LockedMediaCard(
    item: MediaItem,
    isSelected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onUnlock: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .combinedClickable(
                        onClick = onOpen,
                        onLongClick = onLongPress
                    )
            ) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(item.uri, context) {
                        ThumbnailUtils.buildThumbnailRequest(context, item.uri)
                    },
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = if (ThumbnailBoundsTracker.activeViewerUri == item.uri) 0f else 1f
                    },
                    contentScale = ContentScale.Crop
                )
                if (item.isVideo) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp),
                        tint = Color.White
                    )
                }

                // Lock badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "In locked folder",
                        modifier = Modifier
                            .padding(6.dp)
                            .size(14.dp),
                        tint = Color.White
                    )
                }

                if (selectionMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(18.dp)
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onUnlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Move out", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable

@Composable
internal fun AddPhotosToLockedSheet(
    availableMedia: List<MediaItem>,
    onDismiss: () -> Unit,
    onAddSelected: (Set<String>) -> Unit
) {
    var selectedForLock by remember { mutableStateOf<Set<String>>(emptySet()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Add photos to Locked folder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${selectedForLock.size} selected to hide",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { onAddSelected(selectedForLock) },
                    enabled = selectedForLock.isNotEmpty()
                ) {
                    Text("Move to Locked")
                }
            }

            Spacer(Modifier.height(14.dp))

            if (availableMedia.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No other photos found in gallery", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    gridItems(availableMedia, key = { it.uri }) { item ->
                        val isSelected = item.uri in selectedForLock
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedForLock = if (isSelected) selectedForLock - item.uri else selectedForLock + item.uri
                                }
                        ) {
                            val context = LocalContext.current
                            AsyncImage(
                                model = remember(item.uri, context) {
                                    ThumbnailUtils.buildThumbnailRequest(context, item.uri)
                                },
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f)
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(3.dp)
                                        .size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
