package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun OcrClearCacheDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onCleared: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text("Clear OCR Text Cache?") },
            text = {
                Text("This will remove all cached text recognized from photos. You can re-index photos at any time using your selected engine and models.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val deleted = TesseractOcr.clearCachedResults(context)
                            withContext(Dispatchers.Main) {
                                onCleared(deleted)
                            }
                        }
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text("Cancel")
                }
            }
        )
    }
}


internal fun androidx.compose.foundation.lazy.LazyListScope.ocrCacheMaintenanceItems(
    numberFormat: java.text.NumberFormat,
    cachedTextCount: Int,
    onRequestClearCache: () -> Unit
) {
            // Common Section: Cache & Maintenance
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "OCR Cache & Storage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Cached Recognized Photos",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${numberFormat.format(cachedTextCount)} images indexed and searchable",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { onRequestClearCache() },
                                enabled = cachedTextCount > 0,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
}
