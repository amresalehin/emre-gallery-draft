package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.FlorenceModelManager
import com.amresalehin.emregallery.data.FlorenceModelState
import com.amresalehin.emregallery.data.FlorenceStatus
import kotlinx.coroutines.launch


@Composable
internal fun AiLocalModelsTabContent(
    isDark: Boolean,
    allModelStates: Map<String, FlorenceModelState>,
    onSelectModelForDetails: (FlorenceModelManager.LocalModelSpec) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
                    run {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FlorenceModelManager.SUPPORTED_MODELS.forEach { model ->
                                val modelState = allModelStates[model.id] ?: FlorenceModelState()

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.70f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.85f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                    ) {
                                        // Header row without any icon, clean and minimal
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = model.displayName,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )

                                            DynamicFlorenceStatusButton(
                                                state = modelState,
                                                testTagPrefix = model.testTagPrefix,
                                                isDark = isDark,
                                                onInstall = {
                                                    scope.launch {
                                                        FlorenceModelManager.downloadAndInstallModel(context, model.id)
                                                    }
                                                },
                                                onCancel = {
                                                    FlorenceModelManager.cancelDownload(context, model.id)
                                                },
                                                onDetails = {
                                                    onSelectModelForDetails(model)
                                                }
                                            )
                                        }

                                        // Progress bar when downloading or verifying
                                        if (modelState.status == FlorenceStatus.DOWNLOADING || modelState.status == FlorenceStatus.VERIFYING) {
                                            Spacer(Modifier.height(14.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                LinearProgressIndicator(
                                                    progress = { modelState.progress.coerceIn(0f, 1f) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .clip(CircleShape),
                                                    color = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF161A20),
                                                    trackColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0x1F000000)
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = if (modelState.totalBytes > 0) {
                                                            "${formatBytes(modelState.bytesDownloaded)} / ${formatBytes(modelState.totalBytes)}"
                                                        } else {
                                                            "Downloading..."
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "${(modelState.progress * 100).toInt()}%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.5.sp,
                                                        color = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF161A20)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
}

@Composable
internal fun AiLocalModelDetailsDialog(
    selectedModelForDetails: FlorenceModelManager.LocalModelSpec?,
    allModelStates: Map<String, FlorenceModelState>,
    onDismiss: () -> Unit,
    onShowSnackbar: ((String) -> Unit)?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Local Model Details / Options Dialog
    selectedModelForDetails?.let { model ->
        val state = allModelStates[model.id] ?: FlorenceModelState()
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = {
                Text(
                    "${model.displayName} Local Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Status: ${if (state.status == FlorenceStatus.INSTALLED) "Installed & Validated" else "Not installed"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Model: ${model.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.installedSizeBytes > 0) {
                        Text(
                            "Storage footprint: ${formatBytes(state.installedSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.installedPath?.let { path ->
                        Text(
                            "Path: $path",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onDismiss() }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            FlorenceModelManager.uninstallModel(context, model.id)
                            onDismiss()
                            onShowSnackbar?.invoke("${model.displayName} uninstalled")
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Uninstall")
                }
            }
        )
    }
}
