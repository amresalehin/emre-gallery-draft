package com.amresalehin.emregallery.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.DetectedMediaFace
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceThumbnailHelper
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class DetailDrawerTab {
    OVERVIEW, ACTIONS
}

@Composable
fun ViewerButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.94f), CircleShape)
    ) {
        Icon(icon, label, tint = scheme.onSurface)
    }
}

@Composable
fun ViewerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null
) {
    val scheme = MaterialTheme.colorScheme
    val actionColor = tint ?: scheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = actionColor)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = actionColor, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 0.dp,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

/**
 * Top header displaying:
 * - Line 1: Filename
 * - Line 2: Capture date/time and file size (e.g., 25 Oct 2026 · 4:32 PM · 3.8 MB)
 * - Tabs: Overview and Actions in a subtle rounded pill container with underline on selected tab.
 * No Edit button in header.
 */
@Composable
fun DetailDrawerHeader(
    item: MediaItem,
    selectedTab: DetailDrawerTab,
    onSelectTab: (DetailDrawerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatMediaSubtitle(item),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(14.dp))

        // Subtle rounded container for Overview & Actions tabs
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                val isOverview = selectedTab == DetailDrawerTab.OVERVIEW
                val isActions = selectedTab == DetailDrawerTab.ACTIONS

                // Overview Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelectTab(DetailDrawerTab.OVERVIEW) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Overview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isOverview) FontWeight.Bold else FontWeight.Medium,
                            color = if (isOverview) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                        if (isOverview) {
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(84.dp)
                                    .height(2.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface,
                                        RoundedCornerShape(1.dp)
                                    )
                            )
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                // Actions Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelectTab(DetailDrawerTab.ACTIONS) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isActions) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActions) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                        if (isActions) {
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(84.dp)
                                    .height(2.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface,
                                        RoundedCornerShape(1.dp)
                                    )
                            )
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Overview Tab content:
 * 1. Tags (compact chips, hidden if empty)
 * 2. People (individual large portraits, horizontal scroll, no visible scrollbar, hidden if empty)
 * 3. Location (preview box + place name, entire card clickable to open map, hidden if empty)
 * 4. Metadata (compact heading with downward chevron, no duplicate fields, thin dividers)
 */
