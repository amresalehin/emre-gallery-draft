package com.amresalehin.emregallery.pocket

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketDetailScreen(
    item: PocketItem,
    onBack: () -> Unit,
    onDelete: (PocketItem) -> Unit,
    onUpdate: (PocketItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showReaderModeDialog by remember { mutableStateOf(false) }
    var showEditNoteDialog by remember { mutableStateOf(false) }

    val images = remember(item) {
        (listOfNotNull(item.previewImageUri) + item.imageUrls).distinct()
    }
    val pagerState = rememberPagerState(pageCount = { if (images.isNotEmpty()) images.size else 1 })
    val scrollState = rememberScrollState()

    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            // Top action bar matching image_1ef4c7f4.png:
            // Back button on left, 3 circular icon buttons (Reader, Copy, Delete) on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(46.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxSize().testTag("pocket_detail_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Pocket",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Trailing 3 circular actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Reader Mode / Book Icon
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 4.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(46.dp)
                    ) {
                        IconButton(
                            onClick = { showReaderModeDialog = true },
                            modifier = Modifier.fillMaxSize().testTag("pocket_detail_reader")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "Reading mode",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 2. Copy / Share Icon
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 4.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(46.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Pocket URL", item.url.ifBlank { item.title })
                                clipManager?.setPrimaryClip(clip)
                                Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxSize().testTag("pocket_detail_copy")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy link",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 3. Delete / Trash Icon
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 4.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(46.dp)
                    ) {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.fillMaxSize().testTag("pocket_detail_delete")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete item",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("pocket_detail_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Main Hero Image Card with Stacked Layers matching image_1ef4c7f4.png
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(310.dp)
            ) {
                // Background Layer (offset on the right side)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = 8.dp, y = 8.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(colorScheme.surfaceContainerHigh)
                )

                // Middle Layer (offset 4.dp)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = 4.dp, y = 4.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(colorScheme.surfaceContainer)
                )

                // Front Hero Image Card
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (images.isNotEmpty()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val currentUrl = images[page]
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(currentUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // Illustrated placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(colorScheme.surfaceContainerHigh, colorScheme.surfaceVariant)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Landscape,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Carousel / Image indicator pill: [🖼 1 / 3]
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .height(30.dp)
                    .wrapContentWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${pagerState.currentPage + 1} / ${images.size.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Bottom Section / Sheet Container matching image_1ef4c7f4.png
            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Center drag handle pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                    )

                    Spacer(Modifier.height(18.dp))

                    // Row 1: Time
                    DetailMetadataCard(
                        icon = Icons.Default.CalendarToday,
                        label = "Time",
                        content = {
                            Text(
                                text = item.formattedDateTime,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )

                    Spacer(Modifier.height(10.dp))

                    // Row 2: Source (clickable to launch browser)
                    DetailMetadataCard(
                        icon = Icons.Default.Link,
                        label = "Source",
                        trailing = {
                            IconButton(
                                onClick = {
                                    runCatching {
                                        val url = if (!item.url.startsWith("http://") && !item.url.startsWith("https://")) {
                                            "https://${item.url}"
                                        } else {
                                            item.url
                                        }
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    }.onFailure {
                                        Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in browser",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        content = {
                            Text(
                                text = item.url.ifBlank { item.domain },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )

                    Spacer(Modifier.height(10.dp))

                    // Row 3: Note (with tap to edit affordance)
                    DetailMetadataCard(
                        icon = Icons.Default.Description,
                        label = "Note",
                        trailing = {
                            IconButton(
                                onClick = { showEditNoteDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit note",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        content = {
                            Text(
                                text = item.note.ifBlank { "No notes added yet. Tap to add your thoughts or annotations." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.note.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.clickable { showEditNoteDialog = true }
                            )
                        }
                    )

                    Spacer(Modifier.height(10.dp))

                    // Row 4: Tags
                    DetailMetadataCard(
                        icon = Icons.Default.LocalOffer,
                        label = "Tags",
                        content = {
                            if (item.tags.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item.tags.forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.wrapContentSize()
                                        ) {
                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Untagged",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(10.dp))

                    // Row 5: Title
                    DetailMetadataCard(
                        icon = Icons.Default.Notes,
                        label = "Title",
                        content = {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )

                    Spacer(Modifier.height(10.dp))

                    // Row 6: Description
                    DetailMetadataCard(
                        icon = Icons.Default.Subject,
                        label = "Description",
                        content = {
                            Text(
                                text = item.description.ifBlank { "No summary provided for this bookmark." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    )

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete from Pocket?") },
            text = { Text("This will remove \"${item.title}\" from your Pocket bookmarks.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(item)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Note Dialog
    if (showEditNoteDialog) {
        var noteDraft by remember { mutableStateOf(item.note) }
        AlertDialog(
            onDismissRequest = { showEditNoteDialog = false },
            title = { Text("Edit Note") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text("Personal Notes & Annotations") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditNoteDialog = false
                        onUpdate(item.copy(note = noteDraft.trim()))
                    }
                ) {
                    Text("Save Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Immersive Reading Mode Dialog
    if (showReaderModeDialog) {
        ModalBottomSheet(
            onDismissRequest = { showReaderModeDialog = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.domain,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showReaderModeDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close reader")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.formattedDateTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                if (item.note.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Reader Notes",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.note,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (item.readableContent.isNotBlank()) {
                    Text(
                        text = "Saved page",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = item.readableContent.ifBlank { item.description },
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

/**
 * Reusable row card matching the design in image_1ef4c7f4.png.
 * Each has an icon pill, a section label, content, and optional trailing action.
 */
@Composable
fun DetailMetadataCard(
    icon: ImageVector,
    label: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(82.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (trailing != null) 6.dp else 0.dp)
            ) {
                content()
            }

            if (trailing != null) {
                trailing()
            }
        }
    }
}
