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
internal fun OcrIndexingExpandedContent(
    ocrState: OcrState,
    selectedEngineOption: String,
    isMlKit: Boolean,
    ocrLanguages: Set<String>,
    activeTesseractModel: TesseractModel,
    onEngineChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onToggleMlKitLanguage: (String) -> Unit,
    onToggleLanguage: (String) -> Unit,
    onDownloadAndSelectLanguage: (String) -> Unit
) {
    val context = LocalContext.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    // Engine Label (No explanatory text)
                    Text(
                        text = "Engine",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    // Engine Segmented Buttons: ML Kit | Tess-Best | Tess-Fast
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SegmentOption(
                                text = "ML Kit",
                                isSelected = selectedEngineOption == "mlkit",
                                onClick = {
                                    if (!ocrState.isRunning) onEngineChange("google_mlkit")
                                },
                                modifier = Modifier.weight(1f)
                            )

                            VerticalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxHeight(0.6f).width(1.dp)
                            )

                            SegmentOption(
                                text = "Tess-Best",
                                isSelected = selectedEngineOption == "tess_best",
                                onClick = {
                                    if (!ocrState.isRunning) {
                                        onEngineChange("tesseract")
                                        onModelChange("best")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            VerticalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxHeight(0.6f).width(1.dp)
                            )

                            SegmentOption(
                                text = "Tess-Fast",
                                isSelected = selectedEngineOption == "tess_fast",
                                onClick = {
                                    if (!ocrState.isRunning) {
                                        onEngineChange("tesseract")
                                        onModelChange("fast")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Language(s) Label (No explanatory text)
                    Text(
                        text = "Language(s)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    // Language Chips Row (Horizontally scrollable with all supported languages)
                    if (isMlKit) {
                        val mlKitOptions = remember {
                            MlKitLanguage.entries.map { it.code to it.title }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("ocr_languages_mlkit_row"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            mlKitOptions.forEach { (code, label) ->
                                val isSelected = code in ocrState.mlKitLanguages
                                LanguageChip(
                                    label = label,
                                    isSelected = isSelected,
                                    onClick = { onToggleMlKitLanguage(code) },
                                    testTag = "ocr_lang_$code"
                                )
                            }
                        }
                    } else {
                        val tesseractOptions = remember {
                            TesseractOcr.supportedLanguages.entries.map { it.value to it.key }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("ocr_languages_tesseract_row"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tesseractOptions.forEach { (code, label) ->
                                val isSelected = code in ocrLanguages
                                LanguageChip(
                                    label = label,
                                    isSelected = isSelected,
                                    onClick = {
                                        val isInstalled = TesseractOcr.isLanguageInstalled(context, code, activeTesseractModel) ||
                                            TesseractOcr.isLanguageBundledInAssets(context, code, activeTesseractModel)
                                        if (!isInstalled) {
                                            onDownloadAndSelectLanguage(code)
                                        } else {
                                            onToggleLanguage(code)
                                        }
                                    },
                                    testTag = "ocr_lang_$code"
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                }
}
