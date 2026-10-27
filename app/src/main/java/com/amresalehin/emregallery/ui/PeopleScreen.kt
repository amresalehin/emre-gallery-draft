package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.face.PersonSummary
import com.amresalehin.emregallery.face.ReviewSuggestion
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

    var selectedEngineId by remember { mutableStateOf(BuffaloLFaceEngine.ENGINE_ID) }
    var allPeople by remember { mutableStateOf(store.personSummaries(includeHidden = false, engineId = selectedEngineId)) }
    var hiddenPeople by remember { mutableStateOf(store.personSummaries(includeHidden = true, engineId = selectedEngineId).filter { it.isHidden }) }
    var pendingSuggestions by remember { mutableStateOf(store.getPendingSuggestions(engineId = selectedEngineId)) }

    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: People, 1: Review & Hidden, 2: Pets
    var searchQuery by remember { mutableStateOf("") }

    // Dialog states
    var renamePersonId by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var personToMerge by remember { mutableStateOf<PersonSummary?>(null) }
    var selectedTargetMergeId by remember { mutableStateOf<String?>(null) }
    var personMenuExpanded by remember { mutableStateOf(false) }
    var selectedMediaForAction by remember { mutableStateOf<MediaItem?>(null) }

    fun reload() {
        allPeople = store.personSummaries(includeHidden = false, engineId = selectedEngineId)
        hiddenPeople = store.personSummaries(includeHidden = true, engineId = selectedEngineId).filter { it.isHidden }
        pendingSuggestions = store.getPendingSuggestions(engineId = selectedEngineId)
    }

    LaunchedEffect(selectedEngineId) {
        selectedPersonId = null
        reload()
    }

    val minimumGroupFaces = FaceRecognitionSettings.minGroupFaces(context)
    val visiblePeople = remember(allPeople, photoByUri, minimumGroupFaces, selectedEngineId) {
        allPeople.mapNotNull { person ->
            val visibleFaces = store.facesForCluster(person.id, engineId = selectedEngineId).filter { it.mediaUri in photoByUri }
            if (visibleFaces.size < minimumGroupFaces && !person.isNamed) {
                null
            } else {
                person.copy(
                    faceCount = visibleFaces.size,
                    sampleUri = person.sampleUri ?: visibleFaces.firstOrNull()?.mediaUri
                )
            }
        }.sortedWith(compareByDescending<PersonSummary> { it.faceCount }
            .thenBy { it.displayName.lowercase(Locale.getDefault()) })
    }

    val selectedPerson = remember(visiblePeople, allPeople, selectedPersonId) {
        selectedPersonId?.let { id ->
            allPeople.firstOrNull { it.id == id } ?: visiblePeople.firstOrNull { it.id == id }
        }
    }

    val matchingPeople = remember(visiblePeople, searchQuery) {
        if (searchQuery.isBlank()) visiblePeople
        else visiblePeople.filter { it.displayName.contains(searchQuery.trim(), ignoreCase = true) }
    }

    val detailMedia = remember(selectedPersonId, photoByUri, selectedEngineId) {
        selectedPersonId?.let { id ->
            store.facesForCluster(id, engineId = selectedEngineId)
                .mapNotNull { photoByUri[it.mediaUri] }
                .distinctBy { it.uri }
                .sortedByDescending { it.dateTakenMillis.takeIf { d -> d > 0 } ?: it.dateAddedSeconds * 1000L }
        }.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (selectedPersonId != null) selectedPersonId = null else onBack()
                }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = if (selectedPersonId != null) "Back to people" else "Back to albums"
                )
            }

            val titleText = when {
                selectedPerson != null -> if (selectedPerson.isNamed) selectedPerson.displayName else "Unnamed Person"
                selectedPersonId != null -> "Person"
                else -> "People & pets"
            }

            Text(
                text = titleText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (selectedPersonId != null && selectedPerson != null) {
                // Rename icon
                IconButton(
                    onClick = {
                        renamePersonId = selectedPerson.id
                        renameValue = selectedPerson.displayName
                    }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename person")
                }

                // More Options Menu (Merge, Hide, etc.)
                Box {
                    IconButton(onClick = { personMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Person options")
                    }
                    DropdownMenu(
                        expanded = personMenuExpanded,
                        onDismissRequest = { personMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Merge with someone") },
                            leadingIcon = { Icon(Icons.Default.MergeType, contentDescription = null) },
                            onClick = {
                                personMenuExpanded = false
                                personToMerge = selectedPerson
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (selectedPerson.isHidden) "Restore person" else "Hide person") },
                            leadingIcon = {
                                Icon(
                                    if (selectedPerson.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                personMenuExpanded = false
                                store.setPersonHidden(selectedPerson.id, !selectedPerson.isHidden)
                                reload()
                                if (!selectedPerson.isHidden) selectedPersonId = null
                            }
                        )
                    }
                }
            }

            IconButton(onClick = { reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh people and pets")
            }
        }

        if (selectedPersonId == null) {
            // Search Bar
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

            // Engine Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedEngineId == BuffaloLFaceEngine.ENGINE_ID,
                    onClick = { selectedEngineId = BuffaloLFaceEngine.ENGINE_ID },
                    label = { Text("Buffalo_L (Default)") },
                    leadingIcon = if (selectedEngineId == BuffaloLFaceEngine.ENGINE_ID) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = selectedEngineId == EnteFaceConstants.ENGINE_ID,
                    onClick = { selectedEngineId = EnteFaceConstants.ENGINE_ID },
                    label = { Text("Ente (YOLO5Face)") },
                    leadingIcon = if (selectedEngineId == EnteFaceConstants.ENGINE_ID) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            // Tabs: People, Review & Hidden, Pets
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("People (${visiblePeople.size})") },
                    icon = { Icon(Icons.Default.Face, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        val reviewBadge = pendingSuggestions.size + hiddenPeople.size
                        Text(if (reviewBadge > 0) "Review ($reviewBadge)" else "Review")
                    },
                    icon = { Icon(Icons.Default.Rule, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Pets") },
                    icon = { Icon(Icons.Default.Pets, contentDescription = null) }
                )
            }

            when (selectedTab) {
                // Tab 0: Main People Grid
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
                                    if (searchQuery.isBlank()) "No face groups found yet" else "No matching people",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    if (searchQuery.isBlank()) {
                                        "Scan photos in Index Status to identify people and group their faces."
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
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            gridItems(matchingPeople, key = { it.id }) { person ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPersonId = person.id },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        if (person.sampleUri != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(Uri.parse(person.sampleUri))
                                                    .crossfade(false)
                                                    .build(),
                                                contentDescription = if (person.isNamed) person.displayName else "Person",
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
                                                        modifier = Modifier.size(34.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        // Subtle compact pencil action for instant naming/renaming
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clickable {
                                                    renamePersonId = person.id
                                                    renameValue = person.displayName
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit name",
                                                    modifier = Modifier.size(13.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    // Display name (never "Unknown")
                                    val label = if (person.isNamed) person.displayName else "Add name"
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (person.isNamed) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (person.isNamed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${person.faceCount} ${if (person.faceCount == 1) "photo" else "photos"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Tab 1: Review Suggestions & Hidden People
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Ambiguous Match Suggestions
                        if (pendingSuggestions.isNotEmpty()) {
                            Text(
                                "Suggested Matches",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            pendingSuggestions.forEach { suggestion ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(Uri.parse(suggestion.mediaUri))
                                                .crossfade(false)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier.size(52.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "Is this ${suggestion.suggestedPersonName}?",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Similarity match: ${(1f - suggestion.distance / 2f).coerceIn(0f, 1f) * 100}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                store.acceptSuggestion(suggestion.id)
                                                reload()
                                            }
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Accept match", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(
                                            onClick = {
                                                store.rejectSuggestion(suggestion.id)
                                                reload()
                                            }
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Reject match", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        // Hidden / Ignored People
                        if (hiddenPeople.isNotEmpty()) {
                            Text(
                                "Hidden People",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            hiddenPeople.forEach { hidden ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (hidden.sampleUri != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(Uri.parse(hidden.sampleUri))
                                                    .crossfade(false)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp).clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(48.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                if (hidden.isNamed) hidden.displayName else "Unnamed Person",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                "${hidden.faceCount} photos",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                store.setPersonHidden(hidden.id, false)
                                                reload()
                                            }
                                        ) {
                                            Text("Restore")
                                        }
                                    }
                                }
                            }
                        }

                        if (pendingSuggestions.isEmpty() && hiddenPeople.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                                    Text("No suggestions or hidden people", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Tab 2: Pets
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
                                "Emre Gallery groups people using on-device face recognition. Pet grouping will be added in a future update.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            // Selected Person Details View
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
                        Box {
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
                            // Menu button for photo-level action
                            IconButton(
                                onClick = { selectedMediaForAction = item },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.MoreHoriz, contentDescription = "Photo options", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Person Dialog
    if (renamePersonId != null) {
        val targetId = renamePersonId!!
        AlertDialog(
            onDismissRequest = { renamePersonId = null },
            title = { Text(if (renameValue.isBlank()) "Name this person" else "Rename person") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = renameValue.trim()
                        if (cleanName.isNotBlank()) {
                            store.renamePerson(targetId, cleanName)
                        }
                        renamePersonId = null
                        reload()
                    },
                    enabled = renameValue.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamePersonId = null }) { Text("Cancel") }
            }
        )
    }

    // Merge People Dialog
    if (personToMerge != null) {
        val sourcePerson = personToMerge!!
        val mergeCandidates = visiblePeople.filter { it.id != sourcePerson.id }

        AlertDialog(
            onDismissRequest = { personToMerge = null },
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
                                .clickable { selectedTargetMergeId = candidate.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedTargetMergeId == candidate.id,
                                    onClick = { selectedTargetMergeId = candidate.id }
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
                        if (selectedTargetMergeId != null) {
                            store.mergePeople(sourcePerson.id, selectedTargetMergeId!!)
                            selectedPersonId = selectedTargetMergeId
                            personToMerge = null
                            selectedTargetMergeId = null
                            reload()
                        }
                    },
                    enabled = selectedTargetMergeId != null
                ) {
                    Text("Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    personToMerge = null
                    selectedTargetMergeId = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Single Photo Action Dialog (Set Cover / Remove Face)
    if (selectedMediaForAction != null && selectedPersonId != null) {
        val activeMedia = selectedMediaForAction!!
        val activePersonId = selectedPersonId!!

        AlertDialog(
            onDismissRequest = { selectedMediaForAction = null },
            title = { Text("Photo options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            store.setPersonCover(activePersonId, activeMedia.uri)
                            selectedMediaForAction = null
                            reload()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Use as representative cover")
                    }
                    TextButton(
                        onClick = {
                            val faces = store.facesForMedia(activeMedia.uri, engineId = selectedEngineId)
                            faces.filter { it.personId == activePersonId }.forEach { face ->
                                store.removeFaceFromPerson(face.mediaUri, face.faceIndex, engineId = selectedEngineId)
                            }
                            selectedMediaForAction = null
                            reload()
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
                TextButton(onClick = { selectedMediaForAction = null }) { Text("Close") }
            }
        )
    }
}
