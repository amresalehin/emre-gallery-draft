package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.FlorenceModelState
import com.amresalehin.emregallery.data.FlorenceStatus

@Composable
internal fun DynamicFlorenceStatusButton(
    state: FlorenceModelState,
    isDark: Boolean,
    testTagPrefix: String = "florence",
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDetails: () -> Unit
) {
    when (state.status) {
        FlorenceStatus.NOT_INSTALLED -> {
            Surface(
                onClick = onInstall,
                shape = CircleShape,
                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color(0x0F000000),
                border = BorderStroke(
                    1.dp,
                    if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f) else Color(0x1F000000)
                ),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("${testTagPrefix}_install_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = "Install Model",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Install",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }

        FlorenceStatus.DOWNLOADING, FlorenceStatus.VERIFYING -> {
            Surface(
                shape = CircleShape,
                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color(0x0F000000),
                border = BorderStroke(
                    1.dp,
                    if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f) else Color(0x26000000)
                ),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("${testTagPrefix}_downloading_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { state.progress.coerceIn(0.05f, 1f) },
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.8.dp,
                        color = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF161A20)
                    )
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF161A20)
                        )
                    )
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel download",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        FlorenceStatus.INSTALLED -> {
            val bg = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF161A20)
            val border = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF161A20)
            val contentColor = if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White

            Surface(
                onClick = onDetails,
                shape = CircleShape,
                color = bg,
                border = BorderStroke(1.dp, border),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("${testTagPrefix}_installed_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Installed",
                        modifier = Modifier.size(14.dp),
                        tint = contentColor
                    )
                    Text(
                        text = "Installed",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        )
                    )
                }
            }
        }

        FlorenceStatus.ERROR -> {
            val bg = if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            val border = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            val contentColor = if (isDark) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onErrorContainer

            Surface(
                onClick = onInstall,
                shape = CircleShape,
                color = bg,
                border = BorderStroke(1.dp, border),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("${testTagPrefix}_retry_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry download",
                        modifier = Modifier.size(14.dp),
                        tint = contentColor
                    )
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        )
                    )
                }
            }
        }
    }
}
