package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.face.FaceStoreEvents
import androidx.compose.ui.draw.alpha
import com.amresalehin.emregallery.face.FaceProcessingCoordinator
import com.amresalehin.emregallery.face.PersonSummary


internal fun androidx.compose.foundation.lazy.LazyListScope.albumsPeopleCardItem(
    context: android.content.Context,
    isBusy: Boolean,
    selectedEngineId: String,
    onSelectedEngineIdChange: (String) -> Unit,
    engineMenuExpanded: Boolean,
    onEngineMenuExpandedChange: (Boolean) -> Unit,
    detectedPeople: List<PersonSummary>,
    onFilter: (String) -> Unit,
    onOpenPerson: ((String) -> Unit)?
) {
        // 1. People card: single row, very large clickable faces, model dropdown pill, and 'See all >'
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("albums_people_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row: Model selector pill on left, "See all >" on right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Model Dropdown Pill
                        Box {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .clickable(enabled = !isBusy) { onEngineMenuExpandedChange(true) }
                                    .alpha(if (!isBusy) 1.0f else 0.5f)
                                    .testTag("album_face_model_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Face,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (selectedEngineId == com.amresalehin.emregallery.face.EnteFaceConstants.ENGINE_ID) "YOLO5Face" else "Buffalo-L",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Select engine",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = engineMenuExpanded && !isBusy,
                                onDismissRequest = { onEngineMenuExpandedChange(false) }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Buffalo-L") },
                                    enabled = !isBusy,
                                    trailingIcon = if (selectedEngineId == com.amresalehin.emregallery.face.BuffaloLFaceEngine.ENGINE_ID) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    onClick = {
                                        onEngineMenuExpandedChange(false)
                                        if (!FaceProcessingCoordinator.isBusy(context)) {
                                            selectedEngineId = com.amresalehin.emregallery.face.BuffaloLFaceEngine.ENGINE_ID
                                            FaceRecognitionSettings.setActiveEngine(context, com.amresalehin.emregallery.face.BuffaloLFaceEngine.ENGINE_ID)
                                            com.amresalehin.emregallery.face.FaceEngineRegistry.setActiveEngine(com.amresalehin.emregallery.face.BuffaloLFaceEngine.ENGINE_ID)
                                            FaceStoreEvents.notifyAllUpdated()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("YOLO5Face + MobileFaceNet") },
                                    enabled = !isBusy,
                                    trailingIcon = if (selectedEngineId == com.amresalehin.emregallery.face.EnteFaceConstants.ENGINE_ID) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    onClick = {
                                        onEngineMenuExpandedChange(false)
                                        if (!FaceProcessingCoordinator.isBusy(context)) {
                                            selectedEngineId = com.amresalehin.emregallery.face.EnteFaceConstants.ENGINE_ID
                                            FaceRecognitionSettings.setActiveEngine(context, com.amresalehin.emregallery.face.EnteFaceConstants.ENGINE_ID)
                                            com.amresalehin.emregallery.face.FaceEngineRegistry.setActiveEngine(com.amresalehin.emregallery.face.EnteFaceConstants.ENGINE_ID)
                                            FaceStoreEvents.notifyAllUpdated()
                                        }
                                    }
                                )
                            }
                        }

                        // "See all >" Pill
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .clickable { onFilter("People") }
                                .testTag("album_face_see_all_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "See all",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "See all people",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Single horizontal row containing very large circular faces
                    if (detectedPeople.isEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(4) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clickable { onFilter("People") }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Face,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                            modifier = Modifier.size(38.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            items(detectedPeople.size, key = { detectedPeople[it].id }) { index ->
                                val person = detectedPeople[index]
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(96.dp)
                                        .clickable(enabled = !isBusy) {
                                            if (!FaceProcessingCoordinator.isBusy(context)) {
                                                if (onOpenPerson != null) {
                                                    onOpenPerson(person.id)
                                                } else {
                                                    onFilter("People")
                                                }
                                            }
                                        }
                                        .alpha(if (!isBusy) 1.0f else 0.5f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier.size(96.dp)
                                    ) {
                                        FaceCropThumbnail(
                                            mediaUri = person.portraitUri ?: person.sampleUri,
                                            faceIndex = person.portraitFaceIndex,
                                            engineId = person.engineId.ifBlank { selectedEngineId },
                                            contentDescription = person.displayName.takeIf { it.isNotBlank() } ?: "Person",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    }

                                    // Under each face: show name ONLY if configured/available. If not, show nothing!
                                    if (person.isNamed && person.displayName.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = person.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
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
