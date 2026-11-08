package com.amresalehin.emregallery.ui
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.DetectedMediaFace
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.FaceProcessingCoordinator
internal fun androidx.compose.foundation.lazy.LazyListScope.overviewPeopleLocationMetadataItems(
    item: MediaItem,
    hasLocation: Boolean,
    primaryLocation: String,
    secondaryLocation: String,
    metadataRows: List<Pair<String, String>>,
    metadataExpanded: Boolean,
    onToggleMetadataExpanded: () -> Unit,
    faceUiState: ViewerFaceUiState,
    selectedEngineId: String,
    onSelectEngine: (String) -> Unit,
    onScanThisPhoto: () -> Unit,
    onCancelScan: () -> Unit,
    onRescanPhoto: () -> Unit,
    onRescanLowThreshold: () -> Unit,
    onInstallModel: () -> Unit,
    onRetryScan: () -> Unit,
    onViewErrorDetails: () -> Unit,
    onEditTagOrPerson: () -> Unit,
    onFaceClick: ((DetectedMediaFace) -> Unit)?,
    onOpenMap: () -> Unit,
    onEditLocation: () -> Unit
) {
        item(key = "overview_people") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "People",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val context = LocalContext.current
                    val isProcessingActive by FaceProcessingCoordinator.isProcessingActive.collectAsStateWithLifecycle()
                    val isBusy = isProcessingActive || FaceProcessingCoordinator.isBusy(context)
                    var modelMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clickable(enabled = !isBusy) { modelMenuExpanded = true }
                                .alpha(if (!isBusy) 1.0f else 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val currentEngineName = if (selectedEngineId == BuffaloLFaceEngine.ENGINE_ID) "Buffalo-L" else "YOLO5Face + MobileFaceNet"
                                Text(
                                    text = currentEngineName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch face model",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = modelMenuExpanded && !isBusy,
                            onDismissRequest = { modelMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Buffalo-L") },
                                enabled = !isBusy,
                                trailingIcon = if (selectedEngineId == BuffaloLFaceEngine.ENGINE_ID) {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                                onClick = {
                                    modelMenuExpanded = false
                                    if (!FaceProcessingCoordinator.isBusy(context)) {
                                        onSelectEngine(BuffaloLFaceEngine.ENGINE_ID)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("YOLO5Face + MobileFaceNet") },
                                enabled = !isBusy,
                                trailingIcon = if (selectedEngineId == EnteFaceConstants.ENGINE_ID) {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                                onClick = {
                                    modelMenuExpanded = false
                                    if (!FaceProcessingCoordinator.isBusy(context)) {
                                        onSelectEngine(EnteFaceConstants.ENGINE_ID)
                                    }
                                }
                            )
                        }
                    }
                }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (hasLocation) {
                        IconButton(
                            onClick = onEditLocation,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit location",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (hasLocation) {
                    DetailCard(onClick = onOpenMap) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = primaryLocation,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (secondaryLocation.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = secondaryLocation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                        modifier = Modifier.clickable { onEditLocation() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Add location", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        if (metadataRows.isNotEmpty()) {
            item(key = "overview_metadata") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleMetadataExpanded() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Metadata",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (metadataExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle metadata",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (metadataExpanded) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                metadataRows.forEachIndexed { index, (label, value) ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1.1f)
                                        )
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
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
