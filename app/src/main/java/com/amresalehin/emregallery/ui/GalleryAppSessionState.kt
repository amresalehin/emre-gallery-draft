package com.amresalehin.emregallery.ui

import androidx.activity.BackEventCompat
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.LockedVaultManager
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.MediaRepository
import com.amresalehin.emregallery.data.PreferencesRepository
import com.amresalehin.emregallery.data.StoryGroup


data class NavBackStackEntry(
    val destination: Destination,
    val selectedMedia: MediaItem? = null,
    val viewerMediaList: List<MediaItem> = emptyList(),
    val viewerStoryTitle: String? = null,
    val viewerStories: List<StoryGroup> = emptyList(),
    val currentStoryIndex: Int = 0,
    val mapFocusItem: MediaItem? = null,
    val albumFilter: String? = null,
    val albumFilterReturnDestination: Destination = Destination.ALBUMS,
    val inboxInitialViewMode: DownloaderViewMode = DownloaderViewMode.ACTIVITY_PAGE,
    val inboxInitialStack: com.amresalehin.emregallery.data.MediaInboxStack? = null,
    val inboxInitialOpenBrowser: Boolean = false
)

val LocalViewerCoordinatedProgress = compositionLocalOf<() -> Float> { { 0f } }

internal class GalleryAppSessionState(
    val context: android.content.Context,
    val prefs: PreferencesRepository,
    val mediaRepository: MediaRepository,
    val lockedVaultManager: LockedVaultManager
) {
    lateinit var scope: kotlinx.coroutines.CoroutineScope
    lateinit var homeListState: androidx.compose.foundation.lazy.LazyListState
    lateinit var permanentDeleteLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
    lateinit var restoreLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
    lateinit var trashLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>

    var destination by mutableStateOf(Destination.HOME)
    var previousDestination by mutableStateOf(Destination.HOME)
    var albumFilterReturnDestination by mutableStateOf(Destination.ALBUMS)
    var rawMedia by mutableStateOf<List<MediaItem>>(emptyList())
    var galleryData by mutableStateOf(ProcessedGalleryData())
    var media by mutableStateOf<List<MediaItem>>(emptyList())
    var lockedVaultItems by mutableStateOf<List<MediaItem>>(emptyList())
    var loading by mutableStateOf(hasMediaPermission(context))
    var selectedMedia by mutableStateOf<MediaItem?>(null)
    var viewerMediaList by mutableStateOf<List<MediaItem>>(emptyList())
    var viewerStoryTitle by mutableStateOf<String?>(null)
    var viewerStories by mutableStateOf<List<StoryGroup>>(emptyList())
    var currentStoryIndex by mutableIntStateOf(0)
    var mapFocusItem by mutableStateOf<MediaItem?>(null)
    val backStack = mutableStateListOf<NavBackStackEntry>()
    val viewerTransitionProgress = Animatable(0f)
    var showViewerControls by mutableStateOf(false)
    var showDetails by mutableStateOf(false)
    var searchStage by mutableStateOf(SearchStage.DISCOVERY)
    var searchQuery by mutableStateOf("")
    var submittedQuery by mutableStateOf("")
    var searchVideosOnly by mutableStateOf(false)
    var albumFilter by mutableStateOf<String?>(null)

    var favourites: Set<String> = emptySet()
    var hidden: Set<String> = emptySet()
    var inBin: Set<String> = emptySet()
    var archive: Set<String> = emptySet()
    var recentSearches: List<String> = emptyList()
    var themePref: String = "system"
    var customAlbums: List<CustomAlbum> = emptyList()
    var sortOrderPref: String = "date_desc"
    var groupByPref: String = "day"
    var gridColumnsPref: Int = 3
    var gridStylePref: String = "masonry"
    var ocrLanguagesPref: Set<String> = setOf("eng")
    var mlKitLanguagesPref: Set<String> = setOf("latin")
    var ocrModelPref: String = "fast"
    var ocrEnginePref: String = "tesseract"
    var customTagsMap: Map<String, Set<String>> = emptyMap()
    var customPeopleMap: Map<String, Set<String>> = emptyMap()
    var lockedFolderPinHash: String? = null
    var hasCompletedWelcome: Boolean = false

    var lastHomeButtonTapAt by mutableLongStateOf(0L)
    var showHomeViewOptionsSheet by mutableStateOf(false)
    var showSidePanel by mutableStateOf(false)
    var isLockedFolderUnlocked by mutableStateOf(false)
    var pendingPermanentDeleteUris by mutableStateOf<List<String>>(emptyList())
    var pendingRestoreUris by mutableStateOf<List<String>>(emptyList())
    var pendingTrashUris by mutableStateOf<List<String>>(emptyList())
    var homeSelectedUris by mutableStateOf<Set<String>>(emptySet())
    val snackbarHostState = SnackbarHostState()
    var showLockedFolderSetupPrompt by mutableStateOf(false)
    var activePersonId by mutableStateOf<String?>(null)
    var inboxInitialViewMode by mutableStateOf(DownloaderViewMode.ACTIVITY_PAGE)
    var inboxInitialStack by mutableStateOf<com.amresalehin.emregallery.data.MediaInboxStack?>(null)
    var inboxInitialOpenBrowser by mutableStateOf(false)
    var isPocketDetailOpen by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)

    var showCreateAlbumDialog by mutableStateOf(false)
    var createAlbumPresetParent by mutableStateOf("")
    var showAddToAlbumSheet by mutableStateOf(false)
    var mediaForAddToAlbum by mutableStateOf<MediaItem?>(null)
    var showAddPhotosPickerForAlbum by mutableStateOf<CustomAlbum?>(null)

    var isNavigatingBack by mutableStateOf(false)
    var isPredictiveBackActive by mutableStateOf(false)
    var predictiveBackProgress by mutableFloatStateOf(0f)
    var predictiveBackEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)

    var activeCustomAlbum: CustomAlbum? = null
    var activeAlbumTitle: String? = null
    var visibleMedia: List<MediaItem> = emptyList()
    var topContentPadding: Dp = 0.dp
    var bottomContentPadding: Dp = 0.dp
}
