package com.amresalehin.emregallery.ui

import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.FaceClusterStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.amresalehin.emregallery.face.PersonSummary


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeopleDialogsAndSheets(
    store: FaceClusterStore,
    selectedEngineId: String,
    selectedPerson: PersonSummary?,
    selectedPersonId: String?,
    onSelectedPersonIdChange: (String?) -> Unit,
    visiblePeople: List<PersonSummary>,
    detailMedia: List<MediaItem>,
    showAvatarPicker: Boolean,
    onDismissAvatarPicker: () -> Unit,
    renamePersonId: String?,
    onDismissRename: () -> Unit,
    renameValue: String,
    onRenameValueChange: (String) -> Unit,
    personToMerge: PersonSummary?,
    onDismissMerge: () -> Unit,
    selectedTargetMergeId: String?,
    onSelectedTargetMergeIdChange: (String?) -> Unit,
    selectedMediaForAction: MediaItem?,
    onDismissMediaAction: () -> Unit,
    onReload: () -> Unit
) {
    val reloadScope = rememberCoroutineScope()
    // Avatar Picker Modal Bottom Sheet
    if (showAvatarPicker && selectedPerson != null) {
        ModalBottomSheet(
            onDismissRequest = { onDismissAvatarPicker() },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Choose profile photo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap any photo to set it as this person's avatar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    gridItems(detailMedia, key = { "avatar_${it.uri}" }) { mediaItem ->
                        val context = LocalContext.current
                        AsyncImage(
                            model = remember(mediaItem.uri, context) {
                                ThumbnailUtils.buildThumbnailRequest(context, mediaItem.uri)
                            },
                            contentDescription = "Select as avatar",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    reloadScope.launch(Dispatchers.IO) {
                                        store.setPersonCover(selectedPerson.id, mediaItem.uri)
                                        onReload()
                                    }
                                    onDismissAvatarPicker()
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }

    // Rename Person Dialog
    val currentRenamePersonId = renamePersonId
    if (currentRenamePersonId != null) {
        val targetId = currentRenamePersonId
        AlertDialog(
            onDismissRequest = { onDismissRename() },
            title = { Text(if (renameValue.isBlank()) "Name this person" else "Rename person") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = onRenameValueChange,
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = renameValue.trim()
                        val target = targetId
                        onDismissRename()
                        if (cleanName.isNotBlank()) {
                            reloadScope.launch(Dispatchers.IO) {
                                store.renamePerson(target, cleanName)
                                onReload()
                            }
                        }
                    },
                    enabled = renameValue.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissRename() }) { Text("Cancel") }
            }
        )
    }

    // Merge People Dialog
    val currentPersonToMerge = personToMerge
    if (currentPersonToMerge != null) {
        val sourcePerson = currentPersonToMerge
        val mergeCandidates = visiblePeople.filter { it.id != sourcePerson.id }

        AlertDialog(
            onDismissRequest = { onDismissMerge() },
            title = { Text("Merge with someone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Select the person to combine with ${if (sourcePerson.isNamed) sourcePerson.displayName else "this person"}:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    mergeCandidates.forEach { candidate ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedTargetMergeId == candidate.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectedTargetMergeIdChange(candidate.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedTargetMergeId == candidate.id,
                                    onClick = { onSelectedTargetMergeIdChange(candidate.id) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (candidate.isNamed) candidate.displayName else "Unnamed (${candidate.faceCount} photos)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetMerge = selectedTargetMergeId
                        if (targetMerge != null) {
                            val srcId = sourcePerson.id
                            onSelectedPersonIdChange(targetMerge)
                            onDismissMerge()
                            onSelectedTargetMergeIdChange(null)
                            reloadScope.launch(Dispatchers.IO) {
                                store.mergePeople(srcId, targetMerge)
                                onReload()
                            }
                        }
                    },
                    enabled = selectedTargetMergeId != null
                ) {
                    Text("Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDismissMerge()
                    onSelectedTargetMergeIdChange(null)
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Single Photo Action Dialog (Set Cover / Remove Face)
    val currentSelectedMediaForAction = selectedMediaForAction
    val currentSelectedPersonId = selectedPersonId
    if (currentSelectedMediaForAction != null && currentSelectedPersonId != null) {
        val activeMedia = currentSelectedMediaForAction
        val activePersonId = currentSelectedPersonId

        AlertDialog(
            onDismissRequest = { onDismissMediaAction() },
            title = { Text("Photo options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val pId = activePersonId
                            val mUri = activeMedia.uri
                            onDismissMediaAction()
                            reloadScope.launch(Dispatchers.IO) {
                                store.setPersonCover(pId, mUri)
                                onReload()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Use as representative cover")
                    }
                    TextButton(
                        onClick = {
                            val pId = activePersonId
                            val mUri = activeMedia.uri
                            val engId = selectedEngineId
                            onDismissMediaAction()
                            reloadScope.launch(Dispatchers.IO) {
                                val faces = store.facesForMedia(mUri, engineId = engId)
                                faces.filter { it.personId == pId }.forEach { face ->
                                    store.removeFaceFromPerson(face.mediaUri, face.faceIndex, engineId = engId)
                                }
                                onReload()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remove face from this person")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onDismissMediaAction() }) { Text("Close") }
            }
        )
    }
}
