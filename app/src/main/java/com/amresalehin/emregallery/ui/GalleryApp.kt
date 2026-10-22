package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.MediaRepository
import com.amresalehin.emregallery.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private enum class Destination { HOME, ALBUMS, EXPLORE, SETTINGS, SEARCH, VIEWER }
private enum class SearchStage { DISCOVERY, TYPING, RESULTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesRepository(context.applicationContext) }
    val mediaRepository = remember { MediaRepository(context.contentResolver) }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var previousDestination by remember { mutableStateOf(Destination.HOME) }
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var showViewerControls by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var searchStage by remember { mutableStateOf(SearchStage.DISCOVERY) }
    var searchQuery by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var searchVideosOnly by remember { mutableStateOf(false) }
    var albumFilter by remember { mutableStateOf<String?>(null) }
    val favourites by prefs.favourites.collectAsStateWithLifecycle(initialValue = emptySet())
    val hidden by prefs.hidden.collectAsStateWithLifecycle(initialValue = emptySet())
    val inBin by prefs.bin.collectAsStateWithLifecycle(initialValue = emptySet())
    val recentSearches by prefs.recentSearches.collectAsStateWithLifecycle(initialValue = emptyList())
    val themePref by prefs.theme.collectAsStateWithLifecycle(initialValue = "system")

    fun refreshMedia() {
        if (!hasMediaPermission(context)) return
        loading = true
        scope.launch {
            media = withContext(Dispatchers.IO) { runCatching { mediaRepository.loadMedia() }.getOrDefault(emptyList()) }
            loading = false
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (hasMediaPermission(context)) refreshMedia() }
    LaunchedEffect(Unit) { if (hasMediaPermission(context)) refreshMedia() }

    val visibleMedia = remember(media, hidden, inBin, favourites, albumFilter, searchVideosOnly, submittedQuery, searchStage) {
        media.filter { item ->
            item.uri !in hidden && item.uri !in inBin && (!searchVideosOnly || item.isVideo) &&
                (albumFilter == null || when (albumFilter) {
                    "Favourites" -> item.uri in favourites
                    "Videos" -> item.isVideo
                    "Recently added" -> item.dateAddedSeconds * 1000L > System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                    else -> item.relativePath.contains(albumFilter.orEmpty(), true)
                }) && (searchStage != SearchStage.RESULTS || item.name.contains(submittedQuery, true) || item.relativePath.contains(submittedQuery, true))
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        if (destination in listOf(Destination.HOME, Destination.ALBUMS, Destination.EXPLORE, Destination.SETTINGS)) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
                listOf(Destination.HOME, Destination.ALBUMS, Destination.EXPLORE, Destination.SETTINGS).forEach { dest ->
                    val (label, icon) = when (dest) {
                        Destination.HOME -> "Home" to Icons.Default.Home
                        Destination.ALBUMS -> "Albums" to Icons.Default.PhotoLibrary
                        Destination.EXPLORE -> "Explore" to Icons.Default.Explore
                        else -> "Settings" to Icons.Default.Settings
                    }
                    NavigationBarItem(selected = destination == dest, onClick = { destination = dest; albumFilter = null }, icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(if (destination == Destination.VIEWER) PaddingValues(0.dp) else padding)) {
            when (destination) {
                Destination.HOME -> HomeScreen(media = visibleMedia, loading = loading, onSearch = { previousDestination = destination; destination = Destination.SEARCH; searchStage = SearchStage.DISCOVERY; searchQuery = "" }, onOpen = { selectedMedia = it; showViewerControls = false; showDetails = false; previousDestination = Destination.HOME; destination = Destination.VIEWER })
                Destination.ALBUMS -> AlbumsScreen(media, favourites, onSearch = { previousDestination = destination; destination = Destination.SEARCH; searchStage = SearchStage.DISCOVERY; searchQuery = "" }, onFilter = { albumFilter = it; destination = Destination.HOME }, onOpen = { selectedMedia = it; previousDestination = Destination.ALBUMS; destination = Destination.VIEWER }, onCreate = { albumFilter = "New album" })
                Destination.EXPLORE -> ExploreScreen(media, onSearch = { previousDestination = destination; destination = Destination.SEARCH; searchStage = SearchStage.DISCOVERY; searchQuery = "" }, onFilter = { albumFilter = it; destination = Destination.HOME }, onOpen = { selectedMedia = it; previousDestination = Destination.EXPLORE; destination = Destination.VIEWER })
                Destination.SETTINGS -> SettingsScreen(total = media.size, theme = themePref, onTheme = { scope.launch { prefs.setTheme(it) } }, onRefresh = { refreshMedia() }, onSettings = {}, onPermissionSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
                Destination.SEARCH -> SearchScreen(query = searchQuery, stage = searchStage, results = media.filter { it.uri !in hidden && it.uri !in inBin && (!searchVideosOnly || it.isVideo) && (searchStage != SearchStage.RESULTS || it.name.contains(submittedQuery, true) || it.relativePath.contains(submittedQuery, true)) }, recentSearches = recentSearches, onQuery = { searchQuery = it; searchStage = if (it.isBlank()) SearchStage.DISCOVERY else SearchStage.TYPING }, onSubmit = { submittedQuery = it.trim(); searchQuery = it.trim(); searchStage = SearchStage.RESULTS; if (it.isNotBlank()) scope.launch { prefs.addRecentSearch(it.trim()) } }, onRecent = { searchQuery = it; submittedQuery = it; searchStage = SearchStage.RESULTS }, onClearRecent = { scope.launch { prefs.clearRecentSearches() } }, onBack = { destination = previousDestination }, onOpen = { selectedMedia = it; previousDestination = Destination.SEARCH; destination = Destination.VIEWER }, videosOnly = searchVideosOnly, onVideosOnly = { searchVideosOnly = it })
                Destination.VIEWER -> selectedMedia?.let { item -> ViewerScreen(item, media.filter { it.uri !in hidden && it.uri !in inBin }, item.uri in favourites, showViewerControls, showDetails, { showViewerControls = !showViewerControls }, { showDetails = !showDetails; showViewerControls = true }, { destination = previousDestination }, { scope.launch { prefs.toggleFavourite(item.uri) } }, { scope.launch { prefs.setHidden(item.uri, true) }; destination = Destination.HOME }, { scope.launch { prefs.setInBin(item.uri, true) }; destination = Destination.HOME }, { destination = Destination.ALBUMS }, { selectedMedia = it; showDetails = false }) }
            }
        }
    }
}

private fun hasMediaPermission(context: android.content.Context): Boolean {
    val checks = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    return checks.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } || (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
}

@Composable
private fun PermissionState(onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp)); Text("Your photos, in one place", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Allow access to photos and videos stored on this device to build your gallery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp)); Button(onClick = onRequest) { Text("Allow access") }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit, label: String = "Search photos, people, places…") {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f), modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, "Search", Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun HomeScreen(media: List<MediaItem>, loading: Boolean, onSearch: () -> Unit, onOpen: (MediaItem) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasMediaPermission(context) }
    LaunchedEffect(Unit) { granted = hasMediaPermission(context) }
    if (!granted) {
        PermissionState { launcher.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 8.dp)) { SearchPill(onSearch) }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (media.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No photos or videos found", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { MemoriesStrip(media, onOpen) }
            item { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp)); Text("${media.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item { MasonryMediaGrid(media.take(72), onOpen) }
            if (media.size > 72) item { Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text("Older", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp)); Text("${media.size - 72} items", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (media.size > 72) item { MasonryMediaGrid(media.drop(72).take(72), onOpen) }
        }
    }
}

