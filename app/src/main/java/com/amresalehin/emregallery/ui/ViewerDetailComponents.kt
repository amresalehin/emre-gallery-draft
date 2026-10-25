package com.amresalehin.emregallery.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.ocr.TesseractOcr

@Composable
fun ViewerButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.94f), CircleShape)
    ) {
        Icon(icon, label, tint = scheme.onSurface)
    }
}

@Composable
fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = scheme.onSurface)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = scheme.onSurface, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun DetailActionPill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 1.dp,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
fun ExifColumn(top: String, bottom: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = top,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = bottom,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            fontSize = 9.sp
        )
    }
}

@Composable
fun MetaKeyValueRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(78.dp),
            fontSize = 11.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            fontSize = 11.sp
        )
    }
}

@Composable
fun DetailHeaderSection(
    item: MediaItem,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatMediaDateTime(item.dateTakenMillis, item.dateAddedSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.clickable { onEdit() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun DetailQuickActionsRow(
    isBin: Boolean,
    onShare: () -> Unit,
    onAddTo: () -> Unit,
    onToggleBin: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailActionPill(
            icon = Icons.Default.Share,
            label = "Share",
            modifier = Modifier.weight(1f),
            onClick = onShare
        )
        DetailActionPill(
            icon = Icons.Default.Add,
            label = "Add to",
            modifier = Modifier.weight(1f),
            onClick = onAddTo
        )
        DetailActionPill(
            icon = Icons.Default.Folder,
            label = "Move to",
            modifier = Modifier.weight(1f),
            onClick = onAddTo
        )
        DetailActionPill(
            icon = if (isBin) Icons.Default.Restore else Icons.Default.Delete,
            label = if (isBin) "Restore" else "Delete",
            modifier = Modifier.weight(1f),
            onClick = onToggleBin
        )
        DetailActionPill(
            icon = Icons.Default.MoreHoriz,
            label = "More",
            modifier = Modifier.weight(1f),
            onClick = onMore
        )
    }
}

@Composable
fun DetailLocationCard(
    locationText: String,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    DetailCard(modifier = modifier.clickable(onClick = onOpenMap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = "Location",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = locationText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(10.dp))
            // Mini Map Graphic
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFCBE3CE))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val riverColor = Color(0xFF90CAF9)
                    val roadColor = Color.White.copy(alpha = 0.75f)
                    drawLine(
                        color = riverColor,
                        start = Offset(x = size.width * 0.25f, y = size.height),
                        end = Offset(x = size.width * 0.85f, y = 0f),
                        strokeWidth = 14f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = roadColor,
                        start = Offset(x = 0f, y = size.height * 0.45f),
                        end = Offset(x = size.width, y = size.height * 0.55f),
                        strokeWidth = 6f
                    )
                }
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFFE53935),
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center)
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun DetailAiSummaryCard(
    modifier: Modifier = Modifier
) {
    DetailCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "Visual description",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Snow-covered mountains at sunset with a lake reflecting the peaks, a small village, and dramatic clouds in the sky.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun DetailOcrCard(
    item: MediaItem,
    ocrText: String,
    ocrError: String?,
    ocrBusy: Boolean,
    ocrLanguages: List<String>,
    ocrModel: String = "fast",
    onSelectModel: ((String) -> Unit)? = null,
    onToggleLanguage: (String) -> Unit,
    onRunOcr: () -> Unit,
    onCopyText: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBest = ocrModel.equals("best", ignoreCase = true)

    DetailCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = "Recognized text",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "OCR Text",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))

                // Model Selection Pill Toggle
                if (onSelectModel != null && !item.isVideo) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = if (!isBest) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.clickable(!ocrBusy) { onSelectModel("fast") }
                            ) {
                                Text(
                                    "Fast",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (!isBest) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!isBest) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (isBest) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                                modifier = Modifier.clickable(!ocrBusy) { onSelectModel("best") }
                            ) {
                                Text(
                                    "Best",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isBest) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }

                IconButton(
                    onClick = onRunOcr,
                    enabled = !item.isVideo && !ocrBusy && ocrLanguages.isNotEmpty()
                ) {
                    if (ocrBusy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = if (ocrText.isBlank()) "Recognize text" else "Recognize again"
                        )
                    }
                }
                if (ocrText.isNotBlank() && ocrText != "No text detected in this image.") {
                    IconButton(onClick = onCopyText) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy recognized text")
                    }
                }
            }

            if (!item.isVideo) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TesseractOcr.supportedLanguages.forEach { (languageName, code) ->
                        FilterChip(
                            selected = code in ocrLanguages,
                            onClick = { onToggleLanguage(code) },
                            label = { Text(languageName) },
                            enabled = !ocrBusy
                        )
                    }
                }
            }

            if (ocrError != null) {
                Text(
                    ocrError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (item.isVideo) {
                Text(
                    "Text recognition is unavailable for videos.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (ocrText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        ocrText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun DetailTagsCard(
    tags: Set<String>,
    onAddTag: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedTags = remember(tags) { tags.toList().sorted() }
    DetailCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocalOffer,
                contentDescription = "Tags",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sortedTags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { onAddTag() }
                ) {
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add tag",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun DetailPeopleCard(
    mediaUri: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val faceStore = remember(context) { FaceClusterStore(context.applicationContext) }
    val detectedPeople = remember(mediaUri) {
        faceStore.summaries().filter { cluster ->
            faceStore.facesForCluster(cluster.id).any { face -> face.mediaUri == mediaUri }
        }
    }

    DetailCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PersonOutline,
                contentDescription = "People detected in this photo",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "People",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (detectedPeople.isEmpty()) {
                    Text(
                        text = "No recognized people in this photo. Scan photos in People & pets to identify faces.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        detectedPeople.forEach { person ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.widthIn(max = 76.dp)
                            ) {
                                val sampleUri = person.sampleUri
                                if (sampleUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(Uri.parse(sampleUri))
                                            .size(120, 120)
                                            .crossfade(false)
                                            .build(),
                                        contentDescription = person.displayName,
                                        modifier = Modifier.size(46.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Face, contentDescription = null)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = person.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailAlbumsCard(
    item: MediaItem,
    customAlbums: List<CustomAlbum>,
    items: List<MediaItem>,
    onAddTo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val containingAlbums = remember(customAlbums, item.uri) {
        customAlbums.filter { item.uri in it.mediaUris }
    }

    DetailCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = "Albums",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Albums",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (containingAlbums.isEmpty()) {
                    Text(
                        text = "Not in any custom album",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    containingAlbums.forEach { album ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(width = 48.dp, height = 36.dp)
                            ) {
                                val cover = items.firstOrNull { it.uri in album.mediaUris }
                                if (cover != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(Uri.parse(cover.uri))
                                            .size(144, 108)
                                            .crossfade(false)
                                            .build(),
                                        contentDescription = album.fullPath,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PhotoAlbum, contentDescription = null)
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (album.parentPath.isNotBlank()) {
                                    Text(
                                        text = album.parentPath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAddTo) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit")
            }
        }
    }
}

@Composable
fun DetailMetadataCard(
    metadata: Map<String, String>,
    item: MediaItem,
    modifier: Modifier = Modifier
) {
    val metadataRows = remember(
        metadata,
        item.uri,
        item.dateTakenMillis,
        item.dateAddedSeconds,
        item.width,
        item.height,
        item.sizeBytes,
        item.durationMillis,
        item.isVideo,
        item.mimeType
    ) {
        val videoWidth = metadata["video_width"]?.toIntOrNull() ?: 0
        val videoHeight = metadata["video_height"]?.toIntOrNull() ?: 0
        val width = item.width.takeIf { it > 0 } ?: videoWidth
        val height = item.height.takeIf { it > 0 } ?: videoHeight
        val dimensions = if (width > 0 && height > 0) "$width × $height px" else null

        val videoDurationMillis = item.durationMillis.takeIf { it > 0 }
            ?: metadata["video_duration_ms"]?.toLongOrNull()?.takeIf { it > 0 }
        val videoDuration = if (item.isVideo) {
            videoDurationMillis?.let { formatDuration(it) }
        } else {
            null
        }
        val bitrate = metadata["video_bitrate_bps"]?.toLongOrNull()?.takeIf { it > 0L }?.let {
            when {
                it >= 1_000_000L -> "%.2f Mbps".format(it / 1_000_000.0)
                it >= 1_000L -> "%.0f kbps".format(it / 1_000.0)
                else -> "$it bps"
            }
        }
        val captureFrameRate = metadata["video_capture_framerate"]?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }?.let { "%.2f fps".format(it) }
        val rotation = metadata["video_rotation_degrees"]?.toIntOrNull()?.let { "$it°" }
        val audioTrack = metadata["video_has_audio"]?.let {
            when {
                it.equals("yes", ignoreCase = true) -> "Present"
                it.equals("no", ignoreCase = true) -> "Not present"
                else -> it
            }
        }
        val fileFormat = item.mimeType?.takeIf { it.isNotBlank() }
            ?: metadata["mime_type"]?.takeIf { it.isNotBlank() }

        val rows = listOf(
            "Media type" to if (item.isVideo) "Video" else "Image",
            "Duration" to videoDuration,
            "File format" to fileFormat,
            "Dimensions" to dimensions,
            "File size" to item.sizeBytes.takeIf { it > 0 }?.let { formatBytes(it) },
            "Video bitrate" to if (item.isVideo) bitrate else null,
            "Capture frame rate" to if (item.isVideo) captureFrameRate else null,
            "Rotation" to if (item.isVideo) rotation else null,
            "Audio track" to if (item.isVideo) audioTrack else null,
            "Track count" to if (item.isVideo) metadata["video_track_count"] else null,
            "Device manufacturer" to metadata["make"],
            "Device model" to metadata["model"],
            "Aperture" to metadata["aperture"],
            "Shutter speed" to metadata["shutter"],
            "ISO" to metadata["iso"],
            "Focal length" to metadata["focal"],
            "Flash" to metadata["flash"],
            "Color space" to metadata["color"],
            "Location" to metadata["location"],
            "Latitude" to metadata["latitude"],
            "Longitude" to metadata["longitude"],
            "Title" to (metadata["title"]?.takeIf { it.isNotBlank() }
                ?: item.name.substringBeforeLast('.').takeIf { it.isNotBlank() }),
            "Description" to metadata["description"],
            "Creator" to metadata["creator"],
            "Album" to metadata["album"],
            "Genre" to metadata["genre"],
            "Year" to metadata["year"],
            "Copyright" to metadata["copyright"],
            "Subject" to metadata["subject"],
            "Keywords" to metadata["keywords"],
            "Rating" to metadata["rating"],
            "Label" to metadata["label"],
            "Capture date" to (metadata["capture_date"]?.takeIf { it.isNotBlank() }
                ?: item.dateTakenMillis.takeIf { it > 0 }?.let { formatIsoDate(it, item.dateAddedSeconds) })
        )
        rows.mapNotNull { (label, value) ->
            value?.trim()?.takeIf { it.isNotEmpty() && it != "Not available" }?.let { label to it }
        }
    }

    if (metadataRows.isEmpty()) return

    DetailCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Article,
                contentDescription = "Media metadata",
                modifier = Modifier.size(22.dp).padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Metadata",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metadataRows.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                label,
                                modifier = Modifier.weight(0.9f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                value,
                                modifier = Modifier.weight(1.1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
