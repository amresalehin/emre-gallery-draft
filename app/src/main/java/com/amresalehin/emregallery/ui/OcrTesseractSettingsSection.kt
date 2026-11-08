package com.amresalehin.emregallery.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.ocr.TesseractModel
import com.amresalehin.emregallery.ocr.TesseractOcr


internal fun androidx.compose.foundation.lazy.LazyListScope.ocrTesseractSettingsItems(
    context: android.content.Context,
    activeModel: TesseractModel,
    fastStorageBytes: Long,
    bestStorageBytes: Long,
    selectedLanguages: Set<String>,
    installedLanguages: Set<String>,
    downloadingLang: String?,
    onSelectModel: (String) -> Unit,
    onToggleLanguage: (String) -> Unit,
    onDownloadAndInstall: (String) -> Unit
) {
                item {
                    Text(
                        "Tesseract Recognition Model",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Switch between integer-quantized fast inference and full-precision maximum accuracy models.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModelOptionCard(
                            model = TesseractModel.FAST,
                            isSelected = activeModel == TesseractModel.FAST,
                            storageBytes = fastStorageBytes,
                            onSelect = { onSelectModel(TesseractModel.FAST.id) },
                            icon = Icons.Default.Bolt,
                            accentColor = MaterialTheme.colorScheme.primary
                        )

                        ModelOptionCard(
                            model = TesseractModel.BEST,
                            isSelected = activeModel == TesseractModel.BEST,
                            storageBytes = bestStorageBytes,
                            onSelect = { onSelectModel(TesseractModel.BEST.id) },
                            icon = Icons.Default.HighQuality,
                            accentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Section 2: Model Comparison Summary
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CompareArrows,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Model Comparison",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCompareBox(
                                    title = "Speed",
                                    fastValue = "4x Faster",
                                    bestValue = "Standard",
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCompareBox(
                                    title = "Accuracy",
                                    fastValue = "High (98%)",
                                    bestValue = "Max (99.5%)",
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCompareBox(
                                    title = "RAM/Size",
                                    fastValue = "~4 MB/lang",
                                    bestValue = "~15 MB/lang",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Section 3: Language Management
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Languages (${activeModel.title})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Select languages enabled during photo indexing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    "${ocrLanguages.size} active",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                items(TesseractOcr.supportedLanguages.entries.toList()) { (name, code) ->
                    val isSelected = code in ocrLanguages
                    val isBundled = remember(code, activeModel) {
                        TesseractOcr.isLanguageBundledInAssets(context, code, activeModel)
                    }
                    val isInstalled = remember(code, activeModel, fastStorageBytes, bestStorageBytes) {
                        TesseractOcr.isLanguageInstalled(context, code, activeModel)
                    }
                    val isInstalling = installingLanguageCode == code

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleLanguage(code) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleLanguage(code) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "($code)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    when {
                                        isBundled -> "Bundled in APK · Ready offline"
                                        isInstalled -> "Downloaded & installed on device"
                                        else -> "Official GitHub download (${activeModel.memoryFootprint})"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBundled || isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isInstalling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else if (isBundled) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        "Bundled",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else if (isInstalled) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        "Installed",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onDownloadAndInstall(code) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Install", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
}
