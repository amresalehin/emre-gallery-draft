package com.amresalehin.emregallery.ui
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
internal fun androidx.compose.foundation.lazy.LazyListScope.viewerActionsOcrItem(
    item: MediaItem,
    detailsState: ViewerDetailsState,
    selectedEngine: String,
    onSelectedEngineChange: (String) -> Unit,
    engineExpanded: Boolean,
    onEngineExpandedChange: (Boolean) -> Unit,
    modeExpanded: Boolean,
    onModeExpandedChange: (Boolean) -> Unit,
    langExpanded: Boolean,
    onLangExpandedChange: (Boolean) -> Unit
) {
        if (!item.isVideo) {
            item(key = "actions_ocr") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Extract text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
                        onDismissRequest = { onEngineExpandedChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tesseract") },
                            onClick = {
                                selectedEngine = "Tesseract"
                                engineExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Google ML Kit") },
                            onClick = {
                                selectedEngine = "Google ML Kit"
                                engineExpanded = false
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isTesseract = selectedEngine == "Tesseract"
                    val isBest = detailsState.ocrModel.equals("best", ignoreCase = true)
                    ExposedDropdownMenuBox(
                        expanded = modeExpanded,
                        onExpandedChange = { if (isTesseract) modeExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = if (isTesseract) (if (isBest) "Best" else "Fast") else "Multi-Script",
                            onValueChange = {},
                            readOnly = true,
                            enabled = isTesseract,
                            label = { Text("Mode") },
                            trailingIcon = { if (isTesseract) ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                        if (isTesseract) {
                            ExposedDropdownMenu(
                                expanded = modeExpanded,
                                onDismissRequest = { onModeExpandedChange(false) }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Fast") },
                                    onClick = {
                                        detailsState.ocrModel = "fast"
                                        onModeExpandedChange(false)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Best") },
                                    onClick = {
                                        detailsState.ocrModel = "best"
                                        onModeExpandedChange(false)
                                    }
                                )
                            }
                        }
                    }
                    val currentLangCode = detailsState.ocrLanguages.firstOrNull() ?: "eng"
                    val currentLangName = TesseractOcr.supportedLanguages.entries.firstOrNull { it.value == currentLangCode }?.key ?: "English"
                    val currentMlKitLabel = remember(detailsState.mlKitLanguages.toList()) {
                        com.amresalehin.emregallery.ocr.MlKitLanguage.getDisplayLabel(detailsState.mlKitLanguages)
                    }
                    ExposedDropdownMenuBox(
                        expanded = langExpanded,
                        onExpandedChange = { langExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = if (isTesseract) currentLangName else currentMlKitLabel,
                            onValueChange = {},
                            readOnly = true,
                            enabled = true,
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
                            onDismissRequest = { onLangExpandedChange(false) }
                        ) {
                            if (isTesseract) {
                                TesseractOcr.supportedLanguages.forEach { (name, code) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            detailsState.ocrLanguages.clear()
                                            detailsState.ocrLanguages.add(code)
                                            onLangExpandedChange(false)
                                        }
                                    )
                                }
                            } else {
                                val allSelected = detailsState.mlKitLanguages.containsAll(com.amresalehin.emregallery.ocr.MlKitLanguage.ALL_CODES)
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("All Scripts (${com.amresalehin.emregallery.ocr.MlKitLanguage.entries.size})", fontWeight = FontWeight.SemiBold)
                                            Text("Latin, Chinese, Devanagari, Japanese, Korean", style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                    trailingIcon = {
                                        if (allSelected) {
                                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    },
                                    onClick = {
                                        detailsState.mlKitLanguages.clear()
                                        if (allSelected) {
                                            detailsState.mlKitLanguages.add("latin")
                                        } else {
                                            detailsState.mlKitLanguages.addAll(com.amresalehin.emregallery.ocr.MlKitLanguage.ALL_CODES)
                                        }
                                        onLangExpandedChange(false)
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                com.amresalehin.emregallery.ocr.MlKitLanguage.entries.forEach { lang ->
                                    val isSelected = lang.code in detailsState.mlKitLanguages
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(lang.title, fontWeight = FontWeight.SemiBold)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("(${lang.sampleText})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Text(lang.nativeScript, style = MaterialTheme.typography.labelSmall)
                                            }
                                        },
                                        trailingIcon = {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = null
                                            )
                                        },
                                        onClick = {
                                            if (isSelected) {
                                                if (detailsState.mlKitLanguages.size > 1) {
                                                    detailsState.mlKitLanguages.remove(lang.code)
                                                }
                                            } else {
                                                detailsState.mlKitLanguages.add(lang.code)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                detailsState.runOcr(engine = if (selectedEngine == "Google ML Kit") "google_mlkit" else "tesseract")
                            }
                        },
                        enabled = !item.isVideo && !detailsState.ocrBusy,
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
