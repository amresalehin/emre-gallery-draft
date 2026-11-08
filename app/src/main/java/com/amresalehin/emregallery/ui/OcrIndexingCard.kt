package com.amresalehin.emregallery.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.ocr.MlKitLanguage
import com.amresalehin.emregallery.ocr.TesseractModel
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
fun OcrIndexingCard(
    totalPhotos: Int,
    ocrState: OcrState,
    ocrLanguages: Set<String>,
    countsReady: Boolean,
    cardBackground: Color,
    cardBorder: BorderStroke,
    numberFormat: NumberFormat,
    onToggleOcr: () -> Unit,
    onStopOcr: () -> Unit,
    onReindexOcr: () -> Unit,
    onEngineChange: (String) -> Unit = {},
    onModelChange: (String) -> Unit,
    onToggleLanguage: (String) -> Unit,
    onToggleMlKitLanguage: (String) -> Unit = {},
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) {
        if (initiallyExpanded) {
            isExpanded = true
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ocrProgress = if (totalPhotos > 0) (ocrState.processed.toFloat() / totalPhotos).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(targetValue = ocrProgress, label = "ocr_progress")
    val ocrPercent = (ocrProgress * 100f).roundToInt()

    val isMlKit = ocrState.engine.equals("google_mlkit", ignoreCase = true) || ocrState.engine.equals("mlkit", ignoreCase = true)
    val isBestModel = ocrState.model.equals("best", ignoreCase = true)
    val activeTesseractModel = if (isBestModel) TesseractModel.BEST else TesseractModel.FAST

    val selectedEngineOption = when {
        isMlKit -> "mlkit"
        isBestModel -> "tess_best"
        else -> "tess_fast"
    }

    var showActionMenu by remember { mutableStateOf(false) }
    var downloadingLanguageCode by remember { mutableStateOf<String?>(null) }

    fun downloadAndSelectLanguage(code: String) {
        if (downloadingLanguageCode != null) return
        downloadingLanguageCode = code
        scope.launch(Dispatchers.IO) {
            val success = runCatching {
                TesseractOcr.prepareLanguages(context, listOf(code), activeTesseractModel)
                true
            }.getOrDefault(false)

            if (success) {
                onToggleLanguage(code)
            }
            downloadingLanguageCode = null
        }
    }

    val isRunning = ocrState.isRunning && !ocrState.isPaused
    val isPaused = ocrState.isRunning && ocrState.isPaused
    val isCompleted = totalPhotos > 0 && ocrState.processed >= totalPhotos

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("ocr_indexing_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: OCR icon, OCR Title, Dynamic Corner Box, Expand Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clickable Left area (Icon + Title) to toggle expansion
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circle with OCR Icon matching WelcomeScreen
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            OcrIcon(tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Text(
                        text = "OCR",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                // Optional Stop button when running/paused
                if (ocrState.isRunning) {
                    IconButton(
                        onClick = onStopOcr,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("ocr_stop_button")
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

                OcrCornerActionBox(
                    ocrState = ocrState,
                    ocrProgress = ocrProgress,
                    ocrPercent = ocrPercent,
                    showActionMenu = showActionMenu,
                    onShowActionMenuChange = { showActionMenu = it },
                    onToggleOcr = onToggleOcr,
                    onClearAndReindex = onClearAndReindex
                )
                Spacer(Modifier.width(6.dp))

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("ocr_expand_toggle")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse OCR" else "Expand OCR",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Minimalist Divider with integrated Progress Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                if (isExpanded) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (ocrProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }

            if (isExpanded) {
                OcrIndexingExpandedContent(
                    ocrState = ocrState,
                    selectedEngineOption = selectedEngineOption,
                    isMlKit = isMlKit,
                    ocrLanguages = ocrLanguages,
                    activeTesseractModel = activeTesseractModel,
                    onEngineChange = onEngineChange,
                    onModelChange = onModelChange,
                    onToggleMlKitLanguage = onToggleMlKitLanguage,
                    onToggleLanguage = onToggleLanguage,
                    onDownloadAndSelectLanguage = { downloadAndSelectLanguage(it) }
                )
            }
        }
    }
}
