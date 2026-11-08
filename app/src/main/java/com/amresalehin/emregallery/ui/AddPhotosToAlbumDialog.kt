package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import java.util.Locale


@Composable
fun AddPhotosToAlbumDialog(
    album: CustomAlbum,
    allMedia: List<MediaItem>,
    onDismiss: () -> Unit,
    onAddSelected: (List<String>) -> Unit
) {
    val selectedUris = remember { mutableStateListOf<String>() }
    val candidateMedia = remember(allMedia, album) {
        allMedia.filterNot { album.mediaUris.contains(it.uri) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add photos to ${album.name}")
        },
        text = {
            if (candidateMedia.isEmpty()) {
                Text("All existing photos are already in this album.")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(candidateMedia, key = { it.id }) { item ->
                            val isSelected = selectedUris.contains(item.uri)
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isSelected) {
                                            selectedUris.remove(item.uri)
                                        } else {
                                            selectedUris.add(item.uri)
                                        }
                                    }
                            ) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                AsyncImage(
                                    model = remember(item.uri, context) {
                                        ThumbnailUtils.buildThumbnailRequest(context, item.uri)
                                    },
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddSelected(selectedUris.toList()) },
                enabled = selectedUris.isNotEmpty()
            ) {
                Text("Add (${selectedUris.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
