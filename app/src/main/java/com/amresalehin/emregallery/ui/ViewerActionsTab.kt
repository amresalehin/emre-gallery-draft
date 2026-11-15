package com.amresalehin.emregallery.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.DetectedMediaFace
import com.amresalehin.emregallery.face.FaceProcessingCoordinator
import com.amresalehin.emregallery.ocr.MlKitLanguage
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsTabContent(
    item: MediaItem,
    customTags: Set<String>,
    detectedFaces: List<DetectedMediaFace>,
    metadata: Map<String, String>,
    detailsState: ViewerDetailsState,
    onRenameFaceClick: (DetectedMediaFace) -> Unit,
    onAddTo: (() -> Unit)? = null,
    onAddLocationClick: (() -> Unit)? = null,
    onAddTagClick: (() -> Unit)? = null,
    onRescanPhoto: (() -> Unit)? = null,
    onAddPersonClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val coroutineScope = rememberCoroutineScope()
    val hasPeople = detectedFaces.isNotEmpty()

    var selectedEngine by remember { mutableStateOf("Tesseract") }
    var engineExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var langExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val isProcessingActive by FaceProcessingCoordinator.isProcessingActive.collectAsStateWithLifecycle()
    val isBusy = isProcessingActive || FaceProcessingCoordinator.isBusy(context)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (hasPeople) {
            item(key = "actions_people") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "People",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (onRescanPhoto != null) {
                            TextButton(
                                onClick = onRescanPhoto,
                                enabled = !isBusy,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Re-scan")
                            }
                        }
                    }
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
                                enabled = !isBusy,
                                onRename = {
                                    if (!FaceProcessingCoordinator.isBusy(context)) {
                                        onRenameFaceClick(face)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        item(key = "actions_ocr") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Extract text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Full-width Engine dropdown
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
                            text = { Text("Google ML Kit") },
                            onClick = {
                                selectedEngine = "Google ML Kit"
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
                    }

                    val currentLangCode = detailsState.ocrLanguages.firstOrNull() ?: "eng"
                    val currentLangName = TesseractOcr.supportedLanguages.entries.firstOrNull { it.value == currentLangCode }?.key ?: "English"
                    val currentMlKitLabel = remember(detailsState.mlKitLanguages.toList()) {
                        MlKitLanguage.getDisplayLabel(detailsState.mlKitLanguages)
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
                            onDismissRequest = { langExpanded = false }
                        ) {
                            if (isTesseract) {
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
                            } else {
                                val allSelected = detailsState.mlKitLanguages.containsAll(MlKitLanguage.ALL_CODES)
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("All Scripts (${MlKitLanguage.entries.size})", fontWeight = FontWeight.SemiBold)
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
                                            detailsState.mlKitLanguages.addAll(MlKitLanguage.ALL_CODES)
                                        }
                                        langExpanded = false
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                MlKitLanguage.entries.forEach { lang ->
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

                // Compact Run OCR button aligned to the right
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
                    DetailCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
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