@Composable
fun OverviewTabContent(
    item: MediaItem,
    customTags: Set<String>,
    detectedFaces: List<DetectedMediaFace>,
    metadata: Map<String, String>,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var metadataExpanded by remember { mutableStateOf(true) }

    val hasTags = customTags.isNotEmpty()
    val hasPeople = detectedFaces.isNotEmpty()
    val hasLocation = metadata["location"]?.isNotBlank() == true || metadata.containsKey("latitude")

    // Readable place split into primary and secondary
    val rawLocation = metadata["location"].orEmpty()
    val (primaryLocation, secondaryLocation) = remember(rawLocation, metadata["latitude"], metadata["longitude"]) {
        if (rawLocation.isNotBlank()) {
            val parts = rawLocation.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            when {
                parts.size > 1 -> parts.first() to parts.drop(1).joinToString(", ")
                parts.size == 1 -> parts.first() to ""
                else -> "Location" to ""
            }
        } else if (metadata.containsKey("latitude") && metadata.containsKey("longitude")) {
            val lat = metadata["latitude"]?.toDoubleOrNull() ?: 0.0
            val lon = metadata["longitude"]?.toDoubleOrNull() ?: 0.0
            "%.4f°, %.4f°".format(lat, lon) to "GPS coordinates"
        } else {
            "" to ""
        }
    }

    // Filter metadata to avoid duplicating header/location/tags
    val metadataRows = remember(metadata, item) {
        buildMetadataRows(item, metadata)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Tags Section
        if (hasTags) {
            item(key = "overview_tags") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        customTags.sorted().forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. People Section
        if (hasPeople) {
            item(key = "overview_people") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "People",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        detectedFaces.forEach { face ->
                            OverviewFacePortrait(face = face)
                        }
                    }
                }
            }
        }

        // 3. Location Section
        if (hasLocation) {
            item(key = "overview_location") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    DetailCard(onClick = onOpenMap) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF22342E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF81C784),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Map",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF81C784),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = primaryLocation,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (secondaryLocation.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = secondaryLocation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Metadata Section
        if (metadataRows.isNotEmpty()) {
            item(key = "overview_metadata") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { metadataExpanded = !metadataExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Metadata",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (metadataExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle metadata",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (metadataExpanded) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                metadataRows.forEachIndexed { index, (label, value) ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1.1f)
                                        )
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Large portrait for Overview tab.
 * Uses real face crop if available, falling back to clean stylized face avatar.
 * Displays assigned name only; never displays "Unknown".
 */
@Composable
fun OverviewFacePortrait(
    face: DetectedMediaFace,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var faceCrop by remember(face.mediaUri, face.faceIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(face.mediaUri, face.faceIndex) {
        withContext(Dispatchers.IO) {
            faceCrop = FaceThumbnailHelper.getFaceCrop(context, Uri.parse(face.mediaUri), face.faceIndex)
        }
    }

    val displayName = face.displayName.trim()
    val isNamed = displayName.isNotBlank() && !displayName.equals("Unknown", ignoreCase = true)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(min = 68.dp, max = 80.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.size(64.dp)
        ) {
            val crop = faceCrop
            if (crop != null) {
                Image(
                    bitmap = crop.asImageBitmap(),
                    contentDescription = if (isNamed) displayName else null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                FaceAvatarIllustration(modifier = Modifier.fillMaxSize())
            }
        }
        if (isNamed) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Stylized cartoon avatar portrait matching reference screenshot.
 */
@Composable
fun FaceAvatarIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Dark background circle
        drawCircle(color = Color(0xFF2C3238))

        // Dark hair/hood
        drawCircle(
            color = Color(0xFF4C5259),
            radius = w * 0.38f,
            center = Offset(w * 0.5f, h * 0.52f)
        )

        // Face oval
        drawOval(
            color = Color(0xFFD4AA85),
            topLeft = Offset(w * 0.26f, h * 0.28f),
            size = Size(w * 0.48f, h * 0.48f)
        )

        // Smile and eyes
        val eyeColor = Color(0xFF382A20)
        val strokeWidth = (w * 0.035f).coerceAtLeast(1.5f)

        // Left eye
        drawArc(
            color = eyeColor,
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(w * 0.35f, h * 0.44f),
            size = Size(w * 0.10f, h * 0.08f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Right eye
        drawArc(
            color = eyeColor,
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(w * 0.55f, h * 0.44f),
            size = Size(w * 0.10f, h * 0.08f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Smile
        drawArc(
            color = eyeColor,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.42f, h * 0.56f),
            size = Size(w * 0.16f, h * 0.10f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

/**
 * Actions Tab Content:
 * 1. Media Actions:
 *    - Add to album (always visible)
 *    - Add location (only if media has no saved location)
 *    - Add tags (only if media has no assigned tags)
 *    Rows with label on left and small pencil button on right, thin dividers.
 * 2. People (same detected faces, smaller portraits, muted visual treatment, pencil directly beneath).
 * 3. OCR (Extract text heading, full-width Engine dropdown, Mode and Language side-by-side, compact Run OCR right-aligned).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsTabContent(
    item: MediaItem,
    customTags: Set<String>,
    detectedFaces: List<DetectedMediaFace>,
    metadata: Map<String, String>,
    detailsState: ViewerDetailsState,
    onAddTo: () -> Unit,
    onAddLocationClick: () -> Unit,
    onAddTagClick: () -> Unit,
    onRenameFaceClick: (DetectedMediaFace) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val hasLocation = metadata["location"]?.isNotBlank() == true || metadata.containsKey("latitude")
    val hasTags = customTags.isNotEmpty()
    val hasPeople = detectedFaces.isNotEmpty()

    var selectedEngine by remember { mutableStateOf("Tesseract") }
    var engineExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var langExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Media actions
        item(key = "media_actions") {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row: Add to album
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddTo() }
                        .padding(vertical = 12.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Add to album",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onAddTo,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Add to album",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Row: Add location (only when media has no saved location)
                if (!hasLocation) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddLocationClick() }
                            .padding(vertical = 12.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Add location",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = onAddLocationClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Add location",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Row: Add tags (only when media has no assigned tags)
                if (!hasTags) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddTagClick() }
                            .padding(vertical = 12.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Add tags",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = onAddTagClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Add tags",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. People (same detected faces, smaller portraits, muted look, pencil directly beneath)
        if (hasPeople) {
            item(key = "actions_people") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "People",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        detectedFaces.forEach { face ->
                            ActionFacePortrait(
                                face = face,
                                onRename = { onRenameFaceClick(face) }
                            )
                        }
                    }
                }
            }
        }

        // 3. OCR Section
        item(key = "actions_ocr") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Plain Extract text heading without leading icon
                Text(
                    text = "Extract text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Full-width Engine dropdown on its own row
                ExposedDropdownMenuBox(
                    expanded = engineExpanded,
                    onExpandedChange = { engineExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedEngine,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Engine") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = engineExpanded,
                        onDismissRequest = { engineExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tesseract") },
                            onClick = {
                                selectedEngine = "Tesseract"
                                engineExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("ML Kit (Planned)") },
                            onClick = {
                                selectedEngine = "ML Kit"
                                engineExpanded = false
                            }
                        )
                    }
                }

                // Mode and Language dropdowns side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isTesseract = selectedEngine == "Tesseract"
                    val isBest = detailsState.ocrModel.equals("best", ignoreCase = true)

                    // Mode dropdown
                    ExposedDropdownMenuBox(
                        expanded = modeExpanded,
                        onExpandedChange = { if (isTesseract) modeExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = if (isBest) "Best" else "Fast",
                            onValueChange = {},
                            readOnly = true,
                            enabled = isTesseract,
                            label = { Text("Mode") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = modeExpanded,
                            onDismissRequest = { modeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Fast") },
                                onClick = {
                                    detailsState.ocrModel = "fast"
                                    modeExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Best") },
                                onClick = {
                                    detailsState.ocrModel = "best"
                                    modeExpanded = false
                                }
                            )
                        }
                    }

                    // Language dropdown
                    val currentLangCode = detailsState.ocrLanguages.firstOrNull() ?: "eng"
                    val currentLangName = TesseractOcr.supportedLanguages.entries.firstOrNull { it.value == currentLangCode }?.key ?: "English"

                    ExposedDropdownMenuBox(
                        expanded = langExpanded,
                        onExpandedChange = { if (isTesseract) langExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = currentLangName,
                            onValueChange = {},
                            readOnly = true,
                            enabled = isTesseract,
                            label = { Text("Language") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false }
                        ) {
                            TesseractOcr.supportedLanguages.forEach { (name, code) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        detailsState.ocrLanguages.clear()
                                        detailsState.ocrLanguages.add(code)
                                        langExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Compact Run OCR button below dropdowns, aligned to the right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (selectedEngine == "Tesseract") {
                                coroutineScope.launch { detailsState.runOcr() }
                            }
                        },
                        enabled = !item.isVideo && !detailsState.ocrBusy && selectedEngine == "Tesseract",
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
                    ) {
                        if (detailsState.ocrBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Running...")
                        } else {
                            Text("Run OCR")
                        }
                    }
                }

                if (item.isVideo) {
                    Text(
                        text = "Text recognition is unavailable for videos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (selectedEngine == "ML Kit") {
                    Text(
                        text = "ML Kit text recognition is planned for a future update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (detailsState.ocrError != null) {
                    Text(
                        text = detailsState.ocrError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (detailsState.ocrText.isNotBlank()) {
                    DetailCard(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recognized Text",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = { detailsState.copyOcrText() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy text",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = detailsState.ocrText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Smaller portrait for Actions tab with muted visual treatment and a small pencil button directly beneath.
 */
@Composable
fun ActionFacePortrait(
    face: DetectedMediaFace,
    onRename: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var faceCrop by remember(face.mediaUri, face.faceIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(face.mediaUri, face.faceIndex) {
        withContext(Dispatchers.IO) {
            faceCrop = FaceThumbnailHelper.getFaceCrop(context, Uri.parse(face.mediaUri), face.faceIndex)
        }
    }

    val displayName = face.displayName.trim()
    val isNamed = displayName.isNotBlank() && !displayName.equals("Unknown", ignoreCase = true)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(min = 56.dp, max = 70.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { alpha = 0.88f }
        ) {
            val crop = faceCrop
            if (crop != null) {
                Image(
                    bitmap = crop.asImageBitmap(),
                    contentDescription = if (isNamed) displayName else null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                FaceAvatarIllustration(modifier = Modifier.fillMaxSize())
            }
        }
        if (isNamed) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(2.dp))
        IconButton(
            onClick = onRename,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Rename person",
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildMetadataRows(item: MediaItem, metadata: Map<String, String>): List<Pair<String, String>> {
    val videoWidth = metadata["video_width"]?.toIntOrNull() ?: 0
    val videoHeight = metadata["video_height"]?.toIntOrNull() ?: 0
    val width = item.width.takeIf { it > 0 } ?: videoWidth
    val height = item.height.takeIf { it > 0 } ?: videoHeight
    val dimensions = if (width > 0 && height > 0) "$width × $height px" else null

    val videoDurationMillis = item.durationMillis.takeIf { it > 0 }
        ?: metadata["video_duration_ms"]?.toLongOrNull()?.takeIf { it > 0 }
    val videoDuration = if (item.isVideo) {
        videoDurationMillis?.let { formatDuration(it) }
    } else null

    val bitrate = metadata["video_bitrate_bps"]?.toLongOrNull()?.takeIf { it > 0L }?.let {
        when {
            it >= 1_000_000L -> "%.2f Mbps".format(it / 1_000_000.0)
            it >= 1_000L -> "%.0f kbps".format(it / 1_000.0)
            else -> "$it bps"
        }
    }
    val captureFrameRate = metadata["video_capture_framerate"]?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }?.let { "%.2f fps".format(it) }
    val rotation = metadata["video_rotation_degrees"]?.toIntOrNull()?.let { "$it°" }
    val audioTrack = metadata["video_has_audio"]?.let {
        when {
            it.equals("yes", ignoreCase = true) -> "Present"
            it.equals("no", ignoreCase = true) -> "Not present"
            else -> it
        }
    }

    val formatString = run {
        val raw = item.mimeType?.takeIf { it.isNotBlank() } ?: metadata["mime_type"].orEmpty()
        when {
            raw.contains("jpeg", ignoreCase = true) || raw.contains("jpg", ignoreCase = true) -> "JPEG"
            raw.contains("png", ignoreCase = true) -> "PNG"
            raw.contains("webp", ignoreCase = true) -> "WEBP"
            raw.contains("gif", ignoreCase = true) -> "GIF"
            raw.contains("heic", ignoreCase = true) || raw.contains("heif", ignoreCase = true) -> "HEIF"
            raw.contains("mp4", ignoreCase = true) -> "MP4"
            raw.contains("webm", ignoreCase = true) -> "WEBM"
            raw.contains("3gp", ignoreCase = true) -> "3GP"
            raw.isNotBlank() -> raw.substringAfter('/') .uppercase()
            else -> null
        }
    }

    val gpsCoordinates = if (metadata.containsKey("latitude") && metadata.containsKey("longitude")) {
        val lat = metadata["latitude"]?.toDoubleOrNull()
        val lon = metadata["longitude"]?.toDoubleOrNull()
        if (lat != null && lon != null) {
            "%.4f° %s, %.4f° %s".format(
                abs(lat), if (lat >= 0) "N" else "S",
                abs(lon), if (lon >= 0) "E" else "W"
            )
        } else null
    } else null

    val rawRows = listOf(
        "Media type" to if (item.isVideo) "Video" else "Image",
        "File format" to formatString,
        "Dimensions" to dimensions,
        "Camera manufacturer" to metadata["make"],
        "Camera model" to metadata["model"],
        "Aperture" to metadata["aperture"],
        "Shutter speed" to metadata["shutter"],
        "ISO" to metadata["iso"],
        "Focal length" to metadata["focal"],
        "Flash" to metadata["flash"],
        "Color space" to metadata["color"],
        "GPS coordinates" to gpsCoordinates,
        "Duration" to videoDuration,
        "Video bitrate" to (if (item.isVideo) bitrate else null),
        "Capture frame rate" to (if (item.isVideo) captureFrameRate else null),
        "Rotation" to (if (item.isVideo) rotation else null),
        "Audio track" to (if (item.isVideo) audioTrack else null),
        "Track count" to (if (item.isVideo) metadata["video_track_count"] else null),
        "Creator" to metadata["creator"],
        "Description" to metadata["description"],
        "Copyright" to metadata["copyright"],
        "Subject" to metadata["subject"],
        "Keywords" to metadata["keywords"],
        "Rating" to metadata["rating"]
    )

    return rawRows.mapNotNull { (label, value) ->
        value?.trim()?.takeIf { it.isNotEmpty() && it != "Not available" }?.let { label to it }
    }
}