@Composable
private fun MemoriesStrip(media: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    if (media.isEmpty()) return
    val stories = listOf("Recently added", "Camera roll", "Videos", "Your library")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(stories.size) { i ->
            val candidate = when (i) { 2 -> media.firstOrNull { it.isVideo }; 1 -> media.firstOrNull { it.relativePath.contains("Camera", true) }; else -> media.getOrNull(i) } ?: media.first()
            Box(Modifier.width(118.dp).height(174.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpen(candidate) }) {
                AsyncImage(Uri.parse(candidate.uri), candidate.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxWidth().height(74.dp).align(Alignment.BottomCenter).background(Color.Black.copy(alpha = .55f)))
                Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) { Text(stories[i], color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${media.size} items", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun MasonryMediaGrid(media: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    val columns = remember(media) { media.chunked(2) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        columns.forEachIndexed { index, pair -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            pair.forEach { item ->
                val aspect = if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height.toFloat() else if (index % 2 == 0) .82f else 1.15f
                Box(Modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(.55f, 1.75f)).clip(RoundedCornerShape(9.dp)).clickable { onOpen(item) }) {
                    AsyncImage(Uri.parse(item.uri), item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (item.isVideo) Icon(Icons.Default.PlayCircleFilled, "Video", Modifier.align(Alignment.BottomStart).padding(8.dp).size(24.dp), tint = Color.White)
                }
            }
        } }
    }
}

