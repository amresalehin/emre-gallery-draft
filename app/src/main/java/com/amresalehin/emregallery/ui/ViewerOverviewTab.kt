package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.DetectedMediaFace

/**
 * Overview Tab content:
 * 1. Albums (membership chips + add control in header)
 * 2. Tags (clean monochrome chips + add control in header)
 * 3. People (individual large portraits, engine selector, and indexing controls)
 * 4. Location (preview card opening map or placeholder to add location)
 * 5. Metadata (collapsible EXIF/video details)
 */
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
    listState: LazyListState = rememberLazyListState(),
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
    var metadataExpanded by remember { mutableStateOf(true) }

    val hasTags = customTags.isNotEmpty()
    val hasLocation = metadata["location"]?.isNotBlank() == true || metadata.containsKey("latitude")
    val assignedAlbums = remember(item.uri, customAlbums) {
        customAlbums.filter { item.uri in it.mediaUris }
    }

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

    val metadataRows = remember(metadata, item) {
        buildMetadataRows(item, metadata)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "overview_albums") {
            OverviewAlbumsSection(
                assignedAlbums = assignedAlbums,
                onAddToAlbum = onAddToAlbum
            )
        }

        item(key = "overview_tags") {
            OverviewTagsSection(
                hasTags = hasTags,
                customTags = customTags,
                onAddTag = onAddTag
            )
        }

        item(key = "overview_people") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewPeopleHeader(
                    selectedEngineId = selectedEngineId,
                    onSelectEngine = onSelectEngine
                )
                ViewerFaceContainer(
                    faceUiState = faceUiState,
                    onScanThisPhoto = onScanThisPhoto,
                    onCancelScan = onCancelScan,
                    onRescanPhoto = onRescanPhoto,
                    onRescanLowThreshold = onRescanLowThreshold,
                    onInstallModel = onInstallModel,
                    onRetryScan = onRetryScan,
                    onViewErrorDetails = onViewErrorDetails,
                    onEditTagOrPerson = onEditTagOrPerson,
                    onFaceClick = onFaceClick
                )
            }
        }

        item(key = "overview_location") {
            OverviewLocationSection(
                hasLocation = hasLocation,
                primaryLocation = primaryLocation,
                secondaryLocation = secondaryLocation,
                onOpenMap = onOpenMap,
                onEditLocation = onEditLocation
            )
        }

        if (metadataRows.isNotEmpty()) {
            item(key = "overview_metadata") {
                OverviewMetadataSection(
                    metadataRows = metadataRows,
                    expanded = metadataExpanded,
                    onToggleExpanded = { metadataExpanded = !metadataExpanded }
                )
            }
        }
    }
}
