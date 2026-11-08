package com.amresalehin.emregallery.ui

import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EnteFaceConstants
import androidx.compose.ui.draw.alpha
import com.amresalehin.emregallery.face.PersonSummary


@Composable
internal fun ColumnScope.PeopleOverviewGridSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortByPhotosCount: Boolean,
    onToggleSort: () -> Unit,
    selectedEngineId: String,
    engineDropdownExpanded: Boolean,
    onEngineDropdownExpandedChange: (Boolean) -> Unit,
    onSelectEngine: (String) -> Unit,
    sortedMatchingPeople: List<PersonSummary>,
    onSelectPerson: (String) -> Unit,
    onOpenIndexStatus: () -> Unit,
    onBack: () -> Unit
) {
            // Top App Bar / Search Bar (Matching Image 3)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back to albums"
                    )
                }

                // Search Bar pill
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
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { onSearchQueryChange(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "People and pets",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Sort toggle button
                IconButton(
                    onClick = { isSortAscending = !isSortAscending },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Sort people",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Model dropdown chip
                Box {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable(enabled = !isBusy) { onEngineDropdownExpandedChange(true) }
                            .alpha(if (!isBusy) 1.0f else 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (selectedEngineId == EnteFaceConstants.ENGINE_ID) "YOLO5Face" else "Buffalo-L",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select engine",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = engineDropdownExpanded && !isBusy,
                        onDismissRequest = { onEngineDropdownExpandedChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Buffalo-L") },
                            enabled = !isBusy,
                            trailingIcon = if (selectedEngineId == BuffaloLFaceEngine.ENGINE_ID) {
                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            onClick = {
                                onEngineDropdownExpandedChange(false)
                                onSelectEngine(BuffaloLFaceEngine.ENGINE_ID)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("YOLO5Face + MobileFaceNet") },
                            enabled = !isBusy,
                            trailingIcon = if (selectedEngineId == EnteFaceConstants.ENGINE_ID) {
                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            onClick = {
                                onEngineDropdownExpandedChange(false)
                                onSelectEngine(EnteFaceConstants.ENGINE_ID)
                            }
                        )
                    }
                }
            }

            // Main People Grid: Clean 3 columns, no photo counts, names shown only if configured
            if (sortedMatchingPeople.isEmpty()) {
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
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "No face groups found yet" else "No matching people",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "Scan photos in Index Status to identify people and group their faces." else "Try another name or clear your search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isBlank()) {
                            Button(onClick = onOpenIndexStatus) {
                                Text("Open Index Status")
                            }
                        } else {
                            TextButton(onClick = { onSearchQueryChange("") }) {
                                Text("Clear search")
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    gridItems(sortedMatchingPeople, key = { it.id }) { person ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPerson(person.id) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Circular face avatar
                            PersonAvatar(person = person, size = 96.dp)

                            // Under avatar: Show name ONLY if configured. If not, show nothing!
                            if (person.isNamed && person.displayName.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = person.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
}
