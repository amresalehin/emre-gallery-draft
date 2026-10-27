package com.amresalehin.emregallery.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import com.amresalehin.emregallery.face.FaceEngineRegistry
import com.amresalehin.emregallery.face.FaceThumbnailHelper
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
import com.amresalehin.emregallery.face.FaceStoreEvents
import com.amresalehin.emregallery.face.acceptSuggestionAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.amresalehin.emregallery.face.PersonSummary
import com.amresalehin.emregallery.face.ReviewSuggestion
import java.util.Locale

private data class PeopleScreenSnapshot(
    val people: List<PersonSummary>,
    val hiddenPeople: List<PersonSummary>,
    val suggestions: List<ReviewSuggestion>,
    val mediaIndex: Map<String, com.amresalehin.emregallery.face.PersonMediaIndex>
)

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

    var selectedEngineId by remember { mutableStateOf(FaceRecognitionSettings.getActiveEngine(context)) }
    var allPeople by remember { mutableStateOf(emptyList<PersonSummary>()) }
    var hiddenPeople by remember { mutableStateOf(emptyList<PersonSummary>()) }
    var pendingSuggestions by remember { mutableStateOf(emptyList<ReviewSuggestion>()) }
    var personMediaIndex by remember { mutableStateOf<Map<String, com.amresalehin.emregallery.face.PersonMediaIndex>>(emptyMap()) }
    val reloadScope = rememberCoroutineScope()
    var reloadGeneration by remember { mutableIntStateOf(0) }

    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showAllPeople by remember { mutableStateOf(false) }

    fun setEngine(target: String) {
        selectedEngineId = target
        FaceRecognitionSettings.setActiveEngine(context, target)
        FaceEngineRegistry.setActiveEngine(target)
    }

    var renamePersonId by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var personToMerge by remember { mutableStateOf<PersonSummary?>(null) }
    var selectedTargetMergeId by remember { mutableStateOf<String?>(null) }
    var personMenuExpanded by remember { mutableStateOf(false) }
    var selectedMediaForAction by remember { mutableStateOf<MediaItem?>(null) }

    fun reload() {
        reloadGeneration += 1
        val request = reloadGeneration
        val engineId = selectedEngineId
        val availableUris = photoByUri.keys.toList()
        reloadScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                PeopleScreenSnapshot(
                    people=store.personSummaries(includeHidden=false,engineId=engineId),
                    hiddenPeople=store.personSummaries(includeHidden=true,engineId=engineId).filter { it.isHidden },
                    suggestions=store.getPendingSuggestions(engineId=engineId),
                    mediaIndex=store.personMediaIndex(engineId,availableUris)
                )
            }
            if(request==reloadGeneration && engineId==selectedEngineId) {
                allPeople=snapshot.people
                hiddenPeople=snapshot.hiddenPeople
                pendingSuggestions=snapshot.suggestions
                personMediaIndex=snapshot.mediaIndex
            }
        }
    }

    LaunchedEffect(selectedEngineId) { selectedPersonId = null }
    LaunchedEffect(selectedEngineId, photoByUri) {
        reload()
        FaceStoreEvents.peopleUpdates.collect { reload() }
    }

    val minimumGroupFaces = FaceRecognitionSettings.minGroupFaces(context, selectedEngineId)
    val visiblePeople = remember(allPeople, personMediaIndex, photoByUri, minimumGroupFaces, selectedEngineId, showAllPeople) {
        allPeople.mapNotNull { person ->
            val index = personMediaIndex[person.id]
            val count = index?.faceCount ?: 0
            if (!showAllPeople && count < minimumGroupFaces && !person.isNamed) null
            else person.copy(
                faceCount = count,
                sampleUri = person.sampleUri?.takeIf { it in photoByUri } ?: index?.mediaUris?.firstOrNull()
            )
        }.sortedWith(compareByDescending<PersonSummary> { it.faceCount }
            .thenBy { it.displayName.lowercase(Locale.getDefault()) })
    }

    val selectedPerson = remember(visiblePeople, allPeople, selectedPersonId) {
        selectedPersonId?.let { id -> allPeople.firstOrNull { it.id == id } ?: visiblePeople.firstOrNull { it.id == id } }
    }
    val matchingPeople = remember(visiblePeople, searchQuery) {
        if (searchQuery.isBlank()) visiblePeople
        else visiblePeople.filter { it.displayName.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val detailMedia = remember(selectedPersonId, photoByUri, personMediaIndex, selectedEngineId) {
        selectedPersonId?.let { id ->
            personMediaIndex[id]?.mediaUris.orEmpty().mapNotNull { photoByUri[it] }
                .distinctBy { it.uri }
                .sortedByDescending { it.dateTakenMillis.takeIf { d -> d > 0 } ?: it.dateAddedSeconds * 1000L }
        }.orEmpty()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selectedPersonId != null) selectedPersonId = null else onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = if (selectedPersonId != null) "Back to people" else "Back to albums")
            }
            val titleText = when {
                selectedPerson != null -> if (selectedPerson.isNamed) selectedPerson.displayName else "Unnamed Person"
                selectedPersonId != null -> "Person"
                else -> "People & pets"
            }
            Text(titleText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selectedPersonId != null && selectedPerson != null) {
                IconButton(onClick = { renamePersonId = selectedPerson.id; renameValue = selectedPerson.displayName }) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename person")
                }
                Box {
                    IconButton(onClick = { personMenuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Person options") }
                    DropdownMenu(expanded = personMenuExpanded, onDismissRequest = { personMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Merge with someone") }, leadingIcon = { Icon(Icons.Default.MergeType, contentDescription = null) }, onClick = {
                            personMenuExpanded = false; personToMerge = selectedPerson
                        })
                        DropdownMenuItem(
                            text = { Text(if (selectedPerson.isHidden) "Restore person" else "Hide person") },
                            leadingIcon = { Icon(if (selectedPerson.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null) },
                            onClick = {
                                personMenuExpanded = false
                                reloadScope.launch(Dispatchers.IO) { store.setPersonHidden(selectedPerson.id, !selectedPerson.isHidden); reload() }
                                if (!selectedPerson.isHidden) selectedPersonId = null
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh people and pets") }
        }

        if (selectedPersonId == null) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true, shape = RoundedCornerShape(28.dp), placeholder = { Text("Search people") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear search") } }
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedEngineId == BuffaloLFaceEngine.ENGINE_ID, onClick = { setEngine(BuffaloLFaceEngine.ENGINE_ID) }, label = { Text("Buffalo_L (Default)") },
                    leadingIcon = if (selectedEngineId == BuffaloLFaceEngine.ENGINE_ID) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null)
                FilterChip(selected = selectedEngineId == EnteFaceConstants.ENGINE_ID, onClick = { setEngine(EnteFaceConstants.ENGINE_ID) }, label = { Text("Ente (YOLO5Face)") },
                    leadingIcon = if (selectedEngineId == EnteFaceConstants.ENGINE_ID) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null)
            }
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("People (${visiblePeople.size})") }, icon = { Icon(Icons.Default.Face, contentDescription = null) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = {
                    val reviewBadge = pendingSuggestions.size + hiddenPeople.size
                    Text(if (reviewBadge > 0) "Review ($reviewBadge)" else "Review")
                }, icon = { Icon(Icons.Default.Rule, contentDescription = null) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Pets") }, icon = { Icon(Icons.Default.Pets, contentDescription = null) })
            }

            when (selectedTab) {
                0 -> {
                    if (matchingPeople.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                val hasHiddenGroups = allPeople.isNotEmpty() && !showAllPeople && searchQuery.isBlank()
                                Text(if (hasHiddenGroups) "${allPeople.size} unnamed people hidden" else if (searchQuery.isBlank()) "No face groups found yet" else "No matching people",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                                Text(if (hasHiddenGroups) "Groups with fewer than $minimumGroupFaces photos are filtered by default." else if (searchQuery.isBlank()) "Scan photos in Index Status to identify people and group their faces." else "Try another name or clear your search.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                if (hasHiddenGroups) Button(onClick = { showAllPeople = true }) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Show all ${allPeople.size} people")
                                } else if (searchQuery.isBlank()) Button(onClick = onOpenIndexStatus) { Text("Open Index Status") }
                                else TextButton(onClick = { searchQuery = "" }) { Text("Clear search") }
                            }
                        }
                    } else {
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 104.dp), modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            gridItems(matchingPeople, key = { it.id }) { person ->
                                Column(modifier = Modifier.fillMaxWidth().clickable { selectedPersonId = person.id }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        PersonAvatar(person = person, size = 88.dp)
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            modifier = Modifier.size(26.dp).clickable { renamePersonId = person.id; renameValue = person.displayName }) {
                                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, contentDescription = "Edit name", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurface) }
                                        }
                                    }
                                    val label = if (person.isNamed) person.displayName else "Add name"
                                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (person.isNamed) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (person.isNamed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${person.faceCount} ${if (person.faceCount == 1) "photo" else "photos"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (pendingSuggestions.isNotEmpty()) {
                            Text("Suggested Matches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            pendingSuggestions.forEach { suggestion ->
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(Uri.parse(suggestion.mediaUri)).crossfade(false).build(),
                                            contentDescription = null, modifier = Modifier.size(52.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Is this ${suggestion.suggestedPersonName}?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text("Similarity match: ${(1f - suggestion.distance / 2f).coerceIn(0f, 1f) * 100}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { reloadScope.launch(Dispatchers.IO) { store.acceptSuggestionAtomically(suggestion.id); reload() } }) {
                                            Icon(Icons.Default.Check, contentDescription = "Accept match", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { reloadScope.launch(Dispatchers.IO) { store.rejectSuggestion(suggestion.id); reload() } }) {
                                            Icon(Icons.Default.Close, contentDescription = "Reject match", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        if (hiddenPeople.isNotEmpty()) {
                            Text("Hidden People", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            hiddenPeople.forEach { hidden ->
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        PersonAvatar(person = hidden, size = 48.dp); Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(if (hidden.isNamed) hidden.displayName else "Unnamed Person", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text("${hidden.faceCount} photos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        TextButton(onClick = { reloadScope.launch(Dispatchers.IO) { store.setPersonHidden(hidden.id, false); reload() } }) { Text("Restore") }
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
                else -> {
                    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Pets, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Pet groups aren’t available yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                            Text("Emre Gallery groups people using on-device face recognition. Pet grouping will be added in a future update.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        } else {
            if (detailMedia.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No photos in this group", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            } else LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                gridItems(detailMedia, key = { it.uri }) { item ->
                    Box {
                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(Uri.parse(item.uri)).crossfade(false).build(),
                            contentDescription = item.name, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onOpenMedia(item) }, contentScale = ContentScale.Crop)
                        IconButton(onClick = { selectedMediaForAction = item }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "Photo options", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (renamePersonId != null) {
        val targetId = renamePersonId!!
        AlertDialog(onDismissRequest = { renamePersonId = null }, title = { Text(if (renameValue.isBlank()) "Name this person" else "Rename person") },
            text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = {
                val cleanName = renameValue.trim(); val target = targetId; renamePersonId = null
                if (cleanName.isNotBlank()) reloadScope.launch(Dispatchers.IO) { store.renamePerson(target, cleanName); reload() }
            }, enabled = renameValue.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renamePersonId = null }) { Text("Cancel") } })
    }

    if (personToMerge != null) {
        val sourcePerson = personToMerge!!
        val mergeCandidates = visiblePeople.filter { it.id != sourcePerson.id }
        AlertDialog(onDismissRequest = { personToMerge = null }, title = { Text("Merge with someone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select the person to combine with ${if (sourcePerson.isNamed) sourcePerson.displayName else "this person"}:", style = MaterialTheme.typography.bodySmall)
                    mergeCandidates.forEach { candidate ->
                        Surface(shape = RoundedCornerShape(12.dp), color = if (selectedTargetMergeId == candidate.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth().clickable { selectedTargetMergeId = candidate.id }) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedTargetMergeId == candidate.id, onClick = { selectedTargetMergeId = candidate.id }); Spacer(Modifier.width(8.dp))
                                Text(if (candidate.isNamed) candidate.displayName else "Unnamed (${candidate.faceCount} photos)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = {
                val targetMerge = selectedTargetMergeId
                if (targetMerge != null) {
                    val srcId = sourcePerson.id; selectedPersonId = targetMerge; personToMerge = null; selectedTargetMergeId = null
                    reloadScope.launch(Dispatchers.IO) { store.mergePeople(srcId, targetMerge); reload() }
                }
            }, enabled = selectedTargetMergeId != null) { Text("Merge") } },
            dismissButton = { TextButton(onClick = { personToMerge = null; selectedTargetMergeId = null }) { Text("Cancel") } })
    }

    if (selectedMediaForAction != null && selectedPersonId != null) {
        val activeMedia = selectedMediaForAction!!
        val activePersonId = selectedPersonId!!
        AlertDialog(onDismissRequest = { selectedMediaForAction = null }, title = { Text("Photo options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val pId = activePersonId; val mUri = activeMedia.uri; selectedMediaForAction = null
                        reloadScope.launch(Dispatchers.IO) { store.setPersonCover(pId, mUri); reload() }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Use as representative cover")
                    }
                    TextButton(onClick = {
                        val pId = activePersonId; val mUri = activeMedia.uri; val engId = selectedEngineId; selectedMediaForAction = null
                        reloadScope.launch(Dispatchers.IO) {
                            store.facesForMedia(mUri, engineId = engId).filter { it.personId == pId }.forEach { store.removeFaceFromPerson(it.mediaUri, it.faceIndex, engineId = engId) }
                            reload()
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Remove face from this person")
                    }
                }
            }, confirmButton = {}, dismissButton = { TextButton(onClick = { selectedMediaForAction = null }) { Text("Close") } })
    }
}

@Composable
private fun PersonAvatar(person: PersonSummary, size: androidx.compose.ui.unit.Dp = 88.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var faceBitmap by remember(person.id, person.portraitUri, person.portraitFaceIndex, person.sampleUri, person.engineId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(person.id, person.portraitUri, person.portraitFaceIndex, person.sampleUri, person.engineId) {
        val targetUri = person.portraitUri ?: person.sampleUri
        if (targetUri != null) {
            faceBitmap = withContext(Dispatchers.IO) {
                val parsed = runCatching { Uri.parse(targetUri) }.getOrNull() ?: return@withContext null
                FaceThumbnailHelper.getFaceCrop(context, parsed, person.portraitFaceIndex ?: 0, person.engineId)
            }
        }
    }
    if (faceBitmap != null) {
        androidx.compose.foundation.Image(bitmap = faceBitmap!!.asImageBitmap(), contentDescription = if (person.isNamed) person.displayName else "Person",
            modifier = modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else if (person.sampleUri != null) {
        AsyncImage(model = ImageRequest.Builder(context).data(Uri.parse(person.sampleUri)).crossfade(false).build(),
            contentDescription = if (person.isNamed) person.displayName else "Person", modifier = modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Surface(modifier = modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(size * 0.45f), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
