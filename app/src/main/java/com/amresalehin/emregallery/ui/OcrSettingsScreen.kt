package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.ocr.TesseractModel
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSettingsScreen(
    currentEngine: String = "tesseract",
    currentModel: String,
    ocrLanguages: Set<String>,
    mlKitLanguages: Set<String> = setOf("latin"),
    onSelectEngine: (String) -> Unit = {},
    onSelectModel: (String) -> Unit,
    onToggleLanguage: (String) -> Unit,
    onToggleMlKitLanguage: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isMlKit = currentEngine.equals("google_mlkit", ignoreCase = true) || currentEngine.equals("mlkit", ignoreCase = true)
    val activeModel = remember(currentModel) { TesseractModel.fromId(currentModel) }
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

    var cachedTextCount by remember { mutableIntStateOf(0) }
    var fastStorageBytes by remember { mutableLongStateOf(0L) }
    var bestStorageBytes by remember { mutableLongStateOf(0L) }
    var installingLanguageCode by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    fun refreshStats() {
        scope.launch(Dispatchers.IO) {
            val count = TesseractOcr.cachedResultCount(context)
            val fastBytes = TesseractOcr.getModelStorageBytes(context, TesseractModel.FAST)
            val bestBytes = TesseractOcr.getModelStorageBytes(context, TesseractModel.BEST)
            withContext(Dispatchers.Main) {
                cachedTextCount = count
                fastStorageBytes = fastBytes
                bestStorageBytes = bestBytes
            }
        }
    }

    LaunchedEffect(currentModel) {
        refreshStats()
    }

    fun downloadAndInstall(code: String) {
        if (installingLanguageCode != null) return
        installingLanguageCode = code
        actionMessage = "Installing $code for ${activeModel.title}…"
        scope.launch(Dispatchers.IO) {
            val success = runCatching {
                TesseractOcr.prepareLanguages(context, listOf(code), activeModel)
                true
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    actionMessage = "Failed to download $code: ${e.message}"
                }
            }.getOrDefault(false)

            withContext(Dispatchers.Main) {
                installingLanguageCode = null
                if (success) {
                    actionMessage = "Successfully installed $code for ${activeModel.title}!"
                    refreshStats()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "OCR & Text Recognition",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (isMlKit) "Google ML Kit (Multi-Language)" else "Tesseract Neural Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Action feedback banner if any
            if (actionMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                actionMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { actionMessage = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Engine Switch Selector
            item {
                Text(
                    "Default OCR Engine",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Choose between Google ML Kit (on-device multi-script neural models) or Tesseract LSTM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    SegmentedButton(
                        selected = !isMlKit,
                        onClick = { onSelectEngine("tesseract") },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {}
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tesseract OCR", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    SegmentedButton(
                        selected = isMlKit,
                        onClick = { onSelectEngine("google_mlkit") },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {}
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Google ML Kit", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (isMlKit) {
                ocrMlKitSettingsItems(
                    mlKitLanguages = mlKitLanguages,
                    onToggleMlKitLanguage = onToggleMlKitLanguage
                )
            } else {
                ocrTesseractSettingsItems(
                    context = context,
                    activeModel = activeModel,
                    fastStorageBytes = fastStorageBytes,
                    bestStorageBytes = bestStorageBytes,
                    selectedLanguages = selectedLanguages,
                    installedLanguages = installedLanguages,
                    downloadingLang = downloadingLang,
                    onSelectModel = onSelectModel,
                    onToggleLanguage = onToggleLanguage,
                    onDownloadAndInstall = { downloadAndInstall(it) }
                )
            }

            ocrCacheMaintenanceItems(
                numberFormat = numberFormat,
                cachedTextCount = cachedTextCount,
                onRequestClearCache = { showClearCacheDialog = true }
            )
        }
    }
    OcrClearCacheDialog(
        showDialog = showClearCacheDialog,
        onDismiss = { showClearCacheDialog = false },
        onCleared = { deleted ->
            actionMessage = "Cleared $deleted cached OCR text results."
            showClearCacheDialog = false
            refreshStats()
        }
    )
}