@Composable
private fun AlbumsScreen(media: List<MediaItem>, favourites: Set<String>, onSearch: () -> Unit, onFilter: (String) -> Unit, onOpen: (MediaItem) -> Unit, onCreate: () -> Unit) {
    val folders = media.groupBy { it.relativePath.substringBeforeLast('/', "Internal storage").ifBlank { "Internal storage" } }.entries.sortedByDescending { it.value.size }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp)); SearchPill(onSearch, "Search folders, albums…")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 16.dp)) {
            item { SectionHeading("Device folders", "See all") { onFilter("Camera") }; LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(folders.take(8)) { folder -> FolderCard(folder.key.substringAfterLast('/'), folder.value, { folder.value.firstOrNull()?.let(onOpen) }, { onFilter(folder.key.substringAfterLast('/')) }) } } }
            item { SectionHeading("My Vision", "See all") { onFilter("My Vision") }; LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { item { CreateCollectionCard(onCreate) }; listOf("Travel Dreams", "Family Moments", "Study Focus").forEachIndexed { i, name -> val candidate = media.getOrNull(i); if (candidate != null) FolderCard(name, listOf(candidate), { onFilter(name) }, { onFilter(name) }, false) } }; Text("AI collections are shortcuts until an actual classification engine is integrated.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { SectionHeading("Nested albums", "See all") { onFilter("Albums") }; LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { listOf("Personal", "Work", "Projects", "References").forEach { name -> FolderCard(name, folders.firstOrNull { it.key.contains(name, true) }?.value ?: emptyList(), { onFilter(name) }, { onFilter(name) }, false) } } }
            item { SectionHeading("Other collections", "See all") { onFilter("Favourites") }; Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("Favourites" to Icons.Default.Favorite, "Videos" to Icons.Default.PlayCircle, "Recently added" to Icons.Default.Schedule, "Hidden" to Icons.Default.Lock, "Bin" to Icons.Default.Delete).forEach { (label, icon) -> Surface(onClick = { onFilter(label) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.weight(1f).height(100.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(3.dp)) { Icon(icon, null, Modifier.size(24.dp)); Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(when (label) { "Favourites" -> favourites.size.toString(); "Videos" -> media.count { it.isVideo }.toString(); else -> "" }, style = MaterialTheme.typography.labelSmall) } } } } }
        }
    }
}

