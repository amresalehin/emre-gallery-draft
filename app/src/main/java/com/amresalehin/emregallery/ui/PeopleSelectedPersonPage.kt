package com.amresalehin.emregallery.ui
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.FaceClusterStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.amresalehin.emregallery.face.PersonSummary
@Composable
internal fun ColumnScope.PeopleSelectedPersonPage(
    selectedPerson: PersonSummary,
    selectedPersonId: String?,
    initialPersonId: String?,
    selectedEngineId: String,
    store: FaceClusterStore,
    visiblePeople: List<PersonSummary>,
    detailMedia: List<MediaItem>,
    personPhotoSearchQuery: String,
    onPersonPhotoSearchQueryChange: (String) -> Unit,
    showPersonOverflowMenu: Boolean,
    onShowPersonOverflowMenuChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onClearSelectedPerson: () -> Unit,
    onOpenRename: (String, String) -> Unit,
    onOpenMerge: (PersonSummary) -> Unit,
    onOpenAvatarPicker: () -> Unit,
    onOpenMedia: (MediaItem, List<MediaItem>) -> Unit,
    onSelectMediaForAction: (MediaItem) -> Unit,
    onReload: () -> Unit
) {
    val reloadScope = rememberCoroutineScope()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        if (initialPersonId != null && selectedPersonId == initialPersonId) {
                            onBack()
                        } else {
                            selectedPersonId = null
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back to people"
                    )
                }
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Search in ${if (selectedPerson?.isNamed == true) selectedPerson.displayName else "Person"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
                            text = { Text(if (selectedPerson?.isHidden == true) "Restore person" else "Hide person") },
                            leadingIcon = {
                                Icon(
                                    if (selectedPerson?.isHidden == true) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                personMenuExpanded = false
                                if (selectedPerson != null) {
                                    reloadScope.launch(Dispatchers.IO) {
                                        store.setPersonHidden(selectedPerson.id, !selectedPerson.isHidden)
                                        reload()
                                    }
                                    if (!selectedPerson.isHidden) selectedPersonId = null
                                }
                            }
                        )
                    }
                }
            }
            if (selectedPerson != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        PersonAvatar(person = selectedPerson, size = 90.dp)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { onOpenAvatarPicker() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Pick avatar",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (selectedPerson.isNamed) selectedPerson.displayName else "Add name",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPerson.isNamed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable {
                                    renamePersonId = selectedPerson.id
                                    renameValue = if (selectedPerson.isNamed) selectedPerson.displayName else ""
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    renamePersonId = selectedPerson.id
                                    renameValue = if (selectedPerson.isNamed) selectedPerson.displayName else ""
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit name",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "${java.text.NumberFormat.getInstance().format(detailMedia.size)} ${if (detailMedia.size == 1) "photo" else "photos"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (detailMedia.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No photos in this group",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    dayGroups.forEach { group ->
                        val isCollapsed = group.dayKey in collapsedDayKeys
                        item(key = "header_${group.dayKey}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isCollapsed) collapsedDayKeys.remove(group.dayKey)
                                        else collapsedDayKeys.add(group.dayKey)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = group.dayLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = {
                                        if (isCollapsed) collapsedDayKeys.remove(group.dayKey)
                                        else collapsedDayKeys.add(group.dayKey)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCollapsed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isCollapsed) "Expand group" else "Collapse group",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        if (!isCollapsed) {
                            val chunkedRows = group.items.chunked(3)
                            items(chunkedRows.size, key = { rowIndex -> "row_${group.dayKey}_$rowIndex" }) { rowIndex ->
                                val rowItems = chunkedRows[rowIndex]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 1.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    for (col in 0 until 3) {
                                        val item = rowItems.getOrNull(col)
                                        if (item != null) {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                                                val context = LocalContext.current
                                                AsyncImage(
                                                    model = remember(item.uri, context) {
                                                        ThumbnailUtils.buildThumbnailRequest(context, item.uri)
                                                    },
                                                    contentDescription = item.name,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clickable { onOpenMedia(item) },
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
}
