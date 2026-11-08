package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.DetectedMediaFace


@Composable
fun OverviewTabContent(
    item: MediaItem,
    customAlbums: List<CustomAlbum>,
    customTags: Set<String>,
    detectedFaces: List<DetectedMediaFace>,
    metadata: Map<String, String>,
    onOpenMap: () -> Unit,
    onAddToAlbum: () -> Unit,
    onAddTag: () -> Unit,
    onEditLocation: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onScanPeople: () -> Unit = {},
    isScanningPeople: Boolean = false,
    faceScanError: String? = null,
    faceUiState: ViewerFaceUiState = ViewerFaceUiState(
        state = if (isScanningPeople) ViewerFaceState.IN_PROGRESS
        else if (faceScanError != null) ViewerFaceState.FAILED
        else if (detectedFaces.isNotEmpty()) ViewerFaceState.COMPLETED_FACES_FOUND
        else ViewerFaceState.NOT_INDEXED,
        faces = detectedFaces,
        errorMessage = faceScanError
    ),
    onScanThisPhoto: () -> Unit = onScanPeople,
    onCancelScan: () -> Unit = {},
    onRescanPhoto: () -> Unit = onScanPeople,
    onRescanLowThreshold: () -> Unit = onScanPeople,
    onInstallModel: () -> Unit = {},
    onRetryScan: () -> Unit = onScanPeople,
    onViewErrorDetails: () -> Unit = {},
    onEditTagOrPerson: () -> Unit = {},
    onFaceClick: ((DetectedMediaFace) -> Unit)? = null,
    selectedEngineId: String = BuffaloLFaceEngine.ENGINE_ID,
    onSelectEngine: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var metadataExpanded by remember { mutableStateOf(true) }

    val hasTags = customTags.isNotEmpty()
    val hasPeople = detectedFaces.isNotEmpty()
    val hasLocation = metadata["location"]?.isNotBlank() == true || metadata.containsKey("latitude")
    val assignedAlbums = remember(item.uri, customAlbums) {
        customAlbums.filter { item.uri in it.mediaUris }
    }

    // Readable place split into primary and secondary
    val rawLocation = metadata["location"].orEmpty()
    val (primaryLocation, secondaryLocation) = remember(rawLocation, metadata["latitude"], metadata["longitude"]) {
        if (rawLocation.isNotBlank()) {
            val parts = rawLocation.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            when {
                parts.size > 1 -> parts.first() to parts.drop(1).joinToString(", ")
                parts.size == 1 -> parts.first() to ""
                else -> "Location" to ""
            }
        } else if (metadata.containsKey("latitude") && metadata.containsKey("longitude")) {
            val lat = metadata["latitude"]?.toDoubleOrNull() ?: 0.0
            val lon = metadata["longitude"]?.toDoubleOrNull() ?: 0.0
            "%.4f°, %.4f°".format(lat, lon) to "GPS coordinates"
        } else {
            "" to ""
        }
    }

    // Filter metadata to avoid duplicating header/location/tags
    val metadataRows = remember(metadata, item) {
        buildMetadataRows(item, metadata)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Albums Section
        item(key = "overview_albums") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Albums",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onAddToAlbum,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add to album",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (assignedAlbums.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        assignedAlbums.forEach { album ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { onAddToAlbum() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PhotoAlbum,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = album.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable { onAddToAlbum() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Add to album", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // 2. Tags Section
        item(key = "overview_tags") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onAddTag,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add tag",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (hasTags) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        customTags.sorted().forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable { onAddTag() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Add tag", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        overviewPeopleLocationMetadataItems(
            item = item,
            hasLocation = hasLocation,
            primaryLocation = primaryLocation,
            secondaryLocation = secondaryLocation,
            metadataRows = metadataRows,
            metadataExpanded = metadataExpanded,
            onToggleMetadataExpanded = { metadataExpanded = !metadataExpanded },
            faceUiState = faceUiState,
            selectedEngineId = selectedEngineId,
            onSelectEngine = onSelectEngine,
            onScanThisPhoto = onScanThisPhoto,
            onCancelScan = onCancelScan,
            onRescanPhoto = onRescanPhoto,
            onRescanLowThreshold = onRescanLowThreshold,
            onInstallModel = onInstallModel,
            onRetryScan = onRetryScan,
            onViewErrorDetails = onViewErrorDetails,
            onEditTagOrPerson = onEditTagOrPerson,
            onFaceClick = onFaceClick,
            onOpenMap = onOpenMap,
            onEditLocation = onEditLocation
        )
    }
}
