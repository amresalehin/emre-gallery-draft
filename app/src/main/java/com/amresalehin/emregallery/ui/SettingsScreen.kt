package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.PreferencesRepository
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EngineComparisonResult
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    media: List<MediaItem> = emptyList(),
    total: Int = 0,
    theme: String,
    ocrLanguages: Set<String> = setOf("eng"),
    ocrModel: String = "fast",
    ocrEngine: String = "tesseract",
    mlKitLanguages: Set<String> = setOf("latin"),
    onModelChange: ((String) -> Unit)? = null,
    onEngineChange: ((String) -> Unit)? = null,
    onToggleLanguage: ((String) -> Unit)? = null,
    onToggleMlKitLanguage: ((String) -> Unit)? = null,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onTheme: (String) -> Unit,
    onRefresh: () -> Unit,
    onSettings: (String) -> Unit,
    onPermissionSettings: () -> Unit,
    onMenuClick: () -> Unit,
    prefs: PreferencesRepository? = null,
    onShowSnackbar: ((String) -> Unit)? = null,
    indexingViewModel: IndexingStatusViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actualPrefs = prefs ?: remember {
        PreferencesRepository(context.applicationContext)
    }
    val uiState by indexingViewModel.uiState.collectAsStateWithLifecycle()
    val faceStore = remember { FaceClusterStore(context.applicationContext) }

    var activeTargetEngineId by remember { mutableStateOf(BuffaloLFaceEngine.ENGINE_ID) }
    var showFaceScanOptions by remember { mutableStateOf(false) }
    var showModelRequiredDialog by remember { mutableStateOf(false) }
    var showComparisonDialog by remember { mutableStateOf(false) }
    var comparisonResult by remember { mutableStateOf<EngineComparisonResult?>(null) }

    val availableFolders = remember(media) {
        media.filterNot { it.isVideo }
            .groupBy { FaceRecognitionSettings.normalizeFolderPath(it.relativePath) }
            .map { (path, items) ->
                FaceFolderInfo(
                    path = path,
                    name = path.substringAfterLast('/').ifBlank { "Internal storage" },
                    photoCount = items.size,
                    coverUri = items.firstOrNull()?.uri
                )
            }
            .sortedByDescending { it.photoCount }
    }

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

    LaunchedEffect(media, ocrLanguages, ocrModel, ocrEngine, mlKitLanguages) {
        indexingViewModel.setMedia(media, ocrLanguages, ocrModel, ocrEngine, mlKitLanguages)
    }

    var query by remember { mutableStateOf("") }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val lightAppearance = !isDark
    val cardBackground = if (lightAppearance) Color.White else MaterialTheme.colorScheme.surfaceContainer
    val cardBorder = BorderStroke(
        1.dp,
        if (lightAppearance) Color(0xFFE5E7EB) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

    val matchesTheme = query.isBlank() ||
        "theme".contains(query, ignoreCase = true) ||
        "dark".contains(query, ignoreCase = true) ||
        "light".contains(query, ignoreCase = true) ||
        "mode".contains(query, ignoreCase = true)

    val matchesOcr = query.isBlank() ||
        "ocr".contains(query, ignoreCase = true) ||
        "text".contains(query, ignoreCase = true) ||
        "recognition".contains(query, ignoreCase = true) ||
        "language".contains(query, ignoreCase = true) ||
        "tesseract".contains(query, ignoreCase = true) ||
        "ml kit".contains(query, ignoreCase = true) ||
        "scan".contains(query, ignoreCase = true)

    val matchesFace = query.isBlank() ||
        "face".contains(query, ignoreCase = true) ||
        "people".contains(query, ignoreCase = true) ||
        "recognition".contains(query, ignoreCase = true) ||
        "clustering".contains(query, ignoreCase = true) ||
        "buffalo".contains(query, ignoreCase = true) ||
        "ente".contains(query, ignoreCase = true)

    val matchesAi = query.isBlank() ||
        "ai configuration".contains(query, ignoreCase = true) ||
        "cloud".contains(query, ignoreCase = true) ||
        "local".contains(query, ignoreCase = true) ||
        "florence".contains(query, ignoreCase = true) ||
        "model".contains(query, ignoreCase = true) ||
        "provider".contains(query, ignoreCase = true)

    val otherEntries = listOf(
        "About" to Icons.Default.Info
    ).filter { it.first.contains(query, ignoreCase = true) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = topContentPadding + 64.dp,
                bottom = bottomContentPadding
            )
        ) {
            // 1. THEME CARD (At the very top)
            if (matchesTheme) {
                item(key = "theme_card") {
                    AestheticThemeCard(
                        theme = theme,
                        isDark = isDark,
                        onTheme = onTheme
                    )
                }
            }

            // 2. OCR CARD (Moved out of indexing status page and placed below theme card)
            if (matchesOcr) {
                item(key = "ocr_indexing_card") {
                    OcrIndexingCard(
                        totalPhotos = uiState.totalPhotos,
                        ocrState = uiState.ocr,
                        ocrLanguages = ocrLanguages,
                        countsReady = uiState.countsReady,
                        cardBackground = cardBackground,
                        cardBorder = cardBorder,
                        numberFormat = numberFormat,
                        onToggleOcr = indexingViewModel::toggleOcr,
                        onStopOcr = indexingViewModel::stopOcr,
                        onReindexOcr = indexingViewModel::clearAndReindexOcr,
                        onEngineChange = { newEngine ->
                            indexingViewModel.setOcrEngine(newEngine)
                            onEngineChange?.invoke(newEngine)
                        },
                        onModelChange = { newModel ->
                            indexingViewModel.setOcrModel(newModel)
                            onModelChange?.invoke(newModel)
                        },
                        onToggleLanguage = { code ->
                            onToggleLanguage?.invoke(code)
                        },
                        onToggleMlKitLanguage = { code ->
                            onToggleMlKitLanguage?.invoke(code)
                        },
                        onClearCache = {
                            scope.launch(Dispatchers.IO) {
                                TesseractOcr.clearCachedResults(context)
                                withContext(Dispatchers.Main) {
                                    indexingViewModel.refreshCounts()
                                }
                            }
                        },
                        initiallyExpanded = query.isNotBlank() && matchesOcr
                    )
                }
            }

            settingsFeatureCardsItems(
                query = query,
                matchesFace = matchesFace,
                matchesAi = matchesAi,
                matchesTheme = matchesTheme,
                matchesOcr = matchesOcr,
                matchedDestinations = matchedDestinations,
                indexingState = indexingState,
                indexingViewModel = indexingViewModel,
                availableFolders = availableFolders,
                activeTargetEngineId = activeTargetEngineId,
                onActiveTargetEngineIdChange = { activeTargetEngineId = it },
                onShowModelRequiredDialog = { showModelRequiredDialog = true },
                onNavigate = onNavigate,
                onRunPipelineComparison = {
                    val sampleUri = faceStore.findCommonIndexedMediaUri() ?: media.firstOrNull { !it.isVideo }?.uri
                    if (sampleUri != null) {
                        comparisonResult = faceStore.compareEnginesOnMedia(
                            mediaUri = sampleUri,
                            context = context
                        )
                        showComparisonDialog = true
                    }
                }
            )
        }

        SettingsFloatingSearchBar(
            query = query,
            onQueryChange = { query = it },
            onMenuClick = onMenuClick
        )
    }

    SettingsActionDialogs(
        showFaceScanOptions = showFaceScanOptions,
        onDismissFaceScanOptions = { showFaceScanOptions = false },
        showModelRequiredDialog = showModelRequiredDialog,
        onShowModelRequiredChange = { showModelRequiredDialog = it },
        activeTargetEngineId = activeTargetEngineId,
        indexingViewModel = indexingViewModel,
        showComparisonDialog = showComparisonDialog,
        onDismissComparisonDialog = { showComparisonDialog = false },
        comparisonResult = comparisonResult
    )
}
