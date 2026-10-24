package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.amresalehin.emregallery.data.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinScreen(
    items: List<MediaItem>,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onRestore: (MediaItem) -> Unit,
    onDeletePermanently: (MediaItem) -> Unit,
    onEmptyBin: (List<MediaItem>) -> Unit
) {
    var confirmEmptyBin by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Albums")
            }
            Column(Modifier.weight(1f)) {
                Text("Bin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${items.size} ${if (items.size == 1) "item" else "items"} · Restore or permanently delete",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { confirmEmptyBin = true }, enabled = items.isNotEmpty()) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Empty bin")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(76.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Bin is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Photos and videos you move to the Bin will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(items, key = { it.uri }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Column {
                            Box {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(Uri.parse(item.uri))
                                        .crossfade(true)
                                        .size(Size(720, 720))
                                        .precision(Precision.INEXACT)
                                        .build(),
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .clickable { onOpen(item) },
                                    contentScale = ContentScale.Crop
                                )
                                if (item.isVideo) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "Video",
                                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = { onRestore(item) },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Restore", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    IconButton(onClick = { onDeletePermanently(item) }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            Icons.Default.DeleteForever,
                                            contentDescription = "Delete ${item.name} permanently",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmEmptyBin) {
        AlertDialog(
            onDismissRequest = { confirmEmptyBin = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
            title = { Text("Empty Bin?") },
            text = { Text("Permanently delete all ${items.size} items in the Bin from this device? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmptyBin = false
                    onEmptyBin(items)
                }) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmptyBin = false }) { Text("Cancel") } }
        )
    }
}
