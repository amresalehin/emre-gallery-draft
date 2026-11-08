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
fun CreateAlbumDialog(
    presetParent: String = "",
    existingAlbums: List<CustomAlbum> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, parentPath: String) -> Unit
) {
    var albumName by remember { mutableStateOf("") }
    var parentPath by remember { mutableStateOf(presetParent) }
    var showParentDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (parentPath.isBlank()) "Create new folder or album" else "Create nested sub-folder")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = albumName,
                    onValueChange = { albumName = it },
                    label = { Text("Folder / Album name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (existingAlbums.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Parent album (optional):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { showParentDropdown = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (parentPath.isBlank()) "None (Root level)" else parentPath,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            DropdownMenu(
                                expanded = showParentDropdown,
                                onDismissRequest = { showParentDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None (Root level)") },
                                    onClick = {
                                        parentPath = ""
                                        showParentDropdown = false
                                    }
                                )
                                existingAlbums.forEach { alb ->
                                    DropdownMenuItem(
                                        text = { Text(alb.fullPath) },
                                        onClick = {
                                            parentPath = alb.fullPath
                                            showParentDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (albumName.isNotBlank()) {
                        onConfirm(albumName.trim(), parentPath)
                    }
                },
                enabled = albumName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)

fun CollectionPill(
    title: String,
    count: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconBg: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tag = "collection_pill_" + title.lowercase(Locale.ROOT).replace(" ", "_")
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(tag),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 3.5.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CollectionTile(
    title: String,
    count: String = "",
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconBg: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tag = "collection_tile_" + title.lowercase(Locale.ROOT).replace(" ", "_")
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 3.5.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (count.isNotBlank()) {
                    Text(
                        text = count,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
