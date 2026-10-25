package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amresalehin.emregallery.data.MediaItem
import java.text.NumberFormat
import java.util.Locale

@Composable
fun IndexingStatusScreen(
    media: List<MediaItem>,
    ocrLanguages: Set<String>,
    ocrModel: String = "fast",
    onModelChange: ((String) -> Unit)? = null,
    onOpenOcrSettings: (() -> Unit)? = null,
    onBack: () -> Unit,
    viewModel: IndexingStatusViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showFaceScanOptions by remember { mutableStateOf(false) }
    var showFallbackChoice by remember { mutableStateOf(false) }

    // Request notification permission on Android 13+ (POST_NOTIFICATIONS)
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

    LaunchedEffect(media, ocrLanguages, ocrModel) {
        viewModel.setMedia(media, ocrLanguages, ocrModel)
    }

    val lightAppearance = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val pageBackground = if (lightAppearance) Color(0xFFF7F7F7) else MaterialTheme.colorScheme.background
    val cardBackground = if (lightAppearance) Color.White else MaterialTheme.colorScheme.surfaceContainer
    val cardBorder = BorderStroke(
        1.dp,
        if (lightAppearance) Color(0xFFE5E7EB) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        // Main scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.statusBarsPadding())
            Spacer(Modifier.height(56.dp))

            // 1. OCR Indexing Card
            OcrIndexingCard(
                totalPhotos = uiState.totalPhotos,
                ocrState = uiState.ocr,
                countsReady = uiState.countsReady,
                cardBackground = cardBackground,
                cardBorder = cardBorder,
                numberFormat = numberFormat,
                onToggleOcr = viewModel::toggleOcr,
                onStopOcr = viewModel::stopOcr,
                onReindexOcr = viewModel::clearAndReindexOcr,
                onModelChange = { newModel ->
                    viewModel.setOcrModel(newModel)
                    onModelChange?.invoke(newModel)
                },
                onOpenOcrSettings = onOpenOcrSettings
            )

            // 2. Face Finding (Detection) Card
            FaceFindingCard(
                totalPhotos = uiState.totalPhotos,
                faceFindingState = uiState.faceFinding,
                engineState = uiState.engine,
                modelDownloadState = uiState.modelDownload,
                countsReady = uiState.countsReady,
                cardBackground = cardBackground,
                cardBorder = cardBorder,
                numberFormat = numberFormat,
                onConfidenceChange = viewModel::updateDetectionConfidence,
                onToggleFaceFinding = {
                    viewModel.toggleFaceFinding { showFallbackChoice = true }
                },
                onStopFaceFinding = viewModel::stopFaceFinding,
                onRescanRequested = { showFaceScanOptions = true },
                onDownloadModel = viewModel::startModelDownload,
                onCancelDownload = viewModel::cancelModelDownload
            )

            // 3. Face Clustering Card
            FaceClusteringCard(
                faceDetected = uiState.faceFinding.totalDetected,
                clusteringState = uiState.clustering,
                cardBackground = cardBackground,
                cardBorder = cardBorder,
                numberFormat = numberFormat,
                onMinGroupFacesChange = viewModel::updateMinGroupFaces,
                onClusterDistanceChange = viewModel::updateClusterDistance,
                onClusterFaces = viewModel::runFaceClustering,
                onResetClusters = viewModel::resetFaceClusters
            )

            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(16.dp))
        }

        // Floating Top Bar: Tactile Back & Refresh Buttons
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Surface(
                onClick = viewModel::refreshCounts,
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
        FaceScanOptionsDialog(
            onDismiss = { showFaceScanOptions = false },
            onRescanAll = {
                showFaceScanOptions = false
                viewModel.clearAndRescanFaces { showFallbackChoice = true }
            },
            onRemainingOnly = {
                showFaceScanOptions = false
                viewModel.toggleFaceFinding { showFallbackChoice = true }
            }
        )
    }

    if (showFallbackChoice) {
        FallbackChoiceDialog(
            onDismiss = { showFallbackChoice = false },
            onDownloadModel = {
                showFallbackChoice = false
                viewModel.startModelDownload()
            },
            onUseFallback = {
                showFallbackChoice = false
                viewModel.confirmFallbackChoiceAndStart(viewModel.rescanAllRequested)
            }
        )
    }
}
