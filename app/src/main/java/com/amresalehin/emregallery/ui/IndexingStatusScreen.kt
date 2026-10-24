package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.IndexingForegroundService
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.*
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun IndexingStatusScreen(
    media: List<MediaItem>,
    ocrLanguages: Set<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result is reflected by Android notification settings. */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val photos = remember(media) { media.filterNot { it.isVideo } }
    val photoUris = remember(photos) { photos.map { it.uri } }
    val faceStore = remember { FaceClusterStore(context.applicationContext) }
    val lightAppearance = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val pageBackground = if (lightAppearance) Color(0xFFF7F7F7) else MaterialTheme.colorScheme.background
    val cardBackground = if (lightAppearance) Color.White else MaterialTheme.colorScheme.surfaceContainer
    val cardBorder = BorderStroke(1.dp, if (lightAppearance) Color(0xFFE5E7EB) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

    var countsReady by remember { mutableStateOf(false) }
    var ocrProcessed by remember { mutableIntStateOf(0) }
    var ocrCachedCount by remember { mutableIntStateOf(0) }
    var faceProcessed by remember { mutableIntStateOf(0) }
    var faceTotalDetected by remember { mutableIntStateOf(0) }
    var faceFailedCount by remember { mutableIntStateOf(0) }
    var clusterSummaries by remember { mutableStateOf<List<FaceClusterSummary>>(emptyList()) }
    var clusterDistance by remember { mutableFloatStateOf(FaceRecognitionSettings.recognitionDistance(context)) }
    var detectionConfidence by remember { mutableFloatStateOf(FaceRecognitionSettings.detectionConfidence(context)) }
    var minimumGroupFaces by remember { mutableIntStateOf(FaceRecognitionSettings.minGroupFaces(context)) }
    var isClusteringRunning by remember { mutableStateOf(false) }
    var clusterStatusMessage by remember { mutableStateOf<String?>(null) }

    var ocrItemsPerMinute by remember { mutableIntStateOf(0) }
    var faceItemsPerMinute by remember { mutableIntStateOf(0) }

    var ocrRunning by remember { mutableStateOf(false) }
    var ocrPaused by remember { mutableStateOf(false) }
    var ocrJob by remember { mutableStateOf<Job?>(null) }
    var ocrRunToken by remember { mutableIntStateOf(0) }
    val ocrPauseSignal = remember { MutableStateFlow(false) }

    var faceRunning by remember { mutableStateOf(false) }
    var facePaused by remember { mutableStateOf(false) }
    var faceJob by remember { mutableStateOf<Job?>(null) }
    var faceRunToken by remember { mutableIntStateOf(0) }
    val facePauseSignal = remember { MutableStateFlow(false) }

    var isBuffaloLInstalled by remember { mutableStateOf(BuffaloLFaceEngine.isModelInstalled(context)) }
    var activeEngineName by remember { mutableStateOf("Detecting...") }
    var primaryModelFailure by remember { mutableStateOf<String?>(null) }

    var isDownloadingModel by remember { mutableStateOf(false) }
    var isModelDownloadPaused by remember { mutableStateOf(false) }
    val modelDownloadPauseSignal = remember { MutableStateFlow(false) }
    var showFaceScanOptions by remember { mutableStateOf(false) }
    var allowNativeFallback by remember { mutableStateOf(false) }
    var rescanAllRequested by remember { mutableStateOf(false) }
    var showFallbackChoice by remember { mutableStateOf(false) }
    var downloadStage by remember { mutableStateOf("downloading") }
    var downloadBytesReceived by remember { mutableLongStateOf(0L) }
    var downloadTotalBytes by remember { mutableLongStateOf(0L) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun updateEngineState() {
        val (engine, failure) = FaceIndexingRepository.queryEngineInfo(context)
        isBuffaloLInstalled = BuffaloLFaceEngine.isModelInstalled(context)
        activeEngineName = engine
        primaryModelFailure = failure
    }

    fun startModelDownload(force: Boolean = false) {
        if (isDownloadingModel || ocrRunning || faceRunning) return
        isDownloadingModel = true
        isModelDownloadPaused = false
        downloadError = null
        downloadStage = "downloading"
        downloadBytesReceived = 0L
        downloadTotalBytes = 0L
        IndexingForegroundService.downloadModel(context, force)
    }

    fun toggleModelDownloadPause() {
        isModelDownloadPaused = !isModelDownloadPaused
        if (isModelDownloadPaused) IndexingForegroundService.pause(context)
        else IndexingForegroundService.resume(context)
    }

    fun cancelModelDownload() {
        IndexingForegroundService.cancel(context)
        isDownloadingModel = false
        isModelDownloadPaused = false
    }

    fun refreshCounts() {
        updateEngineState()
        scope.launch {
            countsReady = false
            withContext(Dispatchers.IO) {
                val oCount = photos.count { item ->
                    TesseractOcr.hasCachedResult(context, Uri.parse(item.uri))
                }
                val oFiles = TesseractOcr.cachedResultCount(context)
                val fCount = faceStore.indexedMediaCount(photoUris)
                val fFaces = faceStore.indexedFaceCount()
                val fFailures = faceStore.failedMediaCount()
                val cSummaries = faceStore.summaries()
                withContext(Dispatchers.Main.immediate) {
                    ocrProcessed = oCount.coerceIn(0, photos.size)
                    ocrCachedCount = oFiles
                    faceProcessed = fCount.coerceIn(0, photos.size)
                    faceTotalDetected = fFaces
                    faceFailedCount = fFailures
                    clusterSummaries = cSummaries
                    countsReady = true
                    updateEngineState()
                }
            }
        }
    }

    LaunchedEffect(photoUris) {
        updateEngineState()
        refreshCounts()
    }

    LaunchedEffect(Unit) {
        IndexingForegroundService.status(context)
        var wasRunning = false
        while (true) {
            val state = context.getSharedPreferences(IndexingForegroundService.STATE_PREFS, android.content.Context.MODE_PRIVATE)
            val task = state.getString(IndexingForegroundService.KEY_TASK, "") ?: ""
            val running = state.getBoolean(IndexingForegroundService.KEY_RUNNING, false)
            val paused = state.getBoolean(IndexingForegroundService.KEY_PAUSED, false)
            val processed = state.getInt(IndexingForegroundService.KEY_PROCESSED, 0)
            val total = state.getInt(IndexingForegroundService.KEY_TOTAL, 0)
            when (task) {
                "ocr" -> { ocrRunning = running; ocrPaused = paused; if (total > 0) ocrProcessed = processed }
                "face" -> { faceRunning = running; facePaused = paused; if (total > 0) faceProcessed = processed; faceTotalDetected = state.getInt(IndexingForegroundService.KEY_FACES, faceTotalDetected); faceFailedCount = state.getInt(IndexingForegroundService.KEY_FAILED, faceFailedCount) }
                "model" -> { isDownloadingModel = running; isModelDownloadPaused = paused; downloadStage = state.getString(IndexingForegroundService.KEY_STAGE, "downloading") ?: "downloading"; downloadBytesReceived = state.getLong(IndexingForegroundService.KEY_BYTES, 0L); downloadTotalBytes = state.getLong(IndexingForegroundService.KEY_BYTES_TOTAL, 0L) }
            }
            downloadError = state.getString(IndexingForegroundService.KEY_ERROR, null)
            if (wasRunning && !running) {
                if (task == "ocr" || task == "face") refreshCounts()
                updateEngineState()
            }
            wasRunning = running
            delay(750)
        }
    }



    fun itemsPerMinute(processedThisRun: Int, startedAtMillis: Long): Int {
        if (processedThisRun <= 0) return 0
        val elapsed = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(1_000L)
        return ((processedThisRun * 60_000.0) / elapsed).roundToInt().coerceAtLeast(1)
    }

    fun toggleOcr() {
        if (!countsReady || photos.isEmpty()) return
        if (ocrRunning) {
            ocrPaused = !ocrPaused
            if (ocrPaused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
            return
        }
        if (faceRunning || isDownloadingModel) return
        if (ocrProcessed >= photos.size) return
        ocrRunning = true
        ocrPaused = false
        IndexingForegroundService.startOcr(context, ocrLanguages.toList().ifEmpty { listOf("eng") })
    }

    fun stopOcr() {
        IndexingForegroundService.cancel(context)
        ocrRunning = false
        ocrPaused = false
        refreshCounts()
    }

    fun clearAndReindexOcr() {
        if (ocrRunning || faceRunning || isDownloadingModel) return
        ocrRunning = true
        ocrPaused = false
        IndexingForegroundService.startOcr(context, ocrLanguages.toList().ifEmpty { listOf("eng") }, rescanAll = true)
    }

    fun toggleFaceFinding() {
        if (!countsReady || photos.isEmpty()) return
        if (faceRunning) {
            facePaused = !facePaused
            if (facePaused) IndexingForegroundService.pause(context) else IndexingForegroundService.resume(context)
            return
        }
        if (ocrRunning || isDownloadingModel) return
        if (faceProcessed >= photos.size) return
        if (!isBuffaloLInstalled && !allowNativeFallback) {
            rescanAllRequested = false
            showFallbackChoice = true
            return
        }
        faceRunning = true
        facePaused = false
        IndexingForegroundService.startFace(context, allowNative = allowNativeFallback)
    }

    fun stopFaceFinding() {
        IndexingForegroundService.cancel(context)
        faceRunning = false
        facePaused = false
        refreshCounts()
    }

    fun clearAndRescanFaces() {
        if (ocrRunning || faceRunning || isDownloadingModel) return
        if (!isBuffaloLInstalled && !allowNativeFallback) {
            rescanAllRequested = true
            showFallbackChoice = true
            return
        }
        faceRunning = true
        facePaused = false
        IndexingForegroundService.startFace(context, rescanAll = true, allowNative = allowNativeFallback)
    }

    fun runFaceClustering(threshold: Float) {
        if (isClusteringRunning) return
        isClusteringRunning = true
        clusterStatusMessage = "Clustering facial embeddings..."
        scope.launch {
            val resultCount = withContext(Dispatchers.IO) {
                faceStore.reclusterAll(threshold)
            }
            withContext(Dispatchers.Main.immediate) {
                clusterSummaries = withContext(Dispatchers.IO) { faceStore.summaries() }
                faceTotalDetected = withContext(Dispatchers.IO) { faceStore.indexedFaceCount() }
                isClusteringRunning = false
                clusterStatusMessage = "Successfully grouped into $resultCount people profiles."
            }
        }
    }

    fun resetFaceClusters() {
        if (isClusteringRunning) return
        scope.launch {
            withContext(Dispatchers.IO) {
                faceStore.resetClustersOnly()
            }
            withContext(Dispatchers.Main.immediate) {
                clusterSummaries = emptyList()
                clusterStatusMessage = "Clusters reset. Stored facial embeddings preserved."
            }
        }
    }

    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        // Scrollable content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Space reserved for the floating back button and status bar
            Spacer(Modifier.statusBarsPadding())
            Spacer(Modifier.height(56.dp))

            // 1. OCR Indexing Card
            val ocrProgress = if (photos.isNotEmpty()) (ocrProcessed.toFloat() / photos.size).coerceIn(0f, 1f) else 0f
            val ocrPercent = (ocrProgress * 100f).roundToInt()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "OCR Text Indexing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Status Badge
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    when {
                                        ocrRunning && !ocrPaused -> "Scanning"
                                        ocrRunning && ocrPaused -> "Paused"
                                        ocrProcessed >= photos.size && photos.isNotEmpty() -> "Complete"
                                        else -> "Idle"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = when {
                                    ocrRunning && !ocrPaused -> MaterialTheme.colorScheme.primaryContainer
                                    ocrRunning && ocrPaused -> MaterialTheme.colorScheme.secondaryContainer
                                    ocrProcessed >= photos.size && photos.isNotEmpty() -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                disabledLabelColor = when {
                                    ocrRunning && !ocrPaused -> MaterialTheme.colorScheme.onPrimaryContainer
                                    ocrRunning && ocrPaused -> MaterialTheme.colorScheme.onSecondaryContainer
                                    ocrProcessed >= photos.size && photos.isNotEmpty() -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Progress
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${numberFormat.format(ocrProcessed)} of ${numberFormat.format(photos.size)} photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$ocrPercent%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { ocrProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IndexingMetric(
                            label = "Processing Rate",
                            value = "$ocrItemsPerMinute/min",
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Cached Results",
                            value = numberFormat.format(ocrCachedCount),
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Pending",
                            value = numberFormat.format((photos.size - ocrProcessed).coerceAtLeast(0)),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = ::toggleOcr,
                            enabled = countsReady && photos.isNotEmpty() && (ocrRunning || ocrProcessed < photos.size),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (ocrRunning && !ocrPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    ocrRunning && !ocrPaused -> "Pause"
                                    ocrRunning && ocrPaused -> "Resume"
                                    ocrProcessed >= photos.size && photos.isNotEmpty() -> "Finished"
                                    else -> "Start OCR"
                                }
                            )
                        }
                        if (ocrRunning) {
                            OutlinedButton(
                                onClick = ::stopOcr,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                        OutlinedButton(
                            onClick = ::clearAndReindexOcr,
                            enabled = countsReady && photos.isNotEmpty() && !ocrRunning,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Re-index")
                        }
                    }
                }
            }

            // 2. Face Finding (Detection) Card
            val faceProgress = if (photos.isNotEmpty()) (faceProcessed.toFloat() / photos.size).coerceIn(0f, 1f) else 0f
            val facePercent = (faceProgress * 100f).roundToInt()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val downloadPercent = if (downloadTotalBytes > 0) {
                        ((downloadBytesReceived.toFloat() / downloadTotalBytes) * 100f).roundToInt().coerceIn(0, 100)
                    } else if (downloadBytesReceived > 0) {
                        ((downloadBytesReceived.toFloat() / (288L * 1024 * 1024)) * 100f).roundToInt().coerceIn(0, 99)
                    } else 0

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Face,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Face Finding",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Status Badge

                        val faceChipLabel = when {
                            isDownloadingModel -> when (downloadStage) {
                                "extracting" -> "Extracting"
                                "installing" -> "Installing"
                                else -> "Downloading $downloadPercent%"
                            }
                            faceRunning && !facePaused -> "Detecting"
                            faceRunning && facePaused -> "Paused"
                            faceProcessed >= photos.size && photos.isNotEmpty() -> "Complete"
                            downloadError != null -> "Download Failed"
                            !isBuffaloLInstalled -> "Native Fallback"
                            primaryModelFailure != null -> "Primary Failed"
                            else -> "Buffalo_L Ready"
                        }

                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    faceChipLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = when {
                                    isDownloadingModel -> MaterialTheme.colorScheme.primaryContainer
                                    faceRunning && !facePaused -> MaterialTheme.colorScheme.primaryContainer
                                    faceRunning && facePaused -> MaterialTheme.colorScheme.secondaryContainer
                                    faceProcessed >= photos.size && photos.isNotEmpty() -> MaterialTheme.colorScheme.tertiaryContainer
                                    downloadError != null || (primaryModelFailure != null && isBuffaloLInstalled) -> MaterialTheme.colorScheme.errorContainer
                                    !isBuffaloLInstalled -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                },
                                disabledLabelColor = when {
                                    isDownloadingModel -> MaterialTheme.colorScheme.onPrimaryContainer
                                    faceRunning && !facePaused -> MaterialTheme.colorScheme.onPrimaryContainer
                                    faceRunning && facePaused -> MaterialTheme.colorScheme.onSecondaryContainer
                                    faceProcessed >= photos.size && photos.isNotEmpty() -> MaterialTheme.colorScheme.onTertiaryContainer
                                    downloadError != null || (primaryModelFailure != null && isBuffaloLInstalled) -> MaterialTheme.colorScheme.onErrorContainer
                                    !isBuffaloLInstalled -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Detection confidence control. Applies to the next face scan.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Face Detection Confidence", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                            Text(String.format(Locale.US, "%.2f", detectionConfidence), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = detectionConfidence,
                            onValueChange = {
                                detectionConfidence = (it * 100).roundToInt() / 100f
                                FaceRecognitionSettings.preferences(context).edit().putFloat(FaceRecognitionSettings.KEY_DETECTION_CONFIDENCE, detectionConfidence).apply()
                            },
                            valueRange = 0.30f..0.90f,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !faceRunning
                        )
                        Text("Higher values reject weaker detections. Changes apply to new scans; use Re-scan to reprocess indexed photos.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Active Engine Status & Model Download
                    val engineBannerShape = RoundedCornerShape(14.dp)
                    if (isDownloadingModel) {
                        Surface(
                            shape = engineBannerShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        when (downloadStage) {
                                            "extracting" -> "Extracting Buffalo_L Model…"
                                            "installing" -> "Installing Buffalo_L Model…"
                                            else -> "Downloading Buffalo_L Model ($downloadPercent%)"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.weight(1f))
                                    TextButton(
                                        onClick = ::cancelModelDownload,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                val isTransferStage = downloadStage == "downloading"
                                val progressFloat = if (downloadTotalBytes > 0) {
                                    (downloadBytesReceived.toFloat() / downloadTotalBytes).coerceIn(0f, 0.99f)
                                } else 0f
                                if (isTransferStage && downloadTotalBytes > 0) {
                                    LinearProgressIndicator(
                                        progress = { progressFloat },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (isTransferStage) Text(
                                    text = "${formatBytes(downloadBytesReceived)} of ${if (downloadTotalBytes > 0) formatBytes(downloadTotalBytes) else "unknown size"} downloaded",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (!isBuffaloLInstalled) {
                        Surface(
                            shape = engineBannerShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Active Engine: Android Native FaceDetector (Fallback)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Primary Buffalo_L model (288 MB) is not downloaded. Using built-in 128-d face detection. Download Buffalo_L for deep face recognition.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (downloadError != null) {
                                    Text(
                                        "Download failed: $downloadError",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Button(
                                    onClick = { startModelDownload(force = true) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download Buffalo_L (288 MB)")
                                }
                            }
                        }
                    } else if (primaryModelFailure != null) {
                        Surface(
                            shape = engineBannerShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Primary Engine Failed: Buffalo_L",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            "Reason: $primaryModelFailure\nActive engine: Android Native FaceDetector (Fallback)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { startModelDownload(force = true) },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Re-download Model")
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = engineBannerShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Active Engine: Buffalo_L (InsightFace ONNX)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "High-accuracy SCRFD detector + 512-dim ArcFace embeddings active.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Progress
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${numberFormat.format(faceProcessed)} of ${numberFormat.format(photos.size)} photos scanned",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$facePercent%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { faceProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IndexingMetric(
                            label = "Detected Faces",
                            value = numberFormat.format(faceTotalDetected),
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Detection Speed",
                            value = "$faceItemsPerMinute/min",
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Unreadable",
                            value = numberFormat.format(faceFailedCount),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = ::toggleFaceFinding,
                            enabled = countsReady && photos.isNotEmpty() && !isDownloadingModel && (faceRunning || faceProcessed < photos.size),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (faceRunning && !facePaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    faceRunning && !facePaused -> "Pause"
                                    faceRunning && facePaused -> "Resume"
                                    faceProcessed >= photos.size && photos.isNotEmpty() -> "Finished"
                                    else -> "Find Faces"
                                }
                            )
                        }
                        if (faceRunning) {
                            OutlinedButton(
                                onClick = ::stopFaceFinding,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                        OutlinedButton(
                            onClick = { showFaceScanOptions = true },
                            enabled = countsReady && photos.isNotEmpty() && !faceRunning,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Re-scan")
                        }
                    }
                }
            }

            // 3. Face Clustering Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Face Clustering",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Status Badge
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    when {
                                        isClusteringRunning -> "Clustering"
                                        faceTotalDetected == 0 -> "No Faces"
                                        else -> "${clusterSummaries.size} Groups"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = when {
                                    isClusteringRunning -> MaterialTheme.colorScheme.primaryContainer
                                    clusterSummaries.isNotEmpty() -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                disabledLabelColor = when {
                                    isClusteringRunning -> MaterialTheme.colorScheme.onPrimaryContainer
                                    clusterSummaries.isNotEmpty() -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Minimum group size only hides small groups from People & pets; it does not delete indexed faces.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Minimum Faces per Person", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                            Text("$minimumGroupFaces faces", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = minimumGroupFaces.toFloat(),
                            onValueChange = {
                                minimumGroupFaces = it.roundToInt().coerceIn(1, 30)
                                FaceRecognitionSettings.preferences(context).edit().putInt(FaceRecognitionSettings.KEY_MIN_GROUP_FACES, minimumGroupFaces).apply()
                            },
                            valueRange = 1f..30f,
                            steps = 28,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Groups below this size stay indexed but are hidden from People & pets.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Recognition Distance Line Drag Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Maximum Recognition Distance",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val thresholdLabel = when {
                                clusterDistance <= 0.45f -> "Strict"
                                clusterDistance <= 0.60f -> "Balanced"
                                else -> "Loose"
                            }
                            Text(
                                String.format(Locale.US, "%.2f (%s)", clusterDistance, thresholdLabel),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = clusterDistance,
                            onValueChange = {
                                clusterDistance = (it * 100).roundToInt() / 100f
                                FaceRecognitionSettings.preferences(context).edit().putFloat(FaceRecognitionSettings.KEY_RECOGNITION_DISTANCE, clusterDistance).apply()
                            },
                            valueRange = 0.25f..0.75f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Strict (0.25)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Loose (0.75)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Metrics Grid
                    val largestGroup = remember(clusterSummaries) {
                        clusterSummaries.maxOfOrNull { it.faceCount } ?: 0
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IndexingMetric(
                            label = "People Groups",
                            value = numberFormat.format(clusterSummaries.size),
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Clustered Faces",
                            value = numberFormat.format(faceTotalDetected),
                            modifier = Modifier.weight(1f)
                        )
                        IndexingMetric(
                            label = "Largest Group",
                            value = "$largestGroup faces",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Feedback status text
                    if (clusterStatusMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    clusterStatusMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else if (faceTotalDetected == 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Tip: Run Face Finding on your photos first so face embeddings are available for clustering.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { runFaceClustering(clusterDistance) },
                            enabled = !isClusteringRunning && faceTotalDetected > 0,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isClusteringRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Clustering…")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (clusterSummaries.isEmpty()) "Cluster Faces" else "Re-cluster Faces")
                            }
                        }
                        if (clusterSummaries.isNotEmpty()) {
                            OutlinedButton(
                                onClick = ::resetFaceClusters,
                                enabled = !isClusteringRunning,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }
                    }
                }
            }

            // Bottom navigation bar padding
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(16.dp))
        }

        // Floating Top Bar: Floating Back Button & Refresh Button
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tactile Floating Back Button
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Floating Refresh Button
            Surface(
                onClick = ::refreshCounts,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh status",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showFaceScanOptions) {
        AlertDialog(
            onDismissRequest = { showFaceScanOptions = false },
            title = { Text("Rescan faces") },
            text = { Text("Choose whether to scan only photos that have not been indexed, or clear the existing face index and scan every photo again.") },
            confirmButton = {
                TextButton(onClick = {
                    showFaceScanOptions = false
                    clearAndRescanFaces()
                }) { Text("Rescan all") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showFaceScanOptions = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        showFaceScanOptions = false
                        toggleFaceFinding()
                    }) { Text("Remaining only") }
                }
            }
        )
    }

    if (showFallbackChoice) {
        AlertDialog(
            onDismissRequest = { showFallbackChoice = false },
            title = { Text("Buffalo_L model unavailable") },
            text = { Text("Download the Buffalo_L model for higher-quality face recognition, or explicitly choose the built-in Android face detector. The native detector has lower-dimensional embeddings and may group faces less accurately.") },
            confirmButton = {
                TextButton(onClick = {
                    showFallbackChoice = false
                    startModelDownload()
                }) { Text("Download model") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showFallbackChoice = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        showFallbackChoice = false
                        allowNativeFallback = true
                        if (rescanAllRequested) {
                            rescanAllRequested = false
                            faceRunning = true
                            facePaused = false
                            IndexingForegroundService.startFace(context, rescanAll = true, allowNative = true)
                        } else {
                            toggleFaceFinding()
                        }
                    }) { Text("Use native fallback") }
                }
            }
        )
    }
}

@Composable
fun IndexingMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
