package com.amresalehin.emregallery.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.BrowserSession
import com.amresalehin.emregallery.data.DownloadQueueManager
import com.amresalehin.emregallery.data.DownloaderSession
import com.amresalehin.emregallery.data.MediaInboxStack
import com.amresalehin.emregallery.data.ScrapedMediaItem


@Composable
internal fun ColumnScope.DiscoveredMediaListAndBottomBar(
    filteredItems: List<ScrapedMediaItem>,
    selectedIds: Set<String>,
    onSelectedIdsChange: (Set<String>) -> Unit,
    onDownloadItem: (ScrapedMediaItem) -> Unit,
    onDownloadBatch: (List<ScrapedMediaItem>) -> Unit
) {
    val context = LocalContext.current
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredItems, key = { it.id.ifBlank { it.url } }) { item ->
                        val itemId = item.id.ifBlank { item.url }
                        val isSelected = selectedIds.contains(itemId)

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onSelectedIdsChange(if (isSelected) selectedIds - itemId else selectedIds + itemId)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Checkbox for choosing
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        onSelectedIdsChange(if (checked) selectedIds + itemId else selectedIds - itemId)
                                    },
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(Modifier.width(10.dp))

                                AsyncImage(
                                    model = item.thumbnailUrl?.takeIf { it.isNotBlank() } ?: item.url,
                                    contentDescription = item.title,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title.ifBlank { item.url },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (item.isVideo || item.isStream) "Video" else if (item.isAudio) "Audio" else item.format.ifBlank { "Image" },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        item.resolutionHint?.takeIf { it.isNotBlank() }?.let { res ->
                                            Text(
                                                text = "• $res",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                // Single item download button
                                IconButton(
                                    onClick = {
                                        onDownloadItem(item)
                                        Toast.makeText(context, "Download queued", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download ${item.title}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom floating action bar for downloading chosen items
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedIds.isEmpty()) {
                                "Download all (${filteredItems.size})"
                            } else {
                                "${selectedIds.size} item${if (selectedIds.size > 1) "s" else ""} chosen"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Button(
                            onClick = {
                                val targets = if (selectedIds.isEmpty()) {
                                    filteredItems
                                } else {
                                    filteredItems.filter { selectedIds.contains(it.id.ifBlank { it.url }) }
                                }
                                onDownloadBatch(targets)
                            },
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (selectedIds.isEmpty()) "Download All" else "Download Selected"
                            )
                        }
                    }
                }
            }

}
