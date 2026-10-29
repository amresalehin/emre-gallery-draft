package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.PersonSummary
import java.util.Locale

private val SearchInk @Composable get() = MaterialTheme.colorScheme.onSurface
private val SearchMuted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    stage: SearchStage,
    results: List<MediaItem>,
    recentSearches: List<String>,
    customAlbums: List<CustomAlbum>,
    customTagsMap: Map<String, Set<String>>,
    customPeopleMap: Map<String, Set<String>>,
    namedPeople: List<PersonSummary>,
    onQuery: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenAlbum: (CustomAlbum) -> Unit,
    videosOnly: Boolean,
    onVideosOnly: (Boolean) -> Unit
) {
    val returnProgress = LocalViewerCoordinatedProgress.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp).destinationTopBarEntrance(returnProgress),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = SearchInk) }
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search photos, people, places…", maxLines = 1) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SearchMuted) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear query", tint = SearchInk) }
                        }
                        IconButton(onClick = { onVideosOnly(!videosOnly) }) {
                            Icon(Icons.Default.FilterList, "Toggle video filter", tint = SearchInk)
                        }
                    }
                },
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    cursorColor = SearchInk,
                    focusedLeadingIconColor = SearchMuted,
                    unfocusedLeadingIconColor = SearchMuted,
                    focusedTrailingIconColor = SearchInk,
                    unfocusedTrailingIconColor = SearchInk
                )
            )
        }

        when (stage) {
            SearchStage.DISCOVERY -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (namedPeople.isNotEmpty()) {
                    item { Text("People", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(namedPeople.take(6), key = { "discovery-person-${it.id}" }) { person ->
                        PersonSuggestionRow(person = person, onClick = { onOpenPerson(person.id) })
                    }
                }
                if (customAlbums.isNotEmpty()) {
                    item { Text("Albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(customAlbums.take(6), key = { "discovery-album-${it.id}" }) { album ->
                        AlbumSuggestionRow(album = album, results = results, onClick = { onOpenAlbum(album) })
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearRecent, colors = ButtonDefaults.textButtonColors(contentColor = SearchInk)) {
                            Text("Clear all")
                        }
                    }
                    if (recentSearches.isEmpty()) {
                        Text("Your recent searches will appear here.", color = SearchMuted)
                    } else {
                        recentSearches.forEach { recent ->
                            ListItem(
                                headlineContent = { Text(recent, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Default.History, null, tint = SearchMuted) },
                                trailingContent = { Icon(Icons.Default.NorthWest, null, tint = SearchMuted) },
                                modifier = Modifier.clickable { onRecent(recent) },
                                colors = searchListItemColors()
                            )
                        }
                    }
                }
            }

            SearchStage.TYPING -> {
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !videosOnly,
                        onClick = { onVideosOnly(false) },
                        label = { Text("Photos & videos") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            selectedLabelColor = SearchInk,
                            selectedLeadingIconColor = SearchInk
                        )
                    )
                    FilterChip(
                        selected = videosOnly,
                        onClick = { onVideosOnly(true) },
                        label = { Text("Videos") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            selectedLabelColor = SearchInk,
                            selectedLeadingIconColor = SearchInk
                        )
                    )
                }

                val queryKey = query.trim()
                val matchingPeople = remember(queryKey, namedPeople) {
                    if (queryKey.isBlank()) emptyList() else namedPeople
                        .filter { it.isNamed && it.displayName.contains(queryKey, ignoreCase = true) }
                        .take(2)
                }
                val matchingAlbums = remember(queryKey, customAlbums) {
                    if (queryKey.isBlank()) emptyList() else customAlbums
                        .filter { it.name.contains(queryKey, true) || it.fullPath.contains(queryKey, true) }
                        .take(2)
                }
                val matchingFiles = remember(queryKey, results) {
                    if (queryKey.isBlank()) emptyList() else results
                        .filter { it.name.contains(queryKey, true) || it.relativePath.contains(queryKey, true) }
                        .distinctBy { it.uri }
                        .take(2)
                }
                val matchingTags = remember(queryKey, customTagsMap) {
                    if (queryKey.isBlank()) emptyList() else customTagsMap.values.flatten()
                        .distinct()
                        .filter { it.contains(queryKey, true) }
                        .sortedBy { it.lowercase(Locale.ROOT) }
                        .take(2)
                }
                val matchingPlaces = remember(queryKey, results) {
                    if (queryKey.isBlank()) emptyList() else results
                        .mapNotNull { item ->
                            item.relativePath.split('/', '\\\\').dropLast(1).lastOrNull()?.trim()
                                ?.takeIf { it.isNotBlank() && it.contains(queryKey, true) }
                        }
                        .distinctBy { it.lowercase(Locale.ROOT) }
                        .take(2)
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    if (matchingPeople.isNotEmpty()) {
                        item { SuggestionSectionHeader("People", onSeeAll = { onSubmit(queryKey) }) }
                        items(matchingPeople, key = { "person-${it.id}" }) { person ->
                            PersonSuggestionRow(person = person, onClick = { onOpenPerson(person.id) })
                        }
                    }
                    if (matchingPlaces.isNotEmpty()) {
                        item { SuggestionSectionHeader("Places", onSeeAll = { onSubmit(queryKey) }) }
                        items(matchingPlaces, key = { "place-$it" }) { place ->
                            SimpleSuggestionRow(
                                title = place,
                                subtitle = "Location folder",
                                icon = Icons.Default.Place,
                                onClick = { onSubmit(place) }
                            )
                        }
                    }
                    if (matchingAlbums.isNotEmpty()) {
                        item { SuggestionSectionHeader("Albums", onSeeAll = { onSubmit(queryKey) }) }
                        items(matchingAlbums, key = { "album-${it.id}" }) { album ->
                            AlbumSuggestionRow(album = album, results = results, onClick = { onOpenAlbum(album) })
                        }
                    }
                    if (matchingFiles.isNotEmpty()) {
                        item { SuggestionSectionHeader("Files", onSeeAll = { onSubmit(queryKey) }) }
                        items(matchingFiles, key = { "file-${it.uri}" }) { item ->
                            MediaSuggestionRow(item = item, onClick = { onOpen(item) })
                        }
                    }
                    if (matchingTags.isNotEmpty()) {
                        item { SuggestionSectionHeader("Tags", onSeeAll = { onSubmit(queryKey) }) }
                        items(matchingTags, key = { "tag-$it" }) { tag ->
                            SimpleSuggestionRow(
                                title = tag,
                                subtitle = "${customTagsMap.values.count { tag in it }} tagged items",
                                icon = Icons.Default.LocalOffer,
                                onClick = { onSubmit(tag) }
                            )
                        }
                    }
                    if (matchingPeople.isEmpty() && matchingPlaces.isEmpty() && matchingAlbums.isEmpty() &&
                        matchingFiles.isEmpty() && matchingTags.isEmpty()
                    ) {
                        item {
                            Text(
                                "No matching suggestions",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                color = SearchMuted
                            )
                        }
                    }
                    item {
                        TextButton(
                            onClick = { onSubmit(queryKey) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = SearchInk)
                        ) { Text("Search for ‘$queryKey’") }
                    }
                }
            }

            SearchStage.RESULTS -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(query.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = SearchInk)
                        Text("${results.size} items", color = SearchMuted)
                    }
                    IconButton(onClick = { onVideosOnly(!videosOnly) }) {
                        Icon(Icons.Default.FilterList, "Filter results", tint = SearchInk)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    gridItems(results) { item ->
                        val context = LocalContext.current
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(.82f).trackThumbnailBounds(item.uri)
                                .clip(RoundedCornerShape(5.dp)).clickable { onOpen(item) }
                        ) {
                            AsyncImage(
                                model = remember(item.uri, context) { ThumbnailUtils.buildThumbnailRequest(context, item.uri) },
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    alpha = if (ThumbnailBoundsTracker.activeViewerUri == item.uri) 0f else 1f
                                },
                                contentScale = ContentScale.Crop
                            )
                            if (item.isVideo) {
                                Icon(
                                    Icons.Default.PlayArrow, null,
                                    Modifier.align(Alignment.BottomEnd).padding(4.dp).graphicsLayer {
                                        alpha = if (ThumbnailBoundsTracker.activeViewerUri == item.uri) 0f else 1f
                                    },
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionSectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(
            onClick = onSeeAll,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        ) { Text("See all") }
    }
}

@Composable
private fun PersonSuggestionRow(person: PersonSummary, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = person.portraitUri ?: person.sampleUri,
                contentDescription = person.displayName,
                modifier = Modifier.size(52.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(person.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${person.faceCount} items", style = MaterialTheme.typography.bodySmall, color = SearchMuted)
            }
            Icon(Icons.Default.NorthWest, contentDescription = "Open person", tint = SearchMuted)
        }
    }
}

