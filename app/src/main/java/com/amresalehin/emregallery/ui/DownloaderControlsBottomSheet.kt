package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.DownloadActivityItem
import com.amresalehin.emregallery.data.DownloadActivityManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloaderControlsBottomSheet(
    context: android.content.Context,
    showControlsSheet: Boolean,
    onDismiss: () -> Unit,
    failedItems: List<DownloadActivityItem>,
    downloadingItems: List<DownloadActivityItem>,
    finishedItems: List<DownloadActivityItem>
) {
    if (showControlsSheet) {
        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Activity Controls",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (failedItems.isNotEmpty()) {
                    Button(
                        onClick = {
                            DownloadActivityManager.retryAllFailed(context)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry All Failed (${failedItems.size})")
                    }
                }

                if (downloadingItems.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            DownloadActivityManager.cancelAllActive(context)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel All Active Downloads")
                    }
                }

                if (finishedItems.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            DownloadActivityManager.clearFinished()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Past Download History")
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloaderControlsBottomSheet(
    context: android.content.Context,
    showControlsSheet: Boolean,
    onDismiss: () -> Unit,
    failedItems: List<DownloadActivityItem>,
    downloadingItems: List<DownloadActivityItem>,
    finishedItems: List<DownloadActivityItem>
) {
    if (showControlsSheet) {
        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Activity Controls",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (failedItems.isNotEmpty()) {
                    Button(
                        onClick = {
                            DownloadActivityManager.retryAllFailed(context)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry All Failed (${failedItems.size})")
                    }
                }

                if (downloadingItems.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            DownloadActivityManager.cancelAllActive(context)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel All Active Downloads")
                    }
                }

                if (finishedItems.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            DownloadActivityManager.clearFinished()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Past Download History")
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
