package com.amresalehin.emregallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.face.StageProgress
import com.amresalehin.emregallery.face.StageState
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "-- KB/s"
    val mbps = bytesPerSec.toDouble() / (1024 * 1024)
    return if (mbps >= 1.0) String.format(Locale.US, "%.1f MB/s", mbps)
    else String.format(Locale.US, "%.0f KB/s", bytesPerSec.toDouble() / 1024)
}

private fun formatEta(seconds: Long): String {
    if (seconds <= 0L) return "--"
    return if (seconds >= 60) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s"
}

@Composable
fun FaceRecognitionCard(
    totalPhotos: Int,
    faceFindingState: FaceFindingState,
    engineState: EngineState,
    modelDownloadState: ModelDownloadState,
    clusteringState: FaceClusteringState,
    enteFaceFindingState: FaceFindingState = FaceFindingState(),
    enteModelDownloadState: ModelDownloadState = ModelDownloadState(),
    enteClusteringState: FaceClusteringState = FaceClusteringState(),
    countsReady: Boolean,
    cardBackground: Color,
    cardBorder: BorderStroke,
    numberFormat: NumberFormat,
    onConfidenceChange: (Float, String) -> Unit,
    onMinGroupFacesChange: (Int, String) -> Unit,
    onClusterDistanceChange: (Float, String) -> Unit,
    onToggleFaceFinding: (String) -> Unit,
    onStopFaceFinding: (String) -> Unit,
    onRescanRequested: (String) -> Unit,
    onDownloadModel: (force: Boolean, engineId: String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onClusterFaces: (Float, String) -> Unit,
    onResetClusters: (String) -> Unit,
    onComparePipelines: (() -> Unit)? = null,
    onRetryFailedFaces: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedEngineId by remember {
        mutableStateOf(FaceRecognitionSettings.getActiveEngine(context))
    }
    val isEnte = selectedEngineId == EnteFaceConstants.ENGINE_ID

    val currentFindingState = if (isEnte) enteFaceFindingState else faceFindingState
    val currentDownloadState = if (isEnte) enteModelDownloadState else modelDownloadState
    val currentClusteringState = if (isEnte) enteClusteringState else clusteringState
    val isModelInstalled = if (isEnte) engineState.isEnteInstalled else engineState.isBuffaloLInstalled

    val faceProgress = if (totalPhotos > 0) (currentFindingState.processed.toFloat() / totalPhotos).coerceIn(0f, 1f) else 0f
    val facePercent = (faceProgress * 100f).roundToInt()

    val downloadPercent = if (currentDownloadState.totalBytes > 0) {
        ((currentDownloadState.bytesReceived.toFloat() / currentDownloadState.totalBytes) * 100f).roundToInt().coerceIn(0, 100)
    } else if (currentDownloadState.bytesReceived > 0) {
        val estimatedTotal = if (isEnte) 25L * 1024 * 1024 else 288L * 1024 * 1024
        ((currentDownloadState.bytesReceived.toFloat() / estimatedTotal) * 100f).roundToInt().coerceIn(0, 99)
    } else 0

    var isTelemetryExpanded by remember { mutableStateOf(false) }
    var pendingEngineSwitch by remember { mutableStateOf<String?>(null) }
    var showErrorLogDialog by remember { mutableStateOf(false) }
    var isAdvancedSettingsExpanded by remember { mutableStateOf(false) }

    // Mutex confirmation dialog when attempting to switch engines during active download
    if (pendingEngineSwitch != null) {
        val targetEngine = pendingEngineSwitch!!
        val targetEngineName = if (targetEngine == EnteFaceConstants.ENGINE_ID) "Ente" else "Buffalo_L"
        val activeEngineName = if (isEnte) "Ente" else "Buffalo_L"
        AlertDialog(
            onDismissRequest = { pendingEngineSwitch = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Cancel Active Download?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "A model download is currently active for $activeEngineName. Switching to $targetEngineName requires cancelling the current download. Do you want to cancel and switch?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelDownload(selectedEngineId)
                        selectedEngineId = targetEngine
                        FaceRecognitionSettings.setActiveEngine(context, targetEngine)
                        pendingEngineSwitch = null
                    }
                ) {
                    Text("Cancel Download & Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEngineSwitch = null }) {
                    Text("Keep Downloading")
                }
            }
        )
    }

    // Error log dialog for viewing failed items
    if (showErrorLogDialog) {
        AlertDialog(
            onDismissRequest = { showErrorLogDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Face Indexing Error Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    val failures = currentFindingState.failedItems
                    if (failures.isEmpty()) {
                        Text(
                            "${currentFindingState.failedCount} items encountered errors during indexing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(failures) { (uri, reason) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = uri.substringAfterLast('/'),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = reason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showErrorLogDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Icon, Face Recognition Title, Start/Pause Action, Expand/Collapse
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Face Recognition",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (isEnte) "Ente (YOLO5Face + MobileFaceNet)" else "Buffalo_L (InsightFace ArcFace)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Action Start / Pause / Resume button
                // Action Guarding: Guarded strictly when model is missing/downloading
                val canStartIndexing = isModelInstalled && !currentDownloadState.isDownloading && countsReady && totalPhotos > 0
                Button(
                    onClick = { onToggleFaceFinding(selectedEngineId) },
                    enabled = canStartIndexing && (currentFindingState.isRunning || currentFindingState.processed < totalPhotos),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        when {
                            currentFindingState.isRunning && !currentFindingState.isPaused -> Icons.Default.Pause
                            currentFindingState.processed >= totalPhotos && totalPhotos > 0 -> Icons.Default.Check
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            currentFindingState.isRunning && !currentFindingState.isPaused -> "Pause"
                            currentFindingState.isRunning && currentFindingState.isPaused -> "Resume"
                            currentFindingState.processed >= totalPhotos && totalPhotos > 0 -> "Done"
                            else -> "Start"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (currentFindingState.isRunning) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { onStopFaceFinding(selectedEngineId) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop face scanning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = { isTelemetryExpanded = !isTelemetryExpanded },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isTelemetryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isTelemetryExpanded) "Collapse telemetry" else "Expand telemetry",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Engine Selection Catalog Chips with Concurrency & Mutex
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Recognition Engine",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isDownloadingActive = currentDownloadState.isDownloading
                    FilterChip(
                        selected = !isEnte,
                        onClick = {
                            if (isEnte) {
                                if (isDownloadingActive) {
                                    pendingEngineSwitch = BuffaloLFaceEngine.ENGINE_ID
                                } else {
                                    selectedEngineId = BuffaloLFaceEngine.ENGINE_ID
                                    FaceRecognitionSettings.setActiveEngine(context, BuffaloLFaceEngine.ENGINE_ID)
                                }
                            }
                        },
                        label = {
                            Column {
                                Text("Buffalo_L", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (engineState.isBuffaloLInstalled) "Installed · 288 MB" else "Not downloaded · 288 MB",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        leadingIcon = if (!isEnte) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isEnte,
                        onClick = {
                            if (!isEnte) {
                                if (isDownloadingActive) {
                                    pendingEngineSwitch = EnteFaceConstants.ENGINE_ID
                                } else {
                                    selectedEngineId = EnteFaceConstants.ENGINE_ID
                                    FaceRecognitionSettings.setActiveEngine(context, EnteFaceConstants.ENGINE_ID)
                                }
                            }
                        },
                        label = {
                            Column {
                                Text("Ente", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (engineState.isEnteInstalled) "Installed · ~25 MB" else "Not downloaded · ~25 MB",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        leadingIcon = if (isEnte) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // --- 2. ENGINE LIFECYCLE MANAGEMENT BANNERS ---

            // State A: Pre-Download State (Model missing)
            if (!isModelInstalled && !currentDownloadState.isDownloading) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Model Download Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (isEnte) "Download YOLO5Face & MobileFaceNet weights (~25 MB) to enable scanning."
                                    else "Download Buffalo_L ArcFace ONNX model pack (288 MB) to enable scanning.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { onDownloadModel(true, selectedEngineId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download ${if (isEnte) "Ente Models (~25 MB)" else "Buffalo_L Model (288 MB)"}")
                        }
                    }
                }
            }

            // State B: Download & Verification Lifecycle
            if (currentDownloadState.isDownloading) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when (currentDownloadState.stage.lowercase(Locale.ROOT)) {
                                        "connecting" -> "Connecting…"
                                        "verifying" -> "Verifying SHA-256 Checksum…"
                                        "extracting" -> "Extracting Archive…"
                                        "installing" -> "Installing Weights…"
                                        "ready" -> "Installation Complete"
                                        else -> "Downloading ${if (isEnte) "Ente" else "Buffalo_L"}…"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "$downloadPercent%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        LinearProgressIndicator(
                            progress = { (downloadPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val transferredStr = formatBytes(currentDownloadState.bytesReceived)
                            val totalStr = formatBytes(currentDownloadState.totalBytes.takeIf { it > 0 } ?: if (isEnte) 25L * 1024 * 1024 else 288L * 1024 * 1024)
                            val speedStr = formatSpeed(currentDownloadState.speedBytesPerSec)
                            val etaStr = formatEta(currentDownloadState.etaSeconds)

                            Text(
                                "$transferredStr / $totalStr · $speedStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                "ETA: $etaStr",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { onCancelDownload(selectedEngineId) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cancel Download")
                            }
                        }
                    }
                }
            }

            // Download Failure / Verification Error Banner with Retry
            val modelFailure = if (isEnte) engineState.enteModelFailure else engineState.primaryModelFailure
            val downloadError = currentDownloadState.error
            val displayError = downloadError ?: (if (!isModelInstalled) modelFailure else null)

            if (displayError != null && !currentDownloadState.isDownloading) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = displayError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { onDownloadModel(true, selectedEngineId) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            // State C: Installed confirmation chip
            if (isModelInstalled && !currentDownloadState.isDownloading && displayError == null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${if (isEnte) "Ente" else "Buffalo_L"} model verified and ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- 3. FACE INDEXING OVERALL PIPELINE TELEMETRY ---

            // Active processing target filename & status
            if (currentFindingState.isRunning && (currentFindingState.currentItemName.isNotBlank() || currentFindingState.currentItemStatus.isNotBlank())) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (currentFindingState.currentItemName.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Analyzing: ${currentFindingState.currentItemName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (currentFindingState.currentItemStatus.isNotBlank()) {
                            Text(
                                currentFindingState.currentItemStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 24.dp)
                            )
                        }
                    }
                }
            }

            // Aggregate metrics & linear progress bar
            val successfulCount = if (currentFindingState.successfulCount > 0) {
                currentFindingState.successfulCount
            } else {
                (currentFindingState.processed - currentFindingState.failedCount).coerceAtLeast(0)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${numberFormat.format(successfulCount)} / ${numberFormat.format(totalPhotos)} indexed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (currentFindingState.failedCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "(${numberFormat.format(currentFindingState.failedCount)} failed)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Text(
                        "$facePercent%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { faceProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Post-execution error log banner with dedicated actions
            if (currentFindingState.failedCount > 0 && !currentFindingState.isRunning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${currentFindingState.failedCount} items failed to scan",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        TextButton(
                            onClick = { showErrorLogDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("View Log", style = MaterialTheme.typography.labelMedium)
                        }

                        if (onRetryFailedFaces != null) {
                            Spacer(Modifier.width(4.dp))
                            FilledTonalButton(
                                onClick = { onRetryFailedFaces(selectedEngineId) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Retry Failed", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            when {
                currentClusteringState.isClusteringRunning || currentFindingState.clusteringStage.state == StageState.RUNNING -> {
                    Row(Modifier.fillMaxWidth().padding(vertical=6.dp), verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier=Modifier.size(20.dp),strokeWidth=2.dp)
                        Column(modifier=Modifier.weight(1f)) {
                            Text("Grouping people…",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
                            val stage=currentFindingState.clusteringStage
                            if(stage.total>0) Text("${stage.processed} of ${stage.total} face embeddings",
                                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                currentFindingState.clusteringStage.state == StageState.FAILED -> {
                    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Column(modifier=Modifier.weight(1f)) {
                            Text("Grouping failed",style=MaterialTheme.typography.bodyMedium,
                                fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.error)
                            Text(currentFindingState.currentItemStatus.removePrefix("Grouping failed: ").ifBlank { "Grouping could not finish." },
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick={onClusterFaces(currentClusteringState.clusterDistance,selectedEngineId)}) {
                            Text("Retry grouping")
                        }
                    }
                }
                currentFindingState.isRunning && currentFindingState.clusteringStage.state == StageState.PENDING ->
                    Text("Grouping — waiting for embeddings to finish",style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.fillMaxWidth().padding(vertical=6.dp))
            }

            // --- 3.2 GRANULAR STAGE BREAKDOWN (EXPANDED PROGRESS CARD) ---
            AnimatedVisibility(
                visible = isTelemetryExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .surfaceBackground(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Sub-Stage Breakdown (${if (isEnte) "Ente Pipeline" else "Buffalo_L Pipeline"})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Sub-stage 1: Face Detection
                    StageRow(
                        stageName = if (isEnte) "Face Detection (YOLO5Face)" else "Face Detection (SCRFD-10GF)",
                        stage = currentFindingState.detectionStage,
                        totalItems = totalPhotos
                    )

                    // Sub-stage 2: Embedding Generation
                    StageRow(
                        stageName = if (isEnte) "Embedding Generation (MobileFaceNet)" else "Embedding Generation (ArcFace R50)",
                        stage = currentFindingState.embeddingStage,
                        totalItems = totalPhotos
                    )

                    // Sub-stage 3: Clustering (engine-conditional visibility)
                    StageRow(
                        stageName = if (isEnte) "Linear Incremental Clustering" else "Centroid Group Clustering",
                        stage = currentFindingState.clusteringStage,
                        totalItems = currentFindingState.clusteringStage.total.takeIf { it > 0 } ?: currentFindingState.totalDetected
                    )
                }
            }

            TextButton(onClick={isAdvancedSettingsExpanded=!isAdvancedSettingsExpanded},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Tune,contentDescription=null,modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if(isAdvancedSettingsExpanded) "Hide advanced settings" else "Advanced settings")
            }
            if(isAdvancedSettingsExpanded) {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
    // Slider 1: Detection confidence
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.TrackChanges,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Detection confidence",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = currentFindingState.detectionConfidence,
                            onValueChange = { onConfidenceChange(it, selectedEngineId) },
                            valueRange = 0.30f..0.90f,
                            enabled = !currentFindingState.isRunning,
                            modifier = Modifier.fillMaxWidth().height(26.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        String.format(Locale.US, "%.2f", currentFindingState.detectionConfidence),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(36.dp)
                    )
                }
    
                // Slider 2: Min faces per person
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Min faces to show person (People tab filter)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Unnamed people with fewer faces are hidden in the People tab.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = currentClusteringState.minGroupFaces.toFloat(),
                            onValueChange = { onMinGroupFacesChange(it.roundToInt().coerceIn(1, 30), selectedEngineId) },
                            valueRange = 1f..30f,
                            modifier = Modifier.fillMaxWidth().height(26.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${currentClusteringState.minGroupFaces}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(36.dp)
                    )
                }
    
                // Slider 3: Clustering distance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isEnte) "Clustering distance (Quality-adaptive)" else "Clustering distance",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = currentClusteringState.clusterDistance,
                            onValueChange = { onClusterDistanceChange(it, selectedEngineId) },
                            valueRange = 0.25f..0.75f,
                            modifier = Modifier.fillMaxWidth().height(26.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        String.format(Locale.US, "%.2f", currentClusteringState.clusterDistance),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(36.dp)
                    )
                }
                }
            }
            OutlinedButton(
                onClick={onRescanRequested(selectedEngineId)},
                enabled=countsReady && totalPhotos>0 && !currentFindingState.isRunning && isModelInstalled,
                shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh,contentDescription=null,modifier=Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Re-scan")
            }

        }
    }
}

@Composable
private fun StageRow(
    stageName: String,
    stage: StageProgress,
    totalItems: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (stage.state) {
                    StageState.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    StageState.COMPLETED -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    StageState.FAILED -> Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    StageState.PAUSED -> Icon(Icons.Default.PauseCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                    StageState.PENDING -> Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    stageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when (stage.state) {
                    StageState.RUNNING -> MaterialTheme.colorScheme.primaryContainer
                    StageState.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer
                    StageState.FAILED -> MaterialTheme.colorScheme.errorContainer
                    StageState.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
                    StageState.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = stage.state.name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        val total = if (stage.total > 0) stage.total else totalItems
        val pct = if (total > 0) ((stage.processed.toFloat() / total) * 100f).roundToInt().coerceIn(0, 100)
                  else if (stage.state == StageState.COMPLETED) 100 else 0

        LinearProgressIndicator(
            progress = { (pct / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (stage.state == StageState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${stage.processed} / $total processed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$pct%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Modifier.surfaceBackground(color: Color): Modifier = this.then(
    Modifier.clip(RoundedCornerShape(14.dp)).then(
        Modifier.drawSurface(color)
    )
)

private fun Modifier.drawSurface(color: Color): Modifier = this.then(
    Modifier.drawBehind { drawRect(color) }
)
