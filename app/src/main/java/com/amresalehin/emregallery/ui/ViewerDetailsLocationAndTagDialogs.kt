package com.amresalehin.emregallery.ui

import android.location.Geocoder
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.PhotoMapLocationCache
import com.amresalehin.emregallery.face.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.Locale


@Composable
internal fun ViewerDetailsLocationTagPersonDialogs(
    item: MediaItem,
    detailsState: ViewerDetailsState,
    faceStore: FaceClusterStore,
    showAddLocationDialog: Boolean,
    onDismissAddLocation: () -> Unit,
    onOpenMap: () -> Unit,
    showAddTagDialog: Boolean,
    onDismissAddTag: () -> Unit,
    onAddTag: (String) -> Unit,
    renamingFace: DetectedMediaFace?,
    onDismissRenamingFace: () -> Unit,
    onIncrementFaceReloadKey: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Add Location Dialog
    if (showAddLocationDialog) {
        var locationInput by remember { mutableStateOf("") }
        var isGeocoding by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { onDismissAddLocation() },
            title = { Text("Add location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a place name, city, or coordinates:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = locationInput,
                        onValueChange = { locationInput = it },
                        label = { Text("Location name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = {
                            onDismissAddLocation()
                            onOpenMap()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Select on map")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = locationInput.trim()
                        if (input.isNotEmpty()) {
                            isGeocoding = true
                            coroutineScope.launch {
                                val geocoder = runCatching {
                                    @Suppress("DEPRECATION")
                                    Geocoder(context, Locale.getDefault()).getFromLocationName(input, 1)
                                }.getOrNull()?.firstOrNull()

                                val point = if (geocoder != null) {
                                    GeoPoint(geocoder.latitude, geocoder.longitude)
                                } else {
                                    val parts = input.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                                    if (parts.size >= 2) GeoPoint(parts[0], parts[1]) else null
                                }

                                withContext(Dispatchers.IO) {
                                    PhotoMapLocationCache(context).save(item.uri, point)
                                }

                                val updated = detailsState.metadata.toMutableMap()
                                updated["location"] = input
                                if (point != null) {
                                    updated["latitude"] = point.latitude.toString()
                                    updated["longitude"] = point.longitude.toString()
                                }
                                detailsState.metadata = updated
                                isGeocoding = false
                                onDismissAddLocation()
                            }
                        }
                    },
                    enabled = !isGeocoding && locationInput.isNotBlank()
                ) {
                    if (isGeocoding) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissAddLocation() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Tag Dialog
    if (showAddTagDialog) {
        var tagInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { onDismissAddTag() },
            title = { Text("Add tag") },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tagInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddTag(trimmed)
                            onDismissAddTag()
                        }
                    },
                    enabled = tagInput.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissAddTag() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename / Manage Person Dialog
    val currentRenamingFace = renamingFace
    if (currentRenamingFace != null) {
        val face = currentRenamingFace
        val personId = face.personId.orEmpty()
        var nameInput by remember(face) {
            val initial = if (face.displayName.isBlank() || face.displayName.equals("Unknown", ignoreCase = true)) "" else face.displayName
            mutableStateOf(initial)
        }
        AlertDialog(
            onDismissRequest = { onDismissRenamingFace() },
            title = {
                Text(if (face.displayName.isBlank() || face.displayName.equals("Unknown", ignoreCase = true)) "Name this person" else "Person details")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Person name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = {
                            faceStore.removeFaceFromPerson(face.mediaUri, face.faceIndex, engineId = face.engineId)
                            onIncrementFaceReloadKey()
                            onDismissRenamingFace()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove face from this person")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = nameInput.trim()
                        if (cleanName.isNotEmpty()) {
                            if (personId.isNotBlank()) faceStore.renamePerson(personId, cleanName)
                            onIncrementFaceReloadKey()
                            onDismissRenamingFace()
                        }
                    },
                    enabled = nameInput.isNotBlank() && personId.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissRenamingFace() }) {
                    Text("Cancel")
                }
            }
        )
    }
}
