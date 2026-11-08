package com.amresalehin.emregallery.ui

import androidx.activity.compose.BackHandler
import com.amresalehin.emregallery.face.FaceEngineRegistry
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.face.FaceStoreEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.face.FaceProcessingCoordinator
import com.amresalehin.emregallery.face.PersonSummary
import com.amresalehin.emregallery.face.ReviewSuggestion
import java.util.Locale

internal data class PeopleScreenSnapshot(
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
    onOpenIndexStatus: () -> Unit,
    initialPersonId: String? = null
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

    var selectedPersonId by remember(initialPersonId) { mutableStateOf<String?>(initialPersonId) }
    var searchQuery by remember { mutableStateOf("") }
    var showAllPeople by remember { mutableStateOf(false) }
    var isSortAscending by remember { mutableStateOf(false) }
    var engineDropdownExpanded by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }

    val isProcessingActive by FaceProcessingCoordinator.isProcessingActive.collectAsStateWithLifecycle()
    val isBusy = isProcessingActive || FaceProcessingCoordinator.isBusy(context)

    fun setEngine(target: String) {
        if (FaceProcessingCoordinator.isBusy(context)) return
        selectedEngineId = target
        FaceRecognitionSettings.setActiveEngine(context, target)
        FaceEngineRegistry.setActiveEngine(target)
        FaceStoreEvents.notifyAllUpdated()
    }

    // Dialog states
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
                    people = store.personSummaries(includeHidden = false, engineId = engineId),
                    hiddenPeople = store.personSummaries(includeHidden = true, engineId = engineId).filter { it.isHidden },
                    suggestions = store.getPendingSuggestions(engineId = engineId),
                    mediaIndex = store.personMediaIndex(engineId, availableUris)
                )
            }
            if (request == reloadGeneration && engineId == selectedEngineId) {
                allPeople = snapshot.people
                hiddenPeople = snapshot.hiddenPeople
                pendingSuggestions = snapshot.suggestions
                personMediaIndex = snapshot.mediaIndex
            }
        }
    }

    LaunchedEffect(selectedEngineId) {
        if (initialPersonId == null) {
            selectedPersonId = null
        }
    }

    LaunchedEffect(selectedEngineId, photoByUri) {
        reload()
        kotlinx.coroutines.coroutineScope {
            launch { FaceStoreEvents.peopleUpdates.collect { reload() } }
            launch { FaceStoreEvents.mediaUpdates.collect { reload() } }
        }
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
        selectedPersonId?.let { id ->
            allPeople.firstOrNull { it.id == id } ?: visiblePeople.firstOrNull { it.id == id }
                ?: run {
                    val pName = store.getPersonName(id)
                    val sampleUri = store.facesForCluster(id, selectedEngineId).firstOrNull()?.mediaUri
                    PersonSummary(
                        id = id,
                        displayName = pName,
                        isNamed = pName.isNotBlank() && !pName.equals("Unknown", ignoreCase = true),
                        faceCount = 0,
                        sampleUri = sampleUri,
                        engineId = selectedEngineId
                    )
                }
        }
    }

    val matchingPeople = remember(visiblePeople, searchQuery) {
        if (searchQuery.isBlank()) visiblePeople
        else visiblePeople.filter { it.displayName.contains(searchQuery.trim(), ignoreCase = true) }
    }

    val sortedMatchingPeople = remember(matchingPeople, isSortAscending) {
        if (isSortAscending) {
            matchingPeople.sortedWith(compareBy<PersonSummary> { !it.isNamed }
                .thenBy { it.displayName.lowercase(Locale.getDefault()) })
        } else {
            matchingPeople
        }
    }

    val detailMedia = remember(selectedPersonId, photoByUri, personMediaIndex, selectedEngineId, reloadGeneration) {
        selectedPersonId?.let { id ->
            val urisFromIndex = personMediaIndex[id]?.mediaUris.orEmpty()
            val uris = if (urisFromIndex.isNotEmpty()) {
                urisFromIndex
            } else {
                store.facesForCluster(id, selectedEngineId).map { it.mediaUri }.toSet()
            }
            uris.mapNotNull { photoByUri[it] }
                .distinctBy { it.uri }
                .sortedByDescending { it.dateTakenMillis.takeIf { d -> d > 0 } ?: it.dateAddedSeconds * 1000L }
        }.orEmpty()
    }

    val dayGroups = remember(detailMedia) {
        groupMediaByDay(detailMedia)
    }

    val collapsedDayKeys = remember(selectedPersonId) { mutableStateListOf<Long>() }

    BackHandler(enabled = selectedPersonId != null) {
        if (initialPersonId != null && selectedPersonId == initialPersonId) {
            onBack()
        } else {
            selectedPersonId = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (selectedPersonId == null) {
            PeopleOverviewGridSection(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                sortByPhotosCount = sortByPhotosCount,
                onToggleSort = { sortByPhotosCount = !sortByPhotosCount },
                selectedEngineId = selectedEngineId,
                engineDropdownExpanded = engineDropdownExpanded,
                onEngineDropdownExpandedChange = { engineDropdownExpanded = it },
                onSelectEngine = { setEngine(it) },
                sortedMatchingPeople = sortedMatchingPeople,
                onSelectPerson = { selectedPersonId = it },
                onOpenIndexStatus = onOpenIndexStatus,
                onBack = onBack
            )
        } else {
            PeopleSelectedPersonPage(
                selectedPerson = selectedPerson,
                selectedPersonId = selectedPersonId,
                initialPersonId = initialPersonId,
                selectedEngineId = selectedEngineId,
                store = store,
                visiblePeople = visiblePeople,
                detailMedia = detailMedia,
                personPhotoSearchQuery = personPhotoSearchQuery,
                onPersonPhotoSearchQueryChange = { personPhotoSearchQuery = it },
                showPersonOverflowMenu = showPersonOverflowMenu,
                onShowPersonOverflowMenuChange = { showPersonOverflowMenu = it },
                onBack = onBack,
                onClearSelectedPerson = {
                    selectedPersonId = null
                    personPhotoSearchQuery = ""
                },
                onOpenRename = { id, name ->
                    renamePersonId = id
                    renameValue = name
                },
                onOpenMerge = { person ->
                    personToMerge = person
                    selectedTargetMergeId = null
                },
                onOpenAvatarPicker = { showAvatarPicker = true },
                onOpenMedia = onOpenMedia,
                onSelectMediaForAction = { selectedMediaForAction = it },
                onReload = { reload() }
            )
        }
    }

    PeopleDialogsAndSheets(
        store = store,
        selectedEngineId = selectedEngineId,
        selectedPerson = selectedPerson,
        selectedPersonId = selectedPersonId,
        onSelectedPersonIdChange = { selectedPersonId = it },
        visiblePeople = visiblePeople,
        detailMedia = detailMedia,
        showAvatarPicker = showAvatarPicker,
        onDismissAvatarPicker = { showAvatarPicker = false },
        renamePersonId = renamePersonId,
        onDismissRename = { renamePersonId = null },
        renameValue = renameValue,
        onRenameValueChange = { renameValue = it },
        personToMerge = personToMerge,
        onDismissMerge = { personToMerge = null },
        selectedTargetMergeId = selectedTargetMergeId,
        onSelectedTargetMergeIdChange = { selectedTargetMergeId = it },
        selectedMediaForAction = selectedMediaForAction,
        onDismissMediaAction = { selectedMediaForAction = null },
        onReload = { reload() }
    )
}
