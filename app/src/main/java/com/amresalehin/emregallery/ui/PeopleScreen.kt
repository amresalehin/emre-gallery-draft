package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import java.util.Locale

@Composable
fun PeopleScreen(
    media: List<MediaItem>,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onOpenIndexStatus: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { FaceClusterStore(context.applicationContext) }
    val photoByUri = remember(media) { media.filterNot { it.isVideo }.associateBy { it.uri } }
    var clusters by remember { mutableStateOf(store.summaries()) }
    var selectedCluster by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var renameCluster by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }

    fun reload() {
        clusters = store.summaries()
    }

    val minimumGroupFaces = FaceRecognitionSettings.minGroupFaces(context)
    val visiblePeople = remember(clusters, photoByUri, minimumGroupFaces) {
        clusters.mapNotNull { cluster ->
            val visibleFaces = store.facesForCluster(cluster.id).filter { it.mediaUri in photoByUri }
            if (visibleFaces.size < minimumGroupFaces) {
                null
            } else {
                cluster.copy(
                    faceCount = visibleFaces.size,
                    sampleUri = visibleFaces.first().mediaUri
                )
            }
        }.sortedWith(compareByDescending<com.amresalehin.emregallery.face.FaceClusterSummary> { it.faceCount }
            .thenBy { it.displayName.lowercase(Locale.getDefault()) })
    }
    val selectedPerson = visiblePeople.firstOrNull { it.id == selectedCluster }
    val matchingPeople = remember(visiblePeople, searchQuery) {
        visiblePeople.filter { it.displayName.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val detailMedia = remember(selectedCluster, photoByUri, visiblePeople) {
        selectedCluster?.let { clusterId ->
            store.facesForCluster(clusterId)
                .mapNotNull { photoByUri[it.mediaUri] }
                .distinctBy { it.uri }
                .sortedByDescending { it.dateTakenMillis.takeIf { date -> date > 0 } ?: it.dateAddedSeconds * 1000L }
        }.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (selectedCluster != null) selectedCluster = null else onBack() }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = if (selectedCluster != null) "Back to people and pets" else "Back to albums"
                )
            }
            Text(
                text = selectedPerson?.displayName ?: if (selectedCluster != null) "Person" else "People & pets",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selectedCluster != null && selectedPerson != null) {
                IconButton(
                    onClick = {
                        renameCluster = selectedPerson.id
                        renameValue = selectedPerson.displayName
                    }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename person")
                }
            }
            IconButton(onClick = { reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh people and pets")
            }
        }

        if (selectedCluster == null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search people") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                }
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("People") },
                    icon = { Icon(Icons.Default.Face, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Pets") },
                    icon = { Icon(Icons.Default.Pets, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> {
                    if (matchingPeople.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Face,
                                    contentDescription = null,
                                    modifier = Modifier.size(46.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (searchQuery.isBlank()) "No people found yet" else "No matching people",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    if (searchQuery.isBlank()) {
                                        "Run Face Finding to group people from your photos."
                                    } else {
                                        "Try another name or clear your search."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (searchQuery.isBlank()) {
                                    Button(onClick = onOpenIndexStatus) {
                                        Text("Open Index Status")
                                    }
                                } else {
                                    TextButton(onClick = { searchQuery = "" }) {
                                        Text("Clear search")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 104.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp)
                        ) {
                            gridItems(matchingPeople, key = { it.id }) { person ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCluster = person.id },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (person.sampleUri != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(Uri.parse(person.sampleUri))
                                                .crossfade(false)
                                                .build(),
                                            contentDescription = person.displayName,
                                            modifier = Modifier.size(88.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(88.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Face,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(34.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        person.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Pets,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Pet groups aren’t available yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "This version can group people using face matching, but it doesn’t yet include an animal-recognition model to find and group pets.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            if (detailMedia.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No photos in this group",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    gridItems(detailMedia, key = { it.uri }) { item ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(Uri.parse(item.uri))
                                .crossfade(false)
                                .build(),
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clickable { onOpenMedia(item) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }

    if (renameCluster != null) {
        AlertDialog(
            onDismissRequest = { renameCluster = null },
            title = { Text("Rename person") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.renameCluster(renameCluster!!, renameValue.trim())
                    renameCluster = null
                    reload()
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameCluster = null }) { Text("Cancel") }
            }
        )
    }
}
