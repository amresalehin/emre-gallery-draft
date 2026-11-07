package com.amresalehin.emregallery.pocket

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketScreen(
    items: List<PocketItem>,
    onOpenItem: (PocketItem) -> Unit,
    onSaveUrl: (String) -> Unit,
    onBack: () -> Unit,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    availableMedia: List<MediaItem> = emptyList(),
    onAddMediaToPocket: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedDomainFilter by remember { mutableStateOf<String?>(null) }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showGalleryPickerSheet by remember { mutableStateOf(false) }

    val allDomains = remember(items) {
        items.map { it.domain }.distinct().sorted()
    }
    val allTags = remember(items) {
        items.flatMap { it.tags }.distinct().sorted()
    }

    // Filter items
    val filteredItems = remember(items, searchQuery, selectedDomainFilter, selectedTagFilter) {
        items.filter { item ->
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                item.title.contains(searchQuery, ignoreCase = true) ||
                        item.description.contains(searchQuery, ignoreCase = true) ||
                        item.note.contains(searchQuery, ignoreCase = true) ||
                        item.domain.contains(searchQuery, ignoreCase = true) ||
                        item.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
            val matchesDomain = selectedDomainFilter == null || item.domain.equals(selectedDomainFilter, ignoreCase = true)
            val matchesTag = selectedTagFilter == null || item.tags.any { it.equals(selectedTagFilter, ignoreCase = true) }
            matchesQuery && matchesDomain && matchesTag
        }
    }

    // Check if search query looks like a URL
    val isUrlQuery = remember(searchQuery) {
        val q = searchQuery.trim()
        q.contains(".") && !q.contains(" ") && (q.startsWith("http://") || q.startsWith("https://") || q.contains(".com") || q.contains(".org") || q.contains(".net") || q.contains(".io") || q.contains(".edu"))
    }

    val gridState = rememberLazyGridState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pocket_screen")
    ) {
        // Main 2-column Grid of Pocket cards
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topContentPadding + 64.dp, bottom = bottomContentPadding, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Bookmarks,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedDomainFilter != null || selectedTagFilter != null)
                            "No matching saved items"
                        else
                            "Your Pocket is empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotBlank())
                            "Try searching with different keywords or paste a link to save it."
                        else
                            "Paste web links or save bookmarks from Wikipedia, articles, and your gallery.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotBlank() || selectedDomainFilter != null || selectedTagFilter != null) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                searchQuery = ""
                                selectedDomainFilter = null
                                selectedTagFilter = null
                            }
                        ) {
                            Text("Clear filters")
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 20.dp,
                    top = topContentPadding + 68.dp,
                    bottom = bottomContentPadding + 72.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("pocket_grid")
            ) {
                items(
                    items = filteredItems,
                    key = { it.id }
                ) { item ->
                    PocketStackedCard(
                        item = item,
                        onClick = { onOpenItem(item) }
                    )
                }
            }
        }

        // Top Search Bar matching the design in image_84952544.png
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = topContentPadding, start = 16.dp, end = 16.dp)
                .zIndex(20f)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("pocket_search_pill")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("pocket_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 2.dp, end = 8.dp)
                            .size(20.dp)
                    )

                    // Text Input
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search library or paste URL",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = if (isUrlQuery) ImeAction.Done else ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (isUrlQuery) {
                                    onSaveUrl(searchQuery.trim())
                                    searchQuery = ""
                                    focusManager.clearFocus()
                                }
                            },
                            onSearch = { focusManager.clearFocus() }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pocket_search_input")
                    )

                    // Quick Paste URL Action if typed URL
                    if (isUrlQuery) {
                        FilledTonalButton(
                            onClick = {
                                onSaveUrl(searchQuery.trim())
                                searchQuery = ""
                                focusManager.clearFocus()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Save", style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // Trailing Filter Button (tune/filter icon from image)
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("pocket_filter_button")
                    ) {
                        val isFiltered = selectedDomainFilter != null || selectedTagFilter != null
                        Box {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filter items",
                                tint = if (isFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                            if (isFiltered) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button to add links or gallery items
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomContentPadding + 12.dp)
                .testTag("pocket_add_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add to Pocket")
        }

        // Add Dialog (URL paste or import from Gallery)
        if (showAddDialog) {
            PocketAddDialog(
                onDismiss = { showAddDialog = false },
                onSaveUrl = { url ->
                    showAddDialog = false
                    onSaveUrl(url)
                },
                onPickFromGallery = {
                    showAddDialog = false
                    showGalleryPickerSheet = true
                }
            )
        }

        // Gallery Picker Bottom Sheet
        if (showGalleryPickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showGalleryPickerSheet = false },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Save photo to Pocket",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Select a photo from your gallery to save as a pocket bookmark.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        items(availableMedia.take(30), key = { it.uri }) { mediaItem ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showGalleryPickerSheet = false
                                        onAddMediaToPocket(mediaItem)
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(mediaItem.uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = mediaItem.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Filter Modal Bottom Sheet
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filter Pocket",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedDomainFilter != null || selectedTagFilter != null) {
                            TextButton(onClick = {
                                selectedDomainFilter = null
                                selectedTagFilter = null
                            }) {
                                Text("Reset")
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    Text("By Domain", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedDomainFilter == null,
                            onClick = { selectedDomainFilter = null },
                            label = { Text("All Domains") }
                        )
                        allDomains.take(3).forEach { dom ->
                            FilterChip(
                                selected = selectedDomainFilter == dom,
                                onClick = { selectedDomainFilter = if (selectedDomainFilter == dom) null else dom },
                                label = { Text(dom) }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("By Tag", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTagFilter == null,
                            onClick = { selectedTagFilter = null },
                            label = { Text("All Tags") }
                        )
                        allTags.take(4).forEach { tag ->
                            FilterChip(
                                selected = selectedTagFilter == tag,
                                onClick = { selectedTagFilter = if (selectedTagFilter == tag) null else tag },
                                label = { Text(tag) }
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { showFilterSheet = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apply Filters (${filteredItems.size} items)")
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Stacked Deck Card Component replicating the layered card design from image_84952544.png.
 * Displays offset colored layers behind the main white card.
 */
@Composable
fun PocketStackedCard(
    item: PocketItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = item.accentColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("pocket_card_${item.title.lowercase().replace(" ", "_")}")
    ) {
        // Backmost Layer 2: Offset (8.dp, 8.dp)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(accent.copy(alpha = 0.28f))
        )

        // Middle Layer 1: Offset (4.dp, 4.dp)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(accent.copy(alpha = 0.55f))
        )

        // Front Main Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 5.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Image section with top rounded corners
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(138.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    accent.copy(alpha = 0.25f),
                                    accent.copy(alpha = 0.12f)
                                )
                            )
                        )
                ) {
                    if (!item.primaryImage.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.primaryImage)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Elegant decorative placeholder
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmarks,
                                contentDescription = null,
                                tint = accent.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                // Text section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Title
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(3.dp))

                    // Date & Time
                    Text(
                        text = item.formattedDateTime,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(7.dp))

                    // Favicon symbol + vertical separator + domain name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = item.faviconSymbol,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        Text(
                            text = "|",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Spacer(Modifier.width(6.dp))

                        Text(
                            text = item.domain,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PocketAddDialog(
    onDismiss: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onPickFromGallery: () -> Unit
) {
    val context = LocalContext.current
    var inputUrl by remember { mutableStateOf("") }

    // Auto-check clipboard for URLs
    LaunchedEffect(Unit) {
        runCatching {
            val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipManager?.primaryClip?.getItemAt(0)
            val text = item?.text?.toString()?.trim()
            if (!text.isNullOrBlank() && (text.startsWith("http://") || text.startsWith("https://"))) {
                inputUrl = text
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Pocket") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Save an article, web link, or photo to your Pocket library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("Paste Web URL") },
                    placeholder = { Text("https://en.wikipedia.org/wiki/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onPickFromGallery,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Select Photo from Gallery")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inputUrl.isNotBlank()) {
                        onSaveUrl(inputUrl.trim())
                    }
                },
                enabled = inputUrl.isNotBlank()
            ) {
                Text("Save Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