@Composable
private fun AlbumSuggestionRow(album: CustomAlbum, results: List<MediaItem>, onClick: () -> Unit) {
    val cover = remember(album, results) { results.firstOrNull { it.uri in album.mediaUris } }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (cover != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(cover.uri, context) { ThumbnailUtils.buildThumbnailRequest(context, cover.uri, 180, 180) },
                    contentDescription = album.name,
                    modifier = Modifier.size(76.dp, 72.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.size(76.dp, 72.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.PhotoAlbum, null, tint = SearchMuted) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(album.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${album.mediaUris.size} items", style = MaterialTheme.typography.bodySmall, color = SearchMuted)
            }
            Icon(Icons.Default.NorthWest, contentDescription = "Open album", tint = SearchMuted)
        }
    }
}

@Composable
private fun MediaSuggestionRow(item: MediaItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(Uri.parse(item.uri)).size(180, 180)
                    .crossfade(true).diskCachePolicy(CachePolicy.ENABLED).memoryCachePolicy(CachePolicy.ENABLED).build(),
                contentDescription = null,
                modifier = Modifier.size(76.dp, 72.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.relativePath, style = MaterialTheme.typography.bodySmall, color = SearchMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.NorthWest, contentDescription = null, tint = SearchMuted)
        }
    }
}

@Composable
private fun SimpleSuggestionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = SearchMuted) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SearchMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.NorthWest, contentDescription = null, tint = SearchMuted)
        }
    }
}

@Composable
private fun searchListItemColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    headlineColor = MaterialTheme.colorScheme.onSurface,
    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)