@Composable
private fun FolderCard(name: String, items: List<MediaItem>, onClick: () -> Unit, onMore: () -> Unit, tall: Boolean = true) {
    Column(Modifier.width(if (tall) 134.dp else 142.dp)) {
        Box(Modifier.fillMaxWidth().height(if (tall) 126.dp else 112.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
            items.firstOrNull()?.let { AsyncImage(Uri.parse(it.uri), name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            IconButton(onClick = onMore, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(32.dp).background(Color.Black.copy(alpha = .35f), CircleShape)) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
        }
        Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text("${items.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CreateCollectionCard(onClick: () -> Unit) {
    Column(Modifier.width(142.dp)) {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(112.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Create collection", Modifier.size(38.dp)) } }
        Text("Create collection", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp)); Text("Create a new album", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(action, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)); Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ExploreScreen(media: List<MediaItem>, onSearch: () -> Unit, onFilter: (String) -> Unit, onOpen: (MediaItem) -> Unit) {
    val folders = media.groupBy { it.relativePath.substringBeforeLast('/', "Internal storage").ifBlank { "Internal storage" } }.entries.sortedByDescending { it.value.size }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp)); SearchPill(onSearch)
        LazyColumn(contentPadding = PaddingValues(top = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item { SectionHeading("Memories", "See all") { onFilter("Recently added") }; LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(listOf("On this day", "Trips", "Best moments", "Recently added")) { title -> val item = media.firstOrNull(); if (item != null) Box(Modifier.width(146.dp).height(180.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpen(item) }) { AsyncImage(Uri.parse(item.uri), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Box(Modifier.fillMaxWidth().height(76.dp).align(Alignment.BottomCenter).background(Color.Black.copy(alpha = .56f))); Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text("${media.size} items", color = Color.White, style = MaterialTheme.typography.labelSmall) } } } } }
            item { SectionHeading("People & pets", "See all") { onFilter("People") }; Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Face, null, Modifier.size(32.dp)); Spacer(Modifier.width(12.dp)); Column { Text("People grouping not configured", fontWeight = FontWeight.Medium); Text("A face-clustering engine is required for real groups.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
            item { SectionHeading("Places", "See all") { onFilter("Places") }; LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(folders.take(8)) { folder -> val item = folder.value.firstOrNull(); if (item != null) Column(Modifier.width(124.dp).clickable { onFilter(folder.key.substringAfterLast('/')) }) { AsyncImage(Uri.parse(item.uri), folder.key, Modifier.fillMaxWidth().height(102.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop); Text(folder.key.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 5.dp)); Text("${folder.value.size} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }; Text("Real maps require GPS metadata and a map provider.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { SectionHeading("Things", "See all") { onFilter("Things") }; LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(listOf("Mountains", "Food", "Animals", "Flowers", "Documents", "Outdoor")) { topic -> val item = media.firstOrNull(); if (item != null) Column(Modifier.width(105.dp).clickable { onFilter(topic) }) { AsyncImage(Uri.parse(item.uri), topic, Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop); Text(topic, maxLines = 1, modifier = Modifier.padding(top = 5.dp)) } } }; Text("Topic cards are starter shortcuts, not AI classifications.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(query: String, stage: SearchStage, results: List<MediaItem>, recentSearches: List<String>, onQuery: (String) -> Unit, onSubmit: (String) -> Unit, onRecent: (String) -> Unit, onClearRecent: () -> Unit, onBack: () -> Unit, onOpen: (MediaItem) -> Unit, videosOnly: Boolean, onVideosOnly: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.weight(1f), placeholder = { Text("Search photos, people, places…", maxLines = 1) }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { Row(verticalAlignment = Alignment.CenterVertically) { if (query.isNotBlank()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear query") }; IconButton(onClick = { onVideosOnly(!videosOnly) }) { Icon(Icons.Default.FilterList, "Toggle video filter") } } }, shape = CircleShape)
        }
        when (stage) {
            SearchStage.DISCOVERY -> LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item { Text("People", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Person recognition will appear when configured.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Text("Places", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Search location metadata when available.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Text("Albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("Favourites", "Camera", "Videos", "Recently added")) { label -> AssistChip(onClick = { onQuery(label); onSubmit(label) }, label = { Text(label) }, leadingIcon = { Icon(Icons.Default.PhotoAlbum, null) }) } } }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Recent searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); TextButton(onClick = onClearRecent) { Text("Clear all") } }; if (recentSearches.isEmpty()) Text("Your recent searches will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) else recentSearches.forEach { item -> ListItem(headlineContent = { Text(item) }, leadingContent = { Icon(Icons.Default.History, null) }, modifier = Modifier.clickable { onRecent(item) }) } }
            }
            SearchStage.TYPING -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = !videosOnly, onClick = { onVideosOnly(false) }, label = { Text("Photos & videos") }); FilterChip(selected = videosOnly, onClick = { onVideosOnly(true) }, label = { Text("Videos") }) }
                LazyColumn {
                    val matches = results.filter { it.name.contains(query, true) || it.relativePath.contains(query, true) }.take(8)
                    items(matches) { item -> ListItem(headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(item.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingContent = { AsyncImage(Uri.parse(item.uri), null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }, trailingContent = { Icon(Icons.Default.NorthWest, null) }, modifier = Modifier.clickable { onOpen(item) }) }
                    item { TextButton(onClick = { onSubmit(query) }, modifier = Modifier.fillMaxWidth()) { Text("Search for ‘$query’") } }
                }
            }
            SearchStage.RESULTS -> {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(query.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${results.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = { onVideosOnly(!videosOnly) }) { Icon(Icons.Default.FilterList, "Filter results") } }
                LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { gridItems(results) { item -> Box(Modifier.fillMaxWidth().aspectRatio(.82f).clip(RoundedCornerShape(5.dp)).clickable { onOpen(item) }) { AsyncImage(Uri.parse(item.uri), item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); if (item.isVideo) Icon(Icons.Default.PlayArrow, null, Modifier.align(Alignment.BottomEnd).padding(4.dp), tint = Color.White) } } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScreen(item: MediaItem, items: List<MediaItem>, favourite: Boolean, showControls: Boolean, showDetails: Boolean, onToggleControls: () -> Unit, onToggleDetails: () -> Unit, onBack: () -> Unit, onFavourite: () -> Unit, onHide: () -> Unit, onBin: () -> Unit, onAddTo: () -> Unit, onOpenNeighbor: (MediaItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMore by remember { mutableStateOf(false) }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    val share = { runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = item.mimeType ?: if (item.isVideo) "video/*" else "image/*"; putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share media")) } }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(item.uri) { detectTapGestures(onTap = { onToggleControls() }, onLongPress = { showMore = true }) }.pointerInput(items, item.uri) {
        detectHorizontalDragGestures(onHorizontalDrag = { _, amount -> horizontalDrag += amount }, onDragEnd = { if (kotlin.math.abs(horizontalDrag) > 80f) { val index = items.indexOfFirst { it.uri == item.uri }; items.getOrNull(if (horizontalDrag < 0) index + 1 else index - 1)?.let(onOpenNeighbor) }; horizontalDrag = 0f })
    }) {
        if (item.isVideo) {
            androidx.compose.ui.viewinterop.AndroidView(factory = { vc -> androidx.media3.ui.PlayerView(vc).apply { player = androidx.media3.exoplayer.ExoPlayer.Builder(vc).build().also { p -> p.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(item.uri))); p.prepare(); p.playWhenReady = true }; useController = true } }, modifier = Modifier.fillMaxSize(), update = { it.useController = showControls })
        } else AsyncImage(Uri.parse(item.uri), item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        if (showControls && !showDetails) {
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { ViewerButton(Icons.Default.ArrowBack, "Back", onBack); Spacer(Modifier.weight(1f)); ViewerButton(if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favourite", onFavourite); ViewerButton(Icons.Default.Build, "Tools") { showMore = true }; ViewerButton(Icons.Default.MoreVert, "More") { showMore = true } }
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ViewerAction(Icons.Default.Share, "Share", share)
                ViewerAction(Icons.Default.Tune, "Edit") { runCatching { context.startActivity(Intent(Intent.ACTION_EDIT).setDataAndType(Uri.parse(item.uri), item.mimeType ?: "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } }
                ViewerAction(Icons.Default.Add, "Add to", onAddTo)
                ViewerAction(Icons.Default.Delete, "Delete", onBin)
                ViewerAction(Icons.Default.MoreHoriz, "More") { showMore = true }
            }
            Surface(Modifier.align(Alignment.BottomCenter).offset(y = (-80).dp).clickable { onToggleDetails() }, color = Color.Black.copy(alpha = .45f), contentColor = Color.White, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) { Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.KeyboardArrowUp, null); Text("Swipe up for details") } }
        }
        if (showDetails) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .24f)).clickable { onToggleDetails() })
            Surface(Modifier.fillMaxWidth().fillMaxHeight(.78f).align(Alignment.BottomCenter), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 9.dp), contentAlignment = Alignment.Center) { Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f))) }
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(formatDate(item.dateTakenMillis, item.dateAddedSeconds), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; TextButton(onClick = { showDetails = false }) { Icon(Icons.Default.Edit, null); Text("Edit") } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { DetailAction(Icons.Default.Share, "Share", share); DetailAction(Icons.Default.Add, "Add to", onAddTo); DetailAction(Icons.Default.Folder, "Move to", onAddTo); DetailAction(Icons.Default.Delete, "Delete", onBin); DetailAction(Icons.Default.MoreHoriz, "More") { showMore = true } }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { MetadataRow(Icons.Default.LocationOn, "Location", "No location metadata available") }
                        item { MetadataRow(Icons.Default.AutoAwesome, "Description", "AI caption not configured") }
                        item { MetadataRow(Icons.Default.TextFields, "Text recognition", "OCR not configured") }
                        item { MetadataRow(Icons.Default.LocalOffer, "Tags", "No tags added") }
                        item { MetadataRow(Icons.Default.People, "People", "No people assigned") }
                        item { MetadataRow(Icons.Default.PhotoAlbum, "Albums", "No album membership info") }
                        item { MetadataRow(Icons.Default.CameraAlt, "Format", item.mimeType ?: "Unknown format") }
                        item { MetadataRow(Icons.Default.AspectRatio, "Dimensions", if (item.width > 0) "${item.width} × ${item.height}" else "Not available") }
                        item { MetadataRow(Icons.Default.Storage, "File size", formatBytes(item.sizeBytes)) }
                        if (item.isVideo) item { MetadataRow(Icons.Default.Videocam, "Duration", formatDuration(item.durationMillis)) }
                        item { MetadataRow(Icons.Default.Folder, "Folder", item.relativePath.ifBlank { "Unknown folder" }) }
                    }
                }
            }
        }
    }
    if (showMore) ModalBottomSheet(onDismissRequest = { showMore = false }) {
        ListItem(headlineContent = { Text("Hide from gallery") }, leadingContent = { Icon(Icons.Default.VisibilityOff, null) }, modifier = Modifier.clickable { showMore = false; onHide() })
        ListItem(headlineContent = { Text("Move to Bin") }, leadingContent = { Icon(Icons.Default.Delete, null) }, modifier = Modifier.clickable { showMore = false; onBin() })
        ListItem(headlineContent = { Text("Media information") }, leadingContent = { Icon(Icons.Default.Info, null) }, modifier = Modifier.clickable { showMore = false; if (!showDetails) onToggleDetails() })
        ListItem(headlineContent = { Text("Cancel") }, leadingContent = { Icon(Icons.Default.Close, null) }, modifier = Modifier.clickable { showMore = false })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ViewerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) { IconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 3.dp).background(Color.White.copy(alpha = .92f), CircleShape)) { Icon(icon, label, tint = Color(0xFF172235)) } }
@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp)) { Surface(shape = CircleShape, color = Color.White.copy(alpha = .9f), modifier = Modifier.size(46.dp)) { Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = Color(0xFF172235)) } }; Spacer(Modifier.height(4.dp)); Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall) } }
@Composable
private fun DetailAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 6.dp)) { Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null) } }; Text(label, style = MaterialTheme.typography.labelSmall) } }
@Composable
private fun MetadataRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) { Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.Medium); Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@Composable
private fun SettingsScreen(total: Int, theme: String, onTheme: (String) -> Unit, onRefresh: () -> Unit, onSettings: (String) -> Unit, onPermissionSettings: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val entries = listOf("Library overview" to Icons.Default.PhotoLibrary, "Theme" to Icons.Default.Palette, "Tools" to Icons.Default.Build, "Intelligence" to Icons.Default.Psychology, "OCR" to Icons.Default.DocumentScanner, "Indexing Status" to Icons.Default.Storage, "Duplicates or Similar" to Icons.Default.Collections, "Permissions" to Icons.Default.Lock, "About" to Icons.Default.Info).filter { it.first.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp), placeholder = { Text("Search settings…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = CircleShape, singleLine = true)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(entries) { (label, icon) ->
            if (label == "Theme") ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(30.dp)); Spacer(Modifier.width(14.dp)); Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }; Spacer(Modifier.height(12.dp)); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEachIndexed { index, (key, name) -> SegmentedButton(selected = theme == key, onClick = { onTheme(key) }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(name) } } } } }
            else ElevatedCard(onClick = { if (label == "Library overview" || label == "Indexing Status") onRefresh(); if (label == "Permissions") onPermissionSettings(); onSettings(label) }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(30.dp)); Spacer(Modifier.width(18.dp)); Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium); Text(when (label) { "Library overview" -> "$total media items indexed"; "Intelligence" -> "AI providers not configured"; "OCR" -> "Engine not configured"; "Indexing Status" -> "Local MediaStore scan"; "Duplicates or Similar" -> "Detection engine not configured"; "About" -> "Emre Gallery · 0.1.0"; "Tools" -> "Local library tools"; else -> "Manage access" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
        } }
    }
}

private fun formatDate(takenMillis: Long, addedSeconds: Long): String { val millis = takenMillis.takeIf { it > 0 } ?: addedSeconds * 1000; if (millis <= 0) return "Date unavailable"; return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis)) }
private fun formatBytes(bytes: Long): String = when { bytes <= 0 -> "Not available"; bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0); bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024)); else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024)) }
private fun formatDuration(ms: Long): String { val seconds = ms / 1000; return "%d:%02d".format(seconds / 60, seconds % 60) }
